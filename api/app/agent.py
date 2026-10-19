"""보험 약관 Q&A 에이전트.

프레임워크 없이 tool use 루프를 직접 구현했다. LLM 백엔드는 두 가지:
- ollama: 로컬 GPU (기본값, 무료)
- anthropic: Claude API

도구 호출 내역(trace), 토큰 사용량, 지연시간을 기록해 agent_runs 테이블에 남긴다.
"""
import json
import time
from dataclasses import dataclass, field
from functools import lru_cache

import anthropic
import ollama
from psycopg.types.json import Jsonb

from .config import settings
from .db import pool
from .tools import TOOL_DEFINITIONS, run_tool

SYSTEM_PROMPT = """당신은 보험 약관 상담을 돕는 에이전트입니다. 고객 상담원과 설계사가 약관 내용을 빠르고 정확하게 확인하도록 돕습니다.

원칙:
- 답변은 반드시 도구로 찾은 약관 내용에 근거합니다. 기억이나 일반 상식으로 보장 여부를 단정하지 않습니다.
- 근거 조항을 [상품명 제N조(제목), p.페이지] 형식으로 인용합니다.
- 질문에 상품명이 없으면 사용자에게 되묻기 전에 list_products 로 등록 상품을 확인합니다. 상품이 하나뿐이면 그 상품 기준으로 답하고, 여러 개면 전체 검색 후 상품별로 정리합니다.
- 보장 조건과 면책(보상하지 않는 손해) 조항을 함께 확인합니다.
- 금액 계산이 필요하면 약관의 비율·공제금액·한도를 적용한 계산 과정을 보여줍니다.
- 약관에서 근거를 찾지 못하면 찾지 못했다고 말하고, 추가로 확인할 내용을 안내합니다.
- 최종 보장 여부는 보험사 심사에 따라 달라질 수 있음을 마지막에 한 줄로 안내합니다.
- 한국어로 간결하게 답합니다."""

# 거절(refusal) 시 서버가 자동으로 다른 모델로 재시도하도록 하는 베타 기능
FALLBACK_BETA = "server-side-fallback-2026-07-01"

LIMIT_MESSAGE = "도구 호출 횟수 한도에 도달해 답변을 완성하지 못했습니다."


@dataclass
class AgentResult:
    answer: str = ""
    provider: str = ""
    model: str = ""
    trace: list[dict] = field(default_factory=list)
    stop_reason: str | None = None
    input_tokens: int = 0
    output_tokens: int = 0
    latency_ms: int = 0
    run_id: int | None = None


def _execute(name: str, tool_input: dict, trace: list[dict]) -> tuple[str, bool]:
    """도구 하나를 실행하고 trace 에 기록한다. 오류는 모델에게 알려서 스스로 복구하게 한다."""
    started = time.perf_counter()
    try:
        output, is_error = run_tool(name, tool_input), False
    except Exception as e:
        output, is_error = f"도구 실행 오류: {e}", True
    trace.append({
        "tool": name,
        "input": tool_input,
        "is_error": is_error,
        "latency_ms": int((time.perf_counter() - started) * 1000),
        "output_preview": output[:300],
    })
    return output, is_error


# ---------------------------------------------------------------- Claude

@lru_cache(maxsize=1)
def _claude() -> anthropic.Anthropic:
    # API 키 없이도 서버가 뜨도록 첫 호출 시점에 생성
    return anthropic.Anthropic()


def _run_claude(question: str, result: AgentResult) -> None:
    messages: list[dict] = [{"role": "user", "content": question}]

    for _ in range(settings.max_agent_turns):
        response = _claude().beta.messages.create(
            model=settings.claude_model,
            max_tokens=16000,
            system=SYSTEM_PROMPT,
            tools=TOOL_DEFINITIONS,
            messages=messages,
            output_config={"effort": settings.claude_effort},
            betas=[FALLBACK_BETA],
            fallbacks="default",
        )
        result.input_tokens += response.usage.input_tokens
        result.output_tokens += response.usage.output_tokens
        result.stop_reason = response.stop_reason

        # thinking 블록까지 포함해 응답 전체를 그대로 이어 붙인다
        messages.append({"role": "assistant", "content": response.content})

        if response.stop_reason == "tool_use":
            tool_results = []
            for block in response.content:
                if block.type == "tool_use":
                    output, is_error = _execute(block.name, block.input, result.trace)
                    tool_result = {"type": "tool_result", "tool_use_id": block.id, "content": output}
                    if is_error:
                        tool_result["is_error"] = True
                    tool_results.append(tool_result)
            # 병렬 호출 결과는 하나의 user 메시지로 묶어서 돌려준다
            messages.append({"role": "user", "content": tool_results})
            continue

        if response.stop_reason == "pause_turn":
            continue

        if response.stop_reason == "refusal":
            result.answer = "요청을 처리할 수 없습니다. 질문을 바꿔서 다시 시도해 주세요."
        else:  # end_turn, max_tokens 등
            result.answer = "".join(b.text for b in response.content if b.type == "text").strip()
        return

    result.answer, result.stop_reason = LIMIT_MESSAGE, "max_agent_turns"


# ---------------------------------------------------------------- Ollama

OLLAMA_TOOLS = [
    {
        "type": "function",
        "function": {"name": t["name"], "description": t["description"], "parameters": t["input_schema"]},
    }
    for t in TOOL_DEFINITIONS
]


@lru_cache(maxsize=1)
def _ollama() -> ollama.Client:
    return ollama.Client(host=settings.ollama_host, timeout=300)


def _run_ollama(question: str, result: AgentResult) -> None:
    messages: list = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": question},
    ]

    for _ in range(settings.max_agent_turns):
        response = _ollama().chat(model=settings.ollama_model, messages=messages, tools=OLLAMA_TOOLS)
        result.input_tokens += response.prompt_eval_count or 0
        result.output_tokens += response.eval_count or 0
        result.stop_reason = response.done_reason

        message = response.message
        messages.append(message)

        if message.tool_calls:
            result.stop_reason = "tool_use"
            for call in message.tool_calls:
                output, _ = _execute(call.function.name, dict(call.function.arguments), result.trace)
                messages.append({"role": "tool", "tool_name": call.function.name, "content": output})
            continue

        result.answer = (message.content or "").strip()
        return

    result.answer, result.stop_reason = LIMIT_MESSAGE, "max_agent_turns"


# ---------------------------------------------------------------- entrypoint

def run_agent(question: str) -> AgentResult:
    started = time.perf_counter()
    if settings.llm_provider == "anthropic":
        result = AgentResult(provider="anthropic", model=settings.claude_model)
        _run_claude(question, result)
    else:
        result = AgentResult(provider="ollama", model=settings.ollama_model)
        _run_ollama(question, result)

    result.latency_ms = int((time.perf_counter() - started) * 1000)
    result.run_id = _save_run(question, result)
    return result


def _save_run(question: str, r: AgentResult) -> int:
    with pool.connection() as conn:
        return conn.execute(
            "INSERT INTO agent_runs (question, answer, provider, model, trace, stop_reason, "
            "input_tokens, output_tokens, latency_ms) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id",
            (question, r.answer, r.provider, r.model, Jsonb(r.trace), r.stop_reason,
             r.input_tokens, r.output_tokens, r.latency_ms),
        ).fetchone()["id"]


if __name__ == "__main__":
    import sys

    pool.open()
    res = run_agent(" ".join(sys.argv[1:]) or "등록된 상품 목록을 알려줘")
    print(res.answer)
    print(json.dumps(res.trace, ensure_ascii=False, indent=2))

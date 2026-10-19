from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

import anthropic
import ollama

from .agent import run_agent
from .db import pool
from .ingest import ingest_all
from .tools import list_products, search_policy


@asynccontextmanager
async def lifespan(_: FastAPI):
    pool.open()
    # 최초 기동 시(문서 없음) data/policies 를 자동 적재
    if not list_products():
        await run_in_threadpool(ingest_all)
    yield
    pool.close()


app = FastAPI(title="Ax - 보험 약관 AI 에이전트", lifespan=lifespan)


class SearchRequest(BaseModel):
    query: str
    product: str = ""
    top_k: int = 5


class ChatRequest(BaseModel):
    question: str


@app.get("/health")
def health():
    with pool.connection() as conn:
        conn.execute("SELECT 1")
    return {"status": "ok"}


@app.get("/documents")
def documents():
    return list_products()


@app.post("/ingest")
async def ingest():
    return await run_in_threadpool(ingest_all)


@app.post("/search")
async def search(req: SearchRequest):
    """LLM 없이 검색 결과만 확인 (검색 품질 디버깅 / Eval 용)."""
    return await run_in_threadpool(search_policy, req.query, req.product, req.top_k)


@app.post("/chat")
async def chat(req: ChatRequest):
    try:
        result = await run_in_threadpool(run_agent, req.question)
    except anthropic.AuthenticationError:
        raise HTTPException(500, "ANTHROPIC_API_KEY 가 올바르지 않습니다. .env 를 확인하세요.")
    except anthropic.APIStatusError as e:
        raise HTTPException(502, f"Claude API 오류 ({e.status_code}): {e.message}")
    except anthropic.APIConnectionError:
        raise HTTPException(502, "Claude API 에 연결할 수 없습니다.")
    except ollama.ResponseError as e:
        raise HTTPException(502, f"Ollama 오류: {e.error}")
    except ConnectionError:
        raise HTTPException(502, "Ollama 에 연결할 수 없습니다. OLLAMA_HOST 를 확인하세요.")
    return result


@app.get("/runs")
def runs(limit: int = 20):
    with pool.connection() as conn:
        return conn.execute(
            "SELECT id, question, answer, provider, model, trace, stop_reason, input_tokens, output_tokens, latency_ms, created_at "
            "FROM agent_runs ORDER BY id DESC LIMIT %s",
            (limit,),
        ).fetchall()

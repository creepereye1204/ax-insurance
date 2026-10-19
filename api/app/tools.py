"""에이전트가 호출하는 도구들. 각 도구는 JSON 문자열을 반환한다."""
import json

from .db import pool
from .embedding import embed_query

TOOL_DEFINITIONS = [
    {
        "name": "list_products",
        "description": "DB에 등록된 보험 상품(약관 문서) 목록을 반환한다. 어떤 상품이 있는지 모를 때 먼저 호출한다.",
        "strict": True,
        "input_schema": {"type": "object", "properties": {}, "required": [], "additionalProperties": False},
    },
    {
        "name": "search_policy",
        "description": (
            "약관 본문을 의미 기반으로 검색해 관련 조항 청크를 반환한다. "
            "보장 여부, 면책 사유, 지급 조건 등 약관 내용이 필요할 때 사용한다. "
            "한 번에 하나의 구체적인 질문으로 검색하고, 결과가 부족하면 표현을 바꿔 다시 검색한다."
        ),
        "strict": True,
        "input_schema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "검색할 내용 (자연어)"},
                "product": {
                    "type": "string",
                    "description": "특정 상품으로 한정할 때 상품명. 전체 검색이면 빈 문자열.",
                },
            },
            "required": ["query", "product"],
            "additionalProperties": False,
        },
    },
    {
        "name": "get_article",
        "description": "특정 상품의 특정 조항 전문을 가져온다. 검색 결과가 잘려 있거나 조항 전체 맥락이 필요할 때 사용한다.",
        "strict": True,
        "input_schema": {
            "type": "object",
            "properties": {
                "product": {"type": "string", "description": "상품명"},
                "article_no": {"type": "string", "description": "조항 번호. 예: '제3조', '제12조의2'"},
            },
            "required": ["product", "article_no"],
            "additionalProperties": False,
        },
    },
]


def list_products() -> list[dict]:
    with pool.connection() as conn:
        return conn.execute(
            "SELECT d.product, d.num_pages, count(c.id) AS chunks "
            "FROM documents d LEFT JOIN chunks c ON c.document_id = d.id "
            "GROUP BY d.id ORDER BY d.product"
        ).fetchall()


def search_policy(query: str, product: str = "", top_k: int = 5) -> list[dict]:
    vec = embed_query(query)
    with pool.connection() as conn:
        rows = conn.execute(
            "SELECT d.product, c.article, c.page, c.content, 1 - (c.embedding <=> %s) AS score "
            "FROM chunks c JOIN documents d ON d.id = c.document_id "
            "WHERE (%s = '' OR d.product = %s) "
            "ORDER BY c.embedding <=> %s LIMIT %s",
            (vec, product, product, vec, top_k),
        ).fetchall()
    for r in rows:
        r["score"] = round(float(r["score"]), 4)
    return rows


def get_article(product: str, article_no: str) -> list[dict]:
    with pool.connection() as conn:
        return conn.execute(
            "SELECT d.product, c.article, c.page, c.content "
            "FROM chunks c JOIN documents d ON d.id = c.document_id "
            "WHERE d.product = %s AND c.article LIKE %s ORDER BY c.chunk_index",
            (product, article_no.replace(" ", "") + "(%"),
        ).fetchall()


_HANDLERS = {
    "list_products": lambda _: list_products(),
    "search_policy": lambda i: search_policy(i["query"], i.get("product", "")),
    "get_article": lambda i: get_article(i["product"], i["article_no"]),
}


def run_tool(name: str, tool_input: dict) -> str:
    if name not in _HANDLERS:
        raise ValueError(f"알 수 없는 도구: {name}")
    result = _HANDLERS[name](tool_input)
    if not result:
        return json.dumps({"result": [], "note": "결과 없음. 검색어나 상품명을 바꿔 보세요."}, ensure_ascii=False)
    return json.dumps(result, ensure_ascii=False, default=str)

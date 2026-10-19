"""약관 문서(PDF/TXT)를 조항 단위로 쪼개 임베딩 후 DB에 저장한다.

실행: python -m app.ingest
"""
import re
from dataclasses import dataclass
from pathlib import Path

from pypdf import PdfReader

from .config import settings
from .db import pool
from .embedding import embed_passages

# "제3조(보험금의 지급사유)", "제12조의2(계약의 해지)" 같은 조항 제목
ARTICLE_RE = re.compile(r"(?m)^\s*(제\s*\d+\s*조(?:\s*의\s*\d+)?\s*[(（][^)）\n]{1,40}[)）])")


@dataclass
class Chunk:
    article: str | None
    page: int
    content: str


def read_pages(path: Path) -> list[str]:
    if path.suffix.lower() == ".pdf":
        return [page.extract_text() or "" for page in PdfReader(path).pages]
    return [path.read_text(encoding="utf-8")]


def split_articles(pages: list[str]) -> list[Chunk]:
    """페이지를 순회하며 조항 제목 기준으로 구간을 나눈다. 조항이 페이지를 넘어가도 이어 붙인다."""
    sections: list[Chunk] = []
    current = Chunk(article=None, page=1, content="")

    for page_no, text in enumerate(pages, start=1):
        pos = 0
        for m in ARTICLE_RE.finditer(text):
            current.content += text[pos:m.start()]
            if current.content.strip():
                sections.append(current)
            current = Chunk(article=re.sub(r"\s+", "", m.group(1)), page=page_no, content="")
            pos = m.start()
        current.content += text[pos:] + "\n"

    if current.content.strip():
        sections.append(current)
    return sections


def window(section: Chunk, size: int, overlap: int) -> list[Chunk]:
    """긴 조항은 겹치는 구간을 두고 다시 자른다."""
    text = section.content.strip()
    if len(text) <= size:
        return [Chunk(section.article, section.page, text)]
    step = size - overlap
    return [Chunk(section.article, section.page, text[i:i + size]) for i in range(0, len(text), step)]


def chunk_document(path: Path) -> tuple[list[Chunk], int]:
    pages = read_pages(path)
    chunks = [
        c
        for section in split_articles(pages)
        for c in window(section, settings.chunk_size, settings.chunk_overlap)
    ]
    return chunks, len(pages)


def ingest_file(path: Path) -> int:
    chunks, num_pages = chunk_document(path)
    if not chunks:
        return 0

    # 조항 제목을 본문 앞에 붙여 임베딩하면 검색 정확도가 올라간다
    vectors = embed_passages([f"{c.article or ''}\n{c.content}" for c in chunks])

    with pool.connection() as conn, conn.transaction():
        # 같은 파일을 다시 넣으면 기존 데이터를 교체 (멱등)
        conn.execute("DELETE FROM documents WHERE file_name = %s", (path.name,))
        doc_id = conn.execute(
            "INSERT INTO documents (file_name, product, num_pages) VALUES (%s, %s, %s) RETURNING id",
            (path.name, path.stem, num_pages),
        ).fetchone()["id"]
        with conn.cursor() as cur:
            cur.executemany(
                "INSERT INTO chunks (document_id, chunk_index, article, page, content, embedding) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                [(doc_id, i, c.article, c.page, c.content, v) for i, (c, v) in enumerate(zip(chunks, vectors))],
            )
    return len(chunks)


def ingest_all() -> dict[str, int]:
    files = sorted(
        p for p in Path(settings.policy_dir).iterdir() if p.suffix.lower() in {".pdf", ".txt"}
    )
    return {p.name: ingest_file(p) for p in files}


if __name__ == "__main__":
    pool.open()
    for name, count in ingest_all().items():
        print(f"{name}: {count} chunks")

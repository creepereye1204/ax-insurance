CREATE EXTENSION IF NOT EXISTS vector;

-- 기간계(Spring) 전용 스키마. 테이블은 JPA 가 생성한다.
CREATE SCHEMA IF NOT EXISTS core;

-- 약관 문서 (파일 1개 = 상품 1개)
CREATE TABLE documents (
    id          SERIAL PRIMARY KEY,
    file_name   TEXT UNIQUE NOT NULL,
    product     TEXT NOT NULL,
    num_pages   INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 조항 단위 청크
CREATE TABLE chunks (
    id           BIGSERIAL PRIMARY KEY,
    document_id  INT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index  INT NOT NULL,
    article      TEXT,              -- 예: "제3조(보험금의 지급사유)"
    page         INT NOT NULL,
    content      TEXT NOT NULL,
    embedding    vector(384) NOT NULL
);

CREATE INDEX chunks_embedding_idx ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX chunks_document_idx ON chunks (document_id);

-- 에이전트 실행 로그 (관측성 / 이후 Eval 분석용)
CREATE TABLE agent_runs (
    id             BIGSERIAL PRIMARY KEY,
    question       TEXT NOT NULL,
    answer         TEXT,
    provider       TEXT,             -- ollama | anthropic (모델 비교 Eval 용)
    model          TEXT,
    trace          JSONB NOT NULL,   -- 도구 호출 기록
    stop_reason    TEXT,
    input_tokens   INT,
    output_tokens  INT,
    latency_ms     INT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

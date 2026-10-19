import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    database_url: str = os.environ.get("DATABASE_URL", "postgresql://ax:ax@localhost:55440/ax")
    policy_dir: str = os.environ.get("POLICY_DIR", "./data/policies")

    llm_provider: str = os.environ.get("LLM_PROVIDER", "ollama")
    ollama_host: str = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
    ollama_model: str = os.environ.get("OLLAMA_MODEL", "gemma4:12b-it-qat")

    claude_model: str = os.environ.get("CLAUDE_MODEL", "claude-opus-5-5")
    claude_effort: str = os.environ.get("CLAUDE_EFFORT", "medium")
    max_agent_turns: int = int(os.environ.get("MAX_AGENT_TURNS", "8"))

    embedding_model: str = os.environ.get("EMBEDDING_MODEL", "intfloat/multilingual-e5-small")
    embedding_dim: int = int(os.environ.get("EMBEDDING_DIM", "384"))

    chunk_size: int = int(os.environ.get("CHUNK_SIZE", "800"))
    chunk_overlap: int = int(os.environ.get("CHUNK_OVERLAP", "100"))


settings = Settings()

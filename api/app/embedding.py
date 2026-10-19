"""임베딩 모델 래퍼.

multilingual-e5 계열은 입력 앞에 "query: " / "passage: " 접두어를 붙여야 성능이 제대로 나온다.
"""
from functools import lru_cache

import numpy as np
from sentence_transformers import SentenceTransformer

from .config import settings


@lru_cache(maxsize=1)
def _model() -> SentenceTransformer:
    return SentenceTransformer(settings.embedding_model, device="cpu")


def embed_passages(texts: list[str]) -> np.ndarray:
    return _model().encode(
        [f"passage: {t}" for t in texts],
        normalize_embeddings=True,
        batch_size=32,
        show_progress_bar=False,
    )


def embed_query(text: str) -> np.ndarray:
    return _model().encode(f"query: {text}", normalize_embeddings=True)

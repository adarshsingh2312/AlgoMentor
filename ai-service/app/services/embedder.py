from functools import lru_cache
from typing import Protocol, runtime_checkable
from fastembed import TextEmbedding

from app.config.settings import get_settings


@runtime_checkable
class EmbedderProtocol(Protocol):
    """
    Protocol defining the embedder interface.
    Allows easy mocking in tests without downloading models.
    """

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """Batch embeds multiple document chunks into vector floats."""
        ...

    def embed_query(self, text: str) -> list[float]:
        """Embeds a single search query into a vector float list."""
        ...


class FastEmbedder:
    """
    FastEmbed implementation using local ONNX runtime.
    Default model: BAAI/bge-small-en-v1.5 (384 dimensions).
    """

    def __init__(self, model_name: str = "BAAI/bge-small-en-v1.5"):
        self.model_name = model_name
        self._model = TextEmbedding(model_name=model_name)

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        embeddings_iter = self._model.embed(texts)
        return [e.tolist() for e in embeddings_iter]

    def embed_query(self, text: str) -> list[float]:
        embeddings_iter = self._model.embed([text])
        return next(iter(embeddings_iter)).tolist()


@lru_cache
def get_embedder() -> EmbedderProtocol:
    settings = get_settings()
    return FastEmbedder(model_name=settings.embedding_model)


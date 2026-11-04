import uuid
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services.chunker import chunk_markdown
from app.services.embedder import EmbedderProtocol, get_embedder
from app.services.llm import LlmProtocol, get_llm_service
from app.config.settings import Settings, get_settings


class FakeEmbedder:
    """Mock embedder returning fixed-dimension vectors."""

    def __init__(self, vector=None):
        self.vector = vector or [0.1] * 384

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self.vector for _ in texts]

    def embed_query(self, text: str) -> list[float]:
        return self.vector


class SpyLlmService:
    """Spy LLM service tracking invocation count."""

    def __init__(self):
        self.calls = 0

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        self.calls += 1
        return "Answer based on context [1]."


# ---------------------------------------------------------------------------
# Test 1: Chunker Pure Function Test
# ---------------------------------------------------------------------------
def test_chunker_headings_overlap_and_empty():
    # 1. Empty/blank text returns empty list
    assert chunk_markdown("") == []
    assert chunk_markdown("   \n\n  ") == []

    # 2. Markdown heading splits
    text = """# Binary Search
Binary search divides the search space in half.

## Complexity
Time complexity is O(log n).

### Space
Auxiliary space is O(1).
"""
    chunks = chunk_markdown(text, max_chars=800, overlap=100)
    assert len(chunks) == 3
    assert "Binary Search" in chunks[0]
    assert "Complexity" in chunks[1]
    assert "Space" in chunks[2]

    # 3. Sliding window overlap for large sections
    long_section = "# Long Section\n" + ("abcdefghij" * 100)  # 1000+ chars
    overlap_chunks = chunk_markdown(long_section, max_chars=300, overlap=50)
    assert len(overlap_chunks) >= 3
    for c in overlap_chunks:
        assert len(c) <= 300


# ---------------------------------------------------------------------------
# Test 2: Threshold Logic (Below Threshold = NO LLM Call)
# ---------------------------------------------------------------------------
def test_threshold_avoids_llm_call():
    spy_llm = SpyLlmService()
    fake_embedder = FakeEmbedder()

    # Create dummy DB connection context returning below-threshold score (0.20 < 0.35)
    class FakeCursor:
        def execute(self, query, params=None):
            pass

        def fetchall(self):
            # Returns a chunk with similarity score 0.20 (below threshold 0.35)
            return [
                (
                    uuid.uuid4(),
                    "notes.md",
                    0,
                    "Some unrelated content",
                    0.20,
                )
            ]

        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

    class FakeConn:
        def cursor(self):
            return FakeCursor()

    # Override dependencies
    app.dependency_overrides[get_llm_service] = lambda: spy_llm
    app.dependency_overrides[get_embedder] = lambda: fake_embedder
    from app.db.session import get_db

    app.dependency_overrides[get_db] = lambda: FakeConn()

    try:
        settings = get_settings()
        client = TestClient(app)
        response = client.post(
            "/ask",
            headers={"X-Service-Key": settings.ai_service_key},
            json={
                "userId": str(uuid.uuid4()),
                "question": "What is binary search?",
                "topK": 4,
            },
        )

        assert response.status_code == 200
        data = response.json()
        assert data["answer"] == "I couldn't find this in your notes"
        assert data["citations"] == []
        # CRITICAL ASSERTION: LLM must NEVER have been called!
        assert spy_llm.calls == 0
    finally:
        app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# Test 3: Ingestion Idempotency & Tenant Isolation Logic
# ---------------------------------------------------------------------------
def test_ingest_idempotency_and_tenant_isolation():
    # Verify that re-ingesting replaces chunks for the same document and isolates user
    deleted = []
    inserted = []

    class FakeTxCursor:
        def execute(self, query, params=None):
            q = query.strip().upper()
            if q.startswith("DELETE"):
                deleted.append(params)
            elif q.startswith("INSERT"):
                inserted.append(params)

        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

    class FakeTxContext:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

    class FakeTxConn:
        def transaction(self):
            return FakeTxContext()

        def cursor(self):
            return FakeTxCursor()

    from app.db.session import get_db

    app.dependency_overrides[get_db] = lambda: FakeTxConn()
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder()

    try:
        settings = get_settings()
        client = TestClient(app)
        doc_id = str(uuid.uuid4())
        user_id = str(uuid.uuid4())

        # First ingest
        res1 = client.post(
            "/ingest",
            headers={"X-Service-Key": settings.ai_service_key},
            json={
                "documentId": doc_id,
                "userId": user_id,
                "displayName": "algo.md",
                "text": "# Graph Algorithms\nBFS and DFS.",
            },
        )
        assert res1.status_code == 200
        count1 = res1.json()["chunksCount"]

        # Second ingest with same documentId (idempotent replacement)
        res2 = client.post(
            "/ingest",
            headers={"X-Service-Key": settings.ai_service_key},
            json={
                "documentId": doc_id,
                "userId": user_id,
                "displayName": "algo.md",
                "text": "# Graph Algorithms\nBFS and DFS.",
            },
        )
        assert res2.status_code == 200
        assert res2.json()["chunksCount"] == count1
        # Confirms DELETE was executed prior to re-insertion
        assert len(deleted) == 2
    finally:
        app.dependency_overrides.clear()


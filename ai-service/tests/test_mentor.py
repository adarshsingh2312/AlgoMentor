import uuid
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.db.session import get_db
from app.services.embedder import get_embedder
from app.services.llm import get_llm_service
from app.models.schemas import HintOut


class FakeEmbedder:
    def __init__(self, vector=None):
        self.vector = vector or [0.1] * 384

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self.vector for _ in texts]

    def embed_query(self, text: str) -> list[float]:
        return self.vector


class SpyLlmService:
    def __init__(self, outputs: list[HintOut] | None = None):
        self.calls = 0
        self.outputs = outputs or []

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        self.calls += 1
        return "Fake LLM answer."

    def generate_structured(self, prompt: str, schema: type, system_instruction: str | None = None):
        self.calls += 1
        if self.outputs and len(self.outputs) >= self.calls:
            return self.outputs[self.calls - 1]
        return HintOut(
            level=1,
            hint="What subproblem emerges when inspecting the input boundaries?",
            follow_up_question="Can you verify this with a small test case?",
            uses_notes=False,
        )


class InMemoryProgressDb:
    """Mock connection providing an in-memory dictionary for hint_progress table."""

    def __init__(self):
        self.progress = {}  # (user_id, problem_key) -> level

    def cursor(self):
        return InMemoryCursor(self)

    def commit(self):
        pass


class InMemoryCursor:
    def __init__(self, db: InMemoryProgressDb):
        self.db = db
        self.last_result = None

    def execute(self, query: str, params: tuple = ()):
        q = " ".join(query.split()).lower()
        if "select level from hint_progress" in q:
            user_id, problem_key = params
            level = self.db.progress.get((str(user_id), str(problem_key)))
            self.last_result = (level,) if level is not None else None
        elif "insert into hint_progress" in q:
            user_id, problem_key, level = params[:3]
            self.db.progress[(str(user_id), str(problem_key))] = int(level)
            self.last_result = None
        elif "update hint_progress" in q:
            level, user_id, problem_key = params[:3]
            self.db.progress[(str(user_id), str(problem_key))] = int(level)
            self.last_result = None
        elif "delete from hint_progress" in q:
            user_id, problem_key = params
            self.db.progress.pop((str(user_id), str(problem_key)), None)
            self.last_result = None
        elif "select document_id" in q:
            # Empty note chunks for pure progression tests
            self.last_result = []
        else:
            self.last_result = None

    def fetchone(self):
        return self.last_result

    def fetchall(self):
        return self.last_result or []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass


# ---------------------------------------------------------------------------
# Test 1: Level Progression 1 -> 2 -> 3 -> Exhausted (4th call makes NO LLM call)
# ---------------------------------------------------------------------------
def test_hint_progression_and_exhaustion():
    db = InMemoryProgressDb()
    spy_llm = SpyLlmService(
        outputs=[
            HintOut(level=1, hint="How can you decompose the problem?", follow_up_question="What is the subproblem?", uses_notes=False),
            HintOut(level=2, hint="Apply Two Pointers to maintain the window invariant.", follow_up_question="What property stays constant?", uses_notes=False),
            HintOut(level=3, hint="Process the array sequentially from both ends. Shrink the window when the sum exceeds target.", follow_up_question="Ready to code?", uses_notes=False),
        ]
    )

    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder()
    app.dependency_overrides[get_llm_service] = lambda: spy_llm

    try:
        client = TestClient(app)
        user_id = str(uuid.uuid4())
        problem_id = str(uuid.uuid4())
        payload = {
            "userId": user_id,
            "problem": {
                "id": problem_id,
                "name": "Subarray Sum",
                "rating": 1400,
                "tags": ["two pointers"],
                "url": "https://codeforces.com/problem/1/A",
            },
        }
        headers = {"X-Service-Key": "7/txs2596+jo8teCV9uTcSDwGYNFXFUU1xxAG2ADeH0="}

        # Call 1 -> Level 1
        r1 = client.post("/mentor/hint", json=payload, headers=headers)
        assert r1.status_code == 200
        assert r1.json()["level"] == 1
        assert r1.json()["exhausted"] is False
        assert spy_llm.calls == 1

        # Call 2 -> Level 2
        r2 = client.post("/mentor/hint", json=payload, headers=headers)
        assert r2.status_code == 200
        assert r2.json()["level"] == 2
        assert r2.json()["exhausted"] is False
        assert spy_llm.calls == 2

        # Call 3 -> Level 3
        r3 = client.post("/mentor/hint", json=payload, headers=headers)
        assert r3.status_code == 200
        assert r3.json()["level"] == 3
        assert r3.json()["exhausted"] is False
        assert spy_llm.calls == 3

        # Call 4 -> Exhausted (MUST make NO LLM call!)
        r4 = client.post("/mentor/hint", json=payload, headers=headers)
        assert r4.status_code == 200
        assert r4.json()["level"] == 3
        assert r4.json()["exhausted"] is True
        assert spy_llm.calls == 3, "The 4th call must make ZERO LLM calls!"
    finally:
        app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# Test 2: Code Fence Replaced by Safe Prose Fallback
# ---------------------------------------------------------------------------
def test_code_fence_triggers_fallback():
    db = InMemoryProgressDb()
    # LLM outputs forbidden code blocks repeatedly
    poisoned_llm = SpyLlmService(
        outputs=[
            HintOut(level=1, hint="```python\ndef solve(): pass\n```", follow_up_question="Is this clear?", uses_notes=False),
            HintOut(level=1, hint="```python\ndef retry(): pass\n```", follow_up_question="Try now?", uses_notes=False),
        ]
    )

    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder()
    app.dependency_overrides[get_llm_service] = lambda: poisoned_llm

    try:
        client = TestClient(app)
        payload = {
            "userId": str(uuid.uuid4()),
            "problem": {
                "id": str(uuid.uuid4()),
                "name": "Binary Search Problem",
                "rating": 1200,
                "tags": ["searching"],
                "url": "https://codeforces.com/problem/1/B",
            },
        }
        headers = {"X-Service-Key": "7/txs2596+jo8teCV9uTcSDwGYNFXFUU1xxAG2ADeH0="}

        res = client.post("/mentor/hint", json=payload, headers=headers)
        assert res.status_code == 200
        hint_text = res.json()["hint"]

        # Code fences MUST be eliminated and replaced by fallback
        assert "```" not in hint_text
        assert "def solve" not in hint_text
        assert len(hint_text) > 0
    finally:
        app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# Test 3: Hint Progress Isolated Per User
# ---------------------------------------------------------------------------
def test_hint_progress_user_isolation():
    db = InMemoryProgressDb()
    spy_llm = SpyLlmService()

    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder()
    app.dependency_overrides[get_llm_service] = lambda: spy_llm

    try:
        client = TestClient(app)
        user_a = str(uuid.uuid4())
        user_b = str(uuid.uuid4())
        problem_id = str(uuid.uuid4())

        headers = {"X-Service-Key": "7/txs2596+jo8teCV9uTcSDwGYNFXFUU1xxAG2ADeH0="}

        # User A advances problem to Level 1
        payload_a = {
            "userId": user_a,
            "problem": {"id": problem_id, "name": "Shared Problem", "rating": 1000, "tags": [], "url": ""},
        }
        r_a1 = client.post("/mentor/hint", json=payload_a, headers=headers)
        assert r_a1.json()["level"] == 1

        # User A advances problem to Level 2
        r_a2 = client.post("/mentor/hint", json=payload_a, headers=headers)
        assert r_a2.json()["level"] == 2

        # User B requests hint for the SAME problem -> MUST start at Level 1 (not Level 3!)
        payload_b = {
            "userId": user_b,
            "problem": {"id": problem_id, "name": "Shared Problem", "rating": 1000, "tags": [], "url": ""},
        }
        r_b1 = client.post("/mentor/hint", json=payload_b, headers=headers)
        assert r_b1.json()["level"] == 1, "User B's progress must be isolated from User A!"
    finally:
        app.dependency_overrides.clear()


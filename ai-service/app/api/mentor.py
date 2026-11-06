from uuid import UUID
import psycopg
from fastapi import APIRouter, Depends, Query, status

from app.api.deps import verify_service_key
from app.config.settings import Settings, get_settings
from app.db.session import get_db
from app.models.schemas import (
    Citation,
    MentorHintRequest,
    MentorHintResponse,
    MentorResetResponse,
)
from app.services.embedder import EmbedderProtocol, get_embedder
from app.services.llm import LlmProtocol, get_llm_service
from app.services.mentor_graph import build_mentor_graph

router = APIRouter(
    prefix="/mentor",
    dependencies=[Depends(verify_service_key)],
    tags=["mentor"],
)


@router.post(
    "/hint",
    response_model=MentorHintResponse,
    status_code=status.HTTP_200_OK,
)
def get_or_advance_hint(
    request: MentorHintRequest,
    conn: psycopg.Connection = Depends(get_db),
    embedder: EmbedderProtocol = Depends(get_embedder),
    llm_service: LlmProtocol = Depends(get_llm_service),
    settings: Settings = Depends(get_settings),
) -> MentorHintResponse:
    """
    Socratic Hint Ladder Endpoint:
    - Step 1: Increments server-side hint level (1 -> 2 -> 3 -> exhausted).
    - Step 2: Retrieves student's personal notes relevant to the problem.
    - Step 3: Generates level-specific hint via structured LLM output.
    - Step 4: Deterministic safety guard ensures zero code output.
    """
    problem_dict = request.problem.model_dump()
    problem_key = str(request.problem.id)

    graph = build_mentor_graph(
        conn=conn,
        embedder=embedder,
        llm_service=llm_service,
        rag_similarity_threshold=settings.rag_similarity_threshold,
    )

    initial_state = {
        "user_id": str(request.user_id),
        "problem": problem_dict,
        "problem_key": problem_key,
        "level": 1,
        "exhausted": False,
        "citations": [],
        "notes_context": "",
        "attempt": 0,
        "guard_passed": False,
        "guard_error": None,
        "hint_out": None,
        "final_hint": "",
        "final_follow_up": "",
    }

    final_state = graph.invoke(initial_state)

    citations = [
        Citation(
            document_id=UUID(c["documentId"]),
            display_name=c["displayName"],
            chunk_index=c["chunkIndex"],
            snippet=c["snippet"],
            score=c["score"],
        )
        for c in final_state.get("citations", [])
    ]

    return MentorHintResponse(
        level=final_state.get("level", 1),
        hint=final_state.get("final_hint", ""),
        follow_up=final_state.get("final_follow_up", ""),
        exhausted=final_state.get("exhausted", False),
        citations=citations,
    )


@router.delete(
    "/hint",
    response_model=MentorResetResponse,
    status_code=status.HTTP_200_OK,
)
def reset_hint_progress(
    user_id: UUID = Query(..., alias="userId"),
    problem_id: UUID = Query(..., alias="problemId"),
    conn: psycopg.Connection = Depends(get_db),
) -> MentorResetResponse:
    """
    Resets the student's hint progression back to Level 0 for this problem.
    """
    problem_key = str(problem_id)
    with conn.cursor() as cur:
        cur.execute(
            """
            DELETE FROM hint_progress
            WHERE user_id = %s AND problem_key = %s
            """,
            (str(user_id), problem_key),
        )
        conn.commit()

    return MentorResetResponse(
        status="RESET",
        user_id=user_id,
        problem_id=problem_id,
    )


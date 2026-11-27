"""
Topic Knowledge Enrichment (Option B — LLM Self-Knowledge)
===========================================================
When a user struggles on a problem tag (e.g. "binary search"), this endpoint:
  1. Checks whether a knowledge document for that tag already exists (global dedup).
  2. If not, asks the LLM to generate a comprehensive topic guide.
  3. Embeds and stores the guide in note_chunks under a fixed SYSTEM user UUID
     so ALL users benefit from a single generation.

The ask.py RAG query is extended to also pull SYSTEM-level chunks, giving the
LLM rich educational context even when the user hasn't written notes on the topic.
"""

from uuid import UUID, uuid4
import psycopg
from fastapi import APIRouter, Depends, status
from loguru import logger

from app.api.deps import verify_service_key
from app.config.settings import Settings, get_settings
from app.db.session import get_db
from app.models.schemas import EnrichTagRequest, EnrichTagResponse
from app.services.chunker import chunk_markdown
from app.services.embedder import EmbedderProtocol, get_embedder
from app.services.llm import LlmProtocol, get_llm_service

router = APIRouter(dependencies=[Depends(verify_service_key)], tags=["knowledge"])

# Fixed UUID representing system-level (global) knowledge — not tied to any real user.
# Must be stable across restarts so dedup works. Never changes.
SYSTEM_USER_ID = UUID("00000000-0000-0000-0000-000000000001")

_TOPIC_GUIDE_SYSTEM_PROMPT = """\
You are an expert competitive programming and computer science educator.
Write thorough, well-structured educational content in Markdown.
Be precise, include concrete examples with code (C++ preferred), and cover
time/space complexity explicitly.
"""

_TOPIC_GUIDE_PROMPT_TEMPLATE = """\
Write a comprehensive educational guide about the topic: **{tag}**

Structure your guide with the following sections (use ## for each):

## What is {tag}?
Brief, clear definition and intuition.

## Core Algorithm / Approach
Step-by-step breakdown. If it is an algorithm, show pseudocode or describe the steps precisely.

## Time & Space Complexity
Clearly state the complexity and explain *why*.

## Canonical Code Example
A clean, well-commented C++ (or Python) implementation demonstrating the core concept.

## Common Problem Patterns
List 3-5 typical problem patterns where this topic appears on competitive programming platforms.

## Common Mistakes & Edge Cases
Things beginners get wrong. Edge cases to watch out for.

## Related Topics
Brief mention of related or prerequisite topics.

Write in depth. This will be used as a reference document for a competitive programming student.
"""


def _tag_already_enriched(conn: psycopg.Connection, tag_key: str) -> bool:
    """Return True if a knowledge doc for this tag already exists in the DB."""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT 1 FROM note_chunks
            WHERE user_id = %s AND display_name = %s
            LIMIT 1
            """,
            (SYSTEM_USER_ID, _display_name(tag_key)),
        )
        return cur.fetchone() is not None


def _display_name(tag: str) -> str:
    return f"[Knowledge] {tag.strip().title()}"


@router.post(
    "/knowledge/enrich-tag",
    response_model=EnrichTagResponse,
    status_code=status.HTTP_200_OK,
)
def enrich_tag(
    request: EnrichTagRequest,
    conn: psycopg.Connection = Depends(get_db),
    embedder: EmbedderProtocol = Depends(get_embedder),
    llm_service: LlmProtocol = Depends(get_llm_service),
    settings: Settings = Depends(get_settings),
) -> EnrichTagResponse:
    tag = request.tag.strip().lower()
    display = _display_name(tag)
    doc_id = uuid4()

    # 1. Global dedup — one knowledge doc per tag, shared across all users.
    # Rollback/close the implicit read transaction before LLM call so the later
    # `with conn.transaction():` is a top-level transaction that actually commits!
    already_exists = _tag_already_enriched(conn, tag)
    conn.rollback()
    if already_exists:
        logger.info(f"[Knowledge] Tag '{tag}' already enriched — skipping generation")
        return EnrichTagResponse(tag=tag, status="ALREADY_EXISTS", chunks_count=0)

    # 2. Generate comprehensive topic guide via LLM
    logger.info(f"[Knowledge] Generating topic guide for tag: '{tag}'")
    prompt = _TOPIC_GUIDE_PROMPT_TEMPLATE.format(tag=tag)
    try:
        guide_text = llm_service.generate(
            prompt=prompt,
            system_instruction=_TOPIC_GUIDE_SYSTEM_PROMPT,
        )
    except Exception as e:
        logger.error(f"[Knowledge] LLM generation failed for tag '{tag}': {e}")
        raise

    if not guide_text or not guide_text.strip():
        logger.warning(f"[Knowledge] LLM returned empty guide for tag '{tag}'")
        return EnrichTagResponse(tag=tag, status="EMPTY_RESPONSE", chunks_count=0)

    # 3. Chunk and embed
    chunks = chunk_markdown(guide_text)
    if not chunks:
        return EnrichTagResponse(tag=tag, status="NO_CHUNKS", chunks_count=0)

    embeddings = embedder.embed_documents(chunks)

    # 4. Store under SYSTEM_USER_ID (global, deduped per tag)
    with conn.transaction():
        with conn.cursor() as cur:
            # Idempotent: delete any stale doc with same display_name before re-inserting
            cur.execute(
                "DELETE FROM note_chunks WHERE user_id = %s AND display_name = %s",
                (SYSTEM_USER_ID, display),
            )
            for idx, (chunk, embedding) in enumerate(zip(chunks, embeddings)):
                cur.execute(
                    """
                    INSERT INTO note_chunks (
                        id, document_id, user_id, display_name,
                        chunk_index, content, embedding, embedding_model, created_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, now())
                    """,
                    (
                        uuid4(),
                        doc_id,
                        SYSTEM_USER_ID,
                        display,
                        idx,
                        chunk,
                        embedding,
                        settings.embedding_model,
                    ),
                )

    logger.info(f"[Knowledge] Enriched tag '{tag}' → {len(chunks)} chunks stored (docId={doc_id})")
    return EnrichTagResponse(tag=tag, status="ENRICHED", chunks_count=len(chunks))

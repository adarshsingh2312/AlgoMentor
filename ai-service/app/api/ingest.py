from uuid import UUID, uuid4
import psycopg
from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.deps import verify_service_key
from app.config.settings import Settings, get_settings
from app.db.session import get_db
from app.models.schemas import DeleteResponse, IngestRequest, IngestResponse
from app.services.chunker import chunk_markdown
from app.services.embedder import EmbedderProtocol, get_embedder

router = APIRouter(dependencies=[Depends(verify_service_key)], tags=["ingest"])

MAX_TEXT_BYTES = 1024 * 1024  # 1 MB
MAX_CHUNKS = 2000


@router.post(
    "/ingest",
    response_model=IngestResponse,
    status_code=status.HTTP_200_OK,
)
def ingest_document(
    request: IngestRequest,
    conn: psycopg.Connection = Depends(get_db),
    embedder: EmbedderProtocol = Depends(get_embedder),
    settings: Settings = Depends(get_settings),
) -> IngestResponse:
    # 1. Size validation
    if len(request.text.encode("utf-8")) > MAX_TEXT_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Document text exceeds maximum permitted limit (1MB)",
        )

    # 2. Pure chunking by markdown headings and character window
    chunks = chunk_markdown(request.text)

    if len(chunks) > MAX_CHUNKS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Document exceeds maximum chunk limit ({MAX_CHUNKS})",
        )

    # 3. Generate embeddings for all chunks in batch
    embeddings = embedder.embed_documents(chunks) if chunks else []

    # 4. Atomic upsert in ONE transaction: delete old chunks, insert new ones
    with conn.transaction():
        with conn.cursor() as cur:
            # Delete any previous chunks for this document to ensure idempotency
            cur.execute(
                "DELETE FROM note_chunks WHERE document_id = %s AND user_id = %s",
                (request.document_id, request.user_id),
            )

            # Insert newly embedded chunks
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
                        request.document_id,
                        request.user_id,
                        request.display_name,
                        idx,
                        chunk,
                        embedding,
                        settings.embedding_model,
                    ),
                )

    return IngestResponse(
        document_id=request.document_id,
        chunks_count=len(chunks),
        status="INGESTED",
    )


@router.delete(
    "/documents/{document_id}",
    response_model=DeleteResponse,
    status_code=status.HTTP_200_OK,
)
def delete_document_chunks(
    document_id: UUID,
    user_id: UUID = Query(..., alias="userId"),
    conn: psycopg.Connection = Depends(get_db),
) -> DeleteResponse:
    """
    Deletes all vector chunks belonging to a document and user.
    """
    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute(
                "DELETE FROM note_chunks WHERE document_id = %s AND user_id = %s",
                (document_id, user_id),
            )
            deleted_count = cur.rowcount

    return DeleteResponse(
        document_id=document_id,
        deleted_count=deleted_count,
    )


from contextlib import contextmanager
from typing import Generator
import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool
from pgvector.psycopg import register_vector
from loguru import logger

from app.config.settings import Settings, get_settings

_pool: ConnectionPool | None = None


def get_conninfo(settings: Settings) -> str:
    return (
        f"host={settings.db_host} "
        f"port={settings.db_port} "
        f"dbname={settings.db_name} "
        f"user={settings.db_user} "
        f"password={settings.db_password}"
    )


def configure_connection(conn: psycopg.Connection) -> None:
    # Register pgvector type handlers with psycopg 3 connection
    register_vector(conn)


def init_db_pool(settings: Settings | None = None) -> ConnectionPool:
    global _pool
    if _pool is None:
        if settings is None:
            settings = get_settings()
        _pool = ConnectionPool(
            conninfo=get_conninfo(settings),
            min_size=1,
            max_size=10,
            open=True,
            configure=configure_connection,
            check=ConnectionPool.check_connection,
        )
        logger.info(f"Database pool initialized connecting to {settings.db_host}:{settings.db_port}/{settings.db_name}")
    return _pool


def close_db_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None
        logger.info("Database pool closed")


@contextmanager
def get_db_connection() -> Generator[psycopg.Connection, None, None]:
    """Context manager yielding a pooled connection."""
    global _pool
    if _pool is None:
        init_db_pool()
    with _pool.connection() as conn:
        yield conn


def get_db() -> Generator[psycopg.Connection, None, None]:
    """FastAPI Depends provider yielding a pooled database connection."""
    with get_db_connection() as conn:
        yield conn


def init_db_schema() -> None:
    """
    Idempotent database schema initialization:
    - Creates pgvector extension
    - Creates note_chunks table
    - Creates composite B-Tree index on (user_id, document_id)
    - Creates HNSW cosine similarity vector index on embedding column
    """
    settings = get_settings()
    ddl = f"""
    CREATE EXTENSION IF NOT EXISTS vector;

    CREATE TABLE IF NOT EXISTS note_chunks (
        id UUID PRIMARY KEY,
        document_id UUID NOT NULL,
        user_id UUID NOT NULL,
        display_name TEXT NOT NULL,
        chunk_index INT NOT NULL,
        content TEXT NOT NULL,
        embedding vector({settings.embedding_dimensions}),
        embedding_model TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE INDEX IF NOT EXISTS idx_note_chunks_user_doc 
        ON note_chunks (user_id, document_id);

    CREATE INDEX IF NOT EXISTS idx_note_chunks_embedding_hnsw 
        ON note_chunks USING hnsw (embedding vector_cosine_ops);
    """
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(ddl)
            conn.commit()
        logger.info("Database schema initialized (pgvector extension, note_chunks table, and HNSW index)")
    except Exception as e:
        logger.warning(f"Database initialization deferred (PostgreSQL may not be running yet): {e}")


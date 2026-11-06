from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api import health, ingest, ask, mentor
from app.config.settings import get_settings
from app.db.session import init_db_schema, close_db_pool


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Validates required settings (e.g. AI_SERVICE_KEY length >= 32) on startup
    get_settings()
    # Idempotent DB schema initialization (extension, note_chunks, HNSW index)
    init_db_schema()
    yield
    # Graceful shutdown of connection pool
    close_db_pool()


app = FastAPI(
    title="AlgoMentor AI Service",
    description="FastAPI service for RAG ingestion, embedding, and question-answering",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(health.router)
app.include_router(ingest.router)
app.include_router(ask.router)
app.include_router(mentor.router)


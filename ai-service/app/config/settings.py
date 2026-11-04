from functools import lru_cache
from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=("../.env", ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Database configuration (matches Spring Boot properties)
    db_host: str = Field(default="localhost", validation_alias="DB_HOST")
    db_port: int = Field(default=5432, validation_alias="DB_PORT")
    db_name: str = Field(default="algomentor", validation_alias="DB_NAME")
    db_user: str = Field(default="postgres", validation_alias="DB_USER")
    db_password: str = Field(default="postgres", validation_alias="DB_PASSWORD")

    # Service-to-service auth key (NO default, min 32 chars, refuses startup if invalid)
    ai_service_key: str = Field(..., min_length=32, validation_alias="AI_SERVICE_KEY")

    # LLM & RAG configuration
    llm_provider: str = Field(default="gemini", validation_alias="LLM_PROVIDER")
    llm_model: str = Field(default="gemini-3.5-flash", validation_alias="LLM_MODEL")
    gemini_api_key: str | None = Field(default=None, validation_alias="GEMINI_API_KEY")

    # FastEmbed model
    embedding_model: str = Field(
        default="BAAI/bge-small-en-v1.5",
        validation_alias="EMBEDDING_MODEL",
    )
    embedding_dimensions: int = 384

    # Similarity cutoff threshold for retrieval
    rag_similarity_threshold: float = Field(
        default=0.35,
        validation_alias="RAG_SIMILARITY_THRESHOLD",
    )

    @field_validator("ai_service_key")
    @classmethod
    def validate_service_key(cls, v: str) -> str:
        trimmed = v.strip()
        if len(trimmed) < 32:
            raise ValueError("AI_SERVICE_KEY must be at least 32 characters long")
        return trimmed


@lru_cache
def get_settings() -> Settings:
    return Settings()


from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class BaseSchema(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
    )


class IngestRequest(BaseSchema):
    document_id: UUID = Field(..., alias="documentId")
    user_id: UUID = Field(..., alias="userId")
    display_name: str = Field(..., alias="displayName")
    text: str


class IngestResponse(BaseSchema):
    document_id: UUID = Field(..., alias="documentId")
    chunks_count: int = Field(..., alias="chunksCount")
    status: str = "INGESTED"


class DeleteResponse(BaseSchema):
    document_id: UUID = Field(..., alias="documentId")
    deleted_count: int = Field(..., alias="deletedCount")


class Citation(BaseSchema):
    document_id: UUID = Field(..., alias="documentId")
    display_name: str = Field(..., alias="displayName")
    chunk_index: int = Field(..., alias="chunkIndex")
    snippet: str
    score: float


class AskRequest(BaseSchema):
    user_id: UUID = Field(..., alias="userId")
    question: str = Field(..., min_length=1, max_length=1000)
    top_k: int = Field(default=4, ge=1, le=8, alias="topK")


class AskResponse(BaseSchema):
    answer: str
    citations: list[Citation] = Field(default_factory=list)


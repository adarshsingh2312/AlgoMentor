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


class ProblemInfo(BaseSchema):
    id: UUID
    name: str = Field(..., max_length=200)
    rating: int | None = Field(default=None, ge=800, le=3500)
    tags: list[str] = Field(default_factory=list)
    url: str | None = Field(default=None, max_length=500)


class MentorHintRequest(BaseSchema):
    user_id: UUID = Field(..., alias="userId")
    problem: ProblemInfo


class MentorHintResponse(BaseSchema):
    level: int
    hint: str
    follow_up: str = Field(default="", alias="followUp")
    exhausted: bool = False
    citations: list[Citation] = Field(default_factory=list)


class MentorResetResponse(BaseSchema):
    status: str = "RESET"
    user_id: UUID = Field(..., alias="userId")
    problem_id: UUID = Field(..., alias="problemId")


class HintOut(BaseModel):
    level: int = Field(description="Current hint level (1, 2, or 3)")
    hint: str = Field(description="The hint text complying with the level constraints")
    follow_up_question: str = Field(description="A short encouraging follow-up question")
    uses_notes: bool = Field(description="Whether personal study notes informed this hint")



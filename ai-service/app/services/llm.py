import time
from functools import lru_cache
from typing import Any, Protocol, TypeVar, runtime_checkable
from google import genai
from google.genai import types
from pydantic import BaseModel

from app.config.settings import Settings, get_settings

T = TypeVar("T", bound=BaseModel)


@runtime_checkable
class LlmProtocol(Protocol):
    """
    Protocol for LLM generation.
    Allows substituting fake/mock LLMs during testing.
    """

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        """Generates response text from prompt with optional system instructions."""
        ...

    def generate_structured(
        self, prompt: str, schema: type[T], system_instruction: str | None = None
    ) -> T:
        """Generates structured response parsed into given Pydantic schema."""
        ...


class GeminiLlmService:
    """
    Google Gemini implementation using the official google-genai SDK.
    """

    def __init__(self, api_key: str | None, model: str = "gemini-3.5-flash"):
        if not api_key:
            raise ValueError("GEMINI_API_KEY is required to initialize GeminiLlmService")
        self.model = model
        self.client = genai.Client(api_key=api_key)

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        config = types.GenerateContentConfig(
            temperature=0.2,  # Low temperature for factual RAG answers
            system_instruction=system_instruction,
        )
        for attempt in range(3):
            try:
                response = self.client.models.generate_content(
                    model=self.model,
                    contents=prompt,
                    config=config,
                )
                return response.text or ""
            except Exception as e:
                if attempt == 2:
                    raise
                time.sleep(1.0 * (attempt + 1))
        return ""

    def generate_structured(
        self, prompt: str, schema: type[T], system_instruction: str | None = None
    ) -> T:
        config = types.GenerateContentConfig(
            temperature=0.2,
            system_instruction=system_instruction,
            response_mime_type="application/json",
            response_schema=schema,
        )
        for attempt in range(3):
            try:
                response = self.client.models.generate_content(
                    model=self.model,
                    contents=prompt,
                    config=config,
                )
                return schema.model_validate_json(response.text)
            except Exception as e:
                if attempt == 2:
                    raise
                time.sleep(1.0 * (attempt + 1))
        raise RuntimeError("Failed to generate structured response")



@lru_cache
def get_llm_service() -> LlmProtocol:
    settings = get_settings()
    return GeminiLlmService(
        api_key=settings.gemini_api_key,
        model=settings.llm_model,
    )


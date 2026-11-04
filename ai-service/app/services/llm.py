from functools import lru_cache
from typing import Protocol, runtime_checkable
from google import genai
from google.genai import types

from app.config.settings import Settings, get_settings


@runtime_checkable
class LlmProtocol(Protocol):
    """
    Protocol for LLM generation.
    Allows substituting fake/mock LLMs during testing.
    """

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        """Generates response text from prompt with optional system instructions."""
        ...


class GeminiLlmService:
    """
    Google Gemini implementation using the official google-genai SDK.
    """

    def __init__(self, api_key: str | None, model: str = "gemini-1.5-flash"):
        if not api_key:
            raise ValueError("GEMINI_API_KEY is required to initialize GeminiLlmService")
        self.model = model
        self.client = genai.Client(api_key=api_key)

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        config = types.GenerateContentConfig(
            temperature=0.2,  # Low temperature for factual RAG answers
            system_instruction=system_instruction,
        )
        response = self.client.models.generate_content(
            model=self.model,
            contents=prompt,
            config=config,
        )
        return response.text or ""


@lru_cache
def get_llm_service() -> LlmProtocol:
    settings = get_settings()
    return GeminiLlmService(
        api_key=settings.gemini_api_key,
        model=settings.llm_model,
    )


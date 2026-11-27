import json
import re
import time
from functools import lru_cache
from typing import Any, Protocol, TypeVar, runtime_checkable
import httpx
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



class OpenAiLlmService:
    """
    OpenAI-compatible Chat Completions implementation using httpx.
    Supports OpenAI, airouter.in, OpenRouter, etc. via configurable base_url.
    """

    def __init__(
        self,
        api_key: str | None,
        base_url: str = "https://api.openai.com/v1",
        model: str = "openai/gpt-5.2",
        timeout: float = 60.0,
    ):
        if not api_key:
            raise ValueError("OPENAI_API_KEY is required when LLM_PROVIDER=openai")
        self.api_key = api_key.strip()
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.client = httpx.Client(
            base_url=self.base_url,
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            timeout=timeout,
        )

    def _chat(self, messages: list[dict[str, str]], json_mode: bool = False) -> str:
        payload: dict[str, Any] = {
            "model": self.model,
            "temperature": 0.2,
            "messages": messages,
        }
        if json_mode:
            payload["response_format"] = {"type": "json_object"}

        for attempt in range(3):
            try:
                resp = self.client.post("/chat/completions", json=payload)
                resp.raise_for_status()
                data = resp.json()
                return data["choices"][0]["message"]["content"] or ""
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(1.0 * (attempt + 1))
        return ""

    def generate(self, prompt: str, system_instruction: str | None = None) -> str:
        messages: list[dict[str, str]] = []
        if system_instruction:
            messages.append({"role": "system", "content": system_instruction})
        messages.append({"role": "user", "content": prompt})
        return self._chat(messages, json_mode=False)

    def generate_structured(
        self, prompt: str, schema: type[T], system_instruction: str | None = None
    ) -> T:
        schema_json = json.dumps(schema.model_json_schema())
        json_instruction = (
            "Respond ONLY with a valid JSON object matching this JSON Schema "
            f"(no markdown fences, no extra text):\n{schema_json}"
        )
        full_system = (
            f"{system_instruction}\n\n{json_instruction}"
            if system_instruction
            else json_instruction
        )
        messages = [
            {"role": "system", "content": full_system},
            {"role": "user", "content": prompt},
        ]
        for attempt in range(3):
            try:
                raw = self._chat(messages, json_mode=True).strip()
                # Strip markdown code fences if a provider wraps JSON anyway
                raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.IGNORECASE | re.DOTALL).strip()
                return schema.model_validate_json(raw)
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(1.0 * (attempt + 1))
        raise RuntimeError("Failed to generate structured response")


@lru_cache
def get_llm_service() -> LlmProtocol:
    settings = get_settings()
    provider = settings.llm_provider.strip().lower()
    if provider == "openai":
        return OpenAiLlmService(
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url,
            model=settings.llm_model,
        )
    return GeminiLlmService(
        api_key=settings.gemini_api_key,
        model=settings.llm_model,
    )


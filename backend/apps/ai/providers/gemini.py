import json
import logging
import socket
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Optional

from django.conf import settings

from apps.ai.exceptions import (
    AiBadRequestError,
    AiInvalidResponseError,
    AiRefusalError,
    AiUnavailableError,
)
from apps.ai.providers.base import AIProvider

logger = logging.getLogger("promise")


class GeminiProvider(AIProvider):
    """Google Gemini AI Provider with controlled 3-key fallback chain."""

    BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    def __init__(
        self,
        api_keys: Optional[List[str]] = None,
        default_model: Optional[str] = None,
        timeout: Optional[int] = None,
        max_output_tokens: Optional[int] = None,
    ):
        if api_keys is not None:
            self._keys = [k for k in api_keys if k and k.strip()]
        else:
            import os
            configured = [
                os.environ.get("GEMINI_API_KEY_1") or getattr(settings, "GEMINI_API_KEY_1", ""),
                os.environ.get("GEMINI_API_KEY_2") or getattr(settings, "GEMINI_API_KEY_2", ""),
                os.environ.get("GEMINI_API_KEY_3") or getattr(settings, "GEMINI_API_KEY_3", ""),
            ]
            self._keys = [str(k).strip() for k in configured if k and str(k).strip()]

        self.default_model = (
            default_model
            or getattr(settings, "GEMINI_DEFAULT_MODEL", "gemini-3.6-flash")
        )
        self.timeout = timeout or getattr(settings, "GEMINI_TIMEOUT_SECONDS", 15)
        self.max_output_tokens = (
            max_output_tokens
            or getattr(settings, "GEMINI_MAX_OUTPUT_TOKENS", 1024)
        )

    def generate_structured(
        self,
        *,
        prompt: str,
        schema: Optional[Dict[str, Any]] = None,
        system_prompt: Optional[str] = None,
        model: Optional[str] = None,
    ) -> Dict[str, Any]:
        chosen_model = model or self.default_model
        raw_text = self._call_with_fallback(
            prompt=prompt,
            system_prompt=system_prompt,
            model=chosen_model,
            response_mime_type="application/json",
            response_schema=schema,
        )

        try:
            parsed = json.loads(raw_text)
            if not isinstance(parsed, dict) and not isinstance(parsed, list):
                raise ValueError("Expected JSON object or array")
            return parsed
        except (json.JSONDecodeError, ValueError) as exc:
            logger.warning(
                "Gemini returned invalid JSON",
                extra={"provider": "gemini", "model": chosen_model, "error_type": "json_parse_error"},
            )
            raise AiInvalidResponseError("AI provider returned malformed structured data.") from exc

    def generate_text(
        self,
        *,
        prompt: str,
        system_prompt: Optional[str] = None,
        model: Optional[str] = None,
    ) -> str:
        chosen_model = model or self.default_model
        return self._call_with_fallback(
            prompt=prompt,
            system_prompt=system_prompt,
            model=chosen_model,
            response_mime_type="text/plain",
        )

    def _call_with_fallback(
        self,
        *,
        prompt: str,
        system_prompt: Optional[str] = None,
        model: str,
        response_mime_type: str = "text/plain",
        response_schema: Optional[Dict[str, Any]] = None,
    ) -> str:
        if not self._keys:
            logger.warning(
                "Gemini AI called with no API keys configured",
                extra={"provider": "gemini", "model": model, "failure_category": "no_keys_configured"},
            )
            raise AiUnavailableError("AI provider is not configured.")

        last_exception = None

        for attempt, key in enumerate(self._keys, start=1):
            try:
                return self._invoke_api(
                    api_key=key,
                    prompt=prompt,
                    system_prompt=system_prompt,
                    model=model,
                    response_mime_type=response_mime_type,
                    response_schema=response_schema,
                )
            except (AiBadRequestError, AiRefusalError):
                # Non-retryable user/prompt error - fail immediately without burning keys
                raise
            except Exception as exc:
                category = self._classify_failure(exc)
                logger.warning(
                    f"Gemini API attempt {attempt}/{len(self._keys)} failed: {category}",
                    extra={
                        "provider": "gemini",
                        "model": model,
                        "attempt": attempt,
                        "failure_category": category,
                    },
                )
                last_exception = exc
                # Try next key in fallback chain

        # If all configured keys failed
        logger.error(
            f"All {len(self._keys)} Gemini API keys failed",
            extra={"provider": "gemini", "model": model, "failure_category": "all_keys_exhausted"},
        )
        raise AiUnavailableError("AI service is currently unavailable.") from last_exception

    def _invoke_api(
        self,
        *,
        api_key: str,
        prompt: str,
        system_prompt: Optional[str] = None,
        model: str,
        response_mime_type: str = "text/plain",
        response_schema: Optional[Dict[str, Any]] = None,
    ) -> str:
        url = f"{self.BASE_URL}/{model}:generateContent"

        contents = [
            {
                "role": "user",
                "parts": [{"text": prompt}],
            }
        ]

        generation_config: Dict[str, Any] = {
            "maxOutputTokens": self.max_output_tokens,
            "responseMimeType": response_mime_type,
        }
        if response_schema is not None:
            generation_config["responseSchema"] = response_schema

        payload: Dict[str, Any] = {
            "contents": contents,
            "generationConfig": generation_config,
        }

        if system_prompt:
            payload["systemInstruction"] = {
                "parts": [{"text": system_prompt}],
            }

        data_bytes = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url=url,
            data=data_bytes,
            headers={
                "Content-Type": "application/json",
                "x-goog-api-key": api_key,
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                status_code = resp.status
                body = resp.read().decode("utf-8")
        except urllib.error.HTTPError as http_err:
            status_code = http_err.code
            err_body = http_err.read().decode("utf-8", errors="replace")
            err_body_lower = err_body.lower()

            # Check if HTTP 400/401/403 is due to an invalid/revoked/unauthorized API Key
            if "api_key" in err_body_lower or "api key" in err_body_lower or "unauthenticated" in err_body_lower or status_code in (401, 403):
                raise AiUnavailableError(f"Gemini API key invalid or unauthorized: {status_code}") from http_err
            elif status_code in (400, 422):
                raise AiBadRequestError(f"AI request validation failed: {status_code}") from http_err
            elif status_code == 429:
                raise AiUnavailableError("AI rate limit / quota exceeded") from http_err
            elif status_code in (500, 502, 503, 504):
                raise AiUnavailableError(f"AI provider server error: {status_code}") from http_err
            else:
                raise AiUnavailableError(f"AI provider error: {status_code}") from http_err
        except (urllib.error.URLError, socket.timeout, TimeoutError) as net_err:
            raise AiUnavailableError("AI provider network timeout or connection failure") from net_err

        # Parse response
        try:
            resp_data = json.loads(body)
        except json.JSONDecodeError as exc:
            raise AiInvalidResponseError("Malformed JSON response from Gemini") from exc

        # Check for safety refusals or block reasons
        prompt_feedback = resp_data.get("promptFeedback", {})
        if prompt_feedback.get("blockReason"):
            raise AiRefusalError(f"AI blocked request: {prompt_feedback.get('blockReason')}")

        candidates = resp_data.get("candidates", [])
        if not candidates:
            raise AiInvalidResponseError("No response candidates returned by Gemini")

        candidate = candidates[0]
        finish_reason = candidate.get("finishReason")
        if finish_reason in ("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT"):
            raise AiRefusalError(f"AI content refusal: {finish_reason}")

        parts = candidate.get("content", {}).get("parts", [])
        if not parts:
            raise AiInvalidResponseError("Empty candidate parts returned by Gemini")

        return parts[0].get("text", "")

    def _classify_failure(self, exc: Exception) -> str:
        if isinstance(exc, AiUnavailableError):
            return "transient_unavailable"
        elif isinstance(exc, (socket.timeout, TimeoutError)):
            return "timeout"
        return exc.__class__.__name__

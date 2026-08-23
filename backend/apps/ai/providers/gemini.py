import enum
import json
import logging
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from django.conf import settings

from apps.ai.exceptions import (
    AiBadRequestError,
    AiInvalidResponseError,
    AiRefusalError,
    AiUnavailableError,
)
from apps.ai.observability import record_ai_metric
from apps.ai.providers.base import AIProvider

logger = logging.getLogger("promise")


class KeyHealthState(enum.Enum):
    HEALTHY = "HEALTHY"
    DEGRADED = "DEGRADED"
    COOLDOWN = "COOLDOWN"


@dataclass
class KeyStatus:
    api_key: str
    key_index: int  # 1-indexed: 1, 2, 3
    state: KeyHealthState = KeyHealthState.HEALTHY
    consecutive_failures: int = 0
    cooldown_until: float = 0.0
    last_used_at: float = 0.0
    total_successes: int = 0
    total_failures: int = 0


class GeminiKeyScheduler:
    """Thread-safe round-robin key scheduler with health tracking and failover."""

    def __init__(
        self,
        api_keys: List[str],
        cooldown_seconds: Optional[float] = None,
        failure_threshold: int = 2,
    ):
        self._keys: List[KeyStatus] = [
            KeyStatus(api_key=str(k).strip(), key_index=i + 1)
            for i, k in enumerate(api_keys)
            if k and str(k).strip()
        ]
        self._lock = threading.Lock()
        self._rotation_index = 0
        if cooldown_seconds is not None:
            self.cooldown_seconds = cooldown_seconds
        else:
            self.cooldown_seconds = float(getattr(settings, "GEMINI_KEY_COOLDOWN_SECONDS", 60.0))
        self.failure_threshold = failure_threshold

    @property
    def key_count(self) -> int:
        with self._lock:
            return len(self._keys)

    def get_candidate_keys(self) -> List[KeyStatus]:
        """Returns ordered list of candidate keys starting with next round-robin key.

        Advances rotation index atomically across all configured keys.
        Skips only keys that are currently in active cooldown.
        """
        with self._lock:
            if not self._keys:
                return []

            now = time.time()
            n = len(self._keys)

            # Update expired cooldowns
            for key in self._keys:
                if key.state == KeyHealthState.COOLDOWN and now >= key.cooldown_until:
                    key.state = KeyHealthState.DEGRADED
                    key.consecutive_failures = 0

            # Advance round-robin index across all configured keys
            start_idx = self._rotation_index % n

            # Build full rotation order starting from start_idx
            ordered = [self._keys[(start_idx + i) % n] for i in range(n)]

            # Filter out keys currently in active cooldown
            available = [
                k for k in ordered
                if not (k.state == KeyHealthState.COOLDOWN and now < k.cooldown_until)
            ]

            # If all keys are in cooldown, return all candidates ordered by earliest cooldown expiry
            if not available:
                self._rotation_index = (start_idx + 1) % n
                ordered_by_expiry = sorted(self._keys, key=lambda k: k.cooldown_until)
                return ordered_by_expiry

            # Set rotation index to advance past the chosen primary available key
            chosen_key = available[0]
            chosen_key_idx = (chosen_key.key_index - 1) % n
            self._rotation_index = (chosen_key_idx + 1) % n

            return available

    def record_success(self, key_index: int):
        with self._lock:
            for key in self._keys:
                if key.key_index == key_index:
                    key.state = KeyHealthState.HEALTHY
                    key.consecutive_failures = 0
                    key.cooldown_until = 0.0
                    key.last_used_at = time.time()
                    key.total_successes += 1
                    break

    def record_failure(self, key_index: int, is_transient: bool = True, force_cooldown: bool = False):
        with self._lock:
            now = time.time()
            for key in self._keys:
                if key.key_index == key_index:
                    key.last_used_at = now
                    key.total_failures += 1
                    key.consecutive_failures += 1

                    if force_cooldown or key.consecutive_failures >= self.failure_threshold:
                        key.state = KeyHealthState.COOLDOWN
                        key.cooldown_until = now + self.cooldown_seconds
                    else:
                        key.state = KeyHealthState.DEGRADED
                    break

    def get_key_status(self, key_index: int) -> Optional[KeyStatus]:
        with self._lock:
            for key in self._keys:
                if key.key_index == key_index:
                    return KeyStatus(
                        api_key=key.api_key,
                        key_index=key.key_index,
                        state=key.state,
                        consecutive_failures=key.consecutive_failures,
                        cooldown_until=key.cooldown_until,
                        last_used_at=key.last_used_at,
                        total_successes=key.total_successes,
                        total_failures=key.total_failures,
                    )
            return None


# Global singleton scheduler for environment-configured keys
_DEFAULT_SCHEDULER: Optional[GeminiKeyScheduler] = None
_DEFAULT_SCHEDULER_LOCK = threading.Lock()


def get_default_scheduler() -> GeminiKeyScheduler:
    global _DEFAULT_SCHEDULER
    with _DEFAULT_SCHEDULER_LOCK:
        if _DEFAULT_SCHEDULER is None:
            import os
            configured = [
                os.environ.get("GEMINI_API_KEY_1") or getattr(settings, "GEMINI_API_KEY_1", ""),
                os.environ.get("GEMINI_API_KEY_2") or getattr(settings, "GEMINI_API_KEY_2", ""),
                os.environ.get("GEMINI_API_KEY_3") or getattr(settings, "GEMINI_API_KEY_3", ""),
            ]
            keys = [str(k).strip() for k in configured if k and str(k).strip()]
            _DEFAULT_SCHEDULER = GeminiKeyScheduler(keys)
        return _DEFAULT_SCHEDULER


def reset_default_scheduler(keys: Optional[List[str]] = None):
    """Utility for testing or reconfiguration to reset global scheduler state."""
    global _DEFAULT_SCHEDULER
    with _DEFAULT_SCHEDULER_LOCK:
        if keys is not None:
            _DEFAULT_SCHEDULER = GeminiKeyScheduler(keys)
        else:
            _DEFAULT_SCHEDULER = None


class GeminiProvider(AIProvider):
    """Google Gemini AI Provider with thread-safe round-robin routing & health-aware failover."""

    BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    def __init__(
        self,
        api_keys: Optional[List[str]] = None,
        default_model: Optional[str] = None,
        timeout: Optional[int] = None,
        max_output_tokens: Optional[int] = None,
        cooldown_seconds: Optional[float] = None,
    ):
        if api_keys is not None:
            self._scheduler = GeminiKeyScheduler(
                api_keys=api_keys,
                cooldown_seconds=cooldown_seconds,
            )
        else:
            self._scheduler = get_default_scheduler()

        self.default_model = (
            default_model
            or getattr(settings, "GEMINI_DEFAULT_MODEL", "gemini-3.6-flash")
        )
        self.timeout = timeout or getattr(settings, "GEMINI_TIMEOUT_SECONDS", 15)
        self.max_output_tokens = (
            max_output_tokens
            or getattr(settings, "GEMINI_MAX_OUTPUT_TOKENS", 1024)
        )

    @property
    def scheduler(self) -> GeminiKeyScheduler:
        return self._scheduler

    def generate_structured(
        self,
        *,
        prompt: str,
        schema: Optional[Dict[str, Any]] = None,
        system_prompt: Optional[str] = None,
        model: Optional[str] = None,
        feature: str = "structured_ai",
    ) -> Dict[str, Any]:
        chosen_model = model or self.default_model
        raw_text = self._call_with_fallback(
            prompt=prompt,
            system_prompt=system_prompt,
            model=chosen_model,
            response_mime_type="application/json",
            response_schema=schema,
            feature=feature,
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
        feature: str = "text_ai",
    ) -> str:
        chosen_model = model or self.default_model
        return self._call_with_fallback(
            prompt=prompt,
            system_prompt=system_prompt,
            model=chosen_model,
            response_mime_type="text/plain",
            feature=feature,
        )

    def _call_with_fallback(
        self,
        *,
        prompt: str,
        system_prompt: Optional[str] = None,
        model: str,
        response_mime_type: str = "text/plain",
        response_schema: Optional[Dict[str, Any]] = None,
        feature: str = "ai_feature",
    ) -> str:
        candidates = self._scheduler.get_candidate_keys()
        if not candidates:
            logger.warning(
                "Gemini AI called with no API keys configured",
                extra={"provider": "gemini", "model": model, "failure_category": "no_keys_configured"},
            )
            raise AiUnavailableError("AI provider is not configured.")

        last_exception = None
        start_time = time.time()

        for attempt, candidate in enumerate(candidates, start=1):
            key_index = candidate.key_index
            try:
                result = self._invoke_api(
                    api_key=candidate.api_key,
                    prompt=prompt,
                    system_prompt=system_prompt,
                    model=model,
                    response_mime_type=response_mime_type,
                    response_schema=response_schema,
                )
                duration_ms = int((time.time() - start_time) * 1000)
                self._scheduler.record_success(key_index)

                record_ai_metric(
                    feature=feature,
                    model=model,
                    prompt_version="1.0",
                    latency_ms=duration_ms,
                    success=True,
                    fallback_key_index=key_index,
                )
                return result

            except (AiBadRequestError, AiRefusalError) as non_retryable:
                # Permanent non-retryable user/prompt error - fail-fast without burning remaining keys
                duration_ms = int((time.time() - start_time) * 1000)
                category = "refusal" if isinstance(non_retryable, AiRefusalError) else "bad_request"
                record_ai_metric(
                    feature=feature,
                    model=model,
                    prompt_version="1.0",
                    latency_ms=duration_ms,
                    success=False,
                    failure_category=category,
                    fallback_key_index=key_index,
                )
                raise

            except Exception as exc:
                category = self._classify_failure(exc)
                force_cooldown = False
                err_str = str(exc).lower()
                if "rate limit" in err_str or "429" in err_str or "quota" in err_str or "unauthorized" in err_str or "invalid" in err_str:
                    force_cooldown = True

                self._scheduler.record_failure(
                    key_index=key_index,
                    is_transient=True,
                    force_cooldown=force_cooldown,
                )

                logger.warning(
                    f"Gemini API key {key_index} (attempt {attempt}/{len(candidates)}) failed: {category}",
                    extra={
                        "provider": "gemini",
                        "model": model,
                        "key_index": key_index,
                        "attempt": attempt,
                        "failure_category": category,
                    },
                )
                last_exception = exc
                # Try next candidate key in rotation

        # All candidate keys failed
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(
            f"All {len(candidates)} Gemini API keys failed",
            extra={"provider": "gemini", "model": model, "failure_category": "all_keys_exhausted"},
        )
        record_ai_metric(
            feature=feature,
            model=model,
            prompt_version="1.0",
            latency_ms=duration_ms,
            success=False,
            failure_category="all_keys_exhausted",
            fallback_key_index=0,
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
                body = resp.read().decode("utf-8")
        except urllib.error.HTTPError as http_err:
            status_code = http_err.code
            err_body = http_err.read().decode("utf-8", errors="replace")
            err_body_lower = err_body.lower()

            # 1. Key-specific authorization / invalid key failure (401, 403, or API key invalid in body)
            # This is a key-specific error -> retryable on the next key in rotation.
            is_key_auth_error = (
                status_code in (401, 403)
                or "api_key" in err_body_lower
                or "api key" in err_body_lower
                or "unauthenticated" in err_body_lower
                or "permission_denied" in err_body_lower
                or "api_key_invalid" in err_body_lower
            )
            if is_key_auth_error:
                raise AiUnavailableError(f"Gemini API key invalid or unauthorized: {status_code}") from http_err

            # 2. Malformed request / validation failure (400, 422)
            # Permanent request error -> fail-fast immediately (AiBadRequestError), NOT retryable across keys.
            elif status_code in (400, 422):
                raise AiBadRequestError(f"AI request validation failed: {status_code}") from http_err

            # 3. Rate limiting / Quota (429) -> retryable transient error
            elif status_code == 429:
                raise AiUnavailableError("AI rate limit / quota exceeded: 429") from http_err

            # 4. Server errors (500, 502, 503, 504) -> retryable transient error
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

        # Check for safety refusals or block reasons (Non-retryable content refusal -> fail fast)
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

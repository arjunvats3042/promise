import logging
from typing import Optional

logger = logging.getLogger("promise")


def record_ai_metric(
    *,
    feature: str,
    model: str,
    prompt_version: str,
    latency_ms: int,
    success: bool,
    failure_category: Optional[str] = None,
    fallback_key_index: int = 1,
):
    """Records sanitized AI observability metrics without logging keys or raw prompts."""
    extra_data = {
        "event_type": "ai_invocation",
        "feature": feature,
        "model": model,
        "prompt_version": prompt_version,
        "latency_ms": latency_ms,
        "success": success,
        "fallback_key_index": fallback_key_index,
    }
    if failure_category:
        extra_data["failure_category"] = failure_category

    if success:
        logger.info(
            f"AI [{feature}] completed in {latency_ms}ms (model={model}, version={prompt_version})",
            extra=extra_data,
        )
    else:
        logger.warning(
            f"AI [{feature}] failed: {failure_category} in {latency_ms}ms (model={model})",
            extra=extra_data,
        )

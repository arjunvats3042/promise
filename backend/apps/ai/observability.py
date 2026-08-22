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

    # Emit analytics event safely
    try:
        from apps.analytics.events import EVENT_AI_UNAVAILABLE
        from apps.analytics.services import record_analytics_event

        feature_event_map = {
            "goal_builder": "ai_goal_builder_used",
            "commitment_refiner": "ai_commitment_refiner_used",
            "thought_parser": "ai_thought_parser_used",
            "insights": "ai_weekly_insights_viewed",
            "command_parser": "ai_command_used",
            "planner": "ai_planner_used",
            "reflection": "ai_reflection_used",
            "shared_goal_summary": "ai_shared_goal_summary_used",
            "chat_summary": "ai_chat_summary_used",
        }
        an_event = feature_event_map.get(feature, "ai_command_used")
        props = {
            "feature": feature,
            "latency_ms": latency_ms,
            "success": success,
        }
        record_analytics_event(event_name=an_event, properties=props)
        if not success:
            record_analytics_event(event_name=EVENT_AI_UNAVAILABLE, properties={"feature": feature})
    except Exception:
        pass

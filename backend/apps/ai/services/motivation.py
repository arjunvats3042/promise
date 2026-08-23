import logging
import re
import time
from datetime import date
from typing import List, Optional

from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.ai.models import DailyMotivationQuote
from apps.ai.observability import record_ai_metric
from apps.ai.prompts import DAILY_QUOTE_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

logger = logging.getLogger("promise")

FALLBACK_QUOTES: List[str] = [
    "Consistent small steps taken today create the foundation for lasting progress tomorrow.",
    "Focus your energy on what you can control right now and let momentum build naturally.",
    "A steady and calm pace often carries you much further than hurried effort.",
    "Each day is an opportunity to practice patience and recommit to what matters most.",
    "Quiet persistence in simple daily habits brings meaningful growth over time.",
    "Clarity comes from taking action rather than waiting for perfect conditions to appear.",
    "Give your full attention to the present task and trust the cumulative process.",
    "Small commitments honored with care build genuine self-trust and enduring resilience.",
    "Progress is measured by steady return to practice rather than unbroken perfection.",
    "Begin where you are with whatever you have and take the next honest step.",
    "Quiet dedication to your daily routines yields compound results in due season.",
    "A thoughtful approach to your responsibilities creates peace and sustained focus.",
    "Honor your intentions today by simply completing what lies directly in front of you.",
    "Patience with yourself allows steady progress to unfold without unnecessary strain.",
    "True discipline is choosing gentle consistency over sporadic bursts of intensity.",
    "Focus on doing one meaningful thing well rather than scattering your attention.",
    "Every quiet effort you make today contributes to your long-term capability.",
    "A calm mind and steady hands accomplish more than anxious haste.",
    "Show up for what matters with quiet resolve and let the results follow.",
    "Consistent practice transforms difficult beginnings into natural daily rhythms.",
    "Trust the value of patient daily practice and stay grounded in your purpose.",
    "Simple actions repeated daily build a quiet confidence that withstands obstacles.",
    "Focus on making steady progress rather than seeking immediate and effortless outcomes.",
    "Caring for your daily routines creates the space needed for thoughtful work.",
    "Every intentional choice you make today strengthens your commitment to growth.",
    "Small promises kept with integrity become the quiet pillars of genuine achievement.",
    "When you approach your day with calm focus, complex challenges become manageable.",
    "Dedicate your effort to the work at hand and allow momentum to develop.",
]


def validate_quote(raw_text: Optional[str]) -> Optional[str]:
    """Validates that candidate quote meets strict guidelines.

    Rules:
    - 8 to 20 words
    - Exactly 1 sentence
    - Non-empty
    - No attribution markers (- , — , by , etc.)
    - No mention of Promise
    - Returns stripped clean quote if valid, else None.
    """
    if not raw_text or not isinstance(raw_text, str):
        return None

    # Clean surrounding quotation marks, markdown, and whitespace
    cleaned = raw_text.strip().strip('"\'`“”«»')
    cleaned = re.sub(r"\s+", " ", cleaned).strip()

    if not cleaned:
        return None

    # Disallow forbidden terms / attributions
    cleaned_lower = cleaned.lower()
    if "promise" in cleaned_lower:
        return None

    # Check for attribution markers like " - Author", " — Author", " by Author"
    if re.search(r"(\s*[-—–]\s+[A-Za-z]+|\s+by\s+[A-Z][a-z]+)", cleaned):
        return None

    # Check word count (8 to 20 words)
    words = cleaned.split()
    if len(words) < 8 or len(words) > 20:
        return None

    # Check sentence count (must be exactly one sentence ending with . ! or ?)
    # Remove final punctuation for check
    sentence_delimiters = re.findall(r"[.!?]", cleaned)
    if len(sentence_delimiters) > 1:
        # Check if multiple sentences or multiple punctuation marks
        sentences = [s.strip() for s in re.split(r"[.!?]+", cleaned) if s.strip()]
        if len(sentences) > 1:
            return None

    if not cleaned.endswith((".", "!", "?")):
        cleaned = f"{cleaned}."

    return cleaned


def get_deterministic_fallback_quote(target_date: date) -> str:
    """Returns a deterministic curated quote for the specified calendar date."""
    index = target_date.toordinal() % len(FALLBACK_QUOTES)
    return FALLBACK_QUOTES[index]


def get_or_create_daily_quote(
    target_date: Optional[date] = None,
    provider: Optional[AIProvider] = None,
) -> DailyMotivationQuote:
    """Retrieves or atomically creates exactly one shared daily motivation quote for target_date.

    Flow:
    1. Fast DB check for today's existing quote.
    2. If missing, generate candidate quote outside transaction using GeminiProvider.
    3. Validate candidate; fallback to deterministic curated quote if Gemini fails or invalid.
    4. Open short atomic transaction and save with unique constraint.
    5. On concurrent race condition (IntegrityError), re-query and return the winning quote.
    """
    if target_date is None:
        target_date = timezone.localdate()

    # 1. Fast path: check if quote already exists in database
    existing = DailyMotivationQuote.objects.filter(date=target_date).first()
    if existing:
        return existing

    # 2. Generate candidate quote outside database transaction
    quote_text: Optional[str] = None
    provider_name = "gemini"
    model_name = get_model_for_feature("daily_quote") or "gemini-3.6-flash"
    success = False
    failure_category = None
    start_time = time.time()

    if provider is None:
        provider = GeminiProvider()

    try:
        raw_text = provider.generate_text(
            prompt="Generate one original, calm, practical motivational sentence for today.",
            system_prompt=DAILY_QUOTE_PROMPT_V1,
            model=model_name,
            feature="daily_quote",
        )
        validated = validate_quote(raw_text)
        if validated:
            quote_text = validated
            success = True
        else:
            failure_category = "quote_validation_failed"
            logger.warning(
                "Gemini generated quote failed validation rules, using fallback",
                extra={"date": str(target_date), "raw_text": raw_text},
            )
    except Exception as exc:
        failure_category = exc.__class__.__name__
        logger.warning(
            "Gemini failed to generate daily quote, using fallback",
            extra={"date": str(target_date), "error": str(exc)},
        )
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="daily_quote",
            model=model_name,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )

    # Use deterministic fallback if generation failed or invalid
    if not quote_text:
        quote_text = get_deterministic_fallback_quote(target_date)
        provider_name = "deterministic_fallback"
        model_name = "curated_v1"

    # 3. Short atomic transaction insert with IntegrityError handling for concurrent requests
    try:
        with transaction.atomic():
            quote_obj, _ = DailyMotivationQuote.objects.get_or_create(
                date=target_date,
                defaults={
                    "quote": quote_text,
                    "provider": provider_name,
                    "model": model_name,
                },
            )
            return quote_obj
    except IntegrityError:
        # Another concurrent request already created the quote for this date
        return DailyMotivationQuote.objects.get(date=target_date)

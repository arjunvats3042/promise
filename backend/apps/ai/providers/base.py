from abc import ABC, abstractmethod
from typing import Any, Dict, Optional


class AIProvider(ABC):
    """Abstract interface for AI model providers."""

    @abstractmethod
    def generate_structured(
        self,
        *,
        prompt: str,
        schema: Optional[Dict[str, Any]] = None,
        system_prompt: Optional[str] = None,
        model: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Generate structured JSON conforming to the given schema."""
        pass

    @abstractmethod
    def generate_text(
        self,
        *,
        prompt: str,
        system_prompt: Optional[str] = None,
        model: Optional[str] = None,
    ) -> str:
        """Generate free-form text response."""
        pass

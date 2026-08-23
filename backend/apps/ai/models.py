import uuid
from django.db import models


class DailyMotivationQuote(models.Model):
    """Daily motivational quote generated via AI or deterministic curated fallback."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    date = models.DateField(unique=True, db_index=True)
    quote = models.TextField()
    provider = models.CharField(max_length=64, default="gemini")
    model = models.CharField(max_length=64, default="gemini-3.6-flash")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "daily_motivation_quotes"
        ordering = ["-date"]

    def __str__(self):
        return f"{self.date}: {self.quote[:40]}..."

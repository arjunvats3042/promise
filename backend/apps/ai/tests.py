from django.test import TestCase
from rest_framework.exceptions import ValidationError

from apps.ai.serializers import ThoughtParserRequestSerializer
from apps.ai.prompts import THOUGHT_PARSER_PROMPT_V1


class ThoughtParserTests(TestCase):
    def test_thought_serializer_valid_under_500_chars(self):
        serializer = ThoughtParserRequestSerializer(data={
            "thought": "Submit report by 5pm and run 5km daily.",
            "timezone": "Asia/Kolkata",
        })
        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertEqual(serializer.validated_data["thought"], "Submit report by 5pm and run 5km daily.")

    def test_thought_serializer_rejects_over_500_chars(self):
        long_thought = "a" * 501
        serializer = ThoughtParserRequestSerializer(data={
            "thought": long_thought,
            "timezone": "Asia/Kolkata",
        })
        self.assertFalse(serializer.is_valid())
        self.assertIn("thought", serializer.errors)

    def test_thought_serializer_accepts_exactly_500_chars(self):
        thought_500 = "a" * 500
        serializer = ThoughtParserRequestSerializer(data={
            "thought": thought_500,
            "timezone": "Asia/Kolkata",
        })
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_prompt_contains_out_of_scope_guardrail(self):
        self.assertIn("NO actionable tasks", THOUGHT_PARSER_PROMPT_V1)
        self.assertIn('{"items": []}', THOUGHT_PARSER_PROMPT_V1)

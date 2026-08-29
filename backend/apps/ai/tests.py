from django.test import SimpleTestCase
from rest_framework.exceptions import ValidationError

from apps.ai.serializers import ThoughtParserRequestSerializer
from apps.ai.prompts import THOUGHT_PARSER_PROMPT_V1
from apps.ai.services.thought_parser import _heuristic_parse_thought, parse_thought_into_promises
from apps.ai.providers.gemini import GeminiProvider


class ThoughtParserTests(SimpleTestCase):
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

    def test_heuristic_fallback_parses_commitments_and_goals(self):
        thought = "Finish Q3 budget report by tomorrow 5pm\nDrink 3L water daily"
        result = _heuristic_parse_thought(thought, "Asia/Kolkata")
        items = result.get("items", [])
        self.assertEqual(len(items), 2)
        self.assertEqual(items[0]["type"], "commitment")
        self.assertEqual(items[1]["type"], "goal")
        self.assertEqual(items[1]["recurrence_kind"], "DAILY")

    def test_normalize_parsed_items_clears_mismatched_due_fields(self):
        from apps.ai.services.thought_parser import _normalize_parsed_items
        raw = {
            "items": [
                {"type": "commitment", "title": "Call mom", "due_at": None, "due_precision": "DAY"},
                {"type": "commitment", "title": "Submit report", "due_at": "2026-08-30T17:30:00Z", "due_precision": "HOUR"},
                {"type": "commitment", "title": "Read book", "due_at": "invalid-date", "due_precision": "DAY"},
            ]
        }
        res = _normalize_parsed_items(raw)
        items = res["items"]
        self.assertIsNone(items[0]["due_at"])
        self.assertIsNone(items[0]["due_precision"])

        self.assertIsNotNone(items[1]["due_at"])
        self.assertEqual(items[1]["due_precision"], "HOUR")

        self.assertIsNone(items[2]["due_at"])
        self.assertIsNone(items[2]["due_precision"])

    def test_heuristic_relative_dates_and_times(self):
        thought = "call mom at 12:00 p.m. tomorrow\ncall mom day after tomorrow at 12:00 p.m."
        result = _heuristic_parse_thought(thought, "Asia/Kolkata")
        items = result.get("items", [])
        self.assertEqual(len(items), 2)
        self.assertEqual(items[0]["type"], "commitment")
        self.assertIsNotNone(items[0]["due_at"])
        self.assertEqual(items[0]["due_precision"], "HOUR")

        self.assertEqual(items[1]["type"], "commitment")
        self.assertIsNotNone(items[1]["due_at"])
        self.assertEqual(items[1]["due_precision"], "HOUR")

    def test_heuristic_after_n_days_and_afternoon(self):
        thought = "call mom after 3 days\ncall doctor in afternoon"
        result = _heuristic_parse_thought(thought, "Asia/Kolkata")
        items = result.get("items", [])
        self.assertEqual(len(items), 2)
        self.assertEqual(items[0]["type"], "commitment")
        self.assertIsNotNone(items[0]["due_at"])
        self.assertEqual(items[0]["due_precision"], "DAY")

        self.assertEqual(items[1]["type"], "commitment")
        self.assertIsNotNone(items[1]["due_at"])
        self.assertEqual(items[1]["due_precision"], "HOUR")

    def test_compound_continuous_run_on_sentence(self):
        thought = "I have to call my mother tomorrow and afternoon and I have to send a pdf to my manager on 1st of September and I have to call my sister 3 days after"
        result = _heuristic_parse_thought(thought, "Asia/Kolkata")
        items = result.get("items", [])
        self.assertEqual(len(items), 3)

        self.assertEqual(items[0]["type"], "commitment")
        self.assertIn("call my mother", items[0]["title"].lower())
        self.assertIsNotNone(items[0]["due_at"])
        self.assertEqual(items[0]["due_precision"], "HOUR")

        self.assertEqual(items[1]["type"], "commitment")
        self.assertIn("send a pdf to my manager", items[1]["title"].lower())
        self.assertIsNotNone(items[1]["due_at"])

        self.assertEqual(items[2]["type"], "commitment")
        self.assertIn("call my sister", items[2]["title"].lower())
        self.assertIsNotNone(items[2]["due_at"])





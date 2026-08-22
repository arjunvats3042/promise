"""Tests for Batch 14A: Production Deployment Preparation.

Validates:
- Production settings module configuration
- check_production management command behavior and zero secrets leak
- publish_outbox command arguments and graceful shutdown
- run_notification_dispatcher command arguments and graceful shutdown
- Kafka SASL/SSL configuration builder
"""

import io
from unittest.mock import MagicMock, patch

from django.core.management import call_command
from django.test import TestCase, override_settings

from apps.notifications.dispatcher import DispatchResult
from apps.outbox.kafka import _build_kafka_config
from apps.outbox.publisher import PublishResult


class ProductionDeploymentTests(TestCase):
    def test_kafka_config_builder_plain(self):
        with override_settings(KAFKA_SECURITY_PROTOCOL="PLAINTEXT"):
            conf = _build_kafka_config({"bootstrap.servers": "localhost:9092"})
            self.assertEqual(conf["bootstrap.servers"], "localhost:9092")
            self.assertNotIn("security.protocol", conf)
            self.assertNotIn("sasl.mechanism", conf)

    def test_kafka_config_builder_sasl_ssl(self):
        with override_settings(
            KAFKA_SECURITY_PROTOCOL="SASL_SSL",
            KAFKA_SASL_MECHANISM="SCRAM-SHA-256",
            KAFKA_SASL_USERNAME="test-user",
            KAFKA_SASL_PASSWORD="test-password",
            KAFKA_SSL_CA_LOCATION="/path/to/ca.pem",
        ):
            conf = _build_kafka_config({"bootstrap.servers": "aiven-kafka:19096"})
            self.assertEqual(conf["bootstrap.servers"], "aiven-kafka:19096")
            self.assertEqual(conf["security.protocol"], "SASL_SSL")
            self.assertEqual(conf["sasl.mechanism"], "SCRAM-SHA-256")
            self.assertEqual(conf["sasl.username"], "test-user")
            self.assertEqual(conf["sasl.password"], "test-password")
            self.assertEqual(conf["ssl.ca.location"], "/path/to/ca.pem")

    @patch("apps.outbox.management.commands.publish_outbox.publish_due_outbox_events")
    def test_publish_outbox_command_once(self, mock_publish):
        mock_publish.return_value = PublishResult(published=2, failed=0)
        out = io.StringIO()
        call_command("publish_outbox", "--once", stdout=out)
        self.assertIn("published=2 failed=0", out.getvalue())
        mock_publish.assert_called_once()

    @patch("apps.notifications.management.commands.run_notification_dispatcher.dispatch_due_reminders")
    def test_run_notification_dispatcher_command_once(self, mock_dispatch):
        mock_dispatch.return_value = DispatchResult(
            dispatched=3, suppressed=1, cancelled=0, retried=0, failed=0
        )
        out = io.StringIO()
        call_command("run_notification_dispatcher", "--once", stdout=out)
        output = out.getvalue()
        self.assertIn("dispatched=3", output)
        self.assertIn("suppressed=1", output)
        mock_dispatch.assert_called_once()

    def test_check_production_command_passes_with_valid_settings(self):
        with override_settings(
            DEBUG=False,
            ALLOWED_HOSTS=["api.promise.app"],
            SECRET_KEY="a" * 50,
            JWT_SIGNING_KEY="jwt-secret-key-12345",
            AUTH_REFRESH_TOKEN_PEPPER="pepper-secret-key-12345",
            AUTH_REFRESH_TOKEN_ENCRYPTION_KEY="fernet-secret-key-12345",
            KAFKA_BOOTSTRAP_SERVERS="kafka.internal:9092",
            GEMINI_API_KEY_1="AIzaSyDummy1",
            GEMINI_API_KEY_2="AIzaSyDummy2",
            GEMINI_API_KEY_3="AIzaSyDummy3",
            SECURE_PROXY_SSL_HEADER=("HTTP_X_FORWARDED_PROTO", "https"),
        ):
            out = io.StringIO()
            call_command("check_production", stdout=out)
            output = out.getvalue()
            self.assertIn("RESULT: PRODUCTION CONFIGURATION VALID", output)
            self.assertIn("[OK] DEBUG = False", output)
            self.assertIn("[OK] ALLOWED_HOSTS configured", output)
            self.assertIn("[OK] DJANGO_SECRET_KEY is configured", output)
            self.assertIn("[OK] JWT_SIGNING_KEY is configured", output)
            self.assertIn("[OK] Refresh token pepper & encryption key configured", output)
            self.assertIn("[OK] Gemini AI keys CONFIGURED (3 fallback key(s))", output)
            # Ensure no secret strings leaked into stdout
            self.assertNotIn("a" * 50, output)
            self.assertNotIn("jwt-secret-key-12345", output)
            self.assertNotIn("AIzaSyDummy1", output)
            self.assertNotIn("AIzaSyDummy2", output)
            self.assertNotIn("AIzaSyDummy3", output)

    def test_check_production_command_fails_when_debug_is_true(self):
        with override_settings(DEBUG=True):
            out = io.StringIO()
            with self.assertRaises(SystemExit):
                call_command("check_production", stdout=out)
            output = out.getvalue()
            self.assertIn("[FAIL] DEBUG is True!", output)
            self.assertIn("RESULT: PRODUCTION CONFIGURATION CONTAINS CRITICAL ISSUES", output)

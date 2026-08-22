"""Production readiness and configuration sanity checker.

Validates that critical production settings, secrets, database, Redis,
Kafka, Firebase, and Gemini credentials are appropriately configured.

CRITICAL SECURITY RULE:
Never output secret values, tokens, credentials, or keys to stdout/stderr.
Report status only (e.g. CONFIGURED, MISSING, HEALTHY, UNHEALTHY).
"""

import sys
from django.conf import settings
from django.core.management.base import BaseCommand
from django.db import connection

from apps.core.redis import get_redis_client


class Command(BaseCommand):
    help = "Validates production environment configuration and downstream service health."

    def handle(self, *args, **options):
        self.stdout.write("==================================================")
        self.stdout.write("PROMISE PRODUCTION CONFIGURATION AUDIT")
        self.stdout.write("==================================================")

        all_ok = True

        # 1. Debug Mode
        if settings.DEBUG is False:
            self.stdout.write(self.style.SUCCESS("[OK] DEBUG = False"))
        else:
            self.stdout.write(self.style.ERROR("[FAIL] DEBUG is True! Must be False in production."))
            all_ok = False

        # 2. Allowed Hosts
        allowed_hosts = getattr(settings, "ALLOWED_HOSTS", [])
        if allowed_hosts and "*" not in allowed_hosts:
            self.stdout.write(self.style.SUCCESS(f"[OK] ALLOWED_HOSTS configured ({len(allowed_hosts)} host(s))"))
        else:
            self.stdout.write(self.style.ERROR("[FAIL] ALLOWED_HOSTS is empty or contains wildcard '*'"))
            all_ok = False

        # 3. Secret Key
        secret_key = getattr(settings, "SECRET_KEY", "")
        if secret_key and secret_key != "changeme" and len(secret_key) >= 32:
            self.stdout.write(self.style.SUCCESS("[OK] DJANGO_SECRET_KEY is configured"))
        else:
            self.stdout.write(self.style.ERROR("[FAIL] DJANGO_SECRET_KEY is missing, default, or too short"))
            all_ok = False

        # 4. JWT Signing Key
        jwt_key = getattr(settings, "JWT_SIGNING_KEY", "")
        if jwt_key and jwt_key != "changeme":
            self.stdout.write(self.style.SUCCESS("[OK] JWT_SIGNING_KEY is configured"))
        else:
            self.stdout.write(self.style.ERROR("[FAIL] JWT_SIGNING_KEY is missing or unconfigured"))
            all_ok = False

        # 5. Refresh Token Cryptography
        pepper = getattr(settings, "AUTH_REFRESH_TOKEN_PEPPER", "")
        enc_key = getattr(settings, "AUTH_REFRESH_TOKEN_ENCRYPTION_KEY", "")
        if pepper and pepper != "changeme" and enc_key and enc_key != "changeme":
            self.stdout.write(self.style.SUCCESS("[OK] Refresh token pepper & encryption key configured"))
        else:
            self.stdout.write(self.style.ERROR("[FAIL] AUTH_REFRESH_TOKEN_PEPPER or AUTH_REFRESH_TOKEN_ENCRYPTION_KEY missing"))
            all_ok = False

        # 6. PostgreSQL Database Connection
        try:
            connection.ensure_connection()
            self.stdout.write(self.style.SUCCESS("[OK] PostgreSQL connection HEALTHY"))
        except Exception as exc:
            self.stdout.write(self.style.ERROR(f"[FAIL] PostgreSQL connection UNHEALTHY ({type(exc).__name__})"))
            all_ok = False

        # 7. Redis Connection
        try:
            redis_client = get_redis_client()
            redis_client.ping()
            self.stdout.write(self.style.SUCCESS("[OK] Redis connection HEALTHY"))
        except Exception as exc:
            self.stdout.write(self.style.ERROR(f"[FAIL] Redis connection UNHEALTHY ({type(exc).__name__})"))
            all_ok = False

        # 8. Kafka Configuration
        kafka_bootstrap = getattr(settings, "KAFKA_BOOTSTRAP_SERVERS", "")
        kafka_proto = getattr(settings, "KAFKA_SECURITY_PROTOCOL", "PLAINTEXT")
        if kafka_bootstrap:
            self.stdout.write(self.style.SUCCESS(f"[OK] Kafka bootstrap servers CONFIGURED (protocol={kafka_proto})"))
        else:
            self.stdout.write(self.style.ERROR("[FAIL] KAFKA_BOOTSTRAP_SERVERS is missing"))
            all_ok = False

        # 9. Gemini AI Keys
        gemini_keys = [
            getattr(settings, "GEMINI_API_KEY_1", ""),
            getattr(settings, "GEMINI_API_KEY_2", ""),
            getattr(settings, "GEMINI_API_KEY_3", ""),
        ]
        configured_keys = [k for k in gemini_keys if k and k.strip()]
        if configured_keys:
            self.stdout.write(self.style.SUCCESS(f"[OK] Gemini AI keys CONFIGURED ({len(configured_keys)} fallback key(s))"))
        else:
            self.stdout.write(self.style.WARNING("[WARN] No Gemini API keys configured. AI features will fail closed."))

        # 10. Firebase / FCM
        fcm_enabled = getattr(settings, "FCM_ENABLED", False)
        fcm_creds = getattr(settings, "FIREBASE_CREDENTIALS_JSON", "")
        if fcm_enabled:
            if fcm_creds:
                self.stdout.write(self.style.SUCCESS("[OK] Firebase FCM ENABLED with credentials payload"))
            else:
                self.stdout.write(self.style.WARNING("[WARN] Firebase FCM ENABLED but credentials JSON is empty"))
        else:
            self.stdout.write(self.style.NOTICE("[INFO] Firebase FCM is DISABLED (using mock dispatcher)"))

        # 11. Security Headers (if in production settings)
        sec_header = getattr(settings, "SECURE_PROXY_SSL_HEADER", None)
        if sec_header == ("HTTP_X_FORWARDED_PROTO", "https"):
            self.stdout.write(self.style.SUCCESS("[OK] SECURE_PROXY_SSL_HEADER configured for HTTPS reverse proxy"))
        else:
            self.stdout.write(self.style.NOTICE(f"[INFO] SECURE_PROXY_SSL_HEADER: {sec_header}"))

        self.stdout.write("==================================================")
        if all_ok:
            self.stdout.write(self.style.SUCCESS("RESULT: PRODUCTION CONFIGURATION VALID"))
        else:
            self.stdout.write(self.style.ERROR("RESULT: PRODUCTION CONFIGURATION CONTAINS CRITICAL ISSUES"))
            sys.exit(1)

"""Production settings for Promise.

Enforces DEBUG=False, strict host validation, SSL proxy headers,
secure cookie flags, HSTS, and required secret verification.
"""

from .base import *  # noqa: F403
from .base import env

DEBUG = False

# Strict host allowlist from environment.
# Must be set to production domains (e.g. api.promise.app, promise-web.up.railway.app).
ALLOWED_HOSTS = env.list("DJANGO_ALLOWED_HOSTS", default=[])
if not ALLOWED_HOSTS:
    ALLOWED_HOSTS = env.list("ALLOWED_HOSTS", default=[])

# Reverse proxy / TLS termination headers (Railway / Cloudflare / Load Balancers)
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
SECURE_SSL_REDIRECT = env.bool("SECURE_SSL_REDIRECT", default=True)

# Cookie & Session Security
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
SESSION_COOKIE_HTTPONLY = True
CSRF_COOKIE_HTTPONLY = True

# HTTP Strict Transport Security (HSTS)
SECURE_HSTS_SECONDS = env.int("SECURE_HSTS_SECONDS", default=31536000)  # 1 year
SECURE_HSTS_INCLUDE_SUBDOMAINS = env.bool("SECURE_HSTS_INCLUDE_SUBDOMAINS", default=True)
SECURE_HSTS_PRELOAD = env.bool("SECURE_HSTS_PRELOAD", default=True)
SECURE_CONTENT_TYPE_NOSNIFF = True

# Default Aiven Kafka security protocols in production
KAFKA_SECURITY_PROTOCOL = env("KAFKA_SECURITY_PROTOCOL", default="SASL_SSL")
KAFKA_SASL_MECHANISM = env("KAFKA_SASL_MECHANISM", default="SCRAM-SHA-256")

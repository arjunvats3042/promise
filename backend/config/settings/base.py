from pathlib import Path

import environ

BASE_DIR = Path(__file__).resolve().parent.parent.parent
REPO_ROOT = BASE_DIR.parent

env = environ.Env()

for env_file in (REPO_ROOT / ".env", BASE_DIR / ".env"):
    if env_file.exists():
        env.read_env(env_file, overwrite=False)

SECRET_KEY = env("DJANGO_SECRET_KEY")
DEBUG = env.bool("DJANGO_DEBUG", default=False)
ALLOWED_HOSTS = env.list("DJANGO_ALLOWED_HOSTS", default=["localhost", "127.0.0.1", "192.168.1.29"])

INSTALLED_APPS = [
    "daphne",
    "channels",
    "apps.core.apps.CoreConfig",
    "apps.users.apps.UsersConfig",
    "apps.authentication.apps.AuthenticationConfig",
    "apps.outbox.apps.OutboxConfig",
    "apps.commitments.apps.CommitmentsConfig",
    "apps.goals.apps.GoalsConfig",
    "apps.notifications.apps.NotificationsConfig",
    "apps.search.apps.SearchConfig",
    "apps.ai.apps.AiConfig",
    "apps.analytics.apps.AnalyticsConfig",
    "django.contrib.postgres",
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "django.contrib.staticfiles",
    "rest_framework",
]

ASGI_APPLICATION = "config.asgi.application"

CHANNEL_LAYERS = {
    "default": {
        "BACKEND": "channels_redis.core.RedisChannelLayer",
        "CONFIG": {
            "hosts": [env("REDIS_URL", default="redis://localhost:6379/0")],
        },
    },
}

AUTH_USER_MODEL = "users.User"

PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2SHA1PasswordHasher",
]

AUTH_PASSWORD_VALIDATORS = [
    {
        "NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator",
        "OPTIONS": {"user_attributes": ["email", "name"]},
    },
    {
        "NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
        "OPTIONS": {"min_length": 8},
    },
    {
        "NAME": "django.contrib.auth.password_validation.CommonPasswordValidator",
    },
    {
        "NAME": "django.contrib.auth.password_validation.NumericPasswordValidator",
    },
]

MIDDLEWARE = [
    "apps.core.middleware.RequestObservabilityMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

DATABASES = {
    "default": env.db("DATABASE_URL"),
}

REDIS_URL = env("REDIS_URL")

# Kafka Event Streaming & Aiven SASL/SSL
KAFKA_BOOTSTRAP_SERVERS = env("KAFKA_BOOTSTRAP_SERVERS")
KAFKA_SECURITY_PROTOCOL = env("KAFKA_SECURITY_PROTOCOL", default="PLAINTEXT")
KAFKA_SASL_MECHANISM = env("KAFKA_SASL_MECHANISM", default="SCRAM-SHA-256")
KAFKA_SASL_USERNAME = env("KAFKA_SASL_USERNAME", default="")
KAFKA_SASL_PASSWORD = env("KAFKA_SASL_PASSWORD", default="")
KAFKA_SSL_CA_LOCATION = env("KAFKA_SSL_CA_LOCATION", default="")
# PEM certificate string (preferred for Railway/cloud deploys where no filesystem
# CA path exists). Takes precedence over KAFKA_SSL_CA_LOCATION when both are set.
KAFKA_SSL_CA_CERT = env("KAFKA_SSL_CA_CERT", default="")

AUTH_REFRESH_TOKEN_PEPPER = env("AUTH_REFRESH_TOKEN_PEPPER", default="")
AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = env(
    "AUTH_REFRESH_TOKEN_ENCRYPTION_KEY",
    default="",
)

JWT_ALGORITHM = "HS256"
JWT_ACCESS_TTL_SECONDS = 900
JWT_LEEWAY_SECONDS = 30
JWT_ISSUER = env("JWT_ISSUER", default="promise-api")
JWT_AUDIENCE = env("JWT_AUDIENCE", default="promise-client")
JWT_SIGNING_KEY = env("JWT_SIGNING_KEY", default="")
JWT_SIGNING_KEY_PREVIOUS = env("JWT_SIGNING_KEY_PREVIOUS", default="")
JWT_KEY_ID = env("JWT_KEY_ID", default="local")
JWT_KEY_ID_PREVIOUS = env("JWT_KEY_ID_PREVIOUS", default="")

# CORS / CSRF
CORS_ALLOWED_ORIGINS = env.list("CORS_ALLOWED_ORIGINS", default=[])
CSRF_TRUSTED_ORIGINS = env.list("CSRF_TRUSTED_ORIGINS", default=[])

# Email Provider Configuration
EMAIL_BACKEND = env(
    "DJANGO_EMAIL_BACKEND",
    default="django.core.mail.backends.console.EmailBackend" if DEBUG else "django.core.mail.backends.smtp.EmailBackend",
)
EMAIL_HOST = env("EMAIL_HOST", default="")
EMAIL_PORT = env.int("EMAIL_PORT", default=587)
EMAIL_HOST_USER = env("EMAIL_HOST_USER", default="")
EMAIL_HOST_PASSWORD = env("EMAIL_HOST_PASSWORD", default="")
EMAIL_USE_TLS = env.bool("EMAIL_USE_TLS", default=True)
EMAIL_USE_SSL = env.bool("EMAIL_USE_SSL", default=False)
DEFAULT_FROM_EMAIL = env("DEFAULT_FROM_EMAIL", default="Promise <noreply@promise.app>")

# Firebase Cloud Messaging (FCM)
FCM_ENABLED = env.bool("FCM_ENABLED", default=False)
FIREBASE_CREDENTIALS_JSON = env("FIREBASE_CREDENTIALS_JSON", default="")
FIREBASE_PROJECT_ID = env("FIREBASE_PROJECT_ID", default="")

LANGUAGE_CODE = "en-us"
TIME_ZONE = "Asia/Kolkata"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "apps.authentication.authentication.JWTAccessAuthentication",
    ],
    "DEFAULT_PERMISSION_CLASSES": [
        "rest_framework.permissions.IsAuthenticated",
    ],
    "DEFAULT_RENDERER_CLASSES": [
        "rest_framework.renderers.JSONRenderer",
    ],
    "EXCEPTION_HANDLER": "config.exceptions.api_exception_handler",
}

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "standard": {
            "format": "{asctime} {levelname} {name} {message}",
            "style": "{",
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "standard",
        },
    },
    "root": {
        "handlers": ["console"],
        "level": "INFO",
    },
    "loggers": {
        "django": {
            "handlers": ["console"],
            "level": "INFO",
            "propagate": False,
        },
        "django.request": {
            "handlers": ["console"],
            "level": "ERROR",
            "propagate": False,
        },
        "promise": {
            "handlers": ["console"],
            "level": "INFO",
            "propagate": False,
        },
    },
}

# Gemini AI Provider Configuration
GEMINI_API_KEY_1 = env("GEMINI_API_KEY_1", default="")
GEMINI_API_KEY_2 = env("GEMINI_API_KEY_2", default="")
GEMINI_API_KEY_3 = env("GEMINI_API_KEY_3", default="")
GEMINI_DEFAULT_MODEL = env("GEMINI_DEFAULT_MODEL", default="gemini-3.6-flash")
GEMINI_FAST_MODEL = env("GEMINI_FAST_MODEL", default="gemini-3.6-flash")
GEMINI_TIMEOUT_SECONDS = env.int("GEMINI_TIMEOUT_SECONDS", default=15)
GEMINI_MAX_OUTPUT_TOKENS = env.int("GEMINI_MAX_OUTPUT_TOKENS", default=1024)

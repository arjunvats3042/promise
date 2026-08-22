# ==============================================================================
# Promise Backend Production Dockerfile (Python 3.12 + ASGI Daphne)
# ==============================================================================

FROM python:3.12-slim-bookworm AS builder

# Set build-time environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install system dependencies required for compiling C-extensions (librdkafka, psycopg)
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    librdkafka-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy and install dependencies into a virtual environment
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
RUN pip install -r requirements.txt

# ==============================================================================
# Final Production Runtime Stage
# ==============================================================================
FROM python:3.12-slim-bookworm AS runner

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH" \
    PORT=8000 \
    DJANGO_SETTINGS_MODULE=config.settings.production

WORKDIR /app

# Install runtime libraries only
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    librdkafka1 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy virtualenv from builder
COPY --from=builder /opt/venv /opt/venv

# Create unprivileged application user
RUN groupadd -r promise && useradd -r -g promise -d /app -s /sbin/nologin promise

# Copy application source
COPY --chown=promise:promise . /app

# Prepare directories
RUN mkdir -p /app/staticfiles && chown -R promise:promise /app

# Switch to non-root user
USER promise

EXPOSE 8000

# Default entrypoint starts Daphne ASGI web server
CMD ["sh", "-c", "daphne -b 0.0.0.0 -p ${PORT:-8000} config.asgi:application"]

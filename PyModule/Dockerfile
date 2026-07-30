FROM python:3.13.14-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

RUN groupadd --system cafehelp \
    && useradd --system --gid cafehelp --home-dir /app cafehelp

COPY requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock

COPY --chown=cafehelp:cafehelp . .
RUN mkdir -p /app/models \
    && chown -R cafehelp:cafehelp /app/models

USER cafehelp

EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

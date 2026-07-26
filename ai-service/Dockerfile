FROM python:3.12-slim

# uv 바이너리 복사
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /app
COPY . .

# 런타임 의존성만 설치 (dev 제외), lock 고정
RUN uv sync --no-dev --frozen

EXPOSE 8000

CMD ["uv", "run", "--no-dev", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

# Dockerfile
# Лёгкий сервис без torch/diffusers — всё тяжёлое делает ComfyUI отдельно

FROM python:3.11-slim

WORKDIR /app

# Устанавливаем curl для удобной отладки (проверка COMFY_BASE_URL из контейнера)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Зависимости Python (только FastAPI и requests, без torch/diffusers)
COPY requirements.txt .

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

# Код приложения
COPY . .

# Директория для изображений и workflow'ов
RUN mkdir -p /data/frames /app/workflows

# Переменные окружения
ENV PYTHONUNBUFFERED=1 \
    STORAGE_DIR=/data/frames \
    IMAGE_URL_BASE=http://localhost:8000/images \
    COMFY_BASE_URL=http://comfyui:8188 \
    WORKFLOWS_DIR=/app/workflows

EXPOSE 8000

# Запуск FastAPI
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]

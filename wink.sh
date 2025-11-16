#!/usr/bin/env bash
set -euo pipefail

# Переходим в директорию, где лежит сам скрипт (и docker-compose.yml)
cd "$(dirname "$0")"

echo "== Получаем внешний IP =="

# Пытаемся получить внешний IP с нескольких сервисов
get_external_ip() {
  local ip=""

  # 1 вариант
  ip=$(curl -s https://api.ipify.org || true)
  if [[ -n "$ip" ]]; then
    echo "$ip"
    return 0
  fi

  # 2 вариант
  ip=$(curl -s https://ifconfig.me || true)
  if [[ -n "$ip" ]]; then
    echo "$ip"
    return 0
  fi

  # 3 вариант
  ip=$(curl -s http://icanhazip.com || true)
  if [[ -n "$ip" ]]; then
    echo "$ip"
    return 0
  fi

  return 1
}

EXTERNAL_IP=$(get_external_ip || echo "")

if [[ -z "$EXTERNAL_IP" ]]; then
  echo "❌ Не удалось получить внешний IP"
  exit 1
fi

echo "Внешний IP: $EXTERNAL_IP"

echo "== Обновляем .env (SERVER_IP) =="

# Если .env существует — обновляем SERVER_IP, иначе создаём
if [[ -f .env ]]; then
  if grep -qE '^SERVER_IP=' .env; then
    # Обновляем существующую строку
    sed -i "s/^SERVER_IP=.*/SERVER_IP=$EXTERNAL_IP/" .env
  else
    # Добавляем новую строку
    echo "SERVER_IP=$EXTERNAL_IP" >> .env
  fi
else
  echo "SERVER_IP=$EXTERNAL_IP" > .env
fi

echo "✅ SERVER_IP обновлён в .env"

echo "== Ищем docker compose / docker-compose =="

DC=""

if command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
elif command -v docker >/dev/null 2>&1; then
  # Проверяем, поддерживает ли docker подкоманду compose
  if docker compose version >/dev/null 2>&1; then
    DC="docker compose"
  fi
fi

if [[ -z "$DC" ]]; then
  echo "⚠️  Docker / docker-compose не найден в PATH."
  echo "Файл .env обновлён, но контейнеры не перезапущены."
  exit 0
fi

echo "Используем: $DC"

echo "== Перезапускаем контейнеры =="

$DC down
$DC up -d

echo "✅ Готово! Контейнеры запущены с SERVER_IP=$EXTERNAL_IP"

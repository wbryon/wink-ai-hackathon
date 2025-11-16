#!/bin/bash

# Получаем внешний IP адрес
EXTERNAL_IP=$(curl -s http://checkip.amazonaws.com)

# Записываем новый IP в файл .env
echo "SERVER_IP=$EXTERNAL_IP" > .env

# Выводим сообщение о том, что IP обновлен
echo "Updated SERVER_IP to $EXTERNAL_IP in .env"

# Если нужно перезапустить контейнеры с новым IP, можно использовать:
docker-compose down
docker-compose up -d
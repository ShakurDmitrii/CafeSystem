#!/bin/sh
# Полный сброс стенда: удаляет БД и файлы, при следующем запуске заново создаются тестовые данные.
set -e
cd "$(dirname "$0")"
docker-compose down -v
docker-compose up -d
echo "Сброшено. Через 1-3 минуты откройте http://localhost:3000"

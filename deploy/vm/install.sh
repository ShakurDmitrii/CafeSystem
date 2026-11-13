#!/bin/sh
# Установка тестового стенда CafeHelp на ВМ. Запуск: sudo sh install.sh
set -e
cd "$(dirname "$0")"

if [ "$(id -u)" != "0" ]; then
  echo "Запустите через sudo: sudo sh install.sh"; exit 1
fi

# 1. docker-compose 1.29.2 (в Astra его нет, а Docker 18.09 не знает 'docker compose')
if ! command -v docker-compose >/dev/null 2>&1; then
  echo "== Устанавливаю docker-compose 1.29.2 в /usr/local/bin"
  install -m 0755 docker-compose-Linux-x86_64 /usr/local/bin/docker-compose
fi
docker-compose version --short

# 2. Образы
echo "== Загружаю образы (пара минут)"
gunzip -c cafehelp-images.tar.gz | docker load

# Пароли стенда генерируются один раз
rnd() { tr -dc 'a-zA-Z0-9' </dev/urandom | head -c "$1"; }
if [ ! -f .env ]; then
  echo "== Генерирую .env с паролями стенда"
  cat > .env <<ENV
DB_PASSWORD=$(rnd 20)
MINIO_ROOT_USER=cafehelp
MINIO_ROOT_PASSWORD=$(rnd 24)
SECURITY_JWT_SECRET=$(rnd 96)
INTERNAL_SERVICE_TOKEN=$(rnd 40)
ENV
fi

# 3. Права на init-скрипты БД (Postgres в контейнере должен их прочитать)
chmod -R a+rX initdb
chmod 600 .env

# 4. Запуск
echo "== Запускаю контейнеры"
docker-compose up -d

echo "== Жду backend (первый запуск: создание БД и тестовых данных, 1-3 минуты)"
i=0
health() {
  if command -v curl >/dev/null 2>&1; then curl -sf http://localhost:8080/api/health >/dev/null 2>&1
  else wget -q -O /dev/null http://localhost:8080/api/health; fi
}
until health; do
  i=$((i+1))
  if [ $i -gt 90 ]; then
    echo "Backend не поднялся за 6 минут. Логи: sudo docker-compose logs --tail=100 backend db"; exit 1
  fi
  sleep 4
done

echo
echo "Готово! Откройте в браузере ВМ:  http://localhost:3000"
echo "  Владелец: admin / admin123"
echo "  Кассир:   kassir / kassir123"

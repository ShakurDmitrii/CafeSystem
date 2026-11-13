#!/bin/sh
# Создаёт налоговую БД рядом с основной (экономим память ВМ: один Postgres вместо двух)
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE taxdb"
psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d taxdb -f /docker-entrypoint-initdb.d/tax-schema.sql.txt

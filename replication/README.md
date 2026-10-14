# Асинхронная репликация master → slave (PostgreSQL 16, CafeHelp)

## Схема

| Роль | Контейнер | Порт на хосте | Режим |
|---|---|---|---|
| master | `cafehelp-db` (сервис `db` из `docker-compose.yml`) | 5432 | чтение и запись |
| slave (replica) | `cafehelp-db-replica` (`docker-compose.replication.yml`) | 5434 | hot standby, только чтение |

Тип репликации — **физическая потоковая (streaming replication)**. Master пишет изменения в WAL, процесс `walsender` передаёт WAL реплике, а её `walreceiver` применяет его.
Репликация **асинхронная**: `synchronous_standby_names` пустой, поэтому master подтверждает COMMIT, не дожидаясь реплики (`sync_state = async`).

## Что настроено

**Master** (`replication/enable-replication.ps1`):
1. `wal_level = replica`, `max_wal_senders = 10`. В `postgres:16` это значения по умолчанию, их достаточно.
2. Роль `CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD ...`.
3. Физический слот `pg_create_physical_replication_slot('replica1')`. Пока реплика не получила WAL, master его не удаляет.
4. Строка в `pg_hba.conf`: `host replication replicator all scram-sha-256` и `pg_reload_conf()`.

**Slave** (`docker-compose.replication.yml`):
1. При пустом томе выполняется базовая копия master:
   `pg_basebackup -h db -U replicator -D $PGDATA -Fp -Xs -R -S replica1`.
   Флаг `-R` создаёт `standby.signal` и прописывает `primary_conninfo` в `postgresql.auto.conf`.
2. Запуск `postgres -c hot_standby=on`: реплика принимает SELECT.

## Запуск

```powershell
docker start cafehelp-db
powershell -File replication/enable-replication.ps1
docker compose -f docker-compose.replication.yml up -d
powershell -File replication/check-replication.ps1
```

## Результат проверки (`check-replication.ps1`)

```
=== MASTER: pg_stat_replication ===
 application_name | client_addr |   state   | sync_state
 walreceiver      | 172.18.0.3  | streaming | async

=== REPLICA: pg_stat_wal_receiver ===
 is_replica |  status   | sender_host | slot_name
 t          | streaming | db          | replica1

=== Данные CafeHelp на master и replica ===
 dishes | clients | orders
    167 |       5 |    120          (одинаково на обоих)

=== INSERT на master -> SELECT на replica ===
  2 | hello from master | 2026-10-14 17:46:11+00   (строка появилась на реплике)

=== INSERT на replica ===
ERROR:  cannot execute INSERT in a read-only transaction

=== Отставание ===
 write_lag 00:00:00.0002 | flush_lag 00:00:00.0009 | replay_lag 00:00:00.0009
```

## Остановка / удаление реплики

```powershell
docker compose -f docker-compose.replication.yml down -v        # удалить реплику и её том
docker exec cafehelp-db psql -U postgres -c "SELECT pg_drop_replication_slot('replica1');"
```
Слот нужно удалить, если реплика больше не нужна. Иначе master будет копить WAL бесконечно.

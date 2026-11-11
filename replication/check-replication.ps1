# Проверка репликации: статус, совпадение данных, запись на master -> чтение на replica, запрет записи на replica
function Master($sql) { docker exec cafehelp-db psql -U postgres -c $sql }
function Replica($sql) { docker exec cafehelp-db-replica psql -U postgres -c $sql }

Write-Host "`n=== 1. MASTER: подключённые реплики ==="
Master "SELECT application_name, client_addr, state, sync_state, sent_lsn, replay_lsn FROM pg_stat_replication;"

Write-Host "=== 2. REPLICA: режим восстановления и источник ==="
Replica "SELECT pg_is_in_recovery() AS is_replica, status, sender_host, slot_name FROM pg_stat_wal_receiver;"

Write-Host "=== 3. Данные CafeHelp совпадают (master / replica) ==="
$q = "SELECT (SELECT count(*) FROM sales.dish) AS dishes, (SELECT count(*) FROM sales.client) AS clients, (SELECT count(*) FROM sales.""order"") AS orders;"
Master $q; Replica $q

Write-Host "=== 4. INSERT на MASTER ==="
Master "CREATE TABLE IF NOT EXISTS public.replication_check (id serial PRIMARY KEY, note text, created_at timestamptz DEFAULT now());"
Master "INSERT INTO public.replication_check(note) VALUES ('hello from master') RETURNING *;"
Start-Sleep -Milliseconds 500

Write-Host "=== 5. Строка читается на REPLICA ==="
Replica "SELECT * FROM public.replication_check ORDER BY id DESC LIMIT 1;"

Write-Host "=== 6. Запись на REPLICA запрещена ==="
Replica "INSERT INTO public.replication_check(note) VALUES ('from replica');"

Write-Host "=== 7. Отставание реплики ==="
Master "SELECT application_name, write_lag, flush_lag, replay_lag FROM pg_stat_replication;"

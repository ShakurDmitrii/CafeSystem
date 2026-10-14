# Готовит master (cafehelp-db) к репликации. Повторный запуск безопасен.
# wal_level=replica, max_wal_senders=10 и hot_standby=on в postgres:16 уже стоят по умолчанию - менять конфиг не нужно.
$ErrorActionPreference = 'Stop'
$password = if ($env:REPLICATION_PASSWORD) { $env:REPLICATION_PASSWORD } else { 'repl_pass' }

function Invoke-MasterSql($sql) { docker exec cafehelp-db psql -U postgres -v ON_ERROR_STOP=1 -tAc $sql }

# 1. Роль с правом REPLICATION
Invoke-MasterSql @"
DO `$`$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'replicator') THEN
    CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '$password';
  END IF;
END `$`$;
"@

# 2. Физический слот репликации
Invoke-MasterSql "SELECT pg_create_physical_replication_slot('replica1') WHERE NOT EXISTS (SELECT FROM pg_replication_slots WHERE slot_name = 'replica1');" | Out-Null

# 3. Разрешаем replication-подключения из docker-сети по паролю
# (внутри только одинарные кавычки: PowerShell 5.1 ломает двойные в аргументах native-команд)
docker exec cafehelp-db sh -c 'grep -q ''^host replication replicator'' $PGDATA/pg_hba.conf || echo ''host replication replicator all scram-sha-256'' >> $PGDATA/pg_hba.conf'
Invoke-MasterSql 'SELECT pg_reload_conf();' | Out-Null

Write-Host 'master ready: role replicator, slot replica1, pg_hba updated'

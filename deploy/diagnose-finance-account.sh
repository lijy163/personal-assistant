#!/usr/bin/env bash
set -Eeuo pipefail

BACKEND_CONTAINER="${BACKEND_CONTAINER:-personal-assistant-backend}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-personal-assistant-postgres}"

echo "=== backend finance errors (last 30 minutes) ==="
docker logs --since 30m "$BACKEND_CONTAINER" 2>&1 | grep -E -A 20 -B 5 '资金账户保存失败|finance_account|DataIntegrityViolation|PSQLException' || true

echo "=== finance_account database state ==="
docker exec "$POSTGRES_CONTAINER" sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT con.conname, con.contype, pg_get_constraintdef(con.oid) AS definition
FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid
WHERE rel.relname = 'finance_account' ORDER BY con.conname;
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'finance_account' ORDER BY indexname;
SELECT MAX(id) AS max_account_id, pg_get_serial_sequence('finance_account', 'id') AS sequence_name FROM finance_account;
SELECT sequencename, last_value FROM pg_sequences
WHERE schemaname = current_schema()
  AND sequencename = split_part(pg_get_serial_sequence('finance_account', 'id'), '.', 2);
SELECT id, author, dateexecuted, exectype FROM databasechangelog
WHERE id IN ('033-fix-finance-account-type-uniqueness', '034-repair-finance-account-sequence') ORDER BY orderexecuted;
SQL

echo "=== postgres errors (last 30 minutes) ==="
docker logs --since 30m "$POSTGRES_CONTAINER" 2>&1 | grep -E -A 8 -B 3 'ERROR|DETAIL|finance_account' || true

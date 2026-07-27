#!/bin/sh
set -eu

[ "${RESTORE_CONFIRM:-NO}" = "YES" ] || { echo "Set RESTORE_CONFIRM=YES to continue" >&2; exit 2; }
ARCHIVE="${1:?Usage: restore.sh /app/backup/personal-assistant-*.tar.gz}"
[ -f "$ARCHIVE" ] || { echo "Backup archive not found: $ARCHIVE" >&2; exit 2; }
DB_HOST="${DB_HOST:-postgres}"; DB_PORT="${DB_PORT:-5432}"; DB_NAME="${POSTGRES_DB:-personal_assistant}"; DB_USER="${POSTGRES_USER:-assistant}"; FILES_DIR="${FILES_DIR:-/app/files}"
export PGPASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
tar -C "$WORK" -xzf "$ARCHIVE"
pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner "$WORK/database.dump"
if [ -f "$WORK/files.tar.gz" ]; then mkdir -p "$FILES_DIR"; tar -C "$FILES_DIR" -xzf "$WORK/files.tar.gz"; fi
echo "Restore completed from $ARCHIVE"
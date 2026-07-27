#!/bin/sh
set -eu

BACKUP_DIR="${BACKUP_DIR:-/app/backup}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
FILES_DIR="${FILES_DIR:-/app/files}"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-personal_assistant}"
DB_USER="${POSTGRES_USER:-assistant}"
export PGPASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

STAMP="$(date +%Y%m%d-%H%M%S)"
WORK="$BACKUP_DIR/.work-$STAMP"
ARCHIVE="$BACKUP_DIR/personal-assistant-$STAMP.tar.gz"
mkdir -p "$WORK" "$BACKUP_DIR"
trap 'rm -rf "$WORK"' EXIT

pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -Fc -f "$WORK/database.dump"
if [ -d "$FILES_DIR" ]; then
  tar -C "$FILES_DIR" -czf "$WORK/files.tar.gz" .
fi
cat > "$WORK/manifest.txt" <<EOF
created_at=$(date -Iseconds)
database=$DB_NAME
files_dir=$FILES_DIR
EOF
tar -C "$WORK" -czf "$ARCHIVE" .
find "$BACKUP_DIR" -maxdepth 1 -name 'personal-assistant-*.tar.gz' -mtime "+$RETENTION_DAYS" -delete
printf 'BACKUP_FILE=%s\n' "$ARCHIVE"
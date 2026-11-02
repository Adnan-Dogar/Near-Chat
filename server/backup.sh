#!/usr/bin/env bash
# Local database backup; move completed backups to encrypted off-server storage.
set -euo pipefail
cd "$(dirname "$0")"
umask 077
mkdir -p backups
stamp=$(date -u +'%Y%m%dT%H%M%SZ')
target="backups/nearchat-${stamp}.dump"
trap 'rm -f "$target"' ERR
sudo docker compose exec -T db pg_dump -U nearchat -d nearchat -Fc > "$target"
[ -s "$target" ] || { rm -f "$target"; echo 'Backup was empty' >&2; exit 1; }
echo "Created $target (unencrypted; restrict access and transfer to encrypted off-site backup)."

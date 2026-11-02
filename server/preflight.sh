#!/usr/bin/env bash
# Read-only checks before deploying; does not require sharing credentials.
set -euo pipefail
cd "$(dirname "$0")"
command -v docker >/dev/null || { echo 'Docker not installed.'; exit 1; }
sudo docker compose version
[ -f .env ] || { echo 'Copy .env.example to .env and set secrets locally.'; exit 1; }
perm=$(stat -c %a .env)
if [ "$perm" != '600' ] && [ "$perm" != '400' ]; then echo "Warning: set .env to 600 (currently $perm)."; exit 1; fi
set -a
# Read a single known key without executing the env file as a shell program.
domain=$(sed -n 's/^CHAT_DOMAIN=//p' .env | tail -n1 | tr -d '\r')
if [ -z "$domain" ] || [ "$domain" = 'chat.example.net' ]; then echo 'Set CHAT_DOMAIN to a real public hostname.'; exit 1; fi
if ! [[ "$domain" =~ ^[a-zA-Z0-9.-]+$ ]]; then echo 'Invalid hostname.'; exit 1; fi
getent ahostsv4 "$domain" >/dev/null || { echo 'Hostname does not resolve to IPv4 yet.'; exit 1; }
sudo docker compose config -q
echo "Ready for installation checks: $domain resolves, Compose config parses. Confirm public TCP 80/443 and your SSH firewall rules before startup."

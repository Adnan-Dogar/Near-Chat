# Near-Chat v0.8 self-hosted upgrade — Ubuntu 22.04 / Contabo (8GB RAM / 2 vCPU)

## What v0.8 adds
- Real server-side message edit (author only, within 15 minutes), deletion (author only), emoji reactions (6 supported emoji), and message search (auth + chat membership required).
- Per-user chat mute and pin, server-stored profiles with display name and bio, block/unblock, reporting.
- Android interface for these options; long-press messages for reactions/edit/delete.
- Build and contract/security test workflows, database schema migration statements, local backup and deployment preflight scripts.

**This is an alpha. No real voice/video calling, media upload, E2EE, account recovery, push notifications, or broad public content moderation.** The group features are limited and need end-to-end mobile testing.

## Preparing your VPS without sharing passwords
1. Get the **public IPv4** from the Contabo panel and create a public DNS hostname (a free DNS subdomain works) that resolves to this IP. Do not share passwords or private keys.
2. Open ports **80/TCP** and **443/TCP** in the Contabo firewall and host firewall, and **keep your current SSH port allowed before enabling the host firewall**.
3. Install Docker Engine and the Compose plugin using the current official Ubuntu instructions: https://docs.docker.com/engine/install/ubuntu/ . Do not run an unreviewed convenience install script on your VPS.
4. On the VPS, use the source branch `selfhosted-v08`, then edit `server/.env` locally: `cp .env.example .env && chmod 600 .env`. Set `CHAT_DOMAIN` and use separate strong random values for `POSTGRES_PASSWORD` and `JWT_SECRET` generated on the VPS with `openssl rand -hex 32`.
5. Check `cd server && ./preflight.sh` and run `sudo docker compose up -d --build` only after confirming hostname/firewall and secrets. Check `sudo docker compose ps` and `curl -fsS https://YOUR_HOSTNAME/healthz`.
6. Install the Android APK from GitHub Actions for the new branch; enter the HTTPS URL only (no trailing path). Register two **test** accounts and verify 2-way messages before inviting others.

### Upgrading an existing v0.7 database
The SQL schema is intentionally additive: `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` and new `CREATE TABLE IF NOT EXISTS` commands. **Take an encrypted off-server backup before updating**. Do not delete the `pgdata` volume. Deploying the newer API runs schema updates on startup; only one API worker is supported.

### Backup / rollback
Run `./backup.sh` from the `server` folder to make a local database dump. Treat dumps as **sensitive plaintext** and copy them promptly to *encrypted* off-server backup storage; test restoring to a separate PostgreSQL instance. A code rollback after a schema migration should be tested in staging. Keep rollback source revisions and backup files outside the public repository.

### Security notes
- Caddy handles HTTPS; Android refuses cleartext HTTP and does not accept certificates with errors. No passwords or tokens should be pasted in chat or committed to GitHub.
- The server can read message contents. This is **not** end-to-end encrypted. Do not promise private encrypted conversations to users.
- No remote VPS actions have been performed. The provided project is ready for administrator-controlled deployment, not currently live on Contabo.
- Debug builds are not production release builds. Production needs stable signing, monitored backups, retention policies, abuse management, and E2EE decisions.

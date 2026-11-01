# Deploy Near-Chat on Contabo — Ubuntu 22.04, 8 GB RAM / 2 vCPU

> Only proceed on a VPS you administer. Never paste root passwords, SSH private keys, JWT secrets or database passwords into ChatGPT/GitHub issues/public source files. These steps are not automatically executed for you.

## 1. Prepare a hostname for TLS

A paid domain is **not necessary**. Register a hostname/subdomain with a DNS provider you trust (a free dynamic DNS hostname can be used for testing). Set its DNS **A record** to your VPS public IPv4. Wait until public DNS resolves correctly. Don't put the VPS public IP, secrets, or SSH credentials in GitHub source code.

Near-Chat **requires a valid HTTPS hostname** in the Android app. Don't use HTTP to transmit passwords/messages. Caddy will request/renew a trusted TLS certificate if ports 80 and 443 are reachable and DNS points correctly.

## 2. Harden the VPS first

From your authorized administrative SSH session:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ca-certificates curl ufw openssl
# Confirm your actual SSH port first (example assumes 22!) and allow it before enabling UFW.
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
# Only enable UFW after verifying SSH access and rules:
# sudo ufw enable
```

For a long-term deployment, set up a separate sudo user, SSH-key login, automatic security updates, audit logging, and backups. Check provider firewall settings as well.

## 3. Install Docker Engine and Compose

Follow Docker's **official Ubuntu installation documentation**, including its GPG key and repository steps:
https://docs.docker.com/engine/install/ubuntu/

Confirm these commands work:

```bash
docker --version
sudo docker compose version
```

Important: Docker-published ports may bypass some UFW rules. The project publishes only the Caddy ports; PostgreSQL and the application API stay on the private Compose network. Apply Contabo's upstream firewall rules as another control.

## 4. Copy the backend into a private server directory

Clone the branch and keep secrets outside public Git tracking:

```bash
git clone --branch selfhosted-vps https://github.com/Adnan-Dogar/Near-Chat.git
cd Near-Chat/server
cp .env.example .env
chmod 600 .env
openssl rand -hex 32
openssl rand -hex 32
```

Replace the `.env` placeholders with your HTTPS hostname and **two different** random secrets (one for the PostgreSQL password and one for JWT signing). Use a local editor on the VPS; do not commit `.env`.

```
CHAT_DOMAIN=chat.yourhostname.example
POSTGRES_PASSWORD=<first 64-char randomly generated hex value>
JWT_SECRET=<second 64-char randomly generated hex value>
```

## 5. Launch / check

```bash
sudo docker compose config -q
sudo docker compose up -d --build
sudo docker compose ps
sudo docker compose logs --tail=60 api caddy
curl -fsS https://chat.yourhostname.example/healthz
```

Expected health response: `{"ok":true}`. If not: check DNS A record, inbound TCP 80/443, reverse proxy logs, date/time and `.env`. Never share full logs before redacting tokens, passwords, public IPs and other secrets.

## 6. Build and test the Android client

The Android source is in `android/`, and the GitHub Actions workflow on the `selfhosted-vps` branch produces `NearChat-selfhosted-debug-APK` if compilation succeeds.

Install the app on two Android phones. Use the exact base URL `https://chat.yourhostname.example` and create **two different** accounts with passwords of at least 12 characters. Send messages and confirm they are shown on both devices. The app currently requires foreground activity for instant WebSocket events and can manually refresh messages.

## 7. Operating and safety

- Don't run a public sign-up campaign yet: this is an alpha without end-to-end encryption, account recovery, full abuse protections, or push notifications.
- Keep Docker images, Ubuntu, Python requirements, and Android packages updated. Pin image digests for production.
- Back up PostgreSQL daily to **encrypted off-server storage**, and test restoration. Docker volumes are not backups.
- To upgrade safely, back up before pulling source and running `sudo docker compose up -d --build`.
- If upgrading to multiple API workers or multiple VPS hosts, add a message broker for cross-worker WebSocket delivery and distributed rate limiting.
- Use stable release signing keys for user-installed Android updates, stored securely outside GitHub source.
- For real calling, budget additional STUN/TURN relay infrastructure; voice/video, notifications and user safety workflows are separate milestones.

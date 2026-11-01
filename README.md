# Near-Chat v0.7 — Contabo Self-Hosted Alpha

This is the first **real online** version of Near-Chat, not the Firebase-free fake-chat preview.

- **Backend:** Python FastAPI + PostgreSQL, password hashing (Argon2id), signed 12-hour access tokens, direct and group chats, persistent text history, authenticated WebSocket live updates, block/report endpoints, basic API throttling.
- **Android:** HTTPS server URL setup, signup/login, encrypted local token using Android Keystore, direct and group chats, sending/reading actual text messages and foreground WebSocket updates. Custom app ID (`com.nearchat.selfhost`) so it installs beside previous demos.
- **Deployment:** Ubuntu 22.04 VPS, Docker Compose, Caddy reverse proxy and automatic HTTPS with a DNS hostname.

**This is an alpha, not a finished public social network.** No Firebase is needed, but **your VPS must be deployed and reachable over HTTPS** for chats to work. The code does not install itself on your server. Your Contabo plan (8 GB RAM / 2 CPU) is enough to begin testing with a small pilot; usage requirements depend on concurrent connections and traffic.

## The code

- `server/`: FastAPI service + schema + Caddy + PostgreSQL + Compose.
- `android/`: Android Studio project for the actual online client, separate from the local preview.
- `.github/workflows/selfhost-android-apk.yml`: builds a debug APK when the `selfhosted-vps` branch is pushed.
- `DEPLOY_UBUNTU.md`: detailed server setup steps, no root passwords needed in chat.

## Implemented online capabilities

| Feature | State |
|---|---|
| Create username/password account | Backend + Android code |
| Login, 12h signed token | Backend + Android code |
| Find people by exact username | Backend |
| One-to-one messaging | Backend + Android code |
| Group messaging (up to 30 invitees) | Backend + Android code |
| PostgreSQL message persistence | Backend |
| Live foreground chat update | WebSocket |
| Block and report user | Backend API, Android UI later |
| Push notifications / calls / E2EE | Not implemented |
| Photo/video/voice upload | Not implemented in the online alpha |
| Password reset, email verification, abuse management | Not implemented |

Messages are protected **in transit by HTTPS/TLS** but **NOT end-to-end encrypted**. The VPS operator can access database message contents. Do not promote this app as private/E2EE. The backend runs **one API worker**; adding more workers will require cross-worker event fan-out (Redis/NATS). In-memory rate limiting is not sufficient for a public launch. Add automated database backups, monitoring, moderation, terms/privacy policy, retention rules, and secure release signing before inviting untrusted users.

## Quick orientation

1. Complete `DEPLOY_UBUNTU.md` on the VPS. Use a domain or free DNS hostname pointing at the VPS public IP. Don't send SSH credentials in chats.
2. Check `https://YOUR_HOSTNAME/healthz` returns `{ "ok": true }`.
3. Download Android APK from the branch's GitHub Actions artifacts (or compile `android/` with Android Studio), install on two devices.
4. Both devices enter `https://YOUR_HOSTNAME`, register different usernames/passwords, start a chat by username, and exchange text messages.

**Security note:** This alpha is not ready for open registration by the general public. Restrict access while validating security, and keep an updated server and regular off-server backups.

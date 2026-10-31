# Near-Chat Preview v0.6 — local feature preview

A working on-device prototype of a modern messaging interface. Installs side-by-side with the Near-Chat online MVP because the app ID is `com.nearchat.preview`. Android 6.0+ (API 23), no Firebase credentials, no internet permission.

## Working offline on one phone

- Save private conversations and demo groups, search chat names and message text
- Write messages, keep drafts across app restarts, reply and react
- Edit your own messages, delete a message, star, forward between local conversations, copy
- Pick photos and PDFs/text files through Android's file picker, open attachments from chat
- Record and play voice notes with microphone permission (stays in app-private storage)
- Manage conversation titles, group member labels, pin/archive/mute/block conversations, clear history
- Export a chat transcript using Android's native share sheet
- Post text status updates that expire after 24 hours locally
- Edit display name and status; toggle light/dark and preview notification preference
- Explore sample calling screen and product roadmap clearly marked as *not connected*

## Not yet functional across devices

This app does not send actual messages to other people. It is not an online messenger or a replacement for WhatsApp. It does not make real calls or share updates online, and the stored messages aren't end-to-end encrypted. Any real online messaging, push notifications, media hosting, abuse controls, account recovery and voice/video infrastructure need backend services, additional programming, and end-to-end security testing.

The original Firebase-backed MVP remains available on the `main` branch of this repository. The local full-featured demo is on `install-preview`.

## APK

GitHub Actions on `install-preview` builds a debug APK and uploads an artifact. Download and extract the artifact, then install `app-debug.apk`. GitHub's debug signing key can differ between builds, so before distributing public updates configure a persistent release signing key.

On an Android phone, allow installation only from sources you trust. If your device rejects installation, look at the exact package installer error and phone's Android version; don't disable device security protections.

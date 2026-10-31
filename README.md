# Near-Chat Android • v0.4 Interactive Demo

An online-chat Android MVP with a **fully navigable local demo** that needs no Firebase login, API keys, or internet connection. The primary build is a **demo**, not a real messaging service.

## Try without Firebase

Install the debug APK and open Near-Chat. If no Firebase configuration has been provided, the app goes directly into local Demo Mode with preloaded sample conversations. No setup wall. You can also open Demo Mode from the live sign-in page if Firebase has been configured.

### Demo interactions

- Newly designed teal Near-Chat launcher icon and branded header
- Chat list, search across messages, pinned and unread states
- Conversation pages, local send, saved demo history, reply and emoji reactions
- Create sample contacts and group conversations on this phone
- Pick a photo to preview as a local attachment
- Status screen with interactive sample stories and editable own status
- Calls screen with sample history; voice/video actions clearly **planned**, not active
- Profile, about, appearance (dark/light), notification preference, privacy, plans and reset

## Important limitations

Demo Mode does NOT deliver messages to another phone, sync any chats, notify other users, make calls or run end-to-end encryption. Fake contacts and sample call history are labeled previews. Local demo data is saved in private app preferences, not uploaded.

### Real online messaging

The Firebase-powered v0.3-style one-to-one/small-group messaging code still exists separately in `MainActivity.kt` and activates only when valid Firebase project settings are compiled into the app. See [SETUP_FIREBASE.md](SETUP_FIREBASE.md) and deploy [firestore.rules](firestore.rules). Firestore rules and security, account recovery, moderation, push notifications and encryption require additional work before any public launch.

## Build

GitHub Actions on `main` compiles a debug APK and uploads it as an artifact. The launcher demo works out of the box without configuration. Build uses Android SDK 35, JDK 17, Gradle 8.11.1, Kotlin 2.4 and Firebase Android SDK.

**Roadmap:** validate demo → connect real backend → private chats & identity → notifications & media → safety, blocking and reporting → secure real-time calls → deeper social features.

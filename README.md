# NearTalk · offline Android messaging (v0.2)

NearTalk is an **open-source-ready Android app project** for direct text chats between two nearby phones **without mobile data or Wi-Fi internet**. It uses the Google Play services Nearby Connections API to find phones and exchange encrypted *transport* data over local radios (Bluetooth/BLE/Wi-Fi). This project is a **prototype, not a production-ready secure messenger**, and the APK has not yet been compiled or tested on physical Android devices.

## What's improved in v0.2

- Scan for nearby phones or advertise your own phone.
- Approve a connection with a matching four-digit authentication code on **both** phones.
- Text conversations with local timestamps, sending/sent/delivered/failed statuses.
- **Saved conversations:** messages are stored in the app's private SQLite database and can be reopened even while offline.
- **History button:** open previous conversations. Long-press it to delete the conversation currently displayed on *your* device.
- Stable random app identity exchanged when a verified connection is established; histories are grouped by this locally saved identifier instead of ambiguous display names.
- Local binary wire protocol with a version header, packet size limits, and acknowledgements. Protocol unit tests included.
- No login, no account, no cloud/backend, and no Android `INTERNET` permission.
- GitHub Actions workflow tests and builds an Android debug APK when pushed to a repository.

## Requirements

- Two physical Android devices with working Google Play services, Android 8.0+.
- Bluetooth and Wi-Fi **radios on**, but no Wi-Fi internet or mobile data necessary. Some Android versions also require Location permission / Location services for nearby discovery.
- Both devices running NearTalk and remaining in the foreground, within reliable wireless range.
- To build: Android Studio + Android SDK platform 35 + JDK 17, or GitHub Actions (internet is required *to build and download dependencies*, not to chat).

## Build locally

1. Open this project in Android Studio.
2. Sync Gradle and choose a real Android device.
3. Click **Run** or use **Build > Build APK(s)**.
4. The Gradle project includes `gradle/wrapper/gradle-wrapper.properties`, but not generated `gradlew` / `gradle-wrapper.jar`. The included GitHub Actions workflow uses a preinstalled Gradle command, so the wrapper is not needed there. For a local CLI build, run a locally installed Gradle 8.11.1 or generate the wrapper first.

## Try it on two phones

1. Install the **same APK** on both Android phones. Turn on Bluetooth and Wi-Fi, but disconnect from internet for the offline test.
2. Enter a display name on each device and grant the requested permissions.
3. On A press **Be visible**. On B press **Find nearby**, then tap A's device name.
4. Compare the four-digit codes displayed on both phones; **only accept if they match**.
5. Once connected and introduced, type and send messages. Each phone stores its own copy locally. Tap **History** to view saved chats; long-press it to clear the viewed chat on your phone.

## Create a GitHub repository

A new GitHub repository still needs to be created in your GitHub account. The connector available to this project can push files to existing repositories but does not offer the **Create repository** action. See [PUBLISH_TO_GITHUB.md](PUBLISH_TO_GITHUB.md). The ZIP includes `.github/workflows/build-apk.yml` for free GitHub Actions CI (subject to account quotas/policies).

## Important security and feature limitations

- This is **one-to-one, direct nearby chat** only. **No mesh forwarding, no group chats, no background notifications, no calls, no attachments, no iPhone version, and no messages to people beyond radio range**.
- Nearby provides encryption *on the direct link*. It is not an independently audited end-to-end messaging protocol. Users must compare the pairing code on both devices.
- Message history is held in private app storage but **is not encrypted at rest**. Do not treat NearTalk as suitable for highly sensitive conversations; users with access to an unlocked/compromised device may access local content. Deleting a local chat does not delete copies on other phones.
- Random app identity is **not cryptographically signed** across sessions. A malicious actor could impersonate a prior identity if they successfully pair, so this is not a verified contacts system.
- Delivered means the other app acknowledged storing a message in its app database, not that the person read it.
- There is no guaranteed store-and-forward or offline queued delivery after leaving radio range. The app should remain open while talking.
- Nearby SDK may collect operational diagnostics as documented by Google even though this app has no server.
- Existing source has only been statically inspected and the protocol compiled/tested with Kotlin CLI. **Android compilation, integration testing, and a real-device security review are pending.**

## Project files

- `MainActivity.kt`: UI, permissions, peer discovery, pairing, chat and receipts.
- `WireProtocol.kt`: versioned message format, validated codec.
- `ChatStore.kt`: private SQLite history.
- `activity_main.xml`: native Android interface.
- `.github/workflows/build-apk.yml`: unit tests and debug APK build.

Useful official docs: [Nearby Connections overview](https://developers.google.com/nearby/connections/overview) and [Get started](https://developers.google.com/nearby/connections/android/get-started).

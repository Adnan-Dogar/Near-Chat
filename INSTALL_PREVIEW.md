# Near-Chat Preview — installation compatibility build

This preview is installable **alongside** existing Near-Chat packages. It uses a separate Android application ID `com.nearchat.preview` to avoid signature mismatch conflicts with the previous `com.example.neartalk` APK. It supports Android 6.0+ (API 23) if the device's security policies permit installation.

The preview runs without Firebase configuration and includes fake sample messages and local interactions only. No real online messages are sent in demo mode. You can keep or uninstall your older Near-Chat installation separately.

**Installing:** download `app-debug.apk` from GitHub Actions for the `install-preview` branch. Open it with Android's package installer and allow installation from the source you used. If Android reports an error, note the exact wording and Android version. Do not disable device security features just to install the app.

**Developer note:** GitHub-hosted runners generate a new debug signing key on each fresh runner. For repeatable updates under the *same* application ID, configure a stable release signing key through GitHub secrets and sign release builds. This preview is a separate package, not a final production signing solution.

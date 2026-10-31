# Near-Chat Online: Firebase configuration

The repository builds an APK without Firebase secrets, but that APK shows a configuration screen; online messaging requires your own Firebase project. This is intentional: never ship an app claiming to be online when its backend isn't connected.

1. Open https://console.firebase.google.com/ and create a project.
2. Add an Android app with package name `com.example.neartalk`.
3. In **Authentication > Sign-in method**, enable **Email/Password**.
4. Create **Cloud Firestore** in production mode. In **Firestore > Rules**, replace the default rules with this repo's `firestore.rules` and publish them. Do NOT use allow-all rules.
5. In Firebase Project Settings > Your apps > Android app / SDK setup, locate:
   - **Web API key** (the project's Firebase client API key) -> `FIREBASE_API_KEY`
   - **App ID** (looks like `1:...:android:...`) -> `FIREBASE_APP_ID`
   - **Project ID** -> `FIREBASE_PROJECT_ID`
6. In GitHub, open repository **Settings > Secrets and variables > Actions**. Add these three **repository secrets** with the exact names above. (They're Firebase client configuration, not a password to your account.)
7. Go to **Actions** and run **Build Near-Chat Online APK** on branch `online-mvp` (or after merging into main). Download the `NearChat-online-debug-APK` artifact. Extract it and install `app-debug.apk` on two Android phones.
8. Sign up with two different emails on two devices. Share friend codes, open a chat, then exchange messages over Wi-Fi or cellular internet.

If the build fails, download artifact `NearChat-build-log` and inspect the final `FAILURE: Build failed` section. Do not upload passwords, private signing keys, or service-account JSON to your public repository.

## Important limitations

- Messages are stored in Firestore and are **not end-to-end encrypted**. Do not advertise this as secure as WhatsApp.
- No notifications when the app is closed. No voice/video calling, image sharing, friend blocking, abuse reporting, or social feeds yet.
- Signed-in users can query non-sensitive profiles and their friend codes. Do not put private information in your profile fields.
- Firebase usage has billing and quota limits; monitor them before inviting public users.
- This is an untested online messaging MVP. Test authentication, rules, group joins, and all chat flows on real devices before wider release.

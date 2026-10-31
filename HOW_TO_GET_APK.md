# Get a NearTalk v0.2 debug APK

An APK cannot be generated in this environment because Android SDK and Gradle are not installed. This project includes an automated GitHub Actions build, but it needs a GitHub repository first.

1. Follow [PUBLISH_TO_GITHUB.md](PUBLISH_TO_GITHUB.md) to create and populate the NearTalk repository.
2. In GitHub open **Actions** and choose **Build NearTalk Android APK**.
3. Click **Run workflow** or wait for the workflow triggered by the first commit to `main`.
4. The workflow installs JDK 17, Android SDK platform 35 and Gradle 8.11.1, runs protocol unit tests, and compiles `:app:assembleDebug`.
5. If it succeeds, open the run details and download the `NearTalk-debug-APK` artifact, then unzip the artifact to obtain `app-debug.apk`.
6. Install the APK on two Android phones, then test the offline connection and message flow.

The APK is a debug build, not a store-ready release. If a build fails, use its logs to fix the problem first.

# Upload NearTalk to your GitHub account

**Option A — GitHub's website (no Git CLI required)**

1. While signed in to your GitHub account, open https://github.com/new
2. Choose repository name **NearTalk**, select **Private** for development (you can change it later), and create the repository **without pre-generating a README**.
3. Download/extract `NearTalk_Expanded_GitHub_Ready.zip` (the source ZIP provided with this delivery).
4. On the empty repository's page choose **uploading an existing file** and upload the extracted project contents, preserving `.github/workflows/build-apk.yml`. Web upload support for directories may depend on your browser; the Git CLI below is more reliable.
5. Commit your changes to `main`. Open **Actions > Build NearTalk Android APK > Run workflow**.
6. If tests and Android compilation succeed, download the **NearTalk-debug-APK** artifact in the completed run. Unzip it to obtain `app-debug.apk`.

**Option B — Git CLI (preferred, handles hidden folders)**

1. Create an empty GitHub repository named `NearTalk` as above.
2. Open a terminal inside the extracted NearTalk source folder.
3. Run:

```bash
git init -b main
git add .
git commit -m "NearTalk offline Android prototype v0.2"
git remote add origin https://github.com/YOUR_USERNAME/NearTalk.git
git push -u origin main
```

Use your own GitHub username; authenticate via GitHub's supported method. Do **not** paste credentials into a chat.

**Option C — Git bundle**

A `.bundle` file (provided separately) contains the initial local Git commit. After creating an empty remote:

```bash
git clone NearTalk-v0.2.bundle NearTalk
cd NearTalk
git remote add origin https://github.com/YOUR_USERNAME/NearTalk.git
git push -u origin main
```

## After creating your repository

Send its GitHub link in this conversation. The connected GitHub integration can inspect the repository, help fix CI build errors, and update source files if appropriate.

## Build caveats

- The workflow needs internet to download the Android SDK, Gradle dependencies and CI tooling.
- It creates an **unsigned-for-distribution debug APK** signed with a debug key, suitable only for testing (not an app store release).
- A failed build does **not** produce a usable APK. Check the Actions log, fix the error, rerun.
- The source has not been successfully built in this environment because it has no Android SDK/Gradle installation.

# Release signing & GitHub Secrets

## 1. Create the keystore (one time)

**On Windows (PowerShell):**
```powershell
.\create-keystore.ps1
```

**On macOS/Linux (or Git Bash):**
```bash
chmod +x create-keystore.sh
./create-keystore.sh
```

Credentials are read from **`signing/keystore-credentials`** (ignored by git). If that file doesn’t exist or is incomplete, the script will prompt you for:

- Key alias (default: `paqetng`)
- Keystore password
- Key password (Enter = same as keystore)

**Optional:** create `signing/keystore-credentials` so the script doesn’t prompt (one key=value per line):

```
KEY_ALIAS=paqetng
KEYSTORE_PASSWORD=your-secret-password
KEY_PASSWORD=your-key-password
```

The script creates `signing/release.keystore` and prints the values you need for GitHub.

## 2. GitHub Secrets to add

In your repo: **Settings → Secrets and variables → Actions → New repository secret.**

Add these **4 secrets** (use the values printed by the script):

| Secret name | Description | Example |
|-------------|-------------|---------|
| `ANDROID_KEYSTORE_BASE64` | Entire keystore file, base64-encoded (one long line) | Output of script or `base64 -w0 signing/release.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password | `changeit` (or what you set) |
| `ANDROID_KEY_ALIAS` | Key alias | `paqetng` |
| `ANDROID_KEY_PASSWORD` | Key password | Same as store password unless you set a different one |

After adding these, tagged pushes (e.g. `v1.0`) will build **signed** release APKs and attach them to the GitHub Release.

## 3. Local signed release build

With `signing/release.keystore` present, set the password and run:

```bash
export KEYSTORE_PASSWORD=changeit   # or your password
./gradlew assembleRelease
```

Or point to the keystore and set all four env vars: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

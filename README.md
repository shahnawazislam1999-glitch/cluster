# Raj App Store

One native Android app (Kotlin) that holds all your web apps.

- Opens with a splash screen ("from Raj"), then straight into the **last app you used**.
- Press **Back twice quickly** inside an app to return to the **lobby** and choose another.
- First time you open an app, it downloads from your GitHub Pages link and is saved on the phone. After that it works **offline**.
- Each time you open an app online (at most every 6 hours) it quietly refreshes from GitHub. The new version is used the next time you open it.
- Long-press an app in the lobby to **update now** or **remove** its offline copy.
- Files an app creates (backups, PDFs) are saved to **Downloads/RajAppStore**.

## Get the APK

1. Create a new GitHub repository and push everything in this folder to the `main` branch.
2. Open the **Actions** tab. The workflow **Build Raj App Store APK** runs by itself (about 3 to 5 minutes).
3. Download the APK from either place:
   - **Releases** > **Raj App Store (latest build)** > `RajAppStore.apk`
   - the finished workflow run > **Artifacts** > `RajAppStore-apk`
4. Install it on your phone (allow "install unknown apps" when asked).

Every push builds a new APK, and new builds install over the old one (same signing key).

## Add another app later

Open `app/src/main/assets/apps.json` and add one block to the `apps` list:

```json
{
  "id": "myapp",
  "name": "My App",
  "description": "One line about the app.",
  "url": "https://YOUR-NAME.github.io/YOUR-REPO/",
  "entry": "index.html",
  "files": ["index.html", "manifest.json", "icon-192.png"],
  "externals": [],
  "accent": "#22C55E"
}
```

- `id`: short, lowercase letters/numbers only. Never change it later.
- `files`: every file the app needs, as paths relative to its GitHub Pages link.
- `externals`: full links to outside files the app loads (Google Fonts CSS, CDN libraries). They are saved for offline use too.
- Optional: put a 144px icon at `app/src/main/assets/icons/<id>.png` for the lobby card.

Push, and the new APK includes it.

### Add apps without rebuilding the APK (optional)

Host a copy of `apps.json` somewhere online (for example in a GitHub repo, using its "raw" link) and put that link in the `"remote"` field of `app/src/main/assets/apps.json`. The lobby then picks up new apps from there whenever it is online.

## Notes

- The signing key `app/raj-hub.jks` is included on purpose so every build is signed the same way. This is fine for a personal app. Do not use it for the Play Store.
- Each app is served from its own private address inside the phone, so their saved data never mixes.

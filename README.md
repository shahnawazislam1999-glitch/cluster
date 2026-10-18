# App Cluster

One native Android app (Kotlin) that holds all your web apps.

## Lobby

- **Search** apps by name or description.
- **Favorite** (star) and **pin to top** (pin) any app. Pinned apps always sit at the top.
- **Filters**: All, Favorites, Downloaded, Need download (each shows a count).
- Press and hold an app for more: favorite, pin, update, remove offline copy.
- The shield button opens the **Permission Center**. A red dot means something is still not allowed.

## Permissions

All permissions are listed in one place: `app/src/main/java/com/raj/hub/Perms.kt` (what the screen shows)
and `AndroidManifest.xml` (what Android knows about). The Permission Center opens once on first launch
and any time from the lobby. Tap **Allow everything** to go through them one by one.

Included: notifications, photos/videos/audio, all files access (read and write anywhere), display over other apps,
accessibility service, camera, microphone, location, nearby devices, modify system settings, install unknown apps,
run without battery limits, exact alarms.

Pages inside your apps can also use the camera, microphone and location, and can call
`RajBridge.notify("Title", "Text")` to show an Android notification.

On Android 13 and newer, a sideloaded APK may need: phone Settings > Apps > App Cluster > three dots >
**Allow restricted settings** before Accessibility (and sometimes other special items) can be switched on.

- Opens with a splash screen ("from Raj"), then straight into the **last app you used**.
- Press **Back twice quickly** inside an app to return to the **lobby** and choose another.
- First time you open an app, it downloads from your GitHub Pages link and is saved on the phone. After that it works **offline**.
- Each time you open an app online (at most every 6 hours) it quietly refreshes from GitHub. The new version is used the next time you open it.
- Files an app creates (backups, PDFs) are saved to **Downloads/AppCluster**.

## Get the APK

1. Create a new GitHub repository and push everything in this folder to the `main` branch.
2. Open the **Actions** tab. The workflow **Build App Cluster APK** runs by itself (about 3 to 5 minutes).
3. Download the APK from either place:
   - **Releases** > **App Cluster (latest build)** > `AppCluster.apk`
   - the finished workflow run > **Artifacts** > `AppCluster-apk`
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
- Optional: put a 144px icon at `app/src/main/assets/icons/<id>.png` for the lobby card. Without one, the card shows the app's first letter on its colour.
- Favorites, pins, search and filters work for new apps automatically. No code change needed.

Push, and the new APK includes it.

### Add apps without rebuilding the APK (optional)

Host a copy of `apps.json` somewhere online (for example in a GitHub repo, using its "raw" link) and put that link in the `"remote"` field of `app/src/main/assets/apps.json`. The lobby then picks up new apps from there whenever it is online.

## Notes

- The signing key `app/raj-hub.jks` is included on purpose so every build is signed the same way. This is fine for a personal app. Do not use it for the Play Store.
- Each app is served from its own private address inside the phone, so their saved data never mixes.

## Big video (4 GB MKV) is remembered forever (SHADOWit)

- Tap the video button once and pick the file. The app keeps Android's permission to that file and streams it with seek support.
- If Android refuses to remember the file (some pickers), the app automatically keeps its own copy (needs free space) and shows the progress.
- SHADOWit's `index.html` is **bundled inside the APK** (`app/src/main/assets/sites/shadowit/index.html`) and is never replaced by the older GitHub copy.
  To change the page: replace that file, push, let GitHub build the new APK and install it over the old one.
  To go back to online updates, delete `"bundled": true` for shadowit in `assets/apps.json`.

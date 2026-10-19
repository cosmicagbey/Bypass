# ⚡ Bypass — Floating Video Downloader Overlay for Android

**Bypass** is an Android application designed to eliminate the tedious friction of downloading social media videos. Instead of constantly copying links, switching out of your favorite apps (TikTok, X/Twitter, Facebook, Instagram, etc.), opening a full browser, pasting links, and navigating back, **Bypass floats on top of your screen**.

Whenever you copy a video link, tap the floating **⚡** bubble, choose your platform, and a mini in-app browser opens with your downloader website loaded and your link automatically inserted ready for download. Once downloaded, dismiss the sheet and continue enjoying your content right where you left off.

---

## ✨ Features

- **Floating Overlay Bubble**: Draggable floating bubble powered by Android's `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`. Drag it anywhere along your screen edges.
- **Collapsible Platform Picker**: Tap the bubble to pop open an expandable menu showing your enabled social platforms (TikTok, X, Facebook, and any custom platforms).
- **Custom Platforms & URLs**: Add custom social media platforms with custom names, downloader URLs, accent colors, and emoji icons. Edit or delete anytime.
- **Automatic Clipboard Injection**: When opening a downloader, Bypass inspects your clipboard and uses JavaScript injection to auto-fill the URL into the downloader's input field without manual pasting.
- **Native Download Manager Integration**: Intercepts file download clicks in the mini browser and delegates them to Android's `DownloadManager`, automatically saving videos directly to your device's `Downloads` folder with progress notifications.
- **Non-Intrusive Bottom Sheet Browser**: The downloader opens as an overlay bottom sheet. Tap outside or swipe down to dismiss instantly and return to your social feed.
- **Persistent Service with System Controls**: Foreground service with notification controls to pause or stop anytime, plus an optional "Start on boot" toggle.
- **Material 3 UI**: Clean Jetpack Compose interface with dark mode and light mode support.

---

## 🏗️ Project Architecture

```
Bypass/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bypass/app/
│       │   ├── BypassApp.kt                  # Application class & DB initialization
│       │   ├── MainActivity.kt               # Permission checks & Service toggle
│       │   ├── SettingsActivity.kt           # Social media platform management
│       │   ├── data/
│       │   │   ├── Platform.kt               # Room database entity
│       │   │   ├── PlatformDao.kt            # Room DAO (CRUD & ordering)
│       │   │   ├── PlatformDatabase.kt       # Room SQLite database
│       │   │   └── PlatformRepository.kt     # Repository with default platform seeders
│       │   ├── receiver/
│       │   │   └── BootReceiver.kt           # Auto-starts service on device boot if enabled
│       │   ├── service/
│       │   │   └── FloatingBubbleService.kt  # WindowManager overlay, touch gestures & picker
│       │   ├── ui/
│       │   │   ├── WebViewActivity.kt        # Mini bottom-sheet browser & DownloadManager
│       │   │   ├── screens/
│       │   │   │   ├── HomeScreen.kt         # Service status & quick controls
│       │   │   │   ├── SettingsScreen.kt     # Manage & reorder platforms
│       │   │   │   └── AddEditPlatformScreen.kt # Modal bottom sheet with emoji/color picker
│       │   │   └── theme/
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   └── viewmodel/
│       │       ├── MainViewModel.kt
│       │       └── SettingsViewModel.kt
│       └── res/                              # Layouts, vector drawables, adaptive launcher icons
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Getting Started in Android Studio

1. **Open the Project**:
   - Open **Android Studio** (Hedgehog 2023.1.1 or newer recommended).
   - Click **File > Open...** and select the `c:\Users\bra_s\Documents\Bypass` folder.
   - Wait for Gradle to sync dependencies.

2. **Run on Device or Emulator**:
   - Connect an Android device with USB debugging enabled, or launch an Android Virtual Device (API 26+).
   - Click **Run > Run 'app'** (or press Shift + F10).

3. **Grant Overlay Permission**:
   - When Bypass opens, tap **Grant** on the "Overlay Permission Needed" card.
   - Toggle **Allow display over other apps** for Bypass in Android Settings.
   - Return to Bypass and tap **Start** to spawn the floating bubble.

---

## 📱 How to Use Bypass

1. Open **TikTok**, **X (Twitter)**, **Facebook**, or **Instagram**.
2. Tap **Share** on any video and choose **Copy Link**.
3. Tap the floating **⚡** bubble on your screen.
4. Select the matching platform icon.
5. The mini browser slides up, automatically pre-fills your link, and initiates the downloader page.
6. Tap the download button on the site. Android's DownloadManager will save the video straight to your `Downloads` directory.
7. Tap the dimmed area above the sheet or tap **✕** to immediately return to your video feed!

---

## 🛠️ Adding Custom Platforms

1. Open Bypass and tap **Manage Platforms** (or the settings gear icon in the top right).
2. Tap **+ Add Platform**.
3. Choose an emoji icon and accent color.
4. Enter the platform name (e.g., `Instagram`, `Reddit`, `YouTube`) and the downloader URL (e.g., `https://snapinsta.app`, `https://redditsave.com`).
5. Tap **Add Platform**. The new platform will immediately appear in your floating picker!

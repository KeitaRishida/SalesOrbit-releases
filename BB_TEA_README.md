# BB Tea Malayalam (Unofficial Fan App)

Native Android app for Bigg Boss Malayalam Season 8, optimized for Galaxy S24 FE and Motorola Edge 70 Fusion.

## Features
- Newest YouTube uploads first, sorted using published timestamps (Asia/Kolkata).
- One tap to watch in YouTube, with video thumbnails and creator names.
- Official clips versus commentary / tea.
- Persistent local cache, no accounts or trackers, large easy-to-tap controls, safe system bars.
- In-app update checker and installer, checks SHA-256 before downloading the APK.

## Feed limitation
The app monitors two known YouTube channels, Asianet and BIG BOSS MALLU TALKS, through YouTube Atom feeds. Each channel feed provides about 15 recent uploads (so the app isn't a complete YouTube search index). There is a "See more videos" search link.

## APK
If CI succeeds, download `updates/bb-tea.apk` from this branch. Debug-signed build for personal use; in-place future updates require securely configured persistent signing secrets. The generated eye badge is a fan-inspired icon, not an official Bigg Boss trademark license.

## Build
Use JDK 17, Android SDK 35, Gradle 8.9: `gradle :app:assembleRelease`.

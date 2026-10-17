# BB Tea Malayalam (unofficial fan app)

Native Android viewer for **Bigg Boss Malayalam Season 8**. v1.1.0.

## Discovery

- Search YouTube's date-sorted results for multiple Malayalam, English, and current-season hashtag queries (`#BBM8`, `#BBMS8`, `#BBMS8Promo`, `Season 8`). Reads title, channel, preview description, and relative YouTube publish time.
- Supplement with official and discussion-channel Atom feeds (exact upload timestamps) and a publicly cached feed built periodically by GitHub Actions.
- Reject other seasons/languages; deduplicate by YouTube video ID; newest upload first; preserve valid offline cache. When search pages are blocked, the app shows a meaningful error and offers a YouTube search link.
- Data is a YouTube listing, **not** fact-checked reporting. Search-result relative upload timestamps are approximate; Atom-feed times are exact. The app never invents a publication timestamp for a missing field.
- Public cached feed refresh workflow runs every three hours when the provider is reachable; GitHub schedule delays/outages and YouTube changes are possible. No paid API, tracking, account, or server costs.

## Build

Build via `.github/workflows/bb-tea-android.yml`. Android SDK 35, Gradle 8.9, Java 17. Release uses the reproducible **hobby-only** signing key in `signing/keystore.base64.txt` — **not secure for commercial applications**. The existing v1.0.0 used a different ephemeral signing certificate; Android requires uninstalling v1.0.0 once before installing v1.1.0. Future builds with this key support in-app update checking/installation prompts, subject to Android install-source permission.

## Devices

Optimized for Samsung Galaxy S24 FE and Motorola Edge 70 Fusion, min Android 8.0. Android system bars remain visible and the app uses regular safe content insets.
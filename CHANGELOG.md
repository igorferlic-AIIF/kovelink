# Changelog

## 1.0.0 (2026-10-11) – first KoveLink release, based on KoveMirror build 543 (2026-09-18)

### Added
- **Locked-phone navigation.** `NavNotificationListener` reads the ongoing Google Maps / Waze guidance notification (instruction, road, distance, ETA, manoeuvre icon) into `ExternalNavState`; `KovePresentation` shows it in the TFT navigation banner whenever internal navigation is not running. Mirror → presentation switch now also happens when only external navigation is active.
- Slovenian translation (`values-sl`, all 343 strings) and Slovenian as first entry in the language picker.
- Main screen card "Navigation with the phone locked" with buttons for notification access and battery-optimisation exemption (the permission was declared upstream but never requested).
- Background-location request flow (Android 10+ second step) with explanation dialog.
- Video frame-rate presets 30/24/15 fps (`video_fps` preference) applied to both encoders; default 24.
- GitHub Actions workflow: builds the sideloadable APK on every push, publishes a Release on `v*` tags.

### Security / privacy
- TCP servers refuse to bind to `0.0.0.0` and accept connections only from the bike's gateway (default `192.168.10.1`). Upstream accepted the first client from any device on the same Wi-Fi, i.e. the live phone screen could be streamed to strangers.
- Removed `QUERY_ALL_PACKAGES` (explicit `<queries>` list instead), `usesCleartextTraffic` and `allowBackup`.

### Fixed
- Foreground service declares `location` type and the app requests `ACCESS_BACKGROUND_LOCATION`, so GPS keeps updating the TFT map while the phone is locked on Android 14.
- Wakelock leaked when the service received a second START while running.
- Language button showed "Türkçe" for any unsupported system language; now falls back to English.
- Google Maps and Waze removed from the "app on virtual display" list: Android refuses to launch non-`allowEmbedded` activities on a foreign VirtualDisplay, so the option silently fell back to the internal map.
- Hard-coded Turkish strings in notification label, Wi-Fi status and presentation layout moved to resources.
- Root-warning text no longer talks about non-existent "subscriptions".

### Changed
- App id `si.kovelink.app` (installs side by side with KoveMirror), name KoveLink, version 1.0.0 / code 100; build no longer rewrites `version.properties` on every Gradle run.
- Dead `startControlHeartbeat` stub removed from `TcpServer`.
- Upstream docs moved to `docs/`.

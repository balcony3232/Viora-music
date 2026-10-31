# Viora Music v0.1 (development)

Native Android music player proof of concept (Kotlin, Jetpack Compose, Media3).

## Current implementation
- Audius search and attempt to stream a selected track.
- Media3 foreground-capable playback service and Android media session.
- Basic placeholder home-screen widget.
- GitHub Actions debug APK build on the v0.1-development branch.

## Not implemented yet
- Discord Android Rich Presence (requires SDK verification and integration).
- Functional home-screen widget playback controls and artwork.
- Playlist persistence, favourites, downloads, accounts, recommendations, advanced playback controls.
- Reliable provider fallback, API authentication and licence checks.

## Build
Open GitHub Actions > Android APK, select v0.1-development and run the workflow, or push to that branch. Download the VioraMusic-debug-apk artifact if the workflow succeeds.

This is an experimental source snapshot. APK compilation, playback and device compatibility have not yet been verified.

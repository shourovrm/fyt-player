# DECISIONS

## Current state

Gradle skeleton builds a signed arm64-v8a release APK. Compose + Material 3, single Activity,
theme tokens in place. No contracts, no data layer, no sources, no player yet.

- Module: single `:app`, package `com.fyiplayer.app`, minSdk 26 / targetSdk 35, AGP 8.13, Kotlin 2.4.
- Extraction backend: `youtubedl-android` 0.18.1 (library + ffmpeg). YouTube needs no HTML parsing —
  the engine returns search, metadata and formats as JSON.
- Release-only. Debug variant is never built; there is no debug keystore in this repo.

## Next

- Wave 1: `core/` contracts + `SourceRegistry`, `data/` Room + DataStore, `ui/` AppScaffold + NavHost.
- Wave 2: `engine/` resolver chain, `source/youtube/` end to end, `player/` session + service.
- Wave 3: home/search/detail screens, player chrome + gestures.
- Wave 4: library, downloads, shorts + seek thumbnails + backup.
- Wave 5: clean build, tests, review, arm64 release APK copied to repo root.

## Gotchas

- No device attached to `adb`; end-to-end device verification is outstanding for every wave so far.
- `AndroidManifest.xml` declares a service only in the phase that adds its class — a declaration
  pointing at a missing class is a runtime crash, not a build failure.
- Release APK is ~66 MB at skeleton size; the engine ships a Python runtime. `abiFilters` is pinned
  to `arm64-v8a` because a universal APK triples that.
- `libc++_shared.so` ships in more than one native artifact; `pickFirsts` in `packaging.jniLibs`
  is what keeps packaging from failing.
- Gradle 8.13 wrapper and every dependency version match an already-populated local cache, so a
  cold build downloads nothing. Do not bump versions casually — disk is at 91%.

## Tried / rejected

- `jsoup` dependency — dropped. YouTube extraction goes through the engine's JSON, no markup parsing.
- `biometric` dependency — dropped. No lock feature in the target shape.
- Material-You / dynamic colour — rejected. One deliberate accent; wallpaper never overrides it.
- R8/minify on release — off for now. Keep rules for the engine and Room must land first, and a
  broken release build is worse than a large one.

## Log

- 2026-07-30 | Single `:app` module, no multi-module split | Four layers are enforced by package
  boundaries and review, not by Gradle. Module wiring costs build time and buys nothing yet.
- 2026-07-30 | yt-dlp-class engine as the only YouTube path | No selectors to rot, no signature
  work, and search/detail/formats come from one JSON surface.
- 2026-07-30 | arm64-v8a only, release-only builds | Sideload target is one device class; a debug
  variant would double build output on a disk already at 91%.
- 2026-07-30 | Own signing key generated, 0600, gitignored | Sideloaded updates must keep a stable
  signature; the key never enters git.

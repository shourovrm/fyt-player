# DECISIONS

Index only. Knowledge lives in `docs/decisions/`; read the topic files your task touches.

## Topics
- `docs/decisions/playback.md` — player, session, queue, fullscreen and insets, captions, background service
- `docs/decisions/shorts.md` — pager, shelf, feed, grid
- `docs/decisions/extraction.md` — extractor fork + patches, resolver chain, googlevideo, yt-dlp sources, sign-in
- `docs/decisions/downloads.md` — download queue, stream downloader, export
- `docs/decisions/ui.md` — home, search, detail tabs, library, playlists, settings, backup
- `docs/decisions/build.md` — Gradle, R8, release, project shape, compile quirks, verification backlog
- `docs/decisions/LOG.md` — append-only log; grep only

## Next
- 2026-11-12 v0.3.1: picker track order (original, written, auto; Latin then other scripts), sleep timer moved to bottom row.
- 2026-11-12 v0.3.0: sleep timer (player + Shorts, custom hour/minute wheels), caption sizes 8/10/14/16 pt,
  Shorts captions at top, TTML embedded caption size ignored. See playback.md.
- 2026-11-08 v0.2.47: Shorts ordered by clip date in 3-day slots, round-robin channels; grid stays at top while loading.
- 2026-11-07 v0.2.46: updater downloads the APK matching the device ABI; fullscreen shows video title + channel top-left.
- 2026-11-06 v0.2.45: extractor from the shared fork submodule (kids videos via the embedded player, no SABR; live
  streams from the HLS manifest); kids videos start in ~2 s, embed-blocked ones still 8-10 s. See extraction.md.
- 2026-11-05 v0.2.43: GitHub CI (`.github/workflows/ci.yml` = `just check`), one APK per ABI (arm64-v8a,
  armeabi-v7a, x86_64; release uploads all three), Android 8-10 insets-log crash fixed. Releases: push a vX.Y.Z tag (release.yml).
- 2026-11-05 released v0.2.42 (v0.2.36-42: monochrome + coloured-icon theme, home layouts, channel row, downloads play
  from file, library date groups + selection; see ui.md). Unverified: dark theme, landscape rail, font scale 1.3,
  missing-download-offline error.
- 2026-10-31 released v0.2.34 + v0.2.35 (speed 0.1-3x, sheet fits fullscreen; release APK sha256 = local = installed): wall retry visionos->mweb, per-cause wall copy,
  shade Next/Prev = app rules (device-verified), autoplay switch, History multi-select + undo
  (device-verified), neutral Download button, comment tap fix. Playback on extractor v5.5.0 now
  device-verified (A059, Wi-Fi + mobile data, no VPN).
- Other user's phone (Robi 4G, signed out) gets a wall we can't reproduce; next report: screenshot of
  the new per-cause message tells which wall.
- Open: kids-video 1080p download + pause/resume unverified;
  lintRelease 138 errors.
- History rewritten 2026-10-31 (noreply email, .playwright-mcp dropped); backup bundle at
  ~/fyt-player-pre-rewrite-2026-10-31.bundle. Older backlog: topic files' Open items.

## Gotchas (cross-cutting)
- UI spec = `docs/design-system.md`, copy = `docs/copy.md`; gate tests fail on raw dp/sp/colour
  and on string literals in composables. System JDK is corrupt: `JAVA_HOME=~/.gradle/jdks/temurin-21`.
- Switching the extractor checkout's branch does NOT invalidate Gradle's composite-build output:
  incremental builds kept the old jar (false green on v5.3.1). Always `./gradlew clean` after it.
- Release verification = `adb uninstall` then `adb install`; `install -r` over old data skips yt-dlp python unzip and hides R8 first-run crashes.
- Heavy testing from one IP triggers YouTube's "Sign in to confirm you're not a bot" wall. The app
  correctly classifies it as `AccessChallenge` and stops. It is not a bug, and it is not to be
  worked around with cookies. Wait it out.
- Gradle 8.13 wrapper and every dependency version match an already-populated local cache, so a
  cold build downloads nothing. Do not bump versions casually — disk is at 91%.
- Subagents must not run Gradle, and none of them can verify a build. Every wave so far compiled
  only because the main session built it; expect 1–3 small compile fixes per wave at integration.

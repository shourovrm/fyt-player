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
- 2026-10-31 (v0.2.33 + 8 commits, unreleased): wall retry visionos->mweb, per-cause wall copy,
  shade Next/Prev = app rules (device-verified), autoplay switch, History multi-select + undo
  (device-verified), neutral Download button, comment tap fix. Playback on extractor v5.5.0 now
  device-verified (A059, Wi-Fi + mobile data, no VPN).
- Other user's phone (Robi 4G, signed out) gets a wall we can't reproduce; next report: screenshot of
  the new per-cause message tells which wall. Speed control 0.1-3x: mock at .design-mockups/speed.html.
- Open: Samsung A13 install fails (need `getprop ro.product.cpu.abilist`; armeabi-v7a build?);
  "10 Levels of Jev" download shows Failed; kids-video 1080p download + pause/resume unverified;
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

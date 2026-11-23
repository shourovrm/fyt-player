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
- 2026-11-23 (unreleased, on A059 as a RELEASE build of 0.3.9, so no run-as trace: read Settings > Playback log): search box rework + Back
  through earlier searches (ui.md); 50 s buffer, media3 transport retries, exact resume after an error (playback.md). Open: slow-delivered
  Short, FGS crash, rebuffer classification (playback.md Open items).
- 2026-11-20 v0.3.9: player bottom row 36 dp above the seek line with 20 dp glyphs; resume pill = small "Resume" in the
  dot's colour, centred over the dot (device-verified A059 portrait, light). Fullscreen and dark theme not looked at. See playback.md.
- 2026-11-20 v0.3.8: more transport retries before "No connection" (loading 1/2/4 s, playback 0/2/5 s). v0.3.7 trace: media host stalls
  10-70 s on stable Wi-Fi, reason unknown; fix NOT verified against a real stall. Phone runs the debug build: on the next error read `playback_trace.txt` via run-as (build.md).
  Extractor rebase onto upstream main (33 commits) still open (extraction.md).
- 2026-11-20 v0.3.7: ⌄ minimise lands on the last static page; mini player restores the Detail chain;
  Back skips videos while minimised (device-verified A059 except the Shorts-pager path). See playback.md.
- 2026-11-20 v0.3.6: comment avatars + commenter channel, link-coloured chapter times, one-row channel header with
  picture, Library opens on Channels with pictures (memory-only `ChannelAvatars`). Dark theme only verified. See ui.md.
- 2026-11-20 v0.3.5: resume pill + direct resume on return, seek-bar edge drag, rebuffer trace lines, blue links,
  saved Shorts feed (see shorts.md). Open: "more pausing" report
  not reproduced (starts median 1.6 s, 1 transport error in 23 h of trace); read `rebuffer` / `paused by player` lines in the next Playback report. See playback.md.
- 2026-11-14 v0.3.4 (device-verified A059 in part): resume marker instead of auto-resume, end-of-video sleep timer,
  speed card + outlined speed button, suggestions from www.google.com, saved For you feed at start. Open: Honor 200 fullscreen button. See playback.md, ui.md.
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

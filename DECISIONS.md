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
- 2026-10-28 SABR/kids-video round + extractor v5.5.0: built, 598 tests green, NOT installed or
  device-checked. Checklist in extraction.md Open items ("Made-for-kids"). Run it before anything
  else lands on top.
- 2026-10-28 round (icon player bar, dub, chapters, channel header, Shorts grid, recency order, vendored
  extractor in third_party/) is built, 548 unit tests green, installed on the A059 and device-checked for
  controls, action row, description, chapters sheet, dub, channel header. NOT yet confirmed on device:
  History hiding shorts (isShort column, DB v9), shorts slot order, downloads on the vendored extractor.
- v0.2.26 (Material 3 rebuild) and v0.2.27 (Settings hub, copy cut) released UNverified: no
  device attached. First device run: clean install, walk Home/Detail/player controls/Downloads/
  every Settings page at font scale 1.0 and 1.3; ui.md newest entries list what changed.
- Extractor: tree is now v5.5.0 + 2 patches (device-unverified); last device-verified = v5.2.5.
  The v5.3.1 progressive-403 cause is still unknown -- check progressive + downloads first.
- v0.2.25 (segment-start resume) is RELEASED and installed on the A059 but its new path has
  never been exercised on a device. Verify per playback.md newest entry before building on it.
- Three 2026-10-06 waves (review x2 + PipePipe port) are committed; partly device-verified 2026-10-07 -- each topic's newest
  Current state entry lists what was and was NOT verified. Remaining review items: ui.md Open items.
- Shorts age/CAPTCHA wall not reproduced; on the next report read the `wall:` log line first. See shorts.md.
- Shorts in Similar wait on a signed-in watch-next. See shorts.md.
- Device-unverified backlog (v0.2.2 / v0.2.3 items, shorts "all caught up" footer, SponsorBlock skip): build.md, shorts.md, playback.md "Open items".

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

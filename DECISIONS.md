# DECISIONS

Index only. Knowledge lives in `docs/decisions/`; read the topic files your task touches.

## Topics
- `docs/decisions/playback.md` — player, session, queue, fullscreen and insets, captions, background service
- `docs/decisions/shorts.md` — pager, shelf, feed, grid
- `docs/decisions/extraction.md` — extractor fork + patches, resolver chain, googlevideo, yt-dlp sources, sign-in
- `docs/decisions/downloads.md` — download queue, stream downloader, export
- `docs/decisions/ui.md` — home, search, detail tabs, library, playlists, settings, backup
- `docs/decisions/build.md` — Gradle, R8, release, project shape, compile quirks, verification backlog
- `docs/decisions/waves.md` — dated multi-topic snapshots; grep only
- `docs/decisions/LOG.md` — append-only log; grep only

## Next
- Extractor v5.3.1 (`fyt-patches`) NOT shipped: progressive googlevideo URLs 403, PO token suspected. Shipped = v5.2.5 + 2 patches. See extraction.md.
- Shorts age/CAPTCHA wall not reproduced; on the next report read the `wall:` log line first. See shorts.md.
- Shorts in Similar wait on a signed-in watch-next. See shorts.md.
- Device-unverified backlog (v0.2.2 / v0.2.3 items, shorts "all caught up" footer, SponsorBlock skip): build.md, shorts.md, playback.md "Open items".

## Gotchas (cross-cutting)
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

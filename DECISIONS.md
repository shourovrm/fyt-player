# DECISIONS

## Current state

Signed arm64-v8a release APK builds; 94 JVM unit tests green. Compose + Material 3, single Activity.

In: `core/` contracts + `SourceRegistry`; `data/` (Room + DataStore + repositories); `ui/` shell
(AppScaffold + NavHost + placeholder screens); `engine/` (gate, resolver, error mapping, read-only
WebView tier, chain); `source/youtube/`; `player/` (queue maths, format selection, session, media
session service, shared surface). `FyiApp` wires the resolver into `PlaybackSession` and kicks off
engine init.

Also in: home (tabs, search, paging), results list + long-press actions, detail (pinned player
header, metadata, channel tap-through), listing, settings sections, and the full player — chrome,
gestures, quality/speed sheets, mini player, queue bar, seek-thumbnail mapping.

And: library (likes / playlists / history, multi-select, resume bars), playlist detail with
reorder, the download queue + foreground service + downloads screen, the shorts pager, and library
backup (HTML + embedded JSON, export and import over SAF).

Nothing has run on a device. The shorts pager has no feed behind it — see Tried / rejected.

Home feed rebuilt: YouTube's public trending/explore feeds are dead (confirmed live — both
redirect to youtube.com home and the engine reports the playlist gone), so `homepage()` now
throws `Unsupported` honestly instead of hitting a dead URL. Home's default (no search) view is
composed in `HomeViewModel`/`HomeFeed.kt` instead: newest uploads from up to 4 recently-watched
channels (`WatchHistoryEntity.uploaderUrl`, new column, `Migration(1, 2)`), round-robin
interleaved, already-watched excluded, appended incrementally per channel. Cached in the
ViewModel for its lifetime; a refresh icon next to the search pill is the explicit reload (no
pull-to-refresh — smaller diff, same effect). Search is untouched and still per-source-tabbed.

- Module: single `:app`, package `com.fyiplayer.app`, minSdk 26 / targetSdk 35, AGP 8.13, Kotlin 2.4.
- Extraction backend: `youtubedl-android` 0.18.1 (library + ffmpeg). YouTube needs no HTML parsing —
  the engine returns search, metadata and formats as JSON.
- Release-only. Debug variant is never built; there is no debug keystore in this repo.

## Next

Resume point: HEAD is wave 3, working tree clean, release build + 61 tests green. Wave 4 was
started and stopped before any agent wrote a file — nothing partial to recover.

- Wave 4, three parallel workstreams on disjoint files:
  - Library — `ui/Library*`, `ui/Playlist*`, `ui/SavePlaylistSheet.kt`. Likes + playlists + history
    tabs, multi-select, resume bars from `PositionsRepository.observeAll()`. Keep the wired
    signatures `LibraryScreen(onOpenDetail, onOpenPlaylist)` and `PlaylistDetailScreen(id, onOpenDetail)`.
  - Downloads — new `download/` package, `ui/DownloadsScreen.kt`, plus the `dataSync` service
    declaration in the manifest. Must park a running row as resumable on the Android 15 foreground
    timeout, and mux the separate video + audio pair with ffmpeg or a "1080p" download silently
    yields a 360p file.
  - Shorts + backup — `ui/Shorts*`, new `data/backup/`, `settings/BackupSettings.kt`, and the
    `providesShorts` decision in `source/youtube/`. Backup is one HTML file with the machine copy
    in a `<script type="application/json">` block; additive and idempotent on import.
- Wave 5: clean build, tests, whole-branch review, arm64 release APK copied to repo root.
- Still outstanding at every wave: nothing has run on a device. No `adb` device was attached.
- Not built and not scheduled: engine self-update (`EngineUpdater`). Extractors rot, so this
  matters before the app is usable long-term. Verified API for it is noted in Gotchas.

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
- `material-icons-core` 1.7.8 has no Download / Pause / SkipNext / SkipPrevious / Fullscreen /
  Shuffle glyph, and `Icons.Filled.List` is deprecated in favour of `Icons.AutoMirrored.Filled.List`.
- Persisted rows carry no `remoteId`; entity → `VideoRef` mappers set `remoteId = pageUrl`. A saved
  video is always re-resolved from its page URL, so nothing downstream may treat `remoteId` as a
  platform id when the ref came out of the database.
- `AppScaffold` consumes system-bar insets for the whole app. A future full-bleed surface
  (fullscreen player, shorts pager) has to opt out there, not pad itself.
- `extractNativeLibs=true` is NOT in the manifest — AGP emits it from `packaging.jniLibs
  .useLegacyPackaging = true`. The engine cannot unpack its payload without it, so if native init
  ever starts failing, check the merged manifest before anything else.
- Kotlin: `private companion object` is illegal inside a standalone `object`. Use a private
  top-level val in the same file.
- Search paging refetches cumulatively: page N asks the engine for N×pageSize results and drops the
  earlier ones. The search protocol takes a count, not an offset. Real listings page properly with
  `--playlist-start`/`--playlist-end`.
- Storyboard tile interval is derived as `duration / tileCount`, not published by the engine. Scrub
  previews may drift on very long videos until measured on a device.
- `Listing.key` is assumed to be a full channel/playlist URL, because that is what `detail()` puts
  there. Anything else constructing a `Listing` must honour that or `listing()` breaks.
- `Protocol.DASH` throws in `MediaItemFactory`: `media3-exoplayer-dash` is not a dependency and
  nothing emits DASH yet. Adding a DASH path means adding that artifact first.
- There is exactly ONE shared video surface. `AppScaffold` therefore hides the mini player and
  queue bar on the detail route — mounting both would have the mini bar steal the surface from the
  full player mid-playback.
- `Modifier.padding` has no `horizontal` + `top` overload; name all four edges instead.
- Overriding Java's `LinkedHashMap.removeEldestEntry` from Kotlin needs
  `MutableMap.MutableEntry`, not `MutableMap.Entry`.
- `Prefs.backgroundPlayback` has a settings row but nothing in `player/` reads it yet.
- Engine self-update API, confirmed against the artifact's bytecode: `YoutubeDL.version(context)`,
  `YoutubeDL.updateYoutubeDL(context, channel)`, and `YoutubeDL.UpdateChannel._STABLE` /
  `._NIGHTLY` / `._MASTER` — the leading underscore is the real API, not a typo.
- Subagents must not run Gradle, and none of them can verify a build. Every wave so far compiled
  only because the main session built it; expect 1–3 small compile fixes per wave at integration.
- A Kotlin `public` property may not expose an `internal` type. Several state holders hit this;
  mark the property `internal` rather than widening the type.
- Only ONE screen may hold the shared video surface at a time. `AppScaffold.isFullPlayerRoute`
  gates the mini player and queue bar off those routes — add any new full-bleed route to it.
- Downloads go through `DownloadQueue.enqueue`, never a hand-built repository row: the queue is
  what pairs video-only with audio-only and what starts the service. The single highest-`height`
  format is usually video-only, so picking it directly yields a silent, audio-less file.
- Finished downloads open via `FileProvider` (`${applicationId}.files`, `res/xml/file_paths.xml`).
  `ACTION_VIEW` on a `file://` Uri throws `FileUriExposedException` at this targetSdk.
- The exported backup page says in prose that no cookies are stored, so a naive whole-document
  scan for "cookie" matches that sentence. The security test scans the JSON payload block instead.
- `HomeViewModel.homeResults`/`loadHome` (per-source browse tabs) are gone: `homepage()` always
  throws now, so that machinery was dead weight. `retryTab`/`continueTab` lost their `isSearching`
  param — they only ever act on `searchResults` today. Re-add per-source browse tabs only if a
  future platform actually implements `homepage()`.

## Tried / rejected

- YouTube `/feed/trending` and `/feed/explore` as Home's source — dead. Both redirect to
  youtube.com home and error "the channel/playlist does not exist" (verified live).
- Mix/radio playlists (`watch?v=X&list=RDX`) as a Home feed source — rejected by the extractor
  ("Unable to recognize playlist"), confirmed live. Not an option for any feed.
- Pull-to-refresh (Material3 `PullToRefreshBox`) for the Home feed — skipped for a plain refresh
  icon button next to the search pill; same effect, no experimental-API surface.
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
- 2026-07-30 | Shorts feed left off (`providesShorts = false`) | No feed URL could be verified
  without a live run. An honest empty state beats a feed that silently returns the wrong thing.
- 2026-07-30 | Playback pairs video-only + audio-only via `MergingMediaSource` | The platform caps
  muxed streams low; playing only muxed would be a permanent, visible quality regression.
- 2026-07-30 | Resolution ceiling picked by metered-ness, not radio type | A metered hotspot is
  billed like mobile data even though it reports as wifi.
- 2026-07-31 | Backup carries no timestamps, only page URLs and display text | Skipping them is
  what makes re-import strictly idempotent: match on page URL, skip if present, never overwrite.
- 2026-07-31 | Playlist reorder writes `sortIndex` in place | Delete-and-re-add rewrote `addedAt`
  for every row and cost two writes per item per tap.
- 2026-07-31 | Shorts pager shipped without a feed | The pager is finished and correct; the source
  side stays off until a feed URL can be checked against a live run. It renders an honest empty
  state, which is the same thing it would render if the platform stopped publishing one.
- 2026-07-31 | Home feed = newest uploads from watched channels, not trending | Trending is dead
  platform-side (verified live); watch history already tells us what a user actually cares about.
- 2026-07-31 | `watch_history.thumbnailUrl` stripped to bare path before insert | Listing
  thumbnails carry a signature query that expires; only the unsigned path form is worth persisting.

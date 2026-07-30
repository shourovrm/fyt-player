# DECISIONS

## Current state

Signed arm64-v8a release APK builds; 37 JVM unit tests green. Compose + Material 3, single Activity.

In: `core/` contracts + `SourceRegistry`; `data/` (Room + DataStore + repositories); `ui/` shell
(AppScaffold + NavHost + placeholder screens); `engine/` (gate, resolver, error mapping, read-only
WebView tier, chain); `source/youtube/`; `player/` (queue maths, format selection, session, media
session service, shared surface). `FyiApp` wires the resolver into `PlaybackSession` and kicks off
engine init.

Not in: real screen bodies (every screen still renders a placeholder), player chrome and gestures,
library, downloads, shorts, seek thumbnails, backup. Nothing has run on a device.

- Module: single `:app`, package `com.fyiplayer.app`, minSdk 26 / targetSdk 35, AGP 8.13, Kotlin 2.4.
- Extraction backend: `youtubedl-android` 0.18.1 (library + ffmpeg). YouTube needs no HTML parsing —
  the engine returns search, metadata and formats as JSON.
- Release-only. Debug variant is never built; there is no debug keystore in this repo.

## Next

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
- 2026-07-30 | Shorts feed left off (`providesShorts = false`) | No feed URL could be verified
  without a live run. An honest empty state beats a feed that silently returns the wrong thing.
- 2026-07-30 | Playback pairs video-only + audio-only via `MergingMediaSource` | The platform caps
  muxed streams low; playing only muxed would be a permanent, visible quality regression.
- 2026-07-30 | Resolution ceiling picked by metered-ness, not radio type | A metered hotspot is
  billed like mobile data even though it reports as wifi.

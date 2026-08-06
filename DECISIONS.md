# DECISIONS

## Current state

Signed arm64-v8a release APK builds; 203 JVM unit tests green. Compose + Material 3, single Activity.

2026-08-06 wave: NewPipeExtractor v0.26.4 is BOTH tier-0 of the resolver chain AND the YouTube
VideoSource (`source/newpipe/NewPipeYoutubeSource`, id stays "youtube"): search, channel tabs,
playlists, detail, comments, seek thumbnails, shorts (providesShorts=true now). yt-dlp keeps
downloads, searchChannel (delegated — NewPipe has no channel-scoped search) and resolver fallback.
Paging via PageToken (JSON-serialized NewPipe Page). List cells now carry real
views/age/uploader (device-verified). Library gained a Channels tab (subscriptions, multi-select
unsubscribe) and followed remote playlists (schema v4, `followed_playlists`, merged into the
Playlists tab). Search channel rows navigate to the channel screen. Queue append works after
queue exhaustion and toasts feedback. Background playback works: `PlaybackSession.play()`
starts `PlaybackService` (media3 MediaSessionService), notification/lockscreen/Bluetooth controls,
`Prefs.backgroundPlayback` honored reactively (pause on ON_STOP when off). Captions: `Resolved.
captions` → `SingleSampleMediaSource` merged per track, off by default, CC button + `CaptionSheet`
picker, selection survives quality switch. Edge-to-edge chrome (scaffold background behind status
bar); shorts pager full-bleed via `FullscreenChrome` with a real seekbar; mini player on the shorts
grid. Detail page has a Like/Save/Download/Share/Queue action row; video/shorts cells carry a
`Channel · views · age` meta line (`shortAge()` display transform, pass-through on unknown text).

In: `core/` contracts + `SourceRegistry`; `data/` (Room + DataStore + repositories); `ui/` shell
(AppScaffold + NavHost + placeholder screens); `engine/` (gate, resolver, error mapping, read-only
WebView tier, chain); `source/youtube/`; `player/` (queue maths, format selection, session, media
session service, shared surface). `FyiApp` wires the resolver into `PlaybackSession` and kicks off
engine init.

Also in: home (tabs, search, paging), results list + long-press actions, detail (pinned player
header, metadata, channel tap-through, Similar/Comments tabs below), listing, settings sections,
and the full player — chrome, gestures, quality/speed sheets, mini player, queue bar,
seek-thumbnail mapping.

Detail's "more from this channel" section is now two tabs (`DetailTabsViewModel`): **Similar**
(search on the video's own title, honestly labelled as search matches, never "recommended" — the
engine has no related/recommended list and rejects mix/radio playlists, both confirmed live) and
**Comments** (unchanged threading/replies, now fed by the state holder instead of owning its own
fetch). Each tab fetches at most once per video, gated the same idempotent way
`ListingViewModel.ensureLoaded` is. `YoutubeSource.detail()` still fetches channel uploads into
`VideoDetail.related` (untouched, per this wave's constraints) but the UI no longer reads that
field — a now-pointless extra engine call worth trimming in a future wave.

And: library (likes / playlists / history, multi-select, resume bars), playlist detail with
reorder, the download queue + foreground service + downloads screen, the shorts pager, and library
backup (HTML + embedded JSON, export and import over SAF).

Earlier waves were device-verified on a Nothing A059 (Android 16); the 2026-08-06 wave is not yet.

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

- Device verification of the 2026-08-06 wave is PENDING (user does it): NewPipe resolve speed +
  fallback, background play notification visibility (Android 13+ media-session exemption assumed,
  unverified), lockscreen/Bluetooth, caption rendering, shorts fullscreen/seek, action row,
  edge-to-edge on every route, meta lines.
- NewPipe scope is watch/shorts extraction only — listings/search/comments still yt-dlp. Migrate
  listing calls later if extraction proves itself on device.
- Older open items from review: positions never saved (resume bars empty), signed thumbnail URLs
  persisted for Likes/PlaylistItems, download/like/share unreachable from Channel/Listing screens,
  shuffle desync, backup `unescapeForScript` round-trip.

## Gotchas

- A feed that reads its enabled-source set from DataStore sees an EMPTY set on the first
  composition. Loading then and latching `loaded = true` is why Home and Shorts both silently
  stayed empty. Guard on `sources.isEmpty()` and re-load when the source set actually changes.
- A bare channel URL does not list videos — the engine returns the channel's TABS as playlist
  entries. Channels must be asked for `/videos` explicitly (`channelTabUrl`).
- Downloads need `--newline`, or the engine rewrites one progress line with `\r` and the callback
  never sees a complete line: progress sits at 0% forever.
- The engine writes a bare `NA` (invalid JSON) for eta/speed on the first progress tick. The
  parser rewrites `: NA` to `: null` before decoding.
- `Toast` from a coroutine on the process scope (`Dispatchers.Default`) crashes: no Looper. Always
  go through `showToast`, which posts to the main looper.
- Heavy testing from one IP triggers YouTube's "Sign in to confirm you're not a bot" wall. The app
  correctly classifies it as `AccessChallenge` and stops. It is not a bug, and it is not to be
  worked around with cookies. Wait it out.
- Per-channel errors must never be collapsed into "this channel has nothing" — a fetch failure and
  an empty channel are different facts (`ChannelFetchOutcome.Ok/NoContent/Failed`).

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
- `AppScaffold` consumes system-bar insets for the whole app. A full-bleed surface opts out via
  `ui/AppScaffold.kt`'s `FullscreenChrome.active` seam (set by `DetailScreen`, same package) —
  used by the fullscreen player; a future full-bleed screen (shorts pager) reuses the same seam.
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

- XML layout comments must not contain `--` (broke the resource compile once).
- material3 `Slider`'s `thumb`/`track` slots are `ExperimentalMaterial3Api` — annotate or it's a
  compile error, not a warning, in this project.
- Leaf media source factories (`ProgressiveMediaSource`, `HlsMediaSource`) IGNORE
  `MediaItem.subtitleConfigurations`; only `DefaultMediaSourceFactory` reads them. Sideloaded
  captions on the hand-built `MergingMediaSource` need one `SingleSampleMediaSource` per track
  merged into an outer `MergingMediaSource`.
- The POST_NOTIFICATIONS "media session exemption" does NOT hold in practice: this OEM keeps an
  unrequested app at importance=NONE and the media card never shows. MainActivity requests the
  permission once at launch. Verified on device both ways.
- A STARTED (never bound) MediaSessionService must call addSession() itself — onGetSession only
  fires on a controller bind, and without registration media3's notification manager never
  attaches: no notification, no foreground promotion (startForegroundCount stays 0).
- Start PlaybackService with startService, never startForegroundService: media3 promotes to
  foreground itself once a session is engaged; the manual FGS contract killed the whole app
  (ForegroundServiceDidNotStartInTimeException) whenever promotion hadn't happened yet.
- enqueue() must call prefetchNext() like every other queue mutator — without it, anything queued
  after the queue exhausted (player parked in STATE_ENDED) silently never played.
- Shorts grid and pager are ONE nav route (`Routes.SHORTS`) toggled by `ShortsViewModel.showPlayer`;
  fullscreen gating keys on `FullscreenChrome.active`, not the route string.

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
- 2026-07-31 | Fullscreen button fixed: `fullscreen` hoisted from `PlayerScreen` to `DetailScreen` |
  It was local player state inside a fixed-height 16:9 header `Box` — toggling it only rescaled
  chrome, the box never grew. `DetailScreen` now owns the bool, early-returns to a bare
  `Modifier.fillMaxSize()` player when true (top bar/list not composed), and tells
  `AppScaffold.FullscreenChrome` so the nav bar and system-bar padding drop too. `PlaybackSession`
  gained `videoWidth`/`videoHeight` off `onVideoSizeChanged`; fullscreen orientation locks to
  match once known, so a portrait video no longer gets force-landscaped into a letterbox.
- 2026-08-06 | NewPipeExtractor v0.26.4 as tier-0 resolver, yt-dlp fallback | On-device yt-dlp costs
  3-15 s per resolve (Python spin-up + pure-Python JS); NewPipe does the same job in well under 1 s.
  Fall through only on Unsupported/Network; AccessChallenge/ContentUnavailable stay hard stops.
- 2026-08-06 | Captions off by default, selection per session | Matches the mockup and avoids
  surprising data use; choice survives quality switches, resets per new video.
- 2026-08-06 | Detail action row replaces long-press-only actions on the detail page | Long-press
  sheet stays for list rows; the row reuses its extracted `VideoActions` logic.
- 2026-08-06 | Meta line is text-only (option A) | No avatar fetch per visible row — the no-I/O
  rule for list items decides this, not taste. `shortAge()` transforms platform text, never dates.
- 2026-08-06 | POST_NOTIFICATIONS runtime prompt skipped | Media-session notifications are exempt;
  a permission dialog would be pure friction. Assumption flagged in Gotchas for device test.
- 2026-08-06 | NewPipe is the YouTube VideoSource, yt-dlp keeps downloads + searchChannel | Listings
  now carry views/upload-age/uploader (yt-dlp flat listings never did); resolve + listing latency
  drops from seconds to sub-second. sourceId stays "youtube" so persisted rows keep resolving.
- 2026-08-06 | Followed remote playlists = bookmark row, not item copy | A follow stores only the
  canonical playlist page URL + title; opening it re-fetches live. Copying items would go stale
  and duplicate paging logic.
- 2026-08-06 | Caption cues stripped of embedded positioning at the renderer | Platform TTML/VTT
  regions rendered at the TOP of the surface; SubtitleView's default (bottom-centered) is what
  users expect. One TextOutput wrapper, format-independent.

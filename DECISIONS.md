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

2026-08-07 wave: content **language + country** settings (`Prefs.contentLanguage/contentCountry`,
`settings/ContentSettings.kt`) latch into `NewPipeInit` and reach the extractor as
`Localization`/`ContentCountry`; changes apply live (`setupLocalization`), no restart. Optional
first-party **YouTube sign-in** (`YoutubeLoginActivity` WebView on Google's real sign-in page →
`YoutubeAuth` app-private prefs → `NewPipeDownloader` attaches Cookie/Authorization SAPISIDHASH/
X-Origin to youtube.com hosts only), surfaced as `settings/AccountSettings.kt`. Channel **Courses**
tab delegates to yt-dlp (NewPipe has no such tab). Video **description is now its own tab**
(Similar / Description / Comments) rendering HTML via `AnnotatedString.fromHtml` with in-app link
routing (same-video timestamps seek, other videos open Detail, channels/playlists open Listing,
rest to the browser). Queue **close = clear** (`PlaybackSession.clearQueue`, × on the strip +
"Clear" in the sheet) keeps the playing item and drops the rest. Both seekbars got real touch
targets (40dp bounds, unchanged 2.5/3dp art) and the shorts bar clears the nav-gesture zone.
Optional **download folder** (`settings/DownloadSettings.kt`, `Prefs.downloadTreeUri`): SAF tree
picker via `OpenDocumentTree`, persisted read+write grant. Production download path is untouched --
`DownloadQueue.processNext`'s `EngineOutcome.Done` branch best-effort COPIES the finished
app-private file into the tree (`download/DownloadExport.kt`, `DocumentsContract.createDocument`
+ stream copy) after the row is already COMPLETED; copy failure is swallowed, private file stays
the source of truth. `FyiApp` mirrors the pref into a `@Volatile` field (same pattern as
`maxHeightWifi`) and hands `DownloadQueue.get` a `treeUri: () -> String?` lambda.

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

Device-verified on a Nothing A059 (Android 16) through the 2026-08-06 wave: home feed meta lines,
shorts grid thumbnails+meta, Channels tab, background-play notification/lockscreen, captions
(bottom), channel page + subscribe, edge-to-edge, 60s+ playback with no FGS crash.

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

- **Age-gate wave LANDED (2026-08-07, device-verified):** extractor is now PipePipeExtractor,
  built from the sibling checkout `~/repos/PipePipe/PipePipeExtractor` via composite build
  (`includeBuild` + coordinate substitution in settings.gradle.kts). Signed-in age-gated video
  plays via tier0 in <2 s. Two REQUIRED local patches live in that OTHER repo's working tree
  (not committed there): foojay-resolver block in its settings.gradle, and its Java toolchain
  25 -> 21 (this machine has JDK 21; 25-bytecode also breaks the unit-test JVM). A different
  machine needs those two patches plus the checkout at that path, or the build fails.
- Zulu mystery SOLVED and fixed: the fork hardcodes `Localization("zu")` for all YouTube
  extraction (`YoutubeService.getLocalization` override) — deliberate, it blocks server-side
  title translation, so "original titles" is permanently on and can never be a toggle. Textual
  dates therefore arrive in Zulu; list/comment mappers now format `uploadDate` (DateWrapper,
  parsed via the fork's own zu timeago patterns) through `englishAge()` and fall back to raw
  text only when parsing failed. Device-verified (comments show "1 hour ago").
- SponsorBlock: enabled-off pref, k-anonymity segment fetch (sha256 4-char prefix, never the
  full video id), auto-skip in the session ticker. Device playback verified but an actual
  sponsored-segment skip is still user-unverified.
- If stutter persists after the rn/UA media shaping (user judging): the next step is a ranged
  10 MB chunking media3 DataSource for googlevideo progressive streams — PipePipe's smooth path
  is synthesized-DASH with bounded `range=` chunk fetches, plain open-ended progressive is only
  its fallback. The downloader already proves chunking unlocks full speed on this network.
- YouTube downloads now stream via the extractor chain + OkHttp + MediaMuxer
  (`download/StreamDownloader.kt`); yt-dlp keeps every non-YouTube source. Age-gated download
  verified end-to-end on device (h264+aac mp4, ffprobe-clean).
- Signature/throttling decode falls back to PipePipe's REMOTE decoder API
  (`api.pipepipe.dev/decoder/decode`, sends playerId + sig params only) when no local decoder
  is registered. Wire the fork's WebView JS-decoder seam locally if that dependency bothers us.
- 2026-08-07 shorts/guardrail wave device-verified (this session): back from either shorts pager
  stops playback; channel Shorts tab opens the swipe pager at the tapped clip; resolve failure no
  longer leaves the previous video playing under the guardrail.
- 2026-08-07 wave device-verified on the Nothing A059 EXCEPT sign-in (user is testing that
  themselves): language/country (results shift region, applies live and across cold start),
  Courses tab (freeCodeCamp: two learning paths), description tab (HTML rendered, entities
  decoded, timestamp link seeks in place 15:03 -> 15:33, no navigation), queue × ("1 of 16" ->
  gone, playback continued; enqueue -> "1 of 2"), both seekbars drag (video 16:01 -> 63:01,
  shorts ~70%).
- Courses tab rows have blank thumbnails — the yt-dlp delegate's flat container listing carries
  none. Cosmetic; would need a per-row fetch, which rule 6 forbids.
- User-side verification pending on the newest wave: queue-after-exhaustion on device, channel
  Videos/Shorts play-selected/play-all, followed playlists end-to-end (follow → Library → open →
  remove), playlist tab thumbnails, unsubscribe flow.
- Search channel rows: `toChannelRef` maps subscriber count into viewCountText as a stopgap; a
  typed channel result (Contracts-level) would let the row render properly and drop the URL
  heuristic in HomeScreen.
- Older open items from review: positions never saved (resume bars empty), signed thumbnail URLs
  persisted for Likes/PlaylistItems, shuffle desync, backup `unescapeForScript` round-trip.

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
  fullscreen gating keys on `FullscreenChrome.active`, not the route string. Shorts from OTHER
  listings (channel Shorts tab) use the separate `Routes.SHORTS_PLAYER` + `ShortsPlayerRequest`
  handoff (list can't ride a nav arg). Back from EITHER pager calls `PlaybackSession.clear()` —
  a vertical clip must never leak into the mini player, which would reopen it in the landscape
  detail player with no swipe navigation.
- ChainResolver hard stops (AccessChallenge/ContentUnavailable rethrows) were LOG-SILENT — cost a
  debugging session. They log tier + exception class now; keep it that way.
- A resolve failure for the current item must stop + clear the player: the previous queue item
  otherwise keeps playing under the error guardrail and auto-advances over it later.

- YouTube publishes NO upload date on shorts shelf items: both `YoutubeShortsLockupInfoItemExtractor`
  and `YoutubeReelInfoItemExtractor` return null from `getTextualUploadDate()` (checked in the
  v0.26.4 bytecode). Shorts cells show `Channel · views` and that is the honest ceiling — a date
  would cost one fetch per visible tile, which the no-I/O-per-list-item rule forbids.
- Upstream NewPipeExtractor has no logged-in concept at all, so `YoutubeAuth`'s headers ride on
  whatever InnerTube client the extractor picked. PipePipe's fork additionally swaps the YouTube
  player client when signed in (`NewPipe.setYoutubePlayerClient`, fork-only API). If sign-in turns
  out not to lift age gates on device, that missing client swap is the first suspect.
- `NewPipe.init` must read the latched language/country from `NewPipeInit`, never take them as
  ensure() arguments: init is lazy (first extractor call), so a call site passing defaults would
  silently overwrite the user's setting long after prefs loaded.
- `formatUploadDate` must handle BOTH date shapes: yt-dlp writes "20260805", NewPipe writes
  ISO-8601 ("2026-08-05T04:00:27-07:00"). The old digits-only guard passed the ISO string through
  untouched, so the detail page printed a raw timestamp for every NewPipe-sourced video.
- `AnnotatedString.fromHtml` needs an explicit `import androidx.compose.ui.text.fromHtml` and an
  explicit `LinkInteractionListener { }` SAM wrapper; the lambda alone fails type inference.

- PipePipeExtractor (fork) API vs upstream 0.26: `ChannelTabs` lives in `linkhandler`,
  `ChannelTabInfo` in `channel`; `AntiBotException` replaces `SignInConfirmNotBotException`;
  thumbnails/avatars are plain `...Url` Strings again; `CommentsInfoItem.getCommentText()`
  returns String and has NO channel-owner flag.
- Fork search REQUIRES a registered content filter: `searchQHFactory.getFilterItem(0)` ("all").
  An empty filter list throws a bare `RuntimeException("we have a problem here")` — invisible
  in logcat because it maps to Unsupported; `NewPipeErrors.logged()` (class + frames, never
  messages) exists precisely for this.
- Channel-tab paging can pass `FilterItem(ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.X)` — the tab
  factory matches by name only. Search cannot (registry check).
- Fork `Downloader` adds abstract `executeAsync`; `CancellableCall.setFinished()` must run on
  EVERY exit path or the extractor's await-latch hangs the resolve. `Response` ctor wants raw
  body bytes alongside the string (SABR reads protobuf bodies).
- Fork login = `ServiceList.YouTube.setTokens(cookie)` + player client `tv_downgraded`
  (anonymous: `visionos`), BOTH reapplied on login/logout (`NewPipeInit.applyAuthState`).
  Downloader-level header injection alone does NOT log the extractor in — `addLoggedInHeaders`
  reads only the service tokens.
- jitpack has NO working build of the fork's current history (GitHub mirror diverged from its
  Codeberg origin) — the composite build from the local checkout is the only supply.
- compileSdk is 36 because the fork's okhttp 5.4 AAR demands it; targetSdk stays 35.

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
- Facebook search / page-video subscriptions — impossible, not deferred. yt-dlp has no Facebook
  search extractor and no page-listing extractor (single video/reel IDs only; feature request open
  since 2023); NewPipeExtractor and its forks have no Facebook service; Graph API page-video
  listing needs App Review plus a page-owner token; RSS-Bridge's bridge is chronically anti-bot
  broken. Facebook can only ever be "paste a direct video link". Anything more is scraping against
  active enforcement, which the no-bypass rule excludes.
- Swapping NewPipeExtractor for PipePipeExtractor (the PipePipe fork, also GPLv3) — rejected for
  this wave. Same root package and `NewPipe.init` shape, but it forked before upstream's 0.24
  restructure (`ChannelTabs` lives in `linkhandler`, `ChannelTabInfo` moved), so ~15 of our imports
  and their signatures would need porting and every flow retesting. Login needed only app-layer
  header injection, which upstream supports fine. Revisit only if the player-client swap turns out
  to be required for age-gated content.
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
- 2026-08-07 | Sign-in is app-layer header injection, not an extractor fork | The user authenticates
  with Google directly in a WebView; we only read the session cookie the browser already holds and
  sign requests with Google's own SAPISIDHASH scheme, youtube.com hosts only. No wall is worked
  around — an account either has access or it does not.
- 2026-08-07 | Closing the queue clears it but never stops playback | Queue ≠ player: dismissing
  upcoming items should not kill the video being watched. Enqueue then re-grows it to two items,
  which is what makes the bar reappear showing just the added video.
- 2026-08-07 | Description became a tab instead of an inline block | Descriptions are HTML and full
  of links; a 3-line collapsible with raw markup in it was the worst of both. As a tab it gets the
  room to render properly and joins the Similar/Comments row the screen already had.
- 2026-08-06 | Caption cues stripped of embedded positioning at the renderer | Platform TTML/VTT
  regions rendered at the TOP of the surface; SubtitleView's default (bottom-centered) is what
  users expect. One TextOutput wrapper, format-independent.
- 2026-08-07 | Back from any shorts pager stops playback (`PlaybackSession.clear()`) | A vertical
  clip surviving into the mini player reopens in the landscape detail player with no swipe nav —
  worse than honest silence. User asked for exactly this.
- 2026-08-07 | Channel Shorts tab opens `Routes.SHORTS_PLAYER` (reused `ShortsPager`) | Any shorts
  listing should page with swipe up/down; refs are canonical watch URLs so the TAB context, not
  the URL, is what identifies a short.
- 2026-08-07 | Resolve failure stops the player and drops the stale prefetch | The old item kept
  playing under the guardrail and later auto-advanced over it — root cause of "shows login wall
  but starts playing after some time".
- 2026-08-07 | Signed-in AccessChallenge retries tier1-with-cookie then tier2, rethrows ORIGINAL
  wall | The user's own account may pass an age wall the anonymous extractor cannot; no wall is
  dismissed anywhere. Confirmed insufficient for YouTube age-gate on device (engine ignores the
  bare Cookie header; tier2 captures SABR segments) — kept as chain shape, real fix landed same
  day (next entry).
- 2026-08-07 | Extractor swapped to PipePipeExtractor, composite-built from the local sibling
  checkout | Upstream NewPipeExtractor has no logged-in player client, so age-gated videos can
  never play for a signed-in user; PipePipe's `tv_downgraded` client + service tokens is the
  proven mechanism (its own client ships it). jitpack has no usable artifact, hence
  includeBuild. Verified on device: gated video resolves tier0 <2 s and plays; search, channel
  tabs, shorts grid, comments, home feed all live on the fork.
- 2026-08-07 | Opening any detail page plays that video, once per nav entry | History/Library
  rows only navigated, dead-ending at "Nothing playing"; and with something else playing the
  page showed the wrong video. `rememberSaveable` autoplay latch = pop-back never hijacks
  playback that legitimately moved on; mini-player tap (same ref, no error) never restarts.
- 2026-08-07 | Media requests shaped like NewPipe's YoutubeHttpDataSource: rn counter + real
  browser UA + TE:trailers, one OkHttp interceptor shared by playback and downloads
  (`player/MediaHttp.kt`) | googlevideo paces unshapen clients to ~realtime; a 30 MB download
  took 20+ minutes and playback starved. With shaping + 10 MB ranged chunks the same download
  finished in seconds (device-verified).
- 2026-08-07 | YouTube downloads = extractor resolve + OkHttp ranged fetch + MediaMuxer merge,
  never yt-dlp | yt-dlp is anonymous, so gated downloads could never work; the extractor is the
  signed-in path and MediaMuxer merges without any new dependency. Mux container follows the
  video codec (avc/hevc->mp4, vp8/vp9->webm) with the audio swapped to a compatible codec when
  the pairing crossed families.
- 2026-08-07 | Similar tab tops up from continuations (cap: 2 extra pages, target 8 videos) |
  Niche titles return mostly channels; after the video-only filter 2-3 rows looked broken.
- 2026-08-07 | Scrub UX: slider box height-pinned, auto-hide paused while scrubbing, preview =
  ORIGINAL-size decode + single-tile source-rect crop, card follows tile aspect | Three separate
  drag-release bugs (box inflation, 3s auto-hide unmount, scaled-cache mis-crop) each looked
  like "the drag randomly releases"; all device-verified fixed (8s synthetic drag lands at 85%).
- 2026-08-07 | Notification Close = media3 custom SessionCommand (ICON_STOP, SLOT_OVERFLOW) ->
  PlaybackSession.clear() | media3 1.9.4 facts verified against the artifact bytecode, not
  memory: notification renders from mediaButtonPreferences, setCustomLayout is not what System
  UI reads. Watch page left open after Close shows the ref's poster + replay (PlayerScreen
  pageRef param), never "Nothing playing".
- 2026-08-07 | Shorts scrub thumbnail (YouTube-style portrait card) with LAZY storyboard fetch |
  Fetch costs a full extractor call; most shorts are swiped past, never scrubbed — first drag
  triggers it, page deactivation drops it.
- 2026-08-07 | Shorts "Details" opens a ModalBottomSheet over the pager, not a nav world-switch |
  `ui/ShortsDetailsSheet.kt` reuses `DetailTabsViewModel`/`descriptionTabSection`/`CommentsSection`
  verbatim (Description + Comments only, no Similar) with its own `source.detail(ref)` fetch,
  ~50% screen height so the short stays visible. Tap pauses via `togglePlayPause()` (no plain
  `pause()` on `PlaybackSession`); dismiss never auto-resumes -- existing tap-to-toggle on the
  video surface resumes it. Description links to ANOTHER video/channel/playlist are a no-op
  inside the sheet: `ShortsPage.onOpenDetail` is `() -> Unit`, already curried to the page's own
  ref by `ShortsPager`, so there is no callback here that can route to an arbitrary linked target
  without threading a new nav callback through files outside this task's scope. Same-video
  timestamp links still seek in place (no callback needed).

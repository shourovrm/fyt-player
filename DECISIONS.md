# DECISIONS

## Current state

2026-08-23 (v0.2.8): app renamed to "FYT Player" -- launcher label, backup copy/filename, docs,
rootProject.name only. applicationId stays `com.fyiplayer.app` (a new id = a new app on Android,
no in-place update, all local data lost). README.md added; repo public at github.com/shourovrm/fyt-player.

2026-08-23 (v0.2.7): shorts shelf tap opens the TAPPED clip (device-verified: Detail Similar
shelf card 3 -> card 3 plays, swipe -> card 4). ShortsPager's playback->pager follow effect
reads the LIVE session index + checks the queue is this feed; the `collectAsState` snapshot
it is keyed on is one composition stale and carried the previous queue's index.

2026-08-23 (v0.2.6): FullscreenChrome claim leak FIXED (device-verified: Detail fullscreen ->
back -> back to Home keeps nav bar + status-bar padding). Any Compose `onDispose` must decide
on the effect KEY, never re-read the state it is keyed on -- by dispose time it already holds
the new value.

2026-08-22 wave (v0.2.5): share/open-with plays Facebook (landscape) and TikTok (shorts pager)
via yt-dlp tier1. Cookie header from yt-dlp's per-format `cookies` (TikTok CDN needs
tt_chain_token/ttwid); codec-unknown progressive mp4 now selectable as muxed (Facebook sd/hd);
tiktok hosts in the VIEW filter + `openSharedUrl` routes vertical-clip hosts to the shorts pager.
Landscape white/grey left strip FIXED (AppScaffold consumes the display-cutout inset). yt-dlp
in-app update works (bumped to 2026.08.19 on device).

2026-08-22 wave (v0.2.4): channel search on the fork + shorts shelf; Shorts tab pages per channel.

2026-08-10 wave (v0.2.3; device-verified on the A059 EXCEPT the three flagged in Next): R8+
resource shrinking ON (59.5 MB APK,
proguard-rules.pro, search/play/download exercised under minify); canonical thumbnail URLs at all
three persist seams (`data/repo/ThumbnailUrl.kt`); shuffle order remapped through every queue
mutator; backup HTML parse no longer double-unescapes; typed channel rows (ResultKind/
subscriberCount, "21.1M subscribers" renders); ChunkedRangeDataSource probes totals for clen-less
QUERY-STYLE /videoplayback only (HLS segments are path-encoded, range= query on them = HTTP 400);
Detail re-entry takes playback (Similar-chain back mismatch fix); FullscreenChrome claim-counted
(shorts-from-Similar full-bleed fix); YouTube downloads offer progressive-only options and
StreamDownloader refuses manifests (was saving .m3u8 as the video). Similar-chain BACK itself
verified correct, 3 hops each way.

2026-08-09 wave (4 parallel agents, integrated, tests green, device-UNverified): Similar tab
STAYS title-search — recommendations landed and were reverted same day (user preference, see
Tried/rejected). Home merged feed sorts `uploadEpochMs` descending (new `VideoRef`
field from `uploadDate.offsetDateTime()`; nulls last, stable; `sortByRecency` applied ONLY at
Home's merge — channel tabs/playlists keep service order, Shorts interleave untouched). Playlist
rows now persist `uploaderUrl` (schema v6, additive column — the WatchHistory v1→2 bug all over
again) and Detail's fallback header links the channel straight off the ref instead of plain text.
Share-with/open-with: manifest SEND(text/plain) + VIEW filters (youtube/youtu.be/facebook/
fb.watch/twitter/x hosts), `singleTask` + `onNewIntent`; first https URL in the text →
`nav.openDetail` via a `PendingSharedUrl` compose-state seam (cold start waits for NavHost).
YouTube rides tier0, FB/Twitter fall through to yt-dlp tier1 — no per-host code.
`PlaybackSession.play()` now does `stop()+clearMediaItems()` first: new-queue start while
something else played left the old item running (audio + frame) on the shared surface ~1 s until
the async resolve landed — the "shorts shows previous video" flash. clearMediaItems is what
actually closes PlayerView's shutter (stop alone can skip the same-period check).

2026-08-08 "No connection" ROOT CAUSE FIXED (device-verified: the exact 403 video now plays):
signed-in sessions swapped the fork's player client to `tv_downgraded` (TVHTML5), whose
ciphered-signature URLs googlevideo 403s for popular videos even after correct sig/n decode.
Player client is now ALWAYS `visionos` (unciphered URLs, play fine); `tv_downgraded` only as a
one-shot age-wall retry (`NewPipeInit.withSignedInPlayerClient`, used by NewPipeResolver on
AccessChallenge + session present). Supporting fixes, all landed: local sig/n decoder
(`source/newpipe/WebViewJsDecoder` + `SharedWebViewRuntime`, ported from PipePipeClient, EJS
solver assets under `assets/ejs/`) registered via `YoutubeApiDecoder.setLocalDecoder` — kills
the api.pipepipe.dev remote-decoder dependency (device DNS couldn't resolve it; undecoded sig =
guaranteed 403); googlevideo ranges now ride the `range=` QUERY PARAM (official-client shape)
instead of the Range header in both `ChunkedRangeDataSource` and `StreamDownloader` (range-param
windows answer HTTP 200, not 206 — downloader loop handles both, empty window = EOF); media UA
on `/videoplayback` is the platform default (`http.agent`), never a browser string (client/UA
mismatch is 403-bait), with Origin/Referer/Sec-Fetch added for WEB/TVHTML5-signed URLs only
(mirrors PipePipe's YoutubeHttpDataSource); ChainResolver: tier0 owns YouTube outright — NO
yt-dlp/WebView fallback for YouTube resolve failures (yt-dlp keeps non-YouTube + channel Courses
delegate); playback errors map honestly (`isNetworkCause`) — HTTP-status failures say "Can't
play this video right now", never "No connection". Extractor checkout merged to v5.2.5 (code
identical to 5.2.4 + version bump; local JDK-21 toolchain patch kept). Redaction-safe wire
diagnostics kept on purpose: `NewPipeResolver` logs media URL param NAMES only,
`MediaHttp` logs client/UA-prefix/range-flags/response-code only.

2026-08-08 field-report wave (5 parallel agents, integrated + 275 tests green, device-UNverified):
IOException no longer blanket-maps to Network ("No connection" spam fix — only real transport
exceptions in an 8-deep cause chain qualify); `PlaybackSession.retryCurrent()` + Retry button on
player error states (except AccessChallenge — honest wall keeps no retry) and togglePlayPause
routes to it on error/STATE_IDLE (stuck-after-background fix); player gestures got edge dead
zones (24dp sides / 32dp bottom for system back/home), 24px slop before mode lock, and
full-height-drag ≈ 150% range sensitivity (float accumulator); h:mm:ss time labels
(`formatPosition` seam, feeds mini player too); tap in fullscreen shows system bars with the
chrome (entering fullscreen seeds controlsVisible=false or paused video would pin bars on);
PipePipe queue semantics — row taps everywhere open Detail (single-item play), whole-list play
only via explicit Play All/Background; Similar tab gained the shorts shelf (`onOpenShorts`
threaded through AppShell→DetailScreen); shorts overlay gained HD quality + speed rail entries
(same QualitySheet/SpeedSheet + PlaybackSession seams as PlayerScreen); search: BackHandler exits
search mode, suggestion dropdown (fork `YoutubeSuggestionExtractor` via
`source/newpipe/SearchSuggestions.kt`, 300ms debounce), Clear-all in history dropdown,
PullToRefreshBox on home feed; search returns playlists (stopgap VideoRef, canonical
`playlist?list=` URL, RD* mixes dropped, HomeScreen URL-heuristic routes to listing) and
LIVE/UPCOMING badges (`VideoRef.isLive/isUpcoming`; upcoming = future upload date, the fork
reports premiere start time as upload date); autoplay-next pref (off default, title-search based,
honest subtitle) via injected `autoplayNext` lambda + STATE_ENDED latch; crash visibility:
`CrashLog.kt` uncaught handler writes class-names+frames only (never messages — they carry URLs),
"Last crash" viewer row in EngineSettings; EngineSettings copy now says extractor updates require
a new APK (yt-dlp rows relabelled).

Signed arm64-v8a release APK builds; 203 JVM unit tests green. Compose + Material 3, single Activity.

2026-08-06 wave: NewPipeExtractor v0.26.4 is BOTH tier-0 of the resolver chain AND the YouTube
VideoSource (`source/newpipe/NewPipeYoutubeSource`, id stays "youtube"): search, channel tabs,
playlists, detail, comments, seek thumbnails, shorts (providesShorts=true now). yt-dlp keeps
downloads, channel Courses tab and resolver fallback (searchChannel moved to the fork 2026-08-22).
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

Detail's "more from this channel" section is two tabs (`DetailTabsViewModel`): **Similar** (title
search on the video's own topic — deliberately NOT the platform's recommendations, user's call;
`VideoDetail.related` is populated by the extractor but unused) and **Comments** (unchanged
threading/replies, fed by the state holder instead of owning its own fetch). Each tab fetches at
most once per video, gated the same idempotent way `ListingViewModel.ensureLoaded` is.

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
- Shorts-tab paging landed 2026-08-22 (grid + pager load-more, device-verified growing past the
  old 64-item cap). UNVERIFIED: the "all caught up" end footer (needs every channel exhausted)
  and pager-tail trigger in isolation (shares `loadMore`, grid path proven).

- Device-verify the 2026-08-09 wave (v0.2.2): home recency sort, playlist
  channel link (needs a NEWLY-added playlist item — old rows have null uploaderUrl), share-with
  from YouTube/FB/Twitter apps cold+warm, shorts-entry flash gone.
- "Back through Similar chain goes home" RESOLVED 2026-08-10: back itself walks the chain
  correctly (device-verified, 3 hops). The real complaint was the deeper video still PLAYING
  over the shallower page after back — fixed (Detail re-entry takes playback). Residual foot-gun:
  bottom-nav Home tab stays tappable on Detail and `navigateToTab`'s popBackStack(home) discards
  the whole chain in one tap — if reports continue, that's the remaining suspect (fix would be
  hiding the nav bar on Detail routes, a UX decision → mockup first).
- User-verify v0.2.3: R8 build (66→~59.5 MB), channel rows "N subscribers", shorts-from-Similar
  full-bleed — all three device-verified by me already. STILL DEVICE-UNVERIFIED (USB dropped
  mid-check): back-return playback switch, YouTube download real mp4 (was .m3u8), no per-segment
  probe 400s. Verify these first on reconnect.
- Chunked-source probe for clen-less PROGRESSIVE URLs is live but not yet exercised on device
  (playback rides HLS manifests now) — verify when a progressive-only video shows up.
- **Age-gate wave LANDED (2026-08-07, device-verified):** extractor is now PipePipeExtractor,
  built from the sibling checkout `~/repos/PipePipe/PipePipeExtractor` via composite build
  (`includeBuild` + coordinate substitution in settings.gradle.kts). Signed-in age-gated video
  plays via tier0 in <2 s. Building needs that checkout present at that path.
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
- Search Shorts shelf (`ui/ShortsShelf.kt`, `VideoRef.isShort` wired in `NewPipeYoutubeSource.
  toVideoRef` from `StreamInfoItem.isShortFormContent` + `/shorts/` URL fallback): LazyRow as the
  results list's first item (`ResultsListColumn.topContent`), search mode only. `partitionShorts`
  splits shelf vs. regular/queue. Auto-grows to `MIN_SHELF_SHORTS` (20) by pulling up to
  `MAX_SHELF_AUTO_FETCHES` (3) extra search pages. Device-verified (shelf renders, tap opens the
  pager, swipe navigates, ≥20 cards on a generic query).
- 2026-08-08 wave (device-verified except where noted): resolver LRU cache (60 entries/60 min,
  `ChainResolver`, `StreamResolver.invalidate` seam), expired-URL fast-fail policy + resume
  staleness refresh (`isStale` 50 min — stale-resume path itself not yet exercised on device),
  `SharedSurface` ownership registry (black-screen race fix — race never reproduced on demand,
  fix is registry-by-construction), googlevideo chunked ranged reads
  (`player/ChunkedRangeDataSource.kt`, 10 MB windows, mirrors StreamDownloader) for full-speed
  transfer instead of realtime pacing.
- Search channel rows: `toChannelRef` maps subscriber count into viewCountText as a stopgap; a
  typed channel result (Contracts-level) would let the row render properly and drop the URL
  heuristic in HomeScreen.
- Older open items from review: positions never saved (resume bars empty), signed thumbnail URLs
  persisted for Likes/PlaylistItems, shuffle desync, backup `unescapeForScript` round-trip.

- 2026-08-08 late wave (device-verified): fullscreen-exit right-shift FIXED — the OEM skips the
  window's inset re-dispatch after the in-process rotation; every app-side cache (Compose holder
  AND getRootWindowInsets) then serves landscape values to the portrait layout. Fix is a forced
  WindowManager relayout round-trip (`window.attributes = window.attributes`) on fullscreen exit
  + onConfigurationChanged, plus AppScaffold snapshotting root insets keyed on
  configuration/fullscreen with a double re-read tick. Playback-position resume LANDED
  (device-verified): `Prefs.savePlayPosition` (default on, third toggle in HistorySettings),
  FyiApp owns pref gating + near-end-clears (>=90% clears the row, <5s not saved),
  PlaybackSession saves every ~5s tick + on pause + at STATE_ENDED and resumes via
  `loadPosition` in startAt (shorts never resume). Resume bars in Library now light up.

## Gotchas
- TikTok CDN 403s without session cookies; yt-dlp's per-format `cookies` is Set-Cookie shaped
  (name=val; Domain=..; Path=..) -- strip attributes to a `Cookie:` header (EngineResolver.cookieHeaderFrom).
- TikTok IP-rate-limits repeated extraction: same request that gave 206 flips to 403 after ~10 hits,
  and yt-dlp then wants curl_cffi impersonation (can't ship on Android; = bypass). Honest error only.
- Facebook `Cannot parse data` hits ~2/3 of public URLs intermittently (yt-dlp extractor, upstream);
  some fail on one network and play on another. Not fixable under the no-bypass rule.
- Facebook sd/hd progressive mp4 arrive with NO vcodec/acodec keys -- treated as muxed(unknown),
  else FormatSelector rejects them. `none` still means absent (video-only stays video-only).
- Landscape cutout is a 126px LEFT system inset on this OEM; any nested Scaffold/TopAppBar re-pads
  it into a grey strip unless AppScaffold consumes WindowInsets.displayCutout (device-verified).
- Channel search rows carry NO shorts signal from YouTube (plain videoRenderer, /watch, overlay
  DEFAULT, 16:9 thumb — verified live 2026-08-22); `shortByDuration` (<=60 s) is the only tell.

- Signed-in `tv_downgraded` (TVHTML5) URLs are ciphered and googlevideo 403s them for popular
  videos even with a correct sig/n decode. Never make it the default client again — visionos
  always, TVHTML5 only for the age-wall retry.
- visionos progressive URLs carry NO `c=` param and often no `clen` — `ChunkedRangeDataSource`
  then passes through un-chunked (old pacing risk); watch for stutter reports on long videos.
- The fork's remote decoder (api.pipepipe.dev) silently yields unusable URLs when unreachable:
  a missed decode leaves SIGNATURE_PLACEHOLDER/raw `n` in the URL with NO exception. The local
  WebViewJsDecoder must stay registered before any resolve.
- googlevideo `range=` param windows answer HTTP 200 (not 206) and past-EOF gives an empty 200
  body, never 416.
- visionos playback rides HLS: FormatSelector prefers manifests, so googlevideo traffic is mostly
  SEGMENT fetches whose params are PATH-encoded (no query string — `c=null` in MediaHttp logs is
  normal). Appending a `range=` QUERY param to those answers HTTP 400; never range path-style
  URLs. Query-string presence is the progressive-vs-segment discriminator.
- A YouTube "1080p" download option can map to the HLS master (manifest wins FormatSelector);
  StreamDownloader saving it produces a .m3u8 file as the "video". Downloads must select from
  progressive formats only (engine path keeps manifests — yt-dlp fetches segments itself).

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
- Test files in one package share a namespace for private top-level declarations: two files both
  declaring `private class FakeResolver` fail compilation with a misleading "private in file"
  error. Name test fakes per-file (e.g. `CountingResolver`).
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

- Similar tab via extractor recommendations (`VideoDetail.related`) — worked, reverted 2026-08-09:
  user wants title search, not YouTube's recommendation feed. Never re-add unprompted.

- Bars-follow-chrome in fullscreen (tap shows status bar with the controls) — REVERTED. Repeated
  insetsController hide/show inside a fullscreen session wedges this OEM's inset delivery: after
  exit the window keeps landscape insets and the portrait page renders shifted right. Three
  workarounds failed (requestApplyInsets, WM attribute round-trip, root-inset snapshot — the
  snapshot also latches because Android mutates Configuration in place, so remember keys never
  re-fire). Bars now change exactly twice per session (hide on entry, show on exit); the decor
  inset listener (SystemBarInsetsState) and the WM round-trip stay as hardening.
  PipePipe's recipe if this is ever re-attempted (researched from source): their activity
  RECREATES on rotation (no configChanges), player survives in a Service, fullscreen/system-UI
  state recomputed from scratch on reattach; bars-follow-controls via legacy systemUiVisibility;
  and they STILL hand-reset insets ("Apply window insets because Android will not do it when
  orientation changes from landscape to portrait" -- Player.toggleFullscreen +
  setFragmentListener zero the padding manually). The OS bug is real; their cure is View-world
  manual padding resets.

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
  broken release build is worse than a large one. (LANDED 2026-08-10: conservative keeps for
  extractor/Rhino/yt-dlp/Room/serialization in proguard-rules.pro, device-verified.)
- Once-per-nav-entry autoplay latch on Detail (rememberSaveable `autoplayed`) — REMOVED
  2026-08-10: backing from C to B left C playing over B's page (user-reported mismatch). Detail
  re-entry now takes playback whenever the session plays a different pageUrl; same-video re-entry
  stays a no-op so reopening from the mini player never restarts.
- Boolean FullscreenChrome.active — replaced with claim counting 2026-08-10: nav-transition
  overlap (incoming pager composes before outgoing Detail disposes) let Detail's onDispose stomp
  the pager's `true`; nav bar stayed visible on shorts-from-Similar. Never a single global
  boolean for overlapping lifetimes.

- Suggestion fetch failures collapse to emptyList — a dead suggestion endpoint must never render
  as a search error. `SearchSuggestions.fetch` swallows everything by design.
- `Icons.Filled.History` and `LocalFocusManager` under `androidx.compose.ui.focus` don't exist —
  History glyph missing from material-icons-core (Search icon reused), LocalFocusManager lives in
  `androidx.compose.ui.platform`.

## Log

- 2026-08-08 | Retry button on player errors EXCEPT AccessChallenge | Re-resolving can't pass an
  honest wall; ResultsList already sets onRetry = null for the same reason.
- 2026-08-08 | Search playlist hits: RD* (mix/radio) list ids dropped at mapping | Extractor
  rejects mixes ("Unable to recognize playlist"); a row would only open a broken listing.
- 2026-08-08 | Row tap = single-item play everywhere; whole-list play only behind explicit
  Play All/Background | PipePipe queue model the user asked for: queue holds only what was
  deliberately enqueued. Shorts pager keeps its list — swipe nav needs it.
- 2026-08-08 | Crash log records exception class names + frames, never messages | Extractor
  exception messages embed signed URLs; redaction rule beats debuggability.

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
- 2026-08-08 | Search Shorts shelf reuses `ShortsPager` via `AppShell.openShortsPlayer`, seeded
  with only the loaded shelf list (no swipe-past-end paging) | Same handoff idiom the channel
  Shorts tab already uses; a second paging path for one shelf isn't earned yet.
- 2026-08-08 | Resolver results cached in memory (LRU 60, TTL 60 min) with an `invalidate` seam |
  PipePipe's InfoCache proves the pattern; replay/back-nav now starts instantly, and the player
  invalidates before every expiry re-resolve so a dead URL can't be served back.
- 2026-08-08 | 401/403/410 load errors never retried by ExoPlayer (custom LoadErrorHandlingPolicy) |
  Default backoff burned 30-90 s before onPlayerError's re-resolve; a dead signed URL can't heal.
- 2026-08-08 | SharedSurface ownership = explicit registry, detach re-homes to the surviving host |
  Compose disposal order between hosts is not guaranteed; ordering-dependent detach left the view
  parentless (intermittent black screen after fullscreen exit).
- 2026-08-08 | googlevideo progressive read in 10 MB ranged windows (ChunkedRangeDataSource) |
  One open-ended /videoplayback request is paced to ~realtime; bounded ranges arrive full speed
  (downloader already proved it). PipePipe achieves the same via synthesized-DASH range fetches.
- 2026-08-09 | Similar recommendations reverted same day | User explicitly wants title search,
  not YouTube's recommendation feed. Don't re-attempt.
- 2026-08-09 | Home merged feed sorted by new VideoRef.uploadEpochMs desc, nulls last | Cross-
  channel recency order; DateWrapper approximation fine for a sort key. Home merge point only.
- 2026-08-09 | playlist_items gained uploaderUrl (schema v6) | Same bug class as WatchHistory
  v1→2; without it playlist-opened videos showed uploader as dead text.
- 2026-08-09 | Share-with/open-with via singleTask + PendingSharedUrl seam into nav.openDetail |
  Reuses the host-agnostic resolver chain; zero per-host wiring beyond the manifest filter.
- 2026-08-09 | PlaybackSession.play() stops+clears before starting a new queue | Old item kept
  rendering into the shared surface during the async resolve (shorts-entry flash); clearMediaItems
  needed, stop() alone can leave PlayerView's shutter open.
- 2026-08-10 | R8 + resource shrinking on release, conservative keep rules | 74→59.5 MB; rest is
  Python/ffmpeg .so bulk R8 can't touch. Search/playback/downloads exercised on device.
- 2026-08-10 | Canonical thumbnails at every persist seam (canonicalThumbnailUrl) | Likes and
  playlist items stored signed ytimg URLs — rot + identify; history already stripped, now shared.
- 2026-08-10 | Shuffle order remapped in playNext/enqueue/move/removeAt (QueueMath helpers) |
  Mutators edited `queue` but not `order`; stale indices desynced next/previous under shuffle.
- 2026-08-10 | Backup parse stops unescaping < | JSON string escapes decode natively;
  the extra unescape corrupted titles containing the literal text.
- 2026-08-10 | Typed channel search results (ResultKind + subscriberCount on VideoRef) | Replaces
  the viewCountText="N subscribers" stopgap; UI formats display, source stays typed.
- 2026-08-10 | ChunkedRangeDataSource range=0-0 probe gated to query-style URLs | HLS segment
  URLs are path-encoded and 400 any range= query param — ungated probe cost one wasted request
  per segment (seen live before the gate).
- 2026-08-10 | Detail re-entry takes playback; FullscreenChrome claim-counted | Similar-chain
  back mismatch + nav bar over shorts-from-Similar, both user-reported, both device-verified.
- 2026-08-10 | YouTube download options exclude manifests; StreamDownloader refuses them | 1080p
  mapped to the HLS master and saved a .m3u8 as the finished video.
- 2026-08-22 | channel search via fork ChannelTabs.SEARCH + shorts shelf in channel Search tab | yt-dlp flat JSON had no short flag; fork handler built from full `/search?query=` url (extractor reads query off originalUrl). Shorts flagged by <=60 s heuristic — platform gives none.
- 2026-08-22 | Shorts tab pages: per-channel `ChannelShortsCursor` (buffer + token), `loadMore` appends an interleaved round, never re-merges | first page leftovers were thrown away by the per-channel cap; re-interleaving over shown items would reshuffle under the pager. Honest end footer when all cursors dry.
- 2026-08-22 | share-with plays FB (Detail) + TikTok (shorts pager); cookie header + muxed-unknown-codec fixes; cutout inset consumed | FB public videos + TikTok routing device-verified; TikTok playback blocked by upstream IP rate-limit, honest error.
- 2026-08-22 | v0.2.5 | share/play Facebook & TikTok, landscape border fix
2026-08-23 | v0.2.6: FullscreenChrome release decided on key, not re-read state | onDispose saw fullscreen=false after exit, claim leaked (nav bar gone, page under status bar)
2026-08-23 | v0.2.7: pager follow-effect reads live PlaybackSession.state, not keyed snapshot | stale index (Detail 0 / prior pager page) scrolled pager + JUMPed playback to wrong short on shelf tap
2026-08-23 | v0.2.8: rename to FYT Player is label-only, applicationId unchanged | new applicationId = new app, no in-place update, library/settings/downloads lost
2026-08-23 | LICENSE: MIT for this repo, APK effectively GPL-3.0 via PipePipeExtractor | user choice; README states both

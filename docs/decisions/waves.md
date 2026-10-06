# Wave snapshots

Dated multi-topic snapshots. Grep this file; do not read it whole.

2026-09-24 (v0.2.21) DEVICE-VERIFIED (Nothing A059): Replay = centre button turns into a Refresh
glyph at STATE_ENDED (`PlayerState.ended`), togglePlayPause seeks 0 + plays; controls come up at
the end. "Loop" text button in the bottom row (red when on) = RepeatMode.ONE, per video (play()
resets the player's repeatMode). Shorts grid pull-to-refresh (PullToRefreshBox). Shorts feed
fetches EVERY subscription (`capChannels`/MAX_FEED_CHANNELS deleted), Semaphore(FEED_CONCURRENCY)
on first page and loadMore -- the cap was why Squat University's shorts never showed. Home was
already right (its newest long-form is 12d old; it lands mid-feed by date). Settings "Sign in
again" + a Sign in / Sign in again button on AccessChallenge in player and shorts
(`settings/YoutubeSignIn.kt`; fresh=true clears WebView cookies first, stored session kept until
a new one lands). Extractor SHIPPED = v5.2.5 + our 2 patches (local
checkout on branch `backup-fyt-5.2.5`, GitHub `fyt-patches-5.2.5`). v5.3.1 rebase lives on
`fyt-patches` (local + GitHub) but is NOT shipped: see Next. Long-press sheet scrolls (landscape
clipped Download). Stream downloads HEAD for the total when a format has no clen (row sat at 0%
until done) + ProgressMeter speed/ETA; device-verified 80% -> 95% -> done with MB/s + ETA.

2026-08-30 (v0.2.20) Keep-note wave, DEVICE-VERIFIED (Nothing Phone): Similar tab = YouTube
watch-next recommendations (`detail.related`, DetailScreen gates the fetch on `detailLoaded`),
title search only as fallback. Downloads: quality sheet sizes via HEAD Content-Length on open
(one dialog = one video, I/O allowed), live speed/ETA + final size/duration (startedAt/finishedAt),
"Download complete/failed" one-shot notification + stopForeground when drained, "Also save
subtitles" checkbox writes a same-basename sidecar (findProducedFile skips .srt/.ttml/.vtt or the
row points at the sidecar), thumbnailUrl column (DB v7). Library: `youtubeThumbnailFor(pageUrl)`
hqdefault fallback everywhere a row renders, `withTitleIfBlank()` enriches bare refs on
like/playlist-add/download-start, "Untitled" instead of an empty line. Description: bare URLs /
scheme-less youtube links / @handles / timestamps linkified on both HTML and plain branches.
Playlists: `list=` share-in opens the remote listing (mix RD* rejected), Share on listing /
followed / local playlists (local = title + one URL per line), one `PlaylistRowItem` for
followed and local. Queue sheet: Shuffle text button (no glyph in icons-core).

2026-08-24 (v0.2.13) fullscreen bars + shorts wakelock + in-app update, DEVICE-VERIFIED
(Nothing Phone, gesture nav): fullscreen entry+rotation = no bars; Home->relaunch while
fullscreen = bars stay hidden (ON_RESUME re-assert); exit = no right-shift; shorts pager holds
KEEP_SCREEN_ON while playing, cleared on pause; Settings manual check hit live GitHub API ->
"You're on the latest version" (0.2.13 > released 0.2.12). Banner untestable until a release
newer than installed exists. Still unverified on a 3-button-nav OEM device (the original
reporter's hardware):
- **Fullscreen nav/status bar reappearing** (3-button-nav OEMs): the single entry hide got undone
  by the OEM re-showing bars after the in-process rotation and after return-to-app. Fix: bounded
  fullscreen-scoped re-hides in PlayerScreen (orientation key + ON_RESUME), reusing
  `setSystemBarsVisible`. NOT bars-follow-chrome (still banned, see Tried/rejected) — PipePipe
  re-asserts the same way; safe because layout ignores bar visibility while fullscreen
  (`contentWindowInsets = WindowInsets(0)`), so re-hides can't churn insets. Exit path untouched.
- **Shorts screen timeout**: `setKeepScreenOn` was only wired in PlayerScreen; ShortsPager now
  holds the same isPlaying-keyed wakelock.
- **In-app update** (`update/UpdateCheck.kt`, `update/ApkInstaller.kt`): GitHub
  releases/latest (unauthenticated; tag_name minus "v", first .apk asset), `isNewer` numeric
  segment compare (unit-tested, garbage tag -> false). Auto check once per process
  (FyiApp.onCreate, silent offline) -> dismissible banner above content in AppScaffold (hidden on
  full-player routes; dismissal per version in Prefs.dismissedUpdateVersion). Settings > App:
  "Check for updates" button with inline states (checking/latest/found+Get/offline). Install via
  DownloadManager + ACTION_VIEW (visit-logs port); REQUEST_INSTALL_PACKAGES added to manifest.

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

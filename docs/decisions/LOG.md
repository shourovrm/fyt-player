# Log

Append-only: `YYYY-MM-DD | decision | why`. Grep this file; do not read it whole.

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
2026-08-23 | v0.2.9 perf wave: single StreamInfo fetch, LoadControl 2s, probe folded into first window, progress StateFlow split, decoder prewarm, 64MB media cache | measured ~1.5-2s slower than PipePipe per start; three concurrent StreamInfo fetches + HLS were the bulk
2026-08-23 | FormatSelector: progressive pair beats HLS manifest; manifest only as fallback | HLS start = master+variant playlist RTTs before first byte and no range chunking; 4.5s->3.2s cold measured
2026-08-23 | Media disk cache keys: yt|id|itag|lmt for googlevideo, sha256(url) otherwise | SimpleCache index persists keys; a raw signed URL on disk would break the persist-canonical-only rule
2026-08-24 | keep org.apache.commons.compress.archivers.zip.** in R8 | ExtraFieldUtils registers classes by reflection; shrunk build crashed first-run python unzip on FRESH install only (adb install -r over old data skipped unzip, masked it). Verify releases with uninstall + install. v0.2.10
2026-08-24 | Fullscreen bar re-hide: bounded re-asserts (rotation + resume, fullscreen-scoped) via setSystemBarsVisible | OEM re-shows bars after in-process rotation and return-to-app; safe unlike bars-follow-chrome because fullscreen layout ignores bar visibility, so no inset churn. v0.2.13
2026-08-24 | ShortsPager gets isPlaying-keyed setKeepScreenOn | screen timed out mid-short; detail player was the only caller
2026-08-24 | In-app update via GitHub releases/latest + DownloadManager install (visit-logs port) | sideloaded app, releases page is the only channel; per-version banner dismissal, manual check in Settings > App
2026-08-25 | Shorts pager: one surface host under the pager, pages are overlays; thumbnail until firstFrameRendered | reparenting the TextureView per page reset the decoder surface twice per swipe; shutter is black until first frame regardless. v0.2.14
2026-08-25 | warmNext(): resolve N+1 in parallel with N's load | swipe before prefetch landed or a fling (playAt) paid two serial resolves
2026-08-25 | Media HTTP 8s/8s timeouts, transport errors skip backoff, one auto re-prepare on network error | 29s spinner then dead on WiFi->LTE handover; PipePipe reloads from saved position for the same codes
2026-08-25 | youtube /shorts/ share links route to the pager | landed in the landscape Detail player before
2026-08-25 | Seek preview: unbounded height, largest storyboard tile, 160/200dp card, timestamp above, growing thumb | squashed+blurry card vs YouTube/PipePipe; the 40dp slider Box clamped the child, most-frames level = smallest tiles
2026-08-25 | Shorts seekbar: always-visible 10dp dot | YouTube's red dot makes grab-and-drag discoverable; the thin line alone hid the handle
2026-08-25 | Detail: no top bar/⋮; player gets ✕/⌄ top-left, PipePipe prev/next rule, bottom row with quality+speed, gradient, red bar on the video edge | user design after YouTube/PipePipe comparison; the title bar duplicated the body title and the ⋮ sheet duplicated the action row. v0.2.15
2026-08-25 | ControlBar: ONE Slider call site for collapsed+expanded | a drag starting on the idle line expands the bar mid-gesture; two call sites = new Slider instance, old drag never finished
2026-08-25 | PlaybackSession.updateCurrentMeta from DetailScreen | share/open-with starts playback on a title-less ref; mini player + notification showed no title
2026-08-25 | Home Music chip = charts.youtube.com VIDEOS chart (WEEKLY) per content country | the Music topic channel is a global editorial feed, ignored the country setting (always US). VIDEOS 400s without chart_params_period_type. v0.2.16
2026-08-25 | Music chip = charts VIDEOS period DAILY (was WEEKLY) | user picked "Daily top music videos"; more local, moves faster
2026-08-25 | QueueBar inside Detail LazyColumn; sheet rows unkeyed; row tap opens watch page; playAt publishes current before startAt | queue invisible on watch page, duplicate-key crash, tap only swapped media then Detail guard wiped queue
2026-08-25 | queue dedup by pageUrl; enqueue false + toast, playNext moves existing entry after current | user rule: same video never queued twice
2026-08-25 | queue bar docked in AppScaffold bottom on Detail too (not a LazyColumn item); inset spacer when nav hidden | user wants queue at bottom above nav, alone when nav hides; docked bars sat under gesture pill
2026-08-25 | Local chip = language-phrased searches, not a chart | no YouTube chart ranks local artists first (BD charts = Bollywood + global; YT Music charts page for BD has no Trending shelf); "নতুন গান" gl=BD returns BD artists, "new song" returns Hindi
2026-08-25 | Music chip = US: topic-channel trending; else global daily chart | user: keep the old trending feed for US, worldwide elsewhere; Local covers the country
2026-08-25 | Detail ✕ pops to Routes.HOME | user: close = leave the video page entirely, not unwind one
2026-08-30 | Similar = YouTube related, title search fallback | user: PipePipe parity; old search-only decision superseded
2026-08-30 | bare VideoRef enriched at persist time (like/playlist/download) | share opens hand a title-less ref; one detail() at a user action is fine, per-row fetch is not
2026-08-30 | download row size/path from media file, not newest file | subtitle sidecar written last stole findProducedFile
2026-08-30 | extractor local patches live on branch fyt-patches, recorded here | uncommitted search-shorts patch would have been lost on the next bump
2026-08-30 | subtitles = separate long-press action, always .srt | user: not a checkbox in the quality list; English else first
2026-08-30 | Detail follows autoplay/auto-advance via autoAdvances counter | page showed A while B played; counter (not `current`) avoids double-push on user taps

2026-08-30 | watched bar red 3dp on dark track; finished video stored as full position | blue 2dp invisible; a cleared row hid "fully watched"
2026-09-03 | Home feed: all subscriptions, watched kept, pin top while loading | user: 2-day-old IndyDevDan upload unfindable (channel outside the 8 cap); refresh landed weeks back (key-retained scroll under progressive inserts)
- 2026-09-24 | Shorts feed uncapped, gated by FEED_CONCURRENCY | newest-8 cap hid older subscriptions' shorts (Squat University report).
- 2026-09-24 | Loop is per video, resets on play() | YouTube shape; a global repeat flag desynced state (OFF) from player (ONE).
- 2026-09-24 | Extractor rebased to PipePipe v5.3.1 (not shipped) | downloads 403 on it; 0.2.21 ships on v5.2.5 (user choice).
- 2026-09-27 | shorts channel link: fill uploader at source, clear playback on open | shorts-tab listings have no uploader; vertical clip must not leak into mini player
- 2026-09-27 | resume after long pause just plays; dead URL recovers via onPlayerError | proactive 50-min re-resolve slowed resume (user-reported); PipePipe v5.4.0 keeps the source prepared, re-extracts only on error
- 2026-10-06 | DECISIONS.md = index; knowledge split into docs/decisions/ topic files | whole file was ~15k tokens read at every session start
- 2026-10-06 | waves.md folded into topic files; closed Open items moved to Current state, 4 resolved ones dropped | a topic file must be complete on its own; stale open items mislead
- 2026-10-06 | signed-in retry only for age/login walls; yt-dlp 429/403 = hard stop | retrying a rate-limit or bot wall with the account cookie breaks the no-bypass rule and doubled time-to-error
- 2026-10-06 | persistent playback trace (DiagLog) in Settings | user-reported slow start + error not reproducible in 14 device starts; logcat holds ~20 min
- 2026-10-06 | retry budget re-arms after 30 s healthy playback | a second blip late in a long video went straight to the error screen
- 2026-10-06 | PO-token theory for v5.3.1 403s dropped | upstream reads the resolver only in the mweb/SABR path
- 2026-10-06 | resume restarts PlaybackService | device: 25 min paused left no service or media session, process alive
- 2026-10-06 | MediaButtonReceiver NOT added | would start the service as FGS before a multi-second resolve; known FGS-timeout crash
- 2026-10-06 | backup import accepts any https video URL, not only registered sources | owner-only rule dropped saved Facebook/TikTok/X videos
- 2026-10-06 | account cookie no longer sent to yt-dlp | header applied to every request of an extraction; engine ignored it for auth
- 2026-10-06 | dislike fetch off by default, opt-in setting | fork default sent every opened video id to a third party
- 2026-10-06 | slow resumed start measured: HTTP/1.1-only CDN + aborted 10 MB probe window + exact seek at 1080p | user-reported slowness; extraction and age wall ruled out by ~45 timed starts
- 2026-10-06 | SponsorBlock whitelist in DataStore, not Room | avoids a schema migration
- 2026-10-06 | YouTube pairs play as local-manifest DASH (v0.2.24) | bounded requests complete and reuse the HTTP/1.1 connection; user chose it over Cronet/HTTP3

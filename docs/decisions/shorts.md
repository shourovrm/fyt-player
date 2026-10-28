# Shorts: pager, shelf, feed, grid

## Current state

2026-10-28 (tests NOT run, device-UNverified): order is by recency slot, not a plain shuffle.
Each channel's newest short gets one `VideoSource.detail` call (session-cached by its pageUrl; a
failure is cached as unknown until the next refresh, which retries it once). Slot 0 = newest short <= 3 days old, slot k = (3k,
3k+3] days, unknown date = last. Inside a slot unseen clips come first, seen clips last, both
shuffled by per-session keys. Watched shorts are NO LONGER removed. Seen = `watch_history` read at
refresh only, so a clip watched mid-session keeps its place. Pager settle writes the clip to
`watch_history` once per session (`ShortsViewModel.recordShown`, honours recordWatchHistory). The
Shorts channel-tab route is not recorded (its pager passes no callback). Pure: `orderShortsFeed`,
`shortsSlotFor`, `parseUploadEpochMs` in ShortsFeed.kt.

2026-10-26 (v0.2.27 + shuffle, DEVICE-VERIFIED order only): feed = 4 clips per channel
(`SHORTS_ITEMS_PER_CHANNEL`), all channels pooled and shuffled per refresh (app start /
pull-to-refresh, never on tab return) via `shuffledPool` (since replaced by `orderShortsFeed`, 2026-10-28); per-clip random keys are kept so clips
already shown never reorder while more channels arrive; load-more rounds are shuffled + appended.
Why: subscription-order round-robin put Squat University (~20th of 22) below the first screen.
Shorts-tab items carry NO upload date (YoutubeShortsInfoItemExtractor returns null), so "recent" =
top of each channel's tab (tab sorts newest first, unverified). Two relaunches gave different orders.

2026-10-06 (tests green, device-UNverified): pager maps page <-> session item by pageUrl
(`sessionIndexForPage`, `pageForSessionIndex`, `sessionHoldsFeed`,
`feedItemsMissingFromSession` in ShortsNav) -- position-based mapping drifted when the feed
re-interleaved or enqueue skipped a duplicate. Swipe decisions now see the index the session
published synchronously, so a fast swipe-back is no longer overridden by the pending resolve.

2026-09-27 DEVICE-VERIFIED: shorts show the channel name and it's tappable (` ›`) -> ChannelScreen.
Shorts-tab items (shortsLockupViewModel) carry no uploader, so `channelTab` fills
uploader/uploaderUrl from the channel (`withChannel`; continuation pages reuse the first page's
name via an in-memory map). Tap = PlaybackSession.clear() + openListing; back re-enters the
pager, which replays the same page.

2026-08-25 (v0.2.14) shorts pager smoothness wave, DEVICE-VERIFIED (Nothing Phone, WiFi+LTE,
screenrecord frame analysis): (1) ONE PlayerView host under the VerticalPager, pages are
overlays -- decoder surface resets per swipe went 2 -> 0; (2) active page keeps its thumbnail
until `PlayerState.firstFrameRendered` (onRenderedFirstFrame) -- black gap per swipe went
0.3-0.8s -> 0 black frames at 10fps; (3) `warmNext`: next item's resolve starts in parallel with
the current one's, so a fast swipe/fling never pays two resolves back to back; (4) media OkHttp
8s connect/read + fail-fast on transport errors + one auto re-prepare in onPlayerError (PipePipe's
setRecovery+reload shape) -- WiFi->LTE handover mid-pager: 29s spinner then dead -> 6.5s buffer
then playing; (5) youtube.com/shorts/ share links open the pager (title blank: URL-only ref, same
as TikTok). Measured baseline before the wave: swipe->first frame ~0.5s, PipePipe has no pager
(Player.java:5013 only flips isVerticalVideo for orientation) so its "shorts" = normal open
~2.75s vs ours 4.5s cold incl. process start.

2026-08-23 (v0.2.7): shorts shelf tap opens the TAPPED clip (device-verified: Detail Similar
shelf card 3 -> card 3 plays, swipe -> card 4). ShortsPager's playback->pager follow effect
reads the LIVE session index + checks the queue is this feed; the `collectAsState` snapshot
it is keyed on is one composition stale and carried the previous queue's index.

2026-09-24 (v0.2.21) DEVICE-VERIFIED (Nothing A059): Shorts grid pull-to-refresh
(PullToRefreshBox). Shorts feed fetches EVERY subscription (`capChannels`/MAX_FEED_CHANNELS
deleted), Semaphore(FEED_CONCURRENCY) on first page and loadMore -- the cap was why Squat
University's shorts never showed.

2026-08-24 (v0.2.13) DEVICE-VERIFIED: shorts pager holds KEEP_SCREEN_ON while playing, cleared on
pause.
- **Shorts screen timeout**: `setKeepScreenOn` was only wired in PlayerScreen; ShortsPager now
  holds the same isPlaying-keyed wakelock.

2026-08-22 wave (v0.2.4): channel search on the fork + shorts shelf; Shorts tab pages per channel.

2026-08-08 field-report wave (tests green, device-UNverified): Similar tab gained the shorts shelf
(`onOpenShorts` threaded through AppShell→DetailScreen); shorts overlay gained HD quality + speed
rail entries (same QualitySheet/SpeedSheet + PlaybackSession seams as PlayerScreen).

2026-08-06 wave (device-verified): shorts pager full-bleed via `FullscreenChrome` with a real
seekbar; mini player on the shorts grid.

- 2026-08-07 shorts/guardrail wave device-verified (this session): back from either shorts pager
  stops playback; channel Shorts tab opens the swipe pager at the tapped clip; resolve failure no
  longer leaves the previous video playing under the guardrail.
- Search Shorts shelf (`ui/ShortsShelf.kt`, `VideoRef.isShort` wired in `NewPipeYoutubeSource.
  toVideoRef` from `StreamInfoItem.isShortFormContent` + `/shorts/` URL fallback): LazyRow as the
  results list's first item (`ResultsListColumn.topContent`), search mode only. `partitionShorts`
  splits shelf vs. regular/queue. Auto-grows to `MIN_SHELF_SHORTS` (20) by pulling up to
  `MAX_SHELF_AUTO_FETCHES` (3) extra search pages. Device-verified (shelf renders, tap opens the
  pager, swipe navigates, ≥20 cards on a generic query).

## Open items

- Shorts in Similar need a signed-in watch-next (sidebar shelf is personalised); revisit when
  YouTube sign-in works. Alternative if wanted: a shorts search on the title as a shelf.
- Shorts "age/CAPTCHA" wall that PipePipe doesn't show: NOT reproduced (55 swipes, anonymous,
  v5.3.1). NewPipeErrors now logs `wall: <ExceptionClass> @ frames` -- next report, read that
  first. Suspects: anonymous visionos vs PipePipe's signed-in tv_downgraded; parallel warmNext.
- Shorts-tab paging landed 2026-08-22 (grid + pager load-more, device-verified growing past the
  old 64-item cap). UNVERIFIED: the "all caught up" end footer (needs every channel exhausted)
  and pager-tail trigger in isolation (shares `loadMore`, grid path proven).
- `loadMore` batches are appended after what is on screen, so a fresh-channel short can land after
  older slots already shown (append-only keeps the pager stable); and a channel landing mid-session
  can shift pager indices (pre-existing).

## Gotchas

- Channel search rows carry NO shorts signal from YouTube (plain videoRenderer, /watch, overlay
  DEFAULT, 16:9 thumb — verified live 2026-08-22); `shortByDuration` (<=60 s) is the only tell.
- Shorts grid and pager are ONE nav route (`Routes.SHORTS`) toggled by `ShortsViewModel.showPlayer`;
  fullscreen gating keys on `FullscreenChrome.active`, not the route string. Shorts from OTHER
  listings (channel Shorts tab) use the separate `Routes.SHORTS_PLAYER` + `ShortsPlayerRequest`
  handoff (list can't ride a nav arg). Back from EITHER pager calls `PlaybackSession.clear()` —
  a vertical clip must never leak into the mini player, which would reopen it in the landscape
  detail player with no swipe navigation.
- YouTube publishes NO upload date on shorts shelf items: both `YoutubeShortsLockupInfoItemExtractor`
  and `YoutubeReelInfoItemExtractor` return null from `getTextualUploadDate()` (checked in the
  v0.26.4 bytecode). Shorts cells show `Channel · views` and that is the honest ceiling — a date
  would cost one fetch per visible tile, which the no-I/O-per-list-item rule forbids.
- Shorts have no upload date, so the feed pays ONE newest-short detail lookup per channel per
  refresh (not per clip). Tried/rejected: per-clip date lookups (N calls, no-I/O rule) and
  dropping watched shorts from the feed (feed shrinks to nothing for heavy watchers).
- The newest short is the first clip of the raw tab page, watched or not: its date is the
  channel's date, so watched-exclusion must not run before the lookup.
- Detail `uploadDate` is the extractor's raw `textualUploadDate`: the microformat date, ISO-8601 with
  the uploader's offset. `DetailScreen` shows that local day; the slot parser reads the same string.
  Tried/rejected: converting it to a UTC `DateWrapper` string (shifts the shown day by up to one day).
  Device-unverified: that shorts really return this date (if not, every channel lands in the last slot).

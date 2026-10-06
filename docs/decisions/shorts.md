# Shorts: pager, shelf, feed, grid

## Current state

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

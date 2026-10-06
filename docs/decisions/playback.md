# Playback: player, session, queue, fullscreen and insets, captions, background service

## Current state

2026-10-06 (v0.2.24, tests green, NOT YET RUN ON A DEVICE -- phone was disconnected): YouTube
video+audio pairs play as ONE `DashMediaSource` built from a local SegmentBase manifest
(`player/DashManifestBuilder.kt`, init/index byte ranges from the extractor's ItagItem via
`MediaFormat.segmentIndex`, `media3-exoplayer-dash` added). Every request is a bounded
`range=` window read to completion, so the HTTP/1.1 connection is reused -- the fix chosen for
the measured slow resumed start (see Open items). Same data stack as before (cache ->
ChunkedRangeDataSource -> OkHttp); a DataSpec(P, L) becomes `range=P-(P+L-1)`, no Range header.
Fallback to the old two-progressive merge with a logged reason (`source=progressive
reason=noSegmentIndex|headersDiffer|notGooglevideo|manifestRejected|manifestParseFailed`);
Single/HLS/non-YouTube unchanged. Playback log prints `source=dash` when it is used.

2026-10-06 PipePipe-port wave (465 tests green, device-UNverified except a play smoke):
`player/PlayerErrorPolicy.kt` -- pure `classifyPlayerError(facts, budget)` -> action, executed
by onPlayerError. BEHIND_LIVE_WINDOW = seekToDefaultPosition + prepare (max 3/item); decoder
init failure with a released surface (IllegalArgumentException mentioning "surface", PipePipe's
test) = prepare() in place, 10 s cooldown; other decoder errors = ONE fallback to the next
lower rendition via selectQuality; expired URL / transport = one re-resolve; a 403 on the fresh
URL is shown, never retried. SponsorBlock: 8 categories x Off / Skip automatically / Show skip
button (`core/SponsorPolicy.kt`, per-category DataStore keys, default sponsor=auto so old
installs behave the same), seekbar markers, "Skip <category>" overlay, per-channel whitelist
(DataStore string set; "Skips on" / "Skips off" switch at the right of Detail's channel line,
full sentence only in the content description -- user found the sentence-long button too long). Still the sha256 4-char prefix fetch. Session takes a
`sponsorPolicy` lambda (was `sponsorBlockEnabled`). Loop control = icon button
(`player/RepeatGlyph.kt`, red when on).

2026-10-06 wave 2 (425 tests green, device-UNverified): skipNext/skipPrevious publish
index/current synchronously (`switchToItem`), like playAt. startAt reads the resume position
in parallel with the resolve and starts the source AT it (`setMediaSource(source, ms)`).
`loadedRef` = what the player really holds; position is persisted before every clear and no
longer on a rebuffer (`shouldPersistOnStop`). `startPlaybackService()` swallows a refused
start (background autoplay on Android 12+). Any resume (toggle, replay, retry) restarts
PlaybackService: DEVICE FINDING 2026-10-06 -- after 25 min paused the process was alive but the
service and media session were gone, so resume played with no notification / lockscreen /
headset control. ChunkedRangeDataSource reads through `WindowChain` (one empty reopen, then
IOException -- was unbounded recursion). Sponsor skip guard clears when position goes back
before the segment (Loop). videoWidth/Height reset on a new item.

2026-10-06 review wave (370 tests green, device-UNverified until noted): autoplay-next lookup is
a stored job (`autoplayJob`), cancelled by startAt/clear and re-checked against the ended item
before play() -- it used to replace whatever the user picked meanwhile. removeAt/move always
drop the prefetch slot and remap `window` (`QueueMath.indexAfterRemove/indexAfterMove`); the
old `i < index` path left stale queue indices, so the next item inherited the previous one's
formats/captions/sponsor segments and prefetch stopped for the rest of the queue.
`setWakeMode(WAKE_MODE_NETWORK)`. resolveItem maps any non-ExtractionError to Unsupported
(`failItem`), plus a scope-level CoroutineExceptionHandler backstop -- a resolver tier bug used
to crash the process. selectQuality keeps playWhenReady. seekTo publishes `progress` (paused
seek no longer snaps back). Retry budget (`retriedIndex`) re-arms after 30 s of continuous
playing. Playback trace: `DiagLog` (app-private file, 64 KB cap, Settings > Video engine >
Playback log) records load/resolve/ready/first-frame ms, toggle branch + paused ms, playerError
code/http/action, non-2xx videoplayback -- class names and codes only, never URLs.

2026-08-25 (v0.2.17) queue on the watch page, DEVICE-VERIFIED: AppScaffold docks `QueueBar` above the nav bar on
every non-fullscreen route incl. Detail (stays when nav auto-hides; nav-inset spacer under the
docked bars when nav is hidden); sheet rows have no pageUrl key (duplicate
enqueue crashed); row tap = playAt + openDetail, and playAt publishes `current` synchronously so
Detail's entry guard does not replace the queue. Music chip = charts VIDEOS + DAILY. Queue
never holds one pageUrl twice: enqueue returns false ("Already in queue" toast), play next moves
the existing entry to right after current (QueueMath.playNextTarget).

2026-08-25 (v0.2.15) player chrome redesign, DEVICE-VERIFIED (Nothing Phone): Detail has NO top
bar and no ⋮ (title lives once, under the video; the ⋮ sheet's only unique item, Play next, is
still on long-press of cards elsewhere). Idle portrait = 3dp red line + 10dp dot on the video's
bottom edge; touching the dot starts the drag AND brings the controls up (one Slider call site in
ControlBar for both states -- a second call site orphaned the in-flight drag, isScrubbing stuck).
Tapped = top-left ✕ (PlaybackSession.clear + pop) and ⌄ (pop, mini player keeps playing), centre
⏮ ⏸ ⏭ with prev/next shown independently only when they exist (PipePipe rule), bottom row
time · 720p · 1x · CC · fullscreen above the bar (portrait) / below it with 24dp side inset
(fullscreen), gradient instead of the black band. Preview-grid button + OverflowControls deleted
(JumpGridSheet/PreviewGridGlyph remain in PlayerOverlays, unused). Queue chip removed from the
action row. Seek preview card 160/200dp, largest storyboard tile, tiles >=45px. Shorts: dot
always visible, 5dp line while dragging, 50x90 tile card on drag. Mini player title was blank on
bare-URL opens: DetailScreen hands the enriched ref to PlaybackSession.updateCurrentMeta.

2026-08-25 seekbar/preview pass vs YouTube+PipePipe (device-verified): scrub card was squashed
to ~27dp by the 40dp slider Box (now `wrapContentHeight(unbounded)`), storyboard level now
largest-tile (160x90, was 80x45 = most-frames), card 160dp portrait / 200dp fullscreen with
titleMedium timestamp above it, thumb 12dp idle -> 20dp dragging, transport hidden while
scrubbing, tiles <60px = pill only. Shorts bar keeps its thin line + an always-visible 10dp dot.
Not done (proposed 5/6): bar on the video's bottom edge with a gradient instead of the black
band; fullscreen 24dp inset; YouTube-style fill-the-video preview.

2026-08-23 (v0.2.9) PERF wave, device-measured tap-to-first-audio (same 3 videos, same network):
before 4.5s cold / 3.1-3.7s warm; after 2.6-3.2s cold / 1.9-2.9s warm; PipePipe 2.9 / 1.7-2.0.
What landed: (1) ONE StreamInfo fetch per video (`source/newpipe/StreamInfoCache.kt`, in-flight
dedup + 60min TTL, fetch runs on its own scope so a cancelled Detail effect can't fail the
playback resolve awaiting it; `ChainResolver.invalidate` -> `tier0.invalidate` clears it; age-wall
retry forces refresh). (2) `DefaultLoadControl` 12s/20s/2s/3s (PipePipe values). (3)
`ChunkedRangeDataSource` first window doubles as the probe (reads Content-Range), a `range=`
refusal falls through to passthrough like the old probe did. (4) position/duration split out of
`PlayerState` into `PlaybackSession.progress` -- only progress bars collect it, the 500ms tick no
longer recomposes MiniPlayer/QueueBar/PlayerScreen/ShortsPager. (5) `WebViewJsDecoder.prewarm()`
1s after first resume: WebView + player upload + compile off the play path (1.7s measured). (6)
64MB `SimpleCache` (`player/MediaCache.kt`) outside the chunked source; keys are `yt|id|itag|lmt`
or sha256(url) -- never a raw signed URL on disk. (7) BIGGEST single win: `FormatSelector` now
prefers a progressive video+audio pair over the HLS manifest (manifest = fallback for live/HLS-
only). HLS cost two playlist round trips before the first byte and bypassed range chunking.
Non-YouTube (FB/TikTok/X): verified one yt-dlp run per open already; 2/4/6 apply to them as-is.

2026-08-23 (v0.2.6): FullscreenChrome claim leak FIXED (device-verified: Detail fullscreen ->
back -> back to Home keeps nav bar + status-bar padding). Any Compose `onDispose` must decide
on the effect KEY, never re-read the state it is keyed on -- by dispose time it already holds
the new value.

2026-09-24 (v0.2.21) DEVICE-VERIFIED (Nothing A059): Replay = centre button turns into a Refresh
glyph at STATE_ENDED (`PlayerState.ended`), togglePlayPause seeks 0 + plays; controls come up at
the end. "Loop" text button in the bottom row (red when on) = RepeatMode.ONE, per video (play()
resets the player's repeatMode).

2026-08-30 (v0.2.20) DEVICE-VERIFIED: Queue sheet: Shuffle text button (no glyph in icons-core).

2026-08-24 (v0.2.13) DEVICE-VERIFIED (Nothing Phone, gesture nav): fullscreen entry+rotation = no
bars; Home->relaunch while fullscreen = bars stay hidden (ON_RESUME re-assert); exit = no
right-shift. Still unverified on a 3-button-nav OEM device (the original reporter's hardware):
- **Fullscreen nav/status bar reappearing** (3-button-nav OEMs): the single entry hide got undone
  by the OEM re-showing bars after the in-process rotation and after return-to-app. Fix: bounded
  fullscreen-scoped re-hides in PlayerScreen (orientation key + ON_RESUME), reusing
  `setSystemBarsVisible`. NOT bars-follow-chrome (still banned, see Tried/rejected) — PipePipe
  re-asserts the same way; safe because layout ignores bar visibility while fullscreen
  (`contentWindowInsets = WindowInsets(0)`), so re-hides can't churn insets. Exit path untouched.

2026-08-10 (v0.2.3) device-verified on the A059: shuffle order remapped through every queue
mutator; Detail re-entry takes playback (Similar-chain back mismatch fix); FullscreenChrome
claim-counted (shorts-from-Similar full-bleed fix).

2026-08-09 wave (tests green, device-UNverified): `PlaybackSession.play()` now does
`stop()+clearMediaItems()` first: new-queue start while something else played left the old item
running (audio + frame) on the shared surface ~1 s until the async resolve landed — the "shorts
shows previous video" flash. clearMediaItems is what actually closes PlayerView's shutter (stop
alone can skip the same-period check).

2026-08-08 field-report wave (tests green, device-UNverified): `PlaybackSession.retryCurrent()` +
Retry button on player error states (except AccessChallenge — honest wall keeps no retry) and
togglePlayPause routes to it on error/STATE_IDLE (stuck-after-background fix); player gestures got
edge dead zones (24dp sides / 32dp bottom for system back/home), 24px slop before mode lock, and
full-height-drag ≈ 150% range sensitivity (float accumulator); h:mm:ss time labels
(`formatPosition` seam, feeds mini player too); tap in fullscreen shows system bars with the
chrome (entering fullscreen seeds controlsVisible=false or paused video would pin bars on) --
REVERTED since, see Tried / rejected; PipePipe queue semantics — row taps everywhere open Detail
(single-item play), whole-list play only via explicit Play All/Background; autoplay-next pref (off
default, title-search based, honest subtitle) via injected `autoplayNext` lambda + STATE_ENDED
latch.

2026-08-07 wave (device-verified): Queue **close = clear** (`PlaybackSession.clearQueue`, × on the
strip + "Clear" in the sheet) keeps the playing item and drops the rest. Both seekbars got real
touch targets (40dp bounds, unchanged 2.5/3dp art) and the shorts bar clears the nav-gesture zone.

2026-08-06 wave (device-verified): Queue append works after queue exhaustion and toasts feedback.
Background playback works: `PlaybackSession.play()` starts `PlaybackService` (media3
MediaSessionService), notification/lockscreen/Bluetooth controls, `Prefs.backgroundPlayback`
honored reactively (pause on ON_STOP when off). Captions: `Resolved.captions` →
`SingleSampleMediaSource` merged per track, off by default, CC button + `CaptionSheet` picker,
selection survives quality switch. Edge-to-edge chrome (scaffold background behind status bar).

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

## Open items

- Headset/lockscreen play after the service died: `onPlaybackResumption` + last-played prefs
  are wired (`Prefs.lastPlayed`, page URL + title only) but DORMANT -- the manifest has no
  `androidx.media3.session.MediaButtonReceiver`. Not added on purpose: it starts the service as
  a foreground service, and a 2-5 s resolve before promotion is exactly the
  ForegroundServiceDidNotStartInTimeException in Gotchas. Needs a device session to try.
- Everything in the two 2026-10-06 waves is device-unverified (phone was in use by another
  session). Verify: fresh install, search-play-download, queue remove-above-current, shorts fast
  swipes, pause quality switch, paused seek, long-pause resume + notification, cancel during
  resolve, 1080p-cancel-then-720p download.
- SLOW START, MEASURED 2026-10-06 on the A059 (WiFi + VPN, v0.2.23 + waves, instrumented
  throwaway build). Fresh video: resolve 1.4-2.6 s (one 4.6 s), then ~1.1 s to first frame.
  Video WITH a saved resume position: +1.5-3.5 s (worst seen 7-10 s tap-to-frame on a cold
  process). Not the age-wall retry: none ran in ~45 starts. Causes, each measured:
  (1) googlevideo rr hosts are HTTP/1.1-only over TCP (`curl --http2` -> 1.1, no ALPN; h3 works),
  so OkHttp cannot multiplex; (2) a resumed start first opens a 10 MB window at 0, reads
  1-36 KB (moov+sidx) and aborts it, which kills that connection -- the seek request then pays
  a second full connect (new conn ttfb ~430 ms vs ~135 ms reused; two in a row);
  (3) exact seek: after the seek request, 1.4-2.5 s to download keyframe->position at 1080p on a
  ~1.3 MB/s link. Media3 1.9.4 applies SeekParameters only in seekToInternal, NOT to the initial
  position, so a keyframe-snapped resume is not available by a setter.
  CHOSEN + BUILT in v0.2.24, device-UNVERIFIED: PipePipe-style DASH manifest from init/index
  ranges. Verify first on reconnect: Playback log says `source=dash`; plays, seeks, A/V in sync;
  resumed start sourceSet->firstFrame vs the numbers above; range= chunks answer 200; captions;
  quality switch; whether visionos responses really carry initRange/indexRange (else every
  video logs reason=noSegmentIndex and nothing changed). Cause (3), the exact-seek download, is
  NOT addressed: next step would be resuming at the containing segment's start once the index
  is known. Not chosen: small head window; Cronet/HTTP3 (UDP often blocked on VPN, big dep).
  The user's "shows an error msg" was never reproduced -- read Settings > Playback log first.
- SponsorBlock: enabled-off pref, k-anonymity segment fetch (sha256 4-char prefix, never the
  full video id), auto-skip in the session ticker. Device playback verified but an actual
  sponsored-segment skip is still user-unverified.

## Gotchas

- Retry re-arm after 30 s healthy play means a host that serves ~30 s then 403s every fresh URL
  re-resolves once per >=30 s, unbounded. Accepted (PipePipe has no bound at all); cap it if a
  report shows that pattern in the Playback log.
- Landscape cutout is a 126px LEFT system inset on this OEM; any nested Scaffold/TopAppBar re-pads
  it into a grey strip unless AppScaffold consumes WindowInsets.displayCutout (device-verified).
- `AppScaffold` consumes system-bar insets for the whole app. A full-bleed surface opts out via
  `ui/AppScaffold.kt`'s `FullscreenChrome.active` seam (set by `DetailScreen`, same package) —
  used by the fullscreen player; a future full-bleed screen (shorts pager) reuses the same seam.
- Storyboard tile interval is derived as `duration / tileCount`, not published by the engine. Scrub
  previews may drift on very long videos until measured on a device.
- `Protocol.DASH` formats (yt-dlp tier) still throw in `MediaItemFactory`: the dash dependency
  exists now, but the DASH path is built only from PROGRESSIVE YouTube pairs.
- There is exactly ONE shared video surface. `AppScaffold` therefore hides the mini player and
  queue bar on the detail route — mounting both would have the mini bar steal the surface from the
  full player mid-playback.
- Only ONE screen may hold the shared video surface at a time. `AppScaffold.isFullPlayerRoute`
  gates the mini player and queue bar off those routes — add any new full-bleed route to it.
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
- A resolve failure for the current item must stop + clear the player: the previous queue item
  otherwise keeps playing under the error guardrail and auto-advances over it later.

## Tried / rejected

- Proactive re-resolve on resume after 50 min paused (`isStale`) — REMOVED 2026-09-27: made
  every long-paused resume pay a full extractor call though YouTube URLs live ~6h. PipePipe just
  plays and recovers on error; so do we (onPlayerError, retry re-armed per play press).
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
- Once-per-nav-entry autoplay latch on Detail (rememberSaveable `autoplayed`) — REMOVED
  2026-08-10: backing from C to B left C playing over B's page (user-reported mismatch). Detail
  re-entry now takes playback whenever the session plays a different pageUrl; same-video re-entry
  stays a no-op so reopening from the mini player never restarts.
- Boolean FullscreenChrome.active — replaced with claim counting 2026-08-10: nav-transition
  overlap (incoming pager composes before outgoing Detail disposes) let Detail's onDispose stomp
  the pager's `true`; nav bar stayed visible on shorts-from-Similar. Never a single global
  boolean for overlapping lifetimes.

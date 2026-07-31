# Session summary — 86303cd0

FYI Player, waves 1–5 build + on-device verification. Parallel subagent development, YouTube first.

## 1. Requests and intent

- Explain the repo's md files (CLAUDE.md = rules, DESIGN.md = target shape, DECISIONS.md = knowledge).
- Build the full app with parallel subagents, YouTube first, through wave 5. A local reference app was used for UI/design guidance only.
- **Confidentiality constraint:** no generated file may name or describe the reference app, its sites, its copy, or its languages. Enforced with a repo-wide grep before every commit — always clean.
- **Build constraint:** reuse the existing Gradle setup (disk is tight); no debug APK; arm64 release only, copied to the repo root.
- **Device constraint:** lockscreen password used transiently for one `adb shell input text` during testing; never written to any file, log, or commit.

Features added during the session: channel subscribe; home feed from subscriptions; similar videos (search-derived) instead of "more from this channel"; Comments + Similar as two tabs; channel page with Videos/Shorts/Playlists/Courses/Live + in-channel search and play-all/selected; engine update in Settings; download quality asked every time; delete confirmation (list-only vs. files too) for both the row X and "clear completed"; shorts reworked to grid → fullscreen pager with tap-to-pause, progress bar, like/share/add-to-playlist.

Research only, no implementation: Facebook, Instagram, TikTok, X/Twitter (+xcancel) compatibility; storyboard thumbnail preview feasibility.

## 2. Key technical decisions

- Kotlin, Compose, Material 3, single Activity (`com.fyiplayer.app`).
- `youtubedl-android` 0.18.1 — bundles a Python runtime + yt-dlp at `res/raw/ytdlp`; `ffmpeg` artifact for muxing.
- media3 1.9.4: ExoPlayer, HLS, session, OkHttp datasource; `MergingMediaSource` pairs video-only + audio-only.
- Room v3 with real migrations 1→2, 2→3; DataStore Preferences; kotlinx.serialization.
- Navigation-Compose, all four transitions `None`, no `popUpTo` so back restores scroll.
- Process-scoped `PlaybackSession` object (not a ViewModel); one shared texture `PlayerView` reparented between hosts.
- Foreground services: `mediaPlayback` (PlaybackService) and `dataSync` (DownloadService, Android 15 `onTimeout` parking).
- Layering `ui → player → source+engine → core+data`. Extraction never in UI; one `StreamResolver` seam; only canonical page URLs persisted.
- Subagents never run Gradle (stale-jar false greens); main session does the one clean build. "Port, don't reinvent."

## 3. Load-bearing code

**`engine/EngineExec.kt`** — the single choke point every engine call routes through.

```kotlin
internal suspend fun runEngine(url: String, vararg options: String): String {
    // The engine appends the URL after the options with no end-of-options separator...
    require(!url.startsWith("-")) { "refusing an option-shaped engine argument" }

    EngineGate.await()
    // suspendCancellableCoroutine runs its block on the CALLING thread, and the engine call below
    // blocks on a subprocess for seconds... the system kills the app for not answering input.
    return withContext(Dispatchers.IO) {
        val processId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { YoutubeDL.getInstance().destroyProcessById(processId) }
            try {
                val request = YoutubeDLRequest(url).apply { options.forEach { addOption(it) } }
                val response = engineLock.read {
                    YoutubeDL.getInstance().execute(request, processId, null)
                }
                cont.resume(response.out)
            } catch (e: Exception) {
                cont.resumeWithException(mapEngineError(e))
            }
        }
    }
}
```

**`res/layout/shared_player_view.xml`** (new) — `surface_type` is only read from *compiled* XML; a hand-built `AttributeSet` throws `ClassCastException`.

```xml
<androidx.media3.ui.PlayerView
    app:surface_type="texture_view" app:use_controller="false" />
```

**`source/youtube/YoutubeSource.kt`** — root cause of the empty home feed:

```kotlin
/**
 * A bare channel URL does NOT list videos — the engine returns the channel's TABS as playlist
 * entries ("… - Videos", "… - Shorts"), which carry no video id and are correctly skipped...
 */
override suspend fun listing(listing: Listing, page: String?): SearchPage {
    val url = when (listing.kind) {
        Listing.Kind.CHANNEL -> channelTabUrl(listing.key, ChannelTab.VIDEOS)
        Listing.Kind.PLAYLIST -> listing.key
    }
    return flatPlaylistPage(url, page)
}
```

`homepage()` now throws `Unsupported` honestly (trending is dead). `channelTab` / `channelContainers` / `searchChannel` added; tab-unavailable detected via `TAB_UNAVAILABLE_PREFIX`.

**`ui/HomeViewModel.kt` + `ui/ShortsViewModel.kt`** — the DataStore-timing bug that silently emptied both feeds:

```kotlin
fun loadFeedIfNeeded(sources: List<VideoSource>) {
    // The enabled-source set is read from DataStore, so the FIRST composition always sees an
    // empty list. Loading then yields an empty feed and latches loaded=true...
    if (sources.isEmpty()) return
    ...
}
```

**`ui/HomeFeed.kt`** — a failure is never reported as "nothing here":

```kotlin
internal sealed interface ChannelFetchOutcome {
    data class Ok(val items: List<VideoRef>) : ChannelFetchOutcome
    object NoContent : ChannelFetchOutcome
    object Failed : ChannelFetchOutcome
}
```

**`download/DownloadQueue.kt` / `ProgressParsing.kt`**

```kotlin
// Without --newline the engine rewrites one progress line with \r, so the line
// reader that feeds the callback below never sees a complete line and progress
// stays at zero for the whole download.
addOption("--newline")
addOption("--progress-template", PROGRESS_TEMPLATE)
```

```kotlin
/** The engine writes a bare `NA` (not valid JSON) for a value it doesn't have yet... */
private val NA_TOKEN = Regex(""":\s*NA\s*(?=[,}])""")
```

API split into `resolveOptions(ref)` / `start(ref, option)`, plus `cancelAndDelete()` / `clearCompletedAndDeleteFiles()`.

**`ui/LibraryScreen.kt`** — root-cause fix for the download force-close:

```kotlin
/**
 * Always hops to the main looper instead of trusting the call site. Several callers report the
 * result of a write launched on the process-wide scope, which runs on Dispatchers.Default —
 * and `Toast` throws on a thread with no Looper.
 */
internal fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
}
```

Others: `core/Contracts.kt` (VideoRef/SearchPage/MediaFormat/Resolved/SeekThumbnails/ExtractionError/Listing/VideoDetail/Comment/ChannelTab/ListingPage/VideoSource/StreamResolver), `ui/ChannelScreen.kt` + `ChannelViewModel.kt`, `ui/SimilarVideos.kt` (`buildSimilarQuery`), `ui/DownloadQualityDialog.kt`, `ui/ShortsGrid.kt`, `settings/EngineSettings.kt`, `engine/EngineUpdater.kt`, `data/backup/*`, `data/db/Subscription.kt`.

## 4. Bugs and fixes

1. `private companion object` inside a standalone `object` → private top-level val.
2. QueueMath test asserted `1` where its own comment said `order[0]` (= 2). Impl was right; test fixed.
3. `Icons.Filled.List` deprecated → `Icons.AutoMirrored.Filled.List`.
4. `removeEldestEntry` needs `MutableMap.MutableEntry`.
5. `Modifier.padding(horizontal=, top=)` — no such overload. Hit twice.
6. Public property exposing an internal type — hit 4×.
7. A subagent died on a content-filter block after reading reference files. Fixed by splitting the task and forbidding that agent from reading the reference at all.
8. BackupSecurityTest false failure — a whole-document scan for "cookie" matched the export page's own disclosure sentence. Tripwire scoped to the JSON payload.
9. **ANR reported as a force-close on opening a video** — `Waited 5000ms for MotionEvent`. `runEngine` ran the blocking subprocess on the caller's thread. Fixed with `withContext(Dispatchers.IO)`.
10. `ClassCastException` at SharedVideoSurface.kt:25 — caused by my own constraint (res/ off-limits to that agent), so it hand-built an `AttributeSet`. Fixed with a real layout + LayoutInflater.
11. **HTTP 403 on every media URL / "glitchy player" / "only showing child space"** — bundled engine was yt-dlp `2025.11.12` against a current `2026.07.04`. Verified by extracting `res/raw/ytdlp` from the aar and reading `yt_dlp/version.py`. Fixed by the in-app engine updater; 0 403s afterwards.
12. Retry storm — `retriedIndex = null` in `startAt` re-armed the guard on every re-resolve.
13. Lying UI copy — "Stream link expired. Retrying…" when no retry runs.
14. Downloads never ran — action-sheet writes in `rememberCoroutineScope` were cancelled by the sheet's own dismissal. Moved to `app.appScope`.
15. Download force-close — my own regression from #14: `Toast.makeText` on `Dispatchers.Default`. Fixed at the shared helper.
16. Download progress stuck at 0% — missing `--newline` plus bare `NA` breaking JSON. Unit tests passed because fixtures were hand-written with well-formed numbers.
17. Shorts falsely claimed "none of your subscribed channels post Shorts" — per-channel errors all collapsed into "contributes nothing". Made outcomes explicit + logged → device printed `no source for sourceId='youtube'` → root cause was the DataStore first-emission latch (#see §3).
18. `rememberPagerState` empty-range crash, exposed by the previous fix. `when` restructured so the pager is unreachable when items are empty.
19. Home feed empty — bare channel URL returns channel *tabs*, not videos.
20. Download failure during device test — traced by local reproduction to YouTube's `Sign in to confirm you're not a bot`. **Not an app bug**; classified as `AccessChallenge`, stopped, no retry storm, no cookies.

## 5. What the session established

- Unit tests were consistently blind to the defects that actually broke the app: wiring, async timing, thread affinity, engine-output shape. Every real bug came from the device run.
- Diagnostic pattern: when the UI asserts something it cannot know, fix the honesty first — the corrected message then names the real cause.
- Every platform/feature claim was checked against the live extractor, not memory (trending dead, mixes rejected, channel tabs, channel search, comments, storyboards, and the four researched platforms).

## 6. Device verification (Nothing A059, Android 16, arm64-v8a)

Confirmed working: home feed (real uploads, round-robin interleaved); Similar | Comments tabs with honest captioning; fullscreen at 2392×1080, edge-to-edge, orientation from decoded video size, back exits without interrupting playback; channel page with Subscribed state, play-all, and tabs; Courses tab correctly vanishing for a channel without one; download quality picker (1080p→144p); delete confirmation naming the video with the safe option first and neither destructive choice defaulted; **a 298 MB download completed end to end**; shorts grid (3-column, no bogus duration badges) and the shorts player's like/add/share rail, title, details and progress bar; mini player + queue bar carrying playback across screens.

Blocked by an environmental anti-bot wall (reproduced outside the app, so not a defect): shorts playback in motion and a second download through the new picker. **Not claimed as verified.**

## 7. Final state

179 tests green · leak scan clean · working tree clean · `9910405 feat: shorts grid and overlay controls` · `fyi-player-0.1.0-arm64-release.apk` (66 MB, `lib/arm64-v8a` only, gitignored) at the repo root.

## 8. Open items (surfaced by review, not requested as work)

- `PlaybackService` is never started → no background playback, lockscreen or Bluetooth controls, though DESIGN.md calls it first-class. `Prefs.backgroundPlayback` is read by nothing.
- Playback positions never written (`PositionsRepository.save` has zero call sites) → resume bars always empty.
- Signed thumbnail URLs still persisted for Likes and PlaylistItems (HistoryRepository already strips the query string).
- `unescapeForScript` can corrupt a backup round trip; `BackupSettings` renders raw exception messages.
- Download/like/share unreachable from the Channel and Listing screens — long-press enters multi-select there instead of opening the action sheet.
- Shuffle-order desync and duplicate-queue-key crashes from the correctness review.
- Nav bar stays visible behind the full-screen Shorts pager (the mini player is correctly suppressed).
- Fresh installs ship the stale bundled engine and need a manual Settings update before anything plays.

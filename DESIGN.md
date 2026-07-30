# FYI Player — design template

The blueprint to build against. Read with `CLAUDE.md`, which holds the standing rules.
This describes the target shape, not a finished app. Nothing here has been built yet.

---

## 1. What the app is

A single-Activity Android app (Kotlin, Jetpack Compose, Material 3) that browses, plays, queues and
downloads videos from mainstream social and video platforms, using a yt-dlp-class extraction backend.

Distributed as a sideloaded APK. Background playback is a first-class feature.

## 2. Non-negotiables

These shape the architecture; violating one usually means the design is wrong, not the rule.

1. **Extraction never touches UI.** All markup/API parsing lives in one module per platform under
   `source/`, behind a single interface. UI knows a `VideoRef`, never a selector.
2. **One resolver seam.** Media URLs come only from the `StreamResolver` chain.
3. **Canonical page URLs are the only identity that gets persisted.** Signed/tokenised media and
   image URLs are held in memory, never written to the database, a log, an export, or a bug report.
4. **Stop at every wall.** Login, CAPTCHA, paywall, geo-block, age wall, DRM, rate limit → render an
   honest unavailable state. No retry storms, no signature reconstruction, no request forging.
5. **Per-platform cookie isolation.** Never one shared jar.
6. **Anything called per visible list item does no I/O.** Derive from what the listing already gave
   you, or return null.

## 3. Architecture

Four layers, each depending only downward:

```
ui/            Compose screens + the one app shell
player/        playback engine, surface, gestures, chrome
source/  engine/   platform adapters; extraction + resolver chain
core/  data/       contracts, Room, DataStore
```

`core` holds the contracts everything agrees on and depends on nothing else.

### Package layout

```
com.fyiplayer.app
├─ core/          VideoRef, SearchPage, Listing, VideoDetail, MediaFormat, Resolved,
│                 SeekThumbnails, ExtractionError, VideoSource, StreamResolver, SourceRegistry
├─ data/          Room entities + DAOs, repositories, DataStore prefs, backup model + io
├─ engine/        extraction-engine resolver, headless WebView fallback, chain, engine updater
├─ source/<name>/ one module per platform; parsing only, pure functions where possible
├─ player/        PlaybackSession, PlayerState, shared surface, gestures, overlays, sheets
├─ download/      queue, service, progress parsing, screen
├─ settings/      one file per settings section
└─ ui/            AppScaffold, AppShell (NavHost), screens, mini player, queue bar
```

## 4. Core contracts

Sketch of shape, not final signatures.

```kotlin
data class VideoRef(
    val sourceId: String,
    val pageUrl: String,      // canonical, the one persisted identity
    val remoteId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val uploader: String? = null,
)

interface VideoSource {
    val id: String
    val displayName: String
    fun matches(url: String): Boolean            // does this source own this URL

    suspend fun search(query: String, page: String? = null): SearchPage
    suspend fun homepage(page: String? = null): SearchPage
    suspend fun detail(ref: VideoRef): VideoDetail          // related, uploader, tags
    suspend fun listing(listing: Listing, page: String? = null): SearchPage

    val providesShorts: Boolean get() = false
    suspend fun shorts(page: String? = null): SearchPage

    suspend fun seekThumbnails(ref: VideoRef): SeekThumbnails?   // may fetch
    fun previewThumbnails(ref: VideoRef): SeekThumbnails?        // MUST NOT fetch; see rule 6
}

interface StreamResolver { suspend fun resolve(ref: VideoRef): Resolved }
```

Unimplemented capabilities default to a typed `Unsupported` error, never a silent empty list — an
honest gap is debuggable, a fake empty is not.

`SourceRegistry` is the only place a platform is named. It exposes three deliberately distinct
views: `all` (registration, settings, URL resolution), `browseSources` (Home + search) and
`shortsSources` (the vertical pager). A short-form-only platform belongs in the third but not the
second, while staying in `all` so its saved URLs still resolve.

## 5. Screens and navigation

Bottom nav: **Home · Shorts · Library · Downloads · Settings**.

| Route | Screen |
|---|---|
| `home` | per-source tabs, search field, infinite list |
| `detail/{pageUrl}` | pinned player header + metadata, storyboard, related |
| `listing/{key}` | a channel/tag/uploader listing |
| `shorts` | vertical full-screen pager |
| `library` | Likes tab + Playlists tab, multi-select |
| `playlist/{id}` | one playlist, multi-select, play all |
| `downloads` | queue with pause/resume/cancel |
| `settings` | sections |

**One shared shell** (`AppScaffold`) wraps the whole NavHost and owns:
- window insets, consumed exactly once (no screen pads for system bars itself),
- the bottom nav and its scroll auto-hide, driven by one hoisted nested-scroll connection,
- the mini player and the queue bar.

Set all four NavHost transitions to `None`. Back must restore the previous tab and scroll position —
avoid `popUpTo`; return to an existing back-stack entry instead so state survives.

## 6. Playback — the part worth getting right

**Ownership.** A process-scoped `PlaybackSession` object owns the player, the queue and the current
`PlayerState`. Not a composable, not a ViewModel: it must outlive every screen, or navigating away
from a playing video leaves audio running with no UI handle on it.

**Background.** Keep `MediaSessionService` + a `mediaPlayback` foreground service, so background
audio, lockscreen and headset/Bluetooth controls all work. Set audio focus and becoming-noisy on the
player explicitly — the session does not provide them.

**Surface handover.** One `PlayerView` (texture-based), created once and reparented between the full
player and the mini bar. Rebuilding it per screen is what makes minimising flicker.

**Queue.** Resolve the current item and at most one ahead — never the whole list. Stream URLs are
signed and short-lived, so resolving item 40 while item 3 plays hands the player dead links.
Keep the pure index maths in its own file so it is JVM-testable.

**Mini player + queue bar.** Docked above the nav: a slim strip showing position in queue that
expands to a tappable list, plus the mini bar with the live surface. The mini bar outlives the nav
bar when auto-hide slides it away — playback is never what disappears.

**Player chrome.** One 48dp control row (elapsed · scrubber · total · fullscreen), transport centred
as an overlay, everything else behind an overflow menu. Gestures: vertical drag for
brightness/volume, horizontal drag to scrub with a thumbnail preview, accumulating double-tap seek,
single tap toggles chrome only — never pause.

**Seek thumbnails.** Model both shapes behind one type: a flat list of stills, and sprite sheets with
`(cols, rows, tileWidth, tileHeight, count)`. Crop a sprite tile by drawing the sheet oversized with
an offset inside a clipping parent — no bitmap-region decoding, and the image cache serves every tile
of a sheet from one fetch. Reuse the same mapper for the scrubber preview and a 3×3 "jump to" grid.

## 7. Data

**Room** — watch history, search history, playback positions, likes, playlists, playlist items,
per-domain cookies, downloads. Keys are canonical page URLs.

**DataStore** — settings (enabled sources, resolution per network type, container, gestures,
history toggles). Keep separate stores for unrelated concerns.

**Backup** — one file, exported and imported through the Storage Access Framework. Make it an HTML
page that renders the library readably, with the machine copy embedded as JSON in a
`<script type="application/json">` block: readable without a tool, lossless on re-import. Import is
additive and idempotent — match playlists by name, videos by page URL, never delete. Show counts
before writing. Exclude cookies, any passcode, and every thumbnail URL.

**Downloads** — background queue with pause/resume via the engine's continue flag. Handle the
Android 15 `dataSync` foreground-service timeout: park the running row as resumable and stop
cleanly, or the process is killed with an unhandled-timeout exception.

## 8. Pitfalls

Each of these has cost real debugging time. Not obvious from docs.

- A foreground-service notification **cannot be hidden**, and a user blocking the app's
  notifications does not suppress it.
- Navigation-Compose's **default transition is a ~700ms cross-fade** — an outgoing screen with video
  still drawing gets cross-faded over the incoming list, which reads as a rendering tear.
- A nav route pinned to one id **does not track a queue that advances underneath it**. Follow the
  playback state, during composition — a `LaunchedEffect` leaves one frame where the two disagree.
- `Modifier.size()` **coerces into the parent's constraints**. A child that must exceed its parent
  and be clipped needs `requiredSize()`.
- A fresh player-state object must **seed `isPlaying` from the player**: `onIsPlayingChanged` fires
  only on a change, and skipping between two playing items changes nothing.
- `AwaitPointerEventScope` is `@RestrictsSuspension` — a helper must be a local extension *on* the
  scope, not a plain local `suspend fun`.
- `kotlinx.serialization` **omits fields on their default** unless `encodeDefaults = true`; a
  format-version field then silently vanishes and its gate can never fire.
- XML comments may not contain `--` — resource merging fails with a bare `ParseError`.
- `material-icons-core` has **no** Pause / SkipNext / SkipPrevious / Fullscreen / Shuffle glyph.
- Blank image tiles can mean **bitmap pressure**, not a wrong crop.
- SAF import should accept `*/*`: pickers report a saved `.html` as `text/plain`.
- Timed press gestures race cold media loads and lose. A **directional drag needs no timers** —
  touch slop separates it from a tap for free, and a vertical drag still scrolls the list.
- Never infer system-chrome state from inset visibility; have the screen that changes it say so.

## 9. Build order

Each phase ends with a green build, passing tests, and a run on a real device.

1. **Skeleton** — Gradle, Compose, theme, `AppScaffold` + NavHost, empty screens, bottom nav.
2. **Contracts** — `core` types, `SourceRegistry`, `StreamResolver` chain over the extraction engine.
3. **First platform** — one source end to end (search → detail → resolve → play) as the reference
   the others are written against. Save fixtures; unit-test the parsers against them.
4. **Playback** — session, surface handover, background service, gestures, chrome, queue.
5. **Library** — likes, playlists, multi-select, history and positions.
6. **Downloads**.
7. **Remaining platforms**, one module each, against the fixtures pattern from phase 3.
8. **Shorts pager**, **seek thumbnails**, **backup**.

## 10. Definition of done

- Test suite green, and the changed flow driven end to end on a real device.
- No signed URL in the database, in a log, or in an export.
- Every wall renders an honest unavailable state.
- `DECISIONS.md` updated in the same commit as the change it describes.
- No comment describing behaviour the code does not have.

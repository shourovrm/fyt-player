# Build: Gradle, R8, release, project shape, compile quirks, verification backlog

## Current state

2026-08-23 (v0.2.8): app renamed to "FYT Player" -- launcher label, backup copy/filename, docs,
rootProject.name only. applicationId stays `com.fyiplayer.app` (a new id = a new app on Android,
no in-place update, all local data lost). README.md added; repo public at github.com/shourovrm/fyt-player.

Signed arm64-v8a release APK builds; 203 JVM unit tests green. Compose + Material 3, single Activity.

In: `core/` contracts + `SourceRegistry`; `data/` (Room + DataStore + repositories); `ui/` shell
(AppScaffold + NavHost + placeholder screens); `engine/` (gate, resolver, error mapping, read-only
WebView tier, chain); `source/youtube/`; `player/` (queue maths, format selection, session, media
session service, shared surface). `FyiApp` wires the resolver into `PlaybackSession` and kicks off
engine init.

Device-verified on a Nothing A059 (Android 16) through the 2026-08-06 wave: home feed meta lines,
shorts grid thumbnails+meta, Channels tab, background-play notification/lockscreen, captions
(bottom), channel page + subscribe, edge-to-edge, 60s+ playback with no FGS crash.

- Module: single `:app`, package `com.fyiplayer.app`, minSdk 26 / targetSdk 35, AGP 8.13, Kotlin 2.4.
- Extraction backend: `youtubedl-android` 0.18.1 (library + ffmpeg). YouTube needs no HTML parsing —
  the engine returns search, metadata and formats as JSON.
- Release-only. Debug variant is never built; there is no debug keystore in this repo.

2026-08-24 (v0.2.13): Settings manual update check hit live GitHub API -> "You're on the latest
version" (0.2.13 > released 0.2.12). Banner untestable until a release newer than installed
exists.
- **In-app update** (`update/UpdateCheck.kt`, `update/ApkInstaller.kt`): GitHub
  releases/latest (unauthenticated; tag_name minus "v", first .apk asset), `isNewer` numeric
  segment compare (unit-tested, garbage tag -> false). Auto check once per process
  (FyiApp.onCreate, silent offline) -> dismissible banner above content in AppScaffold (hidden on
  full-player routes; dismissal per version in Prefs.dismissedUpdateVersion). Settings > App:
  "Check for updates" button with inline states (checking/latest/found+Get/offline). Install via
  DownloadManager + ACTION_VIEW (visit-logs port); REQUEST_INSTALL_PACKAGES added to manifest.

2026-08-10 (v0.2.3) device-verified: R8+ resource shrinking ON (59.5 MB APK, proguard-rules.pro,
search/play/download exercised under minify).

2026-08-08 field-report wave (275 tests green, device-UNverified): crash visibility: `CrashLog.kt`
uncaught handler writes class-names+frames only (never messages — they carry URLs), "Last crash"
viewer row in EngineSettings.

- 2026-08-07 wave device-verified on the Nothing A059 EXCEPT sign-in (user is testing that
  themselves): language/country (results shift region, applies live and across cold start),
  Courses tab (freeCodeCamp: two learning paths), description tab (HTML rendered, entities
  decoded, timestamp link seeks in place 15:03 -> 15:33, no navigation), queue × ("1 of 16" ->
  gone, playback continued; enqueue -> "1 of 2"), both seekbars drag (video 16:01 -> 63:01,
  shorts ~70%).

## Open items

- Device-verify the 2026-08-09 wave (v0.2.2): home recency sort, playlist
  channel link (needs a NEWLY-added playlist item — old rows have null uploaderUrl), share-with
  from YouTube/FB/Twitter apps cold+warm, shorts-entry flash gone.
- User-verify v0.2.3: R8 build (66→~59.5 MB), channel rows "N subscribers", shorts-from-Similar
  full-bleed — all three device-verified by me already. STILL DEVICE-UNVERIFIED (USB dropped
  mid-check): back-return playback switch, YouTube download real mp4 (was .m3u8), no per-segment
  probe 400s. Verify these first on reconnect.

## Gotchas

- `AndroidManifest.xml` declares a service only in the phase that adds its class — a declaration
  pointing at a missing class is a runtime crash, not a build failure.
- Release APK is ~66 MB at skeleton size; the engine ships a Python runtime. `abiFilters` is pinned
  to `arm64-v8a` because a universal APK triples that.
- `libc++_shared.so` ships in more than one native artifact; `pickFirsts` in `packaging.jniLibs`
  is what keeps packaging from failing.
- `extractNativeLibs=true` is NOT in the manifest — AGP emits it from `packaging.jniLibs
  .useLegacyPackaging = true`. The engine cannot unpack its payload without it, so if native init
  ever starts failing, check the merged manifest before anything else.
- Kotlin: `private companion object` is illegal inside a standalone `object`. Use a private
  top-level val in the same file.
- Test files in one package share a namespace for private top-level declarations: two files both
  declaring `private class FakeResolver` fail compilation with a misleading "private in file"
  error. Name test fakes per-file (e.g. `CountingResolver`).
- `Modifier.padding` has no `horizontal` + `top` overload; name all four edges instead.
- Overriding Java's `LinkedHashMap.removeEldestEntry` from Kotlin needs
  `MutableMap.MutableEntry`, not `MutableMap.Entry`.
- A Kotlin `public` property may not expose an `internal` type. Several state holders hit this;
  mark the property `internal` rather than widening the type.
- XML layout comments must not contain `--` (broke the resource compile once).
- material3 `Slider`'s `thumb`/`track` slots are `ExperimentalMaterial3Api` — annotate or it's a
  compile error, not a warning, in this project.
- `AnnotatedString.fromHtml` needs an explicit `import androidx.compose.ui.text.fromHtml` and an
  explicit `LinkInteractionListener { }` SAM wrapper; the lambda alone fails type inference.
- jitpack has NO working build of the fork's current history (GitHub mirror diverged from its
  Codeberg origin) — the composite build from the local checkout is the only supply.
- compileSdk is 36 because the fork's okhttp 5.4 AAR demands it; targetSdk stays 35.

## Tried / rejected

- `jsoup` dependency — dropped. YouTube extraction goes through the engine's JSON, no markup parsing.
- `biometric` dependency — dropped. No lock feature in the target shape.
- R8/minify on release — off for now. Keep rules for the engine and Room must land first, and a
  broken release build is worse than a large one. (LANDED 2026-08-10: conservative keeps for
  extractor/Rhino/yt-dlp/Room/serialization in proguard-rules.pro, device-verified.)

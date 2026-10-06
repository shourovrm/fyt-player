# Extraction: extractor fork, resolver chain, googlevideo, yt-dlp sources, sign-in

## Current state

2026-10-06 review wave (tests green, device-UNverified): `ExtractionError.AccessChallenge` carries
an `AccessChallengeReason` (AGE_RESTRICTION, LOGIN_REQUIRED, BOT_CHECK, RATE_LIMIT, GEO_BLOCK,
PAID, UNKNOWN). The signed-in TVHTML5 retry in NewPipeResolver runs ONLY for age/login -- it
used to fire on every wall, i.e. a cookie-bearing second request straight through a 429 or bot
check. yt-dlp `HTTP Error 429` / too many requests / rate limit and `HTTP Error 403` now map to
AccessChallenge (hard stop) instead of Unsupported, which fell through to the WebView tier and
re-hit the walled page. Statuses match the "http error NNN" shape only (bare digits hit video
ids). Resolver logs go through `DiagLog` (see playback.md).
Upstream reference clone: `~/repos/pipepipe` (PipePipe v5.4.0, 2026-09-26, client + extractor
c68e10e) -- read-only, NOT the build checkout (`~/repos/PipePipe/PipePipeExtractor`).

**EXTRACTOR PATCHES (must survive every extractor bump):** `~/repos/PipePipe/PipePipeExtractor`
remotes: `upstream` = codeberg.org/NullPointerException/PipePipeExtractor (fetch only),
`origin` = github.com/shourovrm/PipePipeExtractor (ours, default branch `fyt-patches`; the
submodule clone was SHALLOW -- `git fetch --unshallow upstream` was needed before GitHub
accepted a push, and a 75 MB pack needs `-c http.postBuffer=1048576000`). Branch `fyt-patches`
= upstream tag + two commits: `f23f1133` search collects shorts
shelves (reelShelf/gridShelf/shortsLockupViewModel) + composite-build gradle tweaks, `409d738`
related items include a shorts shelf. Bump recipe: `git fetch upstream --tags && git rebase <newtag> fyt-patches && git push
origin fyt-patches --force-with-lease && git push origin --tags`, re-verify JSON keys against a
live watch page, clean build, playback smoke.
Live finding 2026-08-30: anonymous innertube `next` (WEB/MWEB/continuation) carries NO shorts
shelf -- only signed-in/personalised sessions get one -- so the related patch is dormant until
sign-in works; keep it anyway.

2026-08-22 wave (v0.2.5): share/open-with plays Facebook (landscape) and TikTok (shorts pager)
via yt-dlp tier1. Cookie header from yt-dlp's per-format `cookies` (TikTok CDN needs
tt_chain_token/ttwid); codec-unknown progressive mp4 now selectable as muxed (Facebook sd/hd);
tiktok hosts in the VIEW filter + `openSharedUrl` routes vertical-clip hosts to the shorts pager.
Landscape white/grey left strip FIXED (AppScaffold consumes the display-cutout inset). yt-dlp
in-app update works (bumped to 2026.08.19 on device).

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

2026-09-24 (v0.2.21) DEVICE-VERIFIED (Nothing A059): Settings "Sign in again" + a Sign in / Sign in
again button on AccessChallenge in player and shorts (`settings/YoutubeSignIn.kt`; fresh=true
clears WebView cookies first, stored session kept until a new one lands). Extractor SHIPPED =
v5.2.5 + our 2 patches (local checkout on branch `backup-fyt-5.2.5`, GitHub `fyt-patches-5.2.5`).
v5.3.1 rebase lives on `fyt-patches` (local + GitHub) but is NOT shipped: see Open items.

2026-08-10 (v0.2.3) device-verified: ChunkedRangeDataSource probes totals for clen-less
QUERY-STYLE /videoplayback only (HLS segments are path-encoded, range= query on them = HTTP 400).

2026-08-09 wave (tests green, device-UNverified): Share-with/open-with: manifest SEND(text/plain)
+ VIEW filters (youtube/youtu.be/facebook/fb.watch/twitter/x hosts), `singleTask` +
`onNewIntent`; first https URL in the text → `nav.openDetail` via a `PendingSharedUrl`
compose-state seam (cold start waits for NavHost). YouTube rides tier0, FB/Twitter fall through to
yt-dlp tier1 — no per-host code.

2026-08-08 field-report wave (tests green, device-UNverified): IOException no longer blanket-maps
to Network ("No connection" spam fix — only real transport exceptions in an 8-deep cause chain
qualify).

2026-08-07 wave: content **language + country** settings (`Prefs.contentLanguage/contentCountry`,
`settings/ContentSettings.kt`) latch into `NewPipeInit` and reach the extractor as
`Localization`/`ContentCountry`; changes apply live (`setupLocalization`), no restart. Optional
first-party **YouTube sign-in** (`YoutubeLoginActivity` WebView on Google's real sign-in page →
`YoutubeAuth` app-private prefs → `NewPipeDownloader` attaches Cookie/Authorization SAPISIDHASH/
X-Origin to youtube.com hosts only), surfaced as `settings/AccountSettings.kt`. Channel **Courses**
tab delegates to yt-dlp (NewPipe has no such tab).

2026-08-06 wave: NewPipeExtractor v0.26.4 is BOTH tier-0 of the resolver chain AND the YouTube
VideoSource (`source/newpipe/NewPipeYoutubeSource`, id stays "youtube"): search, channel tabs,
playlists, detail, comments, seek thumbnails, shorts (providesShorts=true now). yt-dlp keeps
downloads, channel Courses tab and resolver fallback (searchChannel moved to the fork 2026-08-22).
Paging via PageToken (JSON-serialized NewPipe Page).

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
- 2026-08-08 wave (device-verified except where noted): resolver LRU cache (60 entries/60 min,
  `ChainResolver`, `StreamResolver.invalidate` seam), expired-URL fast-fail policy + resume
  staleness refresh (REMOVED 2026-09-27, see Tried / rejected),
  `SharedSurface` ownership registry (black-screen race fix — race never reproduced on demand,
  fix is registry-by-construction), googlevideo chunked ranged reads
  (`player/ChunkedRangeDataSource.kt`, 10 MB windows, mirrors StreamDownloader) for full-speed
  transfer instead of realtime pacing.

## Open items

- Extractor v5.3.1 (`fyt-patches`): direct/progressive googlevideo URLs 403 (downloads fail,
  quality-sheet HEAD sizes 403); HLS playback + shorts fine. Cause UNKNOWN. PO token is NOT it
  (checked 2026-10-06 against upstream c68e10e): `getYoutubePoTokenResolver` has one call site,
  YoutubeStreamExtractor.java:2467, inside the mweb/SABR path; the visionos path never reads
  it, in 5.2.5 or 5.4.0. Diagnose from a run on that branch (player-client + url-param-name
  log lines). Also needs `Comment.text` from `Description` (getCommentText type changed).
- Upgrade target is now v5.4.0: both patches pass `git apply --check` on it (subagent-run, not
  re-verified), Downloader + YoutubeJavaScriptDecoder interfaces unchanged, android_vr client
  removed upstream (2a9a92fb), SABR rewritten (c82d0030). mweb/SABR needs app-side PO-token
  generation = crosses the no-bypass rule; do not adopt.
- Chunked-source probe for clen-less PROGRESSIVE URLs is live but not yet exercised on device
  (playback rides HLS manifests now) — verify when a progressive-only video shows up.

## Gotchas

- TikTok CDN 403s without session cookies; yt-dlp's per-format `cookies` is Set-Cookie shaped
  (name=val; Domain=..; Path=..) -- strip attributes to a `Cookie:` header (EngineResolver.cookieHeaderFrom).
- TikTok IP-rate-limits repeated extraction: same request that gave 206 flips to 403 after ~10 hits,
  and yt-dlp then wants curl_cffi impersonation (can't ship on Android; = bypass). Honest error only.
- Facebook `Cannot parse data` hits ~2/3 of public URLs intermittently (yt-dlp extractor, upstream);
  some fail on one network and play on another. Not fixable under the no-bypass rule.
- Facebook sd/hd progressive mp4 arrive with NO vcodec/acodec keys -- treated as muxed(unknown),
  else FormatSelector rejects them. `none` still means absent (video-only stays video-only).
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
- A bare channel URL does not list videos — the engine returns the channel's TABS as playlist
  entries. Channels must be asked for `/videos` explicitly (`channelTabUrl`).
- Search paging refetches cumulatively: page N asks the engine for N×pageSize results and drops the
  earlier ones. The search protocol takes a count, not an offset. Real listings page properly with
  `--playlist-start`/`--playlist-end`.
- `Listing.key` is assumed to be a full channel/playlist URL, because that is what `detail()` puts
  there. Anything else constructing a `Listing` must honour that or `listing()` breaks.
- Engine self-update API, confirmed against the artifact's bytecode: `YoutubeDL.version(context)`,
  `YoutubeDL.updateYoutubeDL(context, channel)`, and `YoutubeDL.UpdateChannel._STABLE` /
  `._NIGHTLY` / `._MASTER` — the leading underscore is the real API, not a typo.
- ChainResolver hard stops (AccessChallenge/ContentUnavailable rethrows) were LOG-SILENT — cost a
  debugging session. They log tier + exception class now; keep it that way.
- Upstream NewPipeExtractor has no logged-in concept at all, so `YoutubeAuth`'s headers ride on
  whatever InnerTube client the extractor picked. PipePipe's fork additionally swaps the YouTube
  player client when signed in (`NewPipe.setYoutubePlayerClient`, fork-only API). If sign-in turns
  out not to lift age gates on device, that missing client swap is the first suspect.
- `NewPipe.init` must read the latched language/country from `NewPipeInit`, never take them as
  ensure() arguments: init is lazy (first extractor call), so a call site passing defaults would
  silently overwrite the user's setting long after prefs loaded.
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

## Tried / rejected

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

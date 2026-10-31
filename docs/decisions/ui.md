# UI: home, search, detail tabs, library, playlists, settings, backup

## Current state
2026-10-31 (built-unverified): speed sheet = mock speed.html option F (`SpeedSheet.kt`, pure maths in `SpeedMath.kt`): big value + -/+ (0.05), slider 0.1-3 that snaps to 0.25 marks when within 0.05, six presets (0.5 1 1.25 1.5 2 3), Reset to 1x; applies live, sheet stays open; bar shows a filled primary pill when speed != 1x. Speed still not persisted.
2026-10-31 (built-unverified): Library History multi-select (mock .design-mockups/history-select.html, d1): long-press selects, rows get checkboxes, `SelectionTopBar(allState=)` shows a tri-state checkbox (other tabs keep the "All" text button), trash Remove -> `HistoryRepository.removeAll` returns the full rows, snackbar "N videos removed" + Undo -> `restore` re-upserts them (same watchedAt). Long-press no longer opens the action sheet on History. Deselecting every row exits selection (empty set == off), unlike the mock's "stays in selection mode".
2026-10-31 (built-unverified): Detail Download button is a plain neutral IconButton like Like/Save/Share; it turns
primary only when the video has a COMPLETED download row (`VideoActions.downloadedFlow` <- `DownloadQueue.isDownloaded`,
derived from the in-memory `rows`, no I/O). No in-progress state existed, none added. Comment bodies (`CommentBody`
in CommentsSection.kt) draw links inert (colour only) and one `detectTapGestures` handler decides: tap inside a link
glyph's box while expanded opens it, anything else toggles collapse. Cause: Compose's per-link tap area is the link's
bounding box, so a wrapped link also swallowed plain text beside it. Gotcha: `getOffsetForPosition` is nearest-char, so
also check `getBoundingBox(offset).contains(position)` or blank space after a line end hits the last char.
2026-10-31: wall errors read per cause (`ErrorMessages.kt`, shared by player and lists); player shows Sign in for bot check/age/login, Retry for rate limit/unknown, neither for region/paid.
2026-10-31: player centre skip buttons (Prev/Next) shrunk to a 48dp circle with a 32dp glyph (were 64dp /
40dp); the 64dp play button is unchanged. The autoplay toggle is now a YouTube-style switch (`AutoplaySwitch`: track + play-glyph knob, primary track while on,
outlined while off, "Autoplay on/off") in the bottom bar left of Repeat; it left the top-right corner.
Behaviour of Next is in playback.md. MiniPlayer's Next is a 48dp target with a 24dp icon, not oversized, so
it is unchanged. NOT device-verified.
2026-10-28 (c): About page rebuilt as mockup variant C (`settings/About*.kt`; `AppSettings.kt` and
`EngineSettings.kt` deleted, only About used them): hero (logo tile, name, version, update-status chip), a
strip of round buttons Update / Site support / Reports (Reports only when a crash or playback log exists),
then a "Site support" group of icon rows (Version, Update channel, Last checked). Update shows its result
as the chip (Checking / Up to date / Failed, retry on tap / Version X is available + Install update); it
does not auto-check. Report Clear now confirms. `SettingsValueRow` gained an optional `leadingIcon`.
Device-verified: page layout and the Up to date chip. Not tapped: Site support update (it changes the
on-device engine, which is yt-dlp 2026.08.19 while desktop is 2026.10.26). The action-message pill now
floats over the page (root Box, anchored under the action row, no layout space) so the tabs never move;
verified on device. Known nit: the Update channel row shows the dropdown arrow twice (leading and trailing).

2026-10-28 (b): Like and the Skips shield show their message as a pill directly under the action icon row
(inverseSurface, fades after 2 s, a repeat restarts the timer; `ActionMessage` in DetailScreen) instead of a
system toast, which Android draws at the bottom near the tab bar. The download-quality picker still toasts.
Device-verified: "Liked" pill under the icons. About tab redesign: four mockups in
/tmp/fyt-mockups/about-variants.html, none chosen yet. The About page today has no icons (rows: Version +
Check for updates, Site support group, Reports); material-icons-core only, no extended set.

2026-10-28 (built, 546 unit tests green; device pass in LOG): Detail action row is icon-only Like / Save /
Download (tonal) / Share / Skips shield, evenly spaced, no sideways scroll; Like and Skips toggles show a
toast; the channel line is the name only (the "Skips on/off" text button is gone). Channel page: header
row under the app bar (avatar when the listing has one, name, Subscribe / tonal Subscribed with check;
no subscriber count: `Listing` has no field). Channel Shorts tab = 3-column portrait grid reusing
`ShortsGridTile` (long-press selects, border shows it). Description shows chapters once: the structured
list stays and the chapter timestamp lines plus their "Chapters:" header are stripped from the parsed
description (`stripChapterLines`, works on the AnnotatedString because YouTube descriptions are HTML).
Library History hides vertical clips: shorts are now recorded as seen (shorts.md) but are not videos.


2026-10-18 (v0.2.27, device-UNverified): Settings is hub-and-spoke: `SettingsScreen` = 8 rows with
live summaries, `SettingsPageScreen(page)` on route `settings/{page}` hosts the unchanged section
composables. Quality = value rows + radio dialog; SponsorBlock = 8 rows + Off/Skip/Show button
menu (dimmed when master switch off, no longer hidden); Home topics = switch rows; update channel
= menu (Stable/Nightly/Master). Every explanatory hint and empty-state second sentence deleted
app-wide (docs/copy.md hint policy: "explaining is slop"). Verify on device: hub summaries,
each subpage, back + tab highlight, radio dialog, Skipping menus.

2026-10-18 (v0.2.26, device-UNverified, no device attached): whole UI rebuilt on
`docs/design-system.md` (Material 3). Tokens in `ui/theme/Spacing.kt` (Spacing/Sizes/Radius),
Material type roles in Type.kt, over-video colours in `fyiExtras`. Every visible string is in
`res/values/strings_<area>.xml` under the `docs/copy.md` vocabulary (that file lists every
rewrite). Gates: `DesignTokensGateTest` + `CopyGateTest` (6 tests) green; 495 unit tests green.
Drift before -> after: raw dp 446/54 distinct -> 124 inline all in {0,1,2,4,6,10}; raw sp 5 -> 0;
raw colours 3 + 66 White/Black -> 0; Kotlin string literals ~190 -> 5 (symbols/dynamic values).
Behaviour changes to verify on device: player quality/speed/captions/loop now behind one More
menu in the 48 dp control row (auto-hide pauses while open); mini player 72 dp; Shorts rail
buttons 48 dp; Detail action row = Like/Add to playlist/Download(filled)/Share, scrolls
sideways; DownloadRow = ResultRow shape, Failed is a badge; Sign out and Clear history confirm;
"App" section renamed "About"; search bar 56 dp with refresh inside. Check DownloadRow at 360 dp
/ font 1.3 (text column ~100 dp), Shorts shelf cards now 120 wide (was 108).

2026-10-07 DEVICE-VERIFIED (v0.2.24): Settings has SponsorBlock (8 categories x 3 modes),
Video page (dislike opt-in), Downloads without "File format", "Clear watch and search
history", Playback log row. Search: type chips work (Channels returns channels), sort chips
scroll off to the right; the keyboard hides on the IME search key (a hardware Enter does not
hide it -- irrelevant on a phone). Not verified: confirm dialogs, empty states, selection
highlight, shared-link replay, Home pin, Detail no-refetch, backup import.

2026-10-06 review wave 2 (425 tests green, device-UNverified): dead "File format" setting and
`Prefs.preferredContainer` removed (nothing read it). Confirm dialogs on delete playlist (both
screens; PlaylistDetail pops after delete via `onDeleted`), Library "Clear history", Settings
"Clear watch and search history" (label now says both). Empty states: "No videos" on listing /
channel tabs, `No results for "..."` in channel search; container-tab first-page error has
Retry. Selection highlight + `semantics.selected` on the shared `ResultRow` (channel, listing,
channel search, likes, playlist). Shared-link intent read only when `savedInstanceState ==
null`. Home feed pin-to-top only while the feed itself shows (`shouldPinFeedToTop`). Search
submit clears focus. Detail's `detail` / history-recorded flag live in `DetailTabsViewModel`
(no refetch on back). Channel keeps one list state per tab. History long-press opens
`VideoActionSheet`. Backup import: liked order preserved (`likedAtForImport`), same-name
playlists merge (`planImportWrites`), URLs sanitized (`sanitizeBackupDocument`: https + host
for videos, unowned video keeps empty sourceId like a shared FB/TikTok link, unowned channel
dropped), dropped count shown in the dialog.

2026-10-06 review wave (tests green, device-UNverified): endless scroll no longer re-requests a
failed page (`shouldAutoLoadMore`, `loadMoreFailed` on `ResultsListColumn`; channel tabs,
in-channel search and playlists kept `nextPage` on error, so the effect refired back to back).
Retry continues from the failed page and keeps loaded rows (`retryTab`, `retrySearch`); the
error row renders at the list tail, container tabs included.

2026-09-03 DEVICE-VERIFIED: Home feed fetches EVERY feed-visible subscription (was newest-
subscribed 8 via `capChannels` -- Shorts still caps), `Semaphore(FEED_CONCURRENCY=6)` bounds the
burst, watched videos stay in (progress bar says watched), viewport pinned to row 0 while the
refresh is loading (`requestScrollToItem(0)` on each items change -- LazyColumn keeps position
by key, so progressive newer-on-top inserts used to ride the viewport down the list).

2026-08-30 (v0.2.20, wave 2) DEVICE-VERIFIED: watched-progress bar on every `ResultRow`
(`LocalPlaybackPositions` provided once in AppShell, empty when "Remember playback position" is
off); Playlists tab: followed + local share one `PlaylistRowItem`, followed ⋮ = Share/Delete
(no Rename), cover = first video (persisted at follow time, `followed_playlists.thumbnailUrl`,
DB v8; one-shot LibraryViewModel backfill also fills a blank title from `SearchPage.title`);
"Download subtitles" = long-press sheet item (rows + Detail title long-press), always `.srt`
via `fmt=vtt` + `vttToSrt`, English preferred, no checkbox in the quality sheet; autoplay next =
first non-short of `detail.related` (title search fallback); Detail follows automatic
advances (`PlayerState.autoAdvances` counter, survives play()'s state reset -- `current` is
null right after play(), so Detail reads `queue[index]`).

2026-08-25 (v0.2.19) Home "Local" chip (first after For you): three YouTube searches -- song /
movie / drama phrased in the content country's language (LocalQueries, 20 countries + English
fallback), year appended from the device clock at fetch time, first pages interleaved, shorts
shelf on top like search. Music chip = US: Music topic-channel trending feed; other countries: charts VIDEOS DAILY
country_code=global (the Local chip is the country-flavoured one). Detail ✕ = clear + pop
to Home (whole chain); ⌄ = pop one.

2026-08-24 (v0.2.12), device-verified fresh-install:
- `ContentSettings` (Language & region) now uses the same `LocaleDropdown` as the onboarding
  sheet instead of a `FilterChip` row -- one picker style everywhere, not chips here and
  dropdowns there.
- Search tab row ("All"/"YouTube") was showing with a single enabled source: it gated on
  `tabIds.size > 1`, but `tabIds` always prepends a synthetic "All" entry on top of
  `browseSources`, so the count was never 1 even with one source. Gated on `browseSources.size > 1`
  instead. Search now shows the result list directly with one source enabled, no tab row.

2026-08-24 (v0.2.11) Explore topics + onboarding + backup rework + settings cleanup, all
device-verified fresh-install:
- **Explore chips** on Home (`source/newpipe/YoutubeTopicFeed.kt`): News/Sports/Music via YouTube's
  official topic-channel `browse` (richGridRenderer richShelfRenderer, videoRenderer or the newer
  lockupViewModel -- Music ships lockups only, News/Sports/Live still videoRenderer). Movies has no
  free channel feed (its channel is a paid storefront) -- uses `charts.youtube.com` `browse`
  (`FEmusic_analytics_charts_home`, chart_type=TRENDING_MOVIES), same request NewPipe 0.29's
  "Trending movies and shows" kiosk sends. Charts carries chart position + release date, NEVER a
  view count -- `viewCountText` stays null there (no per-item fetch to fake one, rule: no I/O per
  list item). Which chips show is a Settings toggle (`Prefs.exploreTopics`, News/Movies/Sports/Music
  on by default, Live off -- it's really PipePipe's "Recommended Lives" kiosk under a friendlier
  name). Country/language change clears the cached topic pages and refetches (`HomeViewModel.init`
  drops+refetches the selected topic on `contentLanguage`/`contentCountry` change).
- **Onboarding**: first-run `ModalBottomSheet` over Home (`settings/Onboarding.kt`), two
  `ExposedDropdownMenuBox` pickers reusing `ContentSettings`' LANGUAGES/COUNTRIES. Gotcha found
  device-side: the system POST_NOTIFICATIONS permission prompt (MainActivity, pre-existing) can
  appear over this sheet on first launch and its mere appearance fires `onDismissRequest` --
  wiring that to `setOnboardingDone()` silently skipped onboarding. Fixed: `onDismissRequest = {}`,
  only the Done button persists `Prefs.onboardingDone`.
- **Backup**: HTML export/import now carries playlists + liked + subscribed channels; watch
  history dropped entirely (`BackupModel` version bumped to 2, old files' `history` array ignored
  via `ignoreUnknownKeys`, `channels` defaults empty for old files).
- **Settings**: new "App" section (version, before Video engine); Autoplay-next helper line and
  Likes-tab empty-state helper text removed (both read as filler); quality chips get an explicit
  "Default quality" label; "Update yt-dlp" moved onto the "yt-dlp update channel" row.
- **Fix (v0.2.10)**: R8 stripped `org.apache.commons.compress.archivers.zip.*` (registered by
  reflection in `ExtraFieldUtils`), crashing yt-dlp's first-run python unzip -- but only on a
  FRESH install; `adb install -r` over existing app data skips that unzip and hid the crash.
  Fixed with a keep rule (`proguard-rules.pro`). Release verification from now on: uninstall then
  install, never `-r` over a previous version, or a first-run-only crash like this won't surface.

Also in: home (tabs, search, paging), results list + long-press actions, detail (pinned player
header, metadata, channel tap-through, Similar/Comments tabs below), listing, settings sections,
and the full player — chrome, gestures, quality/speed sheets, mini player, queue bar,
seek-thumbnail mapping.

Detail's section below the video is three tabs (`DetailTabsViewModel`): **Similar** (YouTube
watch-next from `VideoDetail.related`, title search as fallback), **Description** and **Comments**
(unchanged threading/replies, fed by the state holder instead of owning its own fetch). Each tab
fetches at
most once per video, gated the same idempotent way `ListingViewModel.ensureLoaded` is.

And: library (likes / playlists / history, multi-select, resume bars), playlist detail with
reorder, the download queue + foreground service + downloads screen, the shorts pager, and library
backup (HTML + embedded JSON, export and import over SAF).

Home feed rebuilt: YouTube's public trending/explore feeds are dead (confirmed live — both
redirect to youtube.com home and the engine reports the playlist gone), so `homepage()` now
throws `Unsupported` honestly instead of hitting a dead URL. Home's default (no search) view is
composed in `HomeViewModel`/`HomeFeed.kt` instead: newest uploads from up to 4 recently-watched
channels (`WatchHistoryEntity.uploaderUrl`, new column, `Migration(1, 2)`), round-robin
interleaved, already-watched excluded, appended incrementally per channel. Cached in the
ViewModel for its lifetime; a refresh icon next to the search pill is the explicit reload (no
pull-to-refresh — smaller diff, same effect). Search is untouched and still per-source-tabbed.

2026-08-30 (v0.2.20) Keep-note wave, DEVICE-VERIFIED (Nothing Phone): Similar tab = YouTube
watch-next recommendations (`detail.related`, DetailScreen gates the fetch on `detailLoaded`),
title search only as fallback. Library: `youtubeThumbnailFor(pageUrl)` hqdefault fallback
everywhere a row renders, `withTitleIfBlank()` enriches bare refs on like/playlist-add/
download-start, "Untitled" instead of an empty line. Description: bare URLs / scheme-less youtube
links / @handles / timestamps linkified on both HTML and plain branches. Playlists: `list=`
share-in opens the remote listing (mix RD* rejected), Share on listing / followed / local
playlists (local = title + one URL per line), one `PlaylistRowItem` for followed and local.

2026-08-10 (v0.2.3) device-verified: canonical thumbnail URLs at all three persist seams
(`data/repo/ThumbnailUrl.kt`); backup HTML parse no longer double-unescapes; typed channel rows
(ResultKind/subscriberCount, "21.1M subscribers" renders). Similar-chain BACK itself verified
correct, 3 hops each way.

2026-08-09 wave (tests green, device-UNverified): Home merged feed sorts `uploadEpochMs`
descending (new `VideoRef` field from `uploadDate.offsetDateTime()`; nulls last, stable;
`sortByRecency` applied ONLY at Home's merge — channel tabs/playlists keep service order, Shorts
interleave untouched). Playlist rows now persist `uploaderUrl` (schema v6, additive column — the
WatchHistory v1→2 bug all over again) and Detail's fallback header links the channel straight off
the ref instead of plain text.

2026-08-08 field-report wave (tests green, device-UNverified): search: BackHandler exits search
mode, suggestion dropdown (fork `YoutubeSuggestionExtractor` via
`source/newpipe/SearchSuggestions.kt`, 300ms debounce), Clear-all in history dropdown,
PullToRefreshBox on home feed; search returns playlists (stopgap VideoRef, canonical
`playlist?list=` URL, RD* mixes dropped, HomeScreen URL-heuristic routes to listing) and
LIVE/UPCOMING badges (`VideoRef.isLive/isUpcoming`; upcoming = future upload date, the fork
reports premiere start time as upload date); EngineSettings copy now says extractor updates
require a new APK (yt-dlp rows relabelled).

2026-08-07 wave: Video **description is now its own tab** (Similar / Description / Comments)
rendering HTML via `AnnotatedString.fromHtml` with in-app link routing (same-video timestamps
seek, other videos open Detail, channels/playlists open Listing, rest to the browser).

2026-08-06 wave (device-verified): List cells now carry real views/age/uploader. Library gained a
Channels tab (subscriptions, multi-select unsubscribe) and followed remote playlists (schema v4,
`followed_playlists`, merged into the Playlists tab). Search channel rows navigate to the channel
screen. Detail page has a Like/Save/Download/Share/Queue action row; video/shorts cells carry a
`Channel · views · age` meta line (`shortAge()` display transform, pass-through on unknown text).

## Open items

- Not done from the 2026-10-06 review: strings to resources (app is not localisable, ~93
  literals), tablet/landscape layout (Detail header is width*9/16 with no height cap), single
  followed-playlist unfollow has no confirm.
- Old Library rows persisted before v0.2.20 keep "Untitled" (no backfill); shared-in playlist
  listing shows "Listing" as title (URL-only ref); followed playlist row has no thumbnail (no
  stored data, no per-row fetch). All cosmetic.
- "Back through Similar chain goes home" RESOLVED 2026-08-10: back itself walks the chain
  correctly (device-verified, 3 hops). The real complaint was the deeper video still PLAYING
  over the shallower page after back — fixed (Detail re-entry takes playback). Residual foot-gun:
  bottom-nav Home tab stays tappable on Detail and `navigateToTab`'s popBackStack(home) discards
  the whole chain in one tap — if reports continue, that's the remaining suspect (fix would be
  hiding the nav bar on Detail routes, a UX decision → mockup first).
- Courses tab rows have blank thumbnails — the yt-dlp delegate's flat container listing carries
  none. Cosmetic; would need a per-row fetch, which rule 6 forbids.
- User-side verification pending on the newest wave: queue-after-exhaustion on device, channel
  Videos/Shorts play-selected/play-all, followed playlists end-to-end (follow → Library → open →
  remove), playlist tab thumbnails, unsubscribe flow.

## Gotchas
- Any `.dp` literal outside ui/theme other than 0/1/2/4/6/10, any `.sp`, any raw colour, or any
  quoted literal passed to Text/contentDescription/label fails the gate tests. Add a token or a
  string resource instead; never loosen the regex.
- Core enum labels (SearchType, SponsorMode, Topic) are Kotlin strings the copy gate does not
  scan: keep them to the docs/copy.md vocabulary by hand.

- charts.youtube.com: `gl` must be a real country even when `chart_params_country_code=global`; gl=global returns an empty chart.
- Explore chip set is a persisted pref: a new Topic is only default-on for fresh installs; existing installs enable it in Settings.
- A feed that reads its enabled-source set from DataStore sees an EMPTY set on the first
  composition. Loading then and latching `loaded = true` is why Home and Shorts both silently
  stayed empty. Guard on `sources.isEmpty()` and re-load when the source set actually changes.
- `Toast` from a coroutine on the process scope (`Dispatchers.Default`) crashes: no Looper. Always
  go through `showToast`, which posts to the main looper.
- Per-channel errors must never be collapsed into "this channel has nothing" — a fetch failure and
  an empty channel are different facts (`ChannelFetchOutcome.Ok/NoContent/Failed`).
- `material-icons-core` 1.7.8 has no Download / Pause / SkipNext / SkipPrevious / Fullscreen /
  Shuffle glyph, and `Icons.Filled.List` is deprecated in favour of `Icons.AutoMirrored.Filled.List`.
- Persisted rows carry no `remoteId`; entity → `VideoRef` mappers set `remoteId = pageUrl`. A saved
  video is always re-resolved from its page URL, so nothing downstream may treat `remoteId` as a
  platform id when the ref came out of the database.
- The exported backup page says in prose that no cookies are stored, so a naive whole-document
  scan for "cookie" matches that sentence. The security test scans the JSON payload block instead.
- `HomeViewModel.homeResults`/`loadHome` (per-source browse tabs) are gone: `homepage()` always
  throws now, so that machinery was dead weight. `retryTab`/`continueTab` lost their `isSearching`
  param — they only ever act on `searchResults` today. Re-add per-source browse tabs only if a
  future platform actually implements `homepage()`.
- `formatUploadDate` must handle BOTH date shapes: yt-dlp writes "20260805", NewPipe writes
  ISO-8601 ("2026-08-05T04:00:27-07:00"). The old digits-only guard passed the ISO string through
  untouched, so the detail page printed a raw timestamp for every NewPipe-sourced video.

## Tried / rejected

- Similar tab = title search only — SUPERSEDED 2026-08-30: user asked for YouTube's own
  recommendations (PipePipe parity). `VideoDetail.related` is primary, title search is the
  fallback when related is empty.
- YouTube `/feed/trending` and `/feed/explore` as Home's source — dead. Both redirect to
  youtube.com home and error "the channel/playlist does not exist" (verified live).
- Mix/radio playlists (`watch?v=X&list=RDX`) as a Home feed source — rejected by the extractor
  ("Unable to recognize playlist"), confirmed live. Not an option for any feed.
- Pull-to-refresh (Material3 `PullToRefreshBox`) for the Home feed — skipped for a plain refresh
  icon button next to the search pill; SUPERSEDED: Home and (2026-09-24) Shorts grid both use it.
- Material-You / dynamic colour — rejected. One deliberate accent; wallpaper never overrides it.
- Suggestion fetch failures collapse to emptyList — a dead suggestion endpoint must never render
  as a search error. `SearchSuggestions.fetch` swallows everything by design.
- `Icons.Filled.History` and `LocalFocusManager` under `androidx.compose.ui.focus` don't exist —
  History glyph missing from material-icons-core (Search icon reused), LocalFocusManager lives in
  `androidx.compose.ui.platform`.

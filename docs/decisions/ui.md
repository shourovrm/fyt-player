# UI: home, search, detail tabs, library, playlists, settings, backup

## Current state

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

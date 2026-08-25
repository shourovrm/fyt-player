# FYT Player

Android video player for YouTube, Facebook, Twitter/X and TikTok. Browse and search YouTube, open shared
links from the other two, play in the background, keep a queue, download for offline. No ads, no
account needed. Latest APK: [releases/latest](https://github.com/shourovrm/fyt-player/releases/latest)

## Features

**Browse and search**
- Home feed from your subscriptions (subscribe to at least one channel or "For you" stays
  empty), search with suggestions, channel pages with Videos / Shorts /
  Playlists / Live tabs, playlists, "Similar" and comments on every video.
- Shorts tab: vertical swipe pager over your subscribed channels' shorts.
- Content region and language are settings, not whatever the server guesses.

**Playback**
- Background playback.
- Queue: play next, add to queue, reorder, shuffle, autoplay next.
- Fullscreen with rotation, double-tap seek, swipe for brightness and volume, playback speed,
  quality picker, captions, seek-bar thumbnails.
- Resumes where you left off. Optional SponsorBlock segment skipping.
- Mini player stays on screen of the app while you keep browsing.
- Separate default quality caps for Wi-Fi and mobile data.

**Library**
- Watch history, likes, your own playlists, subscriptions.
- Export playlists, likes and subscriptions to a single HTML file and import them back on another device.

**Downloads**
- Pick video or audio-only, pick a quality, queue several, pause / resume / cancel.
- Choose the download folder and file format.

**Sharing in**
- Share a link from YouTube, Facebook, TikTok or X to FYT Player, or tap a link and choose it
  from the "Open with" list. Facebook videos open in the landscape player, TikTok clips in the
  vertical pager.

## Platform support

| Platform | What works |
|---|---|
| YouTube | Everything above. Optional sign-in for age-restricted and members-only content (currently not working.) |
| Facebook | Play and download a video or reel from a shared link. No search, no page browsing — Facebook offers no public listing API, and the upstream extractor is intermittent on some videos. |
| TikTok | Play a clip from a shared link in the vertical pager. No search, no feed. TikTok rate-limits by IP; after a batch of clips it answers 403 for a while, and the app shows that as unavailable. |
| X / Twitter | Play a video from a shared link. |

## What it refuses to do

When a site puts up a wall — login, CAPTCHA, paywall, geo-block, age gate, DRM, rate limit — the
app shows that state and stops.

## Install

Requires Android 8.0 or newer on a 64-bit ARM device (almost every phone since 2017).

1. Download the APK from the [latest release](https://github.com/shourovrm/fyt-player/releases/latest).
2. Open it on the phone and allow installs from that source if asked.
3. Updates install over the previous version; your library, settings and downloads are kept.

There is no Play Store listing and there will not be one.

## Build from source

Needs JDK 17 and an Android SDK with platform 36 and build-tools 36.

```sh
git clone https://github.com/shourovrm/fyt-player.git
cd fyt-player
```

Release builds are signed from a `keystore.properties` in the repo root, which is gitignored.
Create your own keystore and point the file at it:

```properties
storeFile=my-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then:

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Unit tests: `./gradlew testReleaseUnitTest`. They run against saved fixtures; nothing hits the
network.

## How it is put together

Single Activity, Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer.

```
ui/        screens and the one app shell (scaffold, nav, mini player)
player/    PlaybackSession, media service, gestures, fullscreen chrome
source/    one module per platform behind the VideoSource interface
engine/    stream resolver chain
core/      contracts: VideoRef, VideoDetail, MediaFormat, ...
data/      Room database, DataStore settings, backup codec
```

YouTube metadata and streams come from
[PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor) (a NewPipeExtractor
fork). Everything else, plus YouTube downloads and the sign-in path, goes through
[yt-dlp](https://github.com/yt-dlp/yt-dlp) running on-device via
[youtubedl-android](https://github.com/yausername/youtubedl-android). yt-dlp can be updated from
Settings without a new APK.

Two rules run through the whole codebase: extraction code never lives in UI, and media URLs only
ever come out of the resolver chain. Screens see a page URL and a title; they never see a selector
or a signed stream URL.

## Privacy

- No analytics, no telemetry, no crash upload. The last crash is kept locally and visible in
  Settings so you can paste it into a bug report yourself.
- Only canonical page URLs are persisted. Signed or tokenised media and thumbnail URLs stay in
  memory and are never written to the database, an export file or a log.
- Cookies are isolated per site. Signing in to YouTube does not expose that session to any other
  platform.
- Everything the app stores lives in its own app directory and leaves with an uninstall. The
  library export is a plain HTML file you can open and read.

## License

The source in this repository is [MIT](LICENSE). Do what you like with it.

The release APK also contains [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor)
(GPL-3.0) and [yt-dlp](https://github.com/yt-dlp/yt-dlp) (Unlicense), so the built app as a whole
is distributed under the terms of the GPL-3.0.

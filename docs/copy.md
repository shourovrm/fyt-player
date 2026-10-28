# Copy

Every word the user reads follows this file. The gate is `CopyGateTest`: visible text lives in
`res/values/strings*.xml` and never contains an implementation word, an exclamation mark, a dash
or a trailing question mark.

## Vocabulary

| Word | Means | Never |
|---|---|---|
| video | one playable item, any platform | stream, item, ref, entry |
| Short | a vertical short-form video | reel, clip |
| channel | the account that uploads videos | uploader, creator, author |
| playlist | an ordered list of videos, yours or a channel's | list, collection |
| queue | the videos lined up to play next | playlist (for the queue) |
| Library | the tab holding Likes, Playlists and History | saved, collection |
| Likes | videos you liked | favourites, hearts |
| History | watched videos and past searches | log, recents |
| download | a video saved to this phone | export, file, job |
| site support | the part that reads video sites (yt-dlp) | engine, extractor, resolver, yt-dlp |
| update | a newer version of the app or of site support | upgrade, patch, build |
| sign-in | a YouTube account used inside the app | session, cookie, auth, login |
| captions | subtitles on a video | subs, tracks |
| quality | the video resolution | format, stream, codec |
| sponsored segment | a part of a video SponsorBlock can skip | segment, sponsor, category |
| source | a video platform the app can browse | service, provider, site |
| chapter | a named section of a video | marker |
| connection | the network | internet, network error, offline mode |

Verbs, one meaning each: **Delete** destroys something you made (a playlist, a download).
**Remove** takes one item out of a list (queue, playlist, Likes). **Clear** empties a list
(History, completed downloads). **Subscribe** is for channels, **Follow** is for playlists.
**Like** / **Unlike**, never "remove from likes". **Minimize** (US spelling throughout).

## Rules

- Label: a noun phrase of at most three words, sentence case, never a question.
- Button: a verb, or verb plus object, one to three words. Dialog confirm button repeats the verb
  of the title ("Clear history" dialog confirms with "Clear").
- Hint: almost never. A hint exists only when the label alone would mislead, or to name a
  third-party host the setting talks to ("Uses sponsor.ajay.app"). At most five words. Never
  explain how the app works, what a setting is for, or what the user could do next.
- Explaining is slop. A screen that teaches the app is a screen with the wrong labels. Fix the
  label, delete the sentence.
- Status line: what happened, then what you can do. Error: what failed, then one action.
- Empty state: "No <plural noun> yet." and nothing else, unless the user cannot know how to
  fill it; then one clause of at most six words.
- Dialog body: only when something is lost, at most eight words. Toast: at most four words.
- Confirm dialog: title is the action as a verb phrase ("Clear watch history"), body says what
  is lost, buttons Cancel and the verb.
- One calm voice. No jokes, no exclamation marks, no dashes, no "Oops", no "Please".
- Numbers the user cannot act on stay out of the primary UI (versions live under an
  "About" or "Site support" row, never in a list header).
- Implementation words never appear: yt-dlp, engine, resolver, extractor, DASH, manifest,
  session, token, cookie, cache, hash, index, R8, codec.

## Strings changed

Filled in from the rebuild; one row per string. Reason is one phrase.

| Area | Before | After | Reason |
|---|---|---|---|
| Home | Search | Search videos and channels | spec placeholder |
| Home | Enable a source in Settings to start browsing. | No sources yet. Turn one on in Settings. | empty-state pattern |
| Home | Your feed is empty. Search for a channel, open it, and subscribe to build it. | No videos yet. Subscribe to a channel to fill your feed. | empty-state pattern |
| Home | Couldn't load uploads from N of your subscriptions. Check your connection and pull refresh. | Could not load N channels. Check your connection and pull down to refresh. | plain words |
| Home | No new uploads from your subscriptions right now. | No new videos yet. Check back later. | vocabulary |
| Home | (none) | New from channels you watch | feed section label |
| Home | Clear all | Clear history | verb plus object |
| Home | Remove "q" from history | Remove "q" | shorter |
| Home | Something went wrong | Could not load results. Try again. | what failed, then action |
| Results | LIVE / UPCOMING | Live / Upcoming | sentence case |
| Results | No more results | No more videos. | vocabulary |
| Shorts | You're all caught up — no more Shorts from your subscriptions. | No more Shorts from your channels. | no dash |
| Shorts | Your Shorts feed is built from the channels you subscribe to. Subscribe to a channel to see its shorts here. | No Shorts yet. Subscribe to a channel to see its Shorts here. | empty-state pattern |
| Shorts | Couldn't load shorts from your subscriptions. Check your connection and try again. | Could not load Shorts. Check your connection and try again. | plain words |
| Shorts | Couldn't load shorts from N of your subscriptions. The rest post no shorts. | Could not load Shorts from N channels. The others have none. | plain words |
| Shorts | None of your subscribed channels post Shorts. | No Shorts yet. Your channels have not posted any. | empty-state pattern |
| Shorts | Share link | Share | shared string |
| Update | Update available — vX | Update available: X | no dash |
| Update | Get | Install | verb says what happens |
| Update | Downloading update… | Downloading update | no ellipsis |
| Channel | Unsubscribe (button) | Subscribed (tonal button, check icon) | 2026-10-28: state label chosen from the sub-3-row mock; tapping it unsubscribes |
| Channel | Add selected to queue | Add to queue | shared string |
| Channel | Loading… | Loading | no ellipsis |
| Channel | No videos / This channel has no videos here. | No videos yet / This channel has not posted any here. | empty-state pattern |
| Channel | Nothing here yet | No playlists yet. | empty-state pattern |
| Channel | Search in "Title" | Search this channel | label of at most 3 words |
| Channel | Search this channel's videos (empty prompt) | deleted | label already says it |
| Channel | Nothing in this channel matches that search. | Try another search. | hint states the action |
| Channel | Something went wrong | Could not load videos. Try again. | what failed, then action |
| Listing | Listing (fallback title) | Playlist | vocabulary |
| Listing | Add to playlists / Added to playlists | Follow playlist / Playlist followed | Follow is for playlists |
| Listing | This playlist has no videos. | Videos added to this playlist appear here. | empty-state pattern |
| Listing | This source isn't available | This source is not available. | plain words |
| Errors | This page is behind a login, CAPTCHA, or regional wall the app can't cross. | This page needs a sign-in, a CAPTCHA, or a region the app cannot reach. | vocabulary: sign-in |
| Errors | This link expired. Reopen the video to fetch a fresh one. | This link expired. Open the video again to get a new one. | plain words |
| Errors | This isn't supported yet. | Not supported yet. | plain words |
| Player | Stream link expired. | This link expired. Open the video again to get a new one. | no "stream", gives an action |
| Player | DASH playback not wired up yet / Can't play this video right now. | Cannot play this video yet. | implementation word |
| Player | Minimise | Minimize | US spelling |
| Player | Loop this video | Loop video | shorter |
| Player | Quality, speed, captions, loop as bare chrome buttons | More menu: Quality, Speed, Captions, Loop video | 48 dp targets overflow the row |
| Player | (no description) | More options, Full screen, Exit full screen | icon buttons need a description |
| Mini player | Next in queue | Next video | vocabulary |
| Queue | Close queue (button clears the queue) | Clear queue | says what it does |
| Queue | Queue — 2 of 5 | Queue (2 of 5) | no dash |
| Skip button | Skip Intermission / intro animation (enum label) | Skip intro, Skip sponsor, Skip credits… | short button labels |
| Detail | Save (action button) | Add to playlist | names the object |
| Detail | Like (always) | Unlike when liked | vocabulary |
| Detail | Creator / Liked by creator | Channel / Liked by the channel | vocabulary |
| Detail | No comments. | No comments yet. | empty-state pattern |
| Detail | Skip segments on this channel again | Skip sponsored segments on this channel again | vocabulary |
| Detail | Download subtitles / Subtitles saved / No subtitles for this video / Subtitle format not supported | captions | vocabulary |
| Detail | Share link | Share video | vocabulary |
| Detail | Checking available sizes… | Checking quality options | vocabulary |
| Detail | Can't download this video | Cannot download this video | plain words |
| Detail | OK (error dialog) | Close | button verb |
| Detail | No source for this video | This site is not supported yet. | calm voice |
| Detail | Search failed | Similar videos failed to load. Try again. | what failed, then action |
| Detail | No comments for this source | This site has no comments. | calm voice |
| Detail | comment loading failed | Comments failed to load. Try again. | what failed, then action |
| Playlist | OK (create / rename) | Create / Save | repeats the title verb |
| Playlist | Couldn't create -- that name may already be used | Playlist not created. That name may already be used. | no dash |
| Playlist | No playlist id X. | It may have been deleted. | implementation detail |
| Playlist | No videos in this playlist yet / Add one from a video's long-press menu. | No videos yet / Add one from a video's menu. | empty-state pattern |
| Library | Remove from likes | Unlike | vocabulary |
| Library | Exit selection | Done | one word for leaving a mode |
| Library | Unsubscribe from N channels? | Unsubscribe from N channels, with a body | no question mark |
| Library | Remove N followed playlists? | Unfollow N playlists, with a body | vocabulary |
| Library | Delete playlist "x"? | Delete playlist, body says the videos stay | verb-phrase title |
| Library | Clear watch history? | Clear watch history, with a body | no question mark |
| Library | Delete (followed playlist menu) | Unfollow playlist | vocabulary |
| Library | History recording is off in Settings. These are past entries -- new views won't be added. | History is off. New videos are not added. | no dash |
| Library | No watch history / Videos you open land here. | No history yet / Videos you open appear here. | empty-state pattern |
| Library | Subscribe from a channel page and it lands here. | Subscribe from a channel page to see it here. | plain words |
| Downloads | Remove "title"? | Remove download | no question mark |
| Downloads | Choose whether the downloaded file (size) should also be deleted from storage. | Remove keeps the file on this phone. Delete erases it. Delete frees SIZE. | states what is lost |
| Downloads | Delete file too / Delete N file(s) too | Delete file / Delete files | button verb |
| Downloads | Clear N completed download(s)? | Clear completed downloads | no question mark |
| Downloads | Removed from list; couldn't delete the file | Removed from the list. The file could not be deleted. | one sentence each |
| Downloads | ETA 2:10 | 2:10 left | plain words |
| Downloads | Failed · reason (coloured text) | Failed badge, then the reason | status colour on a badge only |
| Downloads | Open | Play | vocabulary |
| Downloads | File is no longer on disk / No app on this device can open it / Can't open this file from here | This file is no longer on this phone. / No app can open this video. / This file cannot be opened from here. | plain words |
| Settings | Use SponsorBlock — sends a 4-character hash prefix of each YouTube video id to sponsor.ajay.app | Skip sponsored segments / Asks sponsor.ajay.app about each YouTube video you open. | hint is the privacy disclosure |
| Settings | Show dislike counts — asks returnyoutubedislike.com about each video you open | Show dislike counts / Asks returnyoutubedislike.com about each video you open. | label and hint split |
| Settings | Video engine (section) | Site support | implementation word |
| Settings | PipePipeExtractor v5.2.5 · YouTube | deleted | implementation word and version |
| Settings | yt-dlp version: bundled with the app | Version / Bundled with the app | implementation word |
| Settings | yt-dlp update channel / Update yt-dlp | Update channel / Update site support | implementation word |
| Settings | Updated. / Already up to date. | Site support updated. / Site support is up to date. | names the subject |
| Settings | Last crash / Crash log | Crash report | vocabulary |
| Settings | Playback log (recent timings and error types, no links) | Playback report / Recent timings and error types, with no links. | label and hint split |
| Settings | FYT Player 0.2.25 (row in "App") | Version row under "About" | version belongs under About |
| Settings | Couldn't check — are you online? | Update check failed. Check your connection and try again. | no dash, no question |
| Settings | You're on the latest version | You have the latest version. | plain words |
| Settings | Update available — v1.2 / Get | Version 1.2 is available / Install update | no dash, verb plus object |
| Settings | Language & region | Language and region | no ampersand |
| Settings | Change anytime in Settings. | You can change it in Settings. | plain words |
| Settings | Folder | Download folder | label says what it sets |
| Settings | Explore chips on Home (Local = latest songs, movies, dramas for your country) | Home topics / Chips shown on Home. Local shows new releases for your country. | label and hint split |
| Settings | Brightness — drag left side | Brightness / Drag up or down on the left side of the player. | label and hint split |
| Settings | Remember playback position | Remember position | two words |
| Settings | Clear watch and search history (button) | Clear history (destructive row) / Removes watched videos and past searches. | destructive row with hint |
| Settings | Clear watch history and search history? | Clear history, body says what is removed | confirm dialog pattern |
| Settings | Signed in to YouTube / Sign in to YouTube | YouTube account / Signed in, or hint "Lets you play age-restricted and members-only videos." / Sign in | label plus hint |
| Settings | You'll sign in to your Google account inside the app. The session is stored only on this device. | Sign in with your Google account inside the app. The sign-in stays on this phone. | "session" is banned |
| Settings | Continue (sign-in dialog) | Sign in | repeats the title verb |
| Settings | Sign out (no confirm) | Sign out, with a confirm dialog "Sign out of YouTube" | destructive action |
| Settings | Export failed. / Import failed. | Export failed. Try again. / Import failed. Check the file and try again. | what failed, then action |
| Settings | Nothing new to add -- everything in this file is already in your library. | Nothing new to add. Everything in this file is already in your library. | no dash |
| Settings | Adds N playlists, N playlist videos, N liked videos and N channels. | This file adds playlists N, playlist videos N, likes N and channels N. | avoids "1 playlists" |
| Settings | Background playback | Background playback / Keeps audio playing when you leave the app. | states the effect |
| Settings | one scroll of 12 sections | hub of 8 rows (Account, Content, Playback, Skipping, Downloads, History, Backup, About), each its own page | too much on one page |
| Settings | 10 quality chips | "On Wi‑Fi 1080p" / "On mobile data 720p" value rows with a radio dialog | chips are not settings |
| Settings | 24 SponsorBlock chips | 8 rows with a trailing Off / Skip / Show button menu | chips are not settings |
| Settings | every hint (topics, account, background, gestures, clear history, playback report, onboarding) | deleted | explaining is slop |
| Settings | Asks sponsor.ajay.app about each YouTube video you open. | Uses sponsor.ajay.app | third-party hint, five words max |
| Settings | Asks returnyoutubedislike.com about each video you open. | Uses returnyoutubedislike.com | third-party hint |
| Settings | sign-in and sign-out dialog bodies | deleted | nothing is lost |
| Settings | Watched videos and past searches are removed. This cannot be undone. | This cannot be undone. | body says only what is lost |
| Settings | You have the latest version. / Site support is up to date. | Up to date. | shorter |
| Settings | Update check failed. Check your connection and try again. | Could not check. Try again. | shorter |
| Settings | Export failed. Try again. / Import failed. Check the file and try again. | Export failed. / Import failed. | shorter |
| Settings | This file adds playlists N, playlist videos N, likes N and channels N. Nothing you saved is changed or removed. | Adds N playlists, N videos, N likes, N channels. | shorter |
| Settings | Nothing new to add. Everything in this file is already in your library. | Nothing new to add. | shorter |
| Shared | No connection. Check your network and try again. | No connection. | shorter |
| Shared | This video is gone, private, or never existed. | This video is unavailable. | shorter |
| Shared | This page needs a sign-in, a CAPTCHA, or a region the app cannot reach. | This video needs a sign-in or is blocked here. | shorter |
| Shared | This link expired. Open the video again to get a new one. | Link expired. Open the video again. | shorter |
| Empty states | second sentences on Home, Shorts, Channel, Listing, Library, Playlist, Downloads ("Subscribe to a channel to fill your feed", "Videos you download appear here and play offline", …) | deleted; "No <noun> yet." stands alone | explaining is slop |
| Errors | Could not load N channels. Check your connection and pull down to refresh. | N channels did not load. | shorter |
| Errors | Could not load Shorts. Check your connection and try again. | Shorts did not load. Try again. | shorter |
| Library | Playlist not renamed. That name may already be used. | That name is taken. | shorter |
| Library | Every watched video is removed from History. This cannot be undone. | This cannot be undone. | body says only what is lost |
| Library | "x" is deleted. The videos in it are not. | deleted | nothing else is lost |
| Library | unsubscribe / unfollow dialog bodies | deleted | restate the title |
| Downloads | Remove keeps the file on this phone. Delete erases it. Delete frees SIZE. | Delete frees SIZE. | body says only what is lost |
| Downloads | Removed from the list. The file could not be deleted. | File not deleted. | shorter |
| Downloads | This file is no longer on this phone. / No app can open this video. / This file cannot be opened from here. | File missing. / No app can open this. / Cannot open this file. | shorter |
| Detail | No captions for this video / Caption format not supported / Checking quality options | No captions / Captions not supported / Checking quality | toast, four words |
| Detail | Stop skipping sponsored segments on this channel / Skip sponsored segments on this channel again | Stop skipping on this channel / Resume skipping on this channel | shorter |

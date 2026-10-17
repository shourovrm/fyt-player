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
- Hint: one line, at most twelve words, states the effect. Present only when the label is not
  enough. A hint that names a third-party host is the privacy disclosure and stays.
- Status line: what happened, then what you can do. Error: what failed, then one action.
- Empty state: "No <plural noun> yet." plus at most one sentence of what fills it.
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

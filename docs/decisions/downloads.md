# Downloads: queue, stream downloader, export

## Current state

2026-10-06 PipePipe-port (tests green, device-UNverified): `download/UrlRecovery.kt` -- a
mid-file 403/410 on a YouTube stream download triggers ONE invalidate + re-resolve per run,
continues the same format id from the bytes on disk; only after the current URLs have served
bytes (a 403 on a fresh first request is not retried). Format gone / size changed / fresh URL
refused = honest failure, part kept.

2026-10-06 review wave (425 tests green, device-UNverified): part files are keyed by format
(`<base>.<role>.<formatTag>.part`, `partFileName`); other-format and legacy-named parts are
deleted before a fetch (`stalePartFileNames`) -- cancelling 1080p then downloading 720p used to
resume one stream into the other and mark the corrupt mux COMPLETED. Completion is size-checked
when the total is known (`completionCheck`; short = failed, part kept for resume; oversize =
deleted). `windowStep` replaces the old EOF test: a body shorter than its Content-Length is an
error, not EOF. `ActiveRun` exists from the moment a row becomes active, so pause/cancel during
the resolve seconds work, and every row write goes through the `rowWrites` mutex with a
`removed` flag -- a cancelled row can no longer be re-created by a late progress/COMPLETED
upsert. processNext catches everything (FAILED row, class name to DiagLog) and always resets in
`finally`. File names are capped by UTF-8 bytes (`truncateToUtf8Bytes`, 208-byte title).

2026-09-24 (v0.2.21) DEVICE-VERIFIED (Nothing A059): Long-press sheet scrolls (landscape clipped
Download). Stream downloads HEAD for the total when a format has no clen (row sat at 0% until
done) + ProgressMeter speed/ETA; device-verified 80% -> 95% -> done with MB/s + ETA.

2026-08-30 (v0.2.20) DEVICE-VERIFIED (Nothing Phone): Downloads: quality sheet sizes via HEAD
Content-Length on open (one dialog = one video, I/O allowed), live speed/ETA + final
size/duration (startedAt/finishedAt), "Download complete/failed" one-shot notification +
stopForeground when drained, "Also save subtitles" checkbox writes a same-basename sidecar
(findProducedFile skips .srt/.ttml/.vtt or the row points at the sidecar), thumbnailUrl column
(DB v7).

2026-08-10 (v0.2.3): YouTube downloads offer progressive-only options and StreamDownloader refuses
manifests (was saving .m3u8 as the video).

2026-08-07 wave: Optional **download folder** (`settings/DownloadSettings.kt`,
`Prefs.downloadTreeUri`): SAF tree picker via `OpenDocumentTree`, persisted read+write grant.
Production download path is untouched -- `DownloadQueue.processNext`'s `EngineOutcome.Done` branch
best-effort COPIES the finished app-private file into the tree (`download/DownloadExport.kt`,
`DocumentsContract.createDocument` + stream copy) after the row is already COMPLETED; copy failure
is swallowed, private file stays the source of truth. `FyiApp` mirrors the pref into a `@Volatile`
field (same pattern as `maxHeightWifi`) and hands `DownloadQueue.get` a `treeUri: () -> String?`
lambda.

- YouTube downloads now stream via the extractor chain + OkHttp + MediaMuxer
  (`download/StreamDownloader.kt`); yt-dlp keeps every non-YouTube source. Age-gated download
  verified end-to-end on device (h264+aac mp4, ffprobe-clean).

## Gotchas

- Since v0.2.24 the resolver fills `MediaFormat.filesizeBytes` for YouTube adaptive formats
  (itag contentLength), so the quality sheet and StreamDownloader skip their HEAD for those.
- `processNext` picks from the `rows` StateFlow snapshot, which trails Room. A row that is no
  longer QUEUED in the DB makes `runRow` wait (<=2 s) for the snapshot instead of returning at
  once, or the service loop spins on the stale row.
- A YouTube "1080p" download option can map to the HLS master (manifest wins FormatSelector);
  StreamDownloader saving it produces a .m3u8 file as the "video". Downloads must select from
  progressive formats only (engine path keeps manifests — yt-dlp fetches segments itself).
- Downloads need `--newline`, or the engine rewrites one progress line with `\r` and the callback
  never sees a complete line: progress sits at 0% forever.
- The engine writes a bare `NA` (invalid JSON) for eta/speed on the first progress tick. The
  parser rewrites `: NA` to `: null` before decoding.
- Downloads go through `DownloadQueue.enqueue`, never a hand-built repository row: the queue is
  what pairs video-only with audio-only and what starts the service. The single highest-`height`
  format is usually video-only, so picking it directly yields a silent, audio-less file.
- Finished downloads open via `FileProvider` (`${applicationId}.files`, `res/xml/file_paths.xml`).
  `ACTION_VIEW` on a `file://` Uri throws `FileUriExposedException` at this targetSdk.

# Downloads: queue, stream downloader, export

## Open items

- YouTube downloads now stream via the extractor chain + OkHttp + MediaMuxer
  (`download/StreamDownloader.kt`); yt-dlp keeps every non-YouTube source. Age-gated download
  verified end-to-end on device (h264+aac mp4, ffprobe-clean).

## Gotchas

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

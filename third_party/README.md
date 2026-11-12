# third_party

## PipePipeExtractor (GPLv3)
Git submodule `PipePipeExtractor` = github.com/shourovrm/pipe-extractor-patched, branch `patched`: upstream
PipePipeExtractor (commit `1a09d20e`, v5.5.0 + 1 NicoNico fix) plus our patch commits on top (shorts shelves in
search and related items, uploader handle, embedded player for refused kids videos, live streams from the HLS
manifest, no caption download during extraction). The TV app (tubepipe) pins the same commit. The Gradle group is
still upstream's `com.github.InfinityLoop1308.PipePipeExtractor`: `includeBuild` matches the dependency on that name,
so it is a label, not a download from upstream. Built by `includeBuild` in `settings.gradle.kts`.

- Setup after clone: `git submodule update --init`.
- Change recipe: commit in the submodule checkout, push the `patched` branch, then pin the new commit here and in
  tubepipe (`git add third_party/PipePipeExtractor`). Run `./gradlew clean` after moving the pin: the composite build
  keeps the old jar otherwise.
- Upstream bump: in the patched repo, `git fetch upstream` and rebase `patched` onto the new upstream commit, push,
  then pin both apps.

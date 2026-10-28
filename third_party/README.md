# third_party

## PipePipeExtractor (GPLv3)
Vendored source of our extractor fork, built by `includeBuild` in `settings.gradle.kts`.

- Base: upstream PipePipeExtractor v5.2.5 (`aa72c976`) + our 2 patches in `patches/`
  (shorts shelves in search and in related items). Fork: github.com/shourovrm/PipePipeExtractor.
- This folder is the source of truth for the build. Nothing outside the repo is needed.
- Update recipe, when upstream `~/repos/PipePipe` changes: `git -C ~/repos/PipePipe pull`, check out the
  wanted extractor tag in its `PipePipeExtractor` submodule, re-apply `patches/*.patch`
  (`git apply --check` first, redo a patch by hand if it no longer applies), replace this folder's
  contents, run `./gradlew clean` (an incremental build keeps the old jar), re-test on device, update
  the Base line above and `docs/decisions/extraction.md`.

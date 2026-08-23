# FYT Player

Android app (Kotlin, Compose, single Activity): browse, play, queue and download videos from mainstream social and video platforms via a yt-dlp-class extraction backend. Sideloaded APK. Background playback is a first-class feature. Target shape lives in `DESIGN.md`.

## Token economy
- If `caveman` plugin/skill available: keep active (full). Terse output always.
- If `ponytail` plugin/skill available: keep active (full). Laziest working solution.
- Fallback (no plugins): terse replies, no filler; YAGNI, stdlib/native before dependencies, shortest working diff.

## Memory — MANDATORY
Maintain `DECISIONS.md` with five sections:
- **Current state** — living snapshot, edit in place.
- **Next** — in-flight work handoff, 3-5 bullets max, prune ruthlessly.
- **Gotchas** — living quirks (flaky tests, env vars, wrong docs), one line each.
- **Tried / rejected** — one line: what + why dead; never re-attempt anything listed.
- **Log** — append-only: `YYYY-MM-DD | decision | why`.

Read "Current state" + "Next" + "Gotchas" + "Tried / rejected" before any work.
Update in the same commit as the change it describes. Terse. If missing, create it with those five section headers.
Split of memory: CLAUDE.md = rules, DESIGN.md = target shape, DECISIONS.md = knowledge, git history = events. Don't duplicate across them.
Large codebase? `/graphify` (if available) for persistent structural memory.

## Commits
- Every feature/code change = one terse commit immediately. Conventional type prefix (feat/fix/docs/chore), subject ≤50 chars.
- NO AI trailers (no Co-Authored-By etc.) — repo carries no AI traces.
- DECISIONS.md and planning docs: committed — they are the project's working memory and this repo is the only copy.

## Style
- KISS, UNIX philosophy: one file/module = one job, keep files small.
- Terse comments per code block; reusable/templated code.
- Comments explain WHY, especially where the obvious approach is wrong. Never leave a comment describing behaviour the code does not have — a lying comment is worse than none. Same for UI copy: a setting must say what it actually does.
- Check current library docs before using an API (Context7 MCP if available). Don't trust memory for API shapes; verify a version before relying on a feature.

## Project rules
- **Extraction never lives in UI code.** Per-platform parsing stays in its own module under `source/`, behind the one `VideoSource` interface. UI knows a `VideoRef`, never a selector.
- **One resolver seam.** Media URLs come only from the `StreamResolver` chain.
- **Persist canonical page URLs only.** Signed/tokenised media and image URLs stay in memory — never the database, a log, an export file, or a bug report. They expire and they identify.
- Redact cookies, signatures, tokens, IPs and media URLs from all logs and crash output.
- **No access-control bypass.** Stop at login, CAPTCHA, paywall, geo-block, age wall, DRM, rate limit → render an honest unavailable state. No retry storms, no request forging, no signature reconstruction. Platform terms restrict extraction; this boundary is what separates a client from a liability.
- Cookies isolated per service/domain. Never one shared jar.
- **Background playback is wanted here:** keep the media session + foreground service and the lockscreen/headset/Bluetooth controls. Audio focus and becoming-noisy belong on the player, not the session — set both explicitly.
- Anything called per visible list item does no I/O. Derive from what the listing already gave you, or return null.

## Parallelism
When tasks touch disjoint files, run multiple cost-efficient subagents IN PARALLEL (background). Serialize only true conflicts: same-file edits, shared exclusive resources. Build-tool/git locks self-queue — agents retry once on lock errors.

Subagents must not run Gradle — a stale-jar hazard yields false greens. One clean build at integration, run by the main session.

A subagent's report is a claim, not a fact. Verify anything load-bearing yourself, and be especially sceptical of a report that attributes an observation to the user — check the actual conversation before acting on it.

## Agent model tiers
- cheapest model: search, fetch, docs lookup, pure transcription
- mid model: tests, implementation, verification (fresh-eyes review after every logic-heavy task)
- strong model: complex judgement, adversarial review of risky modules (security, data integrity, money), final whole-branch review
- main session: orchestration + final review only — delegate the rest

## Verification
Nothing is "done" until exercised for real: run the test suite AND drive the changed flow end-to-end on a real device/emulator (install the APK, actually search-play-download). Report failures verbatim. Clean up test data. Never claim a fix works because it compiles.
Extractor tests run against saved fixtures in CI; live-site smoke tests are manual and rate-limited.

## Secrets & confidentiality
- Credentials, keys, tokens, personal data: gitignored files only, chmod 600, never printed to output/transcripts, never in commit history.
- Keystore + `keystore.properties` gitignored. Never commit an APK signing key.
- Before any public push: audit HEAD *and history* for leaks (signed URLs count as leaks).

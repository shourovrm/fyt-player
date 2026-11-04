System: material3

Source of every number: androidx material3 1.4.0 and m3.material.io (see
`~/.claude/skills/design-system/references/material3.md`). This file wins over the skill and the
reference wherever they disagree. Change it in the same commit as the code that departs from it.

## Direction

Chosen 2026-11-05 from full-page mocks: "Grid & sheets" (thumbnail grids, secondary pages as
sheets), underline Home tabs, square segmented buttons and switches. Tokens:

- Fonts: Schibsted Grotesk (`res/font/schibsted_grotesk.ttf`, variable, OFL) for every role;
  Spline Sans Mono (`spline_sans_mono.ttf`, OFL) via `NumeralStyle` for times, durations, sizes,
  speeds and counts.
- Colour, dark (default): bg `#000000`, surface `#101012`, surfaceContainerHigh `#1A1A1F`,
  surface3 `#2A2A31`, outline `#24242A`, ink `#F2F2F4`, ink2 `#9D9DA8`, ink3 `#7E7E89`, accent
  `#C6F24E` on `#0C1100`, selection tint accent at 15%, error `#FF7B6B`. Light keeps its neutrals;
  accent `#4A6600`.
- Shape: no pills. `ControlShape` 4 dp on buttons, chips, segmented buttons, switch tracks, the
  search field and nav indicators; 8 dp small and 12 dp grid thumbnails; 28 dp sheet tops.
- Density: grids, not rows, for browsing; 56 dp rows in sheets and settings.
- Words: no explanatory subtitles or hints unless the label alone would cause a mistake.
- Restraint: one selection cue per control (tint, no extra check); no tiles or tinted backdrops
  behind icons; section headers in onSurfaceVariant, never accent; the normal state carries no
  label (only Paused, Failed and the like are spelled out).

## Type

Material roles only, from `ui/theme/Type.kt`; no `.copy(fontSize = …)` anywhere else. titleLarge
22/28 sp 600 (app bar, sheet titles); titleMedium 16/24 600 (video and card titles); bodyLarge
16/24 400 (row titles, fields); bodyMedium 14/20 400 (body, settings labels); bodySmall 12/16 400
(meta); labelLarge 14/20 500 (buttons, tabs, segments); labelMedium 12/16 500 (section labels);
labelSmall 11/16 500 (badges). Numbers merge `NumeralStyle`. Layouts survive font scale 1.3.

## Space

Base 8 dp. Scale in `ui/theme/Spacing.kt`: 8, 16, 24, 32, 40, 48, 56, 64 (`Spacing`), component
sizes (`Sizes`), radii (`Radius`). Inline dp allowed outside the theme: 0, 1, 2, 4, 6, 10 only.
Page margin 16 dp under 600 dp width, 24 dp above. Grid gutter 8 dp.

## Controls

Button 40 dp high, labelLarge, `ControlShape`, one filled per page. Text field and search field
56 dp, `ControlShape`. Segmented button 48 dp, 1 dp outline dividers, selected = accent tint
with accent text. Switch: 48 x 28 dp square track, `ControlShape`. List item 56 dp one-line,
72 dp two-line. Bottom bar 64 dp (Material 3 short bar, icons only): Home, Shorts, Library,
Downloads, each with a content description and a long-press tooltip; selected = 4 dp-cornered
accent tint behind the icon. Touch target 48 x 48 dp on every clickable, including icons over
video. Never set `LocalMinimumInteractiveComponentSize` to 0.

## Colour

Roles: primary/onPrimary for the one accent; secondaryContainer for selection tints; surface,
surfaceContainer, surfaceContainerHigh for elevations; onSurfaceVariant for meta text; error for
LIVE and failures; `fyiExtras` for positive, warning, ink3, surface3, `onVideo`, `videoScrim`.
No `Color(0x…)`, `Color.White` or `Color.Black` outside `ui/theme`. Status colour on badges only,
never body text. Contrast 4.5:1 small text, 3:1 large text and icons.

## Layout of the Home page

App bar: titleLarge "FYT Player", search and settings icons. Underline tab row of topics (For you
first), accent indicator, scrollable, labels never truncated. Feed: 2-column grid of cards (12 dp
16:9 thumbnail with duration badge, titleMedium 2 lines, bodySmall meta; long-press opens the
menu); 4
columns in landscape. Mini player floats above the bottom bar.

## Layout of the Detail page

Pinned 16:9 player, titleMedium title, bodySmall meta, channel row with Subscribe, one row of
icon buttons (Like, Add to playlist, Download, Share, skip-segment shield), then tabs (Similar,
Description, Comments). Quality, speed, captions, audio language, chapters and queue open as
bottom sheets over the page, never a new screen. Back closes a sheet before leaving the page.

## Layout of Shorts, Library and Downloads

Shorts: 3-column 9:16 grid, a tile opens the full-bleed pager. Library: icon segmented filter
(History, Likes, Playlists, Channels), playlists as a 2-column grid. Downloads: rows with the
progress ring on the thumbnail as the pause/resume button; remove asks in a sheet.

## Layout of the Settings page

From the app-bar settings icon. Hub of eight rows: plain 24 dp icon in onSurfaceVariant, bodyLarge
label; only Account and About show a bodySmall state. A row opens its page as a sheet (titleLarge
title, close icon): 56 dp rows, switch or trailing value; up to four values = segmented button,
more = check-mark list; destructive rows in error text with a confirm sheet.

## Layout of the Player chrome

Bottom row: elapsed, scrubber, total (numerals), fullscreen. Above it: speed, quality, captions,
audio language, chapters. Transport centred. Every icon 48 dp with a 24 dp glyph; text over
video is onVideo on videoScrim. Landscape: the option icons stack in a strip on the right.

## Gates

- `DesignTokensGateTest` fails on any `.dp` not in the Space list, any `.sp`, raw colour or pill
  shape outside `ui/theme`.
- `lint.xml`: `HardcodedText` and `ContentDescription` at error, `abortOnError true`.
- Copy gate: every visible string in `res/values/strings*.xml`; `CopyGateTest` fails on a quoted
  literal passed to `Text(`, `contentDescription =`, `label =`, `placeholder =`, or a snackbar.
- Screens: device screencap at font scale 1.0 and 1.3, dark and light, per page.

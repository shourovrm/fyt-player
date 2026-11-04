System: material3

Source of every number: androidx material3 1.4.0 and m3.material.io (see
`~/.claude/skills/design-system/references/material3.md`). This file wins over the skill and the
reference wherever they disagree. Change it in the same commit as the code that departs from it.

## Direction

Chosen 2026-11-05 from full-page mocks: "Grid & sheets" (thumbnail grids, secondary pages as bottom
sheets), Home topics as an underline tab row, settings controls as square segmented buttons and
square switches. Tokens:

- Fonts: Schibsted Grotesk (`res/font/schibsted_grotesk.ttf`, variable, OFL) for every role;
  Spline Sans Mono (`spline_sans_mono.ttf`, OFL) via `NumeralStyle` for times, durations, sizes,
  speeds and counts.
- Colour, dark (default): bg `#000000`, surface `#101012`, surfaceContainerHigh `#1A1A1F`,
  surface3 `#2A2A31`, outline `#24242A`, ink `#F2F2F4`, ink2 `#9D9DA8`, ink3 `#7E7E89`, accent
  `#C6F24E` on `#0C1100`, selection tint accent at 15%, error `#FF7B6B`. Light keeps its neutrals;
  accent `#4A6600`.
- Shape: no pills. `ControlShape` 4 dp on buttons, chips, segmented buttons, switch tracks, the
  search field and nav indicators; 8 dp settings icon tiles and small thumbnails; 12 dp grid
  thumbnails; 28 dp top corners on sheets.
- Density: grids, not rows, for browsing; 56 dp rows in sheets and settings.
- Words: no explanatory subtitles or hints unless the label alone would cause a mistake.

## Type

Material roles only, from `ui/theme/Type.kt`; no `.copy(fontSize = …)` anywhere else.

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| titleLarge | 22 / 28 sp | 600 | app bar titles, dialog and sheet titles |
| titleMedium | 16 / 24 sp | 600 | video title on Detail, grid card title |
| bodyLarge | 16 / 24 sp | 400 | row title, text field text |
| bodyMedium | 14 / 20 sp | 400 | body, mini player title, settings row label |
| bodySmall | 12 / 16 sp | 400 | card and row meta line |
| labelLarge | 14 / 20 sp | 500 | buttons, tabs, segmented buttons |
| labelMedium | 12 / 16 sp | 500 | section labels |
| labelSmall | 11 / 16 sp | 500 | duration badge |

Numbers merge `NumeralStyle` into their role. Layouts survive font scale 1.0 and 1.3.

## Space

Base 8 dp. Scale in `ui/theme/Spacing.kt`: 8, 16, 24, 32, 40, 48, 56, 64 (`Spacing`), component
sizes (`Sizes`), radii (`Radius`). Inline dp allowed outside the theme: 0, 1, 2, 4, 6, 10 only.
Page margin 16 dp under 600 dp width, 24 dp above. Grid gutter 8 dp.

## Controls

Button 40 dp high, labelLarge, `ControlShape`, one filled per page. Text field and search field
56 dp, `ControlShape`. Segmented button 48 dp, 1 dp outline dividers, selected = accent tint
with accent text. Switch: 48 x 28 dp square track, `ControlShape`. List item 56 dp one-line,
72 dp two-line. Bottom bar 80 dp, four icon-only destinations (Home, Shorts, Library,
Downloads), each with a content description and a long-press tooltip; selected = 4 dp-cornered
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
16:9 thumbnail with duration badge, titleMedium 2 lines, bodySmall meta, overflow icon); 4
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

Reached from the settings icon in the app bar. Hub of eight rows: leading icon in an 8 dp accent-
tinted tile, bodyLarge label. Account and About show a bodySmall state (signed in, version); the
other six have no second line. A row opens its page as a bottom sheet with a titleLarge title and
close icon. In a sheet: 56 dp rows, switch or trailing value; a choice of up to four values is a
segmented button; a longer choice is a check-mark list. Destructive rows use error text and a
confirm sheet.

## Layout of the Player chrome

Bottom row: elapsed, scrubber, total (numerals), fullscreen. Above it: speed, quality, captions,
audio language, chapters. Transport centred. Every icon 48 dp with a 24 dp glyph; text over
video is onVideo on videoScrim. Landscape: the option icons stack in a strip on the right.

## Gates

- JVM test `DesignTokensGateTest` scans every `.kt` outside `ui/theme` and fails on any `.dp`
  not in the Space list, any `.sp`, any `Color(0x`, `Color.White`, `Color.Black`.
- `lint.xml`: `HardcodedText` and `ContentDescription` at error, `abortOnError true`.
- Copy gate: every visible string in `res/values/strings*.xml`; `CopyGateTest` fails on a quoted
  literal passed to `Text(`, `contentDescription =`, `label =`, `placeholder =`, or a snackbar.
- Screens: device screencap at font scale 1.0 and 1.3, dark and light, per page.

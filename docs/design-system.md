System: material3

Source of every number: androidx material3 1.4.0 and m3.material.io (see
`~/.claude/skills/design-system/references/material3.md`). This file wins over the skill and the
reference wherever they disagree. Change it in the same commit as the code that departs from it.

## Direction

Chosen 2026-11-05 from full-page mocks: "Grid & sheets" (secondary pages as sheets), underline
Home tabs, square segmented buttons and switches; revised the same day to monochrome, plain white
pages, a labelled 56 dp bar and the round pre-redesign player. Tokens:

- Fonts: Schibsted Grotesk (`res/font/schibsted_grotesk.ttf`, variable, OFL) for every role;
  Spline Sans Mono (`spline_sans_mono.ttf`, OFL) via `NumeralStyle` for times, durations, sizes,
  speeds and counts.
- Colour: monochrome text and pages. Dark (default): bg `#000000`, ink `#F1F1F1` on `#0F0F0F`,
  meta `#AAAAAA`. Light: `#FFFFFF` pages, ink `#0F0F0F`, meta `#606060`, hairline `#E5E5E5`.
  Icons carry a fixed colour per role (`IconColours`). Red only for LIVE and errors.
  Watched-progress bars are white on a video scrim.
- Shape: `ControlShape` 4 dp on buttons, chips, segmented buttons, switch tracks and the search
  field; 8 dp small and 12 dp grid thumbnails; 28 dp sheet tops. The player keeps round controls.
- Density: 56 dp rows in sheets. Content sits flat on the page colour, never in tinted blocks.
- Words: no explanatory subtitles or hints unless the label alone would cause a mistake.
- Restraint: one selection cue per control (colour only); tiles only in Settings; section headers
  in onSurfaceVariant; no label on the normal state (only Paused, Failed and the like).

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

Button 40 dp high, labelLarge, `ControlShape`, one filled per page. Text and search fields 56 dp,
`ControlShape`. Segmented button 48 dp, 1 dp dividers, selected = colour tint, ink text. Switch:
48 x 28 dp square track, `ControlShape`. List item 56 dp one-line, 72 dp two-line. Bottom bar 56 dp, page colour, 1 dp top hairline: Home, Shorts, Library, Downloads,
icon over labelSmall label; selected = tab's icon colour, SemiBold ink label, no indicator.
Touch target 48 x 48 dp on every clickable, including icons over video. Never set
`LocalMinimumInteractiveComponentSize` to 0.

## Colour

Roles: primary/onPrimary for the one accent; secondaryContainer for selection tints; surface,
surfaceContainer(High) for elevations; onSurfaceVariant for meta; error for LIVE and failures;
`fyiExtras` for positive, warning, ink3, surface3, `icons`, `onVideo`, `videoScrim`.
No `Color(0x…)`, `Color.White` or `Color.Black` outside `ui/theme`. Status colour on badges only,
never body text. Contrast 4.5:1 small text, 3:1 large text and icons.

## Layout of the Home page

App bar: titleLarge "FYT Player", search, refresh (For you), layout, settings. The layout icon shows
the current layout; a tap cycles cards, compact, grid for the session; the default (cards) is in
Settings > Content. Underline tab row of topics (For you first), scrollable. Cards: full-width 16:9 thumbnail, titleMedium 2 lines,
bodySmall meta. Compact: 120 x 68 dp thumbnail left, text right. Grid: 2 columns, 4 in landscape.
Long-press opens the menu in every layout. Mini player floats above the bottom bar.

## Layout of the Detail page

Pinned 16:9 player, titleMedium title, bodySmall meta, channel row (40 dp avatar, name, @handle · subscribers, bell), one row of
icon buttons (Like, Add to playlist, Download, Share, skip-segment shield), then tabs (Similar,
Description, Comments). Quality, speed, captions, audio language, chapters and queue open as
bottom sheets over the page, never a new screen. Back closes a sheet before leaving the page.

## Layout of Shorts, Library and Downloads

Shorts: 3-column 9:16 grid opening the full-bleed pager. Library: icon segmented filter, playlists
as a 2-column grid. Downloads: rows, the thumbnail's progress ring pauses; remove asks in a sheet.

## Layout of the Settings page

From the app-bar settings icon. Eight rows: a 24 dp white glyph on a 36 dp tile (8 dp radius)
in the row's colour; bodyLarge label; only Account and About show a bodySmall state.
A row opens its page as a sheet (titleLarge, tile, close icon). Sheet primary is the row colour
(switches, selected segments); text stays ink. 56 dp rows, switch or trailing value; up to four
values = segmented button, more = check list; destructive rows: error text, confirm sheet.

## Layout of the Player chrome

Pre-redesign layout and shapes. Top right: chapters, captions, audio language as 48 dp round
scrim buttons. Centre: previous and next 48 dp circles, play 64 dp circle. Bottom row: time,
speed (12 dp pill when not 1x), quality badge, autoplay, loop, fullscreen; seek line on the
video's bottom edge. Text over video is onVideo on videoScrim.

## Gates

- `DesignTokensGateTest` fails on any `.dp` not in the Space list, any `.sp`, raw colour or
  `RoundedCornerShape(50)` outside `ui/theme`.
- `lint.xml`: `HardcodedText` and `ContentDescription` at error, `abortOnError true`.
- Copy gate: every visible string in `res/values/strings*.xml`; `CopyGateTest` fails on a quoted
  literal passed to `Text(`, `contentDescription =`, `label =`, `placeholder =`, or a snackbar.
- Screens: device screencap at font scale 1.0 and 1.3, dark and light, per page.

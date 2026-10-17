System: material3

Source of every number: androidx material3 1.4.0 and m3.material.io (see
`~/.claude/skills/design-system/references/material3.md`). This file wins over the skill and the
reference wherever they disagree. Change it in the same commit as the code that departs from it.

## Type

Material roles only, from `ui/theme/Type.kt`; no `.copy(fontSize = …)` anywhere else.

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| titleLarge | 22 / 28 sp | 400 | app bar titles, dialog titles |
| titleMedium | 16 / 24 sp | 500 | video title on Detail, section headers in sheets |
| bodyLarge | 16 / 24 sp | 400 | result row title, text field text |
| bodyMedium | 14 / 20 sp | 400 | body, mini player title, settings row label |
| bodySmall | 12 / 16 sp | 400 | row meta line, settings hint, captions |
| labelLarge | 14 / 20 sp | 500 | buttons, chips, tabs, settings section header |
| labelMedium | 12 / 16 sp | 500 | nav bar labels, feed section labels |
| labelSmall | 11 / 16 sp | 500 | duration badge, chip counts |

Font: `FontFamily.Default` (device sans). Layouts survive font scale 1.0 and 1.3; fixed heights
grow with text.

## Space

Base 8 dp. Allowed dp outside `ui/theme/Spacing.kt`: 2, 4, 6, 10 (nested units) and
8, 16, 24, 32, 40, 48, 56, 64. Page margin 16 dp under 600 dp width, 24 dp above. Row gap
8 dp vertical. Thumbnail 120 x 68 (16:9 snapped to the 4 dp grid). Radius: 4 badge, 8 thumbnail
and chip, 12 card and error row, 28 sheet, full pill and search bar.

## Controls

Button 40 dp high, labelLarge, one filled per page, text or outlined for the rest. Text field
56 dp. Search bar 56 dp, full radius, 16 dp side margin. Filter chip 32 dp. List item 56 dp
one-line, 72 dp two-line, 88 dp three-line. Nav bar 80 dp, five destinations, 64 x 32 pill.
Touch target 48 x 48 dp on every clickable, including icon buttons over video; a smaller
glyph sits inside a 48 dp button, never `Modifier.size(<48.dp)` on the button. Never set
`LocalMinimumInteractiveComponentSize` to 0.

## Colour

Scheme in `ui/theme/Color.kt` and `Theme.kt`, dark is the product default. Roles: primary and
onPrimary for the one accent; secondaryContainer for selection pills and selected rows;
surface, surfaceContainer, surfaceContainerHigh for the three elevations; onSurfaceVariant for
meta text; error and onError for LIVE and failures; `fyiExtras` for positive, warning, ink3,
surface3, plus `onVideo` (white over video) and `videoScrim` (black at 72% and 45%). No
`Color(0x…)` or `Color.White` / `Color.Black` outside `ui/theme`. Status colour lives on badges
and pills, never on body text. Watched progress uses primary; LIVE uses error. Contrast 4.5:1
small text, 3:1 large text and icons.

## Layout of the Home page

Search bar, then one row of filter chips (For you + topics), then the feed as a LazyColumn of
72 dp two-line rows under one labelMedium section label. Mini player docks above the nav bar
and outlives it. Primary action: the search bar. Refresh is a trailing icon in the bar.

## Layout of the Detail page

Pinned 16:9 player, then titleMedium title, bodySmall meta, one row of 40 dp buttons (Like,
Add to playlist, Download, Share) with Download filled, then tabs (Similar, Description,
Comments) of 72 dp rows. Primary action: Download.

## Layout of the Settings page

Sections of 56 dp one-line rows (bodyMedium label, bodySmall hint beneath only where the label
is not enough, switch trailing). Section header labelLarge in primary with 16 dp margin.
Destructive rows (clear history, sign out) use error text and a confirm dialog.

## Layout of the Player chrome

One 48 dp control row (elapsed, scrubber, total, fullscreen) at the bottom; transport
centred; every icon button 48 dp with a 24 dp glyph; text over video is onVideo on
videoScrim. Overflow for quality, speed, captions, loop.

## Gates

- JVM test `DesignTokensGateTest` scans every `.kt` outside `ui/theme` and fails on any `.dp`
  not in the Space list, any `.sp`, any `Color(0x`, `Color.White`, `Color.Black`.
- `lint.xml`: `HardcodedText` and `ContentDescription` at error, `abortOnError true`.
- Copy gate: every visible string in `res/values/strings.xml`; test fails on a quoted literal
  passed to `Text(`, `contentDescription =`, `label =`, `placeholder =`, or a snackbar.
- Screens: emulator or device screencap at font scale 1.0 and 1.3, dark and light, per page.

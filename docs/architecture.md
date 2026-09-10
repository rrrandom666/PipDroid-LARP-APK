# App architecture

Decisions that shape where new code goes. Traps that bite while writing it are in
[pitfalls.md](pitfalls.md); tools and rules for moving code are in [refactoring.md](refactoring.md).

## Shape

One activity, `MainActivity.kt`, plus controllers that each own a screen: `MapController`,
`ClockController`, `StatsController`, `JournalController`, `BootSequenceController`,
`SetupWizardController`, `BluetoothController`. Pure helpers sit outside both:
`MenuNavigator`, `TextHelpers`, `GeoReference`, `PedestrianRouter`, `GlobalTextScale`,
and the repositories. `Data.kt` holds two live lists — the ~130 Fallout NV perks and
`ringtoneTracks` — not `strings.xml`.

`docs/map.md` has every member with line ranges. Read that before opening a controller.

## Theming is not optional

Four themes (Green/Amber/White/Blue) selected by `playerUIColour_SPKey`. **Every new or
reworked screen colours itself from the current theme** — buttons, icons, headings, body
text. This follows from the theme existing as a feature at all; it is not a per-screen choice.

How it actually works, because it is not the obvious way:

- The activity applies one of `Theme.PipDroid.{Green,Amber,White,Blue}UI` exactly once, in
  `onCreate`, via `theme.applyStyle(...)`. The theme is **never re-applied** and the activity
  is **not** recreated when the colour changes in Settings.
- So anything that must recolour live is coloured **by code**, reading
  `sharedPreferences.getInt(playerUIColour_SPKey, 0)`. Two parallel paths exist and both are current:
  - `applyAppTheme()` / `applyBackgroundResource()` / `applyTextColor()` / `applyProgressDrawable()`
    — explicit lists of specific views.
  - `currentWizardAccentColor()` — returns `themeGreen`/`themeAmber`/`themeWhite`/`themeBlue`
    for the index. Buttons take it through `button.backgroundTintList = ColorStateList.valueOf(accent)`
    over **neutral** (white/transparent) drawables; text through `setTextColor(accent)`.
- **`ImageView` needs nothing.** The theme defines `android:tint`, and an `ImageView` without
  its own tint inherits it through the default style. Verified on the Vault-Boy icon in the
  wizard (`pipboy_3nv.png`, an ordinary static PNG).
- `backgroundTintList` on buttons started life as a bug — AppCompat pulled a tone from the
  theme's `colorPrimary` over a custom drawable — and is now used deliberately. The flip side
  is a live trap on any view whose background is set from code: see
  [pitfalls.md — tint over a programmatic background](pitfalls.md#tint-lands-on-top-of-any-background-set-from-code).

## Navigation: encoder and menus

The physical encoder walks a tree; touch walks the same tree. Both go through `MenuNavigator`.

- Row 2 of the header is built only from `menuChangeBLE()` → `menuOptionClickedBLE()` → `setupRow2()`.
- `MenuNavigator.Level` **captures** its node list when the level is entered; `childrenProvider`
  runs only on descent. A level's contents are therefore frozen while the cursor sits inside it,
  even though the tree functions (`itemsMenuRoot()` and friends) recompute on every call.
- `menuNavigator.rebuildLevels()` refreshes the captured levels. It must run **before** row 2 is
  rebuilt, because `setupRow2()` reads `rootCursor()`. The cursor is restored by node id, not by
  index — GEIGER becomes the first ITEMS node in some modes and shifts everything after it.

## Operating modes

`PipBoyMode` is PHONE / PIPBOY_2000 / PIPBOY_3000. `refreshSidebarBackItems()` is the single
point where "the mode became known or changed" is handled, and **every mode-gated thing must be
registered there** — sidebar lists, back items, row 2, and the encoder level stack.
The failure mode when something is missed is misleading: highlighting lands on the wrong item and
the last one becomes unreachable, while both index calculations are individually correct. See
[pitfalls.md — mode gating](pitfalls.md#mode-gating-runs-down-four-paths-and-two-get-forgotten).

Careful with an empty row 2: its first build is gated by `row2Views.isEmpty()` in `onBootFinished`,
and rebuilding unconditionally breaks the startup sequence.

## Disabled means shaded, not disabled

Project convention: unavailability is **visual only** (`alpha`). The tap still reaches the handler,
and the handler plays `playErrorAudio()`. Sidebar items work this way — `SidebarMenuItem.enabled`
is tone only, and the screen decides the outcome. Voice commands work the same way.
Never reach for `View.isEnabled = false`; it eats the touch and leaves nowhere to play the sound.

## Activity result launchers stay in the activity

All four `registerForActivityResult` launchers are `MainActivity` fields, because launchers must be
registered before `onStart`. A controller receives the action it needs as a callback
(`requestEnableBluetooth`). When the launcher's callback calls back into the controller, the
references form a cycle and `kotlinc` fails with `Type checking has run into a recursive problem`
on the launcher line — which is not where the problem is. Break it with an explicit type on one
side: `private val bluetooth: BluetoothController by lazy`. The same pair exists for voice
(`openVoiceModelZipLauncher`).

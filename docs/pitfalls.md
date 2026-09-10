# Traps

Each one cost a device run or a debugging session to find. The edit hook
(`dev-tools/hooks/check_pitfalls.py`) catches the mechanical ones automatically on newly
written lines; the rest are here.

## Tint lands on top of any background set from code

A plain `TextView.setBackgroundColor(...)` renders in the theme accent instead of the colour
passed in. AppCompat mixes `backgroundTint` over a programmatically set background on **any**
view, not only buttons with their own drawable. Found on the map.

If code sets a background or text colour and the screen shows a different one — usually the
current theme accent — suspect this first, before hunting for a bug in your own logic. Fix with
an explicit `view.backgroundTintList = null` before `setBackgroundColor()` / `setBackgroundResource()`.
`android:backgroundTint="@null"` in XML or a style does **not** work on this AGP/AAPT2; the tone
can only be cleared from code.

## `isEnabled = false` makes a control silent

`View.isEnabled = false` looks like the natural way to switch a button off, but it swallows the
touch entirely: the handler never runs, so there is physically nowhere left to play the error
sound. That is exactly how [Pause] on the Timer screen went quiet while a wound timer was running.

Keep a permission flag next to the control and check it inside the shared handler body. See the
convention in [architecture.md](architecture.md#disabled-means-shaded-not-disabled).

## Styles with `layout_height="0.0dip"` need an explicit height

`SettingsButtonStyle`, `PipWizardButtonStyle` and `CNDEFFRADButtonStyle` all set the height in the
style itself as `match_constraint`. A view using one of them **must** carry an explicit
`android:layout_height` unless it has both a top and a bottom constraint. Without that the height
stays undefined and the view collapses to invisible — and not only visually: taps in its area fall
through to whatever is underneath. Every working example in the project (Settings, Bluetooth)
writes `layout_height` next to `style=`.

## A full-screen layer does not capture touch by itself

A view laid over the interface passes touches through to what is below until it has
`android:clickable="true"`. Stepped on twice: the PipBoy 2000/3000 wizard (fixed differently there,
by disabling the header and footer buttons, because the wizard has clickable elements of its own),
and the power on/off animation layer plus the black OFF screen, where taps during the sequence
switched tabs and pressed buttons underneath.

For layers with no controls of their own the right fix is `clickable`/`focusable` on the root.
Deliberate exception: the glitch overlay (`iv_glitch_overlay`) does **not** absorb touch — it flashes
for 60–150 ms over a working screen and a tap during it must reach the interface below.

## Focus brackets must be direct siblings of their target

The encoder's corner brackets (`focus_corner_brackets`, a separate `View` with a negative margin
over the button — `viewGeigerResetFocus` and friends) work only when the bracket is a **direct
child of the same `ConstraintLayout`** as the button it references through
`app:layout_constraintXxx_toXxxOf="@id/..."`. `ConstraintLayout` resolves those references only
among its immediate children. If the button actually sits one level deeper — inside a `LinearLayout`
added for `measureWithLargestChild` or weights — the bracket silently collapses to (0,0) and is
invisible, even though `visibility` is set correctly in code.

Found on Cancel/Save in the Journal editor, while the same trick worked on the neighbouring Mic
button that sat directly in the `ConstraintLayout`. The fix is not in `setFocusBracketsVisible()`
— it is moving the target button to the bracket's nesting level, at the cost of whatever the nested
`LinearLayout` was providing, which has to be rebuilt from constraints.

## `overScrollMode="never"` kills the scrollbar

Found on SPECIAL/Skills. The list scrolled, `computeVerticalScrollRange` exceeded `extent`, the
scrollbar was enabled, the thumb was set, and the geometry matched the working screens to the pixel
— but no bar. The only difference from the working Perks/Melody lists was this attribute; removing
it brought the bar back. The exact mechanism in the framework was never traced, but the correlation
had no counterexample, and the attribute only disables the glow when dragging past the edge. Do not
put it on anything that should have a scrollbar.

## Programmatic soft keyboard is usually ignored

`InputMethodManager.showSoftInput()` outside a direct response to a touch is frequently dropped by
Android. Nothing in this project calls it: Settings and Filter, the only screens with `EditText`,
rely on an ordinary tap on the field and system behaviour. That is the working pattern — do not try
to force `requestFocus()` / `showSoftInput()` when opening a panel that contains an input.

## Mode gating runs down four paths, and two get forgotten

Lists that depend on `pipBoyMode` are rebuilt by `refreshSidebarBackItems()`. The other three were
each caught separately, every one of them on a device run:

- **Row 2** is built only from `menuChangeBLE()` → `menuOptionClickedBLE()` → `setupRow2()`, and
  nothing touched it on a mode change — it kept the previous mode's contents and drifted one index
  away from the tree (GEIGER is hidden in Phone mode, so the two lists differ in length).
- **The encoder level stack.** `MenuNavigator.Level` captures its nodes on descent, so while the
  cursor is inside a level its contents stay frozen in the old mode: "To menu" is visible in the
  sidebar but the encoder cannot reach it, and after going up, the section root is one node shorter
  than row 2. `menuNavigator.rebuildLevels()` fixes it and must run **before** row 2 is rebuilt.
- Both symptoms are masked by ordinary use — creating an entry calls `replaceChildrenOf()` and
  repairs the level; moving to a neighbouring section and back calls `resetToRoot()` and repairs the
  root. The bug "goes away by itself", until the next mode change.

## A view's state token is not its display text

`updateBLEConnected()` set the visible text straight from `status: String` — an internal state token
that is compared with `==`. Fixed: `status` is for comparison only, and the text comes from
`R.string.bluetooth_status_connected` / `_disconnected`. General form: when a view has both a state
token and a display text, never let one variable play both roles.

## Screen A over screen B must close B, not just cover it

The PipBoy 2000/3000 wizard, opened through Settings → "Change", did not appear: `selectPipBoyMode()`
hid only the mode-select screen, while Settings stayed visible underneath and came back to the front
after the mode select closed, instead of the wizard's first step. Fixed by having `selectPipBoyMode()`
also close Settings the same way its own close button does. General form: if screen A can open over
screen B and A's logic shows screen C, check that B is closed too.

## What the compiler cannot see

Grep for these after any rename; the snapshot tool catches only part of it.

- `getIdentifier` resource names — `perk_<id>_name` / `perk_<id>_desc`. 277 lines of `strings.xml`
  look unused to every static analyser, `lint`/`UnusedResources` included, so it cannot be run blind here.
- SharedPreferences keys. Twelve are `*_SPKey` constants (see `docs/map.md`), eight are still bare
  string literals: `appLanguage`, `ShowTutorial`, `TrueFullscreen`, `AmbientSoundEnabled`, `width`,
  `height`, `leftMargin`, `topMargin`. A typo when moving one wipes the player's saved state.
- BLE text commands — the contract with the ESP32 firmware. See [ble.md](ble.md).
- The order of blocks inside `onCreate` — the compiler accepts any permutation.
- Unused imports — invisible to both the compiler and a grep over declarations. See
  [refactoring.md](refactoring.md#the-blind-spot-of-both-methods-unused-imports).

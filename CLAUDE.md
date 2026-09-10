# PipBoy LARP — working rules

A functional Pip-Boy for Fallout live-action roleplay: a fork of PipDroid (Malto4) running on the
player's own phone inside a 3D-printed Vault901 "Pip-Boy 2000 Mk VI" case, talking over BLE to an
ESP32-S3 that supplies buttons, an encoder, a Geiger counter and a holotape reader.

**Answer the user in Russian.** Everything written for Claude — this file, `docs/`, agents, commands,
the map — stays in English.

## Hard rules

- **Never publish or distribute the APK.** The fork has no answer from the upstream author yet, and
  decorative details must not be copied from Bethesda's official app. → `docs/legal.md`
- **Development status lives only in `PipBoy_Roadmap.md`**, never here. That file's section 2 →
  "Этапы разработки" is a flat numbered list: `✅` before an item means done, `👉` means next in
  queue, no mark means waiting. Per-feature changelogs go in separate `###` subsections of section 2,
  not inside the list. This file holds what is worth remembering between sessions and is *not* status.
- `master` takes documentation only (`PipBoy_Roadmap.md`, `CLAUDE.md`, `docs/`). Feature and app code
  goes on a dedicated `app-*` branch.
- **Every new or reworked screen colours itself from the current theme.** → `docs/architecture.md`
- **Comments: one sentence announcing a functional block.** Not a line-by-line retelling, not the
  history of a finding, not roadmap references. Editing comments must leave the bytecode identical.
- Button label strings are Title Case, never ALL CAPS — even when copying an existing string that
  got it wrong.
- Disabled means shaded (`alpha`) plus an error sound, never `View.isEnabled = false`.
- The build holds **zero** compiler warnings. A non-zero `w:` count is a regression, and those
  warnings pull real dead code and latent NPEs out with them.
- **The user runs every on-device test pass.** You install the build and read logs; you never drive
  the UI with `adb shell input`.
- The app's target vision (three modes, localization, real radio and map, holotapes, journal, clock,
  POWER animation) is fixed in `PipBoy_Roadmap.md` → "Видение приложения — финальная точка".

## Context discipline

Cost scales with the context each request carries, so the history is paid for again every turn.

- **Read `docs/map.md` before opening a big file**, then read only the range you need with
  `offset`/`limit`. `MainActivity.kt` is 3300+ lines, `PipBoy_Roadmap.md` is 4400+; neither is ever
  read whole. Regenerate the map with `/map` after a batch of edits.
- **Do not re-read a file in the same session.** What was read is still in context. If you need the
  current state after an edit, read only the changed range.
- **Batch independent calls** into one message — several reads and greps together, not one per turn.
- **Delegate high-volume routine to an agent** so its output never lands in the main thread:
  `builder` (Gradle build → verdict plus `w:` lines), `device-logs` (adb install → filtered logcat),
  `snapshot-diff` (refactor snapshots → match or not). Use the built-in `Explore` agent for broad
  searches across many files.
- **One task per session.** At the end, or when the Stop hook warns, run `/carry` and then `/clear`;
  the next session starts from five lines instead of the whole history.

Three hooks check mechanically what would otherwise cost a round trip: project pitfalls on every
edit, a map hint on a whole-file read, a session size counter on stop. → `.claude/settings.json`

## Where things are

| Need | File |
| --- | --- |
| Line ranges for any file, prefs keys, BLE tokens, roadmap sections | `docs/map.md` (generated) |
| Controllers, theming, encoder navigation, modes | `docs/architecture.md` |
| Traps that cost a device run to find | `docs/pitfalls.md` |
| Snapshot and extraction tooling, comment and test rules | `docs/refactoring.md` |
| Wire protocol summary, firmware notes | `docs/ble.md` (authority: `PipBoy_BLE_Protocol_v0.2.md`) |
| ESP32, Geiger mechanics, power, BOM, open questions | `docs/hardware.md` |
| CSV workflow, perks in `Data.kt`, language switching, font | `docs/localization.md` |
| Toolchain versions, LFS, dependencies, `dev-tools/` index | `docs/environment.md` |
| Fork permission and visual distance | `docs/legal.md` |
| Handoff from the previous session | `docs/session-carry.md` |

## Traps, one line each

Read `docs/pitfalls.md` before touching UI code; this list is only so you know what you don't know.

- AppCompat blends `backgroundTint` over **any** background set from code — clear
  `backgroundTintList` first, and `android:backgroundTint="@null"` does not work on this AGP.
- `isEnabled = false` swallows the touch, so a shaded control cannot make its error sound.
- Three styles set `layout_height="0.0dip"`; a view using one needs an explicit height or a
  top+bottom constraint pair, or it collapses and its taps fall through.
- A full-screen overlay does not capture touch without `clickable`; the glitch overlay deliberately
  does not.
- Encoder focus brackets only resolve against direct siblings in the same `ConstraintLayout`.
- `overScrollMode="never"` removes the scrollbar, not just the edge glow.
- Mode gating runs down four paths; `rebuildLevels()` must precede the row 2 rebuild.
- A programmatic `showSoftInput()` outside a touch is usually ignored — rely on the tap.
- Invisible to the compiler: `getIdentifier` resource names, SharedPreferences keys (eight are still
  bare literals), BLE command tokens, `onCreate` block order, unused imports.

## Commands

```bash
./gradlew assembleDebug              # build (Gradle JDK 17; prefer the builder agent)
./gradlew test                       # unit tests: MenuNavigator, TextHelpers, GeoReference, PedestrianRouter
python3 dev-tools/gen_map.py         # regenerate docs/map.md  (/map)
./dev-tools/refactor_snapshot.sh before | after | diff before after
./dev-tools/controller_boundary_measure.py --pattern '<screen words>'
```

Slash commands: `/map` regenerates the map, `/checks` runs everything mechanical, `/carry` writes the
handoff note. After cloning: `git lfs install && git lfs pull`, or the audio is 130-byte pointers and
`MediaPlayer` crashes.

# Development environment

## Machine and toolchain

Mac on an Intel Core i5 — **not** Apple Silicon, so emulator system images must be `x86_64`, never
`arm64-v8a`. macOS Sequoia 15.7.7. Repository at `/Users/alexander/fallout/PipDroid-LARP-APK`.

`Gradle 7.3.3`, `AGP 7.2.1`, `Kotlin 1.6.10`, `compileSdk`/`targetSdk 34`, **`minSdk 19`** (raised
from 18 for the visualizer library below). The Gradle JDK must be **17** — 25 is incompatible with
Gradle 7.3.3; use the Embedded JDK in `Settings → Build Tools → Gradle`.

The toolchain is pinned tightly and deliberately: an AppCompat upgrade was rejected rather than risk
the whole chain (see [localization.md](localization.md#interface-language)).

## A real phone is required

BLE does not work in the emulator, so a separate physical Android device is part of the setup. The
user runs every on-device pass themselves against `dev-tools/smoke-checklist.md`; the assistant
installs the build and reads logs (the `device-logs` agent), and never drives the UI through
`adb shell input`.

## Git LFS

Holotape and radio mp3 files go through Git LFS (`*.mp3 filter=lfs` in `.gitattributes`). After
cloning, `git lfs install && git lfs pull` is mandatory — otherwise the audio files are ~130-byte LFS
pointers and `MediaPlayer` crashes on playback.

## The visualizer dependency

`com.github.gauravk95:audio-visualizer-android:v0.9.1` (JitPack) **can never build**: the library's
own `build.gradle` depends on JCenter/Bintray, closed since 2021. Tested and confirmed. It was fully
removed — import, field, initialisation, nine call sites in `MainActivity.kt`, and the XML tag in
`layout_tab_data_radio.xml`.

Replaced with **`io.github.gautamchibde:audiovisualizer:2.2.7`** (Maven Central, alive and maintained).

- The class is `com.chibde.visualizer.LineVisualizer`. The library wiki gives the stale path
  `com.chibde.audiovisulaizer.visualizer.LineVisualizer` — that is wrong, the real package is shorter.
- API: `.setPlayer(audioSessionId)` (not `.setAudioSessionId()`), `.visibility = View.VISIBLE/GONE`
  (not `.show()/.hide()`), `.setColor(...)` unchanged.
- It requires `android.permission.RECORD_AUDIO`, which was **not in AndroidManifest.xml at all** and
  had to be added, and `minSdk` ≥ 19.
- The variable is `lineVisualizer`, not `waveVisualizer`.

## Tooling in `dev-tools/`

| Script | Purpose |
| --- | --- |
| `gen_map.py` | Regenerates `docs/map.md` from the tree (`/map`). |
| `hooks/check_pitfalls.py` | PostToolUse hook: project traps on newly written lines. |
| `hooks/map_hint.py` | PreToolUse hook: nudges toward the map on a whole-file read of a big file. |
| `hooks/session_size.py` | Stop hook: session request/context counter. |
| `refactor_snapshot.sh` | Bytecode/member/resource snapshot and diff. |
| `controller_boundary_measure.py` | Measures a screen's real boundary before extraction. |
| `controller_extract_check.py` | Verifies an extraction line by line, both directions. |
| `comment_blocks.py` | Lists, measures and rewrites Kotlin comment blocks. |
| `ble_key_sim.py` | Simulates ESP32 BLE traffic without hardware. |
| `smoke-checklist.md` | The on-device pass the user runs. |

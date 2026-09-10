# Hardware, periphery and game mechanics

The wire protocol is in [ble.md](ble.md); the authority on commands and GPIO is
`PipBoy_BLE_Protocol_v0.2.md`. Build status and the full BOM table are in `PipBoy_Roadmap.md`.

## BYOD: the player's own phone is the core

Not a dedicated donor device. It slides in and out of the case freely. Consequences:

- GPS and microphone come from the phone; no separate modules.
- The mount is universal and adjustable (clamps, inserts), not fitted to one model. The original
  Phone Support Bar / Display Case from the STL are sized for ~2018 phones (Display Case
  ~110×106×48 mm) and are too small for current ones; they need rework.
- Pairing is over BLE — each Pip-Boy has its own BLE name and the player connects their own phone.
- A phone charging port in the case is doubtful and probably unnecessary under BYOD.
- Development needs a separate physical Android phone: the emulator has no BLE.

## ESP32-S3-DevKitC-1 (WROOM-1 module)

A full-size board with complete pinout, chosen deliberately over the forked project's legacy board
(the folder name `ESP32_S3_Mini_PythonFiles` is inherited and says nothing about the board). The
reason for the choice and the final GPIO map are in `PipBoy_BLE_Protocol_v0.2.md` section 4.0.

Three roles in one MCU:

1. BLE bridge to the phone.
2. Wi-Fi scanner for the Geiger counter.
3. USB Host for reading the holotape — a bare flash drive on four magnetic pogo contacts, not an
   active device; the reed switch and neodymium magnet from the old design are gone.

Risk: Wi-Fi scanning may interfere with BLE (shared radio). Plan B, not to be bought in advance: a
separate ESP8266 doing only Wi-Fi scanning, connected to the ESP32-S3 over UART.

## The Geiger counter (firmware side)

Instantaneous reading only. It scans for the networks `R10/R20/R50/R100`, and the radiation level is
a **fixed dose per beacon name, not signal strength** — revised at stage 22, it used to be the other
way round (see `PipBoy_Roadmap.md`, "Счётчик радиации — пересмотр механики Гейгера"):
`R10` = 5 rad/s, `R20` = 10, `R50` = 25, `R100` = 50, with a lethal dose of 1000 rad in 20 seconds.
When several beacons are visible the strongest dose wins.

The voltmeter needle and the piezo click rate follow the current dose; no beacon means silence and
zero. No memory, no accumulation, no reset at the scanner level — that does not change. The ESP32
stays a dumb instantaneous sensor and all accumulation belongs to the app.

Indication is an analogue voltmeter (PWM + RC filter from the ESP32-S3), not a stepper — a stepper
would only be for a separate physical clock dial.

## Three radiation things that are not the same thing

- **The player's official dose sensor is a separate project and not part of the Pip-Boy.** A separate
  wearable (ESP8266 + LED) scans the same networks, accumulates dose, and is reset **only by the
  game masters** through a reset network `R0`/`RAD0` (the original document contradicts itself on the
  name; unresolved). It is the authoritative source for game rules — the Pip-Boy cannot influence or
  clear it.
- **The Geiger counter in the case** is the instantaneous sensor described above.
- **The dose counter on ITEMS/Geiger in the app** is a separate convenience feature, implemented at
  stage 22, and does not conflict with the official sensor. The app sums the dose from
  `GEIGER:<rad/s>` sent by the ESP32 once a second unconditionally, clamps to [0, 1000], shows it on
  a needle gauge (0–1000 rad), and **the player zeroes it themselves** with an on-screen button
  whenever they want. It survives an app restart (`SharedPreferences`). It is a personal tracker
  alongside the official sensor, never a replacement, and not authoritative for game rules.

## Case power

Autonomous and separate from the phone (roadmap, Phase C): LiPo (1S, 3.7 V) → TP4056 with protection
→ its own boost converter 3.7 V→5 V → the 5V/VIN input of the ESP32-S3 board (the same input the USB
uses). The power switch goes between the boost converter and the board, **not** in the charging path,
so the pack can charge while the device is off. The charging port belongs to the TP4056 module
(USB-C/micro-USB) and is exposed outside the case; the ESP32-S3's own USB-C stays internal, for
flashing only.

**The POWER button in the protocol is a logical state toggle** (`PipBoy_BLE_Protocol_v0.2.md`, 3.1),
not real power switching — the ESP32 must already be powered and advertising over BLE before POWER
toggles anything. The same is true for pairing: the case has to be switched on first.

## Costs

About **4000 ₽** per Pip-Boy for the fixed positions — excluding the printed case, spare and optional
positions, and the phone charging connector (doubtful under BYOD). It includes the autonomous case
power above. The phone is not in the BOM (BYOD). Full table in `PipBoy_Roadmap.md`.

## Open questions

- The reset network name for the player's dose sensor: `R0` (per code) vs `RAD0` (per the original
  document text). Unresolved; that sensor is a separate project regardless.
- Beacon scanning on the ESP32 stays at once per 6 seconds (a blocking `WLAN.scan()`). At the lethal
  `R100` rate of 20 seconds, that is up to a third of the time on stale readings when entering or
  leaving a zone. Deliberately deferred until real hardware exists.
- Whether a pogo-pin charging connector in the case is needed at all under BYOD.
- How many players are on site simultaneously — not fixed; it matters for the separate "game
  radiation" project's budget, not for this repository.
- **LiPo capacity and physical location** — undetermined, and dependent on the free volume in the
  actual 3D model after the ESP32-S3 board, FM module, pogo contacts and phone are placed. The likely
  candidate by space is Cuff 1 (roadmap, section 5), but it has not been measured for this.

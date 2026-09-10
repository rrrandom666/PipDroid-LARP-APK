# Phone ↔ ESP32-S3 link

`PipBoy_BLE_Protocol_v0.2.md` is the authority on commands and GPIO and is kept current; this file is
the overview. Live token lists extracted from the code are in `docs/map.md`.

## Option A: extend what already exists

Not a custom GATT service. The upstream PipDroid author had already built on the **Nordic UART
Service**, and that is what the new commands extend.

- Service UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`, TX `...0003` (ESP32→phone, notify),
  RX `...0002` (phone→ESP32, write).
- Plain text messages. Parameterised ones use `KEY:VALUE`; the rest are a single keyword, as the
  existing `STATS`/`ITEMS`/`DATA` already are.

## What already works

- Three buttons (GPIO 16/17/18, active-low, pull-up) on the ESP32-S3, in
  `ESP32_S3_Mini_PythonFiles/main.py`. The class `BLESimplePeripheral` is defined in that file;
  `ble_uart_peripheral.py` is **not** used — it is a dead reference file.
- The firmware polls every 350 ms and sends a line **on every cycle while a button is held**, not on
  state change.
- On the phone: `MainActivity.kt`, `menuChangeBLE()` — `when(menu){"STATS"->...}`. The
  `BluetoothGattCallback.onCharacteristicChanged` path already works.
- Pairing is a pick from the devices found by a scan; manual entry of MAC and UUID was removed along
  with the Settings redesign. Only the MAC is stored (`bluetoothMAC`); the service and characteristic
  UUIDs come from the NUS defaults. The keys `bluetoothSUUID`/`bluetoothRUUID`/`bluetoothWUUID` are
  read by `PipBoyBleService` itself and nothing writes them.
- The phone→ESP32 direction (`writeCharacteristic()` / `BluetoothController.send()`) carries exactly
  one command: `RADIOFREQ:<val>` on a radio frequency change, which `on_rx` in the firmware parses.
  The app **no longer sends section names**. This path has never been exercised against a live
  ESP32 — there is no hardware yet.

## New commands

ESP32 → phone: `POWER`, `ENCBTN`, `ENC:±N`, `GEIGER:<rad/s>`, `HOLOTAPE:1/0`, `RADIOFREQ:<val>`.
Phone → ESP32: `RADIOPWR:1/0`, `RADIOFREQ:<val>`, `HOLOTAPE:LIST`, `HOLOTAPE:READ:<n>`.
Holotape chunking (`HOLOTAPE:DATA:...`) is a draft, waiting on a throughput test. This list goes
stale — the protocol document is the source of truth.

## Firmware notes when adding commands

- Assign free GPIOs for Power and the encoder. Avoid 0/3/45/46 (strapping pins) and 19/20 (USB D+/D−,
  if the ESP32-S3 is to do native USB Host for the holotape).
- New functionality must send **on state change**, not on the 350 ms poll — otherwise `ENC:+1`
  duplicates. Leave the existing STATS/ITEMS/DATA behaviour alone; it works.
- `on_rx` already parses `KEY:VALUE` and drives the periphery (`RADIOPWR` → `radio_power()`,
  `RADIOFREQ` → `radio_set_frequency()`). There is no branch for section names and none is needed —
  the app does not send them.

## The trap

These tokens are a text contract across two languages. Renaming one on the app side compiles cleanly
and breaks the device, and nothing in the build will tell you. `docs/map.md` lists what each side
currently sends, dispatches and parses — compare both sides after any rename.

Without hardware, `dev-tools/ble_key_sim.py` simulates the traffic.

---
description: Regenerate docs/map.md from the current tree
allowed-tools: Bash(python3 dev-tools/gen_map.py:*), Bash(git diff:*)
---

!`python3 dev-tools/gen_map.py`

The map is derived entirely from the tree — never edit `docs/map.md` by hand.

Report in one or two lines what moved: files that gained or lost members, ranges that
shifted a lot, new layouts, new SharedPreferences keys, changed BLE tokens. If a BLE token
changed on the app side, check it still matches `ESP32_S3_Mini_PythonFiles/main.py` —
that mismatch compiles fine and breaks the device.

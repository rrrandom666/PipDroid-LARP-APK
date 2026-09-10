---
description: Run the mechanical checks the compiler does not do (build warnings, pitfalls, map freshness)
allowed-tools: Bash, Read, Grep, Task
---

Run the checks that a program can do faster than you can, and report only what failed.

1. **Build and warnings** — delegate to the `builder` agent. The build must be `BUILD SUCCESSFUL`
   with zero `w:` lines; a non-zero warning count is a regression.
2. **Pitfalls across the changed files** — for every file in `git diff --name-only` under `app/src`,
   feed it through the same checks the edit hook runs:
   `python3 dev-tools/hooks/check_pitfalls.py` expects a JSON payload on stdin, so for a whole-file
   sweep run the equivalent greps instead: `isEnabled = false`, `setBackground(Color|Resource)`
   without a preceding `backgroundTintList = null`, `overScrollMode="never"`, `backgroundTint="@null"`,
   bare SharedPreferences string keys, and unused imports.
3. **Map freshness** — `python3 dev-tools/gen_map.py --check`. If it says stale, run `/map`.
4. **Contract greps** the compiler cannot see: `getIdentifier` resource names, SharedPreferences
   keys, BLE command tokens. Confirm nothing renamed on one side only.

Report a short list of failures with file and line. If everything passes, say so in one line —
do not narrate the checks that passed.

This does not replace the on-device pass. Hand `dev-tools/smoke-checklist.md` to the user for that.

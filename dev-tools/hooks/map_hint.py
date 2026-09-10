#!/usr/bin/env python3
"""PreToolUse hook: a whole-file read of a monster file is almost never needed.

MainActivity.kt is 3300+ lines and PipBoy_Roadmap.md is 4400+; reading either one
whole costs more than the edit that follows it. docs/map.md carries line ranges for
both, so the read can be a narrow slice instead.

Adds a note, never blocks — sometimes the whole file really is the point.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MAP = os.path.join(ROOT, "docs", "map.md")
BIG_LINES = 700


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0
    tool_input = payload.get("tool_input") or {}
    path = tool_input.get("file_path")
    if not path or not os.path.isfile(path):
        return 0
    if tool_input.get("offset") or tool_input.get("limit"):
        return 0
    relative = os.path.relpath(path, ROOT)
    if relative.startswith("..") or os.path.abspath(path) == MAP:
        return 0
    if not path.endswith((".kt", ".xml", ".md", ".py")):
        return 0

    try:
        with open(path, "rb") as handle:
            total = sum(1 for _ in handle)
    except OSError:
        return 0
    if total < BIG_LINES:
        return 0

    note = ("%s is %d lines. docs/map.md lists its sections with line ranges — read the range "
            "you need with offset/limit instead of the whole file." % (relative, total))
    try:
        if os.path.getmtime(path) > os.path.getmtime(MAP):
            note += " (That file changed after the map was generated; run /map if the ranges look off.)"
    except OSError:
        note += " (docs/map.md is missing — run /map to generate it.)"

    print(json.dumps({
        "hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": note},
        "suppressOutput": True,
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())

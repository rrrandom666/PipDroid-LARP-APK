#!/usr/bin/env python3
"""PostToolUse hook: catch this project's documented traps mechanically.

Every check here corresponds to a bug that actually happened and cost a device
test to find (details in docs/pitfalls.md). A script finds them in milliseconds;
the model would need several reads and a round trip.

Warns, never blocks. Line checks look only at text the edit just added, so
pre-existing occurrences elsewhere in the file stay quiet; whole-file checks are
limited to the two that are clean across the tree today, where any hit is a regression.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# The eight SharedPreferences keys that are still bare literals, all pre-existing.
KNOWN_BARE_KEYS = {
    "appLanguage", "ShowTutorial", "TrueFullscreen", "AmbientSoundEnabled",
    "width", "height", "leftMargin", "topMargin",
}
ZERO_HEIGHT_STYLES = ("SettingsButtonStyle", "PipWizardButtonStyle", "CNDEFFRADButtonStyle")


def added_lines(tool_input):
    """Stripped text of every line this call wrote, across Write/Edit/MultiEdit."""
    chunks = []
    if isinstance(tool_input.get("content"), str):
        chunks.append(tool_input["content"])
    if isinstance(tool_input.get("new_string"), str):
        chunks.append(tool_input["new_string"])
    for edit in tool_input.get("edits") or []:
        if isinstance(edit, dict) and isinstance(edit.get("new_string"), str):
            chunks.append(edit["new_string"])
    out = set()
    for chunk in chunks:
        for line in chunk.splitlines():
            if line.strip():
                out.add(line.strip())
    return out


def check_kotlin(lines, added, findings):
    for number, raw in enumerate(lines, start=1):
        line = raw.strip()
        if line not in added:
            continue
        if re.search(r"\.isEnabled\s*=\s*false", line):
            findings.append(
                "L%d: `isEnabled = false` swallows the tap, so a shaded control goes silent. "
                "Project convention is `alpha` plus a guard flag checked inside the handler, "
                "which still reaches `playErrorAudio()`." % number
            )
        if re.search(r"\.setBackground(Color|Resource)\s*\(", line):
            window = "\n".join(lines[max(0, number - 4):number])
            if "backgroundTintList = null" not in window:
                findings.append(
                    "L%d: background set programmatically without clearing the tint first. "
                    "AppCompat blends `backgroundTint` over it, so the view renders in the theme "
                    "accent instead of the colour passed here — set `backgroundTintList = null` before it."
                    % number
                )
        match = re.search(r"\.(?:get|put)(?:String|Int|Boolean|Long|Float|StringSet)\(\s*\"([^\"]+)\"", line)
        if match and match.group(1) not in KNOWN_BARE_KEYS:
            findings.append(
                "L%d: SharedPreferences key `\"%s\"` as a bare literal. A typo silently wipes saved "
                "player state — declare it as a `*_SPKey` constant." % (number, match.group(1))
            )
    run = 0
    for number, raw in enumerate(lines + [""], start=1):
        if raw.strip().startswith("//") and raw.strip() in added:
            run += 1
            continue
        if run >= 3:
            findings.append(
                "L%d: comment block of %d lines. Project rule is one sentence announcing a "
                "functional block; narrative belongs in PipBoy_Roadmap.md." % (number - run, run)
            )
        run = 0


def check_imports(lines, findings):
    body = "\n".join(line for line in lines if not line.startswith("import "))
    for number, line in enumerate(lines, start=1):
        match = re.match(r"import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", line.strip())
        if not match:
            continue
        name = match.group(2) or match.group(1).split(".")[-1]
        if name == "*":
            continue
        if not re.search(r"\b%s\b" % re.escape(name), body):
            findings.append(
                "L%d: unused import `%s`. Neither the compiler nor a grep over declarations reports "
                "these — the tree is clean today, so this one is new." % (number, match.group(1))
            )


def check_layout(text, added, findings):
    lines = text.splitlines()
    for number, raw in enumerate(lines, start=1):
        line = raw.strip()
        if line not in added:
            continue
        if 'android:overScrollMode="never"' in line:
            findings.append(
                "L%d: `overScrollMode=\"never\"` removes the scrollbar from a scrolling list, not just "
                "the edge glow. Drop it unless this view is meant to have no scrollbar." % number
            )
        if 'android:backgroundTint="@null"' in line:
            findings.append(
                "L%d: `backgroundTint=\"@null\"` has no effect on this AGP/AAPT2. Clear the tint from "
                "code with `view.backgroundTintList = null`." % number
            )
    for tag in re.findall(r"<[A-Za-z][^>]*?/?>", text, re.S):
        if not any(style in tag for style in ZERO_HEIGHT_STYLES):
            continue
        if not any(line.strip() in added for line in tag.splitlines() if line.strip()):
            continue
        if "android:layout_height" in tag:
            continue
        if "layout_constraintTop_to" in tag and "layout_constraintBottom_to" in tag:
            continue
        name = re.search(r'android:id="@\+id/(\w+)"', tag)
        findings.append(
            "%s: uses a style that sets `layout_height=\"0dip\"` but has neither an explicit "
            "`android:layout_height` nor a top+bottom constraint pair. The view collapses to "
            "invisible and its taps fall through to whatever is underneath."
            % (("`%s`" % name.group(1)) if name else "a styled view")
        )


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0
    tool_input = payload.get("tool_input") or {}
    path = tool_input.get("file_path") or (payload.get("tool_response") or {}).get("filePath")
    if not path or not os.path.isfile(path):
        return 0
    relative = os.path.relpath(path, ROOT)
    if relative.startswith("..") or not relative.startswith("app/src"):
        return 0

    added = added_lines(tool_input)
    if not added:
        return 0
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            text = handle.read()
    except OSError:
        return 0

    findings = []
    if path.endswith(".kt"):
        lines = text.splitlines()
        check_kotlin(lines, added, findings)
        check_imports(lines, findings)
    elif path.endswith(".xml") and "/res/layout" in relative:
        check_layout(text, added, findings)

    if not findings:
        return 0
    report = "Pitfall check on %s:\n%s" % (relative, "\n".join("- " + item for item in findings))
    print(json.dumps({
        "systemMessage": "Pitfall check: %d note(s) in %s" % (len(findings), os.path.basename(path)),
        "hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": report},
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())

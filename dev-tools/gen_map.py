#!/usr/bin/env python3
"""Generate docs/map.md — the code map Claude reads before touching a file.

    python3 dev-tools/gen_map.py            # write docs/map.md
    python3 dev-tools/gen_map.py --check    # exit 1 if the map is out of date

The point: a blind grep over MainActivity.kt (3300+ lines) or PipBoy_Roadmap.md
(4400+ lines) drags the whole file into context. The map gives line ranges instead,
so a read can be a narrow offset/limit slice.

Everything here is derived from the tree. Never edit docs/map.md by hand.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "map.md")

KOTLIN_ROOTS = ["app/src/main/java", "app/src/test/java"]
LAYOUT_DIR = "app/src/main/res/layout"
DOC_FILES = ["PipBoy_Roadmap.md", "PipBoy_BLE_Protocol_v0.2.md"]

MODIFIERS = {
    "private", "internal", "public", "protected", "open", "override", "abstract",
    "final", "inline", "suspend", "lateinit", "const", "data", "enum", "sealed",
    "inner", "annotation", "external", "operator", "infix", "tailrec", "companion",
    "expect", "actual", "crossinline", "noinline", "reified",
}
DECL_KEYWORDS = ("fun", "val", "var", "class", "object", "interface")


def rel(path):
    return os.path.relpath(path, ROOT)


def read(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def walk(subdir, suffix):
    """Files with the given suffix under subdir, sorted."""
    out = []
    for dirpath, _, names in os.walk(os.path.join(ROOT, subdir)):
        for name in sorted(names):
            if name.endswith(suffix):
                out.append(os.path.join(dirpath, name))
    return sorted(out)


def declaration(line):
    """(kind, name) for a Kotlin declaration line, or None."""
    tokens = line.strip().split()
    i = 0
    while i < len(tokens) and (tokens[i] in MODIFIERS or tokens[i].startswith("@")):
        i += 1
    if i >= len(tokens) or tokens[i] not in DECL_KEYWORDS:
        return None
    kind = tokens[i]
    if i + 1 >= len(tokens):
        return None
    name = tokens[i + 1]
    if name.startswith("<"):  # generic parameter list before the name
        if ">" in name:
            name = name.split(">", 1)[1] or (tokens[i + 2] if i + 2 < len(tokens) else "")
        elif i + 2 < len(tokens):
            name = tokens[i + 2]
    name = re.split(r"[(<:={}]", name)[0].strip()
    if not name or not re.match(r"^[A-Za-z_]", name):
        if kind == "object" and tokens[i - 1] == "companion":
            return ("object", "companion object")
        return None
    if i > 0 and tokens[i - 1] == "companion":
        name = "companion object"
    return (kind, name)


def kotlin_members(path):
    """Top-level and class-level declarations as (name, kind, start, end)."""
    lines = read(path).splitlines()
    found = []
    for number, line in enumerate(lines, start=1):
        if not line.strip() or line.lstrip().startswith(("//", "*", "/*")):
            continue
        indent = len(line) - len(line.lstrip())
        if indent > 4:
            continue
        parsed = declaration(line)
        if parsed:
            found.append([parsed[1], parsed[0], number, None])
    for index, item in enumerate(found):
        item[3] = (found[index + 1][2] - 1) if index + 1 < len(found) else len(lines)
    return found, len(lines)


def wrap(entries, width=96, indent=""):
    """Comma-separated entries wrapped into lines."""
    out, current = [], indent
    for entry in entries:
        piece = entry if current.strip() == "" else ", " + entry
        if len(current) + len(piece) > width and current.strip():
            out.append(current)
            current = indent + entry
        else:
            current += piece
    if current.strip():
        out.append(current)
    return out


def git_head():
    try:
        return subprocess.check_output(
            ["git", "-C", ROOT, "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
    except Exception:
        return "unknown"


def doc_outline(path):
    """Markdown headings (## and ###) with the line range each one covers."""
    lines = read(path).splitlines()
    heads = []
    for number, line in enumerate(lines, start=1):
        match = re.match(r"^(#{2,3})\s+(.*)$", line)
        if match:
            heads.append([len(match.group(1)), match.group(2).strip(), number, None])
    for index, head in enumerate(heads):
        head[3] = (heads[index + 1][2] - 1) if index + 1 < len(heads) else len(lines)
    return heads, len(lines)


def prefs_keys():
    """SharedPreferences keys: *_SPKey constants and the bare string literals."""
    constants, literals = [], set()
    known_bare = re.compile(
        r'\.(?:get|put)(?:String|Int|Boolean|Long|Float|StringSet)\(\s*"([^"]+)"'
    )
    for path in walk(KOTLIN_ROOTS[0], ".kt"):
        text = read(path)
        for match in re.finditer(r'val\s+(\w+_SPKey)\s*=\s*"([^"]+)"', text):
            constants.append((match.group(1), match.group(2), rel(path)))
        for match in known_bare.finditer(text):
            literals.add(match.group(1))
    return sorted(constants), sorted(literals)


def ble_commands():
    """Command tokens on each side of the wire, kept narrow so the list stays true.

    A blanket scan of the app pulls in every upper-case literal (SPECIAL stat names,
    `@Suppress("DEPRECATION")`), so the app side is read only where it actually talks
    to the firmware: `send(...)` calls, and the dispatch that consumes what arrives.
    """
    upper = r'[A-Z][A-Z0-9]{2,}(?::[^"\']*)?'
    sent, dispatched, firmware = set(), set(), set()
    for path in walk(KOTLIN_ROOTS[0], ".kt"):
        text = read(path)
        for line in text.splitlines():
            if re.search(r"\bsend\s*\(", line):
                for match in re.finditer(r'"(%s)' % upper, line):
                    sent.add(match.group(1).split(":")[0])
        members, _ = kotlin_members(path)
        lines = text.splitlines()
        for name, _kind, start, end in members:
            if name in ("menuChangeBLE", "menuOptionClickedBLE", "onCommand"):
                for match in re.finditer(r'"(%s)"' % upper, "\n".join(lines[start - 1:end])):
                    dispatched.add(match.group(1).split(":")[0])
    for path in walk("ESP32_S3_Mini_PythonFiles", ".py"):
        text = read(path)
        for match in re.finditer(r'["\'](%s)["\']' % upper, text):
            firmware.add(match.group(1).split(":")[0])
    return sorted(sent), sorted(dispatched), sorted(firmware)


def layout_users():
    """Which Kotlin files and layouts reference each layout."""
    users = {}
    kotlin = [(rel(p), read(p)) for p in walk(KOTLIN_ROOTS[0], ".kt")]
    layouts = [(rel(p), read(p)) for p in walk(LAYOUT_DIR, ".xml")]
    for path in walk(LAYOUT_DIR, ".xml"):
        name = os.path.basename(path)[:-4]
        binding = "".join(part.capitalize() for part in name.split("_")) + "Binding"
        hits = []
        for kt_path, text in kotlin:
            if re.search(r"\bR\.layout\.%s\b" % re.escape(name), text) or binding in text:
                hits.append(os.path.basename(kt_path)[:-3])
        for xml_path, text in layouts:
            if "@layout/%s\"" % name in text:
                hits.append(os.path.basename(xml_path)[:-4] + ".xml")
        users[name] = sorted(set(hits))
    return users


def build():
    lines = []
    add = lines.append

    add("# Project map")
    add("")
    add("Generated by `python3 dev-tools/gen_map.py` at commit `%s`. Do not edit by hand." % git_head())
    add("")
    add("Read this before opening a big file, then read only the range you need")
    add("(`Read` with `offset`/`limit`). Line ranges shift as code changes — regenerate")
    add("with `/map` after a batch of edits.")
    add("")

    # --- Kotlin ---
    add("## Kotlin")
    add("")
    add("`name:startLine-endLine`, in file order. Ranges cover the declaration up to the next one.")
    add("")
    for subdir in KOTLIN_ROOTS:
        for path in walk(subdir, ".kt"):
            members, total = kotlin_members(path)
            add("### %s — %d lines" % (rel(path), total))
            if not members:
                add("")
                continue
            entries = [
                "%s:%d" % (name, start) if start == end else "%s:%d-%d" % (name, start, end)
                for name, _, start, end in members
            ]
            add("")
            for chunk in wrap(entries):
                add(chunk)
            add("")

    # --- Layouts ---
    add("## Layouts")
    add("")
    add("`file (lines) — inflated by`. Ids are unique tokens; grep for a specific `@+id/` instead of reading a layout whole.")
    add("")
    users = layout_users()
    for path in walk(LAYOUT_DIR, ".xml"):
        name = os.path.basename(path)[:-4]
        total = len(read(path).splitlines())
        who = ", ".join(users.get(name, [])) or "—"
        add("- `%s.xml` (%d) — %s" % (name, total, who))
    add("")

    # --- Other resources ---
    add("## Other resources")
    add("")
    for values in ("values", "values-ru"):
        strings_path = os.path.join(ROOT, "app/src/main/res", values, "strings.xml")
        if os.path.exists(strings_path):
            text = read(strings_path)
            count = len(re.findall(r"<string\s+name=", text))
            arrays = len(re.findall(r"<string-array\s+name=", text))
            add("- `res/%s/strings.xml` — %d strings, %d arrays, %d lines"
                % (values, count, arrays, len(text.splitlines())))
    for folder in ("drawable", "raw", "font", "anim", "menu", "values"):
        full = os.path.join(ROOT, "app/src/main/res", folder)
        if os.path.isdir(full):
            names = sorted(os.listdir(full))
            listing = ", ".join(names[:8]) + (", …" if len(names) > 8 else "")
            add("- `res/%s/` — %d file%s: %s" % (folder, len(names), "" if len(names) == 1 else "s", listing))
    assets = os.path.join(ROOT, "app/src/main/assets")
    if os.path.isdir(assets):
        add("- `assets/` — %s" % ", ".join(sorted(os.listdir(assets))))
    add("")
    add("Perk names and descriptions are **not** in `strings.xml`: `Data.kt` holds the list and")
    add("`getIdentifier(\"perk_<id>_name\")` resolves the rest at runtime, invisible to static analysis.")
    add("")

    # --- SharedPreferences ---
    add("## SharedPreferences keys")
    add("")
    constants, literals = prefs_keys()
    add("Declared as constants:")
    add("")
    for name, value, where in constants:
        add("- `%s` = `\"%s\"` — %s" % (name, value, where))
    add("")
    add("Still bare string literals (a typo here silently wipes a player's saved state):")
    add("")
    for chunk in wrap(["`%s`" % key for key in literals]):
        add(chunk)
    add("")

    # --- BLE ---
    add("## BLE command tokens")
    add("")
    sent, dispatched, firmware_tokens = ble_commands()
    add("Text contract with the firmware; the authority is `PipBoy_BLE_Protocol_v0.2.md`.")
    add("Renaming a token here without touching `ESP32_S3_Mini_PythonFiles/main.py` compiles fine and breaks the device.")
    add("")
    add("- app sends: %s" % (", ".join("`%s`" % t for t in sent) or "—"))
    add("- app dispatches on receive: %s" % (", ".join("`%s`" % t for t in dispatched) or "—"))
    add("- firmware side: %s" % (", ".join("`%s`" % t for t in firmware_tokens) or "—"))
    add("")

    # --- Documents ---
    add("## Documents")
    add("")
    add("Heading ranges — read a section, never the whole file.")
    add("")
    for name in DOC_FILES:
        path = os.path.join(ROOT, name)
        if not os.path.exists(path):
            continue
        heads, total = doc_outline(path)
        add("### %s — %d lines" % (name, total))
        add("")
        for level, title, start, end in heads:
            prefix = "- " if level == 2 else "  - "
            add("%s%s — %d-%d" % (prefix, title, start, end))
        add("")

    return "\n".join(lines) + "\n"


def main():
    content = build()
    if "--check" in sys.argv:
        current = read(OUT) if os.path.exists(OUT) else ""
        strip = lambda text: re.sub(r"at commit `\w+`", "", text)
        stale = strip(current) != strip(content)
        print("stale" if stale else "up to date")
        return 1 if stale else 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(content)
    print("wrote %s (%d lines)" % (rel(OUT), len(content.splitlines())))
    return 0


if __name__ == "__main__":
    sys.exit(main())

# Session handoff

Written by `/carry`, overwritten every time. Scratch only — anything worth keeping longer belongs in
`PipBoy_Roadmap.md` or `CLAUDE.md`.

## Done

Context-management layer built from scratch (habr.com/ru/articles/1077658 recommendations):
`CLAUDE.md` rewritten to 98 English lines with the detail split into `docs/{architecture,pitfalls,
refactoring,localization,environment,hardware,ble,legal}.md`; `dev-tools/gen_map.py` generating
`docs/map.md`; three hooks in `dev-tools/hooks/` wired in `.claude/settings.json`; agents
`builder`, `device-logs`, `snapshot-diff`; commands `/map`, `/checks`, `/carry`.

## State

Branch `master`. No app code touched — only documentation, `dev-tools/` and `.claude/`. The hooks
were pipe-tested individually; `settings.json` validates against the schema. The Gradle build was
not re-run, because nothing under `app/` changed.

## Next

Use it for a session and see what the pitfall hook says on real edits. If it turns out noisy on a
check, narrow that check rather than switching the hook off.

## Open

- `docs/localization.md` states plainly that the CSV↔resources comparison has **no committed
  script** — the old CLAUDE.md claimed one existed. Writing it is unclaimed work.
- New hooks only take effect in sessions started after `.claude/settings.json` appeared, or after
  opening `/hooks` once.

## Don't redo

- Do not re-add the whole-file pitfall checks for `overScrollMode`, the 0dip styles or the tint
  reset: measured against the tree, they fire on 11/35/15 pre-existing places. They are deliberately
  scoped to newly written lines. The two whole-file checks (unused imports, bare prefs keys) are
  clean across the tree, which is why they run whole-file.

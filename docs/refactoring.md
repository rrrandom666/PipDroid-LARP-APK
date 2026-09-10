# Refactoring: tools and rules

A step that is not supposed to change behaviour has to be proven, because the compiler does not
check it. Three tools do, and each covers a different case.

## Snapshot comparison — for steps that change nothing

`dev-tools/refactor_snapshot.sh <label>` builds the app and records: bytecode (`javap -p -c` over
every class), a normalised copy of it (constants masked — removing a resource renumbers ids, and
those are inlined into bytecode), the declared members of each class, resource files and names, and
dynamic names. `... diff <a> <b>` compares two snapshots.

Comment cleanup, dead-code removal and unused-import removal **must** produce an identical
snapshot. For deletions, `members.txt` is the precise view: removing code shifts every offset
through the rest of the method, so the raw bytecode diff looks enormous while the member list
changes by exactly what was removed.

Delegate the run to the `snapshot-diff` agent — the raw dump is megabytes and does not belong in
the main thread.

## Extracting a screen into a controller — snapshots are useless there

Methods change class, so the bytecode diverges completely. Two dedicated tools instead:

**Before — `controller_boundary_measure.py --pattern '<screen words>'`.** Counts the screen's
members, its state fields, and — the number that matters — accesses to them from outside,
**excluding** `onCreate` (that block moves with the extraction, so its accesses are not part of the
surface). The decision is made on that number, not on the feeling that a chunk "looks separate":
the measurement cancelled a narrow extraction for the Clock and cancelled the voice extraction
entirely. The name pattern lies in both directions, so the member list must be read with your eyes.

**Re-measure before starting, never trust a number written down in the queue.** The voice estimate
("~190 lines") went stale precisely because a launcher that was never going to move had been
silently counted in. Measuring takes a minute; extracting takes a session.

**Size is not the criterion, and not even the main one.** Bluetooth (190 lines) was worth extracting
for decoupling — the scanner stopped going through the activity. Voice (98 lines net of the
dispatcher and the launcher) was not worth extracting for exactly the opposite reason: `voice/` is
already handed to `MapController`/`JournalController` directly, and a controller would only add a
hop. Before cutting, ask which link disappears after the extraction and which one appears.

**After — `controller_extract_check.py --range A-B --rename OLD=NEW`.** Compares the source line
ranges from `git show HEAD` against the new file in both directions, applying the mechanical
renames. The point is not empty output — it is that **every** difference is one you declared
(callback substitution, `this`→`activity`, `private`→`fun`). An undeclared difference is a line
lost or invented.

## What neither tool catches

Both compare text, not meaning.

- **A helper with a foreign consumer moves out silently.** A function named after a screen may well
  be painting something else: `setWizardButtonState()` also colours the tutorial buttons, and so
  does `equalizeButtonWidths()`. The name-based measurement assigns it to the screen, the extract
  check does not object (the line honestly moved), and only the compiler catches it — and only
  while the function is `private` in the same file. Rule: grep every helper for its call sites
  before moving it, and if even one is outside the screen being extracted, the helper stays in the
  activity and is passed in as a callback.
- **A screen scattered through `onCreate`: a single `setup()` moves its blocks.** Screen code is
  rarely one contiguous piece — STATS was five blocks interleaved with other screens'. One `setup()`
  (as Map, Clock and Journal have) inevitably reorders some of them, and the extract check sees
  nothing: the lines are all there, only the execution order changed. Splitting into a `setup*()`
  per block for literalness is worse — it doubles the controller's surface. Rule: put the single
  call where the **first** block was, so the rest move **upwards**. Then everything that existed at
  the old point still exists there, and exactly two things need checking per block: whether the code
  between them reads the moved state, and whether the moved block depends on what sits between.
  Listener registration and tinting travel freely; only executable assignments and adapter
  construction are dangerous.
- **Merging two similar operations into one "family" method quietly widens behaviour.** The
  temptation: the wizard and the mode-select screen are one family, so let `hide()` close both. But
  the merged method gains call sites where the second view really is visible, and there the
  behaviour changes (`applyPowerState()` on an incoming `POWER` would have closed a mode-select
  opened from Settings). The check sees nothing: no lines were lost, they simply run more often.
  Verify per call site, not per "same family of screens".

## The type-inference cycle

`registerForActivityResult` must stay in the activity (launchers register before `onStart`), so a
controller gets the action as a callback. If the launcher's callback then calls a controller method,
the references close into a cycle and `kotlinc` fails with `Type checking has run into a recursive
problem` **on the launcher line**, which is not where the fault is. Fix with an explicit type on one
side: `private val bluetooth: BluetoothController by lazy`.

## Comments

One sentence, announcing a functional block ("we do X"). Not a line-by-line retelling of the code,
not the history of a finding, not references to roadmap stages — all of that lives in
`PipBoy_Roadmap.md`. Comments do not affect bytecode, so editing them must produce a byte-identical
snapshot. `dev-tools/comment_blocks.py` lists, measures and rewrites comment blocks in a Kotlin file.

## Warnings are a checklist, not noise

The build holds **zero** warnings. A grep over declaration names (how the first cleanup wave hunted
dead code) does not see unused **local** variables inside live functions — `kotlinc` does, and two
genuinely dead functions were found by pulling on exactly those. And nullability warnings are not
always cosmetic: `Unnecessary safe call` next to a `!!` usually means `?.` and `!!` are swapped and
the covering `?:` applies to the wrong operand — that was a latent NPE in `onFling`.

Read the `w:` lines in `./gradlew assembleDebug`, not just `BUILD SUCCESSFUL`. A non-zero count is a
regression. The `builder` agent reports exactly this.

## The blind spot of both methods: unused imports

An import is not a declaration, so a grep over declaration names never finds it, and `kotlinc` does
not warn about it at all (it is an IDE inspection, not a compiler warning) — so the warning checklist
is empty too. Eighteen dead imports survived both cleanup waves and turned up only during the Map
extraction. The edit hook now checks this on every write; the tree is clean today, so any hit is new.
The fix is safe by construction — imports produce no bytecode, so the snapshot must stay byte-identical.

## A green test on the first run proves nothing

Break the production code on purpose, confirm the test fails, then restore the file. On the
`MenuNavigator` tests this immediately caught a blind test: the invariant "the parent's cursor was
preserved" was checked on a cursor sitting at zero, and the mutation "reset the cursor to 0" produces
the same zero — preserved and reset are indistinguishable.

Hence: **never verify "the value was preserved" on the default value.** And restore the file after a
mutation from a copy (`cp` from `/tmp`), not with `git checkout` — that also throws away every
unsaved change in the file.

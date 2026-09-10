---
description: Write the session handoff note so the next session starts from five lines instead of the whole history
allowed-tools: Bash(git status:*), Bash(git log:*), Bash(git diff:*), Read, Write
---

Current branch and working tree:

!`git status --short --branch`

Recent commits:

!`git log --oneline -5`

Write `docs/session-carry.md` (overwrite it) with exactly these five sections, one to three
lines each, in English:

1. **Done** — what actually changed in this session, by file.
2. **State** — branch, what is committed, what is still uncommitted, whether the build is green.
3. **Next** — the single next step, concrete enough to start on without re-reading anything.
4. **Open** — questions that need the user, or decisions deliberately deferred.
5. **Don't redo** — approaches already tried and rejected this session, so the next one does not repeat them.

Rules:

- Facts only, from this session. Never guess at state you did not verify.
- Name files with paths, and line ranges where they help.
- No narrative, no restating the task, no summary of the conversation.
- If a device test is pending, say so — the user runs those, and the result is not yours to assume.

After writing it, tell the user in one line that the handoff is saved and they can `/clear`.
Anything worth keeping beyond the next session belongs in `PipBoy_Roadmap.md` or `CLAUDE.md`,
not here — this file is scratch and gets overwritten every time.

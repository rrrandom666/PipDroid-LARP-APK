#!/usr/bin/env python3
"""Stop hook: report how big this session has grown.

Cost per request scales with the context carried into it, so a long session pays
for its own history again on every turn. The counter makes that visible while
there is still a clean place to stop: run `/carry`, then `/clear`, and start the
next task in a fresh session.

Thresholds follow the article this workflow came from: warn at 120 requests or
150k context, hard stop at 150 requests or 200k.
"""
import json
import os
import sys

WARN_REQUESTS, HARD_REQUESTS = 120, 150
WARN_CONTEXT, HARD_CONTEXT = 150_000, 200_000
MAX_TRANSCRIPT_BYTES = 400 * 1024 * 1024


def measure(path):
    """(request count, context tokens of the latest request)."""
    requests, context = 0, 0
    last = None
    with open(path, "rb") as handle:
        for line in handle:
            if b'"cache_read_input_tokens"' in line:
                requests += 1
                last = line
    if last:
        try:
            usage = json.loads(last.decode("utf-8", "replace"))["message"]["usage"]
            context = (
                usage.get("input_tokens", 0)
                + usage.get("cache_read_input_tokens", 0)
                + usage.get("cache_creation_input_tokens", 0)
            )
        except Exception:
            context = 0
    return requests, context


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0
    path = payload.get("transcript_path")
    if not path or not os.path.isfile(path):
        return 0
    try:
        if os.path.getsize(path) > MAX_TRANSCRIPT_BYTES:
            return 0
        requests, context = measure(path)
    except OSError:
        return 0

    over_hard = requests >= HARD_REQUESTS or context >= HARD_CONTEXT
    over_warn = requests >= WARN_REQUESTS or context >= WARN_CONTEXT
    if not over_warn:
        return 0
    # Past the warning line, nag once every ten requests; past the hard line, every turn.
    if not over_hard and requests % 10 != 0:
        return 0

    state = "over the limit" if over_hard else "getting long"
    print(json.dumps({
        "systemMessage": "Session %s: %d requests, ~%dk context. Run /carry, then /clear."
                         % (state, requests, round(context / 1000)),
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())

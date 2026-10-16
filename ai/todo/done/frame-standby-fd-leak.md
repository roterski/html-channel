# Frame: standby leaks file handles

Observed 2026-10-16 in an MCP process (pid 95444) that was standing by for port 8787.

## Situation

Every `serve` and `mcp` process that doesn't own the port retries the bind every second (`run-standby!`, `server.clj:139`).
`try-start` (`server.clj:136-137`) calls `http/run-server` and swallows any exception, returning `nil`.

## Complication

A failed bind leaks resources: http-kit opens a selector and a server socket before binding, and nothing closes them when the bind throws.
The process then held about 2,560 `TCP (CLOSED)` sockets, about 2,560 kqueues and about 5,100 pipes, roughly 10,240 descriptors in total, which is the JVM's cap on macOS.
After that, every file operation fails.
`ask_human` returned `Too many open files`.
That works out to about 4 descriptors per retry, so the cap is reached after roughly 45 minutes of standby.
The browser side kept working, because the process that owned the port was unaffected.

## Question

How does standby retry the bind without leaking per attempt?

## Constraints

- Takeover latency stays at about 1 s.
- Any number of standby processes may run at once (one per agent chat).

## Non-goals

- Changing the takeover model itself.

## Gaps

- `test/takeover.py` runs for seconds, so it never shows growth in descriptors.
  A test should assert a flat descriptor count across many failed retries.

## Workaround

Reconnect html-channel through `/mcp`.
This frees the descriptors, and the leak starts again from zero.

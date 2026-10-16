# Spike

## Question
Can every project's pages and decisions survive a takeover with no client action?
The setup:
- several processes, one per agent session, each with its own project root, share port 8787;
- one of them owns the port and the others are standbys that retry binding;
- registered roots live in a registry file outside every server process.

When the owner dies, does a standby take over and serve **every** registered root?
That includes the roots of processes that are no longer alive.
Decisions must still land in the right root's `decisions.jsonl`.

## Prediction
- One standby binds within 1.5 s of the owner's death, and exactly one: two standbys never both own the port.
- After the takeover, `GET /<mount>/page.html` returns 200 for every registered mount, including the dead owner's.
- `POST /<mount>/_decisions` appends to that mount's own `decisions.jsonl` and to no other.

Falsified if any of these happen:
- a 404 or connection error persists more than 2 s after the kill;
- two processes report owning the port;
- a decision lands in the wrong file;
- a mount registered only by the dead owner is not served.

## Method
Three bb processes, A, B and C, each stand for one MCP process with its own project root. The port is 8797, and bb is v1.12.217 on macOS.

Each node does three things:
- it appends `{mount, root}` to a shared `roots.jsonl` on startup;
- it loops on `http-kit/run-server` and retries every 1 s on an exception;
- once it owns the port, it re-reads the registry **on every request**.

```clojure
(spit registry (str (json/generate-string {:mount mount :root root}) "\n") :append true)
(defn roots [] (into {} (for [l (fs/read-all-lines registry) :let [e (json/parse-string l true)]] [(:mount e) (:root e)])))
;; handler: /<mount>/<rest> -> file under (get (roots) mount); POST /<mount>/_decisions -> <root>/decisions.jsonl
(loop []
  (if (try (http/run-server handler {:ip "127.0.0.1" :port port}) (catch Exception _ nil))
    @(promise)
    (do (Thread/sleep 1000) (recur))))
```

Scenario (`run.sh`):
1. Start A and wait 2 s, then start B and C and wait 2.5 s.
2. `kill A`.
3. Poll `GET /<mount>/page.html` for all 3 mounts every 50 ms until all return 200, with a 5 s cap.
4. POST one decision per mount, then grep the log for `OWNS`.

Runs: 3 with SIGTERM and 1 with SIGKILL.

## Observed
| Run | Kill | All 3 mounts 200 after | New owner | Owners in log |
|---|---|---|---|---|
| 1 | TERM | 515 ms | B | A, B |
| 2 | TERM | 606 ms | C | A, C |
| 3 | TERM | 522 ms | C | A, C |
| 4 | KILL | 533 ms | C | A, C |

- The dead owner's mount `projA-…` kept serving its page after takeover, in every run.
- Each POST landed only in its own project's `decisions.jsonl`, one line per file.
- In every run, exactly one standby logged `OWNS port`.
  The other standby kept retrying without binding.
- Takeover time is bounded by the 1 s retry interval: 515–606 ms here.

## Verdict
**Answered: yes. The prediction is confirmed.** (confirmed, 4/4 runs)
- With roots kept in a file outside the server, any standby can take over within the retry interval.
- It serves every registered root, including roots whose process has died.
- Decisions stay routed by mount.
- The OS port bind is enough as the ownership lock. No lockfile or election was needed.

## Not shown
- **Browser side.** The squint runtime was not tested reconnecting to the new owner or deriving its mount from the page URL.
  Prototype scenario D covered reconnect for a single root only.
- **Registry hygiene.** Entries are never removed, and duplicate registrations of the same root were not exercised.
  Concurrent appends from several processes were not stress-tested.
- **All processes gone.** When no standby remains, pages go offline. That is out of scope for this question.
- **Real clients.** Nothing ran under Claude Code or ECA.
  ECA probably runs one MCP process per server rather than per chat, so there each process would be a standby for several sessions.
- **Linux and Windows** bind semantics, such as `SO_REUSEADDR` and TIME_WAIT after a SIGKILL.
- **Cost** of re-reading the registry on every request with many entries.

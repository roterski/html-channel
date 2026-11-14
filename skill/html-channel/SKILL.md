---
name: html-channel
description: Present output or questions to the user as an interactive local HTML page instead of markdown, and get their answers back. Use when you need a decision, approval or choice from the user, when presenting options or trade-offs, when asking for review of generated code or a plan, or when a report would read better as a page.
---

# html-channel

You write an HTML page. The user answers on it with buttons and fields, and you read the answers back.
The markup rules are in [CONVENTION.md](CONVENTION.md). Follow them exactly.
Its "Choosing a representation" and "Choosing a control" tables say which form fits which content and question.
[example.html](example.html) is a worked page that uses every form and control. Read it before writing your first page.
Your page may use JavaScript to render things, but answers go only through its `data-decision` controls.
To draw a statechart (modes and the events between them, which the reader drives), also read [STATECHART.md](STATECHART.md).
To explain a topic in depth with instruments the reader drives, also read [EXPLORABLE.md](EXPLORABLE.md)
and the worked [example-explorable.html](example-explorable.html).

Pick the first branch that applies.

## 1. MCP tools available (`channel_setup`, `check_page`)

1. Call `channel_setup`. It returns `dir`, `url_base`, `session` and the convention text.
2. Write `<dir>/<page>.html`, where `<page>` matches `[a-z0-9-]+`.
3. Call `check_page {page}`. Fix every error in `errors` and read the dark screenshot; check again after each fix.
4. Decision page: run `html-channel wait <session>/<page>` as a background command.
   The user can keep talking to you meanwhile. When the command exits, you're woken with its JSON:
   - `status: "done"`: act on `decisions`.
   - `status: "deeper"`: rewrite the page with the targets in `deeper` expanded, start a new wait, and give the URL again.
   - `status: "superseded"`: the page got a new revision; do nothing, the wait for that revision covers it.
   - `status: "waiting"` (only with `--max-wait`): start the wait again.

   Start a new wait after every rewrite of the page, including one the user asked for in chat.
   If your client can't run a background command that wakes you when it exits,
   ask the user to say "done" in chat, then call `get_decisions {page}`.
   Report-only page: don't wait.
5. End your turn with a short text message that shows the full URL, `<url_base><page>.html`, on its own line,
   so a condensed view of the message still shows it.
   The URL goes in this final message, after the wait has started, because a client may hide text written between tool calls.
   Give it for every revision too.

## 2. `html-channel` on PATH (no MCP tools)

1. Run `html-channel health`. If it fails, start a server in the background: `html-channel serve &`.
2. Run `html-channel session new`. It prints `session`, `dir` and `url_base` as JSON.
   Reuse the same session for the rest of the conversation.
3. Write `<dir>/<page>.html`, run `html-channel check <session>/<page>`, fix its `errors` and read the dark screenshot.
4. Decision page: wait as in step 4 of branch 1, with `html-channel wait <session>/<page>` in the background.
   Without background commands that wake you, ask the user to click Done and then tell you in chat.
   Then run `html-channel decisions <session>/<page>`. Act only when `done` is `true`.
   If `deeper` lists targets instead, rewrite the page with them expanded and ask again.
5. End your turn with `<url_base><page>.html`, as in step 5 of branch 1.

## 3. Neither is available

Present the content in markdown and say why: html-channel isn't installed.
Install it with `bbin install io.github.roterski/html-channel`.

## Revising

To revise a page, rewrite the same file. The user's open tab reloads by itself, and their earlier answers carry over.
Each revision needs its own Done.
In the decisions you get back, an answer whose `rev` is lower than the page's `rev` was carried over and not re-confirmed.

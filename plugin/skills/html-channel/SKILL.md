---
name: html-channel
description: Present output or questions to the user as an interactive local HTML page instead of markdown, and get their answers back. Use when you need a decision, approval or choice from the user, when presenting options or trade-offs, when asking for review of generated code or a plan, or when a report would read better as a page.
---

# html-channel

You write an HTML page. The user answers on it with buttons and fields, and you read the answers back.
The markup rules are in [CONVENTION.md](CONVENTION.md). Follow them exactly.
Your page may use JavaScript to render things, but answers go only through its `data-decision` controls.

Pick the first branch that applies.

## 1. MCP tools available (`channel_setup`, `ask_human`)

1. Call `channel_setup`. It returns `dir`, `url_base`, `session` and the convention text.
2. Write `<dir>/<page>.html`, where `<page>` matches `[a-z0-9-]+`.
3. Call `check_page {page}`. Fix every error in `errors` and read the dark screenshot; check again after each fix.
4. Give the user `<url_base><page>.html`.
5. Decision page: call `ask_human {page}`.
   - If it returns `status: "waiting"`, call it again.
   - If it returns `status: "deeper"`, rewrite the page with the targets in `deeper` expanded, then call it again.
   - If it returns `status: "done"`, act on `decisions`.
   - If the call fails or times out in your client, ask the user to say "done" in chat, then call `get_decisions {page}`.
6. Report-only page: don't call `ask_human`.

## 2. `html-channel` on PATH (no MCP tools)

1. Run `html-channel health`. If it fails, start a server in the background: `html-channel serve &`.
2. Run `html-channel session new`. It prints `session`, `dir` and `url_base` as JSON.
   Reuse the same session for the rest of the conversation.
3. Write `<dir>/<page>.html`, run `html-channel check <session>/<page>`, fix its `errors` and read the dark screenshot.
4. Give the user `<url_base><page>.html`.
5. Decision page: ask the user to click Done and then tell you in chat.
   Then run `html-channel decisions <session>/<page>`. Act only when `done` is `true`.
   If `deeper` lists targets instead, rewrite the page with them expanded and ask again.

## 3. Neither is available

Present the content in markdown and say why: html-channel isn't installed.
Install it with `bbin install io.github.roterski/html-channel`.

## Revising

To revise a page, rewrite the same file. The user's open tab reloads by itself, and their earlier answers carry over.
Each revision needs its own Done.
In the decisions you get back, an answer whose `rev` is lower than the page's `rev` was carried over and not re-confirmed.

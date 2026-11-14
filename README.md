# html-channel

Interactive HTML pages as an output channel for agents.

The agent writes a static page into `<project>/ai/html-channel/<session>/`.
You open it in a browser and answer with buttons and fields.
The agent reads your answers back. Clicking Done wakes it, while the chat stays free until then.

- Pages may use JavaScript to render, and the kit renders Mermaid, Vega-Lite and KaTeX blocks itself. Explorable pages, which explain a topic with instruments the reader drives, get scrubbable numbers, state links, timelines and an interaction check ([EXPLORABLE.md](EXPLORABLE.md)). Statecharts the reader drives come from a definition alone ([STATECHART.md](STATECHART.md)). Answers go only through the runtime's controls. The markup rules are in [CONVENTION.md](CONVENTION.md).
- Before showing a page, the agent can render it in headless Chrome (`check_page`) to catch errors and see light and dark screenshots.
- It runs on localhost only (`127.0.0.1:8787`), for a single user, with no auth.
  Other websites open in your browser can't read it or write to it.
- Answers are appended to `ai/html-channel/<session>/decisions.jsonl`, which you can commit.
- Rewriting a page creates a new revision. Open tabs reload, and earlier answers carry over.

## Install

```sh
bbin install io.github.roterski/html-channel
```

This gives you the `html-channel` command. It needs [babashka](https://babashka.org) 1.13.220 or newer and [bbin](https://github.com/babashka/bbin), and no Node.

## Use with an agent

### Claude Code (MCP)

Add to `.mcp.json`, or run `claude mcp add html-channel -- html-channel mcp`:

```json
{ "mcpServers": { "html-channel": { "command": "html-channel", "args": ["mcp"] } } }
```

The agent calls `channel_setup`, writes the page, checks it with `check_page`,
runs `html-channel wait <session>/<page>` as a background command, then ends its turn with the URL.
You can keep chatting, for example to ask about the page or have it reshaped.
When you click Done or a Deeper button, `wait` exits, and Claude Code wakes the agent with its output.
A rewrite of the page ends the old wait with `superseded`, and the agent starts a new one.

`ask_human` waits for the same clicks inside a tool call, which blocks the chat until you click.
It sends progress every 30 s to keep the call alive, and after 25 minutes it returns `waiting`.

To also get the skill, install the plugin in [plugin/](plugin/). It bundles the skill and this MCP config.

### Claude desktop

In `claude_desktop_config.json`:

```json
{ "mcpServers": { "html-channel": { "command": "html-channel", "args": ["mcp", "--max-wait", "50"] } } }
```

Desktop reportedly drops tool calls after about 60 s (unverified), so `ask_human` returns `waiting` after 50 s and the agent polls again.

### ECA

ECA isn't a supported target, but this works. In `~/.config/eca/config.json`:

```json
{ "mcpServers": { "html-channel": { "command": "html-channel", "args": ["mcp", "--max-wait", "100"] } } }
```

ECA times out tool calls at 120 s and runs one call at a time per MCP server.
While one chat is waiting in `ask_human`, other chats' calls to html-channel queue behind it.

### Without MCP (skill or by hand)

```sh
html-channel serve &                     # serve; stands by if another process owns the port
html-channel session new                 # {session, dir, url_base}
# write <dir>/<page>.html
html-channel check <session>/<page>      # {errors, screenshots} from headless Chrome
# open <url_base><page>.html
html-channel wait <session>/<page>       # blocks until Done, Deeper or a new revision; prints decisions + status
html-channel decisions <session>/<page>  # the current answers, without waiting
```

The skill in [skill/html-channel/](skill/html-channel/) follows this flow when no MCP tools are available.

## How the server stays up

Every `html-channel mcp` and `html-channel serve` process registers its root in `~/.local/state/html-channel/roots.jsonl`.
You can change that location with `HTML_CHANNEL_HOME` or `XDG_STATE_HOME`.

All of these processes try to bind the port.
- One process owns the port and serves every registered project at `/<project>-<hash>/<session>/<page>.html`.
- The others retry every second.
- When the owner exits, another process takes over within about a second.
- Once no process is left, open pages show an offline banner.

The port is 8787 by default. Change it with `--port` or `HTML_CHANNEL_PORT`.

## Where sessions go

Sessions go to `<project>/ai/html-channel/<session>/` by default, where `<project>` is `$CLAUDE_PROJECT_DIR`, else the cwd.
Change the root with `--root` or `HTML_CHANNEL_ROOT`, for example in the project's `.mcp.json` `env`.
A relative root resolves against `<project>`, and sessions go directly under it: `--root docs/decisions` writes `docs/decisions/<session>/`.

## CLI

```
html-channel serve [--root ai/html-channel] [--port 8787]
html-channel mcp [--root] [--max-wait 1500] [--port]
html-channel session new [--root]
html-channel decisions <session>/<page> [--root]
html-channel wait <session>/<page> [--root] [--max-wait S]
html-channel check <session>/<page> [--root]
html-channel health [--port]
html-channel version
```

`wait` blocks until the reader acts on the page's revision at start, then prints the `decisions` JSON plus `status`:
`done`, `deeper` (with only the targets requested during the wait), `superseded` (the page got a new revision) or `waiting` (`--max-wait` seconds passed; no timeout without it).
It exits 0 for each status and 1 for a missing page.

`check` finds Chrome through `HTML_CHANNEL_CHROME`, then the standard macOS app, then `google-chrome` or `chromium` on PATH.
Screenshots go to `<session>/.check/`, which it keeps out of git.

## Development

```sh
npm i                                    # squint, for building the runtime only
bb build                                 # runtime/ -> resources/html_channel/kit/; copies CONVENTION.md, EXPLORABLE.md, STATECHART.md and the skill
python3 -m venv .venv && .venv/bin/pip install playwright && .venv/bin/playwright install chromium
bb test                                  # unit tests, then browser and MCP end-to-end tests
bb serve --root examples                 # try the demo: open http://127.0.0.1:8787/
```

Build outputs (`resources/html_channel/`, `skill/html-channel/CONVENTION.md`, `plugin/skills/`) are committed, so installing needs only `bb`.
Edit `CONVENTION.md`, `EXPLORABLE.md` and `STATECHART.md` at the repo root, never their copies.

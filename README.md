# html-channel

Interactive HTML pages as an output channel for agents.

The agent writes a static page into `<project>/.ai/<session>/`.
You open it in a browser and answer with buttons and fields.
The agent reads your answers back, either blocking on an MCP tool or through the CLI.

- Pages contain no JavaScript of their own. The markup rules are in [CONVENTION.md](CONVENTION.md).
- It runs on localhost only (`127.0.0.1:8787`), for a single user, with no auth.
- Answers are appended to `.ai/<session>/decisions.jsonl`, which you can commit.
- Rewriting a page creates a new revision. Open tabs reload, and earlier answers carry over.

## Install

```sh
bbin install io.github.roterski/html-channel
```

This gives you the `html-channel` command. It needs [babashka](https://babashka.org) and [bbin](https://github.com/babashka/bbin), and no Node.

## Use with an agent

### Claude Code (MCP)

Add to `.mcp.json`, or run `claude mcp add html-channel -- html-channel mcp`:

```json
{ "mcpServers": { "html-channel": { "command": "html-channel", "args": ["mcp"] } } }
```

The agent calls `channel_setup`, writes the page, then calls `ask_human`.
`ask_human` blocks until you click Done, and it sends progress every 30 s to keep the call alive.
After 25 minutes it returns `waiting`, and the agent calls it again.

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
html-channel serve --root .ai &          # serve; stands by if another process owns the port
html-channel session new --root .ai      # {session, dir, url_base}
# write <dir>/<page>.html, open <url_base><page>.html, click Done
html-channel decisions <session>/<page> --root .ai
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

## CLI

```
html-channel serve [--root .ai] [--port 8787]
html-channel mcp [--max-wait 1500] [--port]
html-channel session new [--root .ai]
html-channel decisions <session>/<page> [--root .ai]
html-channel health [--port]
html-channel version
```

## Development

```sh
npm i                                    # squint, for building the runtime only
bb build                                 # runtime/ -> resources/html_channel/kit/; copies CONVENTION.md and the skill
python3 -m venv .venv && .venv/bin/pip install playwright && .venv/bin/playwright install chromium
bb test                                  # unit tests, then browser and MCP end-to-end tests
bb serve --root examples                 # try the demo: open http://127.0.0.1:8787/
```

Build outputs (`resources/html_channel/`, `skill/html-channel/CONVENTION.md`, `plugin/skills/`) are committed, so installing needs only `bb`.
Edit `CONVENTION.md` at the repo root, never its copies.

# Frame: one AI directory

Framed 2026-10-16. Answers recorded in `ai/html-channel/c60daa3f/decisions.jsonl`, page `ai-dir-layout` rev 1.
Revised the same day in design: see **Revised decisions**.

## Situation

html-channel writes sessions to `<project>/.ai/<session>/`.
Agent docs (plan, frames, spikes) live in `ai/`.
ai-behaviors writes to `ai/spike/` and `ai/stepback/`.

## Complication

Two sibling directories hold one kind of thing.
html-channel also claims its whole root, so its session ids would mix with docs if both shared one directory.

## Question

Which single directory, and how does html-channel namespace its sessions inside it?

## Decisions

- **Directory:** `ai/`, which matches ai-behaviors.
- **Subdirectory:** `ai/html-channel/<session>/`, named after its owner like `ai/spike/`.
- **`--root`:** points at `ai/`.
  The tool appends `html-channel/` itself, so it owns its namespace, and a wrong root can't drop sessions next to docs.
- **Git:** sessions are tracked, because the decisions are a record.
- **Existing sessions:** `.ai/b658090c/` and `.ai/c60daa3f/` move to `ai/html-channel/`.

```
ai/
├── frame.md
├── frame-ai-dir.md
├── plan.md
├── spike/
└── html-channel/
    ├── b658090c/
    └── c60daa3f/   page.html, page@1.html, decisions.jsonl
```

## Revised decisions

Design overturned the `--root` decision above in favour of flexibility.

- **`--root` names the sessions directory itself:** sessions go to `<root>/<session>/`, and the tool appends nothing.
  A project may point it anywhere; a wrong root is accepted unchecked.
- **Default:** `<project>/ai/html-channel`, where `<project>` is `$CLAUDE_PROJECT_DIR`, else the cwd.
  One function, `store/root-from`, resolves it for the CLI and MCP.
- **Override:** `--root` (CLI and `mcp`), else `$HTML_CHANNEL_ROOT`; a relative value resolves against `<project>`.
- **Mount name:** `<project>` for a root ending in `ai/html-channel`, else the root's own name. The `.ai` case is gone.
- **Old URLs:** break once, because the mount hash comes from the root path. Pages resolve at their new URLs.
- **Git:** `.gitignore` narrows to `examples/**/decisions.jsonl`; snapshots are tracked too.
- **`examples/`:** unchanged; `--root examples` still serves `examples/demo/`.
- **Other projects:** herdness's session moves by hand to `ai/html-channel/`.

## Constraints

- The registry derives a project's mount name from its root.
  `registry.clj:27-30` takes the parent of a root named `.ai`.
  With root `ai/html-channel`, the rule must take the project two levels up.
- Existing pages and answers must keep resolving after the move.
- Docs and code change together.
  No commit should have docs naming one path while the code writes another.

## Non-goals

- Changing ai-behaviors' layout beyond the path in its `#html` README.
- Changing the URL shape beyond the mount name.

## Contradiction to resolve

README says `decisions.jsonl` "you can commit", but this repo's `.gitignore` ignores `decisions.jsonl` everywhere.
The rule is there for `examples/`.
Tracking sessions means narrowing it to `examples/**/decisions.jsonl`.

## Touch points

- `src/html_channel/mcp.clj:5,34`: MCP default root.
- `src/html_channel/cli.clj:17-20,32,40,52`: CLI default root and help text.
- `src/html_channel/session.clj`, `store.clj`: where `<root>/<session>` becomes `<root>/html-channel/<session>`.
- `src/html_channel/registry.clj:27-30`: project-name derivation.
- `CONVENTION.md:11,78`, `README.md`, `skill/html-channel/SKILL.md:26-31`, then `bb build` to refresh the copies.
- `.gitignore`: `decisions.jsonl` rule.
- Tests: `test/support.py:44`, `test/mcp_e2e.py:39,71`, and the registry and server tests.
- ai-behaviors `behaviors/html/README.md:8`.
- `ai/frame.md` and this file: the pointers to `.ai/c60daa3f/decisions.jsonl`.

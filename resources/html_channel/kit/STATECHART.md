# Statecharts

A statechart is a chart of the modes something can be in and the events that move it between them, which the reader drives.
The kit draws it, runs it, and adds the controls; the page writes only a definition.
Everything in CONVENTION.md still applies.

For a worked example, read the source of `/_kit/example-statechart.html` on the server
(for example, `curl http://127.0.0.1:8787/_kit/example-statechart.html`), or `example-statechart.html` next to this file in the skill.

## When to read this

Read it before putting a statechart on any page: a report, a decision page, or an explorable (EXPLORABLE.md).
Use one when the subject has modes and events between them (a request that loads, fails and retries; an editor that is clean or dirty), and seeing the transitions helps.
Continuous quantities belong in a plot or a scrubbed demo instead.

## Markup

```html
<figure data-statechart data-instrument="fetch">
  <script type="application/json">
  { "id": "fetch", "initial": "idle", "context": { "retries": 0 },
    "states": {
      "idle":    { "on": { "FETCH": "loading" }, "meta": { "code": "app:src/api.ts#L12" } },
      "loading": { "on": { "OK": "ready",
                           "FAIL": [ { "target": "loading", "guard": { "lt": ["retries", 3] },
                                       "actions": { "assign": { "retries": { "add": 1 } } } },
                                     { "target": "failed" } ] } },
      "ready":   { "type": "parallel", "states": { … } },
      "failed":  { "on": { "RETRY": "loading" }, "after": { "2000": "idle" } } } }
  </script>
</figure>
```

| Attribute | Meaning |
|---|---|
| `data-statechart` | a chart. Its definition is the `<script type="application/json">` inside it |
| `data-instrument="ID"` | required, on every page: the check's probe drives the chart through it |
| `data-machine="DEF"` | instead of an inline definition, draw the shared `<script type="application/json" id="DEF">` |
| `data-collapse="a,b"` | draw the listed composite states as single states (a key, or a dotted path when the key isn't unique) |
| `data-renderer="mermaid"` or `"smcat"` | force a renderer (see Renderers) |

## Definition

A subset of the XState v5 machine config.

| Key | Where | Meaning |
|---|---|---|
| `initial` | the machine, every compound state | the key of the child entered first |
| `states` | the machine, any state | child states, by key |
| `type` | a state | `"parallel"` (all children active at once), `"final"`, `"history"` |
| `history` | a history state | `"shallow"` (default) or `"deep"` |
| `on` | any state, the machine | `{EVENT: target}`; a target is a string, `{target, guard, actions, meta}`, or a list of them, tried in order |
| `always` | a state | transitions taken as soon as their guard holds |
| `after` | a state | `{"2000": target}`: taken 2000 ms after the state is entered |
| `id` | a state | a name to target with `"#name"` |
| `context` | the machine | the values guards read and the reader edits |
| `meta.code` | a state, a transition object | one code reference or a list (see Code references) |
| `meta.inferred.basis` | the machine | the chart is inferred from code; says from what |
| `meta.enum` | the machine | `{key: [values]}`: the context panel offers these values in a select |

Targets follow XState: `"sibling"`, `"sibling.child"`, `".child"` (inside the source), `"#id"` or `"#machineId.path"`.
A target written without a key is a self-transition that runs only its actions.

**Guards** are objects, never code: `{"lt": ["retries", 3]}`.
Comparisons `eq neq lt lte gt gte` take `[contextKey, value]`; `and` and `or` take a list of guards, `not` one guard.

**Actions** are only assigns: `{"assign": {"retries": {"add": 1}, "mode": {"set": "edit"}}}`.

**Delays** (`after`) run on the kit's clock, which stops while the page is paused (the global pause, `prefers-reduced-motion`, a check's probe). Every change (an event, a step in the log, a context edit) restarts the delays of the states then active.

## What the reader gets

- The chart, with the active states outlined in the accent color. A parallel state's regions are never outlined: all of them are active whenever their parent is.
- A button per event the active states take, disabled while every guard on it is false. Clicking a transition's label in the chart presses its event's button.
- A context panel: a number field, checkbox or select per context key. Changing a value re-checks the guards.
- A log with an entry per event and per context-panel edit (`retries = 3 → loading`, set in italics). Consecutive edits of one key make one entry, so dragging or retyping a value doesn't flood the log. An edit that changes nothing adds nothing.
  Clicking an entry goes back to it, context included; later entries turn grey and clicking one redoes up to it. A new event or edit drops the grey entries.
  Reset empties the log, edits included, and returns to the start.
- "Inferred from code: …" above the chart, when `meta.inferred.basis` is set.
- "Where in the code": each `meta.code` reference, linked. Clicking a state that has references shows its entry.

## Prose controls

- A `data-scrub` whose `data-target` is the chart sets the context key named by its `data-scrub` in the start, and the log is replayed from the new start: every entry shows what happens with the new value, and the current entry stays current. An event that no longer applies stays in the log, marked "(no effect)". The value holds after Reset, a replay link, or a click on the log's start entry.
- A state link with an `events` list resets the chart and replays them: `<a data-set="fetch" data-state='{"events": ["FETCH", "FAIL", "FAIL"]}'>after two failed fetches</a>`.

## Code references

`meta.code` uses the code-links format from CONVENTION.md: declare each repo once with `<meta name="hc-repo">`, and write references as `NAME:PATH#Lstart[-Lend]`.
The kit lists them as `<code data-src>`, so the reader's code-links settings decide where they open.
A malformed reference is reported as kind `code-link`.

## Renderers

Mermaid draws charts by default. State-machine-cat draws a chart that has a history state, because Mermaid has no history notation.
`data-renderer` forces either one; Mermaid then draws a history state as a plain state labeled H or H*.
Both follow the `--hc-*` tokens in light and dark.

## Growing one chart across sections

Write the whole machine once, as a shared definition, and point each figure at it with `data-machine`.
Early sections collapse the composites they haven't reached yet with `data-collapse`; later sections drop them from the list.
Keep state names and the layout the same between versions: refining a state moves every other state in the drawing, so say in the prose which state was refined.

## Events on the figure

| Event | `event.detail` | When |
|---|---|---|
| `hc-statechart` | `{value, context, event}` | after every change; `value` is the XState state value |

The figure's `data-active` holds the outlined states as a JSON array of uids (the key path joined with `__`).
Each drawn state's SVG group carries its uid as `data-uid`.

## Checklist

The kit reports these on every load, as kind `statechart` with the source shown here:

| Source | Meaning |
|---|---|
| `statechart-json` | the definition isn't JSON, lacks `initial` or `states`, or a compound state lacks `initial`. The figure shows the source and a notice |
| `statechart-target` | a target or an `initial` names no state |
| `statechart-guard` | a guard or assign is malformed, or names a context key the context doesn't have |
| `statechart-unreachable` | no path from the start reaches a state (guards ignored) |
| `statechart-history` | a chart with a history state is forced to Mermaid |
| `statechart-machine` | `data-machine` names no definition, or `data-collapse` names something that isn't a composite state |
| `statechart-instrument` | a `data-statechart` without `data-instrument` |

During a check, the probe drives every chart's buttons, on any page with a chart. `inert` and `unprobed` mean what they mean in EXPLORABLE.md.

Before giving the URL, also check what no tool can:

- Every state and transition is traced to the code, through `meta.code`, or the chart says it's inferred and from what.
- The chart shows the behavior the prose describes, at the level of detail the prose needs.

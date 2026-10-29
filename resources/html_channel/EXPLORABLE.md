# Explorable pages

An explorable is one long page that explains a topic in depth, with small instruments the reader drives.
Everything in CONVENTION.md still applies; this file adds what an explorable needs on top.

For a worked example, read the source of `/_kit/example-explorable.html` on the server
(for example, `curl http://127.0.0.1:8787/_kit/example-explorable.html`), or `example-explorable.html` next to this file in the skill.

## When to read this

Read it before writing a page that explains a topic, system or idea, rather than one that asks for a decision.
Pages that only ask for decisions don't need it.

## Page kind

- Report-only by default: the page explains, and the agent doesn't wait.
- A decision page when the explanation leads to choices the reader should make, for example which option to adopt or which part to build first.
  Put each question right after the instrument or section it's about, as CONVENTION.md describes, and end with Done.
- Don't ask "what's still unclear?" on the page: the reader can say that in chat.

## Markup

```html
<meta name="hc-kind" content="explorable">
```

marks the page as an explorable. It turns on the structure lint and, during `check_page`, the interaction probe.

| Markup | Meaning |
|---|---|
| `<section data-concept="ID">` | one concept: why it's needed, one picture, one instrument, a "Notice" line, a stated simplification |
| `data-instrument="ID"` on an element | one interactive demo. Its controls go inside it or point at it |
| `<span data-scrub="NAME" data-target="ID" data-min data-max data-step data-value>` | a number in the prose the reader drags sideways or steps with the arrow keys |
| `<a data-set="ID" data-state='{"k": 1}'>` | a link in the prose that puts a demo into a state |
| `<div data-timeline="ID" data-duration="SECONDS">` | play, pause, step, scrub and speed over time `t` |
| `<pre data-viz="katex">`, `<span data-viz="katex">` | display and inline math (`katex` in `hc-libs`) |

`ID` is the id of an element, or the value of its `data-instrument`.
The kit sends your demo DOM events; your code redraws:

| Event on the demo | `event.detail` | When |
|---|---|---|
| `hc-param` | `{name, value}` | a scrub's value, once at start and on every change |
| `hc-state` | the link's `data-state` object | a state link is clicked; a scrub whose name is a key moves too |
| `hc-time` | `{t, playing}` | a timeline's time, once at start and on every change |
| `hc-pause` (on `document`) | `{paused}` | the global pause toggles |

- Register listeners in an inline script at the end of `<body>`: the kit starts after it and sends the start values.
- The kit adds a global pause button. It starts paused under `prefers-reduced-motion`. Timelines follow it; your own animations must check `window.htmlChannel.paused` each frame.
- These controls never record answers. Answers still go only through `data-decision` controls.

## Anatomy

A page follows one arc:
phenomenon → smallest faithful toy → naive model → its failure → fix → combine → payoff → limits.

- **Opening.** One or two sentences of hook: something the reader has seen, or a claim that sounds impossible. Then a roadmap sentence. A first instrument early.
- **Legend.** If color carries meaning, say so once near the top: the nouns in the prose take the color of the things they name.
- **6–12 concept sections**, each in this order:
  1. Why it's needed: the problem from the previous section that this one fixes.
  2. A setup sentence ending in a colon, saying what to do with the instrument.
  3. The instrument.
  4. A line starting "Notice that…", saying what to see.
  5. The simplification it makes, in plain words.
- **Naive models on purpose.** Build the plausible wrong model, let the reader break it, then fix it. Name common misconceptions and say why they're wrong.
- **Closing**, in this order: **Limits** (where the model stops being true), **Further reading**, **Sources**. Headings start with those words.

## Instruments

Each instrument is a controlled experiment the reader runs.

- **One knob, one effect.** Each control changes one thing the reader can name.
- **Time as a control.** Anything that unfolds gets a timeline: pause, step and scrub, not just play.
- **Numbers in the prose are knobs.** Make the number a sentence is about a `data-scrub`.
- **Links set the demo.** "With 23 keys" becomes a `data-set` link that puts the demo there.
- **Let the reader break it.** Inputs that produce the failure the next section fixes.
- **Hold a baseline.** When comparing, keep the old state visible next to the new one.
- **Reuse one model.** Grow the same demo across sections rather than starting a new one each time.
- Put controls that belong to a demo inside its `data-instrument`. Put scrubs and links in the prose, and timelines right after the instrument.

## Representation

- Pick the form from the question: a distribution, a flow, a hierarchy, a change over time. CONVENTION.md's table applies.
- Introduce encodings one at a time, and say what each one hides.
- Distort for legibility, but keep the ratio that matters, and say which ratio it is.
- Show the smallest instance that is still the real thing: numbers small enough to check by hand.
- Step between scales: one concrete case, then the curve over all cases, then back to a point on it.

## Locking text and picture together

- The sentence before an instrument says what to do with it; the sentence after it says what to see.
- Use the same color for a noun in the prose and the thing it names in the picture.
- Every number on the page that a reader might want to change is a scrub or a state link.

## Grounding

- Research the topic before writing.
- Every factual claim and every constant in a simulation either has an entry under Sources or is listed there as illustrative.
- Say when a formula assumes something the demo doesn't (a perfect hash, no friction, an ideal gas).

## Performance and accessibility

- Draw only what's on screen: start heavy demos with an `IntersectionObserver`.
- Respect the global pause and `prefers-reduced-motion`.
- Give every control an accessible name: a `<label>`, `aria-label` or visible text. The kit labels its own controls.
- Hit areas of at least 24 px; a small visible handle can have a larger invisible hit area.
- Work at phone width: SVG with a `viewBox` and `width: 100%`, controls that wrap.
- A WebGL canvas needs `preserveDrawingBuffer: true`, or the probe can't read it and reports it inert.

## Libraries

- Kit first: scrubs, state links, timelines, KaTeX, Mermaid and Vega-Lite need no library code of your own.
- Inline SVG from a few lines of JavaScript for most demos. Colors from the `--hc-*` tokens, set with `style`.
- D3 or Observable Plot for data-driven charts; canvas for many moving particles; WebGL only when 2D can't show it.
- Libraries load from a CDN at an exact version, as CONVENTION.md says.

## Checklist

`check_page` runs the lint and the probe. Their errors:

| Kind | Source | Meaning |
|---|---|---|
| `explorable` | `sections` | fewer than 6 or more than 12 `<section data-concept>` |
| `explorable` | `no-instrument` | a concept section without a `[data-instrument]` |
| `explorable` | `no-limits`, `no-sources` | no heading starting with Limits or Sources |
| `explorable` | `unlabeled-control` | a control in an instrument without an accessible name |
| `explorable` | `data-target`, `data-set`, `data-timeline`, `data-state` | a control points at no element, or its state isn't JSON |
| `inert` | the instrument | its controls were driven and it didn't change |
| `unprobed` | the instrument | it has no controls and a synthetic drag changed nothing: check it by hand |

The probe drives every instrument, so `check_page`'s screenshots show the page after it, not as a reader first sees it.

Before giving the URL, also check what no tool can:

- Each concept section says why it's needed before it explains.
- No term is used before it's introduced.
- One naive model fails on the page before its fix.
- Each simplification and each limit is stated.
- The page ends with a payoff that needs the sections before it.

## Golden references

ciechanow.ski sets the bar. Read the articles for structure; don't copy their text.

- [GPS](https://ciechanow.ski/gps/): 15 sections and about 40 demos. A naive positioning system (tapes, then drones, then sound) is improved step by step; "This solution actually works well, but it has a fatal flaw." Inline links set a demo's state.
- [Mechanical Watch](https://ciechanow.ski/mechanical-watch/): each mechanism fixes the problem the previous one left. One model is assembled across sections, and the naive date mechanism sits next to the correct one.
- [Airfoil](https://ciechanow.ski/airfoil/): from wind in grass to arrows, from molecules to averages. Each encoding (arrows, markers, color, contour lines) comes with what it hides.
- Both GPS and Watch: a demo within the first few paragraphs, bold colored nouns as the legend, "Notice that…" after demos, a global pause, and a closing with further reading.

# Frame: rich context pages

Framed 2026-10-16. Answers recorded in `ai/html-channel/c60daa3f/decisions.jsonl`, page `frame-rich-context` revs 1–2.

## Situation

html-channel delivers decision pages.
A page is static HTML with form controls and revisions, and answers return as `decisions.jsonl`.
The ai-behaviors `#html` modifier routes decisions there.

## Complication

- Pages are shaped like markdown.
  Diagrams, charts, folds and tiered depth go unused.
- The no-JS rule blocks every visualization library.
- The agent writes pages blind.
  Rev 1 of the frame page shipped light text on a white background in dark mode (`background: Canvas` without `color-scheme`), and the agent couldn't tell.

## Question

What page vocabulary, defaults and guidance give better comprehension and faster, better-informed decisions?
And which layer owns each piece?

Decision moments to serve: design, review, spec/frame, tour, research, debug.

## Constraints

- Localhost, single user.
  The agent writes each page in one pass.
- Pages may load any JS library.
  Libraries are declared in `<head>` and come from a pinned CDN, so pages need network access.
  The kit suggests a few universal libraries and restricts none.
- Answers flow only through the runtime's `data-decision` controls.
  Page JS may render anything.
  An interactive answer (clicking or editing a diagram) sets a control's value through the runtime.
- The agent picks the representation that fits the content.
  When none of the suggested libraries fit, it may use another.
- Depth comes from two sources.
  Folded tiers are authored up front.
  The reader can also ask to go deeper, which the agent answers with a new revision.
  Today `ask_human` wakes only on Done.
- Readability basics come from a base theme the kit ships: contrast, dark mode, readable width.
  Short guidance supplements it, and pages build on it.
- Render feedback is in scope.
  JS and library errors, and possibly a screenshot, reach the agent.
- Ownership:
  - **html-channel:** mechanism, base theme, library suggestions, basic guidance, render feedback.
  - **ai-behaviors:** structure of thought, independent of the channel.
    `#html` only maps that structure (e.g. `#zoom` levels, `#scq` sections) onto pages.
  - **Prompting:** which representation fits each subject.

## In scope

- Interactive data exploration: sort, filter, pivot.
- Reader editing a diagram as an answer.

## Non-goals

- A general dashboard or app framework.
  Boundary: exploring data for one decision on one page, not building reusable apps.
- Publishing beyond localhost.
- Live-streaming page content.

## Coherence obligations

The no-JS rule appears in three places, and they must be reworded together to "page JS renders; answers flow only through the runtime":
- `CONVENTION.md`: "contains no JavaScript", plus the ban on `<script>` blocks.
- `skill/html-channel/SKILL.md`: "the page must contain no JavaScript of its own".
- ai-behaviors `behaviors/html/prompt.md`: `InlinePageScript` in the forbidden set.

Research: [research.md](research.md). Design: [design.md](design.md). Spec: [spec.md](spec.md).

## Next: research threads

1. Libraries for the kit to suggest: diagrams, charts, tables and data exploration, editable diagrams.
2. Prior art on progressive disclosure and tiered depth in documents.
3. Capturing render errors and screenshots for the agent.
4. A wake signal other than Done, for reader-driven depth.

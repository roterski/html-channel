"""Statecharts: the data-statechart control (STATECHART.md), its lint, the
gating of lint and probe by the control, and the worked example.

  .venv/bin/python test/statechart.py

XState, Mermaid and state-machine-cat load from jsDelivr, so this test needs network access.
"""
import json, os, pathlib, shutil
from playwright.sync_api import sync_playwright, expect
from support import Mcp, REPO, Sandbox, fail, run, wait_for, write_page

PORT = 18789

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
%s
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
%s
</body></html>"""

FETCH = {
    "id": "fetch", "initial": "idle", "context": {"retries": 0, "online": True},
    "meta": {"inferred": {"basis": "values of status in src/api.ts"}},
    "states": {
        "idle": {"on": {"FETCH": {"target": "loading", "guard": {"eq": ["online", True]}}},
                 "meta": {"code": "app:src/api.ts#L12"}},
        "loading": {"on": {"OK": "ready", "FAIL": [
            {"target": "loading", "guard": {"lt": ["retries", 3]}, "actions": {"assign": {"retries": {"add": 1}}}},
            {"target": "failed"}]}, "meta": {"code": ["app:src/api.ts#L20-L34"]}},
        "ready": {"type": "parallel", "on": {"RELOAD": "loading"}, "states": {
            "view": {"initial": "viewing", "states": {"viewing": {"on": {"EDIT": "editing"}}, "editing": {"on": {"DONE": "viewing"}}}},
            "persist": {"initial": "saved", "states": {"saved": {"on": {"CHANGE": "dirty"}}, "dirty": {"on": {"SAVE": "saved"}}}}}},
        "failed": {"on": {"RETRY": {"target": "loading", "actions": {"assign": {"retries": {"set": 0}}}}},
                   "after": {"1000": "idle"}}}}

# every definition key: nested states, guarded lists, parallel, final, deep history, always, ids, context
KEYS = {
    "initial": "a", "context": {"n": 0},
    "states": {
        "a": {"on": {"GO": "b", "JUMP": [{"target": "c", "guard": {"gt": ["n", 0]}}, {"target": "b"}]}},
        "b": {"initial": "b1", "on": {"OUT": "c"}, "states": {
            "b1": {"on": {"NEXT": "b2"}},
            "b2": {"always": {"target": "#done", "guard": {"eq": ["n", 0]}}},
            "bh": {"type": "history", "history": "deep"}}},
        "c": {"type": "parallel", "on": {"BACK": "b.bh"}, "states": {
            "r1": {"initial": "x", "states": {"x": {"on": {"T": "y"}}, "y": {}}},
            "r2": {"initial": "p", "states": {"p": {}, "q": {}}}}},
        "done": {"id": "done", "type": "final"}}}

# the same machine written for XState directly, with guards as functions
KEYS_NATIVE = """{
  initial: "a", context: {n: 0},
  states: {
    a: {on: {GO: "b", JUMP: [{target: "c", guard: ({context}) => context.n > 0}, {target: "b"}]}},
    b: {initial: "b1", on: {OUT: "c"}, states: {
      b1: {on: {NEXT: "b2"}},
      b2: {always: {target: "#done", guard: ({context}) => context.n === 0}},
      bh: {type: "history", history: "deep"}}},
    c: {type: "parallel", on: {BACK: "b.bh"}, states: {
      r1: {initial: "x", states: {x: {on: {T: "y"}}, y: {}}},
      r2: {initial: "p", states: {p: {}, q: {}}}}},
    done: {id: "done", type: "final"}}}"""
KEYS_EVENTS = ["JUMP", "OUT", "T", "BACK", "NEXT"]

# keys with hyphens and spaces; a transition with an unknown target gets no button
HYPHEN = {"initial": "east-west", "states": {
    "east-west": {"initial": "red", "on": {"STOP": "all red", "LOST": "nowhere"}, "states": {
        "red": {"on": {"swap-flow": "green"}},
        "green": {"on": {"warn-traffic": "flashing white"}},
        "flashing white": {"on": {"swap-flow": "red"}}}},
    "all red": {"on": {"GO": "east-west"}}}}

# a history chart (state-machine-cat) with hyphenated keys and a code reference
POWER = {"initial": "power-off", "states": {
    "power-off": {"on": {"POWER": "power-on.last-mode"}},
    "power-on": {"initial": "low", "on": {"POWER": "power-off"}, "states": {
        "low": {"on": {"UP": "high"}, "meta": {"code": "app:src/power.ts#L3"}},
        "high": {"on": {"DOWN": "low"}},
        "last-mode": {"type": "history"}}}}}

# an edit that turns an always-guard true moves the state (2.2.9.5)
AUTO = {"initial": "idle", "context": {"armed": False}, "states": {
    "idle": {"always": {"target": "armed", "guard": {"eq": ["armed", True]}}},
    "armed": {"on": {"DISARM": "idle"}}}}
AUTO_NATIVE = """{
  initial: "idle", context: {armed: false},
  on: {"hc.context": {actions: window.XState.assign(({event}) => event.patch)}},
  states: {idle: {always: {target: "armed", guard: ({context}) => context.armed === true}},
           armed: {on: {DISARM: "idle"}}}}"""

HISTORY = {"initial": "off", "states": {
    "off": {"on": {"POWER": "on.hist"}},
    "on": {"initial": "low", "on": {"POWER": "off"}, "states": {
        "low": {"on": {"UP": "high"}}, "high": {"on": {"DOWN": "low"}}, "hist": {"type": "history"}}}}}


def script(d, id=None):
    return '<script type="application/json"%s>%s</script>' % (' id="%s"' % id if id else "", json.dumps(d))


MAIN = HEAD % ('<meta name="hc-repo" content="name=app; root=/work/app; web=https://github.com/acme/app/blob/0123456789abcdef0123456789abcdef01234567/">', """
<p>retries <span id="scrub" data-scrub="retries" data-target="fetch" data-min="0" data-max="5" data-value="0">0</span>
<a id="replay" data-set="fetch" data-state='{"events": ["FETCH", "FAIL", "FAIL"]}'>two failures</a></p>
<figure data-statechart data-instrument="fetch">%s</figure>
<figure data-statechart data-instrument="small" data-machine="fetch-def" data-collapse="ready"></figure>
%s
<figure data-statechart data-instrument="keys">%s</figure>
<figure data-statechart data-instrument="hist">%s</figure>
<figure data-statechart data-instrument="forced" data-renderer="mermaid">%s</figure>
<figure data-statechart data-instrument="hyphen">%s</figure>
<figure data-statechart data-instrument="power">%s</figure>
<figure data-statechart data-instrument="auto">%s</figure>
<script>
  window.sc = [];
  document.querySelector('[data-instrument=fetch]').addEventListener('hc-statechart', e => window.sc.push(e.detail));
  window.keys = [];
  document.querySelector('[data-instrument=keys]').addEventListener('hc-statechart', e => window.keys.push(e.detail.value));
</script>""" % (script(FETCH), script(FETCH, "fetch-def"), script(KEYS), script(HISTORY), script(HISTORY),
                  script(HYPHEN), script(POWER), script(AUTO)))

# not an explorable: one fault per figure, a dead instrument for the probe, and a bad code reference
BROKEN = HEAD % ('<meta name="hc-repo" content="name=app; root=/work/app">', """
<div data-instrument="dead"><button type="button">Nothing happens</button></div>
<figure data-statechart data-instrument="badjson"><script type="application/json">{ "initial": "a", </script></figure>
<figure data-statechart data-instrument="faults">%s</figure>
<figure data-statechart data-instrument="forced" data-renderer="mermaid">%s</figure>
<figure data-statechart data-machine="missing"></figure>
<figure data-statechart data-instrument="col" data-machine="shared" data-collapse="a,zz"></figure>
<figure data-statechart data-instrument="badref">%s</figure>
%s""" % (script({"initial": "a", "context": {"n": 0}, "states": {
    "a": {"on": {"GO": "nope", "G": {"target": "b", "guard": {"lt": ["m", 1]}},
                 "H": {"target": "b", "actions": {"assign": {"q": {"add": 1}}}}, "OK": "b"}},
    "b": {"on": {"BACK": "a"}, "after": {"SHORT": "a"}}, "orphan": {}}}),
    script(HISTORY),
    script({"initial": "a", "states": {"a": {"on": {"GO": "b"}, "meta": {"code": "app:/abs.ts"}}, "b": {}}}),
    script({"initial": "a", "states": {"a": {"on": {"GO": "b"}}, "b": {}}}, "shared")))

# a delay that would fire during a check's probe: page JS reports it if it fires while paused
DELAY = HEAD % ("", """
<figure data-statechart data-instrument="slow">%s</figure>
<script>
  document.querySelector('[data-instrument=slow]').addEventListener('hc-statechart', e => {
    if (String(e.detail.event).startsWith('after') && window.htmlChannel.paused)
      window.htmlChannel.reportError('js', 'a delayed transition fired while paused', 'delay');
  });
</script>""" % script({"initial": "a", "states": {"a": {"on": {"GO": "b"}, "after": {"700": "b"}}, "b": {"on": {"BACK": "a"}}}}))


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()
        mcp = Mcp(sb, project)
        mcp.init()
        setup = mcp.call("channel_setup")
        s, root, base = setup["session"], project / "ai/html-channel", setup["url_base"]
        for name, html in [("main", MAIN), ("broken", BROKEN), ("delay", DELAY),
                           ("example", (REPO / "examples/statechart/answer-runtime.html").read_text())]:
            write_page(root, s, name, html)
        wait_for(lambda: sb.url(root, s, "main") is not None, 15, "server not up")

        src = "".join((REPO / "runtime/decide" / f).read_text()
                      for f in ["statechart.cljs", "statechart_machine.cljs", "statechart_diagram.cljs"])
        if "eval(" in src or "new Function" in src or "js/Function" in src:
            fail("the statechart control evaluates code")
        print("2.2.3 ok: no eval or Function in the control's source")

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page(viewport={"width": 1100, "height": 900})
            page.goto(base + "main.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            for inst in ["fetch", "small", "keys", "hist", "forced", "hyphen", "power", "auto"]:
                expect(page.locator(f"[data-instrument={inst}] .hc-sc-chart svg")).to_have_count(1, timeout=40000)
            active = lambda inst: " ".join(json.loads(page.get_attribute(f"[data-instrument={inst}]", "data-active")))
            send = lambda inst, ev: page.click(f"[data-instrument={inst}] .hc-sc-events button[data-event={ev}]")
            log = lambda inst: page.eval_on_selector_all(f"[data-instrument={inst}] .hc-sc-log li",
                                                         "ls => ls.map(l => [l.className, l.textContent])")
            texts = lambda inst: [t for _, t in log(inst)]
            entry = lambda inst, n: page.click(f"[data-instrument={inst}] .hc-sc-log li:nth-child({n}) button")
            reset = lambda inst: page.click(f"[data-instrument={inst}] .hc-sc-reset")
            field = lambda inst, k: f"[data-instrument={inst}] input[name={k}]"
            def edit(inst, k, v):
                page.fill(field(inst, k), str(v)); page.dispatch_event(field(inst, k), "change")

            # 2.2.1, 2.2.13
            if active("fetch") != "idle" or page.locator("[data-instrument=fetch] .hc-sc-events button").count() != 1:
                fail("start: %s" % active("fetch"))
            expect(page.locator("[data-instrument=fetch] .hc-sc-inferred")).to_have_text("Inferred from code: values of status in src/api.ts")
            print("2.2.1, 2.2.13 ok: chart, buttons, log and the inferred line render")

            # 2.2.2 every key gives the same state values as XState run directly
            page.evaluate("ks => document.querySelector('[data-instrument=keys]').dispatchEvent("
                          "new CustomEvent('hc-state', {detail: {events: ks}}))", KEYS_EVENTS)
            kit = page.evaluate("window.keys.slice(-%d)" % len(KEYS_EVENTS))
            native = page.evaluate("""evs => { const X = window.XState;
                const a = X.createActor(X.createMachine(%s)); a.start();
                return evs.map(e => { a.send({type: e}); return a.getSnapshot().value; }); }""" % KEYS_NATIVE, KEYS_EVENTS)
            if kit != native:
                fail("every-key definition differs from XState:\n kit    %s\n xstate %s" % (kit, native))
            print("2.2.2 ok: %d events give XState's own values, ending in %s" % (len(kit), kit[-1]))

            # 2.2.3 guard table, through the kit's own module
            cases = page.evaluate("""async () => { const M = await import('/_kit/decide/statechart_machine.mjs');
                const ctx = {n: 2, s: "x", b: true};
                const g = [[{lt: ["n", 3]}, true], [{lte: ["n", 2]}, true], [{gt: ["n", 2]}, false], [{gte: ["n", 2]}, true],
                           [{eq: ["s", "x"]}, true], [{neq: ["s", "x"]}, false], [{and: [{eq: ["b", true]}, {lt: ["n", 1]}]}, false],
                           [{or: [{eq: ["b", true]}, {lt: ["n", 1]}]}, true], [{not: {eq: ["b", true]}}, false]];
                return {wrong: g.filter(([x, want]) => M.guard_fn(x)(ctx) !== want).map(([x]) => JSON.stringify(x)),
                        malformed: [{lt: "n"}, {xx: ["n", 1]}, {lt: ["n", 1], gt: ["n", 0]}, "n < 1"].map(x => M.guard_problem(x, ctx) !== null)}; }""")
            if cases["wrong"] or not all(cases["malformed"]):
                fail("guard table: %s" % cases)
            print("2.2.3 ok: 9 guard cases evaluate as written; 4 malformed guards are rejected")

            # 2.2.4, 2.2.6, 2.2.7: assign, highlight, guarded buttons
            send("fetch", "FETCH")
            for _ in range(3): send("fetch", "FAIL")
            if page.input_value("[data-instrument=fetch] input[name=retries]") != "3" or active("fetch") != "loading":
                fail("assign: retries %s, %s" % (page.input_value("[data-instrument=fetch] input[name=retries]"), active("fetch")))
            send("fetch", "FAIL")
            if active("fetch") != "failed":
                fail("the fourth FAIL went to %s" % active("fetch"))
            print("2.2.4 ok: three FAILs make retries 3; the fourth goes to failed")

            send("fetch", "RETRY")
            # 2.2.8 clicking a transition label sends its event, like its button
            page.locator("[data-instrument=fetch] .hc-sc-chart [data-hc-event=OK]").first.click()
            if active("fetch") != "ready ready__view__viewing ready__persist__saved":
                fail("clicking the OK label: %s" % active("fetch"))
            print("2.2.8 ok: clicking the OK label sends OK")
            send("fetch", "EDIT")
            want = "ready ready__view__editing ready__persist__saved"
            svg = page.eval_on_selector_all("[data-instrument=fetch] svg g.active", "gs => gs.map(g => g.dataset.uid).sort()")
            if active("fetch") != want or svg != sorted(want.split()):
                fail("highlight: data-active %s, svg %s" % (active("fetch"), svg))
            look = page.evaluate("""() => { const f = document.querySelector('[data-instrument=fetch]');
                const shape = id => f.querySelector(`g[data-uid="${id}"] rect`);
                const cs = e => getComputedStyle(e);
                const probe = document.createElement('span'); probe.style.color = 'var(--hc-accent)'; f.append(probe);
                const accent = cs(probe).color; probe.remove();
                const on = shape('ready__view__editing'), off = shape('ready__view__viewing');
                return {sameFill: cs(on).fill === cs(off).fill, wider: cs(on).strokeWidth !== cs(off).strokeWidth,
                        accent: cs(on).stroke === accent}; }""")
            if not all(look.values()):
                fail("highlight style: %s" % look)
            print("2.2.6, 2.2.6.1, 2.2.6.3 ok: active states outlined in the accent with fills unchanged; regions never listed")

            page.click("[data-instrument=fetch] .hc-sc-reset")
            page.uncheck("[data-instrument=fetch] input[name=online]")
            expect(page.locator("[data-instrument=fetch] button[data-event=FETCH]")).to_be_disabled()
            page.check("[data-instrument=fetch] input[name=online]")
            expect(page.locator("[data-instrument=fetch] button[data-event=FETCH]")).to_be_enabled()
            print("2.2.7, 2.2.10 ok: a guard turned false by the context panel disables its button")

            # 2.2.10: retries 3 in the panel makes FAIL go to failed
            send("fetch", "FETCH")
            page.fill("[data-instrument=fetch] input[name=retries]", "3"); page.dispatch_event("[data-instrument=fetch] input[name=retries]", "change")
            send("fetch", "FAIL")
            if active("fetch") != "failed":
                fail("retries 3 then FAIL: %s" % active("fetch"))
            print("2.2.10 ok: setting retries to 3 makes FAIL go to failed")

            # 2.2.9 log: A B C, back to A, redo to C, then A and a new event
            page.click("[data-instrument=fetch] .hc-sc-reset")
            if [c for c, _ in log("fetch")] != ["hc-sc-current"]:
                fail("2.2.9.1 Reset: %s" % log("fetch"))
            send("fetch", "FETCH"); send("fetch", "OK"); send("fetch", "EDIT")
            after_abc = active("fetch")
            page.click("[data-instrument=fetch] .hc-sc-log li:nth-child(2) button")
            if [c for c, _ in log("fetch")] != ["", "hc-sc-current", "hc-sc-later", "hc-sc-later"] or active("fetch") != "loading":
                fail("back to A: %s %s" % (log("fetch"), active("fetch")))
            page.click("[data-instrument=fetch] .hc-sc-log li:nth-child(4) button")
            if active("fetch") != after_abc:
                fail("redo to C: %s, want %s" % (active("fetch"), after_abc))
            page.click("[data-instrument=fetch] .hc-sc-log li:nth-child(2) button")
            send("fetch", "FAIL")
            if [t.split(" ")[0] for _, t in log("fetch")] != ["start", "FETCH", "FAIL"]:
                fail("new event after going back: %s" % log("fetch"))
            print("2.2.9, 2.2.9.1 ok: back greys later entries, redo restores, a new event drops them; Reset empties the log")

            # 2.2.9.2, 2.2.9.6: a panel edit is an entry, marked as an edit, holding its context
            reset("fetch"); edit("fetch", "retries", 3)
            if texts("fetch") != ["start → idle", "retries = 3 → idle"]:
                fail("2.2.9.2 edit entry: %s" % texts("fetch"))
            if "hc-sc-edit" not in log("fetch")[1][0].split() or "hc-sc-edit" in log("fetch")[0][0].split():
                fail("2.2.9.6 edit class: %s" % log("fetch"))
            entry("fetch", 1)
            if page.input_value(field("fetch", "retries")) != "0":
                fail("2.2.9.2 start after an edit: retries %s" % page.input_value(field("fetch", "retries")))
            entry("fetch", 2)
            if page.input_value(field("fetch", "retries")) != "3":
                fail("2.2.9.2 back to the edit: retries %s" % page.input_value(field("fetch", "retries")))
            print("2.2.9.2, 2.2.9.6 ok: an edit adds 'retries = 3 → idle', marked as an edit; stepping to it restores 3")

            # 2.2.9.3 merging
            reset("fetch")
            for v in [1, 2, 3]: edit("fetch", "retries", v)
            if texts("fetch") != ["start → idle", "retries = 3 → idle"]:
                fail("2.2.9.3 edits of one key: %s" % texts("fetch"))
            reset("fetch"); edit("fetch", "retries", 1); send("fetch", "FETCH"); edit("fetch", "retries", 2)
            if [t.split(" →")[0] for t in texts("fetch")] != ["start", "retries = 1", "FETCH", "retries = 2"]:
                fail("2.2.9.3 edit, event, edit: %s" % texts("fetch"))
            reset("fetch"); edit("fetch", "retries", 1); page.uncheck(field("fetch", "online"))
            if [t.split(" →")[0] for t in texts("fetch")] != ["start", "retries = 1", "online = false"]:
                fail("2.2.9.3 two keys: %s" % texts("fetch"))
            page.check(field("fetch", "online"))
            if [t.split(" →")[0] for t in texts("fetch")] != ["start", "retries = 1", "online = true"]:
                fail("2.2.9.3 re-edit of the last key: %s" % texts("fetch"))
            print("2.2.9.3 ok: edits of one key merge; an event or another key starts a new entry")

            # 2.2.9.7 an edit to the current value adds nothing
            reset("fetch"); edit("fetch", "retries", 0)
            if texts("fetch") != ["start → idle"]:
                fail("2.2.9.7 unchanged value: %s" % texts("fetch"))
            print("2.2.9.7 ok: setting retries to its current value adds no entry")

            # 2.2.9.4 stepping: edit entries restore their context; an edit drops the greyed entries
            reset("fetch"); send("fetch", "FETCH"); edit("fetch", "retries", 3); send("fetch", "FAIL")
            if active("fetch") != "failed":
                fail("2.2.9.4 FETCH, retries = 3, FAIL: %s" % active("fetch"))
            entry("fetch", 2)
            if page.input_value(field("fetch", "retries")) != "0":
                fail("2.2.9.4 back to FETCH: retries %s" % page.input_value(field("fetch", "retries")))
            entry("fetch", 3)
            if page.input_value(field("fetch", "retries")) != "3" or active("fetch") != "loading":
                fail("2.2.9.4 to the edit: retries %s, %s" % (page.input_value(field("fetch", "retries")), active("fetch")))
            entry("fetch", 2); edit("fetch", "retries", 2)
            if [t.split(" →")[0] for t in texts("fetch")] != ["start", "FETCH", "retries = 2"]:
                fail("2.2.9.4 edit after going back: %s" % texts("fetch"))
            print("2.2.9.4 ok: edit entries restore their context; an edit after going back drops the greyed entries")

            # 2.2.9.1 Reset drops edit entries and the edited values
            reset("fetch"); edit("fetch", "retries", 3); reset("fetch")
            if texts("fetch") != ["start → idle"] or page.input_value(field("fetch", "retries")) != "0":
                fail("2.2.9.1 Reset after an edit: %s, retries %s" % (texts("fetch"), page.input_value(field("fetch", "retries"))))
            print("2.2.9.1 ok: Reset empties the log, edits included, and restores the definition's values")

            # 2.2.9.5 an edit that turns an always-guard true: the entry shows XState's new state
            page.check(field("auto", "armed"))
            native = page.evaluate("""() => { const X = window.XState;
                const a = X.createActor(X.createMachine(%s)); a.start();
                a.send({type: "hc.context", patch: {armed: true}}); return a.getSnapshot().value; }""" % AUTO_NATIVE)
            if texts("auto") != ["start → idle", "armed = true → armed"] or active("auto") != native:
                fail("2.2.9.5 always after an edit: %s, %s, XState %s" % (texts("auto"), active("auto"), native))
            print("2.2.9.5 ok: 'armed = true' moves idle to armed, as XState does")

            # 2.2.5 delays: wait while paused, fire after resuming, cancelled by going back
            page.click("[data-instrument=fetch] .hc-sc-reset")
            send("fetch", "FETCH"); page.fill("[data-instrument=fetch] input[name=retries]", "3")
            page.dispatch_event("[data-instrument=fetch] input[name=retries]", "change")
            page.evaluate("window.htmlChannel.setPaused(true)")
            send("fetch", "FAIL")
            page.wait_for_timeout(1500)
            if active("fetch") != "failed":
                fail("a delay fired while paused: %s" % active("fetch"))
            page.evaluate("window.htmlChannel.setPaused(false)")
            wait_for(lambda: active("fetch") == "idle", 5, "the delay didn't fire after resuming")
            if not log("fetch")[-1][1].startswith("after 1000ms"):
                fail("the log doesn't show the delay: %s" % log("fetch")[-1])
            page.click("[data-instrument=fetch] .hc-sc-log li:nth-child(4) button")  # back to failed: a fresh timer
            page.click("[data-instrument=fetch] .hc-sc-log li:nth-child(2) button")  # then loading: no timer
            page.wait_for_timeout(1500)
            if active("fetch") != "loading":
                fail("going back didn't cancel the pending delay: %s" % active("fetch"))
            print("2.2.5 ok: a delay waits while paused, fires after resuming and shows in the log; going back cancels it")

            # 2.2.11 scrub and replay link; 2.2.15 one hc-statechart per transition
            page.click("[data-instrument=fetch] .hc-sc-reset")
            page.focus("#scrub"); page.keyboard.press("ArrowRight")
            if page.input_value("[data-instrument=fetch] input[name=retries]") != "1":
                fail("scrub didn't set retries")
            page.click("[data-instrument=fetch] .hc-sc-reset")
            if page.input_value("[data-instrument=fetch] input[name=retries]") != "1":
                fail("Reset dropped the scrub's value: retries %s" % page.input_value("[data-instrument=fetch] input[name=retries]"))
            print("review 4 ok: the scrub's value holds after Reset")
            page.click("#replay")
            replayed = [t.split(" ")[0] for _, t in log("fetch")]
            page.click("[data-instrument=fetch] .hc-sc-reset")
            for ev in ["FETCH", "FAIL", "FAIL"]: send("fetch", ev)
            if replayed != [t.split(" ")[0] for _, t in log("fetch")]:
                fail("replay link log %s differs from buttons" % replayed)
            n = page.evaluate("window.sc.length"); send("fetch", "OK")
            if page.evaluate("window.sc.length") != n + 1 or page.evaluate("window.sc.at(-1).event") != "OK":
                fail("hc-statechart: %s" % page.evaluate("window.sc.slice(-2)"))
            print("2.2.11, 2.2.11.1, 2.2.15 ok: the scrub sets retries; the replay link logs what the buttons log; one hc-statechart per change")

            # Q1 (b): a prose scrub changes the start and the log is replayed from it
            reset("fetch"); send("fetch", "FETCH"); send("fetch", "FAIL")  # the scrub holds retries 1, so FAIL makes 2
            if active("fetch") != "loading":
                fail("Q1 before scrubbing: %s" % active("fetch"))
            page.focus("#scrub"); page.keyboard.press("ArrowRight"); page.keyboard.press("ArrowRight")
            if texts("fetch")[-1] != "FAIL → failed" or log("fetch")[-1][0] != "hc-sc-current" or active("fetch") != "failed":
                fail("Q1 replay after scrubbing to 3: %s, %s" % (log("fetch"), active("fetch")))
            entry("fetch", 1)
            if page.input_value(field("fetch", "retries")) != "3":
                fail("Q1 start after scrubbing: retries %s" % page.input_value(field("fetch", "retries")))
            entry("fetch", 3)
            prose = lambda k, v: page.evaluate("([k, v]) => document.querySelector('[data-instrument=fetch]').dispatchEvent("
                                               "new CustomEvent('hc-param', {detail: {name: k, value: v}}))", [k, v])
            prose("online", False)
            if texts("fetch")[1] != "FETCH (no effect) → idle" or active("fetch") != "idle":
                fail("Q1 a blocked event after replay: %s, %s" % (texts("fetch"), active("fetch")))
            prose("online", True)
            if texts("fetch")[1] != "FETCH → loading":
                fail("Q1 unblocked again: %s" % texts("fetch"))
            print("Q1 ok: scrubbing retries to 3 replays the log (FAIL now ends in failed, cursor kept, start shows 3); "
                  "an event a prose value blocks is kept as 'no effect'")

            # 2.2.12 code list through code links; switching the format changes the hrefs
            hrefs = lambda: page.eval_on_selector_all("[data-instrument=fetch] .hc-sc-code a", "as => as.map(a => a.href)")
            if len(hrefs()) != 2 or not all(h.startswith("vscode://") for h in hrefs()):
                fail("code list: %s" % hrefs())
            page.click("#decide-code-links summary"); page.select_option("#decide-code-links select", "github")
            wait_for(lambda: all(h.startswith("https://github.com/acme/app/") for h in hrefs()), 5, "format switch: %s" % hrefs())
            # 2.2.12.1 clicking a state with references shows its entry
            page.click("[data-instrument=fetch] svg g[data-uid=idle]")
            expect(page.locator("[data-instrument=fetch] .hc-sc-code li[data-uid=idle]")).to_have_class("hc-sc-flash")
            print("2.2.12, 2.2.12.1 ok: 2 references link through code links and follow the format; clicking idle shows its entry")

            # 2.2.17 shared definition and collapse
            if page.locator("[data-instrument=small] svg [data-uid=ready__view__viewing]").count() != 0:
                fail("collapsed composite still drawn")
            send("small", "FETCH"); send("small", "OK")
            if active("small") != "ready":
                fail("collapsed: %s" % active("small"))
            print("2.2.17, 2.2.17.1 ok: the shared definition draws again; a collapsed composite is one state")

            # review 1, 8: keys with hyphens and spaces draw and run; a transition with an unknown target has no button
            if page.locator("[data-instrument=hyphen] svg [data-uid='east-west__flashing white']").count() != 1:
                fail("hyphen chart: no drawn state for 'flashing white'")
            if page.locator("[data-instrument=hyphen] button[data-event=LOST]").count() != 0:
                fail("a transition with an unknown target got a button")
            send("hyphen", "swap-flow"); send("hyphen", "warn-traffic")
            if active("hyphen") != "east-west east-west__flashing white":
                fail("hyphen chart: %s" % active("hyphen"))
            send("hyphen", "STOP")
            if page.eval_on_selector_all("[data-instrument=hyphen] svg g.active", "gs => gs.map(g => g.dataset.uid)") != ["all red"]:
                fail("hyphen chart highlight after STOP")
            print("review 1, 8 ok: keys with - and spaces draw in Mermaid and run; no button for a transition with a bad target")

            # review 1, 2, 9 in state-machine-cat: hyphenated keys, label clicks, state clicks
            if "hc-sc-smcat" not in page.get_attribute("[data-instrument=power] .hc-sc-chart", "class"):
                fail("power chart isn't drawn by state-machine-cat")
            page.locator("[data-instrument=power] .hc-sc-chart [data-hc-event=POWER] text").first.click()
            if active("power") != "power-on power-on__low":
                fail("clicking the POWER label: %s" % active("power"))
            send("power", "UP")
            page.locator("[data-instrument=power] .hc-sc-chart [data-hc-event=POWER] text").first.click()
            page.locator("[data-instrument=power] .hc-sc-chart [data-hc-event=POWER] text").first.click()
            if active("power") != "power-on power-on__high":
                fail("history through label clicks: %s" % active("power"))
            page.click("[data-instrument=power] .hc-sc-chart g[data-uid=power-on__low]")
            expect(page.locator("[data-instrument=power] .hc-sc-code li[data-uid=power-on__low]")).to_have_class("hc-sc-flash")
            print("review 2, 9 ok: in state-machine-cat, labels send their events, history holds, and a state click shows its code")

            # 2.2.6 renderer switch, 2.2.6.2, 2.2.14, 2.2.14.1 state-machine-cat colors
            if "hc-sc-smcat" not in page.get_attribute("[data-instrument=hist] .hc-sc-chart", "class") \
                    or "hc-sc-mermaid" not in page.get_attribute("[data-instrument=forced] .hc-sc-chart", "class"):
                fail("renderer switch")
            send("hist", "POWER"); send("hist", "UP"); send("hist", "POWER"); send("hist", "POWER")
            if active("hist") != "on on__high":
                fail("history: %s" % active("hist"))
            for scheme in ["light", "dark"]:
                page.emulate_media(color_scheme=scheme); page.wait_for_timeout(800)
                c = page.evaluate("""() => { const svg = document.querySelector('[data-instrument=keys] svg');
                    const tok = n => { const p = document.createElement('span'); p.style.color = `var(${n})`; document.body.append(p);
                                       const c = getComputedStyle(p).color; p.remove(); return c; };
                    const bad = [...svg.querySelectorAll('[fill], [stroke]')].filter(e => !e.closest('g.hc-sc-active') && !e.closest('.graph > polygon')).flatMap(e => {
                      const cs = getComputedStyle(e), out = [];
                      const f = (e.getAttribute('fill') || '').toLowerCase(), s = (e.getAttribute('stroke') || '').toLowerCase();
                      const inCluster = e.parentElement.matches('g.cluster');
                      if ((f === 'white' || f === '#ffffff') && cs.fill !== tok(inCluster ? '--hc-bg' : '--hc-card')) out.push('fill ' + e.tagName);
                      if (f === 'black' && cs.fill !== tok('--hc-fg')) out.push('fill ' + e.tagName);
                      if (s === 'black' && cs.stroke !== tok('--hc-fg')) out.push('stroke ' + e.tagName);
                      if (s === 'none' && cs.stroke !== 'none') out.push('stroke none ' + e.tagName);
                      return out; });
                    const nested = svg.querySelector('g.node.state.regular > path, g.node > polygon[fill]');
                    const cluster = svg.querySelector('g.cluster > path, g.cluster > polygon');
                    return {bad, differ: getComputedStyle(nested).fill !== getComputedStyle(cluster).fill,
                            parallelSuffix: svg.innerHTML.includes('.parallel'),
                            regionActive: [...svg.querySelectorAll('g.cluster.hc-sc-active > title')].map(t => t.textContent)}; }""")
                if c["bad"] or not c["differ"] or c["parallelSuffix"]:
                    fail("state-machine-cat colors in %s: %s" % (scheme, c))
            print("2.2.6, 2.2.6.2, 2.2.14, 2.2.14.1 ok: history switches to state-machine-cat; tokens in light and dark; no .parallel names")
            browser.close()

        # 2.3, 2.3.0, 2.3.7-2.3.10, 2.4.1, 2.2.5(c), 2.6.1: check_page on a plain page
        if os.environ.get("HTML_CHANNEL_CHROME") or shutil.which("google-chrome") or shutil.which("chromium") \
                or pathlib.Path("/Applications/Google Chrome.app").exists():
            r = mcp.call("check_page", timeout=240, page="broken")
            found = sorted(set((e["kind"], e.get("source")) for e in r["errors"]))
            want = sorted([("statechart", "statechart-json"), ("statechart", "statechart-target"),
                           ("statechart", "statechart-guard"), ("statechart", "statechart-unreachable"),
                           ("statechart", "statechart-history"), ("statechart", "statechart-machine"),
                           ("statechart", "statechart-instrument"), ("inert", "dead"), ("unprobed", "badjson"),
                           ("code-link", "app:/abs.ts")])
            if found != want:
                fail("check_page on the broken page:\n got  %s\n want %s" % (found, want))
            if not any("isn't a number of milliseconds" in e["message"] for e in r["errors"]):
                fail("a delay that isn't a number wasn't reported: %s" % [e["message"] for e in r["errors"]])
            print("2.3 ok: every statechart rule as kind statechart, a bad reference once as code-link; "
                  "the probe ran on a plain page (inert) and the explorable lint didn't")
            d = mcp.call("check_page", timeout=240, page="delay")
            if d["errors"]:
                fail("check_page on the delay page: %s" % d["errors"])
            print("2.2.5 ok: no delayed transition fired while the check's probe had the page paused")
            ex = mcp.call("check_page", timeout=240, page="example")
            if ex["errors"]:
                fail("check_page on the worked example: %s" % ex["errors"])
            print("2.4.1, 2.6.1 ok: the worked example checks clean; its charts aren't inert")
        else:
            print("check skipped: no Chrome")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

"""Moot: links between questions, owned by the kit (CONVENTION, Moot).

  .venv/bin/python test/moot.py

A page declares links in <script type="application/json" id="hc-moot">; the kit
folds and greys moot questions, greys and strikes through moot options, keeps
it current through every way an answer changes, never writes an answer, and
records the report-only `moot` map on Done. Problems are errors of kind moot.
"""
import json, re
from playwright.sync_api import sync_playwright, expect
from support import Sandbox, entries, fail, run, wait_for, write_page

PORT = 18796

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
"""

LINKS = {
    "evict": {"when": "cache=inproc", "reason": "you picked In-process for the cache"},
    "ttl": {"when": "evict=", "reason": "there's no eviction to time"},
    "rollout": {"when": "budget=", "reason": "there's no budget yet"},
    "size=large": {"when": "cache=inproc|memcached"},
    "size=small": {"when": "confirm=true region=eu", "reason": "small doesn't fit"},
}

PAGE = HEAD + """<h1>%s</h1>
<section data-question="cache"><h2>Cache</h2>
  <button data-decision="cache" data-choice="redis">Redis</button>
  <button data-decision="cache" data-choice="inproc">In-process</button>
  <button data-decision="cache" data-choice="memcached">Memcached</button>
  <button data-clear="cache">Clear cache</button>
</section>
<section data-question="evict"><h2>Eviction</h2>
  <p class="evidence">LRU is simple.</p>
  <label><input type="radio" name="evict" data-decision="evict" value="lru"> LRU</label>
  <label><input type="radio" name="evict" data-decision="evict" value="lfu"> LFU</label>
  <button data-clear="evict">Clear evict</button>
  <textarea data-decision="evict-note"></textarea>
</section>
<section data-question="ttl"><h2>TTL</h2>
  <input data-decision="ttl">
</section>
<section data-question="rollout"><h2>Rollout</h2>
  <label><input type="checkbox" data-decision="rollout"> Canary first</label>
</section>
<div>
  <button data-decision="size" data-choice="small">Small</button>
  <button data-decision="size" data-choice="large">Large</button>
</div>
<input data-decision="budget">
<label><input type="checkbox" data-decision="confirm"> Confirm</label>
<select data-decision="region"><option value="">-</option><option value="us">US</option><option value="eu">EU</option></select>
<button data-decision="status" data-choice="done">Done</button>
<script type="application/json" id="hc-moot">%s</script>
</body></html>"""

BROKEN = HEAD + """<h1>broken</h1>
<button data-decision="a" data-choice="x">X</button>
<select data-decision="sel"><option value="p">P</option></select>
<section data-question="c1"><h2>C1</h2><input data-decision="c1"></section>
<section data-question="c2"><h2>C2</h2><input data-decision="c2"></section>
<section data-question="ok"><h2>OK</h2><input data-decision="ok"></section>
<button data-decision="status" data-choice="done">Done</button>
<script type="application/json" id="hc-moot">{
  "ghost": {"when": "a=x", "reason": "r"},
  "ok": {"when": "phantom=1", "reason": "r"},
  "a=x": {"when": "a", "reason": "r"},
  "a=y": {"when": "a=x", "reason": "r"},
  "sel=p": {"when": "a=x", "reason": "r"},
  "a": {"when": "sel=p", "reason": "r"},
  "c1": {"when": "c2=", "reason": "r"},
  "c2": {"when": "c1=", "reason": "r"},
  "status": "not an object"
}</script>
<script type="application/json" id="hc-moot">{}</script>
</body></html>"""

BAD_JSON = HEAD + """<h1>bad json</h1>
<button data-decision="a" data-choice="x">X</button>
<button data-decision="status" data-choice="done">Done</button>
<script type="application/json" id="hc-moot">{"a": </script>
</body></html>"""

# B1 loose text, B2 data-question on a control, B3 a controller inside its dependent's
# container, B4 controls, a container's content and an option that page JS adds later
EDGES = HEAD + """<h1>edges</h1>
<button data-decision="a" data-choice="x">X</button>
<section data-question="loose"><h2>Loose</h2>Loose text that folds <input data-decision="loose"></section>
<select data-question="sel" data-decision="sel"><option value="1">one</option></select>
<section data-question="inner"><h2>Inner</h2><button data-decision="b" data-choice="x">BX</button><input data-decision="inner"></section>
<div id="slot"></div>
<section data-question="late"><h2>Late</h2><div id="slot2"></div></section>
<button data-decision="status" data-choice="done">Done</button>
<script type="application/json" id="hc-moot">{
  "loose": {"when": "a=x", "reason": "r"},
  "sel": {"when": "a=x", "reason": "r"},
  "inner": {"when": "b=x", "reason": "r"},
  "late": {"when": "src=x", "reason": "src picked x"},
  "src=y": {"when": "a=x", "reason": "a picked x"}
}</script>
<script>setTimeout(function () {
  document.getElementById("slot").innerHTML =
    '<button data-decision="src" data-choice="x">SX</button><button data-decision="src" data-choice="y">SY</button>';
  document.getElementById("slot2").innerHTML = '<input data-decision="late">';
}, 300)</script>
</body></html>"""

# the fixes for the open findings: checkbox terms, a title that isn't a direct child,
# radio targets, a repeated container, cycles dropping only their own links, an empty
# value in a list, and a hunk-choice diff inside a moot container (restore-within!)
FIXES = HEAD + """<h1>fixes</h1>
<label><input type="checkbox" data-decision="ok"> ok</label>
<section data-question="when-empty"><h2>When empty</h2><input data-decision="when-empty"></section>
<section data-question="when-false"><h2>When false</h2><input data-decision="when-false"></section>
<button data-decision="a" data-choice="x">X</button>
<section data-question="wrapped"><header><h2>Wrapped title</h2></header><p>wrapped evidence</p><input data-decision="wrapped"></section>
<details data-question="det" open><summary>Det summary</summary><h3>Det evidence heading</h3><p>det evidence</p><input data-decision="det"></details>
<details data-question="detc"><summary>Closed summary</summary><p>closed evidence</p><input data-decision="detc"></details>
<label><input type="checkbox" checked data-decision="keep"> keep, ticked in the markup</label>
<section data-question="keep-false"><h2>Keep false</h2><input data-decision="keep-false"></section>
<section data-question="keep-true"><h2>Keep true</h2><input data-decision="keep-true"></section>
<div>
  <label><input type="radio" name="r" data-decision="r" value="lab"> labelled</label>
  <input type="radio" name="r" id="rf" data-decision="r" value="for"><label for="rf">for label</label>
  <input type="radio" name="r" data-decision="r" value="bare"> bare
</div>
<section data-question="twice"><h2>Twice one</h2><input data-decision="twice"></section>
<section data-question="twice"><h2>Twice two</h2></section>
<section data-question="c1"><h2>C1</h2><input data-decision="c1"></section>
<section data-question="c2"><h2>C2</h2><input data-decision="c2"></section>
<section data-question="dep"><h2>Dep</h2><input data-decision="dep"></section>
<section data-question="hunky"><h2>Hunky</h2><input data-decision="hunky">
  <figure data-diff="fix" data-hunk-choices="accept reject" data-lang="text"><pre data-diff-old>old line</pre><pre data-diff-new>new line</pre></figure>
</section>
<button data-decision="status" data-choice="done">Done</button>
<script type="application/json" id="hc-moot">{
  "when-empty": {"when": "ok=", "reason": "ok unanswered"},
  "when-false": {"when": "ok=false", "reason": "ok unticked"},
  "wrapped": {"when": "a=x", "reason": "r"},
  "det": {"when": "a=x", "reason": "r"},
  "detc": {"when": "a=x", "reason": "closed reason"},
  "keep-false": {"when": "keep=false", "reason": "keep unticked"},
  "keep-true": {"when": "keep=true", "reason": "keep ticked"},
  "r=lab": {"when": "a=x", "reason": "r"},
  "r=for": {"when": "a=x", "reason": "r"},
  "r=bare": {"when": "a=x", "reason": "r"},
  "twice": {"when": "a=x", "reason": "r"},
  "c1": {"when": "c2=", "reason": "r"},
  "c2": {"when": "c1=", "reason": "r"},
  "dep": {"when": "c1=", "reason": "c1 is empty"},
  "hunky": {"when": "a=x", "reason": "r"},
  "a=x": {"when": "ok=true|", "reason": "r"}
}</script>
</body></html>"""

# a kit listener that throws: the answer is still sent, the listeners after it still run
GUARD = HEAD + """<h1>guard</h1>
<button data-decision="g" data-choice="x">X</button>
<section data-question="gq"><h2>GQ</h2><input data-decision="gq"></section>
<button data-decision="status" data-choice="done">Done</button>
<script type="application/json" id="hc-moot">{"gq": {"when": "g=x", "reason": "g picked x"}}</script>
<script type="module">
import("/_kit/decide/core.mjs").then(function (core) {
  core.on_answers_shown_BANG_(function () { throw new Error("boom"); });
  core.on_answers_shown_BANG_(function () { window.__after = (window.__after || 0) + 1; });
  window.__guarded = true;
});
</script>
</body></html>"""

MOOT = re.compile(r"\bhc-moot\b")
MOOT_OPTION = re.compile(r"\bhc-moot-option\b")


def page_html(title):
    return PAGE % (title, json.dumps(LINKS))


def question(page, id):
    return page.locator('[data-question="%s"]' % id)


def is_moot(page, id, label):
    expect(question(page, id)).to_have_class(MOOT, timeout=3000)
    print("ok: %s: %s is moot" % (label, id))


def not_moot(page, id, label):
    expect(question(page, id)).not_to_have_class(MOOT, timeout=3000)
    expect(question(page, id).locator(".hc-moot-bar")).to_have_count(0)
    print("ok: %s: %s is not moot" % (label, id))


def option(page, id, value):
    return page.locator('[data-decision="%s"][data-choice="%s"]' % (id, value))


def moot_errors(root, s, page_id):
    f = root / s / "errors.jsonl"
    lines = [json.loads(l) for l in f.read_text().splitlines()] if f.exists() else []
    return [e for e in lines if e["page"] == page_id and e["kind"] == "moot"]


def ids_recorded(root, s, page_id):
    return [e["id"] for e in entries(root, s) if e["page"] == page_id]


def main():
    sb = Sandbox(PORT)
    try:
        root = sb.project("proj")
        s = "moot"
        write_page(root, s, "links", page_html("v1"))
        write_page(root, s, "broken", BROKEN)
        write_page(root, s, "badjson", BAD_JSON)
        write_page(root, s, "edges", EDGES)
        write_page(root, s, "fixes", FIXES)
        write_page(root, s, "guard", GUARD)
        sb.serve(root)
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()
            page.goto(sb.url(root, s, "links"))
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)

            # at start: empty fields and unanswered questions already count
            not_moot(page, "evict", "start")
            is_moot(page, "ttl", "start, evict unanswered")
            is_moot(page, "rollout", "start, budget empty")
            expect(question(page, "ttl").locator("[data-decision=ttl]")).to_be_hidden()
            expect(question(page, "ttl").locator("h2")).to_be_visible()

            # a button choice greys a question, folds it to its heading and reason, and greys an option
            page.click("button[data-choice=inproc]")
            is_moot(page, "evict", "choose a button")
            expect(question(page, "evict").locator(".hc-moot-reason")).to_have_text("Moot: you picked In-process for the cache")
            expect(question(page, "evict").locator("p.evidence")).to_be_hidden()
            expect(question(page, "evict").locator("input[value=lru]")).to_be_hidden()
            expect(option(page, "size", "large")).to_have_class(MOOT_OPTION)
            reason = page.locator('[data-choice="large"] + .hc-moot-option-reason')
            expect(reason).to_have_text("Moot: “Cache” is “In-process”")
            print("ok: a missing reason is written from the controlling heading and label")

            # opened, a moot question stays greyed, and its controls, Clear and note still save
            toggle = question(page, "evict").locator(".hc-moot-toggle")
            expect(toggle).to_have_attribute("aria-expanded", "false")
            toggle.click()
            expect(toggle).to_have_attribute("aria-expanded", "true")
            expect(question(page, "evict").locator("input[value=lru]")).to_be_visible()
            page.check("input[value=lfu]")
            page.fill("[data-decision=evict-note]", "still LFU")
            page.click("h1")
            wait_for(lambda: "evict-note" in ids_recorded(root, s, "links"), 5, "note in a moot question not saved")
            page.click("button[data-clear=evict]")
            wait_for(lambda: [e for e in entries(root, s) if e["id"] == "evict"][-1]["value"] is None, 5,
                     "Clear in a moot question not saved")
            is_moot(page, "ttl", "chain: evict cleared")
            print("ok: an opened moot question saves its choice, note and Clear")

            # the 7 ways an answer changes, and the term × control table
            page.click("button[data-choice=inproc]")
            not_moot(page, "evict", "click the chosen button again")
            expect(option(page, "size", "large")).not_to_have_class(MOOT_OPTION)
            expect(page.locator(".hc-moot-option-reason")).to_have_count(0)
            page.click("button[data-choice=memcached]")
            expect(option(page, "size", "large")).to_have_class(MOOT_OPTION)
            print("ok: a term with several values matches any of them")
            page.click("button[data-clear=cache]")
            expect(option(page, "size", "large")).not_to_have_class(MOOT_OPTION)
            print("ok: Clear on a button question re-evaluates")
            page.evaluate("htmlChannel.record('cache', 'inproc')")
            is_moot(page, "evict", "htmlChannel.record")
            expect(question(page, "evict").locator(".hc-moot-toggle")).to_have_attribute("aria-expanded", "false")
            print("ok: a question that becomes moot again starts folded")
            question(page, "evict").locator(".hc-moot-toggle").click()
            page.check("input[value=lru]")  # a radio change, while evict is moot
            is_moot(page, "ttl", "chain: a moot question reads as unanswered")
            page.evaluate("htmlChannel.record('cache', 'redis')")
            not_moot(page, "evict", "htmlChannel.record again")
            not_moot(page, "ttl", "chain: evict answered lru again")
            page.fill("[data-decision=budget]", "4 GB")
            page.click("h1")
            not_moot(page, "rollout", "edit a field")
            page.check("[data-decision=confirm]")
            expect(option(page, "size", "small")).to_have_class(MOOT_OPTION)
            print("ok: a checkbox term reads true")
            page.uncheck("[data-decision=confirm]")
            expect(option(page, "size", "small")).not_to_have_class(MOOT_OPTION)
            page.select_option("[data-decision=region]", "eu")
            expect(option(page, "size", "small")).to_have_class(MOOT_OPTION)
            print("ok: a select term, and any one of several terms, matches")

            # a moot option can still be chosen, and shows both markers
            page.click("button[data-choice=small]")
            expect(option(page, "size", "small")).to_have_class(re.compile(r"\bchosen\b"))
            wait_for(lambda: [e for e in entries(root, s) if e["id"] == "size"][-1:] and
                     [e for e in entries(root, s) if e["id"] == "size"][-1]["value"] == "small", 5,
                     "a moot option couldn't be chosen")
            print("ok: a moot option can still be chosen and shows chosen and moot")

            # answers stay untouched: nothing but the reader's own answers was recorded
            got = set(ids_recorded(root, s, "links"))
            mine = {"cache", "evict", "evict-note", "budget", "confirm", "region", "size"}
            if got != mine:
                fail("moot wrote answers: recorded %s, expected only %s" % (sorted(got), sorted(mine)))
            print("ok: moot never records an answer")

            # a reload restores answers, and moot follows them
            page.evaluate("htmlChannel.record('cache', 'inproc')")
            wait_for(lambda: [e for e in entries(root, s) if e["id"] == "cache"][-1]["value"] == "inproc", 5, "cache not saved")
            page.reload()
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            is_moot(page, "evict", "a reload")
            is_moot(page, "ttl", "a reload, chain")
            not_moot(page, "rollout", "a reload, budget restored")

            # a new revision carries the answers over; moot follows; Clear carried answers undoes it
            write_page(root, s, "links", page_html("v2"))
            expect(page.locator("h1")).to_have_text("v2", timeout=4500)
            is_moot(page, "evict", "carried answers")
            page.click("#decide-clear-carried")
            not_moot(page, "evict", "Clear carried answers")
            is_moot(page, "rollout", "Clear carried answers, budget empty")

            # Done records the moot map, with the reasons shown, before status
            page.click("button[data-choice=inproc]")
            is_moot(page, "evict", "before Done")
            page.click("button[data-choice=done]")
            wait_for(lambda: "status" in [e["id"] for e in entries(root, s) if e["page"] == "links" and e["rev"] == 2],
                     5, "Done not saved")
            rev2 = [e for e in entries(root, s) if e["page"] == "links" and e["rev"] == 2]
            ids = [e["id"] for e in rev2]
            if "moot" not in ids or ids.index("moot") > ids.index("status"):
                fail("moot not recorded before status: %s" % ids)
            m = [e for e in rev2 if e["id"] == "moot"][-1]["value"]
            want = {"evict": "you picked In-process for the cache", "ttl": "there's no eviction to time",
                    "rollout": "there's no budget yet", "size=large": "“Cache” is “In-process”"}
            if m != want:
                fail("moot map %s, expected %s" % (m, want))
            print("ok: Done records the moot map with the reasons shown, before status")

            if [(e["rev"], e["message"]) for e in moot_errors(root, s, "links")] != \
                    [(rev, 'the link "size=large" has no "reason", so the kit writes one') for rev in (1, 2)]:
                fail("links page errors: %s" % moot_errors(root, s, "links"))
            print("ok: a missing reason is one moot error per revision")

            # every problem in a list is one moot error; usable links still work
            page.goto(sb.url(root, s, "broken"))
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            wait_for(lambda: len(moot_errors(root, s, "broken")) >= 9, 5, "broken list: errors missing")
            msgs = sorted(e["message"] for e in moot_errors(root, s, "broken"))
            expected = sorted([
                'the link "ghost" names "ghost", which no control on the page answers',
                'the link "ok": its "when" reads "phantom", which no control on the page answers',
                'the link "a=x": the term "a" in its "when" has no =',
                'the link "a=y": question "a" has no button or radio with the value "y"',
                'the link "sel=p": "sel" is a select: only button and radio options can be moot',
                'the link "a": no element has data-question="a" to fold',
                'the links "c1", "c2" form a cycle, so they\'re dropped',
                'the link "status" needs {"when": "…", "reason": "…"}',
                'a page has one link list, <script id="hc-moot">; found 2, so only the first is read',
            ])
            if msgs != expected:
                fail("broken list errors:\n%s\nexpected:\n%s" % ("\n".join(msgs), "\n".join(expected)))
            not_moot(page, "c1", "a cycle member")
            print("ok: each problem in a list is exactly one moot error")

            page.goto(sb.url(root, s, "badjson"))
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            wait_for(lambda: moot_errors(root, s, "badjson"), 5, "bad JSON not reported")
            if not moot_errors(root, s, "badjson")[0]["message"].startswith("the link list isn't a JSON object: "):
                fail("bad JSON error: %s" % moot_errors(root, s, "badjson"))
            page.click("button[data-choice=done]")
            wait_for(lambda: "status" in ids_recorded(root, s, "badjson"), 5, "Done not saved")
            if "moot" in ids_recorded(root, s, "badjson"):
                fail("a list that doesn't parse recorded a moot map")
            print("ok: a list that isn't JSON is one error, and Done records no moot map")

            # B2, B3: wrong containers are errors once the page settles; B4: late controls aren't
            page.goto(sb.url(root, s, "edges"))
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            wait_for(lambda: len(moot_errors(root, s, "edges")) >= 2, 5, "edges: container errors not reported")
            page.wait_for_timeout(500)
            msgs = sorted(e["message"] for e in moot_errors(root, s, "edges"))
            expected = sorted([
                'the link "sel": data-question="sel" is on a control; put it on an element around the question',
                'the link "inner": its data-question container holds the controls of "b", which its "when" reads, '
                'so folding would hide them; move them out',
            ])
            if msgs != expected:
                fail("edges errors:\n%s\nexpected:\n%s" % ("\n".join(msgs), "\n".join(expected)))
            print("ok: a container on a control, or around its own controller, is one error each; late controls none")

            page.click("button[data-choice=x][data-decision=a]")
            is_moot(page, "loose", "B1")
            expect(page.get_by_text("Loose text that folds")).to_be_hidden()
            print("ok: B1 loose text in a folded container is hidden")
            expect(page.locator("select[data-question=sel]")).not_to_have_class(MOOT)
            expect(page.locator("select .hc-moot-bar")).to_have_count(0)
            print("ok: B2 a container on a control never shows")
            page.click("button[data-decision=b][data-choice=x]")
            not_moot(page, "inner", "B3 controller inside")
            expect(page.locator("button[data-decision=b]")).to_be_visible()
            expect(option(page, "src", "y")).to_have_class(MOOT_OPTION)
            print("ok: B4 an option page JS added later goes moot")
            page.click("button[data-decision=src][data-choice=x]")
            is_moot(page, "late", "B4 late controller and container content")
            page.evaluate("document.getElementById('slot').innerHTML = ''")
            not_moot(page, "late", "B4 controls removed")
            print("ok: a link whose controls go away shows nothing again")

            page.goto(sb.url(root, s, "fixes"))
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            # a checkbox never answered matches ID= and ID=false
            is_moot(page, "when-empty", "checkbox never answered, ok=")
            is_moot(page, "when-false", "checkbox never answered, ok=false")
            page.check("[data-decision=ok]")
            not_moot(page, "when-empty", "checkbox ticked, ok=")
            not_moot(page, "when-false", "checkbox ticked, ok=false")
            page.uncheck("[data-decision=ok]")
            not_moot(page, "when-empty", "checkbox answered false, ok=")
            is_moot(page, "when-false", "checkbox answered false, ok=false")
            # a checkbox ticked in the markup and never answered matches what it shows
            not_moot(page, "keep-false", "checkbox ticked in the markup, keep=false")
            is_moot(page, "keep-true", "checkbox ticked in the markup, keep=true")
            # a cycle drops only its own links: dep reads c1 as answered
            is_moot(page, "dep", "dependent of a cycle, c1 empty")
            not_moot(page, "c1", "a cycle member")
            page.fill("[data-decision=c1]", "1")
            page.click("h1")
            not_moot(page, "dep", "dependent of a cycle, c1 answered")

            page.click("button[data-decision=a][data-choice=x]")
            is_moot(page, "wrapped", "a title inside a wrapper")
            expect(page.get_by_text("Wrapped title")).to_be_visible()
            expect(page.get_by_text("wrapped evidence")).to_be_hidden()
            is_moot(page, "det", "a details container")
            expect(page.get_by_text("Det summary")).to_be_visible()
            expect(page.get_by_text("Det evidence heading")).to_be_hidden()
            expect(page.get_by_text("det evidence", exact=True)).to_be_hidden()
            print("ok: a details container keeps its summary, not a heading in its content")
            is_moot(page, "detc", "a closed details container")
            expect(page.get_by_text("Moot: closed reason")).to_be_visible()
            expect(page.get_by_text("closed evidence")).to_be_hidden()
            question(page, "detc").locator(".hc-moot-toggle").click()
            expect(page.get_by_text("closed evidence")).to_be_visible()
            print("ok: a closed details container shows its reason, and Show opens it")
            print("ok: the child holding the title stays visible when folded")
            got = page.evaluate("[...document.querySelectorAll('.hc-moot-option')].map(e => e.tagName + ':' + e.textContent.trim())")
            if got != ["LABEL:labelled", "LABEL:for label", "INPUT:"]:
                fail("radio targets: %s" % got)
            print("ok: radio options grey their label, a wrapping one or label[for]; a bare radio greys alone")
            expect(page.locator('[data-question="twice"]').first).to_have_class(MOOT)
            expect(page.locator('[data-question="twice"]').nth(1)).not_to_have_class(MOOT)
            print("ok: of two containers with one ID, the first folds")

            wait_for(lambda: len(moot_errors(root, s, "fixes")) >= 4, 5, "fixes: errors not reported")
            page.wait_for_timeout(300)
            msgs = sorted(e["message"] for e in moot_errors(root, s, "fixes"))
            expected = sorted([
                'the links "c1", "c2" form a cycle, so they\'re dropped',
                'the link "a=x": the term "ok=true|" in its "when" has an empty value; '
                'to match an unanswered question, write "ok=" as a term of its own',
                'the link "r=bare": the radio "bare" has no <label>, so only the radio greys; wrap it and its text in a <label>',
                'the link "twice": 2 elements have data-question="twice"; only the first folds',
            ])
            if msgs != expected:
                fail("fixes errors:\n%s\nexpected:\n%s" % ("\n".join(msgs), "\n".join(expected)))
            print("ok: a cycle, an empty value, a bare radio and a repeated container are one error each")

            # a hunk choice inside a moot container: answered while open, restored folded
            is_moot(page, "hunky", "a container holding a diff")
            question(page, "hunky").locator(".hc-moot-toggle").click()
            accept = question(page, "hunky").locator("button[data-choice=accept]")
            accept.click()
            wait_for(lambda: any(e["id"].startswith("fix.") and e["value"] == "accept" for e in entries(root, s)), 5,
                     "hunk choice in a moot container not saved")
            page.reload()
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            is_moot(page, "hunky", "a reload, diff inside")
            expect(accept).to_be_hidden()
            question(page, "hunky").locator(".hc-moot-toggle").click()
            expect(accept).to_have_class(re.compile(r"\bchosen\b"))
            print("ok: a hunk choice in a moot container is restored and folded with it")

            # a kit listener that throws doesn't stop saving or the listeners after it
            page.goto(sb.url(root, s, "guard"))
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            wait_for(lambda: page.evaluate("window.__guarded === true"), 5, "guard listeners not registered")
            page.click("button[data-decision=g][data-choice=x]")
            is_moot(page, "gq", "after a throwing listener")
            wait_for(lambda: "g" in ids_recorded(root, s, "guard"), 1.5, "answer not sent at once after a listener threw")
            if not page.evaluate("window.__after >= 1"):
                fail("the listener after the throwing one didn't run")
            f = root / s / "errors.jsonl"
            wait_for(lambda: any(json.loads(l)["page"] == "guard" and json.loads(l)["message"] == "a kit listener failed: boom"
                                 for l in f.read_text().splitlines()), 5, "the listener's throw wasn't reported")
            print("ok: a throwing listener is a js error; the answer is sent and later listeners run")
            browser.close()
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

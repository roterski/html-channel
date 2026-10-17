"""Rich pages: error reporting, visualization adapters, depth tiers, Deeper,
page-JS answers, check_page and channel events.

  .venv/bin/python test/rich.py

Mermaid loads from jsDelivr, so this test needs network access.
"""
import json, os, pathlib, shutil, threading, time, urllib.request
from playwright.sync_api import sync_playwright, expect
from support import Mcp, Sandbox, entries, fail, run, wait_for, write_page

PORT = 18785

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
%s
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
%s
</body></html>"""

BROKEN = "http://127.0.0.1:1/missing.js"

VIZ = HEAD % ('<meta name="hc-libs" content="mermaid">\n<script src="%s"></script>' % BROKEN, """
<pre data-viz="mermaid">graph LR
  A --> B</pre>
<pre data-viz="mermaid">graph LR
  A -->> </pre>
<pre data-viz="vega-lite">{}</pre>
<button data-decision="status" data-choice="done">Done</button>
""")

TIERS = HEAD % ('<meta name="hc-tiers" content="Overview,Shape,Detail">', """
<p id="always">untagged</p>
<section data-tier="0" id="t0">overview</section>
<section data-tier="1" id="t1">shape</section>
<section data-tier="2" id="t2"><p id="deep">detail</p></section>
""")

STYLED = HEAD % ("<style>button { background-color: rgb(1, 2, 3); }</style>", """
<button data-decision="pick" data-choice="a">A</button>
<button data-decision="pick" data-choice="b">B</button>
""")

NOADAPTER = HEAD % ('<meta name="hc-libs" content="nope">', """
<pre data-viz="nope">one</pre>
<pre data-viz="nope">two</pre>
""")

# page JS answers before the deferred runtime starts
EARLY = HEAD % ("", """
<input data-decision="viarecord" id="viarecord"><input data-decision="viaevent" id="viaevent">
<script>
  window.htmlChannel.record("viarecord", "early");
  var e = document.getElementById("viaevent");
  e.value = "early"; e.dispatchEvent(new Event("change", {bubbles: true}));
</script>
""")

# a container-width chart (inside a fold), and a broken spec in another fold
VEGAFOLD = HEAD % ('<meta name="hc-libs" content="vega-lite">', """
<details id="fold"><summary>chart</summary>
<pre data-viz="vega-lite">{"width":"container","data":{"values":[{"a":"x","b":3},{"a":"y","b":5}]},
 "mark":"bar","encoding":{"x":{"field":"a","type":"nominal"},"y":{"field":"b","type":"quantitative"}}}</pre>
</details>
<details><summary>broken</summary><pre data-viz="vega-lite">{"mark": "nope"}</pre></details>
""")

# a dialog during load would block headless Chrome
DIALOG = HEAD % ("<script>alert('hello'); window.ok = confirm('sure?');</script>", "<p>dialog</p>")

# required markup missing: no boot.js, so nothing reports the failed load
BARE = """<!doctype html><html><head><meta charset="utf-8">
<script src="%s"></script></head><body>bare</body></html>""" % BROKEN

DEEPER = HEAD % ("", """
<h1 id="v%d">v%d</h1>
<button data-deeper="cost">More on cost</button>
<button data-deeper="risk">More on risk</button>
<input data-decision="picked" id="picked">
<button data-decision="status" data-choice="done">Done</button>
""")


def by_kind(errors, kind):
    return [e for e in errors if e["kind"] == kind]


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()
        mcp = Mcp(sb, project, "--max-wait", "3")
        init = mcp.init()
        if "experimental" in init["result"]["capabilities"] or "instructions" in init["result"]:
            fail("channel capability declared without --channel: %s" % init["result"])
        setup = mcp.call("channel_setup")
        s, root = setup["session"], project / "ai/html-channel"
        base = setup["url_base"]
        write_page(root, s, "viz", VIZ)
        write_page(root, s, "tiers", TIERS)
        write_page(root, s, "deeper", DEEPER % (1, 1))
        write_page(root, s, "bare", BARE)
        write_page(root, s, "styled", STYLED)
        write_page(root, s, "noadapter", NOADAPTER)
        write_page(root, s, "early", EARLY)
        write_page(root, s, "vegafold", VEGAFOLD)
        write_page(root, s, "race", "<p>race</p>")
        write_page(root, s, "dialog", DIALOG)
        wait_for(lambda: sb.url(root, s, "viz") is not None, 15, "server not up")

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()

            # 3.3, 4.2, 4.3: failed load, a broken and a working diagram, an undeclared block
            page.goto(base + "viz.html")
            expect(page.locator(".hc-viz svg")).to_have_count(1, timeout=20000)
            expect(page.locator(".hc-viz-error")).to_have_count(1)
            expect(page.locator("pre[data-viz=mermaid]").nth(0)).to_be_hidden()
            expect(page.locator("pre[data-viz=mermaid]").nth(1)).to_be_visible()
            if page.locator("text=Syntax error").count():
                fail("mermaid drew its syntax-error graphic")
            wait_for(lambda: len(mcp.call("get_decisions", page="viz")["errors"]) >= 3, 10, "errors not reported")
            errs = mcp.call("get_decisions", page="viz")["errors"]
            load, viz, und = by_kind(errs, "load"), by_kind(errs, "viz"), by_kind(errs, "undeclared")
            if not any(e["source"] == BROKEN for e in load):
                fail("failed CDN load not reported: %s" % errs)
            if not (len(viz) == 1 and viz[0]["source"] == "mermaid#1" and "Parse error" in viz[0]["message"]):
                fail("broken mermaid block not reported as viz #1: %s" % errs)
            if not (len(und) == 1 and und[0]["source"] == "vega-lite"):
                fail("undeclared vega-lite block not reported: %s" % errs)
            print("errors ok: load, viz (mermaid#1) and undeclared reported; block 0 rendered")

            # 6.2: tiers open at level 0, the switch reveals, fragment navigation reveals and raises
            page.goto(base + "tiers.html")
            expect(page.locator("#decide-tiers")).to_be_visible()
            expect(page.locator("#always")).to_be_visible()
            expect(page.locator("#t0")).to_be_visible()
            expect(page.locator("#t1")).to_have_attribute("hidden", "until-found")
            expect(page.locator("#t2")).to_be_hidden()
            page.click("#decide-tiers button:has-text('Shape')")
            expect(page.locator("#t1")).to_be_visible()
            expect(page.locator("#t2")).to_be_hidden()
            page.click("#decide-tiers button:has-text('Overview')")
            expect(page.locator("#t1")).to_be_hidden()
            page.evaluate("location.hash = 'deep'")
            expect(page.locator("#t2")).to_be_visible()
            expect(page.locator("#t1")).to_be_visible()
            expect(page.locator("#decide-tiers button:has-text('Detail')")).to_have_attribute("aria-pressed", "true")
            print("tiers ok: level 0 first, switch shows and hides, a fragment match raises the level to 2")

            # 7.2: Deeper ends ask_human with only the targets new since its last return
            page.goto(base + "deeper.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)

            def ask_while(click):
                result = {}
                t = threading.Thread(target=lambda: result.update(mcp.call("ask_human", timeout=20, page="deeper")))
                t.start()
                time.sleep(0.5)
                click and page.click(click)
                t.join()
                return result

            first = ask_while("button[data-deeper=cost]")
            if first.get("status") != "deeper" or first.get("deeper") != ["cost"]:
                fail("ask_human after Deeper on cost: %s" % first)
            idle = ask_while(None)
            if idle.get("status") != "waiting" or idle.get("deeper") != []:
                fail("ask_human again without a rewrite should wait with no new targets: %s" % idle)
            second = ask_while("button[data-deeper=risk]")
            if second.get("status") != "deeper" or second.get("deeper") != ["risk"]:
                fail("second Deeper should report only risk: %s" % second)
            result = {}
            write_page(root, s, "deeper", DEEPER % (2, 2))
            expect(page.locator("#v2")).to_be_visible(timeout=4500)
            expect(page.locator("button[data-deeper=cost]")).not_to_have_class("chosen")
            again = mcp.call("ask_human", timeout=20, page="deeper")
            if again["status"] != "waiting" or again["deeper"]:
                fail("Deeper carried over to rev 2: %s" % again)
            print("deeper ok: cost, then a wait, then only risk at rev 1; rev 2 waits again")

            # 6: page styles restyle buttons; the chosen marker still shows
            page.goto(base + "styled.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            bg = lambda sel: page.eval_on_selector(sel, "e => getComputedStyle(e).backgroundColor")
            if bg("button[data-choice=a]") != "rgb(1, 2, 3)":
                fail("page style lost to the kit: %s" % bg("button[data-choice=a]"))
            page.click("button[data-choice=a]")
            expect(page.locator("button[data-choice=a]")).to_have_class("chosen")
            if bg("button[data-choice=a]") == "rgb(1, 2, 3)":
                fail("chosen marker hidden by the page style")
            print("styles ok: page button style wins, chosen marker still shows")

            # 9: an adapter that doesn't load marks its blocks and reports once
            page.goto(base + "noadapter.html")
            expect(page.locator(".hc-viz-error")).to_have_count(2, timeout=10000)
            wait_for(lambda: mcp.call("get_decisions", page="noadapter")["errors"], 10, "adapter failure not reported")
            errs = mcp.call("get_decisions", page="noadapter")["errors"]
            if [(e["kind"], e["source"]) for e in errs] != [("viz", "nope")]:
                fail("adapter failure reports: %s" % errs)
            print("adapter ok: both blocks show a notice, one viz error")

            # page JS answers before the runtime starts are saved, not lost
            page.goto(base + "early.html")
            wait_for(lambda: {(e["id"], e["value"]) for e in entries(root, s) if e["page"] == "early"}
                     >= {("viarecord", "early"), ("viaevent", "early")}, 10, "early answers not saved")
            if mcp.call("get_decisions", page="early")["errors"]:
                fail("early answers raised errors: %s" % mcp.call("get_decisions", page="early")["errors"])
            print("early ok: record() and a change event from an inline script were both saved")

            # a container-width chart fills its column, also inside a fold; a broken spec is reported
            page.goto(base + "vegafold.html")
            wait_for(lambda: any(e["kind"] == "viz" for e in mcp.call("get_decisions", page="vegafold")["errors"]),
                     20, "broken spec not reported")
            page.click("#fold summary")
            expect(page.locator("#fold .hc-viz svg")).to_have_count(1, timeout=10000)
            width = page.eval_on_selector("#fold .hc-viz svg", "e => e.getBoundingClientRect().width")
            if width < 100:
                fail("container-width chart drew %s px wide" % width)
            print("chart ok: a container-width chart drew %d px wide; the broken spec was reported" % width)

            page.goto(base + "deeper.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)

            # 1.3: page JS records through window.htmlChannel.record
            page.evaluate("window.htmlChannel.record('picked', 'from-js')")
            expect(page.locator("#picked")).to_have_value("from-js")
            wait_for(lambda: any(e["id"] == "picked" and e["value"] == "from-js" for e in entries(root, s)),
                     5, "htmlChannel.record not saved")
            print("record ok: window.htmlChannel.record saved an answer")

            # 7.4: with --channel, a Done nobody waits on becomes a channel event
            ch = Mcp(sb, project, "--channel")
            init = ch.init()
            caps = init["result"]["capabilities"]
            if caps.get("experimental") != {"claude/channel": {}} or "instructions" not in init["result"]:
                fail("--channel didn't declare the capability: %s" % init["result"])
            ch.call("channel_setup", session=s)
            page.click("button[data-choice=done]")
            end, event = time.time() + 10, None
            while time.time() < end and event is None:
                try:
                    m = ch.msgs.get(timeout=0.5)
                except Exception:
                    continue
                if m.get("method") == "notifications/claude/channel":
                    event = m["params"]
            if not event or event["meta"] != {"page": "deeper", "session": s, "rev": "2", "status": "done"}:
                fail("no channel event for Done: %s" % event)
            print("channel ok: Done on an unwatched page sent %s" % event["meta"])

            # ... but not for a click an ask_human call answered
            write_page(root, s, "deeper", DEEPER % (3, 3))
            expect(page.locator("#v3")).to_be_visible(timeout=4500)
            result.clear()
            t = threading.Thread(target=lambda: result.update(ch.call("ask_human", timeout=20, page="deeper")))
            t.start()
            time.sleep(0.5)
            page.click("button[data-deeper=risk]")
            t.join()
            if result.get("status") != "deeper":
                fail("ask_human with --channel: %s" % result)
            end = time.time() + 3
            while time.time() < end:
                try:
                    m = ch.msgs.get(timeout=0.5)
                except Exception:
                    continue
                if m.get("method") == "notifications/claude/channel":
                    fail("channel event for a click ask_human already returned: %s" % m)
            print("channel ok: no event for a Deeper that ask_human answered")
            browser.close()

        # Deeper clicks racing ask_human returns: each target returned exactly once
        def click(i):
            req = urllib.request.Request(base + "_decisions", method="POST",
                                         data=json.dumps({"page": "race", "id": "deeper", "value": "t%d" % i}).encode())
            urllib.request.urlopen(req, timeout=5).close()
        clicker = threading.Thread(target=lambda: [(click(i), time.sleep(0.003)) for i in range(200)])
        clicker.start()
        got = []
        while True:
            r = mcp.call("ask_human", timeout=10, page="race")
            got += r["deeper"]
            if not clicker.is_alive() and r["status"] == "waiting":
                break
        if sorted(got) != sorted("t%d" % i for i in range(200)):
            fail("race: %d returned, %d distinct, missing %s" % (len(got), len(set(got)),
                 sorted(set("t%d" % i for i in range(200)) - set(got))[:10]))
        print("race ok: 200 Deeper clicks during ask_human loops, each returned exactly once")

        # 5: check_page renders light and dark and returns this check's errors
        if os.environ.get("HTML_CHANNEL_CHROME") or shutil.which("google-chrome") or shutil.which("chromium") \
                or pathlib.Path("/Applications/Google Chrome.app").exists():
            r = mcp.call("check_page", timeout=120, page="viz")
            if not r["chrome"] or sorted(r["screenshots"]) != ["dark", "light"]:
                fail("check_page: %s" % r)
            for p in r["screenshots"].values():
                if not pathlib.Path(p).stat().st_size:
                    fail("empty screenshot %s" % p)
            kinds = sorted({e["kind"] for e in r["errors"]})
            if kinds != ["load", "undeclared", "viz"]:
                fail("check_page errors: %s" % r["errors"])
            if (root / s / ".check/.gitignore").read_text() != "*\n":
                fail(".check isn't git-ignored")
            print("check ok: light and dark screenshots, errors %s" % kinds)
            bare = mcp.call("check_page", timeout=120, page="bare")
            if [e["kind"] for e in bare["errors"]] != ["no-reporter"]:
                fail("check_page on a page without boot.js: %s" % bare["errors"])
            print("check ok: a page without boot.js gets no-reporter, not a clean result")
            t0 = time.time()
            dlg = mcp.call("check_page", timeout=120, page="dialog")
            msgs = sorted(e["message"] for e in dlg["errors"] if e["kind"] == "dialog")
            if len(msgs) != 2 or not msgs[0].startswith("alert() would block the page: hello") \
                    or not msgs[1].startswith("confirm() would block the page: sure?"):
                fail("check_page on a page with dialogs: %s" % dlg["errors"])
            if time.time() - t0 > 30:
                fail("check_page on a page with dialogs took %.0f s" % (time.time() - t0))
            print("check ok: alert() and confirm() reported as dialog in %.1f s" % (time.time() - t0))
        else:
            print("check skipped: no Chrome")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

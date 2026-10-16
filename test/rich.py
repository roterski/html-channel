"""Rich pages: error reporting, visualization adapters, depth tiers, Deeper,
page-JS answers, check_page and channel events.

  .venv/bin/python test/rich.py

Mermaid loads from jsDelivr, so this test needs network access.
"""
import json, os, pathlib, shutil, threading, time
from playwright.sync_api import sync_playwright, expect
from support import Mcp, Sandbox, entries, fail, run, wait_for, write_page

PORT = 18785

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/errors.js"></script>
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
        wait_for(lambda: sb.url(root, s, "viz") is not None and True, 15, "server not up")

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

            # 7.2: Deeper ends ask_human; a rewrite needs a new answer
            page.goto(base + "deeper.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            result = {}
            t = threading.Thread(target=lambda: result.update(mcp.call("ask_human", timeout=20, page="deeper")))
            t.start()
            time.sleep(0.5)
            page.click("button[data-deeper=cost]")
            page.click("button[data-deeper=risk]")
            t.join()
            if result.get("status") != "deeper" or result.get("deeper") not in (["cost"], ["cost", "risk"]):
                fail("ask_human after Deeper: %s" % result)
            write_page(root, s, "deeper", DEEPER % (2, 2))
            expect(page.locator("#v2")).to_be_visible(timeout=4500)
            expect(page.locator("button[data-deeper=cost]")).not_to_have_class("chosen")
            again = mcp.call("ask_human", timeout=20, page="deeper")
            if again["status"] != "waiting" or again["deeper"]:
                fail("Deeper carried over to rev 2: %s" % again)
            print("deeper ok: ask_human returned %s at rev 1; rev 2 waits again" % result["deeper"])

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
        else:
            print("check skipped: no Chrome")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

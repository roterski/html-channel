"""Syntax highlighting: which code is colored, how its language is found, and
what the agent hears when it can't be.

  .venv/bin/python test/highlight.py

Shiki loads from jsDelivr, so this test needs network access.
"""
from playwright.sync_api import sync_playwright, expect
from support import Mcp, Sandbox, fail, run, wait_for, write_page

PORT = 18790

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
<meta name="hc-repo" content="name=api; root=/work/api">
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
%s
<button data-decision="pick" data-choice="a">A</button>
<button data-decision="status" data-choice="done">Done</button>
</body></html>"""

TS = "const x: number = 1; // one"

TARGETS = HEAD % """
<pre id="block" data-lang="ts">%s</pre>
<pre><code id="inner" class="language-python">def f(a):
    return "s" + str(a)</code></pre>
<pre id="by-src" data-src="api:src/core.clj#L1-L2">(defn f [a]
  (str "s" a))</pre>
<pre id="wins" data-src="api:src/core.clj" data-lang="py">x = "s"  # py</pre>
<p>Inline <code id="inline-lang" data-lang="ts">let y = 2</code>,
   <code id="inline-src" data-src="api:src/a.ts#L3">evict()</code>,
   a link to <code id="inline-license" data-src="api:LICENSE">LICENSE</code>,
   and plain <code id="inline-plain">status</code>.</p>
<pre id="text" data-lang="text">plain output, no colors</pre>
<pre id="viz" data-viz="mermaid">graph LR
  A --> B</pre>
<pre id="marked">keep <b>this</b> markup</pre>
""" % TS

PROBLEMS = HEAD % ("".join('<pre id="u%d">unmarked %d</pre>\n' % (i, i) for i in range(10)) + """
<pre id="unknown" data-lang="no-such-lang">x</pre>
<pre id="ext" data-src="api:notes/a.zzz">x</pre>
<pre id="raw" data-lang="ts">const a: Array<string> = []</pre>
""")

NOCODE = HEAD % "<p>No code here, only <code>status</code>.</p>"

OFFLINE = HEAD % '<pre id="block" data-lang="ts">%s</pre><pre data-lang="py">x = 1</pre>' % TS


def errors(mcp, page, kind="highlight"):
    return [e for e in mcp.call("get_decisions", page=page)["errors"] if e["kind"] == kind]


def colored(page, sel):
    return page.locator(sel).evaluate("el => [...el.querySelectorAll('span')].some(s => s.style.color.startsWith('var(--shiki-token-'))")


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()
        mcp = Mcp(sb, project)
        mcp.init()
        setup = mcp.call("channel_setup")
        s, root, base = setup["session"], project / "ai/html-channel", setup["url_base"]
        for name, html in [("targets", TARGETS), ("problems", PROBLEMS), ("nocode", NOCODE),
                           ("offline", OFFLINE), ("slow", OFFLINE)]:
            write_page(root, s, name, html)
        wait_for(lambda: sb.url(root, s, "targets") is not None, 15, "server not up")

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()

            # 2.2, 2.3, 2.4, 2.7, Q1, Q2: hosts, language order, opt-out, attributes and text kept
            page.goto(base + "targets.html")
            expect(page.locator("#block")).to_have_attribute("data-hl", "typescript", timeout=20000)
            expect(page.locator("#inner")).to_have_attribute("data-hl", "python")
            expect(page.locator("#by-src")).to_have_attribute("data-hl", "clojure")
            expect(page.locator("#wins")).to_have_attribute("data-hl", "python")
            expect(page.locator("#inline-lang")).to_have_attribute("data-hl", "typescript")
            expect(page.locator("#inline-src")).to_have_attribute("data-hl", "typescript")
            for sel in ["#block", "#inner", "#by-src", "#inline-lang"]:
                if not colored(page, sel):
                    fail("%s has no var(--shiki-token-*) colors" % sel)
            for sel in ["#inline-plain", "#inline-license", "#text", "#viz", "#marked"]:
                expect(page.locator(sel)).not_to_have_attribute("data-hl", "")
            if page.locator("#text span, #inline-plain span").count():
                fail("plain code got token spans")
            if page.locator("#block").text_content() != TS:
                fail("highlighting changed the text: %r" % page.locator("#block").text_content())
            if page.locator("#marked b").count() != 1:
                fail("the page's markup inside a block was replaced")
            expect(page.locator(".hc-src a.hc-src-link")).to_have_count(2)
            expect(page.locator("a.hc-src-link #inline-src")).to_have_count(1)
            expect(page.locator(".hc-src-copy")).to_have_count(4)
            if errors(mcp, "targets"):
                fail("targets page reported highlight errors: %s" % errors(mcp, "targets"))
            print("hosts ok: data-lang, language-*, data-src, data-lang over data-src, text opt-out, links kept")

            # 2.5, 2.6: problems grouped by cause
            page.goto(base + "problems.html")
            wait_for(lambda: len(errors(mcp, "problems")) >= 3, 20, "problems not reported")
            errs = errors(mcp, "problems")
            by_source = {e["source"]: e["message"] for e in errs}
            if len(errs) != 4:
                fail("want 4 grouped errors, got %s" % errs)
            raw = [m for k, m in by_source.items() if k.startswith("an unescaped <")]
            if not (raw and "(read as <string>)" in raw[0]):
                fail("unescaped < not reported with its tag: %s" % errs)
            expect(page.locator("#raw")).not_to_have_attribute("data-hl", "")
            if not by_source.get("no language", "").startswith("10 code element(s)"):
                fail("unmarked blocks not grouped into one error: %s" % errs)
            if not any("no-such-lang" in k for k in by_source) or not any(".zzz" in k or "a.zzz" in k for k in by_source):
                fail("unknown language / extension not reported: %s" % errs)
            print("problems ok: one error per cause, the ten unmarked blocks in one, unescaped < named")

            # 2.1: no code to highlight, no Shiki request
            requests = []
            page.on("request", lambda r: requests.append(r.url))
            page.goto(base + "nocode.html")
            page.wait_for_timeout(1500)
            if any("shiki" in u for u in requests):
                fail("a page without code loaded Shiki")
            print("lazy ok: no Shiki request without code")

            # 2.9: Shiki can't load -> code stays plain, one error
            off = browser.new_context().new_page()  # its own cache: Shiki must not come from the earlier pages
            off.route("**/cdn.jsdelivr.net/**", lambda route: route.abort())
            console = []
            off.on("console", lambda m: console.append(m.text))
            off.goto(base + "offline.html")
            # the sync API runs route handlers only inside Playwright calls, so wait in one
            wait_for(lambda: off.wait_for_timeout(100) or errors(mcp, "offline"), 15,
                     "Shiki failure not reported; console %s" % console)
            errs = errors(mcp, "offline")
            if len(errs) != 1 or "Shiki didn't load" not in errs[0]["message"]:
                fail("want one Shiki load error: %s" % errs)
            if off.locator("#block span").count():
                fail("code colored without Shiki")
            print("offline ok: plain code and one error")

            # 2.10: the decision runtime doesn't wait for highlighting
            slow = browser.new_context().new_page()
            slow.route("**/cdn.jsdelivr.net/**", lambda route: (slow.wait_for_timeout(4000), route.continue_()))
            slow.goto(base + "slow.html")
            slow.click("button[data-choice=a]")
            expect(slow.locator("button[data-choice=a]")).to_have_attribute("data-state", "saved", timeout=4000)
            if slow.locator("#block").get_attribute("data-hl"):
                fail("Shiki finished before the click; the test proves nothing")
            print("non-blocking ok: an answer saved while Shiki was still loading")
            slow.unroute_all(behavior="ignoreErrors")
            browser.close()
    finally:
        sb.close()


run(main)

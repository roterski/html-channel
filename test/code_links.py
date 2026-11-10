"""Code links: <meta name="hc-repo"> and data-src references, the settings bar,
settings resolution, the Done report and kind code-link errors.

  .venv/bin/python test/code_links.py
"""
import json, pathlib, re
from playwright.sync_api import sync_playwright, expect
from support import Mcp, REPO, Sandbox, entries, fail, run, wait_for, write_page

PORT = 18788
FIXTURE = (REPO / "examples/code-links/cache-fix.html").read_text()
SHA = "0123456789abcdef0123456789abcdef01234567"
WEB = "https://github.com/acme/api/blob/%s/" % SHA

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
%s
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
%s
</body></html>"""

API = '<meta name="hc-repo" content="name=api; root=/work/api; web=%s">' % WEB

# one fault per line: malformed meta, duplicate name, undeclared repo, bad grammar, missing NAME:
FAULTS = HEAD % (API + """
<meta name="hc-repo" content="name=Bad Name; root=/x">
<meta name="hc-repo" content="name=api; root=/elsewhere">""", """
<code data-src="nope:src/a.ts#L1">undeclared</code>
<code data-src="api:/abs/path.ts">leading slash</code>
<code data-src="src/a.ts#L3">no repo</code>
<code id="ok" data-src="api:src/a.ts">fine</code>
""")

# references that page JS adds after load, including an SVG anchor whose data-src comes later
LATE = HEAD % (API, """
<div id="later"></div>
<svg id="chart" width="120" height="40" xmlns="http://www.w3.org/2000/svg"></svg>
<script>
  setTimeout(() => {
    document.getElementById("later").innerHTML = '<code id="late" data-src="api:src/late.ts#L7">late</code>';
    const a = document.createElementNS("http://www.w3.org/2000/svg", "a");
    a.id = "node";
    a.innerHTML = '<text x="5" y="20">idle</text>';
    document.getElementById("chart").append(a);
    a.setAttribute("data-src", "api:src/machine.ts#L3");
  }, 1000);
</script>
""")

# block <pre><code>, inline code inside the page's own link, paths with spaces and %23,
# and references whose data-src changes or goes away after they were linked
EDGES = HEAD % (API, """
<pre><code id="inner" data-src="api:src/block.ts#L1-L3">block</code></pre>
<a id="own" href="https://example.com/doc">see <code id="inlink" data-src="api:src/b.ts#L2">b</code></a>
<code id="spaced" data-src="api:docs/my notes.md#L4">notes</code>
<code id="hash" data-src="api:c%23/x.cs">x.cs</code>
<pre id="retarget" data-src="api:src/old.ts#L1">old</pre>
<code id="drop" data-src="api:src/drop.ts">drop</code>
<svg width="80" height="30"><a id="svga" data-src="api:src/svg.ts#L5"><text x="5" y="20">s</text></a></svg>
""")

REPORT_ONLY = HEAD % (API, '<code id="r" data-src="api:src/a.ts#L1">a</code>')
NO_REFS = HEAD % (API, "<p>no references</p>")


def vscode(root, path, line):
    return "vscode://file%s/%s:%d:1" % (root.replace(" ", "%20"), path, line)


def href(page, sel):
    return page.locator(sel).evaluate("e => e.closest('a').getAttribute('href')")


def open_bar(page):
    page.locator("#decide-code-links").evaluate("d => d.open = true")


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()
        mcp = Mcp(sb, project)
        mcp.init()
        setup = mcp.call("channel_setup")
        s, root, base = setup["session"], project / "ai/html-channel", setup["url_base"]
        for name, html in [("fix", FIXTURE), ("fix2", FIXTURE), ("faults", FAULTS), ("late", LATE), ("edges", EDGES),
                           ("report", REPORT_ONLY), ("norefs", NO_REFS)]:
            write_page(root, s, name, html)
        wait_for(lambda: sb.url(root, s, "fix") is not None, 15, "server not up")

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            ctx = browser.new_context()
            ctx.grant_permissions(["clipboard-read", "clipboard-write"], origin="http://127.0.0.1:%d" % PORT)
            page = ctx.new_page()

            # 1.2.3, 1.4.1, 1.4.4: collapsed bar, VS Code links, one link and copy button per reference
            page.goto(base + "fix.html")
            bar = page.locator("#decide-code-links")
            expect(bar).to_have_count(1)
            expect(bar).not_to_have_attribute("open", "")
            expect(bar.locator("summary")).to_have_text("Code links: VS Code · 2 repos")
            expect(page.locator(".hc-src-copy")).to_have_count(3)
            expect(page.locator(".hc-src a.hc-src-link")).to_have_text("api/src/cache.ts:10-20")
            if href(page, ".hc-src a") != vscode("/work/api", "src/cache.ts", 10):
                fail("block link: %s" % href(page, ".hc-src a"))
            if href(page, "#inline") != vscode("/work/api", "src/store.ts", 42):
                fail("inline link: %s" % href(page, "#inline"))
            if href(page, "#doc") != vscode("/work/my docs", "guide/limits.md", 3):
                fail("space in root not encoded: %s" % href(page, "#doc"))
            print("links ok: collapsed bar, VS Code hrefs, a copy button per reference")

            # 1.4.5: copy puts ROOT/PATH:start on the clipboard
            page.locator("#doc").locator("xpath=../following-sibling::button[1]").click()
            wait_for(lambda: page.evaluate("navigator.clipboard.readText()") == "/work/my docs/guide/limits.md:3",
                     5, "clipboard: %r" % page.evaluate("navigator.clipboard.readText()"))
            print("copy ok")

            # 1.4.2, 1.4.3, 1.4.6: GitHub format; the repo without web falls back, until its URL is entered
            bar.locator("summary").click()
            bar.locator("select").select_option("github")
            if href(page, ".hc-src a") != WEB + "src/cache.ts?plain=1#L10-L20":
                fail("GitHub block link: %s" % href(page, ".hc-src a"))
            if href(page, "#doc") != vscode("/work/my docs", "guide/limits.md", 3):
                fail("docs without web should fall back to VS Code: %s" % href(page, "#doc"))
            docs = bar.locator("fieldset[data-repo=docs]")
            expect(docs.locator(".hc-cl-unavailable")).to_be_visible()
            docs.locator("input[name=web]").fill("not a url")
            docs.locator("input[name=web]").press("Enter")
            docs.locator("input[name=web]").blur()
            expect(docs.locator(".hc-cl-hint")).to_be_visible()
            if not href(page, "#doc").startswith("vscode://"):
                fail("an invalid web URL must keep the fallback")
            docs.locator("input[name=web]").fill("https://github.com/acme/docs/blob/main")
            docs.locator("input[name=web]").blur()
            expect(docs.locator(".hc-cl-hint")).to_be_hidden()
            expect(docs.locator(".hc-cl-unavailable")).to_be_hidden()
            if href(page, "#doc") != "https://github.com/acme/docs/blob/main/guide/limits.md?plain=1#L3":
                fail("entered web URL not used: %s" % href(page, "#doc"))
            print("github ok: blob URLs with ?plain=1, fallback, entered URL, invalid-URL hint")

            # 1.3.1: page settings persist on reload and stay on this page
            page.reload()
            expect(page.locator("#decide-code-links select")).to_have_value("github")
            other = ctx.new_page()
            other.goto(base + "fix2.html")
            expect(other.locator("#decide-code-links select")).to_have_value("vscode")
            print("page scope ok")

            # 1.2.3, 1.3.4: a root override reopens the bar on load; 1.2.5: Reset drops this page's settings
            api = page.locator("fieldset[data-repo=api]")
            page.locator("#decide-code-links summary").click()
            api.locator("input[name=root]").fill("/home/me/api-fork/")
            api.locator("input[name=root]").blur()
            page.locator("#decide-code-links select").select_option("vscode")
            if href(page, "#inline") != vscode("/home/me/api-fork", "src/store.ts", 42):
                fail("root override not applied: %s" % href(page, "#inline"))
            page.reload()
            expect(page.locator("#decide-code-links")).to_have_attribute("open", "")
            page.locator("#decide-code-links button:has-text('Reset')").click()
            if href(page, "#inline") != vscode("/work/api", "src/store.ts", 42):
                fail("Reset should restore the agent's root: %s" % href(page, "#inline"))
            print("override ok: applied, bar opens on load, Reset restores")

            # 1.3.2, 1.3.3: Save as default carries format and root to another page with the same repo
            api.locator("input[name=root]").fill("/home/me/api-fork")
            api.locator("input[name=root]").blur()
            page.locator("#decide-code-links select").select_option("github")
            page.locator("#decide-code-links button:has-text('Save as default')").click()
            other.reload()
            expect(other.locator("#decide-code-links")).to_have_attribute("open", "")
            expect(other.locator("#decide-code-links select")).to_have_value("github")
            expect(other.locator("fieldset[data-repo=api] input[name=root]")).to_have_value("/home/me/api-fork")
            print("shared defaults ok")

            # 1.5.1, 1.5.2: Done records sources before status, at the same revision
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            page.click("button[data-decision=swap][data-choice=yes]")
            page.click("button[data-decision=status]")
            wait_for(lambda: any(e["id"] == "status" for e in entries(root, s)), 5, "Done not saved")
            fix = [e for e in entries(root, s) if e["page"] == "fix"]
            ids = [e["id"] for e in fix]
            if ids.index("sources") > ids.index("status") or ids.count("sources") != 1:
                fail("sources must be recorded once, before status: %s" % ids)
            src = next(e for e in fix if e["id"] == "sources")
            if src["rev"] != next(e for e in fix if e["id"] == "status")["rev"]:
                fail("sources and status at different revisions")
            want = {"format": "github", "repos": {
                "api": {"root": "/home/me/api-fork", "default": "/work/api", "overridden": True},
                "docs": {"root": "/work/my docs", "default": "/work/my docs", "overridden": False}}}
            if src["value"] != want:
                fail("sources: %s" % json.dumps(src["value"]))
            got = mcp.call("get_decisions", page="fix")["decisions"]["sources"]["value"]
            if got != want:
                fail("get_decisions sources: %s" % got)
            print("done ok: sources before status, overridden flagged")

            # 1.2.2, 1.2.6, 1.4.4a: late references; the bar arrives with the first; SVG href and xlink:href
            late = ctx.new_page()
            late.goto(base + "late.html")
            expect(late.locator("#decide-code-links")).to_have_count(0)
            expect(late.locator("#late")).to_be_attached(timeout=3000)
            expect(late.locator("#decide-code-links")).to_have_count(1)
            if href(late, "#late") is None:
                fail("late reference not linked")
            node = late.locator("#node")
            expect(node).to_have_attribute("href", WEB + "src/machine.ts?plain=1#L3")  # shared default: github
            xl = node.evaluate("a => a.getAttributeNS('http://www.w3.org/1999/xlink', 'href')")
            if xl != WEB + "src/machine.ts?plain=1#L3":
                fail("xlink:href: %s" % xl)
            if late.locator("#chart .hc-src-copy").count():
                fail("an SVG anchor gets no copy button")
            open_bar(late)
            late.locator("#decide-code-links select").select_option("vscode")
            expect(node).to_have_attribute("href", vscode("/home/me/api-fork", "src/machine.ts", 3))
            late.locator("#late").evaluate("e => document.body.append(e.closest('a'))")  # a move links nothing twice
            late.wait_for_timeout(200)
            expect(late.locator("a.hc-src-link:has(#late)")).to_have_count(1)
            if late.locator(".hc-src-copy").count() != 1:
                fail("moving a linked reference added a copy button")
            print("late ok: bar on first reference, SVG anchor managed, a move adds nothing")

            # bugs 2-7 from the edge-case run
            e = ctx.new_page()
            e.add_init_script("localStorage.clear()")
            e.goto(base + "edges.html")
            expect(e.locator("#decide-code-links")).to_have_count(1)
            pre = e.locator("pre:has(#inner)")
            if pre.evaluate("p => p.previousElementSibling.className") != "hc-src" or pre.locator(".hc-src-copy").count():
                fail("<pre><code data-src> must get a header line, and no copy button inside the <pre>")
            if e.locator("#inner").evaluate("c => !!c.closest('a')"):
                fail("<pre><code data-src> must not wrap the code in a link")
            if e.locator("a a").count() or e.locator("#own").get_attribute("href") != "https://example.com/doc":
                fail("inline code inside the page's own link must keep that link")
            if e.locator("#own + .hc-src-copy").count() != 1:
                fail("inline code inside the page's own link gets a copy button after it")
            if href(e, "#spaced") != "vscode://file/work/api/docs/my%20notes.md:4:1":
                fail("path with a space: %s" % href(e, "#spaced"))
            if href(e, "#hash") != "vscode://file/work/api/c%23/x.cs":
                fail("%%23 in a path: %s" % href(e, "#hash"))
            e.locator("#retarget").evaluate("p => p.setAttribute('data-src', 'api:src/new.ts#L9')")
            expect(e.locator(".hc-src:has(+ #retarget) a")).to_have_attribute("href", vscode("/work/api", "src/new.ts", 9))
            expect(e.locator(".hc-src:has(+ #retarget)")).to_have_count(1)
            e.locator("#drop").evaluate("c => c.removeAttribute('data-src')")
            expect(e.locator("a:has(#drop)")).to_have_count(0)
            expect(e.locator("#drop + .hc-src-copy")).to_have_count(0)
            e.locator("#svga").evaluate("a => a.setAttribute('data-src', 'garbage')")
            expect(e.locator("#svga")).not_to_have_attribute("href", re.compile(".*"))
            if e.locator("#svga").evaluate("a => a.getAttributeNS('http://www.w3.org/1999/xlink', 'href')") is not None:
                fail("a retargeted-to-invalid SVG link keeps its xlink:href")
            wait_for(lambda: any("garbage" in x["message"] for x in mcp.call("get_decisions", page="edges")["errors"]),
                     10, "the invalid retarget wasn't reported")
            open_bar(e)
            root_in = e.locator("fieldset[data-repo=api] input[name=root]")
            for bad in ["relative/dir", "~/api"]:
                root_in.fill(bad)
                root_in.press("Enter")
                expect(e.locator("fieldset[data-repo=api] .hc-cl-root-hint")).to_be_visible()
                if href(e, "#spaced") != "vscode://file/work/api/docs/my%20notes.md:4:1":
                    fail("a non-absolute root %r must be ignored: %s" % (bad, href(e, "#spaced")))
            root_in.fill("/srv/api")
            root_in.press("Enter")
            expect(e.locator("fieldset[data-repo=api] .hc-cl-root-hint")).to_be_hidden()
            if href(e, "#spaced") != "vscode://file/srv/api/docs/my%20notes.md:4:1":
                fail("an absolute root must apply: %s" % href(e, "#spaced"))
            print("edges ok: <pre><code>, page links, spaces and %23, retarget and removal, non-absolute roots")

            # 1.2.1: one code-link error per fault; the bad ones stay plain text
            f = ctx.new_page()
            f.goto(base + "faults.html")
            expect(f.locator("#ok")).to_have_count(1)
            wait_for(lambda: len([e for e in mcp.call("get_decisions", page="faults")["errors"]
                                  if e["kind"] == "code-link"]) >= 5, 10, "code-link errors not reported")
            errs = [e for e in mcp.call("get_decisions", page="faults")["errors"] if e["kind"] == "code-link"]
            if len(errs) != 5:
                fail("want 5 code-link errors, got %s" % [e["message"] for e in errs])
            if f.locator("a.hc-src-link").count() != 1:
                fail("only the valid reference should be linked")
            print("faults ok: 5 code-link errors")

            # 1.2.2, 1.5.6: no references, no bar; a page without Done records nothing
            n = ctx.new_page()
            n.goto(base + "norefs.html")
            n.wait_for_timeout(500)
            expect(n.locator("#decide-code-links")).to_have_count(0)
            r = ctx.new_page()
            r.goto(base + "report.html")
            expect(r.locator("#decide-code-links")).to_have_count(1)
            open_bar(r)
            r.locator("#decide-code-links select").select_option("github")
            r.wait_for_timeout(500)
            if [e for e in entries(root, s) if e["page"] == "report"]:
                fail("a report-only page must record nothing")
            print("no-refs and report-only ok")

            # 1.3.5: storage that throws still gives links with the agent's defaults
            broken = browser.new_context()
            broken.add_init_script("Storage.prototype.getItem = () => { throw new Error('denied') };"
                                   "Storage.prototype.setItem = () => { throw new Error('denied') };")
            b = broken.new_page()
            b.goto(base + "fix.html")
            expect(b.locator("#decide-code-links")).to_have_count(1)
            if href(b, "#inline") != vscode("/work/api", "src/store.ts", 42):
                fail("links without storage: %s" % href(b, "#inline"))
            print("storage failure ok")

            browser.close()

        # 1.4.7: the fixture passes check_page in both schemes
        checked = mcp.call("check_page", timeout=60, page="fix")
        if checked["errors"]:
            fail("check_page errors on the fixture: %s" % checked["errors"])
        print("check_page ok")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

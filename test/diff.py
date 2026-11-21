"""Diffs: patch and two-text input, rendering, per-hunk decisions, and the kit's Clear.

  .venv/bin/python test/diff.py

jsdiff and Shiki load from jsDelivr, so this test needs network access.
"""
import html
from playwright.sync_api import sync_playwright, expect
from support import Mcp, Sandbox, entries, fail, run, wait_for, write_page

PORT = 18795

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
<meta name="hc-repo" content="name=api; root=/work/api">
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
%s
<button data-decision="status" data-choice="done">Done</button>
</body></html>"""

PATCH = """diff --git a/src/a.ts b/src/a.ts
--- a/src/a.ts
+++ b/src/a.ts
@@ -10,3 +10,4 @@ class Cache {
   size = 0;
-  max = 100;
+  max = 1000;
+  ttl = 60;
   evict() {
diff --git a/src/b.clj b/src/b.clj
--- a/src/b.clj
+++ b/src/b.clj
@@ -1,3 +1,3 @@
 (ns b)
-(defn put [m k v] (assoc m k v))
+(defn put! [m k v] (assoc m k v))
 (defn get-val [m k] (get m k))
"""

OLD = "(ns store)\n\n(defn put [m k v]\n  (assoc m k v))\n"
NEW = "(ns store)\n\n(defn put! [m k v]\n  (assoc m k v))\n"

REVIEW = HEAD % ("""
<pre id="fix" data-diff="fix" data-repo="api" data-hunk-choices="accept reject">%s</pre>
<figure id="rename" data-diff="rename">
<pre data-diff-old data-src="api:src/store.clj#L5">%s</pre>
<pre data-diff-new>%s</pre>
</figure>
""" % (html.escape(PATCH), html.escape(OLD), html.escape(NEW)))

SAME_PATCH = """--- a/x.clj
+++ b/x.clj
@@ -1,4 +1,4 @@
 (ns store)

-(defn put [m k v]
+(defn put! [m k v]
   (assoc m k v))
"""

SAME = HEAD % ("""
<pre id="p" data-diff="p">%s</pre>
<figure id="t" data-diff="t"><pre data-diff-old data-src="api:x.clj">%s</pre><pre data-diff-new>%s</pre></figure>
""" % (html.escape(SAME_PATCH), html.escape(OLD), html.escape(NEW)))

LONG_OLD = "".join("x%d = %d\n" % (i, i) for i in range(1, 201))
LONG_NEW = LONG_OLD.replace("x100 = 100\n", "x100 = 101\n")
COMMENT_OLD = "/*\n a\n b\n c\n d\n e\n old words here\n*/\nconst x = 1;\n"
COMMENT_NEW = COMMENT_OLD.replace("old words", "new words")

MIN_OLD = "var a=" + "1+" * 800 + "1;\n"
MIN_NEW = MIN_OLD.replace("var a=", "var b=")

TEXTS = HEAD % ("""
<figure id="long" data-diff="long"><pre data-diff-old data-src="api:long.py">%s</pre><pre data-diff-new>%s</pre></figure>
<figure id="comment" data-diff="comment"><pre data-diff-old data-src="api:c.ts">%s</pre><pre data-diff-new>%s</pre></figure>
<figure id="minified" data-diff="minified"><pre data-diff-old data-src="api:m.js">%s</pre><pre data-diff-new>%s</pre></figure>
""" % (LONG_OLD, LONG_NEW, COMMENT_OLD, COMMENT_NEW, MIN_OLD, MIN_NEW))

TWINS = HEAD % """
<pre id="twins" data-diff="twins" data-repo="api" data-hunk-choices="ok">--- a/t.txt
+++ b/t.txt
@@ -2,3 +2,3 @@
 alpha
-x = 1
+x = 2
 beta
@@ -20,3 +20,3 @@
 gamma
-x = 1
+x = 2
 delta
</pre>
<pre id="gone" data-diff="gone" data-repo="api">diff --git a/old.ts b/old.ts
deleted file mode 100644
--- a/old.ts
+++ /dev/null
@@ -1,2 +0,0 @@
-const a = 1;
-const b = 2;
</pre>
"""

BROKEN = HEAD % """
<pre id="bad" data-diff="bad">@@ -1,2 +1,2 @@
-only one line but the header says two
</pre>
<figure id="half" data-diff="half"><pre data-diff-old>a</pre></figure>
<figure id="equal" data-diff="equal"><pre data-diff-old>a</pre><pre data-diff-new>a</pre></figure>
<figure id="raw" data-diff="raw"><pre data-diff-old data-lang="ts">const a: Array<string> = []</pre><pre data-diff-new data-lang="ts">const a: Array<number> = []</pre></figure>
<pre id="d1" data-diff="dup" data-hunk-choices="ok">--- a/x.txt
+++ b/x.txt
@@ -1 +1 @@
-a
+b
</pre>
<pre id="d2" data-diff="dup" data-hunk-choices="ok">--- a/y.txt
+++ b/y.txt
@@ -1 +1 @@
-a
+b
</pre>
"""


def body(lines):
    return "".join(l + "\n" for l in lines)


def shifting(extra):
    """A two-hunk patch; with `extra`, a third hunk first, shifting the others by one line."""
    first = "@@ -1,1 +1,2 @@\n top\n+inserted\n" if extra else ""
    d = 1 if extra else 0
    return ("--- a/s.txt\n+++ b/s.txt\n" + first +
            "@@ -10,1 +%d,1 @@\n-ten\n+TEN\n" % (10 + d) +
            "@@ -40,1 +%d,1 @@\n-forty\n+FORTY\n" % (40 + d))


CARRY = HEAD % '<pre data-diff="s" data-hunk-choices="accept reject">%s</pre>'

CLEAR = HEAD % """
<pre data-diff="c" data-hunk-choices="accept reject">--- a/x.txt
+++ b/x.txt
@@ -1 +1 @@
-a
+b
</pre>%s"""

OLD_SCRIPT = """<script>document.addEventListener("click",e=>{const c=e.target.closest("[data-clear]"),b=e.target.closest("button[data-choice].chosen:not([data-decision=status])"),id=c?c.dataset.clear:b?.dataset.decision;if(id){e.stopPropagation();htmlChannel.record(id,null)}},true)</script>"""


def errs(mcp, page, kind):
    return [e for e in mcp.call("get_decisions", page=page)["errors"] if e["kind"] == kind]


def decisions(mcp, page):
    return mcp.call("get_decisions", page=page)["decisions"]


def row_texts(page, sel):
    return page.locator(sel + " + .hc-diff .hc-diff-unified tr").evaluate_all(
        "rs => rs.map(r => r.cells[2].textContent + r.cells[3].textContent)")


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()
        mcp = Mcp(sb, project)
        mcp.init()
        setup = mcp.call("channel_setup")
        s, root, base = setup["session"], project / "ai/html-channel", setup["url_base"]
        for name, page_html in [("review", REVIEW), ("same", SAME), ("texts", TEXTS), ("broken", BROKEN),
                                ("carry", CARRY % shifting(False)), ("clear", CLEAR % ""),
                                ("oldscript", CLEAR % OLD_SCRIPT), ("twins", TWINS), ("nohl", REVIEW)]:
            write_page(root, s, name, page_html)
        wait_for(lambda: sb.url(root, s, "review") is not None, 15, "server not up")

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page(viewport={"width": 1100, "height": 900})

            # 3.1, 3.3, 3.4, 3.5, 3.8: files, language from path, signs, words over colors, links
            page.goto(base + "review.html")
            fix = page.locator("#fix + .hc-diff")
            expect(fix.locator(".hc-diff-file")).to_have_count(2, timeout=20000)
            expect(page.locator("#fix")).to_be_hidden()
            expect(fix.locator(".hc-diff-file-head")).to_have_text(["src/a.tsCopy", "src/b.cljCopy"])
            expect(fix.locator(".hc-diff-unified .hc-diff-del .hc-diff-sign [aria-hidden]").first).to_have_text("−")
            expect(fix.locator(".hc-diff-unified .hc-diff-add .hc-diff-sign [aria-hidden]").first).to_have_text("+")
            expect(fix.locator(".hc-diff-unified .hc-diff-del .hc-diff-sign .hc-sr").first).to_have_text("removed")
            expect(fix.locator(".hc-diff-unified .hc-diff-add .hc-diff-sign .hc-sr").first).to_have_text("added")
            word = fix.locator(".hc-diff-unified .hc-diff-add .hc-diff-word").first
            expect(word).to_have_text("1000")
            if not word.evaluate("w => w.style.color.startsWith('var(--shiki-token-')"):
                fail("the changed word lost its syntax color")
            clj = fix.locator(".hc-diff-file").nth(1).locator(".hc-diff-unified .hc-diff-code span")
            if not clj.evaluate_all("ss => ss.some(s => s.style.color.startsWith('var(--shiki-token-'))"):
                fail("the .clj file was not colored")
            link = fix.locator(".hc-diff-hunk-head a.hc-src-link").first
            expect(link).to_have_attribute("href", "vscode://file/work/api/src/a.ts:10:1")
            rename = page.locator("#rename + .hc-diff")
            expect(rename.locator(".hc-diff-unified td.hc-diff-no").first).to_have_text("5")
            if errs(mcp, "review", "highlight") or errs(mcp, "review", "diff"):
                fail("review page errors: %s" % mcp.call("get_decisions", page="review")["errors"])
            print("render ok: two files, signs with spoken words, colored changed word, clj colored, hunk link, data-src offset")

            # 3.10: split when wide, unified when narrow, the reader's pick kept
            expect(fix.locator(".hc-diff-split").first).to_be_visible()
            expect(fix.locator(".hc-diff-unified").first).to_be_hidden()
            page.set_viewport_size({"width": 600, "height": 900})
            expect(fix.locator(".hc-diff-unified").first).to_be_visible()
            expect(fix.locator(".hc-diff-split").first).to_be_hidden()
            page.set_viewport_size({"width": 1100, "height": 900})
            fix.locator(".hc-diff-layout button[data-layout=unified]").click()
            page.reload()
            fix = page.locator("#fix + .hc-diff")
            expect(fix.locator(".hc-diff-unified").first).to_be_visible(timeout=20000)
            expect(fix.locator(".hc-diff-layout button[data-layout=unified]")).to_have_attribute("aria-pressed", "true")
            page.evaluate("localStorage.removeItem('html-channel:diff-layout')")
            narrow = browser.new_page(viewport={"width": 600, "height": 900})
            narrow.goto(base + "review.html")
            nfix = narrow.locator("#fix + .hc-diff")
            expect(nfix.locator(".hc-diff-unified table").first).to_be_visible(timeout=20000)
            expect(nfix.locator(".hc-diff-split table")).to_have_count(0)
            narrow.set_viewport_size({"width": 1100, "height": 900})  # auto: widening shows split, built on resize
            expect(nfix.locator(".hc-diff-split table").first).to_be_visible()
            narrow.set_viewport_size({"width": 600, "height": 900})
            nfix.locator(".hc-diff-layout button[data-layout=split]").click()
            expect(nfix.locator(".hc-diff-split table").first).to_be_visible()
            narrow.evaluate("localStorage.removeItem('html-channel:diff-layout')")
            narrow.close()
            print("layout ok: split wide, unified narrow, the pick survives a reload, split built on first show")

            # 4.1, Q4: hunk answers and the hunks map on Done
            buttons = fix.locator(".hc-hunk-choice")
            expect(buttons).to_have_count(2)
            buttons.nth(0).locator("button[data-choice=accept]").click()
            buttons.nth(1).locator("textarea").fill("rename it put-val")
            buttons.nth(1).locator("textarea").blur()
            page.click("button[data-decision=status]")
            wait_for(lambda: mcp.call("get_decisions", page="review")["done"], 10, "Done not recorded")
            d = decisions(mcp, "review")
            hunks = d.get("hunks", {}).get("value", {}).get("fix", {})
            if sorted((v["file"], v["old"], v["new"]) for v in hunks.values()) != \
                    [("src/a.ts", "10,3", "10,4"), ("src/b.clj", "1,3", "1,3")]:
                fail("hunks map: %s" % d.get("hunks"))
            by_file = {v["file"]: "fix." + k for k, v in hunks.items()}
            if d.get(by_file["src/a.ts"], {}).get("value") != "accept":
                fail("hunk answer: %s" % d)
            if d.get(by_file["src/b.clj"] + "-note", {}).get("value") != "rename it put-val":
                fail("hunk note: %s" % d)
            if "rename" in d.get("hunks", {}).get("value", {}):
                fail("a diff without choices is in the hunks map")
            print("decisions ok: answer and note per hunk, hunks map names file and lines")

            # 3.2: a patch and the two texts it came from render the same rows
            page.goto(base + "same.html")
            expect(page.locator("#t + .hc-diff")).to_have_count(1, timeout=20000)
            if row_texts(page, "#p") != row_texts(page, "#t"):
                fail("patch and texts differ:\n%s\n%s" % (row_texts(page, "#p"), row_texts(page, "#t")))
            print("same ok: patch and two texts give identical rows")

            # 3.6, 3.7: whole-text coloring, collapsed context
            page.goto(base + "texts.html")
            long = page.locator("#long + .hc-diff")
            expect(long).to_have_count(1, timeout=20000)
            expect(long.locator(".hc-diff-unified tr")).to_have_count(8)
            expect(long.locator(".hc-diff-expand")).to_have_text(["Show 96 unchanged lines", "Show 97 unchanged lines"])
            first_gap = long.locator(".hc-diff-expand").first
            opened = long.locator(".hc-diff-gap + .hc-diff-body")
            first_gap.click()
            expect(long.locator(".hc-diff-unified tr")).to_have_count(8 + 96)
            expect(opened).to_be_visible()
            expect(first_gap).to_have_text("Hide 96 unchanged lines")
            expect(first_gap).to_have_attribute("aria-expanded", "true")
            first_gap.click()
            expect(opened).to_be_hidden()
            expect(first_gap).to_have_text("Show 96 unchanged lines")
            first_gap.click()
            expect(opened).to_be_visible()
            expect(long.locator(".hc-diff-unified tr")).to_have_count(8 + 96)  # built once, not again
            comment = page.locator("#comment + .hc-diff .hc-diff-unified .hc-diff-add .hc-diff-code span").first
            expect(comment).to_have_attribute("style", "color: var(--shiki-token-comment);")
            expect(page.locator("#minified + .hc-diff .hc-diff-unified .hc-diff-add")).to_have_count(1)
            expect(page.locator("#minified + .hc-diff .hc-diff-word")).to_have_count(0)
            print("texts ok: 3 lines of context, expanders that show and hide, comment colored as comment, no word marks past the cap")

            # 3.9, 4.2: failures keep the source, notices and diff errors; a duplicate ID renders read-only
            page.goto(base + "broken.html")
            wait_for(lambda: len(errs(mcp, "broken", "diff")) >= 5, 20, "diff errors not reported")
            msgs = " | ".join(e["message"] for e in errs(mcp, "broken", "diff"))
            for want in ['diff "bad": the patch doesn', 'diff "half": needs a <pre data-diff-old>',
                         'diff "equal": the two texts are the same', 'diff "dup": its ID is used by another diff',
                         'diff "raw": the browser read <string>, <number> in it as HTML tags', 'write < as &lt;',
                         "Check that each @@ header's line counts match"]:
                if want not in msgs:
                    fail("missing %r in %s" % (want, msgs))
            expect(page.locator("#bad")).to_be_visible()
            expect(page.locator("#bad + .hc-viz-error")).to_have_count(1)
            expect(page.locator("#d1 + .hc-diff")).to_have_count(1)
            expect(page.locator(".hc-hunk-choice")).to_have_count(0)
            if errs(mcp, "broken", "highlight"):
                fail(".txt diffs should be plain without errors: %s" % errs(mcp, "broken", "highlight"))
            print("broken ok: sources stay with notices; duplicate IDs render read-only")

            # 4.3, 4.4: an answer survives a revision that shifts its hunk, shown as carried
            page.goto(base + "carry.html")
            later = page.locator(".hc-hunk-choice").nth(1)
            expect(later).to_have_count(1, timeout=20000)
            later.locator("button[data-choice=reject]").click()
            expect(later.locator("button[data-choice=reject]")).to_have_attribute("data-state", "saved")
            write_page(root, s, "carry", CARRY % shifting(True))
            expect(page.locator(".hc-hunk-choice")).to_have_count(3, timeout=20000)
            moved = page.locator(".hc-hunk-choice").nth(2).locator("button[data-choice=reject]")
            expect(moved).to_have_attribute("data-state", "carried", timeout=10000)
            expect(moved).to_have_class("chosen")
            expect(page.locator("#decide-clear-carried")).to_be_visible()
            print("carry ok: the shifted hunk keeps its answer, marked carried")

            # review 8, 9: repeated changes get distinct keys from their context; a deleted file isn't linked
            page.goto(base + "twins.html")
            twins = page.locator("#twins + .hc-diff .hc-hunk-choice button[data-choice=ok]")
            expect(twins).to_have_count(2, timeout=20000)
            ids = twins.evaluate_all("bs => bs.map(b => b.dataset.decision)")
            if len(set(ids)) != 2 or any("-" in i.split(".")[1] for i in ids):
                fail("repeated hunks should get distinct context keys, not -N: %s" % ids)
            gone = page.locator("#gone + .hc-diff")
            expect(gone.locator(".hc-diff-file-head")).to_have_text("old.ts (deleted)")
            expect(gone.locator(".hc-diff-hunk-head")).to_have_text("@@ -1,2 +0,0 @@")  # as git writes it
            expect(gone.locator("a.hc-src-link")).to_have_count(0)
            print("keys ok: repeated changes keyed by context; deleted file unlinked, its header as git writes it")

            # review 3: Shiki doesn't load, jsdiff does -> the diff renders plain, with a highlight error
            nohl = browser.new_context().new_page()
            nohl.route("**/*shiki*/**", lambda route: route.abort())
            nohl.goto(base + "nohl.html")
            expect(nohl.locator("#fix + .hc-diff .hc-diff-file")).to_have_count(2, timeout=20000)
            wait_for(lambda: nohl.wait_for_timeout(100) or errs(mcp, "nohl", "highlight"), 15, "Shiki failure not reported")
            if not all(e["message"].startswith('diff "') for e in errs(mcp, "nohl", "highlight")):
                fail("highlight errors should name the diff: %s" % errs(mcp, "nohl", "highlight"))
            if errs(mcp, "nohl", "diff"):
                fail("Shiki failing must not fail the diff: %s" % errs(mcp, "nohl", "diff"))
            if nohl.locator("#fix + .hc-diff .hc-diff-code span[style*=shiki]").count():
                fail("colored without Shiki")
            print("no-highlighter ok: diffs render plain, highlight errors name the diff")

            # 4.6: the kit clears; with #html's old script there is still one null
            for name in ["clear", "oldscript"]:
                page.goto(base + name + ".html")
                accept = page.locator(".hc-hunk-choice button[data-choice=accept]")
                expect(accept).to_have_count(1, timeout=20000)
                id = accept.get_attribute("data-decision")
                accept.click()
                expect(accept).to_have_attribute("data-state", "saved")
                page.locator(".hc-hunk-clear").click()
                expect(accept).not_to_have_class("chosen")
                expect(accept).to_have_attribute("data-state", "saved")
                accept.click()
                expect(accept).to_have_attribute("data-state", "saved")
                accept.click()
                expect(accept).not_to_have_class("chosen")
                expect(accept).to_have_attribute("data-state", "saved")
                vals = [e["value"] for e in entries(root, s) if e["page"] == name and e["id"] == id]
                if vals != ["accept", None, "accept", None]:
                    fail("%s: want accept, null, accept, null; got %s" % (name, vals))
            print("clear ok: Clear and re-clicking clear once each, with and without the old script")
            browser.close()
    finally:
        sb.close()


run(main)

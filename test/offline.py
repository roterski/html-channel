"""Answering without the server: Download, the three states, and decisions --file.

  .venv/bin/python test/offline.py

Every decision page offers Download next to Done. Download is a pure read: it
writes an answers .edn and records nothing. While the server is away, Done
hides, the banner says Download keeps the answers, a name field shows, unsent
answers survive a closed tab, and leaving asks first. An offline copy (an
hc-offline meta with mode=share) keeps answers in the browser and shows no Done,
banner or Deeper. `html-channel decisions --file` reads the file back.
"""
import json, subprocess, time
from playwright.sync_api import sync_playwright, expect
from support import REPO, Sandbox, entries, fail, run, stop, wait_for, write_page

PORT = 18797

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
<meta name="hc-repo" content="name=r; root=/tmp/r">
%s
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
"""

FORM = """<h1>form</h1>
<button data-decision="cache" data-choice="redis">Redis</button>
<button data-decision="cache" data-choice="inproc">In-process</button>
<section data-question="evict"><h2>Eviction</h2><input data-decision="evict"></section>
<input data-decision="a b">
<input data-decision='q"x'>
<input data-decision="x/y">
<input data-decision="3fa9">
<textarea data-decision="multi"></textarea>
<button data-deeper="more">Deeper</button>
<p><code data-src="r:README.md">README</code></p>
<p><button data-decision="status" data-choice="done">Done</button></p>
<script type="application/json" id="hc-moot">{"evict": {"when": "cache=inproc", "reason": "in-process evicts on its own"}}</script>
</body></html>"""

REPORT = """<h1>report</h1><p>Nothing to answer.</p></body></html>"""

DONE = '[data-decision="status"][data-choice="done"]'


def page_html(meta=""):
    return HEAD % meta + FORM


def read_back(sb, root, path):
    r = subprocess.run(["bb", "-m", "html-channel.cli", "decisions", "--file", str(path), "--root", str(root)],
                       cwd=REPO, env=sb.env(), capture_output=True, text=True)
    if r.returncode != 0:
        fail("decisions --file failed: %s" % r.stderr)
    return json.loads(r.stdout)


def value(state, id):
    d = state["decisions"].get(id)
    return d and d["value"]


def answer(page, id, text):
    loc = page.locator('[data-decision=%s]' % json.dumps(id))
    loc.fill(text)
    loc.blur()  # the native change event, once


def leave(page):
    """Closes `page` as a reader would. True when it asked first (and stays open),
    False when it closed. Waits on the page itself: the sync API delivers the
    dialog event only while a Playwright call runs."""
    asked = []
    page.once("dialog", lambda d: (asked.append(d.type), d.dismiss()))
    page.close(run_before_unload=True)
    for _ in range(30):
        if asked:
            return True
        try:
            page.wait_for_timeout(100)
        except Exception:
            return False
    return False


def download(page):
    with page.expect_download() as d:
        page.locator(".hc-download-button").click()
    return d.value.suggested_filename, d.value.path()


def connected(sb, root, s, browser):
    page = browser.new_page()
    page.goto(sb.url(root, s, "form"))
    expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
    expect(page.locator(".hc-download-button")).to_be_visible()
    expect(page.locator(DONE)).to_be_visible()
    expect(page.locator(".hc-download-name")).to_be_hidden()
    print("ok: connected: Done and Download, no name field")

    page.locator('[data-decision="cache"][data-choice="redis"]').click()
    for id, text in [("a b", "one"), ('q"x', "two"), ("x/y", "three"), ("3fa9", "four"), ("multi", 'l1\nl2 "q"')]:
        answer(page, id, text)
    page.locator("button[data-deeper]").click()
    expect(page.locator('[data-decision="multi"]')).to_have_attribute("data-state", "saved", timeout=4500)
    wait_for(lambda: any(e["id"] == "deeper" for e in entries(root, s)), 5, "Deeper was not recorded")
    before = len(entries(root, s))
    name, path = download(page)
    time.sleep(0.5)
    if len(entries(root, s)) != before:
        fail("Download recorded answers: %d -> %d entries" % (before, len(entries(root, s))))
    print("ok: connected: Download records nothing")
    if name != "form@1.edn":
        fail("download name %r, wanted form@1.edn" % name)
    text = path.read_text()
    if not text.startswith(";; html-channel answers. Read with: html-channel decisions --file"):
        fail("the file doesn't start with its read hint: %r" % text[:80])
    st = read_back(sb, root, path)
    wants = {"cache": "redis", "a b": "one", 'q"x': "two", "x/y": "three", "3fa9": "four", "multi": 'l1\nl2 "q"'}
    for id, v in wants.items():
        if value(st, id) != v:
            fail("read back %r = %r, wanted %r" % (id, value(st, id), v))
    if (st["respondent"], st["state"], st["checked"], st["done"], st["rev"]) != (":author", "connected", True, True, 1):
        fail("read back header: %s" % {k: st[k] for k in ["respondent", "state", "checked", "done", "rev"]})
    if "deeper" in st["decisions"]:
        fail("a Deeper click is in the file's decisions: %r" % st["decisions"]["deeper"])
    if value(st, "moot") != {} or value(st, "sources") is None:
        fail("read back reports: moot %r, sources %r" % (value(st, "moot"), value(st, "sources")))
    print("ok: connected: the file reads back with every answer, :author, sources and moot")

    page.locator('[data-decision="cache"][data-choice="inproc"]').click()
    st = read_back(sb, root, download(page)[1])
    if "evict" not in (value(st, "moot") or {}):
        fail("moot after In-process: %r" % value(st, "moot"))
    print("ok: connected: the file's moot follows the answers")

    page.locator(DONE).click()
    wait_for(lambda: any(e["id"] == "status" for e in entries(root, s)), 5, "Done was not recorded")
    recorded = {e["id"]: e["value"] for e in entries(root, s)}
    if recorded.get("sources") != value(st, "sources") or recorded.get("moot") != value(st, "moot"):
        fail("Done recorded %r / %r, the file had %r / %r"
             % (recorded.get("sources"), recorded.get("moot"), value(st, "sources"), value(st, "moot")))
    print("ok: connected: Done records the same sources and moot as the file")
    page.close()


def report_only(sb, root, s, browser):
    page = browser.new_page()
    page.goto(sb.url(root, s, "report"))
    expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
    expect(page.locator(".hc-download")).to_have_count(0)
    print("ok: report-only: no Download")
    page.close()


def disconnected(sb, root, s, context, server):
    page = context.new_page()
    page.goto(sb.url(root, s, "form2"))
    other = context.new_page()
    other.goto(sb.url(root, s, "form2"))
    for p in [page, other]:
        expect(p.locator("#decide-offline")).to_be_hidden(timeout=4500)
    stop(server)
    expect(other.locator(DONE)).to_be_hidden(timeout=4500)
    answer(other, "x/y", "from the other tab")
    other.close()
    expect(page.locator(DONE)).to_be_hidden(timeout=4500)
    expect(page.locator("#decide-offline .hc-download-hint")).to_be_visible()
    expect(page.locator(".hc-download-name")).to_be_visible()
    print("ok: disconnected: Done hidden, banner hint and name field shown")

    page.locator('[data-decision="cache"][data-choice="redis"]').click()
    if not leave(page):
        fail("no leave prompt with an answer not downloaded")
    print("ok: disconnected: leaving with an answer not downloaded asks first")

    page.locator(".hc-download-name").fill("Ann Lee")
    answer(page, "evict", "lru")
    name, path = download(page)
    if name != "form2@1-ann-lee.edn":
        fail("download name %r, wanted form2@1-ann-lee.edn" % name)
    st = read_back(sb, root, path)
    if (st["respondent"], st["state"], value(st, "cache"), value(st, "evict")) != ("Ann Lee", "disconnected", "redis", "lru"):
        fail("read back: %s" % {k: st[k] for k in ["respondent", "state"]})
    print("ok: disconnected: Download names the file and the respondent, unsent answers included")
    if leave(page):
        fail("a leave prompt after Download")
    print("ok: disconnected: no leave prompt once downloaded")

    sb.serve(root)
    page = context.new_page()
    page.goto(sb.url(root, s, "form2"))
    # a closed tab's queue is claimed once it has gone orphan-after-ms (10 s) without a refresh
    wait_for(lambda: {"cache", "evict", "x/y"} <= {e["id"] for e in entries(root, s) if e["page"] == "form2"}, 20,
             "answers given while disconnected didn't reach the server after both tabs were closed")
    print("ok: disconnected: answers from two closed tabs are sent once the server is back")
    page.close()


def share(sb, root, s, context):
    page = context.new_page()
    page.goto(sb.url(root, s, "copy"))
    expect(page.locator(".hc-download-button")).to_be_visible(timeout=4500)
    for sel in [DONE, "#decide-offline", "button[data-deeper]"]:
        expect(page.locator(sel)).to_be_hidden()
    expect(page.locator(".hc-download-name")).to_be_visible()
    print("ok: share: Download and name only")

    page.locator('[data-decision="cache"][data-choice="redis"]').click()
    expect(page.locator('[data-decision="cache"][data-choice="redis"]')).to_have_attribute("data-state", "saved")
    page.reload()
    expect(page.locator('[data-decision="cache"][data-choice="redis"]')).to_have_class("chosen", timeout=3000)
    expect(page.locator('[data-decision="cache"][data-choice="redis"]')).to_have_attribute("data-state", "saved")
    if any(e["page"] == "copy" for e in entries(root, s)):
        fail("a share copy sent answers to the server")
    print("ok: share: answers kept in the browser across a reload, none sent")

    name, path = download(page)
    st = read_back(sb, root, path)
    if (name, st["state"], st["session"], st["page"], st["rev"], st["respondent"], value(st, "cache")) != \
            ("copy@1.edn", "share", s, "copy", 1, None, "redis"):
        fail("share read back: %r %s" % (name, {k: st[k] for k in ["state", "session", "page", "rev", "respondent"]}))
    print("ok: share: the file carries the copy's session, page and revision")

    answer(page, "evict", "lfu")
    if not leave(page):
        fail("no leave prompt in share mode")
    print("ok: share: leaving with an answer not downloaded asks first")


def share_storage_refused(sb, root, s, context):
    page = context.new_page()
    page.add_init_script("Storage.prototype.setItem = function () { throw new Error('quota') }")
    page.goto(sb.url(root, s, "copy2"))
    expect(page.locator(".hc-download-button")).to_be_visible(timeout=4500)
    redis = page.locator('[data-decision="cache"][data-choice="redis"]')
    redis.click()
    expect(redis).to_have_attribute("data-state", "rejected", timeout=3000)
    classes = set()
    for _ in range(14):  # 7 s: two polls and more
        classes.add(page.evaluate("document.documentElement.classList.contains('decide-is-offline')"))
        page.wait_for_timeout(500)
    if classes != {False}:
        fail("storage refused: the page flipped offline/online: %r" % classes)
    st = read_back(sb, root, download(page)[1])
    if value(st, "cache") != "redis":
        fail("storage refused: Download lost the answer: %r" % value(st, "cache"))
    errs = (root / s / "errors.jsonl").read_text() if (root / s / "errors.jsonl").exists() else ""
    if "rejected by this browser" not in errs:
        fail("storage refused: no error reported")
    print("ok: share, storage refused: the answer is marked rejected, reported, kept for Download; no flicker")
    page.close()


ORPHAN = """localStorage.setItem("html-channel:queue:" + location.pathname + "#closed-tab",
  JSON.stringify({beat: 0, queue: [{page: "form3", id: %s, value: %s, rev: 1, at: new Date(Date.now() + %d).toISOString()}]}))"""


def orphan(page, id, value, age_ms):
    """Leaves a closed tab's mirrored queue: one entry, given `age_ms` ago."""
    page.evaluate(ORPHAN % (json.dumps(id), json.dumps(value), -age_ms))


def orphan_claims(sb, root, s, context):
    a = context.new_page()
    a.goto(sb.url(root, s, "form3"))
    redis = a.locator('[data-decision="cache"][data-choice="redis"]')
    redis.click()
    expect(redis).to_have_attribute("data-state", "saved", timeout=4500)
    orphan(a, "cache", "inproc", 60000)  # an older answer, from a tab closed a minute ago
    a.close()
    c = context.new_page()
    c.goto(sb.url(root, s, "form3"))
    wait_for(lambda: c.evaluate("Object.keys(localStorage).filter(k => k.includes('#closed-tab')).length") == 0, 10,
             "the closed tab's queue was never claimed")
    c.wait_for_timeout(1000)
    if any(e["page"] == "form3" and e["id"] == "cache" and e["value"] == "inproc" for e in entries(root, s)):
        fail("a closed tab's older answer overrode the newer saved one")
    print("ok: claims: a closed tab's older answer doesn't override a newer saved one")

    d = context.new_page()
    d.goto(sb.url(root, s, "form3"))
    expect(d.locator("#decide-offline")).to_be_hidden(timeout=4500)
    orphan(d, "evict", "ghost", 0)
    wait_for(lambda: any(e["page"] == "form3" and e["value"] == "ghost" for e in entries(root, s)), 10,
             "the closed tab's answer was never sent")
    c.wait_for_timeout(4000)  # every tab has polled again since
    n = sum(1 for e in entries(root, s) if e["page"] == "form3" and e["value"] == "ghost")
    if n != 1:
        fail("a closed tab's answer was sent %d times by two open tabs" % n)
    print("ok: claims: with two tabs open, a closed tab's answer is sent once")
    c.close()
    d.close()


def main():
    sb = Sandbox(PORT)
    try:
        root = sb.project("proj")
        s = "off"
        write_page(root, s, "form", page_html())
        write_page(root, s, "form2", page_html())
        write_page(root, s, "form3", page_html())
        write_page(root, s, "report", HEAD % "" + REPORT)
        write_page(root, s, "copy", page_html('<meta name="hc-offline" content="session=%s; page=copy; rev=1; mode=share">' % s))
        write_page(root, s, "copy2", page_html('<meta name="hc-offline" content="session=%s; page=copy2; rev=1; mode=share">' % s))
        server = sb.serve(root)
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            connected(sb, root, s, browser)
            report_only(sb, root, s, browser)
            # a page of its own context: browser.new_page() closes skipping beforeunload
            share(sb, root, s, browser.new_context())
            share_storage_refused(sb, root, s, browser.new_context())
            orphan_claims(sb, root, s, browser.new_context())
            disconnected(sb, root, s, browser.new_context(), server)
            browser.close()
    finally:
        sb.close()


run(main)

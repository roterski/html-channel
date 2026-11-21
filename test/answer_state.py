"""Answer state: every change of an answer passes through data-state="pending",
and restoring answers on load never does (CONVENTION, JavaScript section).

  .venv/bin/python test/answer_state.py

Page script relies on this to react to answer changes, because buttons, Clear
and htmlChannel.record fire no change event.
"""
from playwright.sync_api import sync_playwright, expect
from support import Sandbox, fail, run, wait_for, write_page

PORT = 18791

PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
<h1>%s</h1>
<button data-decision="cache" data-choice="redis">Redis</button>
<button data-decision="cache" data-choice="inproc">In-process</button>
<button data-clear="cache">Clear cache</button>
<label><input type="radio" name="evict" data-decision="evict" value="lru"> LRU</label>
<label><input type="radio" name="evict" data-decision="evict" value="lfu"> LFU</label>
<button data-clear="evict">Clear evict</button>
<input data-decision="ttl">
<button data-decision="status" data-choice="done">Done</button>
</body></html>"""

# Installed before any page script: logs each data-state an answer's controls take,
# with the answer's value as the control shows it at that moment.
RECORDER = """
window.__states = [];
function shown(id) {
  const els = [...document.querySelectorAll('[data-decision="' + id + '"]')];
  const b = els.find(e => e.tagName === "BUTTON" && e.classList.contains("chosen"));
  if (b) return b.dataset.choice;
  const r = els.find(e => e.type === "radio" && e.checked);
  if (r) return r.value;
  const t = els.find(e => e.tagName === "INPUT" && e.type !== "radio");
  return t ? t.value : null;
}
new MutationObserver(ms => ms.forEach(m => {
  const id = m.target.dataset && m.target.dataset.decision;
  if (id) window.__states.push({id, state: m.target.dataset.state, shown: shown(id)});
})).observe(document, {subtree: true, attributes: true, attributeFilter: ["data-state"]});
"""


def states(page, id):
    return [s for s in page.evaluate("window.__states") if s["id"] == id]


def changes(page, label, id, action, shown):
    """`action` changes answer `id`: its controls pass through pending, then saved,
    and the control already shows `shown` when pending is set."""
    page.evaluate("window.__states = []")
    action()
    wait_for(lambda: any(s["state"] == "saved" for s in states(page, id)), 5, "%s: %s never saved" % (label, id))
    seen = states(page, id)
    pending = [s for s in seen if s["state"] == "pending"]
    if not pending:
        fail("%s: %s went %s without pending" % (label, id, [s["state"] for s in seen]))
    if pending[0]["shown"] != shown:
        fail("%s: %s showed %r when pending was set, expected %r" % (label, id, pending[0]["shown"], shown))
    print("ok: %s -> pending, then saved" % label)


def restores_without_pending(page, label, shown):
    """After a reload, each answer in `shown` (id -> value) is restored as saved, never pending,
    and the control already shows its value when saved appears."""
    page.reload()
    expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
    wait_for(lambda: all(states(page, id) for id in shown), 5, "%s: %s not restored" % (label, list(shown)))
    for id, value in shown.items():
        seen = states(page, id)
        got = {s["state"] for s in seen}
        if got != {"saved"}:
            fail("%s: restoring %s set %s, expected only saved" % (label, id, sorted(got)))
        if seen[0]["shown"] != value:
            fail("%s: %s showed %r when saved appeared, expected the restored %r" % (label, id, seen[0]["shown"], value))
    print("ok: %s restores %s as saved, never pending, value showing when saved appears" % (label, list(shown)))


def main():
    sb = Sandbox(PORT)
    try:
        root = sb.project("proj")
        s = "state"
        write_page(root, s, "answers", PAGE % "v1")
        sb.serve(root)
        url = sb.url(root, s, "answers")
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()
            page.add_init_script(RECORDER)
            page.goto(url)
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)

            changes(page, "choose a button", "cache", lambda: page.click("button[data-choice=inproc]"), "inproc")
            changes(page, "click the chosen button again", "cache", lambda: page.click("button[data-choice=inproc]"), None)
            page.click("button[data-choice=redis]")
            changes(page, "Clear a button question", "cache", lambda: page.click("button[data-clear=cache]"), None)
            changes(page, "choose a radio", "evict", lambda: page.check("input[value=lru]"), "lru")
            changes(page, "Clear a radio question", "evict", lambda: page.click("button[data-clear=evict]"), None)
            changes(page, "edit a field", "ttl", lambda: (page.fill("[data-decision=ttl]", "60"), page.click("h1")), "60")
            changes(page, "htmlChannel.record", "evict",
                    lambda: page.evaluate("htmlChannel.record('evict', 'lfu')"), "lfu")

            page.click("button[data-choice=redis]")
            wait_for(lambda: any(x["state"] == "saved" for x in states(page, "cache")), 5, "cache never saved")
            restores_without_pending(page, "a reload", {"cache": "redis", "evict": "lfu", "ttl": "60"})

            # a new revision carries the answers over; restoring them is still not a change
            page.evaluate("window.__states = []")
            write_page(root, s, "answers", PAGE % "v2")
            expect(page.locator("h1")).to_have_text("v2", timeout=4500)
            wait_for(lambda: states(page, "cache"), 5, "carried answers not restored")
            got = {x["state"] for x in states(page, "cache")}
            if got != {"carried"}:
                fail("a new revision restored cache as %s, expected only carried" % sorted(got))
            print("ok: a new revision restores answers as carried, never pending")

            changes(page, "Clear carried answers", "cache", lambda: page.click("#decide-clear-carried"), None)
            browser.close()
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

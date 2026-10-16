"""Revisions: rewriting a page reloads open tabs, carries answers over,
offers a clear button, and needs a fresh Done.

  .venv/bin/python test/revisions.py
"""
from playwright.sync_api import sync_playwright, expect
from support import DEMO, Mcp, Sandbox, entries, fail, run, stop, write_page

PORT = 18784
V1 = DEMO.read_text()


def version(n):
    return V1.replace("<h1>", '<h1><span id="v%d">v%d</span> ' % (n, n))


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        root = sb.project("proj")
        s = "rev"
        write_page(root, s, "review", version(1))
        server = sb.serve(root)
        url = sb.url(root, s, "review")
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()
            page.goto(url)
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            page.click("button[data-choice=ttl]")
            page.fill("[data-decision=notes]", "first pass")
            page.click("h1")
            expect(page.locator("[data-decision=notes]")).to_have_attribute("data-state", "saved")
            page.click("button[data-choice=done]")
            expect(page.locator("button[data-choice=done]")).to_have_attribute("data-state", "saved")

            # rewrite -> open tab reloads, answers carried, done not carried
            write_page(root, s, "review", version(2))
            expect(page.locator("#v2")).to_be_visible(timeout=4500)
            expect(page.locator("button[data-choice=ttl]")).to_have_attribute("data-state", "carried")
            expect(page.locator("button[data-choice=ttl]")).to_have_class("chosen")
            expect(page.locator("[data-decision=notes]")).to_have_value("first pass")
            expect(page.locator("[data-decision=notes]")).to_have_attribute("data-state", "carried")
            expect(page.locator("button[data-choice=done]")).not_to_have_class("chosen")
            expect(page.locator("#decide-clear-carried")).to_be_visible()
            print("carry ok: tab reloaded at rev 2 with carried answers and a clear button")

            # clear -> nothing pre-filled, even after a reload
            page.click("#decide-clear-carried")
            expect(page.locator("#decide-clear-carried")).to_have_count(0)
            expect(page.locator("[data-decision=notes]")).to_have_value("")
            expect(page.locator("[data-decision=notes]")).to_have_attribute("data-state", "saved")
            page.reload()
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            expect(page.locator("[data-decision=notes]")).to_have_attribute("data-state", "saved")
            expect(page.locator("[data-decision=notes]")).to_have_value("")
            expect(page.locator("button[data-choice=ttl]")).not_to_have_class("chosen")
            expect(page.locator("#decide-clear-carried")).to_have_count(0)
            cleared = [e for e in entries(root, s) if e["value"] is None]
            if {(e["id"], e["rev"]) for e in cleared} != {("eviction", 2), ("notes", 2)}:
                fail("clear entries: %s" % cleared)
            print("clear ok: nulls recorded at rev 2, nothing restored after reload")

            # a click queued while offline keeps its old rev across a rewrite
            stop(server)
            expect(page.locator("#decide-offline")).to_be_visible(timeout=4500)
            page.check("[data-decision=keep-metrics]")
            write_page(root, s, "review", version(3))
            sb.serve(root)
            expect(page.locator("#v3")).to_be_visible(timeout=4500)
            km = [e for e in entries(root, s) if e["id"] == "keep-metrics"]
            if [e["rev"] for e in km] != [2]:
                fail("queued entry should keep rev 2: %s" % km)
            print("queue ok: entry queued at rev 2 saved as rev 2, then the tab reloaded at rev 3")

            # Done at the current rev unblocks ask_human
            mcp = Mcp(sb, project, "--max-wait", "30")
            mcp.init()
            ask = mcp.send("tools/call", {"name": "ask_human", "arguments": {"page": "review", "session": s}})
            page.click("button[data-choice=lru]")
            page.click("button[data-choice=done]")
            res = mcp.wait(ask, 6)["result"]["structuredContent"]
            browser.close()
        d = res["decisions"]
        if (res["status"], res["rev"], d["eviction"]["value"], d["eviction"]["rev"], d["keep-metrics"]["rev"]) \
                != ("done", 3, "lru", 3, 2):
            fail("ask_human at rev 3: %s" % res)
        print("done ok: ask_human returned at rev 3; keep-metrics shows rev 2 (carried, not re-confirmed)")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

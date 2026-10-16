"""Browser end-to-end checks for one served root.

  .venv/bin/python test/e2e.py

  B  decisions are saved, then restored on reload
  D  the server dies mid-session: the banner shows, a click queues as pending,
     and after a restart the banner hides and the queued click is saved
"""
import shutil
from playwright.sync_api import sync_playwright, expect
from support import DEMO, WAIT_MS, Sandbox, entries, run, stop

PORT = 18781


def main():
    sb = Sandbox(PORT)
    try:
        root = sb.project("proj")
        (root / "e2e").mkdir()
        shutil.copy(DEMO, root / "e2e/cache-layer-review.html")
        server = sb.serve(root)
        url = sb.url(root, "e2e", "cache-layer-review")
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()

            # --- B: save and restore
            page.goto(url)
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=WAIT_MS)
            page.click("button[data-choice=ttl]")
            page.check("[data-decision=keep-metrics]")
            page.fill("[data-decision=notes]", "rename evict! to evict")
            page.click("h1")  # blur -> change event
            expect(page.locator("[data-decision=notes]")).to_have_attribute("data-state", "saved")
            page.reload()
            expect(page.locator("button[data-choice=ttl]")).to_have_class("chosen", timeout=WAIT_MS)
            expect(page.locator("button[data-choice=lru]")).not_to_have_class("chosen")
            expect(page.locator("[data-decision=keep-metrics]")).to_be_checked()
            expect(page.locator("[data-decision=notes]")).to_have_value("rename evict! to evict")
            got = {e["id"]: e["value"] for e in entries(root, "e2e")}
            assert got == {"eviction": "ttl", "keep-metrics": True, "notes": "rename evict! to evict"}, got
            assert {e["session"] for e in entries(root, "e2e")} == {"e2e"}
            print("B ok: saved and restored", got)

            # --- D: server dies mid-session
            stop(server)
            expect(page.locator("#decide-offline")).to_be_visible(timeout=WAIT_MS)
            page.click("button[data-choice=done]")
            expect(page.locator("button[data-choice=done]")).to_have_attribute("data-state", "pending")
            assert not any(e["id"] == "status" for e in entries(root, "e2e")), "saved while server was down"
            sb.serve(root)
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=WAIT_MS)
            expect(page.locator("button[data-choice=done]")).to_have_attribute("data-state", "saved", timeout=WAIT_MS)
            last = entries(root, "e2e")[-1]
            assert (last["id"], last["value"]) == ("status", "done"), last
            print("D ok: banner shown while down, queued click flushed after restart")
            browser.close()
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

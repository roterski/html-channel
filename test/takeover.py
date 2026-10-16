"""Takeover: when the port owner dies, a standby serves every registered root.

  .venv/bin/python test/takeover.py

Three `serve` processes, three projects. Kill the owner:
  - every mount answers 200 again within 2 s, including the dead owner's;
  - decisions are routed to their own project's file;
  - a click made in an open tab during the gap is saved within 4 s of the kill.
"""
import shutil, time
from playwright.sync_api import sync_playwright, expect
from support import DEMO, Sandbox, entries, fail, get, health, post, run, stop, wait_for

PORT = 18782


def main():
    sb = Sandbox(PORT)
    try:
        roots = [sb.project(n) for n in ("alpha", "beta", "gamma")]
        for r in roots:
            (r / "s1").mkdir()
            shutil.copy(DEMO, r / "s1/cache-layer-review.html")
        owner = sb.serve(roots[0])
        owner_pid = health(PORT)["owner_pid"]
        for r in roots[1:]:
            sb.cli("serve", "--root", str(r))
        wait_for(lambda: len(health(PORT)["mounts"]) == 3, 10, "not all roots registered")
        urls = [sb.url(r, "s1", "cache-layer-review") for r in roots]

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()
            page.goto(urls[1])
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)

            t0 = time.time()
            stop(owner, "kill")
            page.click("button[data-choice=lru]")
            wait_for(lambda: all(get(u)[0] == 200 for u in urls), 2, "not every mount is back within 2 s")
            took = time.time() - t0
            new_owner = health(PORT)["owner_pid"]
            if new_owner == owner_pid:
                fail("owner did not change")
            print("takeover ok: all 3 mounts served again after %.2f s by pid %s" % (took, new_owner))

            expect(page.locator("button[data-choice=lru]")).to_have_attribute(
                "data-state", "saved", timeout=max(1, 4 - (time.time() - t0)) * 1000)
            print("flush ok: click from the gap saved %.2f s after the kill" % (time.time() - t0))
            browser.close()

        for i, u in enumerate(urls):
            post(u.rsplit("/", 1)[0] + "/_decisions", {"page": "cache-layer-review", "id": "who", "value": i})
        for i, r in enumerate(roots):
            who = [e["value"] for e in entries(r, "s1") if e["id"] == "who"]
            if who != [i]:
                fail("project %d got %s" % (i, who))
        print("routing ok: each decision landed in its own project")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

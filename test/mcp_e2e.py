"""MCP over stdio: channel_setup, ask_human, get_decisions against a real browser.

  .venv/bin/python test/mcp_e2e.py
"""
import json, pathlib, shutil, subprocess, threading, time
from playwright.sync_api import sync_playwright, expect
from support import DEMO, REPO, Mcp, Sandbox, fail, run, write_page

PORT = 18783


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()

        # stdout stays clean: no stdin, no output
        out = subprocess.run(["bb", "-m", "html-channel.cli", "mcp"], cwd=REPO, env=sb.env(CLAUDE_PROJECT_DIR=str(project)),
                             stdin=subprocess.DEVNULL, capture_output=True, timeout=30)
        if out.stdout:
            fail("mcp wrote to stdout without input: %r" % out.stdout[:200])
        print("stdout ok: 0 bytes with closed stdin")

        mcp = Mcp(sb, project, "--max-wait", "60")
        init = mcp.init()
        if init["result"]["serverInfo"]["name"] != "html-channel":
            fail(init)
        names = sorted(t["name"] for t in mcp.request("tools/list")["result"]["tools"])
        if names != ["ask_human", "channel_setup", "check_page", "get_decisions"]:
            fail(names)

        setup = mcp.call("channel_setup")
        again = mcp.call("channel_setup")
        if setup["session"] != again["session"] or len(setup["session"]) != 8:
            fail("session not stable: %s vs %s" % (setup["session"], again["session"]))
        if setup["convention"] != (REPO / "CONVENTION.md").read_text():
            fail("convention differs from CONVENTION.md")
        if setup["dir"] != str((project / "ai/html-channel" / setup["session"]).resolve()):
            fail(setup["dir"])
        print("setup ok: session", setup["session"], "url_base", setup["url_base"])

        shutil.copy(DEMO, setup["dir"] + "/review.html")
        notes = []
        ask = mcp.send("tools/call", {"name": "ask_human", "arguments": {"page": "review"},
                                      "_meta": {"progressToken": "tok"}})
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()
            page.goto(setup["url_base"] + "review.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)
            page.click("button[data-choice=lru]")
            expect(page.locator("button[data-choice=lru]")).to_have_attribute("data-state", "saved")
            t0 = time.time()
            page.click("button[data-choice=done]")
            r = mcp.wait(ask, 5, notes)
            took = time.time() - t0
            browser.close()
        res = r["result"]["structuredContent"]
        if res["status"] != "done" or res["decisions"]["eviction"]["value"] != "lru" or took > 1.5:
            fail("ask_human: %s after %.2fs" % (res, took))
        print("ask_human ok: done %.2f s after the click, rev %s" % (took, res["rev"]))

        got = mcp.call("get_decisions", page="review")
        if not got["done"] or got["rev"] != 1:
            fail(got)

        # a stale done does not unblock the next revision; max-wait returns waiting
        short = Mcp(sb, project, "--max-wait", "2")
        short.init()
        write_page(project / "ai/html-channel", setup["session"], "review", (REPO / "examples/demo/cache-layer-review.html")
                   .read_text().replace("<h1>", "<h1>v2 "))
        t0 = time.time()
        w = short.call("ask_human", timeout=10, page="review", session=setup["session"])
        if w["status"] != "waiting" or w["rev"] != 2 or w["done"]:
            fail("expected waiting at rev 2, got %s" % w)
        print("waiting ok: rev-1 done ignored at rev 2, returned after %.1f s" % (time.time() - t0))

        # the root can be moved: HTML_CHANNEL_ROOT, relative to the project; --root wins over it
        moved = Mcp(sb, project, env={"HTML_CHANNEL_ROOT": "custom"})
        moved.init()
        d = moved.call("channel_setup")["dir"]
        if d != str((project / "custom" / pathlib.Path(d).name).resolve()):
            fail("HTML_CHANNEL_ROOT ignored: %s" % d)
        flagged = Mcp(sb, project, "--root", "flagged", env={"HTML_CHANNEL_ROOT": "custom"})
        flagged.init()
        d = flagged.call("channel_setup")["dir"]
        if d != str((project / "flagged" / pathlib.Path(d).name).resolve()):
            fail("--root did not win over HTML_CHANNEL_ROOT: %s" % d)
        print("root ok: HTML_CHANNEL_ROOT and --root move the sessions directory")

        missing = mcp.request("tools/call", {"name": "ask_human", "arguments": {"page": "nope"}})
        if not missing["result"].get("isError"):
            fail("ask_human on a missing page should be an error: %s" % missing)
        print("error ok:", missing["result"]["content"][0]["text"][:60])
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

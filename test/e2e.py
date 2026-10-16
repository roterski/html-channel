"""Browser end-to-end checks against a running decision server.

  .venv/bin/python test/e2e.py

Scenarios (plan §3):
  B  page served over http: decisions are saved, then restored on reload
  D  server dies mid-session: banner shows, a click queues as pending,
     and after a restart the banner hides and the queued click is saved

Exits non-zero on the first failed assertion. E2E_SERVER_CMD overrides the
server command (it gets `--root <dir>` appended); setting it to `true` must
make this script fail.
"""
import json, os, pathlib, shlex, shutil, socket, subprocess, sys, tempfile, time
from playwright.sync_api import sync_playwright, expect

REPO = pathlib.Path(__file__).resolve().parent.parent
PORT = 8787
SERVER_CMD = shlex.split(os.environ.get("E2E_SERVER_CMD", "bb serve.clj"))
WAIT_MS = 4500  # banner/health poll runs every 3 s

def port_open():
    with socket.socket() as s:
        return s.connect_ex(("127.0.0.1", PORT)) == 0

def wait_port(up, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        if port_open() == up:
            return True
        time.sleep(0.1)
    return False

class Server:
    def __init__(self, root):
        self.root, self.proc = root, None
    def start(self):
        self.proc = subprocess.Popen(SERVER_CMD + ["--root", str(self.root)], cwd=REPO,
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        assert wait_port(True), "server did not start listening on %d" % PORT
    def stop(self):
        if self.proc:
            self.proc.terminate(); self.proc.wait(); self.proc = None
        assert wait_port(False), "server still listening after stop"

def entries(root):
    f = root / "decisions.jsonl"
    return [json.loads(l) for l in f.read_text().splitlines()] if f.exists() else []

def main():
    assert not port_open(), "port %d is already in use; stop that server first" % PORT
    root = pathlib.Path(tempfile.mkdtemp(prefix="html-channel-e2e-"))
    shutil.copy(REPO / "examples/demo.html", root / "demo.html")
    server = Server(root)
    url = "http://127.0.0.1:%d/demo.html" % PORT
    try:
        server.start()
        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()

            # --- B: save and restore over http
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
            got = {e["id"]: e["value"] for e in entries(root)}
            assert got == {"eviction": "ttl", "keep-metrics": True, "notes": "rename evict! to evict"}, got
            print("B ok: saved and restored", got)

            # --- D: server dies mid-session
            server.stop()
            expect(page.locator("#decide-offline")).to_be_visible(timeout=WAIT_MS)
            page.click("button[data-choice=done]")
            expect(page.locator("button[data-choice=done]")).to_have_attribute("data-state", "pending")
            assert not any(e["id"] == "status" for e in entries(root)), "saved while server was down"
            server.start()
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=WAIT_MS)
            expect(page.locator("button[data-choice=done]")).to_have_attribute("data-state", "saved", timeout=WAIT_MS)
            assert entries(root)[-1]["id"] == "status" and entries(root)[-1]["value"] == "done", entries(root)[-1]
            print("D ok: banner shown while down, queued click flushed after restart")
            browser.close()
    finally:
        server.stop()
        shutil.rmtree(root, ignore_errors=True)

if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e, file=sys.stderr); sys.exit(1)
    print("e2e: all scenarios passed")

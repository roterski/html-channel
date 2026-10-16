"""Shared helpers for the browser and MCP end-to-end tests.

Every test gets an isolated registry (HTML_CHANNEL_HOME) and port, and the
Claude Code session variables of the calling shell are removed, so tests
never touch a real server or a real project.
"""
import json, os, pathlib, queue, shutil, socket, subprocess, sys, tempfile, threading, time, urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent
DEMO = REPO / "examples/demo/cache-layer-review.html"
WAIT_MS = 4500  # the runtime polls every 3 s


def fail(msg):
    raise AssertionError(msg)


def run(main):
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e, file=sys.stderr)
        sys.exit(1)
    print(pathlib.Path(sys.argv[0]).name + ": all scenarios passed")


class Sandbox:
    """A temp dir holding the registry and any number of projects."""

    def __init__(self, port):
        self.port = port
        self.dir = pathlib.Path(tempfile.mkdtemp(prefix="html-channel-e2e-"))
        self.procs = []
        if port_open(port):
            fail("port %d is already in use" % port)

    def env(self, **extra):
        env = {k: v for k, v in os.environ.items()
               if not k.startswith("CLAUDE") and not k.startswith("HTML_CHANNEL")}
        env.update(HTML_CHANNEL_HOME=str(self.dir / "state"), HTML_CHANNEL_PORT=str(self.port), **extra)
        return env

    def project(self, name):
        root = self.dir / name / ".ai"
        root.mkdir(parents=True, exist_ok=True)
        return root

    def cli(self, *args, **popen):
        p = subprocess.Popen(["bb", "-m", "html-channel.cli", *args], cwd=REPO, env=self.env(**popen.pop("env", {})),
                             **popen)
        self.procs.append(p)
        return p

    def serve(self, root):
        p = self.cli("serve", "--root", str(root), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        wait_for(lambda: mount_of(self.port, root) is not None, 10, "serve %s did not come up" % root)
        return p

    def url(self, root, session, page):
        return "http://127.0.0.1:%d/%s/%s/%s.html" % (self.port, mount_of(self.port, root), session, page)

    def close(self):
        for p in self.procs:
            stop(p)
        shutil.rmtree(self.dir, ignore_errors=True)


def stop(p, sig="term"):
    if p.poll() is None:
        p.kill() if sig == "kill" else p.terminate()
        p.wait()


def port_open(port):
    with socket.socket() as s:
        return s.connect_ex(("127.0.0.1", port)) == 0


def wait_for(pred, timeout, msg):
    end = time.time() + timeout
    while time.time() < end:
        if pred():
            return
        time.sleep(0.05)
    fail(msg)


def get(url):
    try:
        with urllib.request.urlopen(url, timeout=2) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, ""
    except OSError:
        return None, ""


def post(url, data):
    req = urllib.request.Request(url, data=json.dumps(data).encode(), method="POST")
    with urllib.request.urlopen(req, timeout=2) as r:
        return r.status


def health(port):
    status, body = get("http://127.0.0.1:%d/_health" % port)
    return json.loads(body) if status == 200 else None


def mount_of(port, root):
    """The mount the running server uses for `root`, read from the registry it serves."""
    h = health(port)
    if not h:
        return None
    name = root.parent.name
    ms = [m for m in h["mounts"] if m.rsplit("-", 1)[0] == name.lower()]
    return ms[0] if len(ms) == 1 else None


def entries(root, session):
    f = root / session / "decisions.jsonl"
    return [json.loads(l) for l in f.read_text().splitlines()] if f.exists() else []


def write_page(root, session, page, html):
    f = root / session / (page + ".html")
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(html)
    return f


class Mcp:
    """Drives `html-channel mcp` over stdio."""

    def __init__(self, sandbox, project_dir, *args):
        self.p = sandbox.cli("mcp", *args, env={"CLAUDE_PROJECT_DIR": str(project_dir)},
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                             text=True, bufsize=1)
        self.next_id = 0
        self.msgs = queue.Queue()
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for line in self.p.stdout:
            self.msgs.put(json.loads(line))

    def send(self, method, params=None):
        self.next_id += 1
        self.p.stdin.write(json.dumps({"jsonrpc": "2.0", "id": self.next_id, "method": method,
                                       "params": params or {}}) + "\n")
        self.p.stdin.flush()
        return self.next_id

    def wait(self, id, timeout=10, notes=None):
        end = time.time() + timeout
        while time.time() < end:
            try:
                m = self.msgs.get(timeout=max(0.01, end - time.time()))
            except queue.Empty:
                break
            if m.get("id") == id:
                return m
            if notes is not None:
                notes.append(m)
        fail("no response to request %d within %ss" % (id, timeout))

    def request(self, method, params=None, timeout=10):
        return self.wait(self.send(method, params), timeout)

    def call(self, tool, timeout=10, **args):
        r = self.request("tools/call", {"name": tool, "arguments": args}, timeout)
        if "result" not in r or r["result"].get("isError"):
            fail("%s failed: %s" % (tool, r))
        return r["result"]["structuredContent"]

    def init(self):
        r = self.request("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                        "clientInfo": {"name": "e2e", "version": "0"}})
        self.p.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized"}) + "\n")
        self.p.stdin.flush()
        return r

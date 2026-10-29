"""Explorable pages: the kit's controls (scrub, state link, timeline, global pause),
the KaTeX adapter, the structure lint, and check_page's interaction probe.

  .venv/bin/python test/explorable.py

KaTeX loads from jsDelivr, so this test needs network access.
"""
import os, pathlib, shutil
from playwright.sync_api import sync_playwright, expect
from support import Mcp, Sandbox, entries, fail, run, wait_for, write_page

PORT = 18787

HEAD = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
%s
</head><body>
<div id="decide-offline" style="position:sticky;top:0">offline</div>
%s
</body></html>"""

# the controls on a plain page: each event the demo gets is logged in window.got
CONTROLS = HEAD % ('<meta name="hc-libs" content="katex">', """
<p>n = <span id="n" data-scrub="n" data-target="demo" data-min="0" data-max="10" data-step="2" data-value="4">4</span></p>
<p><a id="six" data-set="demo" data-state='{"n": 6, "mode": "x"}'>six</a></p>
<div data-instrument="demo"><p id="shown"></p></div>
<div id="tl" data-timeline="demo" data-duration="2"></div>
<pre data-viz="katex">\\frac{a}{b}</pre>
<p>inline <span data-viz="katex">\\alpha</span> math</p>
<pre data-viz="katex">\\frac{a}{</pre>
<script>
  window.got = [];
  const demo = document.querySelector('[data-instrument=demo]');
  for (const t of ["hc-param", "hc-state", "hc-time"])
    demo.addEventListener(t, e => { window.got.push([t, e.detail]); });
  document.addEventListener("hc-pause", e => window.got.push(["hc-pause", e.detail]));
</script>
""")

# everything the lint and the probe report, once each
BROKEN = HEAD % ('<meta name="hc-kind" content="explorable">', """
<section data-concept="dead"><h2>Dead</h2>
  <div data-instrument="dead"><button type="button">Nothing happens</button></div></section>
<section data-concept="still"><h2>Still</h2>
  <div data-instrument="still"><svg width="80" height="40"><rect width="80" height="40"></rect></svg></div></section>
<section data-concept="bare"><h2>No instrument</h2><p>prose only</p></section>
<section data-concept="unlabeled"><h2>Unlabeled</h2>
  <div data-instrument="unlabeled"><input type="range" class="r"><output></output></div></section>
<p><a data-set="ghost" data-state='{"x": 1}'>ghost</a></p>
<script>
  const r = document.querySelector('[data-instrument=unlabeled] .r');
  r.addEventListener('input', () => r.nextElementSibling.textContent = r.value);
</script>
""")


def got(page, type):
    return [d for t, d in page.evaluate("window.got") if t == type]


def main():
    sb = Sandbox(PORT)
    try:
        project = sb.dir / "proj"
        project.mkdir()
        mcp = Mcp(sb, project)
        mcp.init()
        setup = mcp.call("channel_setup")
        s, root, base = setup["session"], project / "ai/html-channel", setup["url_base"]
        write_page(root, s, "controls", CONTROLS)
        write_page(root, s, "broken", BROKEN)
        wait_for(lambda: sb.url(root, s, "controls") is not None, 15, "server not up")

        with sync_playwright() as pw:
            browser = pw.chromium.launch()
            page = browser.new_page()
            page.goto(base + "controls.html")
            expect(page.locator("#decide-offline")).to_be_hidden(timeout=4500)

            # 1.2.3 scrub: start value, keys, snapping to the step, clamping, dragging
            if got(page, "hc-param")[:1] != [{"name": "n", "value": 4}]:
                fail("scrub didn't send its start value: %s" % page.evaluate("window.got"))
            page.focus("#n"); page.keyboard.press("ArrowRight")
            expect(page.locator("#n")).to_have_text("6")
            page.keyboard.press("End")
            expect(page.locator("#n")).to_have_attribute("aria-valuenow", "10")
            page.keyboard.press("ArrowRight")
            if got(page, "hc-param")[-1] != {"name": "n", "value": 10}:
                fail("scrub went past its max: %s" % got(page, "hc-param"))
            box = page.locator("#n").bounding_box()
            page.mouse.move(box["x"] + 2, box["y"] + 2); page.mouse.down()
            page.mouse.move(box["x"] - 14, box["y"] + 2); page.mouse.up()
            expect(page.locator("#n")).to_have_text("2")
            print("scrub ok: start value, arrow keys step by 2, End clamps at 10, a 16 px drag left takes 4 steps")

            # 1.2.4 state link: the demo gets the state, and the scrub named in it moves
            page.click("#six")
            if got(page, "hc-state")[-1] != {"n": 6, "mode": "x"}:
                fail("state link: %s" % got(page, "hc-state"))
            expect(page.locator("#n")).to_have_text("6")
            if not page.url.endswith("controls.html"):
                fail("state link navigated to %s" % page.url)
            print("state link ok: hc-state sent, the scrub moved to 6, no navigation")

            # 1.2.5 timeline: plays from 0, steps, scrubs
            times = got(page, "hc-time")
            if times[0] != {"t": 0, "playing": False} or not any(t["t"] > 0 for t in times):
                fail("timeline didn't start at 0 and play: %s" % times[:3])
            page.click("#tl .hc-tl-play")  # pause
            expect(page.locator("#tl .hc-tl-play")).to_have_attribute("aria-label", "Play")
            page.fill("#tl input[type=range]", "1")
            if abs(got(page, "hc-time")[-1]["t"] - 1) > 1e-9:
                fail("timeline scrub: %s" % got(page, "hc-time")[-1])
            page.click("#tl [aria-label='Step forward']")
            if abs(got(page, "hc-time")[-1]["t"] - 1.02) > 1e-9:
                fail("timeline step: %s" % got(page, "hc-time")[-1])
            print("timeline ok: plays from 0, pauses, scrubs to 1 s, steps 0.02 s")

            # 1.2.6 global pause: stops a playing timeline, and resumes it
            page.click("#tl .hc-tl-play")
            expect(page.locator("#tl .hc-tl-play")).to_have_attribute("aria-label", "Pause")
            page.click("#hc-pause")
            expect(page.locator("#hc-pause")).to_have_attribute("aria-pressed", "true")
            expect(page.locator("#tl .hc-tl-play")).to_have_attribute("aria-label", "Play")
            if got(page, "hc-pause")[-1] != {"paused": True} or not page.evaluate("window.htmlChannel.paused"):
                fail("global pause: %s" % got(page, "hc-pause"))
            page.click("#hc-pause")
            expect(page.locator("#tl .hc-tl-play")).to_have_attribute("aria-label", "Pause")
            print("pause ok: the global pause stops the timeline and resumes it")

            # 1.2.1, 1.2.2 KaTeX: display and inline render, broken TeX keeps its source and is reported
            expect(page.locator(".hc-viz .katex-display")).to_have_count(1, timeout=20000)
            expect(page.locator("span.hc-viz .katex")).to_have_count(1)
            expect(page.locator(".hc-viz-error")).to_have_count(1)
            wait_for(lambda: any(e["kind"] == "viz" and e["source"] == "katex#2"
                                 for e in mcp.call("get_decisions", page="controls")["errors"]),
                     10, "broken TeX not reported")
            print("katex ok: display and inline math render; broken TeX reported as viz katex#2")

            # 1.1.3 the controls record nothing
            if any(e["page"] == "controls" for e in entries(root, s)):
                fail("controls recorded answers: %s" % entries(root, s))
            print("answers ok: the controls recorded nothing")

            # reduced motion: the pause starts on, the timeline doesn't play until it's lifted
            calm = browser.new_page(reduced_motion="reduce")
            calm.goto(base + "controls.html")
            expect(calm.locator("#hc-pause")).to_have_attribute("aria-pressed", "true")
            calm.wait_for_timeout(500)
            if any(t["t"] > 0 for t in got(calm, "hc-time")):
                fail("timeline played under reduced motion")
            calm.click("#hc-pause")
            expect(calm.locator("#tl .hc-tl-play")).to_have_attribute("aria-label", "Pause")
            print("reduced motion ok: starts paused; lifting the pause starts the timeline")

            # 1.4 the lint runs on every load of an explorable, without a check
            page.goto(base + "broken.html")
            wait_for(lambda: len([e for e in mcp.call("get_decisions", page="broken")["errors"]
                                  if e["kind"] == "explorable"]) >= 6, 10, "lint findings not reported")
            if any(e["kind"] in ("inert", "unprobed") for e in mcp.call("get_decisions", page="broken")["errors"]):
                fail("the probe ran outside a check")
            print("lint ok: findings reported on a reader's load; the probe didn't run")
            browser.close()

        # 1.4, 1.5, 1.6.4 check_page reports every lint rule, inert and unprobed
        if os.environ.get("HTML_CHANNEL_CHROME") or shutil.which("google-chrome") or shutil.which("chromium") \
                or pathlib.Path("/Applications/Google Chrome.app").exists():
            r = mcp.call("check_page", timeout=180, page="broken")
            found = sorted((e["kind"], e.get("source")) for e in r["errors"])
            want = sorted([("explorable", "sections"), ("explorable", "no-instrument"), ("explorable", "no-limits"),
                           ("explorable", "no-sources"), ("explorable", "unlabeled-control"),
                           ("explorable", "data-set"), ("inert", "dead"), ("unprobed", "still")])
            if found != want:
                fail("check_page on the broken explorable:\n got  %s\n want %s" % (found, want))
            print("check ok: every lint rule, inert and unprobed reported; the responsive instrument passed")
        else:
            print("check skipped: no Chrome")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

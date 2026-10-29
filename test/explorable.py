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
<div data-instrument="demo2"><p>second demo</p></div>
<div id="once" data-timeline="demo2" data-duration="0.3" data-loop="false" data-autoplay="false"></div>
<p><span id="f" data-scrub="f" data-target="demo2" data-min="0.5" data-max="1" data-step="0.01" data-value="0.5">0.5</span>
   <a id="f67" data-set="demo2" data-state='{"f": 0.67}'>0.67</a></p>
<script>
  window.got = [];
  const demo = document.querySelector('[data-instrument=demo]');
  for (const t of ["hc-param", "hc-state", "hc-time"])
    demo.addEventListener(t, e => { window.got.push([t, e.detail]); });
  document.addEventListener("hc-pause", e => window.got.push(["hc-pause", e.detail]));
  document.querySelector('[data-instrument=demo2]')
    .addEventListener("hc-time", e => window.got.push(["hc-time2", e.detail]));
</script>
""")

# everything the lint and the probe report, once each; "frame" and "form" respond and pass
BROKEN = HEAD % ('<meta name="hc-kind" content="explorable">', """
<section data-concept="frame"><h2>Redraws in a frame</h2>
  <div data-instrument="frame"><button type="button" class="b">Go</button><output></output></div></section>
<section data-concept="form"><h2>Submit button</h2>
  <div data-instrument="form"><form><button class="b">Go</button></form><output></output></div></section>
<section data-concept="scrubbed"><h2>Scrub inside, ignored</h2>
  <div data-instrument="scrubbed"><span data-scrub="k" data-target="scrubbed" data-min="0" data-max="3">0</span><p>static</p></div></section>
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
  for (const id of ["frame", "form"]) {
    const el = document.querySelector(`[data-instrument=${id}]`);
    el.querySelector(".b").addEventListener("click", () =>
      requestAnimationFrame(() => el.querySelector("output").textContent = "clicked"));
  }
</script>
""")

# reversed meta attributes, and more controls than the check's time allows
SLOW = HEAD % ('<meta content="explorable" name="hc-kind">', "".join(
    '<section data-concept="s%d"><h2>%d</h2><div data-instrument="s%d">%s</div></section>'
    % (i, i, i, "".join('<button type="button">%d</button>' % j for j in range(60))) for i in range(2)))


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
        write_page(root, s, "slow", SLOW)
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

            # pause and play within one frame run one loop, not two: 0.4 s plays about 0.4 s
            page.click("#tl .hc-tl-play")
            page.fill("#tl input[type=range]", "0")
            page.evaluate("""() => { const b = document.querySelector('#tl .hc-tl-play');
                                     b.click(); b.click(); b.click(); }""")
            page.wait_for_timeout(400)
            page.click("#tl .hc-tl-play")
            t = got(page, "hc-time")[-1]["t"]
            if not 0.2 < t < 0.65:
                fail("timeline after pause/play in one frame played %.2f s in 0.4 s" % t)
            print("timeline ok: pause and play in one frame keep one loop (%.2f s in 0.4 s)" % t)

            # a timeline that doesn't loop stops at its end, and Play starts it over
            page.click("#once .hc-tl-play")
            expect(page.locator("#once .hc-tl-play")).to_have_attribute("aria-label", "Play", timeout=3000)
            if got(page, "hc-time2")[-1]["t"] != 0.3:
                fail("non-looping timeline didn't stop at its end: %s" % got(page, "hc-time2")[-1])
            page.click("#once .hc-tl-play")
            if got(page, "hc-time2")[-1]["t"] != 0:
                fail("Play at the end didn't start over: %s" % got(page, "hc-time2")[-1])
            print("timeline ok: a non-looping timeline stops at its end; Play starts it over")

            # a scrub value has no floating-point noise
            page.click("#f67")
            expect(page.locator("#f")).to_have_attribute("aria-valuenow", "0.67")
            print("scrub ok: 0.5 + 17 steps of 0.01 is exactly 0.67")

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

            # the reader's own pause outranks resuming after the global pause
            calm.reload()
            expect(calm.locator("#hc-pause")).to_have_attribute("aria-pressed", "true")
            calm.click("#tl .hc-tl-play"); calm.click("#tl .hc-tl-play")
            calm.click("#hc-pause")
            calm.wait_for_timeout(300)
            expect(calm.locator("#tl .hc-tl-play")).to_have_attribute("aria-label", "Play")
            print("pause ok: a timeline the reader paused stays paused when the global pause lifts")

            # 1.4 the lint runs on every load of an explorable, without a check
            page.goto(base + "broken.html")
            wait_for(lambda: len([e for e in mcp.call("get_decisions", page="broken")["errors"]
                                  if e["kind"] == "explorable"]) >= 5, 10, "lint findings not reported")
            if any(e["kind"] in ("inert", "unprobed") for e in mcp.call("get_decisions", page="broken")["errors"]):
                fail("the probe ran outside a check")
            print("lint ok: findings reported on a reader's load; the probe didn't run")
            browser.close()

        # 1.4, 1.5, 1.6.4 check_page reports every lint rule, inert and unprobed
        if os.environ.get("HTML_CHANNEL_CHROME") or shutil.which("google-chrome") or shutil.which("chromium") \
                or pathlib.Path("/Applications/Google Chrome.app").exists():
            r = mcp.call("check_page", timeout=180, page="broken")
            found = sorted((e["kind"], e.get("source")) for e in r["errors"])
            want = sorted([("explorable", "no-instrument"), ("explorable", "no-limits"),
                           ("explorable", "no-sources"), ("explorable", "unlabeled-control"),
                           ("explorable", "data-set"), ("inert", "dead"), ("inert", "scrubbed"), ("unprobed", "still")])
            if found != want:
                fail("check_page on the broken explorable:\n got  %s\n want %s" % (found, want))
            print("check ok: every lint rule, inert and unprobed reported; a frame-drawn and a form instrument passed")
            slow = mcp.call("check_page", timeout=180, page="slow")
            if [e["kind"] for e in slow["errors"]][:1] != ["probe-cut-off"] \
                    or ("explorable", "sections") not in [(e["kind"], e.get("source")) for e in slow["errors"]]:
                fail("check_page on a page the probe can't finish: %s" % slow["errors"])
            print("check ok: a probe cut off by the time budget is reported as probe-cut-off")
        else:
            print("check skipped: no Chrome")
    finally:
        sb.close()


if __name__ == "__main__":
    run(main)

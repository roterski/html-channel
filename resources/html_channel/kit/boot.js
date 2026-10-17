// html-channel boot script. A classic (non-module) script, loaded in <head>
// before any other script, so it runs before anything on the page can fail or
// answer. The runtime (decide.mjs) is a module, so it starts only after the
// page has parsed; this script covers the time until then.
//
// 1. Error reporting: failed loads (script, link, img…), uncaught errors and
//    unhandled rejections go to POST <page dir>/_errors. The server stamps the
//    revision and the time. window.htmlChannel.reportError(kind, message, source)
//    is the same path, for the runtime and visualization adapters. During a
//    headless check, alert/confirm/prompt are reported instead of shown.
// 2. Early answers: window.htmlChannel.record(id, value) and `change` events on
//    data-decision controls work from the first inline script. Until the runtime
//    starts they are kept in window.htmlChannel.early, which the runtime saves
//    and then sets to null.
//
// Pre: the page URL is /<mount>/<session>/<page>.html.
(function () {
  var dir = location.pathname.slice(0, location.pathname.lastIndexOf("/") + 1);
  var page = location.pathname.slice(dir.length).replace(/\.html?$/, "");
  var check = new URLSearchParams(location.search).get("_check");
  var hc = window.htmlChannel = window.htmlChannel || {};

  function post(route, body) {
    // no Content-Type header => a "simple" request, no preflight
    fetch(dir + route, { method: "POST", body: JSON.stringify(body), keepalive: true })
      .catch(function () {});
  }

  // ---- error reporting -------------------------------------------------------

  hc.reportError = function (kind, message, source) {
    var entry = { page: page, kind: kind, message: String(message).slice(0, 2000) };
    if (source) entry.source = String(source);
    if (check) entry.check = check;
    post("_errors", entry);
  };

  window.addEventListener("error", function (e) {
    var t = e.target;
    if (t && t !== window && t.nodeType === 1) {
      var url = t.src || t.href || t.currentSrc || "";
      hc.reportError("load", "failed to load <" + t.tagName.toLowerCase() + "> " + url, url);
    } else {
      hc.reportError("js", e.message || String(e.error),
                     e.filename ? e.filename + ":" + e.lineno + ":" + e.colno : null);
    }
  }, true);

  window.addEventListener("unhandledrejection", function (e) {
    var r = e.reason;
    hc.reportError("js", "unhandled rejection: " + (r && r.message ? r.message : String(r)), null);
  });

  // During a headless check, say the reporter is running: without this, a page
  // that lacks this script would look error-free to the check.
  // A dialog would block headless Chrome until the check times out, so here it
  // is reported and answered at once instead (alert: nothing, confirm: false,
  // prompt: null). Readers' pages keep the real dialogs.
  if (check) {
    post("_check", { page: page, check: check });
    [["alert", undefined], ["confirm", false], ["prompt", null]].forEach(function (d) {
      window[d[0]] = function (message) {
        hc.reportError("dialog", d[0] + "() would block the page: " + (message === undefined ? "" : message), null);
        return d[1];
      };
    });
  }

  // ---- early answers ---------------------------------------------------------

  // [{id, value}] for record() calls, [{el}] for changed controls (read when saved)
  hc.early = [];
  hc.record = function (id, value) {
    if (hc.early) hc.early.push({ id: id, value: value });
  };
  document.addEventListener("change", function (e) {
    var el = e.target;
    if (hc.early && el.dataset && el.dataset.decision && el.tagName !== "BUTTON") {
      hc.early.push({ el: el });
    }
  }, true);
})();

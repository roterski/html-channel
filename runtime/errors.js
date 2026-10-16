// html-channel error reporter. A classic (non-module) script, loaded in <head>
// before any library, so it is listening before anything else can fail.
//
// Reports failed loads (script, link, img…), uncaught errors and unhandled
// rejections to POST <page dir>/_errors. The server stamps the current revision.
// window.htmlChannel.reportError(kind, message, source) is the same path, for
// the runtime and visualization adapters.
//
// Pre: the page URL is /<mount>/<session>/<page>.html.
(function () {
  var dir = location.pathname.slice(0, location.pathname.lastIndexOf("/") + 1);
  var page = location.pathname.slice(dir.length).replace(/\.html?$/, "");
  var check = new URLSearchParams(location.search).get("_check");

  function reportError(kind, message, source) {
    var entry = { page: page, kind: kind, message: String(message).slice(0, 2000),
                  at: new Date().toISOString() };
    if (source) entry.source = String(source);
    if (check) entry.check = check;
    // no Content-Type header => a "simple" request, no preflight
    fetch(dir + "_errors", { method: "POST", body: JSON.stringify(entry), keepalive: true })
      .catch(function () {});
  }

  window.addEventListener("error", function (e) {
    var t = e.target;
    if (t && t !== window && t.nodeType === 1) {
      var url = t.src || t.href || t.currentSrc || "";
      reportError("load", "failed to load <" + t.tagName.toLowerCase() + "> " + url, url);
    } else {
      reportError("js", e.message || String(e.error),
                  e.filename ? e.filename + ":" + e.lineno + ":" + e.colno : null);
    }
  }, true);

  window.addEventListener("unhandledrejection", function (e) {
    var r = e.reason;
    reportError("js", "unhandled rejection: " + (r && r.message ? r.message : String(r)), null);
  });

  window.htmlChannel = window.htmlChannel || {};
  window.htmlChannel.reportError = reportError;
})();

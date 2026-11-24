(ns decide
  "Entry point loaded by pages: the core runtime over the http transport (over
  browser storage on an offline copy), then Download, the links between questions
  (moot), the depth switch, the visualization adapters, the statecharts, the explorable
  controls, code links, diffs and syntax highlighting.
  On an explorable page, also the structure lint. During a headless check, the
  interaction probe, on explorable pages and on pages with a statechart.
  The statecharts start before the explorable controls, so a scrub's start
  value reaches the chart it targets."
  (:require [decide.code-links :as code-links]
            [decide.core :as core]
            [decide.diff :as diff]
            [decide.download :as download]
            [decide.explore :as explore]
            [decide.highlight :as highlight]
            [decide.http :as http]
            [decide.lint :as lint]
            [decide.moot :as moot]
            [decide.offline :as offline]
            [decide.probe :as probe]
            [decide.statechart :as statechart]
            [decide.tiers :as tiers]
            [decide.viz :as viz]))

(core/start! (if-let [copy (offline/share-meta)] (offline/transport copy) (http/transport)))
(download/start!)
(moot/start! core/report-error!)
(tiers/start!)
(viz/start! core/report-error!)
(statechart/start! core/report-error!)
(explore/start! core/report-error!)
(code-links/start! core/report-error!)
(diff/start! core/report-error!)
(highlight/start! core/report-error!)
(when (explore/explorable?)
  (lint/start! core/report-error!))
(when (and (or (explore/explorable?) (statechart/present?))
           (.get (js/URLSearchParams. js/location.search) "_check"))
  (probe/start! core/report-error!))

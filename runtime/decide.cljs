(ns decide
  "Entry point loaded by pages: the core runtime over the http transport, then
  the depth switch, the visualization adapters, the statecharts, the explorable
  controls and code links.
  On an explorable page, also the structure lint. During a headless check, the
  interaction probe, on explorable pages and on pages with a statechart.
  The statecharts start before the explorable controls, so a scrub's start
  value reaches the chart it targets."
  (:require [decide.code-links :as code-links]
            [decide.core :as core]
            [decide.explore :as explore]
            [decide.http :as http]
            [decide.lint :as lint]
            [decide.probe :as probe]
            [decide.statechart :as statechart]
            [decide.tiers :as tiers]
            [decide.viz :as viz]))

(core/start! (http/transport))
(tiers/start!)
(viz/start! core/report-error!)
(statechart/start! core/report-error!)
(explore/start! core/report-error!)
(code-links/start! core/report-error!)
(when (explore/explorable?)
  (lint/start! core/report-error!))
(when (and (or (explore/explorable?) (statechart/present?))
           (.get (js/URLSearchParams. js/location.search) "_check"))
  (probe/start! core/report-error!))

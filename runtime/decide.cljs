(ns decide
  "Entry point loaded by pages: the core runtime over the http transport, then
  the depth switch, the visualization adapters and the explorable controls.
  On an explorable page, also the structure lint, and during a headless check
  the interaction probe."
  (:require [decide.core :as core]
            [decide.explore :as explore]
            [decide.http :as http]
            [decide.lint :as lint]
            [decide.probe :as probe]
            [decide.tiers :as tiers]
            [decide.viz :as viz]))

(core/start! (http/transport))
(tiers/start!)
(viz/start! core/report-error!)
(explore/start! core/report-error!)
(when (explore/explorable?)
  (lint/start! core/report-error!)
  (when (.get (js/URLSearchParams. js/location.search) "_check")
    (probe/start! core/report-error!)))

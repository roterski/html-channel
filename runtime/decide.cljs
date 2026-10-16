(ns decide
  "Entry point loaded by pages: the core runtime over the http transport, then
  the depth switch and the visualization adapters."
  (:require [decide.core :as core]
            [decide.http :as http]
            [decide.tiers :as tiers]
            [decide.viz :as viz]))

(core/start! (http/transport))
(tiers/start!)
(viz/start! core/report-error!)

(ns decide
  "Entry point loaded by pages: the core runtime over the http transport."
  (:require [decide.core :as core]
            [decide.http :as http]))

(core/start! (http/transport))

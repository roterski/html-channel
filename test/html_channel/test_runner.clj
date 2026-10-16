(ns html-channel.test-runner
  (:require [clojure.test :as t]
            [html-channel.registry-test]
            [html-channel.server-test]
            [html-channel.store-test]
            [html-channel.theme-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'html-channel.store-test
                                          'html-channel.registry-test
                                          'html-channel.server-test
                                          'html-channel.theme-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

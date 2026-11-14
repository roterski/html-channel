(ns html-channel.test-runner
  (:require [clojure.test :as t]
            [html-channel.check-test]
            [html-channel.registry-test]
            [html-channel.server-test]
            [html-channel.store-test]
            [html-channel.theme-test]
            [html-channel.wait-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'html-channel.check-test
                                          'html-channel.store-test
                                          'html-channel.registry-test
                                          'html-channel.server-test
                                          'html-channel.theme-test
                                          'html-channel.wait-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(ns html-channel.check-test
  (:require [clojure.test :refer [deftest is]]
            [html-channel.check :as check]))

(deftest explorable-page-in-any-attribute-order
  (is (check/explorable-page? "<meta name=\"hc-kind\" content=\"explorable\">"))
  (is (check/explorable-page? "<meta content=\"explorable\" name=\"hc-kind\">"))
  (is (check/explorable-page? "<META name='hc-kind'\n content='explorable' />"))
  (is (not (check/explorable-page? "<meta name=\"hc-kind\"><meta content=\"explorable\">")))
  (is (not (check/explorable-page? "<meta name=\"hc-libs\" content=\"katex\">"))))

(deftest settle-time-grows-with-instruments
  (is (= check/settle-ms (check/settle-for "<p data-instrument=\"a\">")))
  (is (= (+ check/settle-ms (* 2 check/probe-ms-per-instrument))
         (check/settle-for "<meta content=\"explorable\" name=\"hc-kind\"><div data-instrument=\"a\"></div><div data-instrument=\"b\"></div>"))))

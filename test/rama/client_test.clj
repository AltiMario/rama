(ns rama.client-test
  (:require [rama.client :refer :all]
            [clojure.test :refer :all])
  (:import [java.util Date TimeZone]))

(TimeZone/setDefault (TimeZone/getTimeZone "GMT+0"))

(deftest to-json-test
  (is (= "{\"foo\":\"bar\"}"
         (to-json {:foo :bar})))

  (is (nil? (to-json nil)))

  (is (= "[]" (to-json [])))

  (is (= "{\"date\":\"2017-01-01T00:00:00.000Z\"}"
         (to-json {:date (Date. (- 2017 1900) 0 1 0 0 0)}))))

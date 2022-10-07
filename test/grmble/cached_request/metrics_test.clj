(ns grmble.cached-request.metrics-test
  (:import
   [com.codahale.metrics Timer])
  (:require
   [clojure.test :refer [deftest is testing]]
   [grmble.cached-request.metrics :as metrics]
   [promesa.core :as p]))

(defonce ^Timer test-timer (.timer metrics/registry (metrics/metric-name "test" "total")))

(deftest test-metrics

  (testing "jmx reporter can be started and stopped"
    (let [reporter (metrics/jmx-reporter)]
      (is reporter)
      (.stop reporter)))

  (testing "console reporter can be started and stopped"
    (let [reporter (metrics/console-reporter)]
      (is reporter)
      (.stop reporter)))

  (testing "timed-promise will handle expressions, promises and exceptions"
    (is (= 1
           @(metrics/timed-promise test-timer 1)))
    (is (= 3
           @(metrics/timed-promise test-timer (p/future (+ 1 2)))))
    (is (p/rejected? (metrics/timed-promise test-timer (throw (ex-info "oh oh" {})))))))

(ns grmble.cached-request-test
  (:require [clojure.test :refer [deftest testing is]]
            [grmble.cached-request :as cr]
            [promesa.core :as p]))

(defn test-response [opts k]
  (some-> k
          {"clojure" "yay"
           "java" "boo"}
          (as-> body {:body body
                      :status (opts :status 200)
                      :headers (opts :headers {"content-type" "text-plain"})})))

(deftest test-cache-api
  (let [cache (cr/start-cache {:name "test"})]
    (try

      (testing "put-cache! and get-cached"
        (is (not (cr/get-cached cache "manual")))

        (cr/put-cache! cache "manual" (test-response {} "clojure"))
        (let [response (cr/get-cached cache "manual")]
          (is (= response
                 (test-response {} "clojure")))))

      (testing "getting uncached values"
        (doseq [k ["clojure" "java"]]
          (let [result @(cr/cached-result cache k (partial test-response {}))]
            (is (result :time))
            (is (= (dissoc result :time)
                   (test-response {} k))))))

      (testing "getting cached values"
        (doseq [k ["clojure" "java"]]
          (let [now (System/currentTimeMillis)
                result @(cr/cached-result cache k (partial test-response {}))]
            (is (result :time))
            (is (> now (result :time)))
            (is (= (dissoc result :time)
                   (test-response {} k))))))

      (testing "error returns are not cached"
        (let [failed-promise (cr/cached-result cache "unused-so-far"
                                               (fn [_] (throw (ex-info "oh noes!" {}))))]
          ;; the promise contains clojure.lang.ExceptionInfo
          ;; but derefing a rejected future will wrap this with ExecutionException
          (is (thrown? java.util.concurrent.ExecutionException @failed-promise))
          (is (not (cr/get-cached cache "unused-so-far")))))


      (finally (cr/stop-cache cache)))))


(ns grmble.cached-request.config-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [grmble.cached-request.config :as config]))

(defn- explain-keys [config]
  (set (keys (config/explain config))))

(deftest cache-configuration
  (testing "valid configurations"
    (is (= #{} (explain-keys {:name "test"})))
    (is (= #{} (explain-keys {:name "test"
                              :heap-entries 100
                              :offheap-size [512 :MB]})))
    (is (= #{} (explain-keys {:name "test"
                              :heap-entries 100
                              :offheap-size (* 512 1024 1024)})))
    (is (= #{} (explain-keys {:name "test"
                              :heap-entries 100
                              :offheap-size [512 :MB]
                              :disk {:size [5 :GB]
                                     :filename "test.cache"}})))
    (is (= #{} (explain-keys {:name "test" :ttl [12 :hours]})))
    (is (= #{} (explain-keys {:name "test" :ttl 30000}))))
  (testing "invalid configurations"
    (is (= #{:name} (explain-keys {})))
    (is (= #{:name} (explain-keys {:name ""})))
    (is (= #{:offheap-size} (explain-keys {:name "test" :offheap-size "xxx"})))))


(deftest cache-manager-builder
  (is (instance? org.ehcache.config.builders.CacheManagerBuilder
                 (config/cache-manager-builder {:name "test"
                                                :heap-entries 100
                                                :offheap-size [512 :MB]
                                                :disk {:size [5 :GB]
                                                       :filename "test.cache"}
                                                :keys :string
                                                :ttl [12 :hours]}))))

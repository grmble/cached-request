(ns grmble.cached-request.serializer-test
  (:require
   [clojure.test :refer [deftest is]]
   [grmble.cached-request.serializer :as s]))


(deftest test-serializer
  (doseq [obj [{:x 1 :y 2}
               [1.2 "it's a string"]
               ;; byte arrays can be read and written
               ;; but they have pointer comparison it seems
               ]]
    (let [ser (.serialize s/msgpack-serializer obj)
          obj2 (.read s/msgpack-serializer ser)]
      (is (.equals s/msgpack-serializer obj ser))
      (is (= obj obj2)))))

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
    (let [obj2 (->> obj
                    (.serialize s/msgpack-serializer)
                    (.read s/msgpack-serializer))]
      (is (= obj obj2)))))

(ns grmble.cached-request
  (:require
   [grmble.cached-request.ehcache :as ehcache]
   [promesa.core :as p]))

(def ^{:private true}
  active-requests (atom {}))

(defn cached-result
  "Retrieve the result from the cache, or perform the request and cache the result.
   
If a result for key `k` is found in the cache, it is returned
as a resolved promise.
   
Otherwise, a promise is returned that
the handler function will be called with `k`
and that the result will be cached.

Multiple parallel uncached requests will share the same promise,
i.e. the slow operation is executed only once.
   
Attention: the result should conform to the chosen value serializer
(default: jsonista with string keys) or it may change form
when retrieved from the offheap cache.
"
  [cache k handler]
  (if-let [{_time :time :as result} (ehcache/get-cached cache k)]
    (p/resolved result)
    (let [deferred (p/deferred)
          future (-> (p/future (let [result (handler k)]
                                 (if (or (p/promise? result)
                                         (future? result))
                                   @result
                                   result)))
                     (p/then #(ehcache/put-cache! cache k %)))]
      (swap! active-requests #(assoc % k deferred))
      (-> future
          (p/handle (fn [result error]
                      (if result
                        (p/resolve! deferred result)
                        (p/reject! deferred error))
                      (swap! active-requests #(dissoc % k)))))
      deferred)))

(defn- slow-result [k]
  (p/delay 500 {"k" k
                "str" (str "result for " k)}))

(comment

  (def xxx (ehcache/start-cache {:name "test"
                                 :heap-entries 10
                                 :offheap-size [10 :MB]
                                 :ttl [1 :hours]}))

  (ehcache/stop-cache xxx)

  (time @(slow-result "asdf"))

  (time @(cached-result xxx "asdf" slow-result)))

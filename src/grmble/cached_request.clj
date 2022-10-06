(ns grmble.cached-request
  (:import
   [com.codahale.metrics Meter Timer])
  (:require
   [grmble.cached-request.ehcache :as ehcache]
   [grmble.cached-request.metrics :as metrics]
   [promesa.core :as p]))

(def ^{:private true}
  active-requests (atom {}))

(defn- swap-deferred! [k]
  (let [d (p/deferred)
        d2 (-> (swap! active-requests #(assoc % k d))
               (get k))]
    (if (= d d2)
      [d2 true]
      [d2 false])))

(defn- annotate-with-time [result]
  (assoc result :time (System/currentTimeMillis)))

(defn- handle-request! [d cache k handler]
  (-> (p/future (let [result (handler k)]
                  (if (or (p/promise? result)
                          (future? result))
                    @result
                    result)))
      (p/then annotate-with-time)
      (p/then #(ehcache/put-cache! cache k %))
      (p/handle (fn [result error]
                  (if result
                    (p/resolve! d result)
                    (p/reject! d error))
                  (swap! active-requests #(dissoc % k))))))

(defn- deferred-handler [cache k handler]
  (let [[d must-handle] (swap-deferred! k)]
    (when must-handle
      (handle-request! d cache k handler))
    d))

(defn- stale? [{stale-after :stale-after} {time :time}]
  ;; stale-after is normalized to milliseconds in ehcache/start-cache
  (and stale-after
       (> (unchecked-subtract (System/currentTimeMillis) stale-after) time)))

(defonce ^Timer timer-total-request (.timer metrics/registry (metrics/metric-name "request" "total")))
(defonce ^Meter meter-cache-hit (.meter metrics/registry (metrics/metric-name "cache" "hits")))
(defonce ^Meter meter-cache-miss (.meter metrics/registry (metrics/metric-name "cache" "miss")))


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
  (let [ctx (.time timer-total-request)
        cached  (when-let [result (ehcache/get-cached cache k)]
                  (.mark meter-cache-hit)
                  (when (stale? cache result)
                    (deferred-handler cache k handler))
                  (p/resolved result))]
    (-> cached
        (or (.mark meter-cache-miss))
        (or (get @active-requests k))
        (or (deferred-handler cache k handler))
        (p/finally (fn [_ _] (.stop ctx))))))

(defn- slow-result [k]
  (println "running slow-result" k)
  (p/delay 500 {:status 200
                :headers {"Content-type" "text/plain"}
                :body (str "result for " k)}))

(comment

  (def xxx (ehcache/start-cache {:name "test"
                                 :heap-entries 10
                                 :offheap-size [10 :MB]
                                 :stale-after [15 :seconds]
                                 :ttl [1 :hours]}))

  (ehcache/stop-cache xxx)

  (time @(slow-result "asdf"))

  (metrics/jmx-reporter)

  (time @(cached-result xxx "xxx" slow-result)))

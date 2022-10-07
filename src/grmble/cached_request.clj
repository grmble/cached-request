(ns grmble.cached-request
  (:import
   [com.codahale.metrics Meter Timer]
   [clojure.lang IPersistentMap]
   [org.ehcache Cache CacheManager]
   [org.ehcache.config.builders CacheManagerBuilder])
  (:require
   [grmble.cached-request.config :as config]
   [grmble.cached-request.metrics :as metrics]
   [promesa.core :as p]))

(defrecord CacheMap
           [^CacheManager cache-manager
            ^Cache cache
            ^Long stale-after])


(defn start-cache
  "Creates a 'CacheManager' and 'Cache' from `cfg`."
  [cfg]
  (let [^CacheManagerBuilder builder (config/cache-manager-builder cfg)
        ^CacheManager cache-manager (.build builder true)
        cache (.getCache cache-manager (:name cfg) String IPersistentMap)
        stale-after (config/duration-in-millis (:stale-after cfg))]
    (->CacheMap cache-manager cache stale-after)))

(defn stop-cache
  "Stops a cache created by 'start-cache'."
  [{^CacheManager cache-manager :cache-manager}]
  (.close cache-manager))

(defn put-cache!
  "Put a value `m` (usually a map) in the `cache` under the key `k`."
  [{^Cache cache :cache} ^String k m]
  (.put cache k m)
  m)

(defn get-cached
  "Get the cached value from `cache` under the key  `k`.
   
   Returns `nil` if there is no entry in the cache."
  [{^Cache cache :cache} ^String k]
  (.get cache k))

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
  (-> (p/do! (handler k))
      (p/then annotate-with-time)
      (p/then #(put-cache! cache k %))
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
   
The handler should NOT block - it should return immediately or return
a promise.  I used to wrap this in a future, but this does not
really buy anything - blocking code will blow up the default executor,
and well behaved code is punished by the extra future execution.
"
  [cache k handler]
  (metrics/timed-promise
   timer-total-request
   (-> (when-let [result (get-cached cache k)]
         (.mark meter-cache-hit)
         (when (stale? cache result)
           (deferred-handler cache k handler))
         (p/resolved result))
       (or (.mark meter-cache-miss))
       (or (get @active-requests k))
       (or (deferred-handler cache k handler)))))

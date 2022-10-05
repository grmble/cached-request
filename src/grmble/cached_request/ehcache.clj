(ns grmble.cached-request.ehcache
  (:import
   [clojure.lang IPersistentMap]
   [org.ehcache Cache CacheManager]
   [org.ehcache.config.builders CacheManagerBuilder])
  (:require
   [grmble.cached-request.config :as config]))

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

(defn stop-cache [{^CacheManager cache-manager :cache-manager}]
  (.close cache-manager))

(defn put-cache! [{^Cache cache :cache} ^String k m]
  (.put cache k m)
  m)

(defn get-cached [{^Cache cache :cache} ^String k]
  (.get cache k))


(comment

  (set! *warn-on-reflection* true)

  (def xxx (start-cache {:name "test"
                         :heap-entries 10
                         :offheap-size [10 :MB]
                         :ttl [30 :seconds]}))

  (stop-cache xxx)


  (doseq [n (range 20)]
    (put-cache! xxx (str n) {:n n :s (str n)}))


  (time (get-cached xxx "19"))

  (time (get-cached xxx "2")))

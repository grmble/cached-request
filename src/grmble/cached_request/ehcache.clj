(ns grmble.cached-request.ehcache
  (:import
   [clojure.lang IPersistentMap]
   [org.ehcache Cache CacheManager])
  (:require
   [grmble.cached-request.config :as config]))

(defn start-cache
  "Creates a 'CacheManager' and 'Cache' from `cfg`."
  [cfg]
  (let [^CacheManager cache-manager (-> cfg
                                        config/cache-manager-builder
                                        (.build true))
        cache (.getCache cache-manager (:name cfg) String IPersistentMap)]
    {:cache-manager cache-manager
     :cache cache}))

(defn stop-cache [{^CacheManager cache-manager :cache-manager}]
  (.close cache-manager))

(defn put-cache! [{^Cache cache :cache} ^String k m]
  (.put cache k m)
  m)

(defn get-cached [{^Cache cache :cache} ^String k]
  (.get cache k))


(comment

  (def xxx (start-cache {:name "test"
                         :heap-entries 10
                         :offheap-size [10 :MB]
                         :ttl [30 :seconds]}))

  (stop-cache xxx)


  (doseq [n (range 20)]
    (put-cache! xxx (str n) {:n n :s (str n)}))


  (time (get-cached xxx "19"))

  (time (get-cached xxx "2")))

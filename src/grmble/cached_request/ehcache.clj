(ns grmble.cached-request.ehcache
  (:import
   [java.io ByteArrayInputStream PushbackReader InputStreamReader]
   [java.nio.charset StandardCharsets]
   [org.ehcache Cache CacheManager])
  (:require
   [clojure.edn :as edn]
   [grmble.cached-request.config :as config]))

(defn start-cache
  "Creates a 'CacheManager' and 'Cache' from `cfg`."
  [cfg]
  (let [^CacheManager cache-manager (-> cfg
                                        config/cache-manager-builder
                                        (.build true))
        cache (.getCache cache-manager (:name cfg) String (Class/forName "[B"))]
    {:cache-manager cache-manager
     :cache cache}))

(defn stop-cache [{^CacheManager cache-manager :cache-manager}]
  (.close cache-manager))

(defn put-cache! [{^Cache cache :cache} ^String k ^bytes bs]
  (.put cache k bs)
  bs)

(defn get-cached ^bytes [{^Cache cache :cache} ^String k]
  (.get cache k))

(defn print-bytes [x]
  (let [s (pr-str x)]
    (.getBytes s StandardCharsets/UTF_8)))

(defn read-bytes [^bytes bs]
  (-> bs
      (ByteArrayInputStream.)
      (InputStreamReader.)
      (PushbackReader.)
      (edn/read)))

(comment

  (let [x (start-cache {:name "test"})]
    (try
      (put-cache! x "asdf" (print-bytes {:x 1}))
      (println "cached"
               (read-bytes (.get (:cache x) "asdf")))
      (finally (stop-cache x)))))

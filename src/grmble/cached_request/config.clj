(ns grmble.cached-request.config
  "Ehcache configuration"
  (:import
   [clojure.lang IPersistentMap]
   [java.time Duration]
   [org.ehcache.config.units EntryUnit MemoryUnit]
   [org.ehcache.config.builders CacheConfigurationBuilder CacheManagerBuilder
    ExpiryPolicyBuilder ResourcePoolsBuilder])
  (:require
   [grmble.cached-request.serializer :as s]
   [malli.core :as m]
   [malli.error :as me]))

(def SizeSchema
  [:or :int [:tuple :int [:enum :KB :MB :GB]]])

(def DurationSchema
  [:or :int [:tuple :int [:enum :seconds :minutes :hours]]])

(def CacheConfigSchema
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:heap-entries {:optional true} int?]
   [:offheap-size {:optional true} SizeSchema]
   [:disk {:optional true}
    [:map {:closed true}
     [:size SizeSchema]
     [:filename [:string {:min 1}]]]]
   [:stale-after {:optional true} DurationSchema]
   [:ttl {:optional true} DurationSchema]])

(defn explain
  "Explain errors for a cache configuration

 ```clojure
   (explain {:name \"test\", :heap-entry 10})
   ; => {:heap-entry [\"should be spelled :heap-entries\"]}
 ```  
   "

  [cfg]
  (-> CacheConfigSchema
      (m/explain cfg)
      (me/with-spell-checking)
      (me/humanize)))

(def ^{:private true} size-factors
  {:KB 1024
   :MB (* 1024 1024)
   :GB (* 1024 1024 1024)})

(defn- size-in-bytes [sz]
  (if (vector? sz)
    (let [[n unit] sz]
      (* n (size-factors unit)))
    sz))

(def ^{:private true} duration-factors
  {:seconds 1000
   :minutes (* 60 1000)
   :hours (* 60 60 1000)})

(defn duration-in-millis
  "Turn a rich duration into milliseconds.
   
   ```clojure
   (duration-in-millis [10 :seconds])
   ;; => 10000
   
   (duration-in-millis 1)
   ;; => 1
   ```
   "
  [duration]
  (if (vector? duration)
    (let [[n unit] duration]
      (* n (duration-factors unit)))
    duration))

(defn- nonnull-setter
  ([builder cfg ks handler]
   (nonnull-setter builder cfg ks handler 0))

  ([builder cfg ks handler default-size]
   (let [size (size-in-bytes (get-in cfg ks default-size))]
     (if (> size 0)
       (handler builder size)
       builder))))

(defn- heap-setter [^ResourcePoolsBuilder rpb size]
  (.heap rpb size EntryUnit/ENTRIES))

(defn- offheap-setter [^ResourcePoolsBuilder rpb size]
  (.offheap rpb size MemoryUnit/B))

(defn- disk-setter [^ResourcePoolsBuilder rpb size]
  (.disk rpb size MemoryUnit/B true))

(defn- persistence-setter [{{filename :filename} :disk}]
  (fn [^CacheManagerBuilder builder _]
    (.with builder (CacheManagerBuilder/persistence
                    (java.io.File. filename)))))

(defn- resource-pools-builder [cfg]
  (-> (ResourcePoolsBuilder/newResourcePoolsBuilder)
      (nonnull-setter cfg [:heap-entries] heap-setter 10)
      (nonnull-setter cfg [:offheap-size] offheap-setter)
      (nonnull-setter cfg [:disk :size] disk-setter)))

(defn- ttl-setter [^CacheConfigurationBuilder builder ttl]
  (if ttl
    (->> ttl
         (duration-in-millis)
         (Duration/ofMillis)
         (ExpiryPolicyBuilder/timeToLiveExpiration)
         (.withExpiry builder))
    builder))

(defn- cache-builder [cfg]
  (-> (CacheConfigurationBuilder/newCacheConfigurationBuilder
       String
       IPersistentMap
       (resource-pools-builder cfg))
      (ttl-setter (:ttl cfg))
      (.withValueSerializer s/msgpack-serializer)))

(def cache-manager-builder
  "Create a 'CacheManagerBuilder for `cfg`."
  (m/-instrument
   {:schema [:=> [:cat CacheConfigSchema] any?]}

   (fn [cfg]
     (-> (CacheManagerBuilder/newCacheManagerBuilder)
         (nonnull-setter cfg [:disk :size] (persistence-setter cfg))
         (.withCache (:name cfg) (cache-builder cfg))))))


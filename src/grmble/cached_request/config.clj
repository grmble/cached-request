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


(defn- resource-pools-builder ^ResourcePoolsBuilder [cfg]
  (cond-> (ResourcePoolsBuilder/newResourcePoolsBuilder)
    true (.heap (cfg :heap-entries 10) EntryUnit/ENTRIES)
    (cfg :offheap-size) (.offheap (size-in-bytes (cfg :offheap-size)) MemoryUnit/B)
    (-> cfg :disk :size) (.disk (size-in-bytes (-> cfg :disk :size)) MemoryUnit/B true)))


(defn- cache-builder ^CacheConfigurationBuilder [cfg]
  (cond-> (CacheConfigurationBuilder/newCacheConfigurationBuilder
           String
           IPersistentMap
           (resource-pools-builder cfg))
    (cfg :ttl) (.withExpiry (-> (cfg :ttl)
                                (duration-in-millis)
                                (Duration/ofMillis)
                                (ExpiryPolicyBuilder/timeToLiveExpiration)))
    true (.withValueSerializer s/msgpack-serializer)))

(def cache-manager-builder
  "Create a 'CacheManagerBuilder for `cfg`."
  (m/-instrument
   {:schema [:=> [:cat CacheConfigSchema] any?]}

   (fn [cfg]
     (cond-> (CacheManagerBuilder/newCacheManagerBuilder)
       (-> cfg :disk :size) (.with (CacheManagerBuilder/persistence (java.io.File. ^String (get-in cfg [:disk :filename]))))
       true (.withCache ^String (cfg :name) (cache-builder cfg))))))

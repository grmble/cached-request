(ns grmble.cached-request.config
  "Ehcache configuration"
  (:import
   [org.ehcache.config.units EntryUnit MemoryUnit]
   [org.ehcache.config.builders CacheConfigurationBuilder CacheManagerBuilder ResourcePoolsBuilder])
  (:require
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

(defn- cache-builder [cfg]
  (CacheConfigurationBuilder/newCacheConfigurationBuilder
   String
   (Class/forName "[B")
   (resource-pools-builder cfg)))

(def cache-manager-builder
  "Create a 'CacheManagerBuilder for `cfg`."
  (m/-instrument
   {:schema [:=> [:cat CacheConfigSchema] any?]}

   (fn [cfg]
     (-> (CacheManagerBuilder/newCacheManagerBuilder)
         (nonnull-setter cfg [:disk :size] (persistence-setter cfg))
         (.withCache (:name cfg) (cache-builder cfg))))))


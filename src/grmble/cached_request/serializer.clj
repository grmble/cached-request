(ns grmble.cached-request.serializer
  (:import
   [java.nio ByteBuffer]
   [org.ehcache.spi.serialization Serializer])
  (:require
   [msgpack.core :as msg]
   [msgpack.clojure-extensions]))

(def ^{:doc "Serializes clojure values via msg pack.
             
Off-heap or on disk the cached response will be stored
in the format delivered by this serializer."
       :tag Serializer}
  msgpack-serializer
  (reify Serializer
    (serialize [_ obj]
      (-> obj
          (msg/pack)
          (ByteBuffer/wrap)))
    (read [_ ^ByteBuffer bb]
      (-> bb
          (.array)
          (msg/unpack)))
    (equals [this obj bb]
      (let [obj2 (.read this bb)]
        (= obj obj2)))))

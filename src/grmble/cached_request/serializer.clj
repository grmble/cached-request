(ns grmble.cached-request.serializer
  (:import
   [java.nio ByteBuffer]
   [org.ehcache.spi.serialization Serializer])
  (:require
   [jsonista.core :as j]))

(def ^{:doc "Serializes cached values as JSON (string keys)

The default object mapper is used which deserializes
the JSON with string keys.  This seems to be faster when
reading from the cache, also it does not blow up the
symbol table.
"} jsonista-with-string-keys
  (reify Serializer
    (serialize [_ obj]
      (-> obj
          (j/write-value-as-bytes)
          (ByteBuffer/wrap)))
    (read [_ ^ByteBuffer bb]
      (-> bb
          (.array)
          (j/read-value)))
    (equals [this obj bb]
      (let [obj2 (.read this bb)]
        (= obj obj2)))))

(def ^{:doc "Serializes cached values as JSON (keyword keys)"}
  jsonista-with-keywords-keys
  (reify Serializer
    (serialize [_ obj]
      (-> obj
          (j/write-value-as-bytes j/keyword-keys-object-mapper)
          (ByteBuffer/wrap)))
    (read [_ ^ByteBuffer bb]
      (-> bb
          (.array)
          (j/read-value j/keyword-keys-object-mapper)))
    (equals [this obj bb]
      (let [obj2 (.read this bb)]
        (= obj obj2)))))

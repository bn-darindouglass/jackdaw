(ns jackdaw.serdes.avro2.uuid
  (:require [jackdaw.serdes.avro2 :as avro])
  (:import (java.util UUID)))

(defrecord StringUUIDType []
  avro/SchemaType
  (match-clj? [_ uuid]
    (instance? UUID uuid))
  (match-avro? [_ uuid-str]
    (string? uuid-str))
  (avro->clj [_ uuid-utf8]
    (UUID/fromString (str uuid-utf8)))
  (clj->avro [_ uuid]
    (str uuid)))

(defmethod avro/schema-type
  {:type "string" :logical-type "jackdaw.serdes.avro.UUID"}
  [_]
  (StringUUIDType.))

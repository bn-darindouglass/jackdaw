(ns jackdaw.serdes.resolver-test
  (:require
    [clj-uuid :as uuid]
    [clojure.test :refer [deftest is testing] :as test]
    [jackdaw.serdes.avro :as avro]
    [jackdaw.serdes]
    [jackdaw.serdes.json]
    [jackdaw.serdes.edn]
    [jackdaw.serdes.resolver :as resolver]
    [jackdaw.serdes.avro.schema-registry :as reg])
  (:import (clojure.lang ExceptionInfo)
           (org.apache.kafka.common.serialization Serde)))


(deftest load-schema-test
  (testing "a schema can be loaded"
    (is (not (nil? (resolver/load-schema {:schema-filename "resources/example_schema.avsc"})))))

  (testing "an error is thrown if the schema is not found"
    (is (thrown-with-msg? ExceptionInfo
                          #"Could not find schema.*"
                          (resolver/load-schema {:schema-filename "cant-find-me"}))))
  (testing "an error is thrown if the schema name is not present"
    (is (thrown-with-msg? ExceptionInfo
                          #"No :schema-filename defined in serde config"
                          (resolver/load-schema {})))))

(deftest find-serde-var-test
  (testing "Can resolve various serdes names"
    (is (not (nil?
               (resolver/find-serde-var
                 {:serde-keyword :jackdaw.serdes.avro/serde}))))
    (is (not (nil?
               (resolver/find-serde-var
                 {:serde-keyword :jackdaw.serdes/string-serde}))))
    (is (not (nil?
               (resolver/find-serde-var
                 {:serde-keyword :jackdaw.serdes.json/serde}))))
    (is (not (nil?
               (resolver/find-serde-var
                 {:serde-keyword :jackdaw.serdes.edn/serde})))))
  (testing "error is thrown if the name is not known"
    (is (thrown-with-msg? ExceptionInfo
                          #"Could not resolve :serde-keyword value to a serde function"
                          (resolver/find-serde-var
                            {:serde-keyword :bibbity.bobbity/boo})))))

(deftest serdes-resolver-test
  (testing "resolving serdes based on config"
    (testing "string serdes"
      (let [resolver-fn (resolver/serde-resolver)
            string-config {:serde-keyword :jackdaw.serdes/string-serde}
            resolved (resolver-fn string-config)]
        (is (instance? Serde resolved))
        (is (instance? Serde resolved))
        ;; round trip test
        (is (= "foo"
               (->> "foo"
                    (.serialize (.serializer resolved) "string-topic")
                    (.deserialize (.deserializer resolved) "string-topic"))))))

    (testing "avro serdes"
      (let [resolver-fn (resolver/serde-resolver :schema-registry-url ""
                                                 :schema-registry-client (reg/mock-client))
            avro-config {:serde-keyword :jackdaw.serdes.avro.confluent/serde
                         :schema-filename "resources/example_schema.avsc"
                         :key? false}
            resolved (resolver-fn avro-config)
            example-data {:customer-id (str (uuid/v4))
                          :address {:value "foo"
                                    :key-path "foo.bar.baz"}}]
        (is (instance? Serde resolved))
        (is (instance? Serde resolved))
        ;; round trip test
        (is (= example-data
               (->> example-data
                    (.serialize (.serializer resolved) "avro-topic")
                    (.deserialize (.deserializer resolved) "avro-topic"))))))

    (testing "avro serdes with UUID logical type"
      (let [resolver-fn (resolver/serde-resolver :schema-registry-url ""
                                                 :schema-registry-client (reg/mock-client)
                                                 :type-registry (merge
                                                                  avro/+base-schema-type-registry+
                                                                  avro/+UUID-type-registry+))
            avro-config {:serde-keyword :jackdaw.serdes.avro.confluent/serde
                         :schema-filename "resources/example_schema.avsc"
                         :key? false}
            resolved (resolver-fn avro-config)
            example-data {:customer-id (uuid/v4) ; UUID as an actual object
                          :address {:value "foo"
                                    :key-path "foo.bar.baz"}}]
        (is (instance? Serde resolved))
        (is (instance? Serde resolved))
        ;; round trip test
        (is (= example-data
               (->> example-data
                    (.serialize (.serializer resolved) "avro-topic")
                    (.deserialize (.deserializer resolved) "avro-topic"))))))

    (testing "bad config"
      (is (thrown-with-msg? ExceptionInfo
                            #"Invalid serde config.*"
                            ((resolver/serde-resolver) {}))))))

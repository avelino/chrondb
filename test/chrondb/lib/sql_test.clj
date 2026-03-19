(ns chrondb.lib.sql-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [chrondb.lib.core :as lib]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def ^:dynamic *test-db-dir* nil)

(defn- create-temp-dir []
  (str (Files/createTempDirectory "chrondb-sql-test" (make-array FileAttribute 0))))

(defn- delete-directory [path]
  (when (.exists (io/file path))
    (doseq [f (reverse (file-seq (io/file path)))]
      (try (io/delete-file f true) (catch Exception _)))))

(defn temp-dirs-fixture [f]
  (let [db-dir (create-temp-dir)]
    (binding [*test-db-dir* db-dir]
      (try (f)
           (finally (delete-directory db-dir))))))

(use-fixtures :each temp-dirs-fixture)

(deftest test-sql-insert-and-select
  (testing "SQL INSERT followed by SELECT should work"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (is (>= handle 0))

        ;; Insert via SQL
        (let [result-json (lib/lib-execute-sql handle
                                               "INSERT INTO users (id, name, email) VALUES ('user1', 'Alice', 'alice@example.com')"
                                               nil)
              result (json/read-str result-json :key-fn keyword)]
          (is (= "insert" (:type result)))
          (is (= 1 (:affected result))))

        ;; Select via SQL
        (let [result-json (lib/lib-execute-sql handle
                                               "SELECT * FROM users"
                                               nil)
              result (json/read-str result-json :key-fn keyword)]
          (is (= "select" (:type result)))
          (is (= 1 (:count result)))
          (is (seq (:columns result)))
          (is (seq (:rows result))))

        (finally
          (lib/lib-close handle))))))

(deftest test-sql-select-empty-table
  (testing "SQL SELECT on empty/nonexistent table should return empty results"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (let [result-json (lib/lib-execute-sql handle
                                               "SELECT * FROM nonexistent"
                                               nil)
              result (json/read-str result-json :key-fn keyword)]
          (is (= "select" (:type result)))
          (is (= 0 (:count result))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-mixed-with-kv
  (testing "SQL and KV operations should see each other's data"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        ;; Insert via KV
        (lib/lib-put handle "products:p1" "{\"name\": \"Widget\", \"price\": 9.99}" nil)

        ;; Read via SQL
        (let [result-json (lib/lib-execute-sql handle
                                               "SELECT * FROM products"
                                               nil)
              result (json/read-str result-json :key-fn keyword)]
          (is (= "select" (:type result)))
          (is (>= (:count result) 1)))

        ;; Insert via SQL
        (lib/lib-execute-sql handle
                             "INSERT INTO products (id, name, price) VALUES ('p2', 'Gadget', '19.99')"
                             nil)

        ;; Read via KV
        (let [doc (lib/lib-get handle "p2" nil)]
          (is (some? doc))
          (is (.contains doc "Gadget")))

        (finally
          (lib/lib-close handle))))))

(deftest test-sql-error-handling
  (testing "Invalid SQL should return error type"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (let [result-json (lib/lib-execute-sql handle "INVALID SQL GARBAGE" nil)]
          (is (some? result-json))
          (let [result (json/read-str result-json :key-fn keyword)]
            (is (= "error" (:type result)))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-invalid-handle
  (testing "SQL with invalid handle should return nil"
    (let [result (lib/lib-execute-sql -1 "SELECT 1" nil)]
      (is (nil? result)))))

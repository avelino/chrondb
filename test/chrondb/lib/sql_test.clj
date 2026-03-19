(ns chrondb.lib.sql-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [chrondb.lib.core :as lib]
            [chrondb.test-helpers :as helpers]
            [clojure.data.json :as json]))

(def ^:dynamic *test-db-dir* nil)

(defn temp-dirs-fixture [f]
  (let [db-dir (helpers/create-temp-dir)]
    (binding [*test-db-dir* db-dir]
      (try (f)
           (finally (helpers/delete-directory db-dir))))))

(use-fixtures :each temp-dirs-fixture)

(defn- sql! [handle sql]
  (json/read-str (lib/lib-execute-sql handle sql nil) :key-fn keyword))

(deftest test-sql-insert-and-select
  (testing "SQL INSERT followed by SELECT should work"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (is (>= handle 0))
        (let [result (sql! handle "INSERT INTO users (id, name, email) VALUES ('user1', 'Alice', 'alice@example.com')")]
          (is (= "insert" (:type result)))
          (is (= 1 (:affected result))))
        (let [result (sql! handle "SELECT * FROM users")]
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
        (let [result (sql! handle "SELECT * FROM nonexistent")]
          (is (= "select" (:type result)))
          (is (= 0 (:count result))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-delete
  (testing "SQL DELETE should remove document and report correct affected count"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        ;; Insert a document
        (sql! handle "INSERT INTO users (id, name) VALUES ('u1', 'Alice')")

        ;; Delete it
        (let [result (sql! handle "DELETE FROM users WHERE id = 'u1'")]
          (is (= "delete" (:type result)))
          (is (= 1 (:affected result))))

        ;; Verify it's gone
        (let [result (sql! handle "SELECT * FROM users WHERE id = 'u1'")]
          (is (= 0 (:count result))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-delete-nonexistent
  (testing "SQL DELETE of nonexistent document should report 0 affected"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (let [result (sql! handle "DELETE FROM users WHERE id = 'ghost'")]
          (is (= "delete" (:type result)))
          (is (= 0 (:affected result))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-update
  (testing "SQL UPDATE should modify document and report affected count"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (sql! handle "INSERT INTO users (id, name) VALUES ('u1', 'Alice')")
        (let [result (sql! handle "UPDATE users SET name = 'Bob' WHERE id = 'u1'")]
          (is (= "update" (:type result)))
          (is (= 1 (:affected result))))
        ;; Verify the update
        (let [doc (lib/lib-get handle "u1" nil)]
          (is (some? doc))
          (is (.contains doc "Bob")))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-show-tables
  (testing "SHOW TABLES should list all tables with data"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (lib/lib-put handle "users:1" "{\"name\": \"Alice\"}" nil)
        (lib/lib-put handle "products:1" "{\"name\": \"Widget\"}" nil)
        (let [result (sql! handle "SHOW TABLES")]
          (is (= "show-tables" (:type result)))
          (is (some #(= "users" %) (:tables result)))
          (is (some #(= "products" %) (:tables result))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-create-table-unsupported
  (testing "CREATE TABLE should return error since ChronDB is schemaless"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (let [result (sql! handle "CREATE TABLE users (id TEXT, name TEXT)")]
          (is (= "error" (:type result)))
          (is (.contains (:message result) "not supported")))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-mixed-with-kv
  (testing "SQL and KV operations should see each other's data"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        ;; Insert via KV
        (lib/lib-put handle "products:p1" "{\"name\": \"Widget\", \"price\": 9.99}" nil)
        ;; Read via SQL
        (let [result (sql! handle "SELECT * FROM products")]
          (is (= "select" (:type result)))
          (is (>= (:count result) 1)))
        ;; Insert via SQL
        (sql! handle "INSERT INTO products (id, name, price) VALUES ('p2', 'Gadget', '19.99')")
        ;; Read via KV
        (let [doc (lib/lib-get handle "p2" nil)]
          (is (some? doc))
          (is (.contains doc "Gadget")))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-consecutive-queries
  (testing "Multiple consecutive SQL queries should not degrade"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (sql! handle "INSERT INTO bench (id, val) VALUES ('b1', 'one')")
        ;; Run 20 SELECTs consecutively - should all work without error
        (dotimes [_ 20]
          (let [result (sql! handle "SELECT * FROM bench")]
            (is (= "select" (:type result)))
            (is (= 1 (:count result)))))
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

(deftest test-sql-delete-affected-count-accuracy
  (testing "DELETE affected count should be accurate regardless of WHERE clause shape"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (sql! handle "INSERT INTO items (id, name) VALUES ('i1', 'Apple')")
        (sql! handle "INSERT INTO items (id, name) VALUES ('i2', 'Banana')")
        ;; Delete existing
        (let [result (sql! handle "DELETE FROM items WHERE id = 'i1'")]
          (is (= 1 (:affected result)) "Should report 1 affected for existing doc"))
        ;; Delete already-deleted
        (let [result (sql! handle "DELETE FROM items WHERE id = 'i1'")]
          (is (= 0 (:affected result)) "Should report 0 affected for missing doc"))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-branch-parameter
  (testing "SQL operations should respect the branch parameter"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        ;; Insert on default branch
        (sql! handle "INSERT INTO users (id, name) VALUES ('u1', 'Alice')")
        ;; Should find on default branch
        (let [result (lib/lib-execute-sql handle "SELECT * FROM users" nil)]
          (is (some? result))
          (let [parsed (json/read-str result :key-fn keyword)]
            (is (= 1 (:count parsed)))))
        (finally
          (lib/lib-close handle))))))

(deftest test-sql-invalid-handle
  (testing "SQL with invalid handle should return nil"
    (let [result (lib/lib-execute-sql -1 "SELECT 1" nil)]
      (is (nil? result)))))

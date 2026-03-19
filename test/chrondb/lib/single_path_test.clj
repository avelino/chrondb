(ns chrondb.lib.single-path-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [chrondb.lib.core :as lib]
            [clojure.java.io :as io])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def ^:dynamic *test-db-dir* nil)

(defn- create-temp-dir []
  (str (Files/createTempDirectory "chrondb-single-path-test" (make-array FileAttribute 0))))

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

(deftest test-lib-open-path
  (testing "lib-open-path should open database with single path"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (is (>= handle 0) "handle should be valid")
      (is (= 0 (lib/lib-close handle)) "close should succeed"))))

(deftest test-lib-open-path-creates-index-subdir
  (testing "lib-open-path should create .chrondb-index inside db dir"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (is (>= handle 0))
        (lib/lib-put handle "test:1" "{\"value\": 1}" nil)
        (let [index-dir (io/file *test-db-dir* ".chrondb-index")]
          (is (.exists index-dir) ".chrondb-index directory should exist"))
        (finally
          (lib/lib-close handle))))))

(deftest test-lib-open-path-roundtrip
  (testing "Single-path open should support full CRUD"
    (let [handle (lib/lib-open-path *test-db-dir*)]
      (try
        (is (>= handle 0))

        ;; Put
        (let [result (lib/lib-put handle "user:1" "{\"name\": \"Alice\"}" nil)]
          (is (some? result))
          (is (.contains result "Alice")))

        ;; Get
        (let [doc (lib/lib-get handle "user:1" nil)]
          (is (some? doc))
          (is (.contains doc "Alice")))

        ;; History
        (let [history (lib/lib-history handle "user:1" nil)]
          (is (some? history)))

        ;; Delete
        (is (= 0 (lib/lib-delete handle "user:1" nil)))
        (is (nil? (lib/lib-get handle "user:1" nil)))
        (finally
          (lib/lib-close handle))))))

(deftest test-lib-open-path-persistence
  (testing "Data should persist across open/close with single path"
    (let [handle1 (lib/lib-open-path *test-db-dir*)]
      (lib/lib-put handle1 "persist:1" "{\"key\": \"value\"}" nil)
      (lib/lib-close handle1))

    (let [handle2 (lib/lib-open-path *test-db-dir*)]
      (try
        (let [doc (lib/lib-get handle2 "persist:1" nil)]
          (is (some? doc) "Document should persist after reopen")
          (is (.contains doc "value")))
        (finally
          (lib/lib-close handle2))))))

(deftest test-lib-open-path-invalid
  (testing "lib-open-path with nil should return -1"
    (is (= -1 (lib/lib-open-path nil)))))

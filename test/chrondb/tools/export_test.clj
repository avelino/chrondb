(ns chrondb.tools.export-test
  (:require [chrondb.config :as config]
            [chrondb.storage.git.core :as git-core]
            [chrondb.storage.protocol :as protocol]
            [chrondb.tools.export :as export]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]])
  (:import [java.io File]))

(def test-repo-path "test-export-repo")
(def test-export-path "test-export-output")

(def test-config
  {:git {:default-branch "main"
         :committer-name "Test User"
         :committer-email "test@example.com"
         :push-enabled false}
   :storage {:data-dir nil}
   :logging {:level :info}})

(defn delete-directory [^File directory]
  (when (.exists directory)
    (doseq [file (reverse (file-seq directory))]
      (.delete file))))

(defn clean-test-dirs [f]
  (delete-directory (io/file test-repo-path))
  (delete-directory (io/file test-export-path))
  (with-redefs [config/load-config (constantly test-config)]
    (try
      (f)
      (finally
        (delete-directory (io/file test-repo-path))
        (delete-directory (io/file test-export-path))))))

(use-fixtures :each clean-test-dirs)

(defn- create-storage []
  (git-core/create-git-storage test-repo-path nil))

(defn- save-doc [storage id data]
  (protocol/save-document storage (assoc data :id id)))

(defn- exported-json-files
  "Returns all .json files in the export dir, excluding the manifest."
  [export-dir]
  (->> (file-seq (io/file export-dir))
       (filter #(.isFile %))
       (filter #(.endsWith (.getName %) ".json"))
       (remove #(= ".chrondb-export.json" (.getName %)))))

(deftest test-export-empty-repository
  (testing "Exporting an empty repository produces zero files"
    (let [storage (create-storage)]
      (try
        (let [result (export/export-to-directory storage test-export-path)]
          (is (= :ok (:status result)))
          (is (= 0 (:files-exported result)))
          (is (= "main" (:branch result))))
        (finally
          (protocol/close storage))))))

(deftest test-export-single-document
  (testing "Exporting a single document creates the correct file"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice" :age 30})
        (let [result (export/export-to-directory storage test-export-path)]
          (is (= :ok (:status result)))
          (is (= 1 (:files-exported result)))
          (let [files (exported-json-files test-export-path)]
            (is (= 1 (count files)))
            (let [content (json/read-str (slurp (first files)) :key-fn keyword)]
              (is (= "Alice" (:name content)))
              (is (= 30 (:age content))))))
        (finally
          (protocol/close storage))))))

(deftest test-export-multiple-documents
  (testing "Exporting multiple documents across tables"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (save-doc storage "users:2" {:name "Bob"})
        (save-doc storage "orders:100" {:item "Widget" :qty 5})
        (let [result (export/export-to-directory storage test-export-path)]
          (is (= :ok (:status result)))
          (is (= 3 (:files-exported result)))
          ;; Verify manifest
          (let [manifest-file (io/file test-export-path ".chrondb-export.json")]
            (is (.exists manifest-file))
            (let [manifest (json/read-str (slurp manifest-file) :key-fn keyword)]
              (is (= 3 (:files-exported manifest)))
              (is (= "main" (:branch manifest)))
              (is (= "chrondb-export-v1" (:format manifest))))))
        (finally
          (protocol/close storage))))))

(deftest test-export-with-prefix-filter
  (testing "Prefix filter only exports matching documents"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (save-doc storage "users:2" {:name "Bob"})
        (save-doc storage "orders:100" {:item "Widget"})
        (let [result (export/export-to-directory storage test-export-path
                                                 {:prefix "users"})]
          (is (= :ok (:status result)))
          (is (= 2 (:files-exported result))))
        (finally
          (protocol/close storage))))))

(deftest test-export-raw-format
  (testing "Raw format exports blob content as-is without re-formatting"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (let [result (export/export-to-directory storage test-export-path
                                                 {:format :raw})]
          (is (= :ok (:status result)))
          (is (= 1 (:files-exported result)))
          ;; Raw format should produce valid JSON (whatever the blob contains)
          (let [files (exported-json-files test-export-path)]
            (is (some? (json/read-str (slurp (first files)))))))
        (finally
          (protocol/close storage))))))

(deftest test-export-json-format-is-indented
  (testing "JSON format exports pretty-printed content"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (let [result (export/export-to-directory storage test-export-path
                                                 {:format :json})]
          (is (= :ok (:status result)))
          (let [files (exported-json-files test-export-path)
                content (slurp (first files))]
            ;; Pretty-printed JSON has newlines
            (is (.contains content "\n"))))
        (finally
          (protocol/close storage))))))

(deftest test-export-no-decode-paths
  (testing "With decode-paths? false, encoded paths are preserved on disk"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (let [result (export/export-to-directory storage test-export-path
                                                 {:decode-paths? false})]
          (is (= :ok (:status result)))
          (is (= 1 (:files-exported result)))
          ;; Encoded paths should have _COLON_ in directory or file names
          (let [all-paths (->> (file-seq (io/file test-export-path))
                               (map #(.getPath %)))]
            (is (some #(.contains % "_COLON_") all-paths))))
        (finally
          (protocol/close storage))))))

(deftest test-export-decode-paths-default
  (testing "Default export decodes encoded paths"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (let [result (export/export-to-directory storage test-export-path)]
          (is (= :ok (:status result)))
          (is (= 1 (:files-exported result)))
          ;; Decoded paths should NOT have _COLON_
          (let [all-paths (->> (file-seq (io/file test-export-path))
                               (map #(.getPath %)))]
            (is (not (some #(.contains % "_COLON_") all-paths)))))
        (finally
          (protocol/close storage))))))

(deftest test-export-overwrite-protection
  (testing "Fails when target directory is non-empty without overwrite flag"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        ;; First export
        (export/export-to-directory storage test-export-path)
        ;; Second export should fail
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"not empty"
                              (export/export-to-directory storage test-export-path)))
        (finally
          (protocol/close storage))))))

(deftest test-export-overwrite-allowed
  (testing "Succeeds when overwrite? is true"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        ;; First export
        (export/export-to-directory storage test-export-path)
        ;; Second export with overwrite should succeed
        (let [result (export/export-to-directory storage test-export-path
                                                 {:overwrite? true})]
          (is (= :ok (:status result))))
        (finally
          (protocol/close storage))))))

(deftest test-export-specific-commit
  (testing "Export a specific commit (time-travel)"
    (let [storage (create-storage)]
      (try
        ;; Save first version
        (save-doc storage "users:1" {:name "Alice" :version 1})
        ;; Get commit after first save
        (let [repo (:repository storage)
              first-commit (.getName (.resolve repo "main^{commit}"))]
          ;; Save second version (overwrites)
          (save-doc storage "users:1" {:name "Alice Updated" :version 2})
          ;; Export the first commit
          (let [result (export/export-to-directory storage test-export-path
                                                   {:commit first-commit})]
            (is (= :ok (:status result)))
            (is (= first-commit (:commit result)))
            ;; Verify content is from first version
            (let [files (exported-json-files test-export-path)
                  content (json/read-str (slurp (first files)) :key-fn keyword)]
              (is (= 1 (:version content))))))
        (finally
          (protocol/close storage))))))

(deftest test-export-closed-storage
  (testing "Throws when storage is closed"
    (let [storage (create-storage)]
      (protocol/close storage)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"closed"
                            (export/export-to-directory
                             (assoc storage :repository nil)
                             test-export-path))))))

(deftest test-export-directory-structure
  (testing "Export preserves table directory structure"
    (let [storage (create-storage)]
      (try
        (save-doc storage "users:1" {:name "Alice"})
        (save-doc storage "orders:100" {:item "Widget"})
        (export/export-to-directory storage test-export-path)
        ;; Should have subdirectories for each table
        (let [subdirs (->> (.listFiles (io/file test-export-path))
                           (filter #(.isDirectory %))
                           (map #(.getName %))
                           set)]
          (is (contains? subdirs "users"))
          (is (contains? subdirs "orders")))
        (finally
          (protocol/close storage))))))

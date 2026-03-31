(ns chrondb.lib.core
  "Bridge layer for the ChronDB shared library.
   Manages a registry of open database handles and exposes operations
   that can be called from the C entry points.

   Concurrency model:
   - Each unique (data-path, index-path) pair is opened only once (singleton)
   - Multiple handles can reference the same storage/index instance
   - Operations are thread-safe via JGit's internal locking"
  (:require [chrondb.config :as config]
            [chrondb.storage.git.core :as git]
            [chrondb.storage.git.remote :as remote]
            [chrondb.storage.protocol :as storage]
            [chrondb.index.lucene :as lucene]
            [chrondb.index.protocol :as index]
            [chrondb.util.logging :as log]
            [chrondb.util.locks :as locks]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.util.concurrent.atomic AtomicInteger]
           [org.eclipse.jgit.api Git]))

(def ^:private default-index-subdir ".chrondb-index")

(defonce ^:private ^AtomicInteger handle-counter (AtomicInteger. 0))
(defonce ^:private handle-registry (atom {}))

;; Singleton registry for storage/index instances per path pair
;; Key: [data-path index-path], Value: {:storage s :index i :ref-count n}
(defonce ^:private instance-registry (atom {}))
(defonce ^:private instance-lock (Object.))

;; Disable logging by default for library usage.
;; Enable with CHRONDB_DB_LOGS=1 for debugging.
(defonce ^:private _init-logging
  (let [logs-enabled? (= "1" (System/getenv "CHRONDB_DB_LOGS"))]
    (log/init! {:min-level (if logs-enabled? :info :off)})))

(defn- git-repo-exists?
  "Checks if a bare Git repository already exists at the given path.
   A bare Git repo is identified by the presence of the HEAD file directly
   in the data directory (not in .git subdirectory)."
  [data-path]
  (let [git-dir (io/file data-path)]
    (and (.exists git-dir)
         (.isDirectory git-dir)
         (.exists (io/file data-path "HEAD")))))

(defn- normalize-path
  "Normalizes a path to its canonical form for consistent registry keys."
  [path]
  (when path
    (try
      (.getCanonicalPath (io/file path))
      (catch Exception _
        path))))

(defn- get-or-create-instance!
  "Gets an existing instance for the path pair, or creates a new one.
   Increments ref-count when returning existing instance.
   Thread-safe via locking."
  [data-path index-path]
  (locking instance-lock
    (let [registry-key [(normalize-path data-path) (normalize-path index-path)]
          existing (get @instance-registry registry-key)]
      (if existing
        ;; Existing instance: increment ref-count and return
        (do
          (swap! instance-registry update-in [registry-key :ref-count] inc)
          {:storage (:storage existing)
           :index (:index existing)
           :reused true})
        ;; New instance: create storage and index
        (do
          ;; Clean stale locks only when creating new instance
          (locks/clean-stale-locks data-path)
          (locks/clean-stale-locks index-path)
          (let [repo-exists? (git-repo-exists? data-path)
                storage (if repo-exists?
                          (git/open-git-storage data-path)
                          (git/create-git-storage data-path))
                idx (lucene/create-lucene-index index-path)]
            (when (and storage idx)
              (lucene/ensure-index-populated idx storage nil {:async? false})
              (swap! instance-registry assoc registry-key
                     {:storage storage :index idx :ref-count 1}))
            {:storage storage :index idx :reused false}))))))

(defn- release-instance!
  "Decrements ref-count for a path pair. Closes resources when count reaches 0.
   Thread-safe via locking."
  [data-path index-path]
  (locking instance-lock
    (let [registry-key [(normalize-path data-path) (normalize-path index-path)]
          existing (get @instance-registry registry-key)]
      (when existing
        (let [new-count (dec (:ref-count existing))]
          (if (<= new-count 0)
            ;; Last reference: close resources and remove from registry
            (do
              (swap! instance-registry dissoc registry-key)
              (when (:index existing)
                (try (index/close (:index existing)) (catch Exception _ nil)))
              (when (:storage existing)
                (try (storage/close (:storage existing)) (catch Exception _ nil)))
              true)
            ;; Still has references: just decrement count
            (do
              (swap! instance-registry update-in [registry-key :ref-count] dec)
              false)))))))

(defn lib-open
  "Opens a ChronDB instance with the given data and index paths.
   If a Git repository already exists at data-path, it will be opened
   (preserving existing data). Otherwise, a new repository is created.

   Concurrency: Multiple calls with the same paths share the same underlying
   storage/index instance (singleton per path pair). This allows multiple
   handles to safely access the same database concurrently.

   Cleans up any stale lock files before opening to handle orphan locks
   left by crashed processes.
   Returns a handle (>= 0) on success, or -1 on error."
  [data-path index-path]
  (try
    (let [{:keys [storage index]} (get-or-create-instance! data-path index-path)]
      (if (and storage index)
        (let [handle (.getAndIncrement ^AtomicInteger handle-counter)]
          (swap! handle-registry assoc handle
                 {:storage storage
                  :index index
                  :data-path data-path
                  :index-path index-path})
          handle)
        -1))
    (catch Throwable e
      (log/log-error (str "lib-open failed: " (.getMessage e)
                          " | data-path=" data-path
                          " | index-path=" index-path))
      -1)))

(defn lib-close
  "Closes the ChronDB instance associated with the given handle.
   The underlying storage/index is only closed when all handles referencing
   the same path pair are closed (ref-counting).
   Returns 0 on success, -1 on error."
  [handle]
  (try
    (if-let [{:keys [data-path index-path]} (get @handle-registry handle)]
      (do
        (swap! handle-registry dissoc handle)
        (release-instance! data-path index-path)
        0)
      -1)
    (catch Throwable _e
      -1)))

(defn lib-put
  "Saves a document (JSON string) with the given id.
   Returns the saved document as a JSON string, or nil on error."
  [handle id json-str branch]
  (try
    (when-let [{:keys [storage index]} (get @handle-registry handle)]
      (let [doc (-> (json/read-str json-str :key-fn keyword)
                    (assoc :id id))
            saved (storage/save-document storage doc branch)]
        (when (and index saved)
          (index/index-document index saved))
        (json/write-str saved)))
    (catch Throwable _e
      nil)))

(defn lib-get
  "Gets a document by id. Returns JSON string or nil."
  [handle id branch]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (when-let [doc (storage/get-document storage id branch)]
        (json/write-str doc)))
    (catch Throwable _e
      nil)))

(defn lib-delete
  "Deletes a document by id.
   Returns 0 on success, 1 if not found, -1 on error."
  [handle id branch]
  (try
    (if-let [{:keys [storage index]} (get @handle-registry handle)]
      (let [existing (storage/get-document storage id branch)]
        (if existing
          (do
            (storage/delete-document storage id branch)
            (when index (index/delete-document index id))
            0)
          1))
      -1)
    (catch Throwable _e
      -1)))

(defn lib-list-by-prefix
  "Lists documents by ID prefix. Returns JSON array string or nil."
  [handle prefix branch]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [docs (storage/get-documents-by-prefix storage prefix branch)]
        (json/write-str (vec docs))))
    (catch Throwable _e
      nil)))

(defn lib-list-by-table
  "Lists documents by table name. Returns JSON array string or nil."
  [handle table branch]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [docs (storage/get-documents-by-table storage table branch)]
        (json/write-str (vec docs))))
    (catch Throwable _e
      nil)))

(defn lib-history
  "Gets document history. Returns JSON array string or nil."
  [handle id branch]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [history (storage/get-document-history storage id branch)]
        (json/write-str (vec history))))
    (catch Throwable _e
      nil)))

(defn lib-query
  "Executes a query (JSON-encoded query map). Returns JSON result string or nil."
  [handle query-json branch]
  (try
    (when-let [{:keys [storage index]} (get @handle-registry handle)]
      (let [query-map (json/read-str query-json :key-fn keyword)
            result (index/search-query index query-map branch {})
            ids (:ids result)
            docs (mapv (fn [id] (storage/get-document storage id branch)) ids)
            docs (filterv some? docs)]
        (json/write-str {:results docs
                         :total (:total result)
                         :limit (:limit result)
                         :offset (:offset result)})))
    (catch Throwable _e
      nil)))

(defn- derive-index-path
  "Derives the index path from a database directory path.
   The index is stored as a subdirectory inside the database directory."
  [db-path]
  (str (io/file db-path default-index-subdir)))

(defn lib-open-path
  "Opens a ChronDB instance with a single database directory path.
   The index is stored automatically inside the database directory.
   Returns a handle (>= 0) on success, or -1 on error."
  [db-path]
  (lib-open db-path (derive-index-path db-path)))

;; Lazy-load SQL engine functions once, not on every call.
(defonce ^:private sql-fns
  (delay
    (require 'chrondb.api.sql.parser.statements
             'chrondb.api.sql.execution.query)
    {:parse        (ns-resolve 'chrondb.api.sql.parser.statements 'parse-sql-query)
     :select       (ns-resolve 'chrondb.api.sql.execution.query 'handle-select)
     :insert-case  (ns-resolve 'chrondb.api.sql.execution.query 'handle-insert-case)
     :update-case  (ns-resolve 'chrondb.api.sql.execution.query 'handle-update-case)
     :delete-case  (ns-resolve 'chrondb.api.sql.execution.query 'handle-delete-case)}))

(defn- select-col-names
  "Extracts column names from a SELECT result."
  [docs columns]
  (if (some #(= :all (:type %)) columns)
    (->> docs
         (mapcat keys)
         (filter #(not= % :_table))
         distinct
         sort
         (mapv name))
    (->> columns
         (filter #(= :column (:type %)))
         (mapv :column))))

(def ^:private ^:const max-sql-result-rows
  "Safety limit for SELECT results to prevent OOM on large tables.
   Users needing more should use LIMIT in their SQL."
  10000)

;; Null output stream — discards all bytes without buffering.
;; Avoids memory waste from ByteArrayOutputStream in INSERT/UPDATE/DELETE
;; where the protocol output is not needed.
(def ^:private null-output-stream
  (proxy [java.io.OutputStream] []
    (write
      ([_b])
      ([_b _off _len]))
    (flush [])
    (close [])))

(defn- execute-sql-dispatch
  "Dispatches a parsed SQL query and returns a result map."
  [storage index parsed branch]
  (let [{:keys [select insert-case update-case delete-case]} @sql-fns
        query-type (:type parsed)]
    (case query-type
      :select
      (let [all-docs (select storage index parsed)
            truncated? (> (count all-docs) max-sql-result-rows)
            docs (if truncated?
                   (take max-sql-result-rows all-docs)
                   all-docs)
            col-names (select-col-names docs (:columns parsed))]
        (cond-> {:type "select"
                 :columns col-names
                 :rows (mapv (fn [doc]
                               (mapv #(get doc (keyword %) "") col-names))
                             docs)
                 :count (count docs)}
          truncated? (assoc :truncated true
                            :total (count all-docs))))

      :insert
      (let [saved (insert-case storage index null-output-stream parsed)]
        {:type "insert"
         :affected (count (or saved []))})

      :update
      (let [saved (update-case storage index null-output-stream parsed)]
        {:type "update"
         :affected (count (or saved []))})

      :delete
      (let [;; The SQL delete handler writes errors to the output stream instead of
            ;; throwing or returning a status. We verify the actual outcome by checking
            ;; document existence before and after the operation.
            table-name (:table parsed)
            where-cond (:where parsed)
            id-val (when (and (seq where-cond)
                              (= (:field (first where-cond)) "id")
                              (= (:op (first where-cond)) "="))
                     (.replaceAll ^String (:value (first where-cond)) "['\"]" ""))
            pre-exists? (when id-val
                          (some? (storage/get-document storage id-val branch)))
            _ (delete-case storage index null-output-stream parsed)
            post-exists? (when id-val
                           (some? (storage/get-document storage id-val branch)))
            actually-deleted? (and pre-exists? (not post-exists?))]
        (if (and pre-exists? post-exists?)
          ;; Document still exists after delete attempt — operation failed
          {:type "error"
           :message (str "DELETE failed for id '" id-val "' in table '" table-name "'")}
          {:type "delete"
           :affected (cond
                       actually-deleted? 1
                       ;; Non-id WHERE — count difference would require scanning again,
                       ;; but ChronDB DELETE only supports WHERE id = ... currently
                       (not id-val) 0
                       :else 0)}))

      :show-tables
      (let [docs (storage/get-documents-by-prefix storage "" branch)
            table-names (->> docs
                             (map (fn [doc]
                                    (or (:_table doc)
                                        (let [id (str (:id doc))]
                                          (when (.contains id ":")
                                            (first (.split id ":")))))))
                             (filter some?)
                             distinct
                             sort
                             vec)]
        {:type "show-tables" :tables table-names})

      :create-table
      {:type "error"
       :message "CREATE TABLE is not supported. ChronDB is schemaless — just INSERT data."}

      :drop-table
      {:type "error"
       :message "DROP TABLE is not supported. Delete documents individually."}

      ;; Default
      {:type "error"
       :message (str "Unsupported query type: " (name query-type))})))

(defn lib-execute-sql
  "Executes a SQL query against the database.
   Returns a JSON string with the results:
   - SELECT: {\"type\":\"select\",\"columns\":[...],\"rows\":[...],\"count\":N}
   - INSERT/UPDATE/DELETE: {\"type\":\"...\",\"affected\":N}
   - Error: {\"type\":\"error\",\"message\":\"...\"}
   Returns nil if handle is invalid."
  [handle sql branch]
  (try
    (when-let [{:keys [storage index]} (get @handle-registry handle)]
      (let [parsed ((:parse @sql-fns) sql)
            ;; Inject branch into parsed query as :schema so SQL handlers
            ;; route to the correct Git branch. Only override if user
            ;; explicitly provided a branch and the SQL has no explicit schema.
            parsed (if (and branch (nil? (:schema parsed)))
                     (assoc parsed :schema branch)
                     parsed)]
        (json/write-str (execute-sql-dispatch storage index parsed branch))))
    (catch Throwable e
      (json/write-str {:type "error"
                       :message (.getMessage e)}))))

(defn lib-setup-remote
  "Configures a remote repository URL for the database.
   Returns a JSON string with the result:
   - Success: {\"type\":\"ok\",\"remote_url\":\"...\"}
   - Error: {\"type\":\"error\",\"message\":\"...\"}
   Returns nil if handle is invalid."
  [handle remote-url]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [repo (:repository storage)
            git (Git/wrap repo)]
        (remote/setup-remote git {:git {:remote-url remote-url}})
        (json/write-str {:type "ok" :remote_url remote-url})))
    (catch Throwable e
      (json/write-str {:type "error"
                       :message (.getMessage e)}))))

(defn lib-push
  "Pushes local changes to the configured remote repository.
   Returns a JSON string with the result:
   - Success: {\"type\":\"ok\",\"status\":\"pushed\"}
   - Skipped: {\"type\":\"ok\",\"status\":\"skipped\"}
   - Error: {\"type\":\"error\",\"message\":\"...\"}
   Returns nil if handle is invalid."
  [handle]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [repo (:repository storage)
            git (Git/wrap repo)
            config-map (config/load-config)
            result (remote/push-to-remote git config-map)]
        (json/write-str {:type "ok" :status (name result)})))
    (catch Throwable e
      (json/write-str {:type "error"
                       :message (.getMessage e)}))))

(defn lib-pull
  "Pulls changes from the configured remote repository.
   Returns a JSON string with the result:
   - Success: {\"type\":\"ok\",\"status\":\"pulled\"}
   - Current: {\"type\":\"ok\",\"status\":\"current\"}
   - Skipped: {\"type\":\"ok\",\"status\":\"skipped\"}
   - Conflict: {\"type\":\"ok\",\"status\":\"conflict\"}
   - Error: {\"type\":\"error\",\"message\":\"...\"}
   Returns nil if handle is invalid."
  [handle]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [repo (:repository storage)
            git (Git/wrap repo)
            config-map (config/load-config)
            result (remote/pull-from-remote git config-map)]
        (json/write-str {:type "ok" :status (name result)})))
    (catch Throwable e
      (json/write-str {:type "error"
                       :message (.getMessage e)}))))

(defn lib-fetch
  "Fetches changes from the configured remote without merging.
   Returns a JSON string with the result:
   - Success: {\"type\":\"ok\",\"status\":\"fetched\"}
   - Skipped: {\"type\":\"ok\",\"status\":\"skipped\"}
   - Error: {\"type\":\"error\",\"message\":\"...\"}
   Returns nil if handle is invalid."
  [handle]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [repo (:repository storage)
            git (Git/wrap repo)
            config-map (config/load-config)
            result (remote/fetch-from-remote git config-map)]
        (json/write-str {:type "ok" :status (name result)})))
    (catch Throwable e
      (json/write-str {:type "error"
                       :message (.getMessage e)}))))

(defn lib-remote-status
  "Returns the remote configuration status for the database.
   Returns a JSON string:
   - {\"type\":\"ok\",\"configured\":true/false}
   Returns nil if handle is invalid."
  [handle]
  (try
    (when-let [{:keys [storage]} (get @handle-registry handle)]
      (let [repo (:repository storage)
            configured? (remote/remote-configured? repo "origin")]
        (json/write-str {:type "ok" :configured configured?})))
    (catch Throwable e
      (json/write-str {:type "error"
                       :message (.getMessage e)}))))

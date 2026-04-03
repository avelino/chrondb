(ns chrondb.tools.export
  "Export bare repository tree to filesystem directory.
   Materializes Git blobs as regular files, preserving the
   table-name/document-id.json directory structure."
  (:require [chrondb.config :as config]
            [chrondb.storage.git.core :as git-core]
            [chrondb.storage.git.path :as path]
            [chrondb.util.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io ByteArrayInputStream]
           [java.time Instant]
           [org.eclipse.jgit.lib Repository]
           [org.eclipse.jgit.revwalk RevWalk]
           [org.eclipse.jgit.treewalk TreeWalk]
           [org.eclipse.jgit.treewalk.filter PathSuffixFilter]))

(defn- resolve-commit
  "Resolves the commit ObjectId from either an explicit commit hash or a branch HEAD."
  [^Repository repo branch commit]
  (if commit
    (.resolve repo (str commit "^{commit}"))
    (.resolve repo (str branch "^{commit}"))))

(defn- decode-export-path
  "Decodes an encoded Git path back to a human-readable form.
   Splits path into segments, decodes each one, and rejects
   unsafe paths that could escape the export target directory."
  [git-path]
  (let [segments (str/split git-path #"/")
        decoded (mapv (fn [segment]
                        (let [d (path/decode-path segment)]
                          (when (or (= ".." d)
                                    (str/starts-with? d "/")
                                    (str/starts-with? d "\\")
                                    (re-matches #"^[A-Za-z]:.*" d))
                            (throw (ex-info "Unsafe export path detected"
                                            {:git-path git-path
                                             :decoded-segment d})))
                          d))
                      segments)]
    (str/join "/" decoded)))

(defn- pretty-json-bytes
  "Parses JSON content and re-serializes with indentation.
   Falls back to raw content if parsing fails."
  [^bytes content-bytes]
  (let [raw (String. content-bytes "UTF-8")]
    (try
      (let [data (json/read-str raw)]
        (.getBytes (json/write-str data :indent true) "UTF-8"))
      (catch Exception _
        content-bytes))))

(defn- write-manifest
  "Writes export metadata manifest to .chrondb-export.json in the target directory."
  [target-dir {:keys [branch commit files-exported]}]
  (let [manifest {:exported-at (str (Instant/now))
                  :branch branch
                  :commit (str commit)
                  :files-exported files-exported
                  :format "chrondb-export-v1"}
        manifest-file (io/file target-dir ".chrondb-export.json")]
    (spit manifest-file (json/write-str manifest :indent true))))

(defn- validate-target-dir
  "Validates the target directory. Creates it if needed.
   Fails if path exists but is not a directory, or if non-empty without overwrite."
  [target-dir overwrite?]
  (let [dir (io/file target-dir)]
    (if (.exists dir)
      (do
        (when-not (.isDirectory dir)
          (throw (ex-info "Target path exists but is not a directory."
                          {:target-dir target-dir})))
        (let [files (.listFiles dir)]
          (when (and (not overwrite?)
                     files
                     (pos? (count files)))
            (throw (ex-info "Target directory is not empty. Use :overwrite? true to overwrite."
                            {:target-dir target-dir})))))
      (when-not (.mkdirs dir)
        (throw (ex-info "Failed to create target directory."
                        {:target-dir target-dir}))))))

(defn export-to-directory
  "Exports the current state of a branch to a filesystem directory.
   Materializes all Git blobs as regular files, preserving the
   table-name/document-id.json directory structure.

   Options:
   - :branch        branch to export (default: configured default-branch)
   - :commit        specific commit hash (overrides branch HEAD)
   - :prefix        only export documents matching this path prefix
   - :decode-paths? decode encoded paths back to original form (default: true)
   - :overwrite?    overwrite existing files in target directory (default: false)
   - :format        :json (pretty-printed, default) or :raw (as stored)

   Returns:
   {:status :ok
    :files-exported  count
    :target-dir      string
    :branch          string
    :commit          string
    :timestamp       string}"
  ([storage target-dir]
   (export-to-directory storage target-dir {}))
  ([storage target-dir {:keys [branch commit prefix decode-paths? overwrite? format]
                        :or {decode-paths? true overwrite? false format :json}}]
   (let [repo (:repository storage)
         _ (when-not repo
             (throw (ex-info "Storage repository is closed" {})))
         config-map (config/load-config)
         branch-name (or branch (get-in config-map [:git :default-branch]))
         commit-id (resolve-commit repo branch-name commit)
         _ (when-not commit-id
             (throw (ex-info "Could not resolve commit"
                             {:branch branch-name :commit commit})))
         encoded-prefix (when prefix (path/encode-path prefix))]
     (validate-target-dir target-dir overwrite?)
     (let [rev-walk (RevWalk. repo)
           commit-obj (.parseCommit rev-walk commit-id)
           tree (.getTree commit-obj)
           tree-walk (TreeWalk. repo)]
       (try
         (.addTree tree-walk tree)
         (.setRecursive tree-walk true)
         (.setFilter tree-walk (PathSuffixFilter/create ".json"))
         (loop [count 0]
           (if (.next tree-walk)
             (let [git-path (.getPathString tree-walk)]
               (if (and encoded-prefix
                        (not (.startsWith git-path (str encoded-prefix "/")))
                        (not (.startsWith git-path (str encoded-prefix "."))))
                 (recur count)
                 (let [object-id (.getObjectId tree-walk 0)
                       loader (.open repo object-id)
                       content-bytes (.getBytes loader)
                       dest-path (if decode-paths?
                                   (decode-export-path git-path)
                                   git-path)
                       dest-file (io/file target-dir dest-path)]
                   (.mkdirs (.getParentFile dest-file))
                   (let [output-bytes (if (= format :json)
                                        (pretty-json-bytes content-bytes)
                                        content-bytes)]
                     (io/copy (ByteArrayInputStream. output-bytes) dest-file))
                   (when (zero? (mod (inc count) 100))
                     (log/log-info (str "Exported " (inc count) " files...")))
                   (recur (inc count)))))
             (let [result {:status :ok
                           :files-exported count
                           :target-dir (str target-dir)
                           :branch branch-name
                           :commit (.getName commit-id)
                           :timestamp (str (Instant/now))}]
               (when (pos? count)
                 (write-manifest target-dir result))
               (log/log-info (str "Export complete: " count " files to " target-dir))
               result)))
         (finally
           (.close tree-walk)
           (.close rev-walk)))))))

(defn -main
  "CLI entry point for the export tool.

   Usage: clojure -M:export <target-dir> [options]

   Options:
     --branch BRANCH     Branch to export (default: main)
     --commit HASH       Specific commit to export
     --prefix PREFIX     Only export paths matching prefix
     --format FORMAT     json (pretty, default) or raw
     --overwrite         Overwrite existing target directory
     --no-decode         Don't decode encoded paths"
  [& args]
  (let [config-map (config/load-config)
        repository-dir (or (get-in config-map [:storage :repository-dir]) "data")
        data-dir (get-in config-map [:storage :data-dir])
        ;; Parse args
        {:keys [target-dir options]}
        (loop [remaining args
               target nil
               opts {}]
          (if (empty? remaining)
            {:target-dir target :options opts}
            (let [[arg & rest-args] remaining]
              (case arg
                "--branch" (recur (rest rest-args) target (assoc opts :branch (first rest-args)))
                "--commit" (recur (rest rest-args) target (assoc opts :commit (first rest-args)))
                "--prefix" (recur (rest rest-args) target (assoc opts :prefix (first rest-args)))
                "--format" (recur (rest rest-args) target (assoc opts :format (keyword (first rest-args))))
                "--overwrite" (recur rest-args target (assoc opts :overwrite? true))
                "--no-decode" (recur rest-args target (assoc opts :decode-paths? false))
                (recur rest-args (or target arg) opts)))))]
    (when-not target-dir
      (println "Usage: clojure -M:export <target-dir> [--branch BRANCH] [--commit HASH] [--prefix PREFIX] [--format json|raw] [--overwrite] [--no-decode]")
      (System/exit 1))
    (let [storage (git-core/open-git-storage repository-dir data-dir)]
      (try
        (let [result (export-to-directory storage target-dir options)]
          (println (json/write-str result :indent true)))
        (catch Exception e
          (binding [*out* *err*]
            (println (str "Export failed: " (.getMessage e))))
          (System/exit 1))
        (finally
          (.close (:repository storage)))))))

(ns chrondb.cli.local
  "Standalone CLI for interacting with a ChronDB database locally.
   No server required — opens the database directly."
  (:require [chrondb.lib.core :as lib]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(def cli-options
  [["-b" "--branch BRANCH" "Branch name"]
   ["-h" "--help" "Show help"]
   ["-v" "--version" "Show version"]])

(defn- pretty-json [data]
  (json/write-str data :indent true :escape-unicode false))

(defn- print-table-result
  "Prints SQL select results as a formatted table."
  [{:keys [columns rows count]}]
  (when (and (seq columns) (seq rows))
    (let [col-widths (mapv (fn [i]
                             (apply max
                                    (count (nth columns i))
                                    (map #(count (str (nth % i ""))) rows)))
                           (range (count columns)))
          fmt-row (fn [vals]
                    (str "| "
                         (str/join " | "
                                   (map-indexed (fn [i v]
                                                  (format (str "%-" (nth col-widths i) "s") (str v)))
                                                vals))
                         " |"))
          separator (str "+-"
                         (str/join "-+-"
                                   (map #(apply str (repeat % "-")) col-widths))
                         "-+")]
      (println separator)
      (println (fmt-row columns))
      (println separator)
      (doseq [row rows]
        (println (fmt-row row)))
      (println separator)
      (println (str count " row(s)")))))

(defn- execute-sql
  "Executes a SQL statement and prints the result."
  [handle sql branch]
  (let [result-json (lib/lib-execute-sql handle sql branch)]
    (if result-json
      (let [result (json/read-str result-json :key-fn keyword)]
        (case (:type result)
          "select" (print-table-result result)
          "insert" (println (str "INSERT " (:affected result)))
          "update" (println (str "UPDATE " (:affected result)))
          "delete" (println (str "DELETE " (:affected result)))
          "show-tables" (doseq [t (:tables result)]
                          (println t))
          "create-table" (println (str "CREATE TABLE " (:table result)))
          "drop-table" (println (str "DROP TABLE " (:table result)))
          "error" (binding [*out* *err*]
                    (println (str "ERROR: " (:message result))))
          (println (pretty-json result))))
      (binding [*out* *err*]
        (println "ERROR: Failed to execute query")))))

(defn- execute-kv-command
  "Executes a key/value command."
  [handle command args branch]
  (case command
    "get" (if-let [doc (lib/lib-get handle (first args) branch)]
            (println (pretty-json (json/read-str doc)))
            (binding [*out* *err*]
              (println "Not found")))

    "put" (let [[id json-str] args]
            (if (and id json-str)
              (if-let [result (lib/lib-put handle id json-str branch)]
                (println (pretty-json (json/read-str result)))
                (binding [*out* *err*]
                  (println "ERROR: Failed to save document")))
              (binding [*out* *err*]
                (println "Usage: put <id> <json>"))))

    "delete" (let [result (lib/lib-delete handle (first args) branch)]
               (case result
                 0 (println "OK")
                 1 (binding [*out* *err*]
                     (println "Not found"))
                 (binding [*out* *err*]
                   (println "ERROR: Delete failed"))))

    "history" (if-let [result (lib/lib-history handle (first args) branch)]
                (println (pretty-json (json/read-str result)))
                (binding [*out* *err*]
                  (println "No history found")))

    "list" (if-let [result (lib/lib-list-by-prefix handle (or (first args) "") branch)]
             (println (pretty-json (json/read-str result)))
             (println "[]"))

    "tables" (if-let [result (lib/lib-list-by-prefix handle "" branch)]
               (let [docs (json/read-str result :key-fn keyword)
                     tables (->> docs
                                 (map :_table)
                                 (filter some?)
                                 distinct
                                 sort)]
                 (doseq [t tables]
                   (println t)))
               (println "No tables found"))

    (binding [*out* *err*]
      (println (str "Unknown command: " command)))))

(defn- repl-loop
  "Interactive REPL for the database."
  [handle branch]
  (println "ChronDB interactive shell. Type SQL or commands (get, put, delete, history, list, tables).")
  (println "Type .quit to exit.")
  (print "chrondb> ")
  (flush)
  (loop []
    (when-let [line (read-line)]
      (let [trimmed (str/trim line)]
        (when-not (or (= trimmed ".quit") (= trimmed ".exit"))
          (when-not (str/blank? trimmed)
            (try
              (if (or (str/starts-with? (str/upper-case trimmed) "SELECT")
                      (str/starts-with? (str/upper-case trimmed) "INSERT")
                      (str/starts-with? (str/upper-case trimmed) "UPDATE")
                      (str/starts-with? (str/upper-case trimmed) "DELETE")
                      (str/starts-with? (str/upper-case trimmed) "CREATE")
                      (str/starts-with? (str/upper-case trimmed) "DROP")
                      (str/starts-with? (str/upper-case trimmed) "SHOW")
                      (str/starts-with? (str/upper-case trimmed) "DESCRIBE"))
                (execute-sql handle trimmed branch)
                (let [parts (str/split trimmed #"\s+" 3)]
                  (execute-kv-command handle (first parts) (rest parts) branch)))
              (catch Exception e
                (binding [*out* *err*]
                  (println (str "ERROR: " (.getMessage e)))))))
          (print "chrondb> ")
          (flush)
          (recur))))))

(defn usage []
  (str "ChronDB Local CLI\n\n"
       "Usage: chrondb <db-path> [options] [command]\n\n"
       "Open a database and interact with it directly (no server required).\n\n"
       "Examples:\n"
       "  chrondb ./mydb                              # Interactive shell\n"
       "  chrondb ./mydb \"SELECT * FROM users\"        # Execute SQL\n"
       "  chrondb ./mydb get user:1                   # Get document\n"
       "  chrondb ./mydb put user:1 '{\"name\":\"Alice\"}' # Save document\n"
       "  chrondb ./mydb history user:1               # Document history\n"
       "  chrondb ./mydb list users:                  # List by prefix\n"
       "  chrondb ./mydb tables                       # List tables\n\n"
       "Options:\n"
       "  -b, --branch BRANCH  Branch name\n"
       "  -h, --help           Show help\n"
       "  -v, --version        Show version"))

(defn -main
  [& argv]
  (let [{:keys [options arguments errors]} (cli/parse-opts argv cli-options :in-order true)]
    (when (:help options)
      (println (usage))
      (System/exit 0))
    (when (:version options)
      (println "ChronDB 0.1.1")
      (System/exit 0))
    (when errors
      (binding [*out* *err*]
        (println (str "Error: " (str/join "\n" errors))))
      (System/exit 1))
    (when (empty? arguments)
      (println (usage))
      (System/exit 1))

    (let [[db-path & rest-args] arguments
          branch (:branch options)
          handle (lib/lib-open-path db-path)]
      (when (< handle 0)
        (binding [*out* *err*]
          (println (str "ERROR: Failed to open database at " db-path)))
        (System/exit 1))
      (try
        (if (empty? rest-args)
          ;; Interactive REPL
          (repl-loop handle branch)
          ;; Single command execution
          (let [input (str/join " " rest-args)]
            (if (or (str/starts-with? (str/upper-case input) "SELECT")
                    (str/starts-with? (str/upper-case input) "INSERT")
                    (str/starts-with? (str/upper-case input) "UPDATE")
                    (str/starts-with? (str/upper-case input) "DELETE")
                    (str/starts-with? (str/upper-case input) "CREATE")
                    (str/starts-with? (str/upper-case input) "DROP")
                    (str/starts-with? (str/upper-case input) "SHOW")
                    (str/starts-with? (str/upper-case input) "DESCRIBE"))
              (execute-sql handle input branch)
              (let [parts (str/split input #"\s+" 3)]
                (execute-kv-command handle (first parts) (rest parts) branch)))))
        (finally
          (lib/lib-close handle))))))

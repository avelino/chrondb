(ns chrondb.docs
  (:require [codox.main :as codox]
            [clojure.string :as string]))

(defn -main
  [& _]
  (codox/generate-docs
   {:name "ChronDB"
    :version (-> (slurp "VERSION") string/trim)
    :description "Chronological key/value Database storing based on database-shaped git (core) architecture"
    :source-paths ["src"]
    :output-path "docs/api"
    :source-uri "https://github.com/chrondb/chrondb/blob/main/{filepath}#L{line}"
    :metadata {:doc/format :markdown}
    :themes [:default]
    :namespaces '[chrondb.core
                  chrondb.storage.git
                  chrondb.storage.memory
                  chrondb.storage.protocol
                  chrondb.index.lucene
                  chrondb.index.protocol
                  chrondb.index.memory
                  chrondb.api.redis.core
                  chrondb.api.redis.server
                  chrondb.util.logging]}))

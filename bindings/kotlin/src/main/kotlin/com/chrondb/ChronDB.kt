package com.chrondb

import clojure.java.api.Clojure
import clojure.lang.IFn
import org.json.JSONArray
import org.json.JSONObject

/** Base exception for all ChronDB errors. */
open class ChronDBException(message: String) : Exception(message)

/** Thrown when a document is not found. */
class DocumentNotFoundException(message: String = "Document not found") : ChronDBException(message)

/**
 * A connection to a ChronDB database instance.
 *
 * Uses the ChronDB Clojure core directly on the JVM — no FFI, no native-image.
 *
 * ```kotlin
 * val db = ChronDB.openPath("/tmp/mydb")
 * db.put("user:1", mapOf("name" to "Alice", "age" to 30))
 * val doc = db.get("user:1")
 * db.close()
 * ```
 */
class ChronDB private constructor(private val handle: Int) : AutoCloseable {

    companion object {
        private val require: IFn = Clojure.`var`("clojure.core", "require")
        private val read: IFn = Clojure.`var`("clojure.core", "read-string")

        init {
            require.invoke(read.invoke("chrondb.lib.core"))
        }

        private fun libFn(name: String): IFn = Clojure.`var`("chrondb.lib.core", name)

        /**
         * Open a database using a single directory path (preferred).
         *
         * @param dbPath Path for the database directory
         */
        fun openPath(dbPath: String): ChronDB {
            val handle = libFn("lib-open-path").invoke(dbPath) as? Long
                ?: throw ChronDBException("Failed to open database")
            val h = handle.toInt()
            if (h < 0) {
                val err = lastErrorGlobal() ?: "Failed to open database"
                throw ChronDBException(err)
            }
            return ChronDB(h)
        }

        /**
         * Open a database with separate data and index paths.
         *
         * @deprecated Use [openPath] instead — the index is managed automatically.
         */
        @Deprecated("Use openPath() instead — the index is managed automatically.")
        fun open(dataPath: String, indexPath: String): ChronDB {
            val handle = libFn("lib-open").invoke(dataPath, indexPath) as? Long
                ?: throw ChronDBException("Failed to open database")
            val h = handle.toInt()
            if (h < 0) {
                val err = lastErrorGlobal() ?: "Failed to open database"
                throw ChronDBException(err)
            }
            return ChronDB(h)
        }

        private fun lastErrorGlobal(): String? {
            return libFn("lib-last-error").invoke() as? String
        }
    }

    // -- CRUD --

    /**
     * Save a document.
     *
     * @param id Document ID (e.g., "user:1")
     * @param doc Document data as a map
     * @param branch Optional branch name
     * @return The saved document as a map
     */
    fun put(id: String, doc: Map<String, Any?>, branch: String? = null): Map<String, Any?> {
        val jsonDoc = JSONObject(doc).toString()
        val result = libFn("lib-put").invoke(handle, id, jsonDoc, branch)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Put failed")
        return parseObject(result)
    }

    /**
     * Get a document by ID.
     *
     * @param id Document ID
     * @param branch Optional branch name
     * @return The document as a map
     * @throws DocumentNotFoundException if not found
     */
    fun get(id: String, branch: String? = null): Map<String, Any?> {
        val result = libFn("lib-get").invoke(handle, id, branch)
            as? String
        if (result == null) {
            val err = lastErrorGlobal()
            if (err != null && err.lowercase().contains("not found")) {
                throw DocumentNotFoundException(err)
            }
            throw ChronDBException(err ?: "Get failed")
        }
        return parseObject(result)
    }

    /**
     * Delete a document by ID.
     *
     * @param id Document ID
     * @param branch Optional branch name
     * @throws DocumentNotFoundException if not found
     */
    fun delete(id: String, branch: String? = null) {
        val result = (libFn("lib-delete").invoke(handle, id, branch) as? Long)?.toInt() ?: -1
        when (result) {
            0 -> return
            1 -> throw DocumentNotFoundException()
            else -> throw ChronDBException(lastErrorGlobal() ?: "Delete failed")
        }
    }

    // -- Query --

    fun listByPrefix(prefix: String, branch: String? = null): Any {
        val result = libFn("lib-list-by-prefix").invoke(handle, prefix, branch)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "List by prefix failed")
        return parseAny(result)
    }

    fun listByTable(table: String, branch: String? = null): Any {
        val result = libFn("lib-list-by-table").invoke(handle, table, branch)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "List by table failed")
        return parseAny(result)
    }

    fun history(id: String, branch: String? = null): Any {
        val result = libFn("lib-history").invoke(handle, id, branch)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "History failed")
        return parseAny(result)
    }

    fun query(query: Map<String, Any?>, branch: String? = null): Any {
        val queryJson = JSONObject(query).toString()
        val result = libFn("lib-query").invoke(handle, queryJson, branch)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Query failed")
        return parseAny(result)
    }

    fun execute(sql: String, branch: String? = null): Any {
        val result = libFn("lib-execute-sql").invoke(handle, sql, branch)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "SQL execution failed")
        return parseAny(result)
    }

    // -- Remote --

    fun setupRemote(remoteUrl: String): Any {
        val result = libFn("lib-setup-remote").invoke(handle, remoteUrl)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Setup remote failed")
        return parseAny(result)
    }

    fun push(): Any {
        val result = libFn("lib-push").invoke(handle)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Push failed")
        return parseAny(result)
    }

    fun pull(): Any {
        val result = libFn("lib-pull").invoke(handle)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Pull failed")
        return parseAny(result)
    }

    fun fetch(): Any {
        val result = libFn("lib-fetch").invoke(handle)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Fetch failed")
        return parseAny(result)
    }

    fun remoteStatus(): Any {
        val result = libFn("lib-remote-status").invoke(handle)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Remote status failed")
        return parseAny(result)
    }

    // -- Export & Backup --

    fun exportToDirectory(
        targetDir: String,
        branch: String? = null,
        prefix: String? = null,
        format: String = "json",
        decodePaths: Boolean = true,
        overwrite: Boolean = false
    ): Any {
        val opts = mutableMapOf<String, Any>()
        branch?.let { opts["branch"] = it }
        prefix?.let { opts["prefix"] = it }
        if (format != "json") opts["format"] = format
        if (!decodePaths) opts["decode_paths"] = false
        if (overwrite) opts["overwrite"] = true
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        val result = libFn("lib-export").invoke(handle, targetDir, optsJson)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Export failed")
        return parseAny(result)
    }

    fun createBackup(outputPath: String, format: String = "tar.gz", verify: Boolean = true): Any {
        val opts = mutableMapOf<String, Any>()
        if (format != "tar.gz") opts["format"] = format
        if (!verify) opts["verify"] = false
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        val result = libFn("lib-create-backup").invoke(handle, outputPath, optsJson)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Backup failed")
        return parseAny(result)
    }

    fun restoreBackup(inputPath: String, format: String = "tar.gz", verify: Boolean = true): Any {
        val opts = mutableMapOf<String, Any>()
        if (format != "tar.gz") opts["format"] = format
        if (!verify) opts["verify"] = false
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        val result = libFn("lib-restore-backup").invoke(handle, inputPath, optsJson)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Restore failed")
        return parseAny(result)
    }

    fun exportSnapshot(outputPath: String, refs: List<String>? = null): Any {
        val opts = mutableMapOf<String, Any>()
        refs?.let { opts["refs"] = it }
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        val result = libFn("lib-export-snapshot").invoke(handle, outputPath, optsJson)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Export snapshot failed")
        return parseAny(result)
    }

    fun importSnapshot(inputPath: String): Any {
        val result = libFn("lib-import-snapshot").invoke(handle, inputPath, null)
            as? String ?: throw ChronDBException(lastErrorGlobal() ?: "Import snapshot failed")
        return parseAny(result)
    }

    override fun close() {
        libFn("lib-close").invoke(handle)
    }

    // -- Internal --

    private fun parseObject(json: String): Map<String, Any?> = JSONObject(json).toMap()

    private fun parseAny(json: String): Any {
        return if (json.trimStart().startsWith("[")) JSONArray(json).toList()
        else JSONObject(json).toMap()
    }
}

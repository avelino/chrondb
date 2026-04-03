package com.chrondb

import org.json.JSONArray
import org.json.JSONObject
import uniffi.chrondb.ChronDb as ChronDbInner
import uniffi.chrondb.ChronDbException as InnerException

/** Base exception for all ChronDB errors. */
open class ChronDBException(message: String) : Exception(message)

/** Thrown when a document is not found. */
class DocumentNotFoundException(message: String = "Document not found") : ChronDBException(message)

/**
 * A connection to a ChronDB database instance.
 *
 * ```kotlin
 * val db = ChronDB.openPath("/tmp/mydb")
 * db.put("user:1", mapOf("name" to "Alice", "age" to 30))
 * val doc = db.get("user:1")
 * db.close()
 * ```
 */
class ChronDB private constructor(private val inner: ChronDbInner) : AutoCloseable {

    companion object {
        /**
         * Open a database using a single directory path (preferred).
         *
         * @param dbPath Path for the database directory
         */
        fun openPath(dbPath: String): ChronDB {
            return try {
                ChronDB(ChronDbInner.openPath(dbPath))
            } catch (e: InnerException) {
                throw mapError(e)
            }
        }

        /**
         * Open a database with separate data and index paths.
         *
         * @deprecated Use [openPath] instead — the index is managed automatically.
         */
        @Deprecated("Use openPath() instead — the index is managed automatically.")
        fun open(dataPath: String, indexPath: String): ChronDB {
            return try {
                ChronDB(ChronDbInner.open(dataPath, indexPath))
            } catch (e: InnerException) {
                throw mapError(e)
            }
        }

        /**
         * Open a database with idle timeout.
         *
         * @param dataPath Path for data storage
         * @param indexPath Path for the Lucene index
         * @param idleTimeoutSecs Seconds before suspending the GraalVM isolate
         */
        fun openWithIdleTimeout(dataPath: String, indexPath: String, idleTimeoutSecs: ULong): ChronDB {
            return try {
                ChronDB(ChronDbInner.openWithIdleTimeout(dataPath, indexPath, idleTimeoutSecs))
            } catch (e: InnerException) {
                throw mapError(e)
            }
        }

        private fun mapError(e: InnerException): ChronDBException {
            return when (e) {
                is InnerException.NotFound -> DocumentNotFoundException(e.message ?: "Document not found")
                is InnerException.SetupFailed -> ChronDBException("Library setup failed: ${e.msg}")
                is InnerException.OpenFailed -> ChronDBException("Failed to open database: ${e.msg}")
                is InnerException.OperationFailed -> ChronDBException("Operation failed: ${e.msg}")
                is InnerException.JsonError -> ChronDBException("JSON error: ${e.msg}")
                is InnerException.IsolateCreationFailed -> ChronDBException("Failed to create GraalVM isolate")
                is InnerException.CloseFailed -> ChronDBException("Failed to close database")
                else -> ChronDBException(e.message ?: "Unknown error")
            }
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
        return try {
            parseObject(inner.put(id, jsonDoc, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
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
        return try {
            parseObject(inner.get(id, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Delete a document by ID.
     *
     * @param id Document ID
     * @param branch Optional branch name
     * @throws DocumentNotFoundException if not found
     */
    fun delete(id: String, branch: String? = null) {
        try {
            inner.delete(id, branch)
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    // -- Query --

    /**
     * List documents by ID prefix.
     *
     * @param prefix ID prefix to match
     * @param branch Optional branch name
     * @return Parsed JSON result (List or Map)
     */
    fun listByPrefix(prefix: String, branch: String? = null): Any {
        return try {
            parseAny(inner.listByPrefix(prefix, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * List documents by table name.
     *
     * @param table Table name
     * @param branch Optional branch name
     * @return Parsed JSON result
     */
    fun listByTable(table: String, branch: String? = null): Any {
        return try {
            parseAny(inner.listByTable(table, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Get the change history of a document.
     *
     * @param id Document ID
     * @param branch Optional branch name
     * @return List of historical versions
     */
    fun history(id: String, branch: String? = null): Any {
        return try {
            parseAny(inner.history(id, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Execute a query against the Lucene index.
     *
     * @param query Query in Lucene AST format
     * @param branch Optional branch name
     * @return Query results
     */
    fun query(query: Map<String, Any?>, branch: String? = null): Any {
        val queryJson = JSONObject(query).toString()
        return try {
            parseAny(inner.query(queryJson, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Execute a SQL query against the database.
     *
     * @param sql SQL statement
     * @param branch Optional branch name
     * @return Query results
     */
    fun execute(sql: String, branch: String? = null): Any {
        return try {
            parseAny(inner.executeSql(sql, branch))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    // -- Remote --

    /** Configure a remote URL for push/pull. */
    fun setupRemote(remoteUrl: String): Any {
        return try {
            parseAny(inner.setupRemote(remoteUrl))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /** Push changes to the configured remote. */
    fun push(): Any {
        return try {
            parseAny(inner.push())
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /** Pull changes from the configured remote. */
    fun pull(): Any {
        return try {
            parseAny(inner.pull())
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /** Fetch changes from the configured remote without merging. */
    fun fetch(): Any {
        return try {
            parseAny(inner.fetch())
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /** Get the remote synchronization status. */
    fun remoteStatus(): Any {
        return try {
            parseAny(inner.remoteStatus())
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    // -- Export & Backup --

    /**
     * Export the repository tree to a filesystem directory.
     *
     * @param targetDir Target directory path
     * @param branch Branch to export
     * @param prefix Only export paths matching prefix
     * @param format "json" (default) or "raw"
     * @param decodePaths Decode encoded paths (default: true)
     * @param overwrite Overwrite existing target (default: false)
     * @return Export metadata
     */
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
        return try {
            parseAny(inner.exportToDirectory(targetDir, optsJson))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Create a full backup of the repository.
     *
     * @param outputPath Backup file path
     * @param format "tar.gz" (default) or "bundle"
     * @param verify Run integrity checks (default: true)
     * @return Backup metadata
     */
    fun createBackup(outputPath: String, format: String = "tar.gz", verify: Boolean = true): Any {
        val opts = mutableMapOf<String, Any>()
        if (format != "tar.gz") opts["format"] = format
        if (!verify) opts["verify"] = false
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        return try {
            parseAny(inner.createBackup(outputPath, optsJson))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Restore the repository from a backup file.
     *
     * @param inputPath Backup file path
     * @param format "tar.gz" (default) or "bundle"
     * @param verify Run integrity checks (default: true)
     * @return Restore metadata
     */
    fun restoreBackup(inputPath: String, format: String = "tar.gz", verify: Boolean = true): Any {
        val opts = mutableMapOf<String, Any>()
        if (format != "tar.gz") opts["format"] = format
        if (!verify) opts["verify"] = false
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        return try {
            parseAny(inner.restoreBackup(inputPath, optsJson))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Export the repository to a git bundle snapshot.
     *
     * @param outputPath Bundle file path
     * @param refs Refs to include (null for all)
     * @return Snapshot metadata
     */
    fun exportSnapshot(outputPath: String, refs: List<String>? = null): Any {
        val opts = mutableMapOf<String, Any>()
        refs?.let { opts["refs"] = it }
        val optsJson = if (opts.isEmpty()) null else JSONObject(opts as Map<String, Any>).toString()
        return try {
            parseAny(inner.exportSnapshot(outputPath, optsJson))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /**
     * Import a git bundle snapshot into the repository.
     *
     * @param inputPath Bundle file path
     * @return Import metadata
     */
    fun importSnapshot(inputPath: String): Any {
        return try {
            parseAny(inner.importSnapshot(inputPath, null))
        } catch (e: InnerException) {
            throw mapError(e)
        }
    }

    /** Get the last error message, if any. */
    fun lastError(): String? = inner.lastError()

    override fun close() {
        inner.close()
    }

    // -- Internal JSON helpers --

    private fun parseObject(json: String): Map<String, Any?> {
        return JSONObject(json).toMap()
    }

    private fun parseAny(json: String): Any {
        return if (json.trimStart().startsWith("[")) {
            JSONArray(json).toList()
        } else {
            JSONObject(json).toMap()
        }
    }
}

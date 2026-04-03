const { ChronDB: ChronDBNative } = require('./chrondb.node')

class ChronDB {
  /**
   * Open a ChronDB database.
   *
   * Preferred: pass a single path. The index is managed automatically.
   *   new ChronDB('/tmp/mydb')
   *
   * Legacy (deprecated): pass separate data and index paths.
   *   new ChronDB('/tmp/data', '/tmp/index')
   *
   * @param {string} dbPath - Path for the database directory
   * @param {string|Object} [indexPathOrOptions] - Deprecated index path, or options object
   * @param {Object} [options]
   * @param {number} [options.idleTimeout] - Seconds of inactivity before suspending the isolate
   */
  constructor(dbPath, indexPathOrOptions, options = {}) {
    if (typeof indexPathOrOptions === 'string') {
      // Legacy two-path mode (deprecated)
      if (typeof process !== 'undefined' && process.emitWarning) {
        process.emitWarning(
          'Passing separate dataPath and indexPath is deprecated. ' +
          'Use new ChronDB(dbPath) instead — the index is managed automatically.',
          'DeprecationWarning'
        )
      }
      const opts = options || {}
      if (opts.idleTimeout != null) {
        this._inner = ChronDBNative.openWithIdleTimeout(dbPath, indexPathOrOptions, opts.idleTimeout)
      } else {
        this._inner = ChronDBNative.open(dbPath, indexPathOrOptions)
      }
    } else {
      // Single-path mode (preferred)
      const opts = indexPathOrOptions || {}
      if (opts.idleTimeout != null) {
        const indexPath = `${dbPath}/.chrondb-index`
        this._inner = ChronDBNative.openWithIdleTimeout(dbPath, indexPath, opts.idleTimeout)
      } else {
        this._inner = ChronDBNative.openPath(dbPath)
      }
    }
  }

  /**
   * Save a document.
   * @param {string} id - Document ID (e.g., "user:1")
   * @param {Object} doc - Document data
   * @param {string} [branch] - Branch name
   * @returns {Object} The saved document
   */
  put(id, doc, branch = null) {
    const result = this._inner.put(id, JSON.stringify(doc), branch)
    return JSON.parse(result)
  }

  /**
   * Get a document by ID.
   * @param {string} id - Document ID
   * @param {string} [branch] - Branch name
   * @returns {Object} The document
   */
  get(id, branch = null) {
    const result = this._inner.get(id, branch)
    return JSON.parse(result)
  }

  /**
   * Delete a document by ID.
   * @param {string} id - Document ID
   * @param {string} [branch] - Branch name
   */
  delete(id, branch = null) {
    this._inner.delete(id, branch)
  }

  /**
   * List documents by ID prefix.
   * @param {string} prefix - ID prefix to match
   * @param {string} [branch] - Branch name
   * @returns {Array<Object>}
   */
  listByPrefix(prefix, branch = null) {
    const result = this._inner.listByPrefix(prefix, branch)
    return JSON.parse(result)
  }

  /**
   * List documents by table name.
   * @param {string} table - Table name
   * @param {string} [branch] - Branch name
   * @returns {Array<Object>}
   */
  listByTable(table, branch = null) {
    const result = this._inner.listByTable(table, branch)
    return JSON.parse(result)
  }

  /**
   * Get the change history of a document.
   * @param {string} id - Document ID
   * @param {string} [branch] - Branch name
   * @returns {Array<Object>}
   */
  history(id, branch = null) {
    const result = this._inner.history(id, branch)
    return JSON.parse(result)
  }

  /**
   * Execute a query against the Lucene index.
   * @param {Object} query - Query in Lucene AST format
   * @param {string} [branch] - Branch name
   * @returns {Object} Results with results, total, limit, offset
   */
  query(query, branch = null) {
    const result = this._inner.query(JSON.stringify(query), branch)
    return JSON.parse(result)
  }

  /**
   * Execute a SQL query against the database.
   * @param {string} sql - SQL statement
   * @param {string} [branch] - Branch name
   * @returns {Object} Results depending on query type
   */
  execute(sql, branch = null) {
    const result = this._inner.executeSql(sql, branch)
    return JSON.parse(result)
  }

  /**
   * Export the repository tree to a filesystem directory.
   * @param {string} targetDir - Target directory path
   * @param {Object} [options]
   * @param {string} [options.branch] - Branch to export
   * @param {string} [options.prefix] - Only export paths matching prefix
   * @param {string} [options.format] - "json" (pretty, default) or "raw"
   * @param {boolean} [options.decodePaths] - Decode encoded paths (default: true)
   * @param {boolean} [options.overwrite] - Overwrite existing target (default: false)
   * @returns {Object} Export metadata
   */
  exportToDirectory(targetDir, options = {}) {
    const opts = {}
    if (options.branch) opts.branch = options.branch
    if (options.prefix) opts.prefix = options.prefix
    if (options.format) opts.format = options.format
    if (options.decodePaths !== undefined) opts.decode_paths = options.decodePaths
    if (options.overwrite !== undefined) opts.overwrite = options.overwrite
    const result = this._inner.exportToDirectory(targetDir, JSON.stringify(opts))
    return JSON.parse(result)
  }

  /**
   * Create a full backup of the repository.
   * @param {string} outputPath - Backup file path
   * @param {Object} [options]
   * @param {string} [options.format] - "tar.gz" (default) or "bundle"
   * @param {boolean} [options.verify] - Run integrity checks (default: true)
   * @returns {Object} Backup metadata
   */
  createBackup(outputPath, options = {}) {
    const result = this._inner.createBackup(outputPath, JSON.stringify(options))
    return JSON.parse(result)
  }

  /**
   * Restore the repository from a backup file.
   * @param {string} inputPath - Backup file path
   * @param {Object} [options]
   * @param {string} [options.format] - "tar.gz" (default) or "bundle"
   * @param {boolean} [options.verify] - Run integrity checks (default: true)
   * @returns {Object} Restore metadata
   */
  restoreBackup(inputPath, options = {}) {
    const result = this._inner.restoreBackup(inputPath, JSON.stringify(options))
    return JSON.parse(result)
  }

  /**
   * Export the repository to a git bundle snapshot.
   * @param {string} outputPath - Bundle file path
   * @param {Object} [options]
   * @param {string[]} [options.refs] - Refs to include
   * @returns {Object} Snapshot metadata
   */
  exportSnapshot(outputPath, options = {}) {
    const result = this._inner.exportSnapshot(outputPath, JSON.stringify(options))
    return JSON.parse(result)
  }

  /**
   * Import a git bundle snapshot into the repository.
   * @param {string} inputPath - Bundle file path
   * @returns {Object} Import metadata
   */
  importSnapshot(inputPath) {
    const result = this._inner.importSnapshot(inputPath, '{}')
    return JSON.parse(result)
  }
}

module.exports = { ChronDB }

const { ChronDB: ChronDBNative } = require('./chrondb.node')

class ChronDB {
  /**
   * Open a ChronDB database.
   * @param {string} dataPath - Path for the Git repository (data storage)
   * @param {string} indexPath - Path for the Lucene index
   * @param {Object} [options]
   * @param {number} [options.idleTimeout] - Seconds of inactivity before suspending the isolate
   */
  constructor(dataPath, indexPath, options = {}) {
    if (options.idleTimeout != null) {
      this._inner = ChronDBNative.openWithIdleTimeout(dataPath, indexPath, options.idleTimeout)
    } else {
      this._inner = ChronDBNative.open(dataPath, indexPath)
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
}

module.exports = { ChronDB }

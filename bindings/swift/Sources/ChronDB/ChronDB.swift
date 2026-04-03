import Foundation

// The UniFFI-generated file is copied here as chrondb_generated.swift during CI build.
// It defines: ChronDb, ChronDbError, ChronDbProtocol

/// Errors thrown by ChronDB operations.
public enum ChronDBError: Error, LocalizedError {
    case notFound
    case setupFailed(String)
    case openFailed(String)
    case operationFailed(String)
    case jsonError(String)
    case isolateCreationFailed
    case closeFailed

    public var errorDescription: String? {
        switch self {
        case .notFound:
            return "Document not found"
        case .setupFailed(let msg):
            return "Library setup failed: \(msg)"
        case .openFailed(let msg):
            return "Failed to open database: \(msg)"
        case .operationFailed(let msg):
            return "Operation failed: \(msg)"
        case .jsonError(let msg):
            return "JSON error: \(msg)"
        case .isolateCreationFailed:
            return "Failed to create GraalVM isolate"
        case .closeFailed:
            return "Failed to close database"
        }
    }
}

/// A connection to a ChronDB database instance.
///
/// ```swift
/// let db = try ChronDBClient(dbPath: "/tmp/mydb")
/// let saved = try db.put(id: "user:1", doc: ["name": "Alice", "age": 30])
/// let doc = try db.get(id: "user:1")
/// ```
public class ChronDBClient {
    private let inner: ChronDb

    /// Open a ChronDB database using a single directory path (preferred).
    ///
    /// - Parameter dbPath: Path for the database directory
    public init(dbPath: String) throws {
        do {
            self.inner = try ChronDb.openPath(dbPath: dbPath)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Open a ChronDB database with separate data and index paths.
    ///
    /// - Note: Deprecated. Use `init(dbPath:)` instead.
    @available(*, deprecated, message: "Use init(dbPath:) instead — the index is managed automatically.")
    public init(dataPath: String, indexPath: String) throws {
        do {
            self.inner = try ChronDb.open(dataPath: dataPath, indexPath: indexPath)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Open a ChronDB database with idle timeout.
    ///
    /// - Parameters:
    ///   - dataPath: Path for data storage
    ///   - indexPath: Path for the Lucene index
    ///   - idleTimeoutSecs: Seconds before suspending the GraalVM isolate
    public init(dataPath: String, indexPath: String, idleTimeoutSecs: UInt64) throws {
        do {
            self.inner = try ChronDb.openWithIdleTimeout(
                dataPath: dataPath,
                indexPath: indexPath,
                idleTimeoutSecs: idleTimeoutSecs
            )
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    // MARK: - CRUD Operations

    /// Save a document.
    ///
    /// - Parameters:
    ///   - id: Document ID (e.g., "user:1")
    ///   - doc: Document data as a dictionary
    ///   - branch: Optional branch name
    /// - Returns: The saved document as a dictionary
    @discardableResult
    public func put(id: String, doc: [String: Any], branch: String? = nil) throws -> [String: Any] {
        let jsonData = try JSONSerialization.data(withJSONObject: doc)
        guard let jsonString = String(data: jsonData, encoding: .utf8) else {
            throw ChronDBError.jsonError("Failed to encode document to JSON")
        }
        do {
            let result = try inner.put(id: id, jsonDoc: jsonString, branch: branch)
            return try parseJSON(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Get a document by ID.
    ///
    /// - Parameters:
    ///   - id: Document ID
    ///   - branch: Optional branch name
    /// - Returns: The document as a dictionary
    /// - Throws: `ChronDBError.notFound` if the document does not exist
    public func get(id: String, branch: String? = nil) throws -> [String: Any] {
        do {
            let result = try inner.get(id: id, branch: branch)
            return try parseJSON(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Delete a document by ID.
    ///
    /// - Parameters:
    ///   - id: Document ID
    ///   - branch: Optional branch name
    /// - Throws: `ChronDBError.notFound` if the document does not exist
    public func delete(id: String, branch: String? = nil) throws {
        do {
            try inner.delete(id: id, branch: branch)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    // MARK: - Query Operations

    /// List documents by ID prefix.
    ///
    /// - Parameters:
    ///   - prefix: ID prefix to match
    ///   - branch: Optional branch name
    /// - Returns: Array of matching documents
    public func listByPrefix(prefix: String, branch: String? = nil) throws -> Any {
        do {
            let result = try inner.listByPrefix(prefix: prefix, branch: branch)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// List documents by table name.
    ///
    /// - Parameters:
    ///   - table: Table name
    ///   - branch: Optional branch name
    /// - Returns: Array of matching documents
    public func listByTable(table: String, branch: String? = nil) throws -> Any {
        do {
            let result = try inner.listByTable(table: table, branch: branch)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Get the change history of a document.
    ///
    /// - Parameters:
    ///   - id: Document ID
    ///   - branch: Optional branch name
    /// - Returns: Array of historical versions
    public func history(id: String, branch: String? = nil) throws -> Any {
        do {
            let result = try inner.history(id: id, branch: branch)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Execute a query against the Lucene index.
    ///
    /// - Parameters:
    ///   - query: Query in Lucene AST format
    ///   - branch: Optional branch name
    /// - Returns: Query results
    public func query(_ query: [String: Any], branch: String? = nil) throws -> Any {
        let jsonData = try JSONSerialization.data(withJSONObject: query)
        guard let jsonString = String(data: jsonData, encoding: .utf8) else {
            throw ChronDBError.jsonError("Failed to encode query to JSON")
        }
        do {
            let result = try inner.query(queryJson: jsonString, branch: branch)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Execute a SQL query against the database.
    ///
    /// - Parameters:
    ///   - sql: SQL statement
    ///   - branch: Optional branch name
    /// - Returns: Query results
    public func execute(sql: String, branch: String? = nil) throws -> Any {
        do {
            let result = try inner.executeSql(sql: sql, branch: branch)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    // MARK: - Remote Operations

    /// Configure a remote URL for push/pull.
    ///
    /// - Parameter remoteUrl: Git remote URL
    /// - Returns: Setup result
    @discardableResult
    public func setupRemote(remoteUrl: String) throws -> Any {
        do {
            let result = try inner.setupRemote(remoteUrl: remoteUrl)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Push changes to the configured remote.
    @discardableResult
    public func push() throws -> Any {
        do {
            let result = try inner.push()
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Pull changes from the configured remote.
    @discardableResult
    public func pull() throws -> Any {
        do {
            let result = try inner.pull()
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Fetch changes from the configured remote without merging.
    @discardableResult
    public func fetch() throws -> Any {
        do {
            let result = try inner.fetch()
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Get the remote synchronization status.
    public func remoteStatus() throws -> Any {
        do {
            let result = try inner.remoteStatus()
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    // MARK: - Export & Backup

    /// Export the repository tree to a filesystem directory.
    ///
    /// - Parameters:
    ///   - targetDir: Target directory path
    ///   - branch: Branch to export
    ///   - prefix: Only export paths matching prefix
    ///   - format: "json" (default) or "raw"
    ///   - decodePaths: Decode encoded paths (default: true)
    ///   - overwrite: Overwrite existing target (default: false)
    /// - Returns: Export metadata
    @discardableResult
    public func exportToDirectory(
        _ targetDir: String,
        branch: String? = nil,
        prefix: String? = nil,
        format: String = "json",
        decodePaths: Bool = true,
        overwrite: Bool = false
    ) throws -> Any {
        var opts: [String: Any] = [:]
        if let branch = branch { opts["branch"] = branch }
        if let prefix = prefix { opts["prefix"] = prefix }
        if format != "json" { opts["format"] = format }
        if !decodePaths { opts["decode_paths"] = false }
        if overwrite { opts["overwrite"] = true }
        let optsJson = opts.isEmpty ? nil : try encodeJSON(opts)
        do {
            let result = try inner.exportToDirectory(targetDir: targetDir, optionsJson: optsJson)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Create a full backup of the repository.
    ///
    /// - Parameters:
    ///   - outputPath: Backup file path
    ///   - format: "tar.gz" (default) or "bundle"
    ///   - verify: Run integrity checks (default: true)
    /// - Returns: Backup metadata
    @discardableResult
    public func createBackup(
        _ outputPath: String,
        format: String = "tar.gz",
        verify: Bool = true
    ) throws -> Any {
        var opts: [String: Any] = [:]
        if format != "tar.gz" { opts["format"] = format }
        if !verify { opts["verify"] = false }
        let optsJson = opts.isEmpty ? nil : try encodeJSON(opts)
        do {
            let result = try inner.createBackup(outputPath: outputPath, optionsJson: optsJson)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Restore the repository from a backup file.
    ///
    /// - Parameters:
    ///   - inputPath: Backup file path
    ///   - format: "tar.gz" (default) or "bundle"
    ///   - verify: Run integrity checks (default: true)
    /// - Returns: Restore metadata
    @discardableResult
    public func restoreBackup(
        _ inputPath: String,
        format: String = "tar.gz",
        verify: Bool = true
    ) throws -> Any {
        var opts: [String: Any] = [:]
        if format != "tar.gz" { opts["format"] = format }
        if !verify { opts["verify"] = false }
        let optsJson = opts.isEmpty ? nil : try encodeJSON(opts)
        do {
            let result = try inner.restoreBackup(inputPath: inputPath, optionsJson: optsJson)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Export the repository to a git bundle snapshot.
    ///
    /// - Parameters:
    ///   - outputPath: Bundle file path
    ///   - refs: Refs to include (nil for all)
    /// - Returns: Snapshot metadata
    @discardableResult
    public func exportSnapshot(_ outputPath: String, refs: [String]? = nil) throws -> Any {
        var opts: [String: Any] = [:]
        if let refs = refs { opts["refs"] = refs }
        let optsJson = opts.isEmpty ? nil : try encodeJSON(opts)
        do {
            let result = try inner.exportSnapshot(outputPath: outputPath, optionsJson: optsJson)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Import a git bundle snapshot into the repository.
    ///
    /// - Parameter inputPath: Bundle file path
    /// - Returns: Import metadata
    @discardableResult
    public func importSnapshot(_ inputPath: String) throws -> Any {
        do {
            let result = try inner.importSnapshot(inputPath: inputPath, optionsJson: nil)
            return try parseAny(result)
        } catch let error as ChronDbError {
            throw Self.mapError(error)
        }
    }

    /// Get the last error message, if any.
    public func lastError() -> String? {
        return inner.lastError()
    }

    // MARK: - Internal Helpers

    private func parseJSON(_ jsonString: String) throws -> [String: Any] {
        guard let data = jsonString.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ChronDBError.jsonError("Failed to parse JSON response")
        }
        return obj
    }

    private func parseAny(_ jsonString: String) throws -> Any {
        guard let data = jsonString.data(using: .utf8) else {
            throw ChronDBError.jsonError("Failed to parse JSON response")
        }
        return try JSONSerialization.jsonObject(with: data)
    }

    private func encodeJSON(_ dict: [String: Any]) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: dict)
        guard let str = String(data: data, encoding: .utf8) else {
            throw ChronDBError.jsonError("Failed to encode options to JSON")
        }
        return str
    }

    private static func mapError(_ error: ChronDbError) -> ChronDBError {
        switch error {
        case .NotFound:
            return .notFound
        case .SetupFailed(let msg):
            return .setupFailed(msg)
        case .OpenFailed(let msg):
            return .openFailed(msg)
        case .OperationFailed(let msg):
            return .operationFailed(msg)
        case .JsonError(let msg):
            return .jsonError(msg)
        case .IsolateCreationFailed:
            return .isolateCreationFailed
        case .CloseFailed:
            return .closeFailed
        }
    }
}

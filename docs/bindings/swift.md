# ChronDB Swift Binding

Swift client for ChronDB, auto-generated from the Rust SDK via [UniFFI](https://github.com/mozilla/uniffi-rs).

## Requirements

- Swift 5.5+
- macOS 12+ or Linux

## Installation

Download the platform-specific tarball from the [latest GitHub release](https://github.com/avelino/chrondb/releases):

- `chrondb-swift-{version}-macos-aarch64.tar.gz` (macOS Apple Silicon)
- `chrondb-swift-{version}-linux-x86_64.tar.gz` (Linux x86_64)

Extract and add as a local Swift package dependency:

```swift
// In your Package.swift
.package(path: "path/to/chrondb-swift")
```

## Quick Start

```swift
import ChronDB

// Single path (preferred)
let db = try ChronDB.openPath(dbPath: "./mydb")

// Save a document
try db.put(id: "user:1", jsonDoc: #"{"name": "Alice", "age": 30}"#, branch: nil)

// Retrieve it
let doc = try db.get(id: "user:1", branch: nil)
print(doc) // {"name":"Alice","age":30}
```

> **Legacy API (deprecated):** `ChronDB.open(dataPath:indexPath:)` still works but is deprecated. Use `openPath` instead.

## API Reference

### `ChronDB.openPath(dbPath:) throws -> ChronDB`

Opens a database connection using a single directory path. Data and index are stored in subdirectories automatically.

### `ChronDB.open(dataPath:indexPath:) throws -> ChronDB` *(deprecated)*

> **Deprecated.** Use `openPath` instead. This method still works but will be removed in a future release.

### `ChronDB.openWithIdleTimeout(dataPath:indexPath:idleTimeoutSecs:) throws -> ChronDB`

Opens a database with idle timeout.

### Operations

| Method | Description |
|--------|-------------|
| `put(id:jsonDoc:branch:)` | Save a document |
| `get(id:branch:)` | Get a document by ID |
| `delete(id:branch:)` | Delete a document |
| `listByPrefix(prefix:branch:)` | List by ID prefix |
| `listByTable(table:branch:)` | List by table |
| `history(id:branch:)` | Get change history |
| `query(queryJson:branch:)` | Execute a Lucene query |
| `executeSql(sql:branch:)` | Execute a SQL query directly |

## Error Handling

```swift
import ChronDB

do {
    let db = try ChronDB.openPath(dbPath: "./mydb")
    let doc = try db.get(id: "user:999", branch: nil)
} catch ChronDBError.NotFound {
    print("Document does not exist")
} catch {
    print("Database error: \(error)")
}
```

## Examples

### SQL Queries

Execute SQL queries directly without needing a running server:

```swift
let db = try ChronDB.openPath(dbPath: "./mydb")

try db.put(id: "user:1", jsonDoc: #"{"name": "Alice", "age": 30}"#, branch: nil)
try db.put(id: "user:2", jsonDoc: #"{"name": "Bob", "age": 25}"#, branch: nil)

let result = try db.executeSql(sql: "SELECT * FROM user", branch: nil)
print(result) // {"type":"select","columns":[...],"rows":[...],"count":2}
```

### Idle Timeout

```swift
// Isolate suspends after 2 minutes of inactivity
let db = try ChronDB.openWithIdleTimeout(
    dataPath: "/tmp/data",
    indexPath: "/tmp/index",
    idleTimeoutSecs: 120
)

try db.put(id: "audit:1", jsonDoc: #"{"action": "login"}"#, branch: nil)
```

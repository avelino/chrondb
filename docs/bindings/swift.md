# ChronDB Swift Binding

Swift client for ChronDB, auto-generated from the Rust SDK via [UniFFI](https://github.com/mozilla/uniffi-rs).

## Requirements

- Swift 5.5+
- macOS 12+ or Linux

## Installation

Add via Swift Package Manager:

```swift
.package(url: "https://github.com/avelino/chrondb-swift", from: "0.1.0")
```

## Quick Start

```swift
import ChronDB

let db = try ChronDB.open(dataPath: "/tmp/data", indexPath: "/tmp/index")

// Save a document
try db.put(id: "user:1", jsonDoc: #"{"name": "Alice", "age": 30}"#, branch: nil)

// Retrieve it
let doc = try db.get(id: "user:1", branch: nil)
print(doc) // {"name":"Alice","age":30}
```

## API Reference

### `ChronDB.open(dataPath:indexPath:) throws -> ChronDB`

Opens a database connection.

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

## Error Handling

```swift
import ChronDB

do {
    let db = try ChronDB.open(dataPath: "/tmp/data", indexPath: "/tmp/index")
    let doc = try db.get(id: "user:999", branch: nil)
} catch ChronDBError.NotFound {
    print("Document does not exist")
} catch {
    print("Database error: \(error)")
}
```

## Example with Idle Timeout

```swift
// Isolate suspends after 2 minutes of inactivity
let db = try ChronDB.openWithIdleTimeout(
    dataPath: "/tmp/data",
    indexPath: "/tmp/index",
    idleTimeoutSecs: 120
)

try db.put(id: "audit:1", jsonDoc: #"{"action": "login"}"#, branch: nil)
```

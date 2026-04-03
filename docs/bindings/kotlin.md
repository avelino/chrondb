# ChronDB Kotlin Binding

Kotlin client for ChronDB, auto-generated from the Rust SDK via [UniFFI](https://github.com/mozilla/uniffi-rs).

## Requirements

- Kotlin 1.6+
- JNA (Java Native Access)

## Installation

Download the platform-specific tarball from the [latest GitHub release](https://github.com/avelino/chrondb/releases):

- `chrondb-kotlin-{version}-macos-aarch64.tar.gz` (macOS Apple Silicon)
- `chrondb-kotlin-{version}-linux-x86_64.tar.gz` (Linux x86_64)

Extract and add as a local dependency:

```kotlin
// In your build.gradle.kts
dependencies {
    implementation(files("path/to/chrondb-kotlin/lib"))
}
```

## Quick Start

```kotlin
import chrondb.ChronDB
import org.json.JSONObject

// Single path (preferred)
val db = ChronDB.openPath("./mydb")

// Save a document
val doc = JSONObject().put("name", "Alice").put("age", 30)
db.put("user:1", doc.toString(), null)

// Retrieve it
val result = db.get("user:1", null)
println(result) // {"name":"Alice","age":30}
```

> **Legacy API (deprecated):** `ChronDB.open("/tmp/data", "/tmp/index")` still works but is deprecated. Use `openPath` instead.

## API Reference

### `ChronDB.openPath(dbPath: String): ChronDB`

Opens a database connection using a single directory path. Data and index are stored in subdirectories automatically.

### `ChronDB.open(dataPath: String, indexPath: String): ChronDB` *(deprecated)*

> **Deprecated.** Use `openPath` instead. This method still works but will be removed in a future release.

### `ChronDB.openWithIdleTimeout(dataPath: String, indexPath: String, idleTimeoutSecs: ULong): ChronDB`

Opens a database with idle timeout. The GraalVM isolate suspends after the specified seconds of inactivity.

### Operations

| Method | Description |
|--------|-------------|
| `put(id, jsonDoc, branch?)` | Save a document (JSON string in, JSON string out) |
| `get(id, branch?)` | Get a document by ID |
| `delete(id, branch?)` | Delete a document |
| `listByPrefix(prefix, branch?)` | List documents by ID prefix |
| `listByTable(table, branch?)` | List documents by table |
| `history(id, branch?)` | Get change history |
| `query(queryJson, branch?)` | Execute a Lucene query |
| `executeSql(sql, branch?)` | Execute a SQL query directly |

## Error Handling

```kotlin
import chrondb.ChronDB
import chrondb.ChronDBError

try {
    val db = ChronDB.openPath("./mydb")
    val doc = db.get("user:999", null)
} catch (e: ChronDBError.NotFound) {
    println("Document does not exist")
} catch (e: ChronDBError) {
    println("Database error: ${e.message}")
}
```

## Examples

### SQL Queries

Execute SQL queries directly without needing a running server:

```kotlin
val db = ChronDB.openPath("./mydb")

db.put("user:1", """{"name": "Alice", "age": 30}""", null)
db.put("user:2", """{"name": "Bob", "age": 25}""", null)

val result = db.executeSql("SELECT * FROM user", null)
println(result) // {"type":"select","columns":[...],"rows":[...],"count":2}
```

### Idle Timeout

```kotlin
// Isolate suspends after 2 minutes of inactivity
val db = ChronDB.openWithIdleTimeout("/tmp/data", "/tmp/index", 120u)

db.put("audit:1", """{"action": "login"}""", null)
```

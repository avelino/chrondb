# ChronDB Kotlin Binding

Kotlin client for ChronDB, auto-generated from the Rust SDK via [UniFFI](https://github.com/mozilla/uniffi-rs).

## Requirements

- Kotlin 1.6+
- JNA (Java Native Access)

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("run.avelino.chrondb:chrondb:0.1.0")
}
```

## Quick Start

```kotlin
import chrondb.ChronDB
import org.json.JSONObject

val db = ChronDB.open("/tmp/data", "/tmp/index")

// Save a document
val doc = JSONObject().put("name", "Alice").put("age", 30)
db.put("user:1", doc.toString(), null)

// Retrieve it
val result = db.get("user:1", null)
println(result) // {"name":"Alice","age":30}
```

## API Reference

### `ChronDB.open(dataPath: String, indexPath: String): ChronDB`

Opens a database connection.

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

## Error Handling

```kotlin
import chrondb.ChronDB
import chrondb.ChronDBError

try {
    val db = ChronDB.open("/tmp/data", "/tmp/index")
    val doc = db.get("user:999", null)
} catch (e: ChronDBError.NotFound) {
    println("Document does not exist")
} catch (e: ChronDBError) {
    println("Database error: ${e.message}")
}
```

## Example with Idle Timeout

```kotlin
// Isolate suspends after 2 minutes of inactivity
val db = ChronDB.openWithIdleTimeout("/tmp/data", "/tmp/index", 120u)

db.put("audit:1", """{"action": "login"}""", null)
```

# ChronDB Rust Binding

Rust client for ChronDB, a time-traveling key/value database built on Git architecture.

## Requirements

- Rust 1.56+ (2021 edition)
- `libclang` (for bindgen to generate FFI bindings)

## Stack Size (Handled Automatically)

ChronDB uses GraalVM native-image with Apache Lucene and JGit, which require deep call stacks (~64MB).

**This is handled automatically.** The Rust binding spawns a dedicated worker thread with a 64MB stack for all FFI operations. You do not need to configure `RUST_MIN_STACK`, `ulimit`, or any other stack settings.

The worker thread architecture also provides:
- Consistent stack size across all platforms
- No special CI configuration required
- Thread-safe operation via message passing

## Installation

Add `chrondb` from [crates.io](https://crates.io/crates/chrondb):

```bash
cargo add chrondb
```

Or add it directly to your `Cargo.toml`:

```toml
[dependencies]
chrondb = "*"
serde_json = "1"
```

### Native shared library

The crate requires the `libchrondb` native library at runtime. Download it from the [latest GitHub release](https://github.com/avelino/chrondb/releases/tag/latest):

**macOS (Apple Silicon):**

```bash
curl -L https://github.com/avelino/chrondb/releases/download/latest/libchrondb-latest-macos-aarch64.tar.gz | tar xz
```

**Linux (x86_64):**

```bash
curl -L https://github.com/avelino/chrondb/releases/download/latest/libchrondb-latest-linux-x86_64.tar.gz | tar xz
```

### Configure the runtime library path

The shared library must be discoverable at runtime:

**Linux:**

```bash
export LD_LIBRARY_PATH=/path/to/chrondb-rust/lib:$LD_LIBRARY_PATH
```

**macOS:**

```bash
export DYLD_LIBRARY_PATH=/path/to/chrondb-rust/lib:$DYLD_LIBRARY_PATH
```

### Library path (advanced)

To override the library location at build time, set `CHRONDB_LIB_DIR`:

```bash
export CHRONDB_LIB_DIR=/path/to/dir/with/libchrondb
```

## Quick Start

```rust
use chrondb::ChronDB;
use serde_json::json;

fn main() -> chrondb::Result<()> {
    // Single path (preferred)
    let db = ChronDB::open_path("./mydb")?;

    // Save a document
    db.put("user:1", &json!({"name": "Alice", "age": 30}), None)?;

    // Retrieve it
    let doc = db.get("user:1", None)?;
    println!("{}", doc); // {"name":"Alice","age":30}

    Ok(())
    // db is automatically closed via Drop
}
```

> **Legacy API (deprecated):** `ChronDB::open("/tmp/data", "/tmp/index")` still works but is deprecated. Use `open_path` instead.

## API Reference

### `ChronDB::open_path(db_path) -> Result<ChronDB>`

Opens a database connection using a single directory path. Data and index are stored in subdirectories automatically.

| Parameter | Type | Description |
|-----------|------|-------------|
| `db_path` | `&str` | Path for the database (data and index stored inside) |

**Returns:** `Result<ChronDB>`

**Errors:** `IsolateCreationFailed`, `OpenFailed(reason)`

---

### `ChronDB::open(data_path, index_path) -> Result<ChronDB>` *(deprecated)*

> **Deprecated.** Use `open_path` instead. This method still works but will be removed in a future release.

Opens a database connection with separate data and index paths.

| Parameter | Type | Description |
|-----------|------|-------------|
| `data_path` | `&str` | Path for the Git repository (data storage) |
| `index_path` | `&str` | Path for the Lucene index |

**Returns:** `Result<ChronDB>`

**Errors:** `IsolateCreationFailed`, `OpenFailed(reason)`

---

### `ChronDB::builder(data_path, index_path) -> ChronDBBuilder`

Creates a builder for opening a database with custom options like idle timeout.

| Parameter | Type | Description |
|-----------|------|-------------|
| `data_path` | `&str` | Path for the Git repository (data storage) |
| `index_path` | `&str` | Path for the Lucene index |

**Returns:** `ChronDBBuilder`

#### `ChronDBBuilder::idle_timeout(duration) -> Self`

Sets the idle timeout for the GraalVM isolate. When no operations happen for the specified duration, the isolate is automatically closed to free CPU and memory. The next operation transparently reopens it.

| Parameter | Type | Description |
|-----------|------|-------------|
| `duration` | `Duration` | Max idle time before suspending the isolate |

#### `ChronDBBuilder::open() -> Result<ChronDB>`

Opens the database with the configured options.

**Errors:** `IsolateCreationFailed`, `OpenFailed(reason)`

#### Example

```rust
use chrondb::ChronDB;
use std::time::Duration;

let db = ChronDB::builder("/tmp/data", "/tmp/index")
    .idle_timeout(Duration::from_secs(120))
    .open()?;

// Use normally — isolate suspends after 120s of inactivity,
// reopens transparently on next operation.
db.put("user:1", &json!({"name": "Alice"}), None)?;
```

---

### `put(&self, id, doc, branch) -> Result<serde_json::Value>`

Saves a document.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `&str` | Document ID (e.g., `"user:1"`) |
| `doc` | `&serde_json::Value` | Document data as JSON value |
| `branch` | `Option<&str>` | Branch name (`None` for default) |

**Returns:** The saved document as `serde_json::Value`.

**Errors:** `OperationFailed(reason)`

---

### `get(&self, id, branch) -> Result<serde_json::Value>`

Retrieves a document by ID.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `&str` | Document ID |
| `branch` | `Option<&str>` | Branch name |

**Returns:** The document as `serde_json::Value`.

**Errors:** `NotFound`, `OperationFailed(reason)`

---

### `delete(&self, id, branch) -> Result<()>`

Deletes a document by ID.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `&str` | Document ID |
| `branch` | `Option<&str>` | Branch name |

**Returns:** `Ok(())` on success.

**Errors:** `NotFound`, `OperationFailed(reason)`

---

### `list_by_prefix(&self, prefix, branch) -> Result<serde_json::Value>`

Lists documents whose IDs start with the given prefix.

| Parameter | Type | Description |
|-----------|------|-------------|
| `prefix` | `&str` | ID prefix to match |
| `branch` | `Option<&str>` | Branch name |

**Returns:** JSON array of matching documents (empty array if none).

---

### `list_by_table(&self, table, branch) -> Result<serde_json::Value>`

Lists all documents in a table.

| Parameter | Type | Description |
|-----------|------|-------------|
| `table` | `&str` | Table name |
| `branch` | `Option<&str>` | Branch name |

**Returns:** JSON array of matching documents (empty array if none).

---

### `history(&self, id, branch) -> Result<serde_json::Value>`

Returns the change history of a document.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `&str` | Document ID |
| `branch` | `Option<&str>` | Branch name |

**Returns:** JSON array of history entries (empty array if none).

---

### `query(&self, query, branch) -> Result<serde_json::Value>`

Executes a query against the Lucene index.

| Parameter | Type | Description |
|-----------|------|-------------|
| `query` | `&serde_json::Value` | Query in Lucene AST format |
| `branch` | `Option<&str>` | Branch name |

**Returns:** Results object with `results`, `total`, `limit`, `offset`.

**Errors:** `OperationFailed(reason)`

---

### `execute_sql(&self, sql, branch) -> Result<serde_json::Value>`

Executes a SQL query directly against the database without needing a running server.

| Parameter | Type | Description |
|-----------|------|-------------|
| `sql` | `&str` | SQL query string |
| `branch` | `Option<&str>` | Branch name (`None` for default) |

**Returns:** Result object with `type`, `columns`, `rows`, `count`.

**Errors:** `OperationFailed(reason)`

---

### `setup_remote(&self, remote_url) -> Result<serde_json::Value>`

Configures a remote Git repository URL for syncing.

| Parameter | Type | Description |
|-----------|------|-------------|
| `remote_url` | `&str` | Remote Git URL (e.g., `"git@github.com:org/repo.git"`) |

**Returns:** `{"type":"ok","remote_url":"..."}` on success.

**Errors:** `OperationFailed(reason)`

---

### `push(&self) -> Result<serde_json::Value>`

Pushes local changes to the configured remote repository.

**Returns:** `{"type":"ok","status":"pushed"}` on success, `{"type":"ok","status":"skipped"}` if no remote is configured.

**Errors:** `OperationFailed(reason)`

---

### `pull(&self) -> Result<serde_json::Value>`

Pulls changes from the configured remote repository. Fetches and fast-forwards the local branch.

**Returns:** `{"type":"ok","status":"pulled|current|skipped|conflict"}`.

**Errors:** `OperationFailed(reason)`

---

### `fetch(&self) -> Result<serde_json::Value>`

Fetches changes from the configured remote without merging.

**Returns:** `{"type":"ok","status":"fetched|skipped"}`.

**Errors:** `OperationFailed(reason)`

---

### `remote_status(&self) -> Result<serde_json::Value>`

Returns whether a remote repository is configured.

**Returns:** `{"type":"ok","configured":true|false}`.

**Errors:** `OperationFailed(reason)`

---

### `last_error(&self) -> Option<String>`

Returns the last error message from the native library, if any.

---

### `Drop`

The `ChronDB` struct implements `Drop`. When it goes out of scope, the database connection is closed and the GraalVM isolate is torn down automatically.

## Document ID Convention

Documents use the format `table:id`:

```rust
db.put("user:123", &json!({"name": "Alice"}), None)?;
db.put("order:456", &json!({"total": 99.90}), None)?;
```

Internally, `user:123` is stored as `user/user_COLON_123.json` in the Git repository.

Use `list_by_table("user")` to retrieve all documents in the `user` table.

## Error Handling

### `ChronDBError` Enum

```rust
pub enum ChronDBError {
    IsolateCreationFailed,   // GraalVM isolate could not be created
    OpenFailed(String),      // Database failed to open (with reason)
    CloseFailed,             // Database failed to close
    NotFound,                // Document does not exist
    OperationFailed(String), // Operation failed (with reason)
    JsonError(String),       // JSON serialization/deserialization error
}
```

All variants implement `Display` and `std::error::Error`.

The crate also provides a type alias:

```rust
pub type Result<T> = std::result::Result<T, ChronDBError>;
```

### Conversion

`serde_json::Error` is automatically converted to `ChronDBError::JsonError` via `From`.

### Example

```rust
use chrondb::{ChronDB, ChronDBError};

fn main() {
    let db = ChronDB::open_path("./mydb").unwrap();

    match db.get("user:999", None) {
        Ok(doc) => println!("Found: {}", doc),
        Err(ChronDBError::NotFound) => println!("Document does not exist"),
        Err(e) => eprintln!("Error: {}", e),
    }
}
```

## Examples

### Full CRUD

```rust
use chrondb::ChronDB;
use serde_json::json;

fn main() -> chrondb::Result<()> {
    let db = ChronDB::open_path("./mydb")?;

    // Create
    db.put("user:1", &json!({"name": "Alice", "email": "alice@example.com"}), None)?;
    db.put("user:2", &json!({"name": "Bob", "email": "bob@example.com"}), None)?;

    // Read
    let alice = db.get("user:1", None)?;

    // Update
    let mut updated = alice.clone();
    updated["age"] = json!(30);
    db.put("user:1", &updated, None)?;

    // Delete
    db.delete("user:2", None)?;

    // List by table
    let users = db.list_by_table("user", None)?;
    println!("Users: {}", users);

    // List by prefix
    let matched = db.list_by_prefix("user:1", None)?;
    println!("Matched: {}", matched);

    Ok(())
}
```

### Query

```rust
use chrondb::ChronDB;
use serde_json::json;

fn main() -> chrondb::Result<()> {
    let db = ChronDB::open_path("./mydb")?;

    db.put("product:1", &json!({"name": "Laptop", "price": 999}), None)?;
    db.put("product:2", &json!({"name": "Mouse", "price": 29}), None)?;

    let results = db.query(&json!({
        "type": "term",
        "field": "name",
        "value": "Laptop"
    }), None)?;

    println!("Total: {}", results["total"]); // 1

    Ok(())
}
```

### History (Time Travel)

```rust
use chrondb::ChronDB;
use serde_json::json;

fn main() -> chrondb::Result<()> {
    let db = ChronDB::open_path("./mydb")?;

    db.put("config:app", &json!({"version": "1.0"}), None)?;
    db.put("config:app", &json!({"version": "2.0"}), None)?;

    let entries = db.history("config:app", None)?;
    println!("History: {}", entries);

    Ok(())
}
```

### SQL Queries

Execute SQL queries directly without needing a running server:

```rust
use chrondb::ChronDB;
use serde_json::json;

fn main() -> chrondb::Result<()> {
    let db = ChronDB::open_path("./mydb")?;

    db.put("user:1", &json!({"name": "Alice", "age": 30}), None)?;
    db.put("user:2", &json!({"name": "Bob", "age": 25}), None)?;

    let result = db.execute_sql("SELECT * FROM user", None)?;
    println!("Columns: {}", result["columns"]);
    println!("Count: {}", result["count"]);

    let result = db.execute_sql("SELECT * FROM user WHERE name = 'Alice'", None)?;
    println!("Rows: {}", result["rows"]);

    Ok(())
}
```

### Remote Sync

```rust
use chrondb::ChronDB;
use serde_json::json;

fn main() -> chrondb::Result<()> {
    let db = ChronDB::open_path("./mydb")?;

    // Configure remote
    db.setup_remote("git@github.com:org/data.git")?;

    // Write data locally
    db.put("user:1", &json!({"name": "Alice"}), None)?;

    // Push to remote
    let result = db.push()?;
    println!("Push: {}", result["status"]); // "pushed"

    // Pull latest from remote
    let result = db.pull()?;
    println!("Pull: {}", result["status"]); // "pulled" or "current"

    // Check remote status
    let status = db.remote_status()?;
    println!("Remote configured: {}", status["configured"]); // true

    Ok(())
}
```

### Using with `Drop` (automatic cleanup)

```rust
use chrondb::ChronDB;
use serde_json::json;

fn do_work() -> chrondb::Result<()> {
    let db = ChronDB::open_path("./mydb")?;
    db.put("temp:1", &json!({"data": "value"}), None)?;
    Ok(())
    // db is dropped here, closing the connection
}
```

### Idle Timeout (long-running services)

ChronDB loads a GraalVM native-image shared library whose internal threads consume CPU even when no operations are in flight. For long-running services with sporadic database access, use `idle_timeout` to automatically suspend the isolate when idle:

```rust
use chrondb::ChronDB;
use serde_json::json;
use std::time::Duration;

fn main() -> chrondb::Result<()> {
    // Isolate suspends after 2 minutes of inactivity
    let db = ChronDB::builder("/tmp/data", "/tmp/index")
        .idle_timeout(Duration::from_secs(120))
        .open()?;

    // Normal usage — no change in API
    db.put("audit:1", &json!({"action": "login"}), None)?;
    let doc = db.get("audit:1", None)?;

    // After 120s without operations, the GraalVM isolate is torn down.
    // The next call to put/get/query transparently reopens it.

    Ok(())
}
```

**When to use:**
- Long-running daemons that write to ChronDB intermittently (e.g., audit logging, MCP servers)
- Services where memory/CPU usage matters during idle periods

**When NOT to use:**
- Short-lived CLI tools (just use `ChronDB::open`)
- High-throughput services with constant database access (the isolate would never go idle)

## Building from Source

```bash
# 1. Build the shared library (requires Java 11+ and GraalVM)
cd chrondb/
clojure -M:shared

# 2. Build the Rust binding
cd bindings/rust/
CHRONDB_LIB_DIR=../../target cargo build

# 3. Run tests
CHRONDB_LIB_DIR=../../target \
  LD_LIBRARY_PATH=../../target \
  cargo test
```

### `build.rs` Behavior

The build script (`build.rs`):

1. Reads `CHRONDB_LIB_DIR` (defaults to `../../target`)
2. Configures `rustc-link-search` and `rustc-link-lib=dylib=chrondb`
3. If `libchrondb.h` and `graal_isolate.h` exist in that directory, generates FFI bindings via `bindgen`
4. Otherwise, uses stub bindings (`src/ffi_stub.rs`) to allow compilation without the native library

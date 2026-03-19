# ChronDB Python Binding

Python client for ChronDB, auto-generated from the Rust SDK via [UniFFI](https://github.com/mozilla/uniffi-rs).

## Requirements

- Python 3.8+

## Installation

Install directly from the [latest GitHub release](https://github.com/avelino/chrondb/releases/tag/latest):

**macOS (Apple Silicon):**

```bash
pip install https://github.com/avelino/chrondb/releases/download/latest/chrondb-latest-py3-none-macosx_14_0_arm64.whl
```

**Linux (x86_64):**

```bash
pip install https://github.com/avelino/chrondb/releases/download/latest/chrondb-latest-py3-none-manylinux_2_35_x86_64.whl
```

The wheel already includes the native shared library — no extra configuration needed.

### Library path (advanced)

If you need to override the bundled library (e.g., using a custom build), the binding searches in this order:

1. `CHRONDB_LIB_PATH` — full path to the library file
2. `CHRONDB_LIB_DIR` — directory containing the library
3. Bundled `lib/` inside the package
4. System paths: `/usr/local/lib`, `/usr/lib`

```bash
export CHRONDB_LIB_PATH=/path/to/libchrondb.so
```

## Quick Start

```python
from chrondb import ChronDB

# Single path (preferred)
with ChronDB("./mydb") as db:
    # Save a document
    db.put("user:1", {"name": "Alice", "age": 30})

    # Retrieve it
    doc = db.get("user:1")
    print(doc)  # {"name": "Alice", "age": 30}
```

> **Legacy API (deprecated):** `ChronDB("/tmp/data", "/tmp/index")` still works but is deprecated. Use the single-path form instead.

## API Reference

### `ChronDB(db_path: str, idle_timeout: float = None)`

Opens a database connection using a single directory path. Data and index are stored in subdirectories automatically.

| Parameter | Type | Description |
|-----------|------|-------------|
| `db_path` | `str` | Path for the database (data and index stored inside) |
| `idle_timeout` | `Optional[float]` | Seconds of inactivity before suspending the GraalVM isolate. `None` (default) keeps it alive for the entire lifetime. |

When `idle_timeout` is set, the GraalVM isolate is automatically closed after the specified seconds of inactivity, freeing CPU and memory. The next operation transparently reopens it. See [Idle Timeout](#idle-timeout-long-running-services) for details.

**Raises:** `ChronDBError` if the database cannot be opened.

Implements the context manager protocol (`__enter__` / `__exit__`), calling `close()` automatically on exit.

#### Legacy: `ChronDB(data_path: str, index_path: str, idle_timeout: float = None)`

> **Deprecated.** The two-path constructor still works but is deprecated. Use the single-path form above.

| Parameter | Type | Description |
|-----------|------|-------------|
| `data_path` | `str` | Path for the Git repository (data storage) |
| `index_path` | `str` | Path for the Lucene index |
| `idle_timeout` | `Optional[float]` | Seconds of inactivity before suspending the GraalVM isolate |

---

### `put(id, doc, branch=None) -> Dict[str, Any]`

Saves a document.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `str` | Document ID (e.g., `"user:1"`) |
| `doc` | `Dict[str, Any]` | Document data |
| `branch` | `Optional[str]` | Branch name (`None` for default) |

**Returns:** The saved document as a dictionary.

**Raises:** `ChronDBError` on failure.

---

### `get(id, branch=None) -> Dict[str, Any]`

Retrieves a document by ID.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `str` | Document ID |
| `branch` | `Optional[str]` | Branch name |

**Returns:** The document as a dictionary.

**Raises:** `DocumentNotFoundError` if not found, `ChronDBError` on failure.

---

### `delete(id, branch=None) -> bool`

Deletes a document by ID.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `str` | Document ID |
| `branch` | `Optional[str]` | Branch name |

**Returns:** `True` if deleted.

**Raises:** `DocumentNotFoundError` if not found, `ChronDBError` on failure.

---

### `list_by_prefix(prefix, branch=None) -> List[Dict[str, Any]]`

Lists documents whose IDs start with the given prefix.

| Parameter | Type | Description |
|-----------|------|-------------|
| `prefix` | `str` | ID prefix to match |
| `branch` | `Optional[str]` | Branch name |

**Returns:** List of matching documents (empty list if none).

---

### `list_by_table(table, branch=None) -> List[Dict[str, Any]]`

Lists all documents in a table.

| Parameter | Type | Description |
|-----------|------|-------------|
| `table` | `str` | Table name |
| `branch` | `Optional[str]` | Branch name |

**Returns:** List of matching documents (empty list if none).

---

### `history(id, branch=None) -> List[Dict[str, Any]]`

Returns the change history of a document.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `str` | Document ID |
| `branch` | `Optional[str]` | Branch name |

**Returns:** List of history entries (empty list if none).

---

### `query(query, branch=None) -> Dict[str, Any]`

Executes a query against the Lucene index.

| Parameter | Type | Description |
|-----------|------|-------------|
| `query` | `Dict[str, Any]` | Query in Lucene AST format |
| `branch` | `Optional[str]` | Branch name |

**Returns:** Results dict with keys `results`, `total`, `limit`, `offset`.

**Raises:** `ChronDBError` on failure.

---

### `execute(sql, branch=None) -> Dict[str, Any]`

Executes a SQL query directly against the database without needing a running server.

| Parameter | Type | Description |
|-----------|------|-------------|
| `sql` | `str` | SQL query string |
| `branch` | `Optional[str]` | Branch name (`None` for default) |

**Returns:** Result dict with keys `type`, `columns`, `rows`, `count`.

**Raises:** `ChronDBError` on failure.

---

### `close()`

Closes the database connection and releases native resources. Called automatically when using the context manager.

## Document ID Convention

Documents use the format `table:id`:

```python
db.put("user:123", {"name": "Alice"})
db.put("order:456", {"total": 99.90})
```

Internally, `user:123` is stored as `user/user_COLON_123.json` in the Git repository.

Use `list_by_table("user")` to retrieve all documents in the `user` table.

## Error Handling

### Exception Hierarchy

```
ChronDBError (base)
  └── DocumentNotFoundError
```

### `ChronDBError`

Base exception for all ChronDB errors (connection failures, operation errors, native library issues).

### `DocumentNotFoundError`

Raised by `get()` and `delete()` when the target document does not exist.

### Example

```python
from chrondb import ChronDB, ChronDBError, DocumentNotFoundError

try:
    with ChronDB("./mydb") as db:
        doc = db.get("user:999")
except DocumentNotFoundError:
    print("Document does not exist")
except ChronDBError as e:
    print(f"Database error: {e}")
```

## Examples

### Full CRUD

```python
from chrondb import ChronDB

with ChronDB("./mydb") as db:
    # Create
    db.put("user:1", {"name": "Alice", "email": "alice@example.com"})
    db.put("user:2", {"name": "Bob", "email": "bob@example.com"})

    # Read
    alice = db.get("user:1")

    # Update
    alice["age"] = 30
    db.put("user:1", alice)

    # Delete
    db.delete("user:2")

    # List by table
    users = db.list_by_table("user")

    # List by prefix
    matched = db.list_by_prefix("user:1")
```

### Query

```python
with ChronDB("./mydb") as db:
    db.put("product:1", {"name": "Laptop", "price": 999})
    db.put("product:2", {"name": "Mouse", "price": 29})

    results = db.query({
        "type": "term",
        "field": "name",
        "value": "Laptop"
    })
    print(results["total"])  # 1
```

### History (Time Travel)

```python
with ChronDB("./mydb") as db:
    db.put("config:app", {"version": "1.0"})
    db.put("config:app", {"version": "2.0"})

    entries = db.history("config:app")
    for entry in entries:
        print(entry)
```

### SQL Queries

Execute SQL queries directly without needing a running server:

```python
with ChronDB("./mydb") as db:
    db.put("user:1", {"name": "Alice", "age": 30})
    db.put("user:2", {"name": "Bob", "age": 25})

    result = db.execute("SELECT * FROM user")
    print(result["columns"])  # ["name", "age"]
    print(result["count"])    # 2

    result = db.execute("SELECT * FROM user WHERE name = 'Alice'")
    print(result["rows"])     # [{"name": "Alice", "age": 30}]
```

### Idle Timeout (long-running services)

ChronDB loads a GraalVM native-image shared library whose internal threads consume CPU even when no operations are in flight. For long-running services with sporadic database access, use `idle_timeout` to automatically suspend the isolate when idle:

```python
from chrondb import ChronDB

# Isolate suspends after 2 minutes of inactivity
db = ChronDB("./mydb", idle_timeout=120)

# Normal usage — no change in API
db.put("audit:1", {"action": "login"})
doc = db.get("audit:1")

# After 120s without operations, the GraalVM isolate is torn down.
# The next call to put/get/query transparently reopens it.
```

**When to use:**
- Long-running daemons that write to ChronDB intermittently (e.g., audit logging, MCP servers)
- Services where memory/CPU usage matters during idle periods

**When NOT to use:**
- Short-lived scripts (just use `ChronDB(...)` without timeout)
- High-throughput services with constant database access (the isolate would never go idle)

### Pytest Fixture

```python
import pytest
from chrondb import ChronDB

@pytest.fixture
def db(tmp_path):
    with ChronDB(str(tmp_path / "db")) as conn:
        yield conn

def test_put_and_get(db):
    db.put("item:1", {"value": 42})
    doc = db.get("item:1")
    assert doc["value"] == 42
```

## Building from Source

```bash
# 1. Build the shared library (requires Java 11+ and GraalVM)
cd chrondb/
clojure -M:shared

# 2. Install the Python package in development mode
cd bindings/python/
pip install -e .

# 3. Run tests
CHRONDB_LIB_DIR=../../target pytest
```

# ChronDB Ruby Binding

Ruby client for ChronDB, auto-generated from the Rust SDK via [UniFFI](https://github.com/mozilla/uniffi-rs).

## Requirements

- Ruby 3.0+

## Installation

```bash
gem install chrondb
```

## Quick Start

```ruby
require "chrondb"

db = ChronDB::Client.new("/tmp/data", "/tmp/index")

# Save a document
db.put("user:1", { name: "Alice", age: 30 })

# Retrieve it
doc = db.get("user:1")
puts doc  # {"name"=>"Alice", "age"=>30}
```

## API Reference

### `ChronDB::Client.new(data_path, index_path, idle_timeout: nil)`

Opens a database connection.

| Parameter | Type | Description |
|-----------|------|-------------|
| `data_path` | `String` | Path for the Git repository (data storage) |
| `index_path` | `String` | Path for the Lucene index |
| `idle_timeout` | `Integer` | Seconds of inactivity before suspending the GraalVM isolate |

**Raises:** `ChronDB::Error` if the database cannot be opened.

---

### `put(id, doc, branch: nil) -> Hash`

Saves a document.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `String` | Document ID (e.g., `"user:1"`) |
| `doc` | `Hash` | Document data |
| `branch` | `String` | Branch name (`nil` for default) |

---

### `get(id, branch: nil) -> Hash`

Retrieves a document by ID.

**Raises:** `ChronDB::DocumentNotFoundError` if not found.

---

### `delete(id, branch: nil) -> true`

Deletes a document by ID.

**Raises:** `ChronDB::DocumentNotFoundError` if not found.

---

### `list_by_prefix(prefix, branch: nil) -> Array<Hash>`

Lists documents whose IDs start with the given prefix.

---

### `list_by_table(table, branch: nil) -> Array<Hash>`

Lists all documents in a table.

---

### `history(id, branch: nil) -> Array<Hash>`

Returns the change history of a document.

---

### `query(query, branch: nil) -> Hash`

Executes a query against the Lucene index.

## Error Handling

```ruby
require "chrondb"

begin
  db = ChronDB::Client.new("/tmp/data", "/tmp/index")
  doc = db.get("user:999")
rescue ChronDB::DocumentNotFoundError
  puts "Document does not exist"
rescue ChronDB::Error => e
  puts "Database error: #{e.message}"
end
```

## Examples

### Full CRUD

```ruby
require "chrondb"

db = ChronDB::Client.new("/tmp/data", "/tmp/index")

# Create
db.put("user:1", { name: "Alice", email: "alice@example.com" })
db.put("user:2", { name: "Bob", email: "bob@example.com" })

# Read
alice = db.get("user:1")

# Update
alice["age"] = 30
db.put("user:1", alice)

# Delete
db.delete("user:2")

# List
users = db.list_by_table("user")
```

### Idle Timeout (long-running services)

```ruby
# Isolate suspends after 2 minutes of inactivity
db = ChronDB::Client.new("/tmp/data", "/tmp/index", idle_timeout: 120)

db.put("audit:1", { action: "login" })

# After 120s without operations, the GraalVM isolate is torn down.
# The next call transparently reopens it.
```

### Query

```ruby
db.put("product:1", { name: "Laptop", price: 999 })

results = db.query({
  type: "term",
  field: "name",
  value: "Laptop"
})

puts results["total"]  # 1
```

### History (Time Travel)

```ruby
db.put("config:app", { version: "1.0" })
db.put("config:app", { version: "2.0" })

entries = db.history("config:app")
entries.each { |entry| puts entry }
```

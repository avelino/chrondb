# ChronDB Go Binding

Go client for ChronDB, using cgo to call the native `libchrondb` shared library directly.

## Requirements

- Go 1.21+
- `libchrondb.dylib` (macOS) or `libchrondb.so` (Linux)
- C headers (`libchrondb.h`, `graal_isolate.h`)

## Installation

Download the platform-specific tarball from the [latest GitHub release](https://github.com/avelino/chrondb/releases):

- `chrondb-go-{version}-macos-aarch64.tar.gz` (macOS Apple Silicon)
- `chrondb-go-{version}-linux-x86_64.tar.gz` (Linux x86_64)

Extract and set up:

```bash
# Extract
tar xzf chrondb-go-*.tar.gz

# Set library path for linking and runtime
export CGO_LDFLAGS="-L/path/to/chrondb-go/lib"
export CGO_CFLAGS="-I/path/to/chrondb-go/include"
export LD_LIBRARY_PATH="/path/to/chrondb-go/lib"  # Linux
export DYLD_LIBRARY_PATH="/path/to/chrondb-go/lib"  # macOS
```

## Quick Start

```go
package main

import (
    "fmt"
    "log"

    chrondb "github.com/avelino/chrondb/bindings/go"
)

func main() {
    db, err := chrondb.OpenPath("./mydb")
    if err != nil {
        log.Fatal(err)
    }
    defer db.Close()

    // Save a document
    _, err = db.Put("user:1", map[string]any{"name": "Alice", "age": 30}, "")
    if err != nil {
        log.Fatal(err)
    }

    // Retrieve it
    doc, err := db.Get("user:1", "")
    if err != nil {
        log.Fatal(err)
    }
    fmt.Println(doc) // map[age:30 name:Alice]
}
```

## API Reference

### `chrondb.OpenPath(dbPath string) (*DB, error)`

Opens a database using a single directory path.

### `chrondb.Open(dataPath, indexPath string) (*DB, error)` *(deprecated)*

> **Deprecated.** Use `OpenPath` instead.

### Operations

| Method | Description |
|--------|-------------|
| `Put(id, doc, branch)` | Save a document (map in, map out) |
| `Get(id, branch)` | Get a document by ID |
| `Delete(id, branch)` | Delete a document |
| `ListByPrefix(prefix, branch)` | List by ID prefix |
| `ListByTable(table, branch)` | List by table |
| `History(id, branch)` | Get change history |
| `Query(query, branch)` | Execute a Lucene query |
| `Execute(sql, branch)` | Execute a SQL query |
| `Close()` | Close the connection |

Pass `""` (empty string) for default branch.

## Error Handling

```go
doc, err := db.Get("user:999", "")
if err == chrondb.ErrNotFound {
    fmt.Println("Document does not exist")
} else if err != nil {
    fmt.Printf("Database error: %v\n", err)
}
```

### Sentinel Errors

| Error | Description |
|-------|-------------|
| `ErrNotFound` | Document not found |
| `ErrClosed` | Operation on closed connection |

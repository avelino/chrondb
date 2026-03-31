# Production Best Practices

Lessons learned from integrating ChronDB in real-world applications. These apply to **all language bindings** (Rust, Python, Node.js, etc.) since they stem from the ChronDB core (GraalVM native image + Git storage).

## Values must be JSON objects

`put()` requires a JSON object as the document value. Scalar values (numbers, strings, booleans) will fail silently or return an error.

```json
// ❌ Fails
put("meta:counter", 42)

// ✅ Works
put("meta:counter", {"value": 42})
```

This applies to all bindings — always wrap scalars in an object.

## Batch writes: open once, write many, close

Opening ChronDB has overhead (GraalVM isolate startup). For bulk operations, open once and write all documents in a single session.

```
// ✅ Fast — single open/close
db = open_path("./mydb")
for doc in documents:
    db.put(doc.id, doc.data)
// close/drop db

// ❌ Slow — reopening per document
for doc in documents:
    db = open_path("./mydb")
    db.put(doc.id, doc.data)
    // close/drop db
```

The difference is orders of magnitude for large batches. Each `open_path()` creates or attaches to a GraalVM isolate with a dedicated worker thread.

## GraalVM heap exhaustion on large datasets

For sessions with thousands of `put()` calls, the GraalVM native image may run out of heap space:

```
Fatal error: java.lang.OutOfMemoryError: Could not allocate an aligned heap chunk
```

**Workaround:** Split large write batches into smaller sessions. Open, write a batch (e.g., 1000 docs), close, then reopen for the next batch. This allows GraalVM to reclaim memory between sessions.

## Index corruption after interrupted writes

If the process is killed during a write operation (Ctrl+C, SIGKILL, OOM), the Lucene index may be left with a stale `write.lock` file. The next `open_path()` will fail:

```
Failed to open database at <path>. Check that the paths are valid and writable.
```

**Recovery:** Delete the index directory and reopen. ChronDB rebuilds the index automatically from the Git data — no data is lost.

```bash
rm -rf ./mydb/index
# Next open_path() rebuilds the index from git objects
```

For production applications, implement automatic recovery: if `open_path()` fails, delete the index directory and retry.

## Git data directory is a bare repository

ChronDB stores data as a bare Git repository. The data directory contains Git objects directly (`HEAD`, `objects/`, `refs/`) **without** a `.git/` subdirectory.

If you need to interact with the Git repo directly, use `--git-dir`:

```bash
git --git-dir=./mydb/data log --oneline -5
```

## Remote sync

Since v0.2.1, ChronDB supports native remote Git operations via `setup_remote()`, `push()`, `pull()`, and `fetch()`. Use these instead of running Git CLI commands manually.

```
db = open_path("./mydb")
db.setup_remote("git@github.com:org/data.git")
db.pull()   // fetch + fast-forward
// ... write data ...
db.push()   // push to remote
```

**Note:** Passing a Git URL directly to `open_path()` does **not** clone the repository — it creates a literal directory with that name. Always use a local path and configure the remote separately with `setup_remote()`.

## `list_by_prefix` response format

`list_by_prefix()` returns a JSON array of objects. Each object includes an `id` field with the document key:

```json
[
  {"id": "user:1", "name": "Alice", ...},
  {"id": "user:2", "name": "Bob", ...}
]
```

Use the `id` field to extract document keys when iterating results.

## Idle timeout for long-running services

ChronDB's GraalVM isolate consumes CPU and memory even when idle. For services with sporadic database access (daemons, MCP servers, background workers), use `idle_timeout` to automatically suspend the isolate.

The isolate suspends after the timeout and transparently reopens on the next operation.

**Don't use for:** Short-lived CLI tools (just open and close normally) or high-throughput services where the isolate never goes idle.

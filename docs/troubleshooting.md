# Troubleshooting

Common issues and their solutions when running ChronDB.

## Startup Issues

### Port Already in Use

```
java.net.BindException: Address already in use
```

Another process is using the port. Check which process holds it and either stop that process or change the ChronDB port:

```bash
# Find what's using port 3000
lsof -i :3000

# Run ChronDB on different ports
clojure -M:run 8080 6380
```

Or disable unused protocols:

```bash
clojure -M:run --disable-redis --disable-sql
```

### Out of Memory on Startup

```
java.lang.OutOfMemoryError: Java heap space
```

ChronDB needs enough memory for the Lucene index and Git operations. Increase JVM heap:

```bash
java -Xmx2g -jar chrondb.jar
```

For Docker deployments, ensure the container memory limit matches the JVM heap:

```yaml
deploy:
  resources:
    limits:
      memory: 2g
```

### Configuration File Not Found

```
Could not find config file: config.edn
```

ChronDB looks for `config.edn` in the current directory by default. Specify the path explicitly:

```bash
java -jar chrondb.jar --config /path/to/config.edn
```

---

## Storage Issues

### Git Lock Files

```
Cannot lock refs/heads/main. Unable to create new lock file
```

A previous ChronDB process crashed or was killed without releasing Git locks. Remove stale lock files:

```bash
# Find lock files in the data directory
find data/ -name "*.lock" -type f

# Remove stale locks (ensure ChronDB is stopped first)
find data/ -name "*.lock" -type f -delete
```

ChronDB automatically detects and removes lock files older than 60 seconds on startup. If you see this error during normal operation, it may indicate concurrent processes accessing the same data directory. Only one ChronDB instance should use a given data directory.

### Disk Space Exhausted

```
No space left on device
```

Every write creates a Git commit. Over time, history accumulates. To reclaim space:

```bash
# Run Git garbage collection via ChronDB
java -jar chrondb.jar --command compact

# Or manually in the data directory
cd data/ && git gc --aggressive
```

Monitor disk usage proactively:

```bash
# Check data directory size
du -sh data/

# Check Git object database size
du -sh data/.git/objects/
```

The `/health` endpoint reports disk space status. Configure alerts for when free disk drops below 5 GB.

### Corrupted Git Repository

```
org.eclipse.jgit.errors.CorruptObjectException
```

If the Git repository is corrupted (e.g., due to a hard crash during a write):

```bash
# Verify repository integrity
cd data/ && git fsck --full

# Attempt automatic repair
cd data/ && git fsck --full --repair
```

If repair fails, restore from a backup:

```bash
clojure -M:run restore --input /path/to/backup.tar.gz
```

Always maintain regular backups. See the [Operations Guide](operations) for backup procedures.

---

## Index Issues

### Lucene Index Corruption

```
org.apache.lucene.index.CorruptIndexException
```

If the Lucene index is corrupted, ChronDB can rebuild it from the Git repository. The background reindexing process runs automatically on startup. To force a full reindex:

1. Stop ChronDB
2. Delete the index directory (default: `data/index/` or the configured index path)
3. Restart ChronDB — the reindexer will rebuild the index from Git commits

```bash
rm -rf data/index/
java -jar chrondb.jar
```

Monitor reindexing progress in the logs:

```
INFO chrondb.index.lucene - Starting background reindexing
INFO chrondb.index.lucene - Reindexing complete: 15000 documents indexed
```

### Search Returns Stale Results

The Lucene Near-Real-Time (NRT) reader refreshes automatically after writes. If search results seem stale:

1. Check that the write operation completed successfully (no errors in logs)
2. Check the `chrondb_wal_pending_entries` metric — a high value means writes are queued
3. If using remote sync, the document may not have been pulled yet

---

## Protocol-Specific Issues

### REST API: Connection Refused

```
curl: (7) Failed to connect to localhost port 3000: Connection refused
```

1. Verify ChronDB is running: check the process and logs
2. Verify the REST protocol is enabled in `config.edn` (or not disabled via `--disable-rest`)
3. Check if it's bound to the correct interface (default `0.0.0.0` accepts all)
4. Check health: `curl http://localhost:3000/healthz`

### Redis: Authentication Error

ChronDB's Redis protocol does not currently support AUTH commands. If your Redis client requires authentication, configure it to connect without a password, or use a proxy that handles authentication.

### PostgreSQL: Connection Error

```
psql: error: connection to server failed
```

1. Verify the PostgreSQL protocol is enabled and the port is correct
2. Use the configured username and password:

```bash
psql -h localhost -p 5432 -U chrondb -W
# Enter password: chrondb (default)
```

3. Check that `config.edn` has the PostgreSQL server enabled:

```clojure
:servers {:postgresql {:enabled true :port 5432 :username "chrondb" :password "chrondb"}}
```

### PostgreSQL: Unsupported SQL Feature

```
ERROR: Unsupported SQL statement
```

ChronDB implements a subset of SQL. See [Protocols](protocols) for the complete list of supported SQL features. Common limitations:
- No subqueries (yet)
- No window functions (yet)
- No HAVING clause (yet)
- Only INNER JOIN and LEFT JOIN supported

---

## Performance Issues

### Slow Read Operations

If GET operations are slow:

1. **Check document count** — read performance degrades with repository size because of path resolution overhead. This is a known limitation (see issue #109).
2. **Check disk I/O** — Git operations are I/O bound. Use SSDs for the data directory.
3. **Check memory** — insufficient memory forces the JVM to GC frequently. Monitor with `/health`:

```bash
curl -s http://localhost:3000/health | jq '.checks[] | select(.component == "memory")'
```

### Slow Queries

If SQL queries or search operations are slow:

1. **Check the query** — ChronDB currently loads all documents for a table before filtering (see issue #115). Avoid `SELECT *` on large tables when possible.
2. **Use targeted queries** — queries that match indexed fields (via Lucene) are faster than full table scans.
3. **Limit results** — always use `LIMIT` in SQL queries and `limit` parameter in search.
4. **Check index health** — the `chrondb_index_documents` gauge should match your expected document count.

### High Memory Usage

1. Check for large WAL backlogs: `chrondb_wal_pending_entries` should be near 0
2. Check for many concurrent connections: `chrondb_active_connections`
3. Run compaction to optimize Git storage: `java -jar chrondb.jar --command compact`
4. Consider increasing the JVM heap if usage is consistently above 85%

---

## Diagnostic Tools

ChronDB includes built-in diagnostic tools:

### Repository Diagnostics

```bash
# Run diagnostics on the data directory
clojure -M:run diagnose --data-dir data/
```

This checks:
- Git repository integrity
- Lock file presence
- Branch configuration
- Remote sync status

### Data Dump

```bash
# Export all documents for inspection
clojure -M:run dump --data-dir data/ --output dump.json
```

### WAL Status

Check the Write-Ahead Log for pending or failed operations:

```bash
curl -s http://localhost:3000/health | jq '.checks[] | select(.component == "wal")'
```

A healthy WAL should show `"pending-entries": 0`. Values above 100 indicate the WAL is backing up, which may mean storage is slow or experiencing errors.

---

## Getting Help

If your issue is not covered here:

1. Check the [FAQ](faq) for common questions
2. Search [existing issues](https://github.com/avelino/chrondb/issues)
3. Open a new issue with:
   - ChronDB version
   - Operating system and Java version
   - Full error message and stack trace
   - Steps to reproduce
4. Join the [discussions](https://github.com/avelino/chrondb/discussions) for questions

# Indexing and Search

ChronDB uses [Apache Lucene 9.8](https://lucene.apache.org/) for full-text and structured search. Every document saved through any protocol (REST, Redis, SQL, FFI) is automatically indexed. This guide covers how indexing works, available query types, and performance tuning.

## How Indexing Works

### Write Path

When a document is saved:

1. The document is stored in Git (immutable commit)
2. The document is indexed in Lucene with all fields
3. The Lucene NRT (Near-Real-Time) reader is refreshed

Reads see indexed documents immediately after the write returns.

### Field Type Detection

Lucene needs to know field types for storage and querying. ChronDB automatically detects types from document values:

| JSON Value Type | Lucene Field Type | Indexed As |
|----------------|-------------------|------------|
| String | `StringField` + `TextField` | Exact match + full-text |
| Integer/Long | `LongPoint` + `NumericDocValuesField` | Range queries + sorting |
| Float/Double | `DoublePoint` + `NumericDocValuesField` | Range queries + sorting |
| Boolean | `StringField` | Exact match (`"true"` / `"false"`) |
| Nested Object | Flattened with dot notation | `address.city` becomes a field |

Every field is also stored as a `StoredField` so the original document can be retrieved from the index.

### Full-Text Analysis

Text fields are indexed twice:
- **Exact match**: stored as-is for term queries and filters
- **Analyzed**: processed through Lucene's `StandardAnalyzer` for full-text search (tokenized, lowercased, stop words removed)

Full-text search fields use the `_fts` suffix internally (e.g., `content` becomes `content_fts`).

---

## Query Types

ChronDB supports several query types through the AST query system. All query types are available across REST, Redis, and SQL protocols.

### Term Query

Exact match on a field value:

```clojure
;; Clojure AST
(ast/term "status" "active")
```

```sql
-- SQL equivalent
SELECT * FROM user WHERE status = 'active';
```

```bash
# REST search
curl "http://localhost:3000/api/v1/search?q=status:active"
```

### Wildcard Query

Pattern matching with `*` (multiple characters) and `?` (single character):

```clojure
(ast/wildcard "email" "*@example.com")
```

```sql
SELECT * FROM user WHERE email LIKE '%@example.com';
```

### Range Query

Numeric and string range comparisons:

```clojure
;; Numeric range
(ast/range "age" {:gte 18 :lt 65})

;; Open-ended range
(ast/range "salary" {:gte 50000})
```

```sql
SELECT * FROM employee WHERE age >= 18 AND age < 65;
SELECT * FROM employee WHERE salary >= 50000;
```

### Full-Text Search (FTS)

Search analyzed text fields using natural language:

```clojure
(ast/fts "content" "distributed database chronological")
```

```sql
SELECT * FROM articles WHERE FTS_MATCH(content, 'distributed database chronological');
```

FTS queries are processed through Lucene's QueryParser, which supports:
- Boolean operators: `AND`, `OR`, `NOT`
- Phrase search: `"exact phrase"`
- Field targeting: `title:database`
- Grouping: `(database OR store) AND distributed`

### Prefix Query

Match documents where a field starts with a given string:

```clojure
(ast/prefix "name" "Joh")
```

### Exists / Missing

Check for field presence or absence:

```clojure
(ast/exists "email")      ;; documents that have an email field
(ast/missing "deleted_at") ;; documents without a deleted_at field
```

```sql
SELECT * FROM user WHERE email IS NOT NULL;
SELECT * FROM user WHERE deleted_at IS NULL;
```

### Boolean Combinations

Combine queries with boolean logic:

```clojure
(ast/bool
  {:must [(ast/term "status" "active")
          (ast/range "age" {:gte 18})]
   :should [(ast/fts "bio" "engineer")]
   :must-not [(ast/term "role" "banned")]})
```

| Clause | Behavior |
|--------|----------|
| `must` | All conditions must match (AND) |
| `should` | At least one should match (OR) |
| `must-not` | None of these must match (NOT) |
| `filter` | Like `must` but does not affect relevance scoring |

---

## AST Query System

For a comprehensive reference on building structured queries, including pagination, sorting, and cursor-based navigation, see [AST Queries](ast-queries).

Quick example via REST:

```bash
# Structured query with sorting and pagination
curl -G http://localhost:3000/api/v1/search \
  --data-urlencode 'query={"clauses":[{"type":"term","field":"status","value":"active"}]}' \
  --data-urlencode 'sort=name:asc' \
  --data-urlencode 'limit=20'
```

---

## Sorting

Search results can be sorted by any indexed field:

```clojure
(ast/query [(ast/fts "content" "database")]
           {:sort [{:field "name" :direction :asc}
                   {:field "created_at" :direction :desc}]
            :limit 10})
```

```bash
# REST API
curl "http://localhost:3000/api/v1/search?q=database&sort=name:asc,created_at:desc"
```

Sort type is detected automatically from the field name:
- Fields containing `date`, `time`, `created`, `updated` → long (timestamp)
- Fields containing `age`, `count`, `size`, `price`, `score` → numeric
- Default → string (lexicographic)

---

## Background Reindexing

ChronDB maintains index consistency through automatic background reindexing:

### On Startup

When ChronDB starts, a background process walks all Git commits to ensure every document is present in the Lucene index. This handles:
- First startup after restoring from a Git backup
- Recovery after a crash that left the index incomplete
- Index rebuilds after deleting the index directory

### Periodic Maintenance

A scheduled task runs reindexing verification every hour (default). This is safe for production — it processes incremental batches and does not block reads or writes.

### Monitoring Reindexing

```bash
# Check reindexing status in logs
grep "reindex" chrondb.log

# Check index document count via metrics
curl -s http://localhost:3000/metrics | grep chrondb_index_documents
```

### Forcing a Full Reindex

To rebuild the index from scratch:

```bash
# 1. Stop ChronDB
# 2. Delete the index directory
rm -rf data/index/
# 3. Start ChronDB — reindexing happens automatically
```

---

## Performance Tuning

### NRT Reader Configuration

The Near-Real-Time reader pool controls how quickly new writes become visible in search results. Default settings work well for most workloads:

- **Batch size**: 100 documents before committing to the index
- **Commit interval**: Periodic flush to ensure durability
- **RAM buffer**: In-memory buffer before flushing segments to disk

### Query Performance Tips

1. **Use specific field queries over FTS** — `term("status", "active")` is faster than `fts("content", "status:active")`
2. **Use `filter` instead of `must`** — filter clauses skip scoring, which is faster when relevance ordering is not needed
3. **Limit results** — always set a `limit` to avoid loading all matches
4. **Prefer cursor-based pagination** — for deep pagination (offset > 1000), use the `after` cursor instead of `offset`
5. **Avoid leading wildcards** — `*@example.com` requires scanning all terms; `user@*` only scans terms starting with `user@`

### Index Storage

The Lucene index is stored alongside the Git data directory. On production systems:

- Use SSDs for the data directory
- Ensure the filesystem supports `mmap` (ext4, xfs, APFS)
- Monitor index size: it typically grows to 30-50% of the raw document data size

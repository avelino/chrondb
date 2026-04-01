# ChronDB Protocols

ChronDB supports multiple access protocols, allowing integration with different clients and frameworks. All protocols share the same storage and index backend, so data written via one protocol is immediately available through any other.

## Health and Monitoring

These endpoints are available on the REST HTTP port (default 3000) and are protocol-independent.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Full health check (storage, index, WAL, disk, memory) |
| GET | `/healthz` | Kubernetes liveness probe (is the process alive?) |
| GET | `/readyz` | Kubernetes readiness probe (is the service accepting traffic?) |
| GET | `/startupz` | Kubernetes startup probe (has initialization completed?) |
| GET | `/metrics` | Prometheus-format metrics |

### Health Check Response

```json
{
  "status": "healthy",
  "timestamp": "2025-01-15T10:30:00Z",
  "total-latency-ms": 12,
  "checks": [
    {"component": "storage", "status": "healthy", "latency_ms": 3},
    {"component": "index", "status": "healthy", "latency_ms": 2},
    {"component": "wal", "status": "healthy", "details": {"pending-entries": 0}},
    {"component": "disk", "status": "healthy", "details": {"free-gb": 45.2, "used-percent": 55.0}},
    {"component": "memory", "status": "healthy", "details": {"used-mb": 256, "max-mb": 1024}}
  ]
}
```

Status values: `healthy`, `degraded` (returns HTTP 200), `unhealthy` (returns HTTP 503).

### Prometheus Metrics

Available at `GET /metrics` in Prometheus text format:

| Metric | Type | Description |
|--------|------|-------------|
| `chrondb_write_latency_seconds` | Histogram | Write operation latency |
| `chrondb_read_latency_seconds` | Histogram | Read operation latency |
| `chrondb_query_latency_seconds` | Histogram | Query execution latency |
| `chrondb_documents_saved_total` | Counter | Total documents saved |
| `chrondb_documents_deleted_total` | Counter | Total documents deleted |
| `chrondb_documents_read_total` | Counter | Total documents read |
| `chrondb_queries_executed_total` | Counter | Total queries executed |
| `chrondb_active_connections` | Gauge | Current active connections |
| `chrondb_index_documents` | Gauge | Documents in the index |
| `chrondb_wal_pending_entries` | Gauge | Pending WAL entries |
| `chrondb_occ_conflicts_total` | Counter | Optimistic concurrency conflicts |
| `chrondb_occ_retries_total` | Counter | OCC retry attempts |

---

## REST API

ChronDB provides a complete REST API for database operations.

### Configuration

```clojure
:servers {
  :rest {
    :enabled true
    :host "0.0.0.0"
    :port 3000
  }
}
```

### Transaction Metadata Headers

All write endpoints accept optional headers to enrich the Git commit metadata:

| Header | Description |
|--------|-------------|
| `X-ChronDB-Origin` | Override the origin label (defaults to `rest`) |
| `X-ChronDB-User` | Associate the commit with a user id or service account |
| `X-ChronDB-Flags` | Comma-separated semantic flags (e.g., `bulk-load,migration`) |
| `X-Request-Id` | Propagate request correlation id into commit metadata |
| `X-Correlation-Id` | Propagate correlation id for distributed tracing |

All query parameters support `?branch=name` to target a specific branch (defaults to `main`).

### Document Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/save` | Save document (id in body) |
| POST | `/api/v1/put` | Save document (id in params or body) |
| GET | `/api/v1/get/:id` | Get document by id |
| GET | `/api/v1/get/:id/history` | Get document version history |
| DELETE | `/api/v1/delete/:id` | Delete document |
| GET | `/api/v1/documents` | Export all documents |
| POST | `/api/v1/documents/import` | Bulk import documents |

### Search Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/search?q=term` | Full-text search |
| GET | `/api/v1/search?query={...}` | Structured AST query (JSON) |

Search parameters:

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Full-text search query string |
| `query` | JSON | Structured query (see [AST Queries](ast-queries)) |
| `branch` | string | Target branch (default: `main`) |
| `limit` | integer | Max results to return |
| `offset` | integer | Number of results to skip |
| `sort` | string | Sort specification: `field:asc,field2:desc` |
| `after` | string | Base64-encoded cursor for cursor-based pagination |

### History Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/history/:id` | Document version history |
| GET | `/api/v1/history?id=key` | Document history (id as query param) |

### Schema Validation Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/schemas/validation` | List all validation schemas |
| GET | `/api/v1/schemas/validation/:namespace` | Get schema for namespace |
| PUT | `/api/v1/schemas/validation/:namespace` | Save/update schema |
| DELETE | `/api/v1/schemas/validation/:namespace` | Delete schema |
| GET | `/api/v1/schemas/validation/:namespace/history` | Schema version history |
| POST | `/api/v1/schemas/validation/:namespace/validate` | Validate a document against schema |

### Backup and Restore Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/backup` | Create backup (JSON body with `output` and `format`) |
| POST | `/api/v1/restore` | Restore from backup (multipart upload) |
| POST | `/api/v1/export` | Export snapshot (JSON body with `output` and `refs`) |
| GET | `/api/v1/export` | Export all documents |

### System Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/info` | Database info and statistics |
| POST | `/api/v1/init` | Initialize database |
| POST | `/api/v1/verify` | Verify data integrity |

### Example with curl

```bash
# Save a document
curl -X POST http://localhost:3000/api/v1/save \
  -H "Content-Type: application/json" \
  -H "X-ChronDB-User: alice@example.com" \
  -d '{"id":"user:1","name":"John Doe","email":"john@example.com"}'

# Get a document
curl http://localhost:3000/api/v1/get/user:1

# Get document on a specific branch
curl http://localhost:3000/api/v1/get/user:1?branch=staging

# Search documents
curl "http://localhost:3000/api/v1/search?q=name:John&limit=10&sort=name:asc"

# Get document history
curl http://localhost:3000/api/v1/get/user:1/history

# Delete a document
curl -X DELETE http://localhost:3000/api/v1/delete/user:1

# Create a backup
curl -X POST http://localhost:3000/api/v1/backup \
  -H "Content-Type: application/json" \
  -d '{"output":"/tmp/backup.tar.gz","format":"tar.gz"}'

# Health check
curl http://localhost:3000/health
```

---

## Redis Protocol

ChronDB implements a subset of the Redis protocol (RESP2), allowing standard Redis clients to connect directly.

### Configuration

```clojure
:servers {
  :redis {
    :enabled true
    :host "0.0.0.0"
    :port 6379
  }
}
```

### Supported Commands

#### String Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `GET` | `GET key` | Retrieve document by key |
| `SET` | `SET key value` | Store document (JSON value) |
| `SETEX` | `SETEX key seconds value` | Store with TTL |
| `SETNX` | `SETNX key value` | Store only if key does not exist |
| `DEL` | `DEL key` | Delete document |
| `EXISTS` | `EXISTS key` | Check if key exists (returns 1 or 0) |

#### Hash Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `HSET` | `HSET key field value` | Set a field in a hash document |
| `HGET` | `HGET key field` | Get a specific field from a hash |
| `HMSET` | `HMSET key field1 val1 field2 val2 ...` | Set multiple fields |
| `HMGET` | `HMGET key field1 field2 ...` | Get multiple fields |
| `HGETALL` | `HGETALL key` | Get all fields and values |

#### List Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `LPUSH` | `LPUSH key value [value ...]` | Push elements to the head |
| `RPUSH` | `RPUSH key value [value ...]` | Push elements to the tail |
| `LPOP` | `LPOP key` | Remove and return the head element |
| `RPOP` | `RPOP key` | Remove and return the tail element |
| `LRANGE` | `LRANGE key start stop` | Get a range of elements |
| `LLEN` | `LLEN key` | Get the list length |

#### Set Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `SADD` | `SADD key member [member ...]` | Add members to a set |
| `SMEMBERS` | `SMEMBERS key` | Get all members of a set |
| `SISMEMBER` | `SISMEMBER key member` | Check if member exists in set |
| `SREM` | `SREM key member` | Remove a member from a set |

#### Sorted Set Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `ZADD` | `ZADD key score member` | Add member with score |
| `ZRANGE` | `ZRANGE key start stop [WITHSCORES]` | Get members by rank range |
| `ZRANK` | `ZRANK key member` | Get the rank of a member |
| `ZSCORE` | `ZSCORE key member` | Get the score of a member |
| `ZREM` | `ZREM key member` | Remove a member |

#### Search Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `SEARCH` | `SEARCH query [LIMIT offset count]` | Search documents using Lucene syntax |
| `FT.SEARCH` | `FT.SEARCH index query [LIMIT offset count]` | RediSearch-compatible search |

#### Schema Validation Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `SCHEMA.SET` | `SCHEMA.SET namespace schema-json` | Define a validation schema |
| `SCHEMA.GET` | `SCHEMA.GET namespace` | Retrieve a schema |
| `SCHEMA.DEL` | `SCHEMA.DEL namespace` | Delete a schema |
| `SCHEMA.LIST` | `SCHEMA.LIST` | List all schemas |
| `SCHEMA.VALIDATE` | `SCHEMA.VALIDATE namespace document-json` | Validate a document against a schema |

#### Server Commands

| Command | Syntax | Description |
|---------|--------|-------------|
| `PING` | `PING [message]` | Test connectivity (returns PONG or echoes message) |
| `ECHO` | `ECHO message` | Echo the given string |
| `COMMAND` | `COMMAND` | List available commands |
| `INFO` | `INFO` | Server information |

### Example with redis-cli

```bash
# Connect to ChronDB
redis-cli -h localhost -p 6379

# String operations
SET user:1 '{"name":"John Doe","email":"john@example.com"}'
GET user:1

# Hash operations
HSET user:2 name "Jane Doe"
HSET user:2 email "jane@example.com"
HGETALL user:2

# List operations
LPUSH notifications:user:1 '{"type":"welcome","text":"Hello!"}'
LRANGE notifications:user:1 0 -1

# Set operations
SADD tags:user:1 "admin" "editor"
SMEMBERS tags:user:1

# Sorted set operations
ZADD leaderboard 100 "player:1"
ZADD leaderboard 200 "player:2"
ZRANGE leaderboard 0 -1 WITHSCORES

# Search
SEARCH "name:John"
FT.SEARCH idx "name:John" LIMIT 0 10

# Schema validation
SCHEMA.SET user '{"type":"object","required":["name","email"]}'
SCHEMA.VALIDATE user '{"name":"John","email":"john@example.com"}'
```

---

## PostgreSQL Protocol

ChronDB implements a subset of the PostgreSQL wire protocol, allowing connection with standard SQL clients (`psql`, JDBC drivers, etc.).

### Configuration

```clojure
:servers {
  :postgresql {
    :enabled true
    :host "0.0.0.0"
    :port 5432
    :username "chrondb"
    :password "chrondb"
  }
}
```

### Data Model

Documents are mapped to virtual tables based on their key prefix:

- The prefix before `:` becomes the table name
- Document fields become columns
- The `id` column contains the key suffix

Example: key `user:1` with `{"name":"John","email":"john@example.com"}` is queryable as:

```sql
SELECT * FROM user WHERE id = '1';
```

### SQL Features

#### DML (Data Manipulation)

| Statement | Description |
|-----------|-------------|
| `SELECT` | Query documents with full clause support |
| `INSERT` | Create documents |
| `UPDATE` | Modify documents |
| `DELETE` | Remove documents |

#### SELECT Clause Support

| Clause | Example | Description |
|--------|---------|-------------|
| `WHERE` | `WHERE age > 18 AND status = 'active'` | Filter results |
| `GROUP BY` | `GROUP BY department` | Group results |
| `ORDER BY` | `ORDER BY name ASC, age DESC` | Sort results |
| `LIMIT` | `LIMIT 10` | Limit result count |
| `INNER JOIN` | `... INNER JOIN orders ON users.id = orders.user_id` | Inner join |
| `LEFT JOIN` | `... LEFT JOIN orders ON users.id = orders.user_id` | Left outer join |

#### WHERE Operators

| Operator | Example |
|----------|---------|
| `=`, `!=`, `<>` | `WHERE status = 'active'` |
| `>`, `<`, `>=`, `<=` | `WHERE age >= 18` |
| `LIKE` | `WHERE name LIKE 'John%'` |
| `BETWEEN` | `WHERE age BETWEEN 18 AND 65` |
| `IS NULL`, `IS NOT NULL` | `WHERE email IS NOT NULL` |
| `IN`, `NOT IN` | `WHERE status IN ('active', 'pending')` |

#### Aggregate Functions

| Function | Description |
|----------|-------------|
| `COUNT(*)` | Count rows |
| `SUM(column)` | Sum values |
| `AVG(column)` | Average value |
| `MIN(column)` | Minimum value |
| `MAX(column)` | Maximum value |

#### Full-Text Search in SQL

```sql
-- Using FTS_MATCH function
SELECT * FROM articles WHERE FTS_MATCH(content, 'database chronological');

-- Using PostgreSQL-style tsquery
SELECT * FROM articles WHERE content @@ to_tsquery('database & chronological');
```

#### DDL and Metadata

| Statement | Description |
|-----------|-------------|
| `CREATE TABLE name (columns...)` | Create table with column definitions |
| `DROP TABLE [IF EXISTS] name` | Drop a table |
| `SHOW TABLES` | List all tables |
| `SHOW SCHEMAS` / `SHOW DATABASES` | List schemas |
| `DESCRIBE table` / `SHOW COLUMNS FROM table` | Show table structure |

#### Validation Schema Management

```sql
CREATE VALIDATION SCHEMA FOR namespace AS '{"type":"object","required":["name"]}';
DROP VALIDATION SCHEMA FOR namespace;
SHOW VALIDATION SCHEMAS;
```

### ChronDB Temporal Functions

These functions expose ChronDB's time-travel capabilities through SQL:

#### `chrondb_history(table, id)`

Returns the complete modification history of a document.

| Column | Description |
|--------|-------------|
| `commit_id` | Git commit hash identifying the version |
| `timestamp` | When the change was made |
| `committer` | Who made the change |
| `data` | Document content at that version |

```sql
SELECT * FROM chrondb_history('user', '1');
SELECT commit_id, timestamp FROM chrondb_history('user', '1');
```

#### `chrondb_at(table, id, commit_hash)`

Returns the document exactly as it was at a specific commit.

```sql
SELECT * FROM chrondb_at('user', '1', 'abc123def456');
```

#### `chrondb_diff(table, id, commit1, commit2)`

Compares two versions and returns the differences.

| Column | Description |
|--------|-------------|
| `id` | Document id |
| `commit1` | First commit hash |
| `commit2` | Second commit hash |
| `added` | Fields added between versions (JSON) |
| `removed` | Fields removed between versions (JSON) |
| `changed` | Fields modified with old and new values (JSON) |

```sql
SELECT * FROM chrondb_diff('user', '1', 'abc123', 'def456');
SELECT changed FROM chrondb_diff('user', '1', 'abc123', 'def456');
```

#### Branch Management Functions

| Function | Description |
|----------|-------------|
| `chrondb_branch_list()` | List all branches |
| `chrondb_branch_create('name')` | Create a new branch |
| `chrondb_branch_checkout('name')` | Switch to a branch |
| `chrondb_branch_merge('source', 'target')` | Merge branches |

### Example with psql

```bash
# Connect to ChronDB
psql -h localhost -p 5432 -U chrondb

# Create a document
INSERT INTO user (id, name, email) VALUES ('1', 'John Doe', 'john@example.com');

# Query documents
SELECT * FROM user WHERE id = '1';
SELECT name, email FROM user WHERE name LIKE 'John%';

# Aggregation
SELECT department, COUNT(*), AVG(salary) FROM employee GROUP BY department ORDER BY COUNT(*) DESC;

# Join tables
SELECT u.name, o.total
FROM user u
INNER JOIN orders o ON u.id = o.user_id
WHERE o.total > 100;

# Update a document
UPDATE user SET email = 'john.doe@example.com' WHERE id = '1';

# Delete a document
DELETE FROM user WHERE id = '1';

# Time-travel queries
SELECT * FROM chrondb_history('user', '1');
SELECT * FROM chrondb_at('user', '1', 'abc123def456');
SELECT * FROM chrondb_diff('user', '1', 'abc123', 'def456');

# Branch management
SELECT * FROM chrondb_branch_list();
SELECT * FROM chrondb_branch_create('staging');

# Full-text search
SELECT * FROM articles WHERE FTS_MATCH(content, 'database distributed');

# Show metadata
SHOW TABLES;
DESCRIBE user;
```

---

## Protocol Comparison

| Feature | REST | Redis | PostgreSQL |
|---------|------|-------|------------|
| Document CRUD | Full | Full | Full (via SQL) |
| Search / Queries | AST + FTS | Lucene syntax | SQL WHERE + FTS |
| Version History | `/get/:id/history` | Not available | `chrondb_history()` |
| Time Travel | Not available | Not available | `chrondb_at()`, `chrondb_diff()` |
| Branch Support | `?branch=name` | Not available | Branch functions |
| Schema Validation | REST endpoints | `SCHEMA.*` commands | `CREATE VALIDATION SCHEMA` |
| Backup / Restore | REST endpoints | Not available | Not available |
| Aggregation | Not available | Not available | `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` |
| JOINs | Not available | Not available | `INNER JOIN`, `LEFT JOIN` |
| Monitoring | `/health`, `/metrics` | `INFO` | Not available |
| Transaction Metadata | HTTP headers | Not available | Not available |

# ChronDB Node.js Binding

Node.js client for ChronDB, built with [NAPI-RS](https://napi.rs/) from the Rust SDK.

## Requirements

- Node.js 16+

## Installation

```bash
npm install chrondb
```

## Quick Start

```javascript
const { ChronDB } = require('chrondb')

const db = new ChronDB('/tmp/data', '/tmp/index')

// Save a document
db.put('user:1', { name: 'Alice', age: 30 })

// Retrieve it
const doc = db.get('user:1')
console.log(doc) // { name: 'Alice', age: 30 }
```

### ES Modules

```javascript
import { ChronDB } from 'chrondb'

const db = new ChronDB('/tmp/data', '/tmp/index')
```

## API Reference

### `new ChronDB(dataPath, indexPath, options?)`

Opens a database connection.

| Parameter | Type | Description |
|-----------|------|-------------|
| `dataPath` | `string` | Path for the Git repository (data storage) |
| `indexPath` | `string` | Path for the Lucene index |
| `options.idleTimeout` | `number` | Seconds of inactivity before suspending the GraalVM isolate |

**Throws:** `Error` if the database cannot be opened.

---

### `put(id, doc, branch?) -> Object`

Saves a document.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `string` | Document ID (e.g., `"user:1"`) |
| `doc` | `Object` | Document data |
| `branch` | `string \| null` | Branch name |

---

### `get(id, branch?) -> Object`

Retrieves a document by ID.

**Throws:** `Error` if not found.

---

### `delete(id, branch?) -> void`

Deletes a document by ID.

---

### `listByPrefix(prefix, branch?) -> Object[]`

Lists documents whose IDs start with the given prefix.

---

### `listByTable(table, branch?) -> Object[]`

Lists all documents in a table.

---

### `history(id, branch?) -> Object[]`

Returns the change history of a document.

---

### `query(query, branch?) -> Object`

Executes a query against the Lucene index.

## TypeScript

Full TypeScript definitions are included:

```typescript
import { ChronDB, ChronDBOptions } from 'chrondb'

const db = new ChronDB('/tmp/data', '/tmp/index', { idleTimeout: 120 })

const doc: Record<string, unknown> = db.get('user:1')
```

## Error Handling

```javascript
const { ChronDB } = require('chrondb')

try {
  const db = new ChronDB('/tmp/data', '/tmp/index')
  const doc = db.get('user:999')
} catch (err) {
  if (err.message.includes('not found')) {
    console.log('Document does not exist')
  } else {
    console.error('Database error:', err.message)
  }
}
```

## Examples

### Full CRUD

```javascript
const { ChronDB } = require('chrondb')

const db = new ChronDB('/tmp/data', '/tmp/index')

// Create
db.put('user:1', { name: 'Alice', email: 'alice@example.com' })
db.put('user:2', { name: 'Bob', email: 'bob@example.com' })

// Read
const alice = db.get('user:1')

// Update
db.put('user:1', { ...alice, age: 30 })

// Delete
db.delete('user:2')

// List
const users = db.listByTable('user')
```

### Idle Timeout (long-running services)

```javascript
// Isolate suspends after 2 minutes of inactivity
const db = new ChronDB('/tmp/data', '/tmp/index', { idleTimeout: 120 })

db.put('audit:1', { action: 'login' })

// After 120s without operations, the GraalVM isolate is torn down.
// The next call transparently reopens it.
```

### Query

```javascript
db.put('product:1', { name: 'Laptop', price: 999 })

const results = db.query({
  type: 'term',
  field: 'name',
  value: 'Laptop'
})

console.log(results.total) // 1
```

### History (Time Travel)

```javascript
db.put('config:app', { version: '1.0' })
db.put('config:app', { version: '2.0' })

const entries = db.history('config:app')
entries.forEach(entry => console.log(entry))
```

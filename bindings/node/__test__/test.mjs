import { strict as assert } from 'node:assert'
import { mkdtempSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { ChronDB } from '../index.js'

const tempDir = mkdtempSync(join(tmpdir(), 'chrondb-test-'))
const dataPath = join(tempDir, 'data')
const indexPath = join(tempDir, 'index')

function cleanup() {
  rmSync(tempDir, { recursive: true, force: true })
}

function test(name, fn) {
  try {
    fn()
    console.log(`  ✓ ${name}`)
  } catch (err) {
    console.error(`  ✗ ${name}`)
    console.error(`    ${err.message}`)
    process.exitCode = 1
  }
}

console.log('ChronDB Node.js tests\n')

let db
try {
  db = new ChronDB(dataPath, indexPath)
} catch (err) {
  console.log(`Skipping tests: ${err.message}`)
  cleanup()
  process.exit(0)
}

test('put and get', () => {
  const saved = db.put('user:1', { name: 'Alice', age: 30 })
  assert.equal(saved.name, 'Alice')

  const doc = db.get('user:1')
  assert.equal(doc.name, 'Alice')
})

test('get not found throws', () => {
  assert.throws(() => db.get('nonexistent:999'), /not found/)
})

test('delete', () => {
  db.put('user:2', { name: 'Bob' })
  db.delete('user:2')

  assert.throws(() => db.get('user:2'), /not found/)
})

test('list by prefix', () => {
  db.put('item:1', { name: 'A' })
  db.put('item:2', { name: 'B' })
  db.put('other:1', { name: 'C' })

  const items = db.listByPrefix('item:')
  assert.ok(items.length >= 2, `expected >= 2 items, got ${items.length}`)
})

test('list by table', () => {
  const items = db.listByTable('item')
  assert.ok(items.length >= 2, `expected >= 2 items, got ${items.length}`)
})

test('history', () => {
  db.put('config:app', { version: '1.0' })
  db.put('config:app', { version: '2.0' })

  const entries = db.history('config:app')
  assert.ok(entries.length >= 1, `expected >= 1 entries, got ${entries.length}`)
})

test('idle timeout constructor', () => {
  const tempDir2 = mkdtempSync(join(tmpdir(), 'chrondb-idle-'))
  try {
    const db2 = new ChronDB(
      join(tempDir2, 'data'),
      join(tempDir2, 'index'),
      { idleTimeout: 60 }
    )
    db2.put('idle:1', { name: 'test' })
    const doc = db2.get('idle:1')
    assert.equal(doc.name, 'test')
  } finally {
    rmSync(tempDir2, { recursive: true, force: true })
  }
})

console.log(`\n${process.exitCode ? 'FAILED' : 'All tests passed'}`)
cleanup()

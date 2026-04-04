# ChronDB Dart/Flutter Binding

Dart client for ChronDB, using `dart:ffi` to call the native `libchrondb` shared library directly.

## Requirements

- Dart SDK 3.0+
- `libchrondb.dylib` (macOS) or `libchrondb.so` (Linux)

## Installation

Download the platform-specific tarball from the [latest GitHub release](https://github.com/avelino/chrondb/releases):

- `chrondb-dart-{version}-macos-aarch64.tar.gz` (macOS Apple Silicon)
- `chrondb-dart-{version}-linux-x86_64.tar.gz` (Linux x86_64)

Extract and add as a path dependency in your `pubspec.yaml`:

```yaml
dependencies:
  chrondb:
    path: path/to/chrondb-dart
```

## Quick Start

```dart
import 'package:chrondb/chrondb.dart';

// Single path (preferred)
final db = ChronDB.openPath('./mydb');

// Save a document
db.put('user:1', {'name': 'Alice', 'age': 30});

// Retrieve it
final doc = db.get('user:1');
print(doc); // {name: Alice, age: 30}

// Clean up
db.close();
```

## API Reference

### `ChronDB.openPath(String dbPath)` → `ChronDB`

Opens a database using a single directory path. Data and index are stored in subdirectories automatically.

### `ChronDB.open(String dataPath, String indexPath)` *(deprecated)*

> **Deprecated.** Use `openPath` instead.

### Operations

| Method | Description |
|--------|-------------|
| `put(id, doc, {branch})` | Save a document (Map in, Map out) |
| `get(id, {branch})` | Get a document by ID |
| `delete(id, {branch})` | Delete a document |
| `listByPrefix(prefix, {branch})` | List by ID prefix |
| `listByTable(table, {branch})` | List by table |
| `history(id, {branch})` | Get change history |
| `query(queryMap, {branch})` | Execute a Lucene query |
| `execute(sql, {branch})` | Execute a SQL query |
| `close()` | Close the connection |

## Error Handling

```dart
import 'package:chrondb/chrondb.dart';

try {
  final db = ChronDB.openPath('./mydb');
  final doc = db.get('user:999');
} on DocumentNotFoundException {
  print('Document does not exist');
} on ChronDBException catch (e) {
  print('Database error: $e');
}
```

## Flutter Integration

For Flutter apps, place `libchrondb.dylib`/`.so` in your app's native library directory:

- **iOS**: Add to Xcode project as a framework
- **Android**: Place in `android/app/src/main/jniLibs/{abi}/`
- **macOS**: Add to `macos/Runner/` and reference in Xcode
- **Linux**: Install to system library path or bundle with app

Set `CHRONDB_LIB_DIR` environment variable or place the library in `~/.chrondb/lib/`.

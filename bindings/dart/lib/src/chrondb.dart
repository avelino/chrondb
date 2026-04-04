import 'dart:convert';
import 'dart:ffi';

import 'package:ffi/ffi.dart';

import 'bindings.dart';
import 'errors.dart';

/// A connection to a ChronDB database instance.
///
/// ```dart
/// final db = ChronDB.openPath('/tmp/mydb');
/// db.put('user:1', {'name': 'Alice', 'age': 30});
/// final doc = db.get('user:1');
/// db.close();
/// ```
class ChronDB {
  final ChronDBBindings _bindings;
  final Pointer<Void> _thread;
  final int _handle;
  bool _closed = false;

  ChronDB._(this._bindings, this._thread, this._handle);

  /// Open a database using a single directory path (preferred).
  static ChronDB openPath(String dbPath) {
    final bindings = ChronDBBindings.load();
    final thread = _createIsolate(bindings);
    final pathPtr = dbPath.toNativeUtf8();
    try {
      final handle = bindings.chrondbOpenPath(thread, pathPtr);
      if (handle < 0) {
        throw ChronDBException(_getLastError(bindings, thread) ?? 'Failed to open database');
      }
      return ChronDB._(bindings, thread, handle);
    } finally {
      malloc.free(pathPtr);
    }
  }

  /// Open a database with separate data and index paths.
  ///
  /// **Deprecated.** Use [openPath] instead.
  @Deprecated('Use openPath() instead — the index is managed automatically.')
  static ChronDB open(String dataPath, String indexPath) {
    final bindings = ChronDBBindings.load();
    final thread = _createIsolate(bindings);
    final dataPtr = dataPath.toNativeUtf8();
    final indexPtr = indexPath.toNativeUtf8();
    try {
      final handle = bindings.chrondbOpen(thread, dataPtr, indexPtr);
      if (handle < 0) {
        throw ChronDBException(_getLastError(bindings, thread) ?? 'Failed to open database');
      }
      return ChronDB._(bindings, thread, handle);
    } finally {
      malloc.free(dataPtr);
      malloc.free(indexPtr);
    }
  }

  // -- CRUD --

  /// Save a document.
  ///
  /// Returns the saved document as a Map.
  Map<String, dynamic> put(String id, Map<String, dynamic> doc, {String? branch}) {
    _ensureOpen();
    final idPtr = id.toNativeUtf8();
    final docPtr = jsonEncode(doc).toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbPut(_thread, _handle, idPtr, docPtr, branchPtr);
      return _parseObject(result);
    } finally {
      malloc.free(idPtr);
      malloc.free(docPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  /// Get a document by ID.
  ///
  /// Throws [DocumentNotFoundException] if not found.
  Map<String, dynamic> get(String id, {String? branch}) {
    _ensureOpen();
    final idPtr = id.toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbGet(_thread, _handle, idPtr, branchPtr);
      return _parseObject(result);
    } finally {
      malloc.free(idPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  /// Delete a document by ID.
  ///
  /// Throws [DocumentNotFoundException] if not found.
  void delete(String id, {String? branch}) {
    _ensureOpen();
    final idPtr = id.toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbDelete(_thread, _handle, idPtr, branchPtr);
      if (result == 1) {
        throw DocumentNotFoundException();
      } else if (result < 0) {
        throw ChronDBException(_getLastError(_bindings, _thread) ?? 'Delete failed');
      }
    } finally {
      malloc.free(idPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  // -- Query --

  /// List documents by ID prefix.
  dynamic listByPrefix(String prefix, {String? branch}) {
    _ensureOpen();
    final prefixPtr = prefix.toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbListByPrefix(_thread, _handle, prefixPtr, branchPtr);
      return _parseAny(result);
    } finally {
      malloc.free(prefixPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  /// List documents by table name.
  dynamic listByTable(String table, {String? branch}) {
    _ensureOpen();
    final tablePtr = table.toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbListByTable(_thread, _handle, tablePtr, branchPtr);
      return _parseAny(result);
    } finally {
      malloc.free(tablePtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  /// Get the change history of a document.
  dynamic history(String id, {String? branch}) {
    _ensureOpen();
    final idPtr = id.toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbHistory(_thread, _handle, idPtr, branchPtr);
      return _parseAny(result);
    } finally {
      malloc.free(idPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  /// Execute a query against the Lucene index.
  dynamic query(Map<String, dynamic> queryMap, {String? branch}) {
    _ensureOpen();
    final queryPtr = jsonEncode(queryMap).toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbQuery(_thread, _handle, queryPtr, branchPtr);
      return _parseAny(result);
    } finally {
      malloc.free(queryPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  /// Execute a SQL query against the database.
  dynamic execute(String sql, {String? branch}) {
    _ensureOpen();
    final sqlPtr = sql.toNativeUtf8();
    final branchPtr = branch?.toNativeUtf8() ?? nullptr;
    try {
      final result = _bindings.chrondbExecuteSql(_thread, _handle, sqlPtr, branchPtr);
      return _parseAny(result);
    } finally {
      malloc.free(sqlPtr);
      if (branchPtr != nullptr) malloc.free(branchPtr);
    }
  }

  // -- Remote --

  /// Configure a remote URL for push/pull.
  dynamic setupRemote(String remoteUrl) {
    _ensureOpen();
    final urlPtr = remoteUrl.toNativeUtf8();
    try {
      final result = _bindings.chrondbSetupRemote(_thread, _handle, urlPtr, nullptr);
      return _parseAny(result);
    } finally {
      malloc.free(urlPtr);
    }
  }

  /// Push changes to the configured remote.
  dynamic push() {
    _ensureOpen();
    final result = _bindings.chrondbPush(_thread, _handle);
    return _parseAny(result);
  }

  /// Pull changes from the configured remote.
  dynamic pull() {
    _ensureOpen();
    final result = _bindings.chrondbPull(_thread, _handle);
    return _parseAny(result);
  }

  /// Fetch changes without merging.
  dynamic fetch() {
    _ensureOpen();
    final result = _bindings.chrondbFetch(_thread, _handle);
    return _parseAny(result);
  }

  /// Get the remote synchronization status.
  dynamic remoteStatus() {
    _ensureOpen();
    final result = _bindings.chrondbRemoteStatus(_thread, _handle);
    return _parseAny(result);
  }

  // -- Export & Backup --

  /// Export the repository tree to a filesystem directory.
  dynamic exportToDirectory(String targetDir,
      {String? branch, String? prefix, String format = 'json',
       bool decodePaths = true, bool overwrite = false}) {
    _ensureOpen();
    final dirPtr = targetDir.toNativeUtf8();
    final opts = <String, dynamic>{};
    if (branch != null) opts['branch'] = branch;
    if (prefix != null) opts['prefix'] = prefix;
    if (format != 'json') opts['format'] = format;
    if (!decodePaths) opts['decode_paths'] = false;
    if (overwrite) opts['overwrite'] = true;
    final optsPtr = opts.isEmpty ? nullptr : jsonEncode(opts).toNativeUtf8();
    try {
      final result = _bindings.chrondbExport(_thread, _handle, dirPtr, optsPtr);
      return _parseAny(result);
    } finally {
      malloc.free(dirPtr);
      if (optsPtr != nullptr) malloc.free(optsPtr);
    }
  }

  /// Create a full backup.
  dynamic createBackup(String outputPath,
      {String format = 'tar.gz', bool verify = true}) {
    _ensureOpen();
    final pathPtr = outputPath.toNativeUtf8();
    final opts = <String, dynamic>{};
    if (format != 'tar.gz') opts['format'] = format;
    if (!verify) opts['verify'] = false;
    final optsPtr = opts.isEmpty ? nullptr : jsonEncode(opts).toNativeUtf8();
    try {
      final result = _bindings.chrondbCreateBackup(_thread, _handle, pathPtr, optsPtr);
      return _parseAny(result);
    } finally {
      malloc.free(pathPtr);
      if (optsPtr != nullptr) malloc.free(optsPtr);
    }
  }

  /// Restore from a backup file.
  dynamic restoreBackup(String inputPath,
      {String format = 'tar.gz', bool verify = true}) {
    _ensureOpen();
    final pathPtr = inputPath.toNativeUtf8();
    final opts = <String, dynamic>{};
    if (format != 'tar.gz') opts['format'] = format;
    if (!verify) opts['verify'] = false;
    final optsPtr = opts.isEmpty ? nullptr : jsonEncode(opts).toNativeUtf8();
    try {
      final result = _bindings.chrondbRestoreBackup(_thread, _handle, pathPtr, optsPtr);
      return _parseAny(result);
    } finally {
      malloc.free(pathPtr);
      if (optsPtr != nullptr) malloc.free(optsPtr);
    }
  }

  /// Export to a git bundle snapshot.
  dynamic exportSnapshot(String outputPath, {List<String>? refs}) {
    _ensureOpen();
    final pathPtr = outputPath.toNativeUtf8();
    final opts = <String, dynamic>{};
    if (refs != null) opts['refs'] = refs;
    final optsPtr = opts.isEmpty ? nullptr : jsonEncode(opts).toNativeUtf8();
    try {
      final result = _bindings.chrondbExportSnapshot(_thread, _handle, pathPtr, optsPtr);
      return _parseAny(result);
    } finally {
      malloc.free(pathPtr);
      if (optsPtr != nullptr) malloc.free(optsPtr);
    }
  }

  /// Import a git bundle snapshot.
  dynamic importSnapshot(String inputPath) {
    _ensureOpen();
    final pathPtr = inputPath.toNativeUtf8();
    try {
      final result = _bindings.chrondbImportSnapshot(_thread, _handle, pathPtr, nullptr);
      return _parseAny(result);
    } finally {
      malloc.free(pathPtr);
    }
  }

  /// Close the database connection.
  void close() {
    if (_closed) return;
    _closed = true;
    _bindings.chrondbClose(_thread, _handle);
    _bindings.graalTearDownIsolate(_thread);
  }

  // -- Internal --

  void _ensureOpen() {
    if (_closed) throw ChronDBException('Database connection is closed');
  }

  Map<String, dynamic> _parseObject(Pointer<Utf8> ptr) {
    if (ptr == nullptr) {
      final err = _getLastError(_bindings, _thread);
      if (err != null && err.toLowerCase().contains('not found')) {
        throw DocumentNotFoundException(err);
      }
      throw ChronDBException(err ?? 'Operation failed');
    }
    final str = ptr.toDartString();
    return jsonDecode(str) as Map<String, dynamic>;
  }

  dynamic _parseAny(Pointer<Utf8> ptr) {
    if (ptr == nullptr) {
      final err = _getLastError(_bindings, _thread);
      throw ChronDBException(err ?? 'Operation failed');
    }
    return jsonDecode(ptr.toDartString());
  }

  static Pointer<Void> _createIsolate(ChronDBBindings bindings) {
    final isolatePtr = malloc<Pointer<Void>>();
    final threadPtr = malloc<Pointer<Void>>();
    try {
      final result = bindings.graalCreateIsolate(nullptr, isolatePtr, threadPtr);
      if (result != 0) {
        throw ChronDBException('Failed to create GraalVM isolate (code: $result)');
      }
      return threadPtr.value;
    } finally {
      malloc.free(isolatePtr);
      malloc.free(threadPtr);
    }
  }

  static String? _getLastError(ChronDBBindings bindings, Pointer<Void> thread) {
    final ptr = bindings.chrondbLastError(thread);
    if (ptr == nullptr) return null;
    return ptr.toDartString();
  }
}

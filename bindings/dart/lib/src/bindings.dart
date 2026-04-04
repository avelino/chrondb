import 'dart:ffi';

import 'setup.dart';

// GraalVM types
typedef GraalIsolate = Pointer<Void>;
typedef GraalIsolateThread = Pointer<Void>;

// C function signatures
typedef GraalCreateIsolateC = Int32 Function(
    Pointer<Void> params, Pointer<GraalIsolate> isolate, Pointer<GraalIsolateThread> thread);
typedef GraalCreateIsolateDart = int Function(
    Pointer<Void> params, Pointer<GraalIsolate> isolate, Pointer<GraalIsolateThread> thread);

typedef GraalTearDownIsolateC = Int32 Function(GraalIsolateThread thread);
typedef GraalTearDownIsolateDart = int Function(GraalIsolateThread thread);

typedef ChrondbOpenPathC = Int32 Function(GraalIsolateThread thread, Pointer<Utf8> dbPath);
typedef ChrondbOpenPathDart = int Function(GraalIsolateThread thread, Pointer<Utf8> dbPath);

typedef ChrondbOpenC = Int32 Function(
    GraalIsolateThread thread, Pointer<Utf8> dataPath, Pointer<Utf8> indexPath);
typedef ChrondbOpenDart = int Function(
    GraalIsolateThread thread, Pointer<Utf8> dataPath, Pointer<Utf8> indexPath);

typedef ChrondbCloseC = Int32 Function(GraalIsolateThread thread, Int32 handle);
typedef ChrondbCloseDart = int Function(GraalIsolateThread thread, int handle);

typedef ChrondbPutC = Pointer<Utf8> Function(GraalIsolateThread thread, Int32 handle,
    Pointer<Utf8> id, Pointer<Utf8> jsonDoc, Pointer<Utf8> branch);
typedef ChrondbPutDart = Pointer<Utf8> Function(GraalIsolateThread thread, int handle,
    Pointer<Utf8> id, Pointer<Utf8> jsonDoc, Pointer<Utf8> branch);

typedef ChrondbGetC = Pointer<Utf8> Function(
    GraalIsolateThread thread, Int32 handle, Pointer<Utf8> id, Pointer<Utf8> branch);
typedef ChrondbGetDart = Pointer<Utf8> Function(
    GraalIsolateThread thread, int handle, Pointer<Utf8> id, Pointer<Utf8> branch);

typedef ChrondbDeleteC = Int32 Function(
    GraalIsolateThread thread, Int32 handle, Pointer<Utf8> id, Pointer<Utf8> branch);
typedef ChrondbDeleteDart = int Function(
    GraalIsolateThread thread, int handle, Pointer<Utf8> id, Pointer<Utf8> branch);

typedef ChrondbStringReturnC = Pointer<Utf8> Function(
    GraalIsolateThread thread, Int32 handle, Pointer<Utf8> arg1, Pointer<Utf8> arg2);
typedef ChrondbStringReturnDart = Pointer<Utf8> Function(
    GraalIsolateThread thread, int handle, Pointer<Utf8> arg1, Pointer<Utf8> arg2);

typedef ChrondbNoArgC = Pointer<Utf8> Function(GraalIsolateThread thread, Int32 handle);
typedef ChrondbNoArgDart = Pointer<Utf8> Function(GraalIsolateThread thread, int handle);

typedef ChrondbFreeStringC = Void Function(GraalIsolateThread thread, Pointer<Utf8> ptr);
typedef ChrondbFreeStringDart = void Function(GraalIsolateThread thread, Pointer<Utf8> ptr);

typedef ChrondbLastErrorC = Pointer<Utf8> Function(GraalIsolateThread thread);
typedef ChrondbLastErrorDart = Pointer<Utf8> Function(GraalIsolateThread thread);

/// Holds loaded native library function pointers.
class ChronDBBindings {
  final DynamicLibrary _lib;

  late final GraalCreateIsolateDart graalCreateIsolate;
  late final GraalTearDownIsolateDart graalTearDownIsolate;
  late final ChrondbOpenPathDart chrondbOpenPath;
  late final ChrondbOpenDart chrondbOpen;
  late final ChrondbCloseDart chrondbClose;
  late final ChrondbPutDart chrondbPut;
  late final ChrondbGetDart chrondbGet;
  late final ChrondbDeleteDart chrondbDelete;
  late final ChrondbStringReturnDart chrondbListByPrefix;
  late final ChrondbStringReturnDart chrondbListByTable;
  late final ChrondbStringReturnDart chrondbHistory;
  late final ChrondbStringReturnDart chrondbQuery;
  late final ChrondbStringReturnDart chrondbExecuteSql;
  late final ChrondbStringReturnDart chrondbSetupRemote;
  late final ChrondbNoArgDart chrondbPush;
  late final ChrondbNoArgDart chrondbPull;
  late final ChrondbNoArgDart chrondbFetch;
  late final ChrondbNoArgDart chrondbRemoteStatus;
  late final ChrondbStringReturnDart chrondbExport;
  late final ChrondbStringReturnDart chrondbCreateBackup;
  late final ChrondbStringReturnDart chrondbRestoreBackup;
  late final ChrondbStringReturnDart chrondbExportSnapshot;
  late final ChrondbStringReturnDart chrondbImportSnapshot;
  late final ChrondbFreeStringDart chrondbFreeString;
  late final ChrondbLastErrorDart chrondbLastError;

  ChronDBBindings._(this._lib) {
    graalCreateIsolate =
        _lib.lookupFunction<GraalCreateIsolateC, GraalCreateIsolateDart>('graal_create_isolate');
    graalTearDownIsolate = _lib
        .lookupFunction<GraalTearDownIsolateC, GraalTearDownIsolateDart>('graal_tear_down_isolate');
    chrondbOpenPath =
        _lib.lookupFunction<ChrondbOpenPathC, ChrondbOpenPathDart>('chrondb_open_path');
    chrondbOpen = _lib.lookupFunction<ChrondbOpenC, ChrondbOpenDart>('chrondb_open');
    chrondbClose = _lib.lookupFunction<ChrondbCloseC, ChrondbCloseDart>('chrondb_close');
    chrondbPut = _lib.lookupFunction<ChrondbPutC, ChrondbPutDart>('chrondb_put');
    chrondbGet = _lib.lookupFunction<ChrondbGetC, ChrondbGetDart>('chrondb_get');
    chrondbDelete = _lib.lookupFunction<ChrondbDeleteC, ChrondbDeleteDart>('chrondb_delete');
    chrondbListByPrefix =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_list_by_prefix');
    chrondbListByTable =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_list_by_table');
    chrondbHistory =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_history');
    chrondbQuery =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_query');
    chrondbExecuteSql =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_execute_sql');
    chrondbSetupRemote = _lib
        .lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_setup_remote');
    chrondbPush = _lib.lookupFunction<ChrondbNoArgC, ChrondbNoArgDart>('chrondb_push');
    chrondbPull = _lib.lookupFunction<ChrondbNoArgC, ChrondbNoArgDart>('chrondb_pull');
    chrondbFetch = _lib.lookupFunction<ChrondbNoArgC, ChrondbNoArgDart>('chrondb_fetch');
    chrondbRemoteStatus =
        _lib.lookupFunction<ChrondbNoArgC, ChrondbNoArgDart>('chrondb_remote_status');
    chrondbExport =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_export');
    chrondbCreateBackup =
        _lib.lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_create_backup');
    chrondbRestoreBackup = _lib
        .lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_restore_backup');
    chrondbExportSnapshot = _lib
        .lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_export_snapshot');
    chrondbImportSnapshot = _lib
        .lookupFunction<ChrondbStringReturnC, ChrondbStringReturnDart>('chrondb_import_snapshot');
    chrondbFreeString =
        _lib.lookupFunction<ChrondbFreeStringC, ChrondbFreeStringDart>('chrondb_free_string');
    chrondbLastError =
        _lib.lookupFunction<ChrondbLastErrorC, ChrondbLastErrorDart>('chrondb_last_error');
  }

  static ChronDBBindings? _instance;

  /// Load the native library, downloading if not found.
  static ChronDBBindings load() {
    if (_instance != null) return _instance!;

    // ensureLibraryInstalled checks CHRONDB_LIB_DIR, ~/.chrondb/lib/,
    // and auto-downloads from GitHub Releases if needed.
    final libPath = ensureLibraryInstalled();
    final lib = DynamicLibrary.open(libPath);
    _instance = ChronDBBindings._(lib);
    return _instance!;
  }
}

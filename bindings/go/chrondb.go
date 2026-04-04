// Package chrondb provides Go bindings for the ChronDB database.
//
// ChronDB is a chronological key/value database backed by Git.
// This package uses cgo to call the native libchrondb shared library.
//
//	db, err := chrondb.OpenPath("/tmp/mydb")
//	if err != nil {
//	    log.Fatal(err)
//	}
//	defer db.Close()
//
//	err = db.Put("user:1", map[string]any{"name": "Alice", "age": 30}, "")
//	doc, err := db.Get("user:1", "")
package chrondb

/*
#cgo LDFLAGS: -lchrondb
#cgo CFLAGS: -I${SRCDIR}/include

#include <stdlib.h>

// GraalVM isolate types
typedef void graal_isolate_t;
typedef void graal_isolatethread_t;

typedef struct {
    int version;
    unsigned long reserved_address_space_size;
} graal_create_isolate_params_t;

// GraalVM lifecycle
extern int graal_create_isolate(graal_create_isolate_params_t *params,
    graal_isolate_t **isolate, graal_isolatethread_t **thread);
extern int graal_tear_down_isolate(graal_isolatethread_t *thread);

// ChronDB C API
extern int chrondb_open(graal_isolatethread_t *thread,
    const char *data_path, const char *index_path);
extern int chrondb_open_path(graal_isolatethread_t *thread, const char *db_path);
extern int chrondb_close(graal_isolatethread_t *thread, int handle);

extern char *chrondb_put(graal_isolatethread_t *thread, int handle,
    const char *id, const char *json_doc, const char *branch);
extern char *chrondb_get(graal_isolatethread_t *thread, int handle,
    const char *id, const char *branch);
extern int chrondb_delete(graal_isolatethread_t *thread, int handle,
    const char *id, const char *branch);

extern char *chrondb_list_by_prefix(graal_isolatethread_t *thread, int handle,
    const char *prefix, const char *branch);
extern char *chrondb_list_by_table(graal_isolatethread_t *thread, int handle,
    const char *table, const char *branch);
extern char *chrondb_history(graal_isolatethread_t *thread, int handle,
    const char *id, const char *branch);
extern char *chrondb_query(graal_isolatethread_t *thread, int handle,
    const char *query_json, const char *branch);
extern char *chrondb_execute_sql(graal_isolatethread_t *thread, int handle,
    const char *sql, const char *branch);

extern char *chrondb_setup_remote(graal_isolatethread_t *thread, int handle,
    const char *remote_url);
extern char *chrondb_push(graal_isolatethread_t *thread, int handle);
extern char *chrondb_pull(graal_isolatethread_t *thread, int handle);
extern char *chrondb_fetch(graal_isolatethread_t *thread, int handle);
extern char *chrondb_remote_status(graal_isolatethread_t *thread, int handle);

extern char *chrondb_export(graal_isolatethread_t *thread, int handle,
    const char *target_dir, const char *options_json);
extern char *chrondb_create_backup(graal_isolatethread_t *thread, int handle,
    const char *output_path, const char *options_json);
extern char *chrondb_restore_backup(graal_isolatethread_t *thread, int handle,
    const char *input_path, const char *options_json);
extern char *chrondb_export_snapshot(graal_isolatethread_t *thread, int handle,
    const char *output_path, const char *options_json);
extern char *chrondb_import_snapshot(graal_isolatethread_t *thread, int handle,
    const char *input_path, const char *options_json);

extern void chrondb_free_string(graal_isolatethread_t *thread, char *ptr);
extern char *chrondb_last_error(graal_isolatethread_t *thread);
*/
import "C"

import (
	"encoding/json"
	"errors"
	"fmt"
	"unsafe"
)

var (
	ErrNotFound = errors.New("document not found")
	ErrClosed   = errors.New("database connection is closed")
)

// DB represents a connection to a ChronDB database.
type DB struct {
	thread *C.graal_isolatethread_t
	handle C.int
	closed bool
}

// OpenPath opens a database using a single directory path (preferred).
// Downloads the native library automatically if not found.
func OpenPath(dbPath string) (*DB, error) {
	if err := ensureLibraryInstalled(); err != nil {
		return nil, fmt.Errorf("library setup failed: %w", err)
	}

	thread, err := createIsolate()
	if err != nil {
		return nil, err
	}

	cPath := C.CString(dbPath)
	defer C.free(unsafe.Pointer(cPath))

	handle := C.chrondb_open_path(thread, cPath)
	if handle < 0 {
		msg := lastError(thread)
		C.graal_tear_down_isolate(thread)
		return nil, fmt.Errorf("failed to open database: %s", msg)
	}

	return &DB{thread: thread, handle: handle}, nil
}

// Open opens a database with separate data and index paths.
//
// Deprecated: Use OpenPath instead.
func Open(dataPath, indexPath string) (*DB, error) {
	if err := ensureLibraryInstalled(); err != nil {
		return nil, fmt.Errorf("library setup failed: %w", err)
	}

	thread, err := createIsolate()
	if err != nil {
		return nil, err
	}

	cData := C.CString(dataPath)
	cIndex := C.CString(indexPath)
	defer C.free(unsafe.Pointer(cData))
	defer C.free(unsafe.Pointer(cIndex))

	handle := C.chrondb_open(thread, cData, cIndex)
	if handle < 0 {
		msg := lastError(thread)
		C.graal_tear_down_isolate(thread)
		return nil, fmt.Errorf("failed to open database: %s", msg)
	}

	return &DB{thread: thread, handle: handle}, nil
}

// Put saves a document. Returns the saved document.
func (db *DB) Put(id string, doc map[string]any, branch string) (map[string]any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	jsonDoc, err := json.Marshal(doc)
	if err != nil {
		return nil, fmt.Errorf("json encode error: %w", err)
	}

	cID := C.CString(id)
	cDoc := C.CString(string(jsonDoc))
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cID))
	defer C.free(unsafe.Pointer(cDoc))
	defer freeCString(cBranch)

	result := C.chrondb_put(db.thread, db.handle, cID, cDoc, cBranch)
	return db.parseObject(result)
}

// Get retrieves a document by ID.
func (db *DB) Get(id string, branch string) (map[string]any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cID := C.CString(id)
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cID))
	defer freeCString(cBranch)

	result := C.chrondb_get(db.thread, db.handle, cID, cBranch)
	return db.parseObject(result)
}

// Delete removes a document by ID.
func (db *DB) Delete(id string, branch string) error {
	if err := db.ensureOpen(); err != nil {
		return err
	}

	cID := C.CString(id)
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cID))
	defer freeCString(cBranch)

	result := C.chrondb_delete(db.thread, db.handle, cID, cBranch)
	switch result {
	case 0:
		return nil
	case 1:
		return ErrNotFound
	default:
		return fmt.Errorf("delete failed: %s", lastError(db.thread))
	}
}

// ListByPrefix lists documents matching an ID prefix.
func (db *DB) ListByPrefix(prefix string, branch string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cPrefix := C.CString(prefix)
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cPrefix))
	defer freeCString(cBranch)

	result := C.chrondb_list_by_prefix(db.thread, db.handle, cPrefix, cBranch)
	return db.parseAny(result)
}

// ListByTable lists documents by table name.
func (db *DB) ListByTable(table string, branch string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cTable := C.CString(table)
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cTable))
	defer freeCString(cBranch)

	result := C.chrondb_list_by_table(db.thread, db.handle, cTable, cBranch)
	return db.parseAny(result)
}

// History returns the change history of a document.
func (db *DB) History(id string, branch string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cID := C.CString(id)
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cID))
	defer freeCString(cBranch)

	result := C.chrondb_history(db.thread, db.handle, cID, cBranch)
	return db.parseAny(result)
}

// Query executes a Lucene query.
func (db *DB) Query(query map[string]any, branch string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	queryJSON, err := json.Marshal(query)
	if err != nil {
		return nil, fmt.Errorf("json encode error: %w", err)
	}

	cQuery := C.CString(string(queryJSON))
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cQuery))
	defer freeCString(cBranch)

	result := C.chrondb_query(db.thread, db.handle, cQuery, cBranch)
	return db.parseAny(result)
}

// Execute runs a SQL query against the database.
func (db *DB) Execute(sql string, branch string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cSQL := C.CString(sql)
	cBranch := optionalCString(branch)
	defer C.free(unsafe.Pointer(cSQL))
	defer freeCString(cBranch)

	result := C.chrondb_execute_sql(db.thread, db.handle, cSQL, cBranch)
	return db.parseAny(result)
}

// SetupRemote configures a remote URL for push/pull.
func (db *DB) SetupRemote(remoteURL string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cURL := C.CString(remoteURL)
	defer C.free(unsafe.Pointer(cURL))

	result := C.chrondb_setup_remote(db.thread, db.handle, cURL)
	return db.parseAny(result)
}

// Push pushes changes to the configured remote.
func (db *DB) Push() (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}
	result := C.chrondb_push(db.thread, db.handle)
	return db.parseAny(result)
}

// Pull pulls changes from the configured remote.
func (db *DB) Pull() (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}
	result := C.chrondb_pull(db.thread, db.handle)
	return db.parseAny(result)
}

// Fetch fetches changes without merging.
func (db *DB) Fetch() (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}
	result := C.chrondb_fetch(db.thread, db.handle)
	return db.parseAny(result)
}

// RemoteStatus returns the remote synchronization status.
func (db *DB) RemoteStatus() (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}
	result := C.chrondb_remote_status(db.thread, db.handle)
	return db.parseAny(result)
}

// ExportToDirectory exports the repository tree to a filesystem directory.
func (db *DB) ExportToDirectory(targetDir string, opts map[string]any) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cDir := C.CString(targetDir)
	cOpts := optionalJSONCString(opts)
	defer C.free(unsafe.Pointer(cDir))
	defer freeCString(cOpts)

	result := C.chrondb_export(db.thread, db.handle, cDir, cOpts)
	return db.parseAny(result)
}

// CreateBackup creates a full backup.
func (db *DB) CreateBackup(outputPath string, opts map[string]any) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cPath := C.CString(outputPath)
	cOpts := optionalJSONCString(opts)
	defer C.free(unsafe.Pointer(cPath))
	defer freeCString(cOpts)

	result := C.chrondb_create_backup(db.thread, db.handle, cPath, cOpts)
	return db.parseAny(result)
}

// RestoreBackup restores from a backup file.
func (db *DB) RestoreBackup(inputPath string, opts map[string]any) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cPath := C.CString(inputPath)
	cOpts := optionalJSONCString(opts)
	defer C.free(unsafe.Pointer(cPath))
	defer freeCString(cOpts)

	result := C.chrondb_restore_backup(db.thread, db.handle, cPath, cOpts)
	return db.parseAny(result)
}

// ExportSnapshot exports to a git bundle snapshot.
func (db *DB) ExportSnapshot(outputPath string, opts map[string]any) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cPath := C.CString(outputPath)
	cOpts := optionalJSONCString(opts)
	defer C.free(unsafe.Pointer(cPath))
	defer freeCString(cOpts)

	result := C.chrondb_export_snapshot(db.thread, db.handle, cPath, cOpts)
	return db.parseAny(result)
}

// ImportSnapshot imports a git bundle snapshot.
func (db *DB) ImportSnapshot(inputPath string) (any, error) {
	if err := db.ensureOpen(); err != nil {
		return nil, err
	}

	cPath := C.CString(inputPath)
	defer C.free(unsafe.Pointer(cPath))

	result := C.chrondb_import_snapshot(db.thread, db.handle, cPath, nil)
	return db.parseAny(result)
}

// Close closes the database connection.
func (db *DB) Close() error {
	if db.closed {
		return nil
	}
	db.closed = true
	C.chrondb_close(db.thread, db.handle)
	C.graal_tear_down_isolate(db.thread)
	return nil
}

// -- Internal helpers --

func (db *DB) ensureOpen() error {
	if db.closed {
		return ErrClosed
	}
	return nil
}

func (db *DB) parseObject(ptr *C.char) (map[string]any, error) {
	if ptr == nil {
		msg := lastError(db.thread)
		if msg == "document not found" || msg == "not found" {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("operation failed: %s", msg)
	}
	str := C.GoString(ptr)
	var result map[string]any
	if err := json.Unmarshal([]byte(str), &result); err != nil {
		return nil, fmt.Errorf("json decode error: %w", err)
	}
	return result, nil
}

func (db *DB) parseAny(ptr *C.char) (any, error) {
	if ptr == nil {
		return nil, fmt.Errorf("operation failed: %s", lastError(db.thread))
	}
	str := C.GoString(ptr)
	var result any
	if err := json.Unmarshal([]byte(str), &result); err != nil {
		return nil, fmt.Errorf("json decode error: %w", err)
	}
	return result, nil
}

func createIsolate() (*C.graal_isolatethread_t, error) {
	var isolate *C.graal_isolate_t
	var thread *C.graal_isolatethread_t
	result := C.graal_create_isolate(nil, &isolate, &thread)
	if result != 0 {
		return nil, fmt.Errorf("failed to create GraalVM isolate (code: %d)", result)
	}
	return thread, nil
}

func lastError(thread *C.graal_isolatethread_t) string {
	ptr := C.chrondb_last_error(thread)
	if ptr == nil {
		return "unknown error"
	}
	return C.GoString(ptr)
}

func optionalCString(s string) *C.char {
	if s == "" {
		return nil
	}
	return C.CString(s)
}

func optionalJSONCString(opts map[string]any) *C.char {
	if len(opts) == 0 {
		return nil
	}
	data, err := json.Marshal(opts)
	if err != nil {
		return nil
	}
	return C.CString(string(data))
}

func freeCString(ptr *C.char) {
	if ptr != nil {
		C.free(unsafe.Pointer(ptr))
	}
}

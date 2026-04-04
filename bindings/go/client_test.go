package chrondb_test

import (
	"os"
	"path/filepath"
	"testing"

	chrondb "github.com/avelino/chrondb/bindings/go"
)

var libAvailable bool

func TestMain(m *testing.M) {
	dir, err := os.MkdirTemp("", "chrondb-check-")
	if err == nil {
		db, err := chrondb.OpenPath(dir)
		if err == nil {
			db.Close()
			libAvailable = true
		}
		os.RemoveAll(dir)
	}
	os.Exit(m.Run())
}

func setupDB(t *testing.T) (*chrondb.DB, string) {
	t.Helper()
	if !libAvailable {
		t.Skip("ChronDB shared library not available")
	}
	dir, err := os.MkdirTemp("", "chrondb-test-")
	if err != nil {
		t.Fatal(err)
	}
	db, err := chrondb.OpenPath(filepath.Join(dir, "db"))
	if err != nil {
		os.RemoveAll(dir)
		t.Fatal(err)
	}
	return db, dir
}

func TestPutAndGet(t *testing.T) {
	db, dir := setupDB(t)
	defer os.RemoveAll(dir)
	defer db.Close()

	doc := map[string]any{"name": "Alice", "age": float64(30)}
	_, err := db.Put("user:1", doc, "")
	if err != nil {
		t.Fatalf("put failed: %v", err)
	}

	result, err := db.Get("user:1", "")
	if err != nil {
		t.Fatalf("get failed: %v", err)
	}
	if result["name"] != "Alice" {
		t.Errorf("expected Alice, got %v", result["name"])
	}
}

func TestGetNotFound(t *testing.T) {
	db, dir := setupDB(t)
	defer os.RemoveAll(dir)
	defer db.Close()

	_, err := db.Get("nonexistent:999", "")
	if err != chrondb.ErrNotFound {
		t.Errorf("expected ErrNotFound, got %v", err)
	}
}

func TestDelete(t *testing.T) {
	db, dir := setupDB(t)
	defer os.RemoveAll(dir)
	defer db.Close()

	_, err := db.Put("user:2", map[string]any{"name": "Bob"}, "")
	if err != nil {
		t.Fatal(err)
	}

	err = db.Delete("user:2", "")
	if err != nil {
		t.Fatalf("delete failed: %v", err)
	}

	_, err = db.Get("user:2", "")
	if err != chrondb.ErrNotFound {
		t.Errorf("expected ErrNotFound after delete, got %v", err)
	}
}

func TestListByPrefix(t *testing.T) {
	db, dir := setupDB(t)
	defer os.RemoveAll(dir)
	defer db.Close()

	db.Put("item:1", map[string]any{"name": "A"}, "")
	db.Put("item:2", map[string]any{"name": "B"}, "")

	result, err := db.ListByPrefix("item", "")
	if err != nil {
		t.Fatalf("list by prefix failed: %v", err)
	}
	if result == nil {
		t.Error("expected non-nil result")
	}
}

func TestListByTable(t *testing.T) {
	db, dir := setupDB(t)
	defer os.RemoveAll(dir)
	defer db.Close()

	db.Put("product:1", map[string]any{"name": "Widget"}, "")

	result, err := db.ListByTable("product", "")
	if err != nil {
		t.Fatalf("list by table failed: %v", err)
	}
	if result == nil {
		t.Error("expected non-nil result")
	}
}

func TestHistory(t *testing.T) {
	db, dir := setupDB(t)
	defer os.RemoveAll(dir)
	defer db.Close()

	db.Put("doc:1", map[string]any{"version": float64(1)}, "")
	db.Put("doc:1", map[string]any{"version": float64(2)}, "")

	result, err := db.History("doc:1", "")
	if err != nil {
		t.Fatalf("history failed: %v", err)
	}
	if arr, ok := result.([]any); ok {
		if len(arr) < 2 {
			t.Errorf("expected at least 2 history entries, got %d", len(arr))
		}
	}
}

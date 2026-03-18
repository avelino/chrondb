"""Integration tests for ChronDB Python bindings (UniFFI)."""

import os

import pytest

_lib_available = bool(os.environ.get("CHRONDB_LIB_PATH")) or os.path.exists(
    os.path.join(os.path.expanduser("~"), ".chrondb", "lib")
)

_skip_no_lib = pytest.mark.skipif(
    not _lib_available, reason="ChronDB shared library not available"
)

from chrondb import ChronDB, ChronDBError, DocumentNotFoundError


@_skip_no_lib
class TestChronDB:
    @pytest.fixture
    def db(self, tmp_path):
        data_path = str(tmp_path / "data")
        index_path = str(tmp_path / "index")
        with ChronDB(data_path, index_path) as db:
            yield db

    def test_put_and_get(self, db):
        saved = db.put("user:1", {"name": "Alice", "age": 30})
        assert saved["name"] == "Alice"

        doc = db.get("user:1")
        assert doc["name"] == "Alice"

    def test_get_not_found(self, db):
        with pytest.raises(DocumentNotFoundError):
            db.get("nonexistent:999")

    def test_delete(self, db):
        db.put("user:2", {"name": "Bob"})
        assert db.delete("user:2") is True

        with pytest.raises(DocumentNotFoundError):
            db.get("user:2")

    def test_delete_not_found(self, db):
        with pytest.raises((DocumentNotFoundError, ChronDBError)):
            db.delete("nonexistent:999")

    def test_list_by_prefix(self, db):
        db.put("user:1", {"name": "Alice"})
        db.put("user:2", {"name": "Bob"})
        db.put("product:1", {"name": "Widget"})

        users = db.list_by_prefix("user:")
        assert len(users) >= 2

    def test_list_by_table(self, db):
        db.put("user:1", {"name": "Alice"})
        db.put("user:2", {"name": "Bob"})

        users = db.list_by_table("user")
        assert len(users) >= 2

    def test_history(self, db):
        db.put("user:1", {"name": "Alice", "version": 1})
        db.put("user:1", {"name": "Alice Updated", "version": 2})

        history = db.history("user:1")
        assert len(history) >= 1

    def test_idle_timeout(self, tmp_path):
        data_path = str(tmp_path / "data_idle")
        index_path = str(tmp_path / "index_idle")

        with ChronDB(data_path, index_path, idle_timeout=60) as db:
            db.put("idle:1", {"name": "test"})
            doc = db.get("idle:1")
            assert doc["name"] == "test"

    def test_context_manager(self, tmp_path):
        data_path = str(tmp_path / "data_ctx")
        index_path = str(tmp_path / "index_ctx")

        with ChronDB(data_path, index_path) as db:
            db.put("test:1", {"value": "hello"})
            result = db.get("test:1")
            assert result["value"] == "hello"

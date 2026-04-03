"""Pythonic wrapper over UniFFI-generated bindings.

This module provides a dict-based API on top of the JSON-string FFI layer,
plus context manager support and idiomatic exception hierarchy.
"""

import json
import warnings
from typing import Any, Dict, List, Optional

from chrondb._generated import chrondb as _ffi


class ChronDBError(Exception):
    """Base exception for ChronDB errors."""
    pass


class DocumentNotFoundError(ChronDBError):
    """Raised when a document is not found."""
    pass


def _convert_error(e: _ffi.ChronDbError) -> ChronDBError:
    """Convert UniFFI error to Pythonic exception."""
    if isinstance(e, _ffi.ChronDbError.NotFound):
        return DocumentNotFoundError(str(e))
    return ChronDBError(str(e))


class ChronDB:
    """A connection to a ChronDB database instance.

    Use as a context manager for automatic cleanup:

        with ChronDB("/tmp/mydb") as db:
            db.put("user:1", {"name": "Alice"})
            doc = db.get("user:1")

    For long-running services, use ``idle_timeout`` to suspend the GraalVM
    isolate when idle:

        db = ChronDB("/tmp/mydb", idle_timeout=120)
    """

    def __init__(self, db_path: str, index_path: Optional[str] = None,
                 idle_timeout: Optional[int] = None):
        """Open a ChronDB database.

        Args:
            db_path: Path for the database directory. When used alone, the
                index is stored automatically inside this directory.
            index_path: Deprecated. Separate index path. If provided, db_path
                is used as data_path for backward compatibility.
            idle_timeout: Seconds of inactivity before suspending the GraalVM
                isolate. None (default) keeps it alive for the entire lifetime.
        """
        try:
            if index_path is not None:
                warnings.warn(
                    "Passing separate data_path and index_path is deprecated. "
                    "Use ChronDB(db_path) instead — the index is managed "
                    "automatically inside the database directory.",
                    DeprecationWarning,
                    stacklevel=2,
                )
                if idle_timeout is not None:
                    self._inner = _ffi.ChronDb.open_with_idle_timeout(
                        db_path, index_path, idle_timeout)
                else:
                    self._inner = _ffi.ChronDb.open(db_path, index_path)
            else:
                if idle_timeout is not None:
                    idx = f"{db_path}/.chrondb-index"
                    self._inner = _ffi.ChronDb.open_with_idle_timeout(
                        db_path, idx, idle_timeout)
                else:
                    self._inner = _ffi.ChronDb.open_path(db_path)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False

    def put(self, id: str, doc: Dict[str, Any],
            branch: Optional[str] = None) -> Dict[str, Any]:
        """Save a document.

        Args:
            id: Document ID (e.g., "user:1").
            doc: Document data as a dictionary.
            branch: Optional branch name.

        Returns:
            The saved document as a dictionary.
        """
        try:
            result = self._inner.put(id, json.dumps(doc), branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def get(self, id: str, branch: Optional[str] = None) -> Dict[str, Any]:
        """Get a document by ID.

        Args:
            id: Document ID.
            branch: Optional branch name.

        Returns:
            The document as a dictionary.

        Raises:
            DocumentNotFoundError: If the document doesn't exist.
        """
        try:
            result = self._inner.get(id, branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def delete(self, id: str, branch: Optional[str] = None) -> bool:
        """Delete a document by ID.

        Args:
            id: Document ID.
            branch: Optional branch name.

        Returns:
            True if deleted.

        Raises:
            DocumentNotFoundError: If the document doesn't exist.
        """
        try:
            self._inner.delete(id, branch)
            return True
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def list_by_prefix(self, prefix: str,
                       branch: Optional[str] = None) -> List[Dict[str, Any]]:
        """List documents by ID prefix."""
        try:
            result = self._inner.list_by_prefix(prefix, branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def list_by_table(self, table: str,
                      branch: Optional[str] = None) -> List[Dict[str, Any]]:
        """List documents by table name."""
        try:
            result = self._inner.list_by_table(table, branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def history(self, id: str,
                branch: Optional[str] = None) -> List[Dict[str, Any]]:
        """Get the change history of a document."""
        try:
            result = self._inner.history(id, branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def query(self, query: Dict[str, Any],
              branch: Optional[str] = None) -> Dict[str, Any]:
        """Execute a query against the Lucene index."""
        try:
            result = self._inner.query(json.dumps(query), branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def execute(self, sql: str,
                branch: Optional[str] = None) -> Dict[str, Any]:
        """Execute a SQL query against the database.

        Args:
            sql: SQL statement (SELECT, INSERT, UPDATE, DELETE, etc.).
            branch: Optional branch name.

        Returns:
            Result dict with structure depending on query type:
            - SELECT: {"type":"select","columns":[...],"rows":[...],"count":N}
            - INSERT/UPDATE/DELETE: {"type":"...","affected":N}
            - Error: {"type":"error","message":"..."}
        """
        try:
            result = self._inner.execute_sql(sql, branch)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    # --- Export & Backup ---

    def export_to_directory(self, target_dir: str,
                            branch: Optional[str] = None,
                            prefix: Optional[str] = None,
                            format: str = "json",
                            decode_paths: bool = True,
                            overwrite: bool = False) -> Dict[str, Any]:
        """Export the repository tree to a filesystem directory.

        Args:
            target_dir: Target directory path.
            branch: Branch to export (default: main).
            prefix: Only export paths matching this prefix.
            format: "json" (pretty-printed, default) or "raw".
            decode_paths: Decode encoded paths (default: True).
            overwrite: Overwrite existing target directory (default: False).

        Returns:
            Export metadata dict with status, files_exported, etc.
        """
        try:
            opts: Dict[str, Any] = {}
            if branch:
                opts["branch"] = branch
            if prefix:
                opts["prefix"] = prefix
            if format != "json":
                opts["format"] = format
            if not decode_paths:
                opts["decode_paths"] = False
            if overwrite:
                opts["overwrite"] = True
            result = self._inner.export_to_directory(
                target_dir, json.dumps(opts) if opts else None)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def create_backup(self, output_path: str,
                      format: str = "tar.gz",
                      verify: bool = True) -> Dict[str, Any]:
        """Create a full backup of the repository.

        Args:
            output_path: Backup file path.
            format: "tar.gz" (default) or "bundle".
            verify: Run integrity checks (default: True).

        Returns:
            Backup metadata dict with status, path, checksum.
        """
        try:
            opts: Dict[str, Any] = {}
            if format != "tar.gz":
                opts["format"] = format
            if not verify:
                opts["verify"] = False
            result = self._inner.create_backup(
                output_path, json.dumps(opts) if opts else None)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def restore_backup(self, input_path: str,
                       format: str = "tar.gz",
                       verify: bool = True) -> Dict[str, Any]:
        """Restore the repository from a backup file.

        Args:
            input_path: Backup file path.
            format: "tar.gz" (default) or "bundle".
            verify: Run integrity checks (default: True).

        Returns:
            Restore metadata dict with status, restore_type.
        """
        try:
            opts: Dict[str, Any] = {}
            if format != "tar.gz":
                opts["format"] = format
            if not verify:
                opts["verify"] = False
            result = self._inner.restore_backup(
                input_path, json.dumps(opts) if opts else None)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def export_snapshot(self, output_path: str,
                        refs: Optional[list] = None) -> Dict[str, Any]:
        """Export the repository to a git bundle snapshot.

        Args:
            output_path: Bundle file path.
            refs: Optional list of refs to include.

        Returns:
            Snapshot metadata dict.
        """
        try:
            opts: Dict[str, Any] = {}
            if refs:
                opts["refs"] = refs
            result = self._inner.export_snapshot(
                output_path, json.dumps(opts) if opts else None)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

    def import_snapshot(self, input_path: str) -> Dict[str, Any]:
        """Import a git bundle snapshot into the repository.

        Args:
            input_path: Bundle file path.

        Returns:
            Import metadata dict.
        """
        try:
            result = self._inner.import_snapshot(input_path, None)
            return json.loads(result)
        except _ffi.ChronDbError as e:
            raise _convert_error(e) from e

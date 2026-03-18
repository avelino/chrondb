"""Pythonic wrapper over UniFFI-generated bindings.

This module provides a dict-based API on top of the JSON-string FFI layer,
plus context manager support and idiomatic exception hierarchy.
"""

import json
from typing import Any, Dict, List, Optional

from chrondb._generated import chrondb as _ffi


class ChronDBError(Exception):
    """Base exception for ChronDB errors."""
    pass


class DocumentNotFoundError(ChronDBError):
    """Raised when a document is not found."""
    pass


def _convert_error(e: _ffi.ChronDBError) -> ChronDBError:
    """Convert UniFFI error to Pythonic exception."""
    if isinstance(e, _ffi.ChronDBError.NotFound):
        return DocumentNotFoundError(str(e))
    return ChronDBError(str(e))


class ChronDB:
    """A connection to a ChronDB database instance.

    Use as a context manager for automatic cleanup:

        with ChronDB("/tmp/data", "/tmp/index") as db:
            db.put("user:1", {"name": "Alice"})
            doc = db.get("user:1")

    For long-running services, use ``idle_timeout`` to suspend the GraalVM
    isolate when idle:

        db = ChronDB("/tmp/data", "/tmp/index", idle_timeout=120)
    """

    def __init__(self, data_path: str, index_path: str,
                 idle_timeout: Optional[int] = None):
        """Open a ChronDB database.

        Args:
            data_path: Path for the Git repository (data storage).
            index_path: Path for the Lucene index.
            idle_timeout: Seconds of inactivity before suspending the GraalVM
                isolate. None (default) keeps it alive for the entire lifetime.
        """
        try:
            if idle_timeout is not None:
                self._inner = _ffi.ChronDB.open_with_idle_timeout(
                    data_path, index_path, idle_timeout)
            else:
                self._inner = _ffi.ChronDB.open(data_path, index_path)
        except _ffi.ChronDBError as e:
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
        except _ffi.ChronDBError as e:
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
        except _ffi.ChronDBError as e:
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
        except _ffi.ChronDBError as e:
            raise _convert_error(e) from e

    def list_by_prefix(self, prefix: str,
                       branch: Optional[str] = None) -> List[Dict[str, Any]]:
        """List documents by ID prefix."""
        try:
            result = self._inner.list_by_prefix(prefix, branch)
            return json.loads(result)
        except _ffi.ChronDBError as e:
            raise _convert_error(e) from e

    def list_by_table(self, table: str,
                      branch: Optional[str] = None) -> List[Dict[str, Any]]:
        """List documents by table name."""
        try:
            result = self._inner.list_by_table(table, branch)
            return json.loads(result)
        except _ffi.ChronDBError as e:
            raise _convert_error(e) from e

    def history(self, id: str,
                branch: Optional[str] = None) -> List[Dict[str, Any]]:
        """Get the change history of a document."""
        try:
            result = self._inner.history(id, branch)
            return json.loads(result)
        except _ffi.ChronDBError as e:
            raise _convert_error(e) from e

    def query(self, query: Dict[str, Any],
              branch: Optional[str] = None) -> Dict[str, Any]:
        """Execute a query against the Lucene index."""
        try:
            result = self._inner.query(json.dumps(query), branch)
            return json.loads(result)
        except _ffi.ChronDBError as e:
            raise _convert_error(e) from e

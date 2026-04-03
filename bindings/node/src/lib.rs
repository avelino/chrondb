use chrondb::{ChronDB as ChronDBInner, ChronDBError};
use napi::bindgen_prelude::*;
use napi_derive::napi;
use std::time::Duration;

fn to_napi_error(e: ChronDBError) -> napi::Error {
    napi::Error::from_reason(e.to_string())
}

#[napi]
pub struct ChronDB {
    inner: ChronDBInner,
}

#[napi]
impl ChronDB {
    /// Opens a ChronDB database with a single directory path.
    /// The index is stored automatically inside the database directory.
    #[napi(factory)]
    pub fn open_path(db_path: String) -> Result<Self> {
        let inner = ChronDBInner::open_path(&db_path).map_err(to_napi_error)?;
        Ok(Self { inner })
    }

    /// Opens a ChronDB database at the given paths.
    /// Deprecated: Use `openPath` instead.
    #[napi(factory)]
    pub fn open(data_path: String, index_path: String) -> Result<Self> {
        let inner = ChronDBInner::open(&data_path, &index_path).map_err(to_napi_error)?;
        Ok(Self { inner })
    }

    /// Opens a ChronDB database with idle timeout (in seconds).
    /// The GraalVM isolate is suspended after the specified seconds of inactivity.
    #[napi(factory)]
    pub fn open_with_idle_timeout(
        data_path: String,
        index_path: String,
        idle_timeout_secs: u32,
    ) -> Result<Self> {
        let inner = ChronDBInner::builder(&data_path, &index_path)
            .idle_timeout(Duration::from_secs(idle_timeout_secs as u64))
            .open()
            .map_err(to_napi_error)?;
        Ok(Self { inner })
    }

    /// Save a document. Returns the saved document as a JSON string.
    #[napi]
    pub fn put(
        &self,
        id: String,
        json_doc: String,
        branch: Option<String>,
    ) -> Result<String> {
        let doc: serde_json::Value =
            serde_json::from_str(&json_doc).map_err(|e| napi::Error::from_reason(e.to_string()))?;
        let result = self.inner.put(&id, &doc, branch.as_deref()).map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Get a document by ID. Returns JSON string.
    #[napi]
    pub fn get(&self, id: String, branch: Option<String>) -> Result<String> {
        let result = self.inner.get(&id, branch.as_deref()).map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Delete a document by ID.
    #[napi]
    pub fn delete(&self, id: String, branch: Option<String>) -> Result<()> {
        self.inner.delete(&id, branch.as_deref()).map_err(to_napi_error)
    }

    /// List documents by ID prefix. Returns JSON array string.
    #[napi]
    pub fn list_by_prefix(&self, prefix: String, branch: Option<String>) -> Result<String> {
        let result = self.inner.list_by_prefix(&prefix, branch.as_deref()).map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// List documents by table name. Returns JSON array string.
    #[napi]
    pub fn list_by_table(&self, table: String, branch: Option<String>) -> Result<String> {
        let result = self.inner.list_by_table(&table, branch.as_deref()).map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Get document history. Returns JSON array string.
    #[napi]
    pub fn history(&self, id: String, branch: Option<String>) -> Result<String> {
        let result = self.inner.history(&id, branch.as_deref()).map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Execute a query. Returns JSON results string.
    #[napi]
    pub fn query(&self, query_json: String, branch: Option<String>) -> Result<String> {
        let query: serde_json::Value = serde_json::from_str(&query_json)
            .map_err(|e| napi::Error::from_reason(e.to_string()))?;
        let result = self.inner.query(&query, branch.as_deref()).map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Execute a SQL query. Returns JSON results string.
    #[napi]
    pub fn execute_sql(&self, sql: String, branch: Option<String>) -> Result<String> {
        let result = self
            .inner
            .execute_sql(&sql, branch.as_deref())
            .map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Exports the repository tree to a filesystem directory.
    #[napi]
    pub fn export_to_directory(
        &self,
        target_dir: String,
        options_json: Option<String>,
    ) -> Result<String> {
        let result = self
            .inner
            .export_to_directory(&target_dir, options_json.as_deref())
            .map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Creates a full backup of the repository.
    #[napi]
    pub fn create_backup(
        &self,
        output_path: String,
        options_json: Option<String>,
    ) -> Result<String> {
        let result = self
            .inner
            .create_backup(&output_path, options_json.as_deref())
            .map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Restores the repository from a backup file.
    #[napi]
    pub fn restore_backup(
        &self,
        input_path: String,
        options_json: Option<String>,
    ) -> Result<String> {
        let result = self
            .inner
            .restore_backup(&input_path, options_json.as_deref())
            .map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Exports the repository to a git bundle snapshot.
    #[napi]
    pub fn export_snapshot(
        &self,
        output_path: String,
        options_json: Option<String>,
    ) -> Result<String> {
        let result = self
            .inner
            .export_snapshot(&output_path, options_json.as_deref())
            .map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Imports a git bundle snapshot into the repository.
    #[napi]
    pub fn import_snapshot(
        &self,
        input_path: String,
        options_json: Option<String>,
    ) -> Result<String> {
        let result = self
            .inner
            .import_snapshot(&input_path, options_json.as_deref())
            .map_err(to_napi_error)?;
        serde_json::to_string(&result).map_err(|e| napi::Error::from_reason(e.to_string()))
    }

    /// Returns the last error message from the native library.
    #[napi]
    pub fn last_error(&self) -> Option<String> {
        self.inner.last_error()
    }
}

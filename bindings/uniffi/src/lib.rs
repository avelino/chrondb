use chrondb::{ChronDB as ChronDBInner, ChronDBError as InnerError};
use std::fmt;
use std::time::Duration;

uniffi::include_scaffolding!("chrondb");

#[derive(Debug)]
pub enum ChronDBError {
    SetupFailed { msg: String },
    IsolateCreationFailed,
    OpenFailed { msg: String },
    CloseFailed,
    NotFound,
    OperationFailed { msg: String },
    JsonError { msg: String },
}

impl fmt::Display for ChronDBError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::SetupFailed { msg } => write!(f, "library setup failed: {msg}"),
            Self::IsolateCreationFailed => write!(f, "failed to create GraalVM isolate"),
            Self::OpenFailed { msg } => write!(f, "failed to open database: {msg}"),
            Self::CloseFailed => write!(f, "failed to close database"),
            Self::NotFound => write!(f, "document not found"),
            Self::OperationFailed { msg } => write!(f, "operation failed: {msg}"),
            Self::JsonError { msg } => write!(f, "JSON error: {msg}"),
        }
    }
}

impl std::error::Error for ChronDBError {}

impl From<InnerError> for ChronDBError {
    fn from(e: InnerError) -> Self {
        match e {
            InnerError::SetupFailed(msg) => Self::SetupFailed { msg },
            InnerError::IsolateCreationFailed => Self::IsolateCreationFailed,
            InnerError::OpenFailed(msg) => Self::OpenFailed { msg },
            InnerError::CloseFailed => Self::CloseFailed,
            InnerError::NotFound => Self::NotFound,
            InnerError::OperationFailed(msg) => Self::OperationFailed { msg },
            InnerError::JsonError(msg) => Self::JsonError { msg },
        }
    }
}

pub struct ChronDB {
    inner: ChronDBInner,
}

impl ChronDB {
    fn open(data_path: String, index_path: String) -> Result<Self, ChronDBError> {
        let inner = ChronDBInner::open(&data_path, &index_path)?;
        Ok(Self { inner })
    }

    fn open_path(db_path: String) -> Result<Self, ChronDBError> {
        let index_path = format!("{}/.chrondb-index", db_path);
        let inner = ChronDBInner::open(&db_path, &index_path)?;
        Ok(Self { inner })
    }

    fn open_with_idle_timeout(
        data_path: String,
        index_path: String,
        idle_timeout_secs: u64,
    ) -> Result<Self, ChronDBError> {
        let inner = ChronDBInner::builder(&data_path, &index_path)
            .idle_timeout(Duration::from_secs(idle_timeout_secs))
            .open()?;
        Ok(Self { inner })
    }

    fn put(
        &self,
        id: String,
        json_doc: String,
        branch: Option<String>,
    ) -> Result<String, ChronDBError> {
        let doc: serde_json::Value =
            serde_json::from_str(&json_doc).map_err(|e| ChronDBError::JsonError {
                msg: e.to_string(),
            })?;
        let result = self.inner.put(&id, &doc, branch.as_deref())?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn get(&self, id: String, branch: Option<String>) -> Result<String, ChronDBError> {
        let result = self.inner.get(&id, branch.as_deref())?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn delete(&self, id: String, branch: Option<String>) -> Result<(), ChronDBError> {
        self.inner.delete(&id, branch.as_deref())?;
        Ok(())
    }

    fn list_by_prefix(
        &self,
        prefix: String,
        branch: Option<String>,
    ) -> Result<String, ChronDBError> {
        let result = self.inner.list_by_prefix(&prefix, branch.as_deref())?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn list_by_table(
        &self,
        table: String,
        branch: Option<String>,
    ) -> Result<String, ChronDBError> {
        let result = self.inner.list_by_table(&table, branch.as_deref())?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn history(&self, id: String, branch: Option<String>) -> Result<String, ChronDBError> {
        let result = self.inner.history(&id, branch.as_deref())?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn query(&self, query_json: String, branch: Option<String>) -> Result<String, ChronDBError> {
        let query: serde_json::Value =
            serde_json::from_str(&query_json).map_err(|e| ChronDBError::JsonError {
                msg: e.to_string(),
            })?;
        let result = self.inner.query(&query, branch.as_deref())?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn execute_sql(&self, sql: String, branch: Option<String>) -> Result<String, ChronDBError> {
        let result = self.inner.execute_sql(&sql, branch.as_deref())?;
        Ok(result.to_string())
    }

    fn setup_remote(&self, remote_url: String) -> Result<String, ChronDBError> {
        let result = self.inner.setup_remote(&remote_url)?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn push(&self) -> Result<String, ChronDBError> {
        let result = self.inner.push()?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn pull(&self) -> Result<String, ChronDBError> {
        let result = self.inner.pull()?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn fetch(&self) -> Result<String, ChronDBError> {
        let result = self.inner.fetch()?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn remote_status(&self) -> Result<String, ChronDBError> {
        let result = self.inner.remote_status()?;
        serde_json::to_string(&result).map_err(|e| ChronDBError::JsonError {
            msg: e.to_string(),
        })
    }

    fn last_error(&self) -> Option<String> {
        self.inner.last_error()
    }
}

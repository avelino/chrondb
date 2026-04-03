export interface ChronDBOptions {
  /** Seconds of inactivity before suspending the GraalVM isolate. */
  idleTimeout?: number;
}

export interface SqlResult {
  type: string;
  columns?: string[];
  rows?: unknown[][];
  count?: number;
  affected?: number;
  tables?: string[];
  message?: string;
}

export class ChronDB {
  /** Open a database with a single directory path (preferred). */
  constructor(dbPath: string, options?: ChronDBOptions);
  /** @deprecated Use single-path constructor instead. */
  constructor(dataPath: string, indexPath: string, options?: ChronDBOptions);

  put(id: string, doc: Record<string, unknown>, branch?: string | null): Record<string, unknown>;
  get(id: string, branch?: string | null): Record<string, unknown>;
  delete(id: string, branch?: string | null): void;
  listByPrefix(prefix: string, branch?: string | null): Record<string, unknown>[];
  listByTable(table: string, branch?: string | null): Record<string, unknown>[];
  history(id: string, branch?: string | null): Record<string, unknown>[];
  query(query: Record<string, unknown>, branch?: string | null): Record<string, unknown>;
  execute(sql: string, branch?: string | null): SqlResult;

  exportToDirectory(targetDir: string, options?: {
    branch?: string;
    prefix?: string;
    format?: 'json' | 'raw';
    decodePaths?: boolean;
    overwrite?: boolean;
  }): Record<string, unknown>;

  createBackup(outputPath: string, options?: {
    format?: 'tar.gz' | 'bundle';
    verify?: boolean;
  }): Record<string, unknown>;

  restoreBackup(inputPath: string, options?: {
    format?: 'tar.gz' | 'bundle';
    verify?: boolean;
  }): Record<string, unknown>;

  exportSnapshot(outputPath: string, options?: {
    refs?: string[];
  }): Record<string, unknown>;

  importSnapshot(inputPath: string): Record<string, unknown>;
}

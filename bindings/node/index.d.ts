export interface ChronDBOptions {
  /** Seconds of inactivity before suspending the GraalVM isolate. */
  idleTimeout?: number;
}

export class ChronDB {
  constructor(dataPath: string, indexPath: string, options?: ChronDBOptions);

  put(id: string, doc: Record<string, unknown>, branch?: string | null): Record<string, unknown>;
  get(id: string, branch?: string | null): Record<string, unknown>;
  delete(id: string, branch?: string | null): void;
  listByPrefix(prefix: string, branch?: string | null): Record<string, unknown>[];
  listByTable(table: string, branch?: string | null): Record<string, unknown>[];
  history(id: string, branch?: string | null): Record<string, unknown>[];
  query(query: Record<string, unknown>, branch?: string | null): Record<string, unknown>;
}

# Language Bindings

ChronDB provides native bindings for multiple programming languages, all generated from a single Rust SDK.

## Architecture: One Codebase, Many Languages

Instead of maintaining separate implementations for each language, ChronDB uses the **Rust SDK as the single source of truth**. All complex logic — worker thread management, GraalVM isolate lifecycle, idle timeout, lock cleanup, shared registry — lives exclusively in Rust. Other language bindings are thin, auto-generated wrappers.

```
libchrondb.so (GraalVM/Clojure — core database engine)
└── Rust SDK (crate chrondb — all logic lives here)
    │
    ├── UniFFI  → Python, Ruby, Kotlin, Swift
    ├── NAPI-RS → Node.js
    └── Native  → Rust (direct crate dependency)
```

### Why this approach?

Every feature or bug fix is implemented **once in Rust** and automatically available in all languages. No more reimplementing idle timeout logic in Python, Ruby, and Node.js separately.

### UniFFI (Mozilla)

[UniFFI](https://github.com/mozilla/uniffi-rs) is an open-source tool created by Mozilla for generating multi-language bindings from Rust. It powers production components in **Firefox** — the same tool that generates Kotlin/Swift bindings for Firefox's Rust networking, cryptography, and sync engines is what generates ChronDB's Python and Ruby bindings.

Key references:
- [UniFFI repository](https://github.com/mozilla/uniffi-rs)
- [UniFFI user guide](https://mozilla.github.io/uniffi-rs/)
- [Mozilla's blog on shipping Rust in Firefox](https://hacks.mozilla.org/2019/02/rewriting-a-browser-component-in-rust/)
- [Application Services](https://github.com/mozilla/application-services) — Mozilla's production project using UniFFI to ship Rust code to Kotlin, Swift, and Python

### NAPI-RS

[NAPI-RS](https://napi.rs/) generates native Node.js addons from Rust. Used in production by projects like [SWC](https://swc.rs/) (the JavaScript/TypeScript compiler), [Rspack](https://rspack.dev/), and [Biome](https://biomejs.dev/).

Key references:
- [NAPI-RS repository](https://github.com/napi-rs/napi-rs)
- [NAPI-RS documentation](https://napi.rs/docs/introduction)

## Available Bindings

| Language | Tool | Status | Package |
|----------|------|--------|---------|
| [Rust](rust.md) | Native crate | Stable | `chrondb` on crates.io |
| [Python](python.md) | UniFFI | Stable | `chrondb` on PyPI |
| [Ruby](ruby.md) | UniFFI | Stable | `chrondb` gem |
| [Node.js](nodejs.md) | NAPI-RS | Stable | `chrondb` on npm |
| [Kotlin](kotlin.md) | UniFFI | Stable | Maven artifact |
| [Swift](swift.md) | UniFFI | Stable | Swift package |

## API Consistency

All bindings expose the same core API:

| Operation | Description |
|-----------|-------------|
| `open_path(db_path)` | Open a database connection (preferred) |
| `open(data_path, index_path)` | Open with separate paths *(deprecated)* |
| `open_with_idle_timeout(data_path, index_path, secs)` | Open with idle timeout |
| `put(id, doc, branch?)` | Save a document |
| `get(id, branch?)` | Get a document by ID |
| `delete(id, branch?)` | Delete a document |
| `list_by_prefix(prefix, branch?)` | List documents by ID prefix |
| `list_by_table(table, branch?)` | List documents by table |
| `history(id, branch?)` | Get change history |
| `query(query, branch?)` | Execute a Lucene query |
| `execute_sql(sql, branch?)` | Execute a SQL query directly (no server needed) |

Each language wrapper adds idiomatic conveniences (context managers in Python, keyword arguments in Ruby, TypeScript types in Node.js) while the core behavior is identical.

## Building Bindings from Source

All bindings require the `libchrondb` native library (GraalVM native-image) and the Rust toolchain.

```bash
# 1. Build the GraalVM native library
clojure -M:shared-lib
native-image @target/shared-image-args

# 2. Build the Rust SDK
cd bindings/rust && cargo build --release

# 3. Generate UniFFI bindings (Python, Ruby, Kotlin, Swift)
cd bindings/uniffi && cargo build --release
cargo run --bin uniffi-bindgen generate \
  --library target/release/libchrondb_uniffi.dylib \
  --language python --language ruby --language kotlin --language swift \
  --out-dir generated/

# 4. Build Node.js binding
cd bindings/node && npm run build
```

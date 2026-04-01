# Contributing to ChronDB

Thank you for your interest in contributing to ChronDB! This guide will help you get started.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java / GraalVM | 17+ | Runtime and native-image compilation |
| Clojure CLI | 1.11+ | Build tool and dependency management |
| Git | 2.25+ | Required by ChronDB's storage engine |

Install Clojure CLI: https://clojure.org/guides/install_clojure

## Development Setup

```bash
# Clone the repository
git clone https://github.com/avelino/chrondb.git
cd chrondb

# Download dependencies
clojure -P

# Run the full server (REST + Redis + SQL)
clojure -M:run

# Run individual protocols
clojure -M:run-rest    # REST API only (port 3000)
clojure -M:run-redis   # Redis only (port 6379)
clojure -M:run-sql     # PostgreSQL only (port 5432)
```

Verify it's working:

```bash
# REST API
curl http://localhost:3000/healthz

# Redis
redis-cli -p 6379 PING

# PostgreSQL
psql -h localhost -p 5432 -U chrondb -c "SHOW TABLES"
```

## Running Tests

```bash
# Full test suite (recommended before submitting a PR)
clojure -M:test

# Core logic only (faster, runs in parallel)
clojure -M:test-non-external-protocol

# Protocol-specific tests (run sequentially due to port binding)
clojure -M:test-redis-sequential
clojure -M:test-sql-only

# Benchmarks
clojure -M:benchmark
```

## Linting and Formatting

```bash
# Static analysis with clj-kondo
clojure -M:lint

# Check formatting
clojure -M:fmt

# Auto-fix formatting
clojure -M:fmt-fix
```

Run both lint and format checks before submitting a PR.

## Project Structure

```
src/chrondb/
  core.clj              # Entry point and CLI dispatcher
  config.clj            # Configuration loader
  api/                  # Protocol implementations (REST, Redis, SQL)
  storage/              # Git-based storage layer
  index/                # Lucene search index
  query/                # Query AST builders
  transaction/          # Transaction context and metadata
  wal/                  # Write-Ahead Log
  backup/               # Backup and restore
  validation/           # JSON Schema validation
  observability/        # Health checks and metrics
  lib/                  # FFI entry points for native bindings
test/chrondb/           # Test suite (mirrors src structure)
bindings/               # Language bindings (Rust, Python, Node.js)
docs/                   # Documentation (served at chrondb.avelino.run)
```

For a detailed architecture overview, see [Architecture](docs/architecture.md).

## Code Style

- **Naming**: `kebab-case` for all vars and namespaces
- **Namespaces**: follow `chrondb.*` convention
- **Docstrings**: required for all public functions
- **Functions**: should do one thing well, max ~50 lines
- **Error handling**: fail fast with structured context (include what, where, and relevant data)
- **Formatting**: enforced by `cljfmt` — run `clojure -M:fmt-fix` before committing
- **Immutability**: never rewrite Git commits; create new ones

## Making Changes

### Common Patterns

**Adding a Redis command:**
1. Add the handler function in `src/chrondb/api/redis/core.clj`
2. Register it in the command dispatcher
3. Add tests in `test/chrondb/api/redis/`
4. Update `docs/protocols.md` with the new command

**Adding a SQL feature:**
1. Extend the parser in `src/chrondb/api/sql/parser/`
2. Add execution logic in `src/chrondb/api/sql/execution/`
3. Add tests in `test/chrondb/api/sql/`
4. Update `docs/protocols.md` with the new syntax

**Adding a REST endpoint:**
1. Add the handler in `src/chrondb/api/v1.clj`
2. Add the route in `src/chrondb/api/v1/routes.clj`
3. Add tests in `test/chrondb/api/`
4. Update `docs/protocols.md` and `docs/examples-rest.md`

**Adding an FFI operation:**
1. Add the Clojure function in `src/chrondb/lib/core.clj`
2. Add the Java bridge method in `java/chrondb/lib/ChronDBLib.java`
3. Update the UniFFI definition in `bindings/uniffi/src/chrondb.udl`
4. Update each binding (Rust, Python, Node.js)
5. Add tests in binding test directories

### Building Native Image

```bash
# Generate GraalVM args
clojure -M:shared-lib

# Build native library
native-image @target/shared-image-args -H:Name=libchrondb
```

## Pull Request Process

1. Create a feature branch from `main`
2. Make your changes with clear, focused commits
3. Ensure all tests pass: `clojure -M:test`
4. Ensure code is formatted: `clojure -M:fmt`
5. Ensure linting passes: `clojure -M:lint`
6. Open a PR with a clear description of what changed and why
7. Link any related issues

### PR Checklist

- [ ] Tests added/updated for new behavior
- [ ] All existing tests pass
- [ ] Code formatted with `cljfmt`
- [ ] No linting warnings
- [ ] Documentation updated (if user-facing changes)
- [ ] Consistent behavior across REST, Redis, and SQL protocols (if applicable)

## Questions?

- **Issues**: https://github.com/avelino/chrondb/issues
- **Discussions**: https://github.com/avelino/chrondb/discussions
- **Documentation**: https://chrondb.avelino.run

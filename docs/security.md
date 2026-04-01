# Security Best Practices

This guide covers security considerations for deploying ChronDB in production environments.

## Network Security

### Bind to Specific Interfaces

By default, ChronDB binds to `0.0.0.0` (all interfaces). In production, bind to specific interfaces:

```clojure
:servers {
  :rest {:host "127.0.0.1" :port 3000}       ;; Localhost only
  :redis {:host "10.0.0.5" :port 6379}       ;; Internal network only
  :postgresql {:host "10.0.0.5" :port 5432}  ;; Internal network only
}
```

### Disable Unused Protocols

Only enable the protocols your application uses:

```bash
# REST API only
clojure -M:run --disable-redis --disable-sql

# Redis only
clojure -M:run --disable-rest --disable-sql
```

Or in `config.edn`:

```clojure
:servers {
  :rest {:enabled true}
  :redis {:enabled false}
  :postgresql {:enabled false}
}
```

### Firewall Rules

Restrict access to ChronDB ports from trusted networks only:

```bash
# Allow REST API only from internal network
iptables -A INPUT -p tcp --dport 3000 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 3000 -j DROP

# Allow PostgreSQL from application servers only
iptables -A INPUT -p tcp --dport 5432 -s 10.0.1.0/24 -j ACCEPT
iptables -A INPUT -p tcp --dport 5432 -j DROP
```

### TLS Termination

ChronDB does not handle TLS directly. Use a reverse proxy for HTTPS:

**nginx example:**

```nginx
server {
    listen 443 ssl;
    server_name chrondb.internal;

    ssl_certificate /etc/ssl/certs/chrondb.crt;
    ssl_certificate_key /etc/ssl/private/chrondb.key;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

For the Redis and PostgreSQL protocols, use [stunnel](https://www.stunnel.org/) or a similar TLS wrapper if encryption in transit is required.

---

## Authentication

### PostgreSQL Protocol

The PostgreSQL wire protocol supports username/password authentication:

```clojure
:servers {
  :postgresql {
    :enabled true
    :port 5432
    :username "chrondb"
    :password "a-strong-password-here"
  }
}
```

Change the default credentials before deploying to production.

### REST API

The REST API does not include built-in authentication. Implement authentication at the reverse proxy layer:

- **Basic Auth**: via nginx `auth_basic` or similar
- **OAuth2/JWT**: via an API gateway (Kong, Envoy, AWS ALB)
- **mTLS**: mutual TLS at the proxy layer for service-to-service communication

### Redis Protocol

The Redis protocol does not currently support the `AUTH` command. Protect it via network isolation (bind to internal interfaces, firewall rules).

---

## File System Permissions

### Data Directory

The data directory contains the Git repository and Lucene index. Restrict access:

```bash
# Create dedicated user
sudo useradd --system --no-create-home chrondb

# Set ownership
sudo chown -R chrondb:chrondb /var/lib/chrondb/data

# Restrict permissions (owner only)
sudo chmod -R 700 /var/lib/chrondb/data
```

### Configuration File

The configuration file may contain credentials (PostgreSQL password, SSH key paths):

```bash
sudo chown chrondb:chrondb /etc/chrondb/config.edn
sudo chmod 600 /etc/chrondb/config.edn
```

---

## Docker Security

The official Docker image follows security best practices:

- **Non-root user**: runs as the `chrondb` user (not root)
- **Minimal base image**: Debian 12 slim with only required dependencies
- **No shell access needed**: entry point is the compiled binary

### Additional Hardening

```yaml
services:
  chrondb:
    image: ghcr.io/avelino/chrondb:latest
    read_only: true
    tmpfs:
      - /tmp:size=100M
    volumes:
      - chrondb-data:/app/data
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
```

### Kubernetes Security Context

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
```

---

## Remote Git Security

When using remote Git synchronization, ChronDB connects to a remote repository. Secure this connection:

### SSH Keys

Use deploy keys with minimal permissions:

```bash
# Generate a dedicated key pair
ssh-keygen -t ed25519 -f /etc/chrondb/deploy_key -N "" -C "chrondb-deploy"

# Add the public key to your Git hosting as a deploy key (read/write)
```

Configure in `config.edn`:

```clojure
:git {
  :remote-url "git@github.com:your-org/chrondb-data.git"
  :ssh {:ssh-dir "/etc/chrondb/.ssh"
        :auth-methods "publickey"}
}
```

### Key Permissions

```bash
chmod 700 /etc/chrondb/.ssh
chmod 600 /etc/chrondb/.ssh/deploy_key
chmod 644 /etc/chrondb/.ssh/deploy_key.pub
chown -R chrondb:chrondb /etc/chrondb/.ssh
```

### Repository Access

- Use a **dedicated repository** for ChronDB data (not a shared repo)
- Grant **minimal permissions** (deploy key with write access only to the data repo)
- Enable **branch protection** on the remote to prevent force pushes
- Enable **audit logging** on your Git hosting to track access

---

## Backup Security

### Backup Storage

- Store backups in a different location than the primary data (different disk, cloud storage)
- Encrypt backups at rest if they contain sensitive data:

```bash
# Create and encrypt a backup
clojure -M:run backup --output /tmp/backup.tar.gz
gpg --symmetric --cipher-algo AES256 /tmp/backup.tar.gz
rm /tmp/backup.tar.gz

# Decrypt before restoring
gpg --decrypt backup.tar.gz.gpg > backup.tar.gz
clojure -M:run restore --input backup.tar.gz
```

### Backup Access Control

- Restrict who can trigger backup/restore via the REST API
- The `/api/v1/backup` and `/api/v1/restore` endpoints should be behind authentication
- Consider disabling these endpoints in production and using CLI-based backups instead

---

## Audit Trail

ChronDB's Git-based storage provides a built-in audit trail:

- Every write creates an immutable Git commit with timestamp and metadata
- Transaction metadata (user, origin, flags) is stored in Git notes (`refs/notes/chrondb`)
- History cannot be altered without detection (Git hash chain integrity)

### Inspecting the Audit Trail

```bash
# View commit history with notes
cd data/ && git log --show-notes=chrondb

# View metadata for a specific commit
cd data/ && git notes --ref=chrondb show <commit-hash>

# Via SQL
SELECT * FROM chrondb_history('user', '1');
```

### Enriching the Audit Trail

Always set transaction metadata headers on write operations:

```bash
curl -X POST http://localhost:3000/api/v1/save \
  -H "Content-Type: application/json" \
  -H "X-ChronDB-User: alice@example.com" \
  -H "X-ChronDB-Origin: admin-panel" \
  -H "X-ChronDB-Flags: manual-edit" \
  -H "X-Request-Id: req-abc-123" \
  -d '{"id":"user:1","name":"Alice"}'
```

---

## Security Monitoring

### What to Monitor

| Signal | Metric / Log | Action |
|--------|-------------|--------|
| High error rate | `chrondb_occ_conflicts_total` | May indicate concurrent modification attacks |
| Unusual write volume | `chrondb_documents_saved_total` spike | May indicate unauthorized bulk writes |
| Failed connections | Application logs | May indicate port scanning or brute force |
| Disk space exhaustion | `/health` disk check | May indicate DoS via document flooding |
| WAL backlog | `chrondb_wal_pending_entries` | May indicate storage issues or attack |

### Log Review

Regularly review ChronDB logs for:
- Unknown client connections
- Unusual query patterns
- Repeated errors on specific documents
- Operations from unexpected origins (check Git notes)

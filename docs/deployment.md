# Deployment Guide

This guide covers deploying ChronDB in production environments, from single-node Docker setups to Kubernetes clusters.

## Docker

### Single Container

```bash
docker run -d \
  --name chrondb \
  -p 3000:3000 \
  -p 6379:6379 \
  -p 5432:5432 \
  -v chrondb-data:/app/data \
  ghcr.io/avelino/chrondb:latest
```

### Docker Compose

A `docker-compose.yml` is provided at the repository root for local development and simple deployments:

```bash
docker compose up -d
```

This starts ChronDB with:
- Persistent data volume (`chrondb-data`)
- All three protocols exposed (REST 3000, Redis 6379, PostgreSQL 5432)
- Health check on `/healthz` every 10 seconds
- Automatic restart on failure
- 1 GB memory limit

Verify the service is running:

```bash
curl http://localhost:3000/healthz
# {"status":"healthy","timestamp":"..."}
```

### Custom Configuration

Mount a custom `config.edn` to override defaults:

```bash
docker run -d \
  --name chrondb \
  -p 3000:3000 \
  -v chrondb-data:/app/data \
  -v ./config.edn:/app/config.edn \
  ghcr.io/avelino/chrondb:latest --config /app/config.edn
```

See [Configuration](configuration) for all available options.

### Docker Image Details

- **Base**: Debian 12 slim (runtime stage)
- **Build**: GraalVM native-image (multi-stage)
- **User**: Non-root `chrondb` user
- **Ports**: 3000 (REST), 6379 (Redis), 5432 (PostgreSQL)
- **Entry point**: `/usr/local/bin/chrondb`

---

## Kubernetes

### StatefulSet

ChronDB stores data on disk (Git repository + Lucene index), so it should be deployed as a StatefulSet with persistent storage.

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: chrondb
  labels:
    app: chrondb
spec:
  serviceName: chrondb
  replicas: 1
  selector:
    matchLabels:
      app: chrondb
  template:
    metadata:
      labels:
        app: chrondb
    spec:
      containers:
        - name: chrondb
          image: ghcr.io/avelino/chrondb:latest
          ports:
            - name: rest
              containerPort: 3000
            - name: redis
              containerPort: 6379
            - name: postgresql
              containerPort: 5432
          volumeMounts:
            - name: data
              mountPath: /app/data
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
          livenessProbe:
            httpGet:
              path: /healthz
              port: rest
            initialDelaySeconds: 15
            periodSeconds: 10
            timeoutSeconds: 5
          readinessProbe:
            httpGet:
              path: /readyz
              port: rest
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
          startupProbe:
            httpGet:
              path: /startupz
              port: rest
            failureThreshold: 30
            periodSeconds: 5
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
```

### Service

Expose ChronDB to other pods in the cluster:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: chrondb
  labels:
    app: chrondb
spec:
  selector:
    app: chrondb
  ports:
    - name: rest
      port: 3000
      targetPort: rest
    - name: redis
      port: 6379
      targetPort: redis
    - name: postgresql
      port: 5432
      targetPort: postgresql
```

For external access, create an additional LoadBalancer or Ingress resource targeting the service.

### ConfigMap

Store your `config.edn` in a ConfigMap:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: chrondb-config
data:
  config.edn: |
    {:git {:committer-name "ChronDB"
           :committer-email "chrondb@example.com"
           :default-branch "main"
           :push-enabled false}
     :storage {:data-dir "/app/data"}
     :logging {:level :info
               :output :stdout}}
```

Mount it in the StatefulSet:

```yaml
volumes:
  - name: config
    configMap:
      name: chrondb-config
containers:
  - name: chrondb
    args: ["--config", "/etc/chrondb/config.edn"]
    volumeMounts:
      - name: config
        mountPath: /etc/chrondb
```

### Prometheus ServiceMonitor

If you use Prometheus Operator, create a ServiceMonitor to scrape ChronDB metrics:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: chrondb
  labels:
    app: chrondb
spec:
  selector:
    matchLabels:
      app: chrondb
  endpoints:
    - port: rest
      path: /metrics
      interval: 15s
```

---

## Resource Guidance

### Memory

| Documents | Recommended Memory |
|-----------|-------------------|
| < 10,000 | 512 MB |
| 10,000 - 100,000 | 1 - 2 GB |
| 100,000 - 1,000,000 | 2 - 4 GB |
| > 1,000,000 | 4+ GB |

Memory usage is driven by:
- Lucene index segment buffers (in-memory during writes)
- Git object cache (recently accessed documents)
- JVM heap for query processing (sorts, aggregations, joins)

### Disk

Disk usage grows with both document count and history depth. Every write creates a Git commit, so a document updated 100 times uses ~100x the space of its current size. Run `git gc` periodically (via the `compact` command) to optimize storage.

Rule of thumb: allocate **3-5x** the expected current dataset size for history and Git overhead.

### CPU

ChronDB is mostly I/O bound. A single core handles typical workloads. Allocate additional cores for:
- Concurrent query processing
- Lucene index merges (background)
- Git operations (push/pull to remotes)

---

## Production Checklist

### Data Persistence

- [ ] Data directory is on persistent storage (not ephemeral)
- [ ] Volume is backed up regularly (see [Operations Guide](operations))
- [ ] Disk space monitoring is configured with alerts

### Monitoring

- [ ] Health check endpoint (`/healthz`) is monitored
- [ ] Prometheus scraping is configured for `/metrics`
- [ ] Alerts are set for `chrondb_wal_pending_entries > 100` (WAL backlog)
- [ ] Alerts are set for disk space usage > 80%
- [ ] Alerts are set for memory usage > 85%

### Security

- [ ] ChronDB is not exposed to the public internet without a reverse proxy
- [ ] PostgreSQL protocol has authentication configured (username/password)
- [ ] Data directory has restricted file permissions
- [ ] Remote Git sync uses SSH keys (not passwords)
- [ ] See [Security Best Practices](security) for full guidance

### Backups

- [ ] Automated backup schedule is configured
- [ ] Backup restore has been tested
- [ ] Backups are stored off-host (different disk, S3, etc.)

### Network

- [ ] Only needed protocols are enabled (disable unused ones in `config.edn`)
- [ ] Ports are firewalled to trusted networks
- [ ] Consider a reverse proxy (nginx, Envoy) for REST API TLS termination

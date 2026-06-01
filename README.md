# ☁️ CloudVault

> A full-stack, AWS S3-compatible cloud object storage platform with intelligent lifecycle management, virus scanning, and fine-grained billing.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Quick Start with Docker Compose](#quick-start-with-docker-compose)
  - [Manual Setup](#manual-setup)
    - [Backend Setup](#backend-setup)
    - [Frontend Setup](#frontend-setup)
- [Configuration Reference](#configuration-reference)
- [API Reference](#api-reference)
- [Storage Classes & Lifecycle Tiers](#storage-classes--lifecycle-tiers)
- [Billing Model](#billing-model)
- [Security](#security)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)

---

## Overview

CloudVault is a self-hosted cloud storage platform inspired by AWS S3. It provides users with bucket-based object storage, complete with intelligent storage-class lifecycle management, automated billing, virus scanning on upload, object versioning, and an audit trail — all accessible through a modern React dashboard.

---

## Features

### 🗄️ Storage
- Create and manage **named buckets** with descriptions and storage classes
- Upload, download, delete, and **paginate** objects within buckets
- **Custom object keys** / folder-path simulation
- **Presigned URL generation** for time-limited access (configurable expiry)
- Object **tag-based filtering** across buckets
- Asynchronous upload jobs with real-time progress tracking
- Files under 100 MB are processed synchronously; files at or above 100 MB are automatically routed through the async upload pipeline and tracked via a job ID

### 🔄 Lifecycle Management
- Four-tier automatic lifecycle system: **Standard → Warm → Instant Glacier → Deep Glacier**
- **Predefined policy**: auto-downgrades buckets on inactivity, auto-upgrades on access surges
- **Custom policy**: user-defined transition rules per bucket
- **Restore requests** (Expedited / Standard / Bulk) for Glacier-tier objects
- Minimum storage duration enforcement with penalty billing
- Scheduled daily/hourly lifecycle evaluation via Spring cron jobs

### 🦠 Virus Scanning
- ClamAV integration — every uploaded file is scanned before storage commit
- Scanning uses the INSTREAM TCP protocol — file is streamed in 128 KB chunks directly to the ClamAV daemon with no intermediate disk write and no memory pressure regardless of file size
- Blocking modal alert on malware detection; file is rejected and never stored
- For async uploads (≥ 100 MB), the file is uploaded to a temporary MinIO prefix, scanned, then moved to its final key on success or deleted on detection

### 💰 Billing
- Monthly invoice generation (runs at midnight on the 1st of each month)
- Itemised per-bucket charges: storage capacity, Class A/B requests, bandwidth, data retrieval, archive restore, minimum-duration penalties
- Tiered bandwidth pricing (4 tiers based on egress GB)
- Versioning surcharges on non-current object versions
- Current-month usage estimation exposed in the dashboard
- Invoice history with PAID / GENERATED status tracking

### 🔐 Security
- JWT-based stateless authentication (access token + refresh token)
- BCrypt password hashing
- Per-request rate limiting via **Bucket4j**

### 🗃️ Versioning & Tagging
- Enable / disable versioning per bucket
- List all versions of an object; download or restore a specific version
- Each version stored as a separate MinIO object with a unique UUID-suffixed key
- Attach arbitrary key–value tags to objects for metadata and cross-bucket filtering

### 📊 Observability
- Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/info`, `/actuator/metrics`)
- Full audit log of every operation (bucket creation, object upload/delete, lifecycle transitions, etc.)
- Hikari connection pool with leak detection

### 💾 Backup
- On-demand and scheduled MySQL dump backups stored and pushed to a dedicated MinIO backup bucket
- MinIO objects are copied server-side 
- Configurable retention period (default: 30 days)

---

## Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                        Browser (React SPA)                        │
│   React 19 + Vite 8 + TailwindCSS 4 + React Router 7 + Recharts   │
└────────────────────────────┬──────────────────────────────────────┘
                             │  REST / JSON  (Axios)
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│              Spring Boot 3.2  (storage-engine)                    │
│                                                                   │
│  Controllers  →  Services  →  Repositories (Spring Data JPA)      │
│                                                                   │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌────────────────┐    │
│  │ Security │  │  Billing │  │ Lifecycle │  │  Async Upload  │    │
│  │ JWT+RBAC │  │Scheduler │  │ Scheduler │  │   + ClamAV     │    │
│  └──────────┘  └──────────┘  └───────────┘  └────────────────┘    │
└──────┬───────────────────┬───────────────────────┬───────────────┘
       │                   │                       │
       ▼                   ▼                       ▼
  ┌─────────┐        ┌──────────┐           ┌──────────┐
  │  MySQL  │        │  MinIO   │           │  Redis   │
  │  (JPA)  │        │  (S3 API)│           │  Cache   │
  └─────────┘        └──────────┘           └──────────┘
                           │
                     ┌──────────┐
                     │  ClamAV  │
                     │ (TCP 3310│
                     └──────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 8, TailwindCSS 4, React Router 7, Recharts, Axios, Lucide React |
| Backend | Spring Boot 3.2, Spring Security, Spring Data JPA, Spring Cache |
| Database | MySQL 8 (via Hikari pool) |
| Object Storage | MinIO (AWS S3-compatible) |
| Caching | Redis |
| Auth | JWT (JJWT 0.12.5), BCrypt |
| Rate Limiting | Bucket4j 8.10 |
| Virus Scanning | ClamAV (via TCP INSTREAM socket) |
| Build Tools | Maven (backend), Vite (frontend) |
| Runtime | Java 17, Node.js 18+ (frontend dev) |

---

## Getting Started

### Prerequisites

| Dependency | Minimum Version | Notes |
|---|---|---|
| Java | 17 | Required for backend |
| Maven | 3.8+ | Or use included `mvnw` wrapper |
| Node.js | 18+ | For frontend dev server |
| MySQL | 8.0 | Database auto-created on first run |
| MinIO | Latest | Run locally or use Docker |
| Redis | 7+ | Used for caching and rate limiting |
| ClamAV | 1.0+ | `clamd` must be running on port 3310 |
| Docker | 20.10+ | Optional but strongly recommended |

---

### Quick Start with Docker Compose

The fastest way to get all dependencies running is with Docker Compose.

**1. Create `docker-compose.yml`** in the project root:

```yaml
version: "3.9"

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: your_password
      MYSQL_DATABASE: storage_billing
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  clamav:
    image: clamav/clamav:stable
    ports:
      - "3310:3310"
    volumes:
      - clamav_data:/var/lib/clamav

volumes:
  mysql_data:
  minio_data:
  clamav_data:
```

**2. Start all dependencies:**

```bash
docker-compose up -d
```

> **Note:** ClamAV downloads virus definitions on first start. This can take 2–5 minutes.
>  The backend will return `503` on `/api/admin/health/clamav` until definitions are ready. You can still use the platform;
>  uploads will fail the scan check until ClamAV is ready.

**3. Create the MinIO backup bucket** (required for backup feature):

```bash
# Using MinIO CLI (mc)
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/backup-storage
```

**4. Configure and run the backend** (see [Backend Setup](#backend-setup) below).

---

### Manual Setup

#### Backend Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/CloudVault.git
   cd CloudVault/storage-engine
   ```

2. **Configure application properties**
   ```bash
   cp src/main/resources/application.properties.example \
      src/main/resources/application.properties
   ```
   Edit `application.properties` — at minimum update:
   - `spring.datasource.username` / `password`
   - `minio.endpoint`, `minio.access-key`, `minio.secret-key`
   - `jwt.secret` (use a strong random string, ≥ 32 chars)
   - `clamav.host` / `port`
   - `spring.data.redis.host` / `port`

3. **Start dependencies** (if not using Docker Compose):
   ```bash
   # MinIO
   docker run -p 9000:9000 -p 9001:9001 \
     -e MINIO_ROOT_USER=your_access_key \
     -e MINIO_ROOT_PASSWORD=your_secret_key \
     minio/minio server /data --console-address ":9001"

   # Redis
   docker run -p 6379:6379 redis:7-alpine

   # ClamAV
   docker run -p 3310:3310 clamav/clamav:stable
   ```

4. **Run the backend**
   ```bash
   ./mvnw spring-boot:run
   # Server starts on http://localhost:8080
   # Swagger UI available at http://localhost:8080/swagger-ui.html
   ```

#### Frontend Setup

```bash
cd CloudVault/frontend
npm install
npm run dev
# Dev server starts on http://localhost:5173
```

---

## Configuration Reference

All configuration lives in `storage-engine/src/main/resources/application.properties`.

| Section | Key Properties |
|---|---|
| **Server** | `server.port=8080` |
| **MySQL** | `spring.datasource.url`, `username`, `password` |
| **JPA** | `spring.jpa.hibernate.ddl-auto=update` (schema auto-managed) |
| **MinIO** | `minio.endpoint`, `minio.access-key`, `minio.secret-key`, `minio.bucket` |
| **JWT** | `jwt.secret` (≥ 32 chars), `jwt.expiration` (ms), `jwt.refresh-expiration` (ms) |
| **Redis** | `spring.data.redis.host`, `port`, `spring.cache.redis.time-to-live` |
| **ClamAV** | `clamav.host`, `clamav.port`, `clamav.enabled`, `clamav.timeout` |
| **Rate Limiting** | `app.rate-limiting.enabled` |
| **Async Upload** | `spring.servlet.multipart.max-file-size=-1`, `max-request-size=-1` |
| **Billing** | `billing.storage.*`, `billing.requests.*`, `billing.bandwidth.*`, `billing.cron` |
| **Lifecycle** | `lifecycle.rate.*`, `lifecycle.retrieval.*`, `lifecycle.predefined.*` |
| **Backup** | `backup.local-path`, `backup.bucket`, `backup.retention-days` |

> Set `spring.servlet.multipart.max-file-size=-1` and `max-request-size=-1` to remove Spring's default 1 MB upload limit.
> CloudVault handles large files via its own async pipeline.

---

## API Reference

All endpoints are under `http://localhost:8080`. Protected endpoints require `Authorization: Bearer <token>`.

### Authentication

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user | Public |
| `POST` | `/api/auth/login` | Obtain access + refresh tokens | Public |
| `POST` | `/api/auth/refresh` | Exchange refresh token for new access token | Public |
| `POST` | `/api/auth/logout` | Revoke refresh token | Bearer |

### Buckets

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/storage/buckets` | List user's buckets | Bearer |
| `POST` | `/api/storage/buckets` | Create a bucket | Bearer |
| `DELETE` | `/api/storage/buckets/{name}` | Delete a bucket | Bearer |

### Objects

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/storage/buckets/{name}/objects` | List objects (paginated) | Bearer |
| `POST` | `/api/storage/buckets/{name}/objects` | Upload an object (auto async ≥ 100 MB) | Bearer |
| `GET` | `/api/storage/buckets/{name}/objects/{key}/download` | Download object | Bearer |
| `GET` | `/api/storage/buckets/{name}/objects/{key}/presign` | Get presigned URL | Bearer |
| `DELETE` | `/api/storage/buckets/{name}/objects/{key}` | Delete object | Bearer |
| `POST` | `/api/storage/buckets/{name}/objects/delete-bulk` | Bulk delete objects | Bearer |
| `POST` | `/api/storage/buckets/{name}/objects/{key}/copy` | Copy object to another bucket | Bearer |

### Async Upload Jobs

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/storage/upload-jobs/{jobId}` | Poll async upload status | Bearer |
| `GET` | `/api/storage/upload-jobs` | List all upload jobs | Bearer |

### Tagging

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `PUT` | `/api/storage/buckets/{name}/objects/{key}/tags` | Set tags on object | Bearer |
| `GET` | `/api/storage/buckets/{name}/objects/{key}/tags` | Get object tags | Bearer |
| `DELETE` | `/api/storage/buckets/{name}/objects/{key}/tags/{tagKey}` | Remove a specific tag | Bearer |
| `GET` | `/api/storage/objects/filter` | Filter objects by tag across all buckets | Bearer |

### Versioning

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/versioning/buckets/{name}/enable` | Enable versioning on bucket | Bearer |
| `GET` | `/api/versioning/buckets/{name}/versions` | List all versions of an object | Bearer |
| `GET` | `/api/versioning/buckets/{name}/versions/download` | Download a specific version | Bearer |
| `DELETE` | `/api/versioning/buckets/{name}/versions/{versionNumber}` | Delete a specific version | Bearer |

### Lifecycle

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/lifecycle/buckets/{name}/status` | Current tier, days in tier, next transition | Bearer |
| `GET` | `/api/lifecycle/buckets/{name}/policy` | Get active lifecycle policy | Bearer |
| `POST` | `/api/lifecycle/buckets/{name}/policy` | Set predefined or custom policy | Bearer |
| `GET` | `/api/lifecycle/buckets/{name}/history` | Tier transition audit log | Bearer |
| `POST` | `/api/lifecycle/buckets/{name}/restore` | Request object restore from DEEP_GLACIER | Bearer |
| `GET` | `/api/lifecycle/buckets/{name}/restore/status` | Check restore completion | Bearer |

### Billing

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/billing/usage/current` | Current month estimated charges | Bearer |
| `GET` | `/api/billing/usage/{year}/{month}` | Historical usage for a month | Bearer |
| `GET` | `/api/billing/invoices` | Invoice history | Bearer |
| `GET` | `/api/billing/invoices/{id}` | Invoice detail with per-bucket breakdown | Bearer |
| `POST` | `/api/billing/invoices/generate` | Manually trigger invoice generation | Bearer |

### Audit

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/audit/logs` | All operations (paginated) | Bearer |
| `GET` | `/api/audit/logs/bucket/{name}` | Operations on a specific bucket | Bearer |
| `GET` | `/api/audit/logs/object` | Operations on a specific object key | Bearer |

### Admin

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/admin/overview` | Platform stats (users, storage, revenue) | Admin |
| `GET` | `/api/admin/users` | List all users | Admin |
| `PUT` | `/api/admin/users/{userId}/quota` | Set user storage quota | Admin |
| `POST` | `/api/admin/billing/run/{year}/{month}` | Force billing run for a month | Admin |
| `POST` | `/api/admin/cache/clear` | Evict all caches | Admin |
| `GET` | `/api/admin/health/clamav` | ClamAV daemon health + definition version | Admin |

### Backup

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/admin/backups/health` | Backup system health check | Public |
| `POST` | `/api/admin/backups/trigger` | Trigger full backup (DB + MinIO) | Admin |
| `GET` | `/api/admin/backups/list` | List all completed backups | Admin |
| `GET` | `/api/admin/backups/info/{backupId}` | Backup metadata and size | Admin |
| `POST` | `/api/admin/backups/restore/{backupId}` | Initiate restore from backup | Admin |

> Interactive API documentation is available at **`http://localhost:8080/swagger-ui.html`** when the backend is running.

---

## Storage Classes & Lifecycle Tiers

### Storage Classes (Bucket-level, S3-style)

| Class | Price/GB/month | Use Case |
|---|---|---|
| `STANDARD` | $0.023 | Frequent access |
| `VAULT` | $0.0125 | Infrequent access |
| `COLD_VAULT` | $0.004 | Rare access |
| `ARCHIVE` | $0.002 | Long-term archival |
| `SMART_TIER` | Varies | Auto-tiered |

### Lifecycle Tiers (Dynamic, auto-managed)

| Tier | Price/GB/month | Retrieval Fee | Min Duration | Access Speed |
|---|---|---|---|---|
| `STANDARD` | $0.023 | None | None | Immediate |
| `WARM` | $0.0125 | $0.01/GB | 30 days | Immediate |
| `INSTANT_GLACIER` | $0.004 | $0.03/GB | 90 days | Immediate |
| `DEEP_GLACIER` | $0.00099 | Restore required | 180 days | 1–10 min (restore) |

**Downgrade schedule (predefined policy):**

| From Tier | To Tier | Inactivity Threshold |
|---|---|---|
| STANDARD | WARM | 30 days |
| WARM | INSTANT_GLACIER | 60 days |
| INSTANT_GLACIER | DEEP_GLACIER | 90 days |

**Upgrade thresholds (all policies):**

| Current Tier | Upgrade To | Trigger |
|---|---|---|
| DEEP_GLACIER | INSTANT_GLACIER | More than 1 request in 180-day window |
| INSTANT_GLACIER | WARM | More than 3 requests in 30-day window |
| WARM | STANDARD | More than 6 requests in 30-day window |

---

## Billing Model

Monthly invoices are broken down per bucket and include:

| Charge | Basis |
|---|---|
| Storage capacity | GB-months consumed at current tier rate |
| Class A requests | PUT, POST, DELETE — $0.005 per 1,000 |
| Class B requests | GET, HEAD — $0.0004 per 10,000 |
| Bandwidth egress | Tiered by total monthly egress GB (see table below) |
| Data retrieval | Applies to WARM and GLACIER tiers on download |
| Archive restore | Varies by restore speed (see table below) |
| Versioning | Non-current versions: 10% storage discount, 20% download surcharge |
| Min-duration penalty | Charged when objects are deleted before minimum storage period |

### Bandwidth Tiers

| Monthly Egress | Rate |
|---|---|
| First 50 TB | $0.09 / GB |
| Next 100 TB (50–150 TB) | $0.085 / GB |
| Next 350 TB (150–500 TB) | $0.07 / GB |
| Over 500 TB | $0.05 / GB |

### DEEP_GLACIER Restore Speeds

| Speed | Storage Cost | Per-Request Fee | Access Window | Typical Use |
|---|---|---|---|---|
| Expedited | $0.03 / GB | $0.01 | 24 hours | Urgent recovery |
| Standard | $0.02 / GB | $0.0025 | 72 hours | Routine access |
| Bulk | $0.0025 / GB | None | 7 days | Large batch restores |

Restore fees are charged immediately on request submission.

---

## Security

- **Authentication**: Stateless JWT — short-lived access token (15 min) + long-lived refresh token (7 days)
- **Password storage**: BCrypt hashing (10 rounds)
- **Authorization**: Role-based — `USER` (default) and `ADMIN`
- **Account lockout**: Locks after 5 consecutive failed logins; auto-unlocks after 30 minutes
- **Rate limiting**: Bucket4j token-bucket algorithm applied at servlet filter level to write operations
- **CORS**: Configurable allowed origins (wildcard in development only)
- **Virus scanning**: ClamAV scans every upload before it is committed to MinIO; infected files are never persisted
- **Tenant isolation**: Every object is stored under a unique `tenantId` prefix — users cannot access each other's data even if bucket names collide

---

## Project Structure

```
CloudVault/
├── frontend/                          # React SPA
│   ├── src/
│   │   ├── api/                       # Axios API client modules
│   │   │   ├── storage.js             # Bucket & object operations
│   │   │   ├── billing.js             # Billing & invoice endpoints
│   │   │   ├── lifecycle.js           # Lifecycle policy & restore
│   │   │   ├── versioning.js          # Object versioning
│   │   │   ├── tagging.js             # Object tagging
│   │   │   ├── audit.js               # Audit log
│   │   │   ├── auth.js                # Login / register
│   │   │   └── admin.js               # Admin endpoints
│   │   ├── components/                # Shared UI components
│   │   │   ├── Sidebar.jsx
│   │   │   ├── UploadProgressPopup.jsx
│   │   │   ├── VirusScanPopups.jsx
│   │   │   ├── CostBreakdown.jsx
│   │   │   ├── Pagination.jsx
│   │   │   └── Toast.jsx
│   │   ├── context/                   # React Context providers
│   │   │   ├── AuthContext.jsx
│   │   │   ├── UploadJobContext.jsx
│   │   │   └── VirusScanContext.jsx
│   │   └── pages/                     # Route-level page components
│   │       ├── Dashboard.jsx
│   │       ├── Buckets.jsx
│   │       ├── BucketDetail.jsx
│   │       ├── Billing.jsx
│   │       ├── Invoices.jsx
│   │       ├── AuditLogs.jsx
│   │       ├── Admin.jsx
│   │       ├── Login.jsx
│   │       └── Register.jsx
│   └── package.json
│
└── storage-engine/                    # Spring Boot backend
    ├── src/main/java/com/cloudvault/storage_engine/
    │   ├── config/                    # App configuration beans
    │   │   ├── SecurityConfig.java
    │   │   ├── MinioConfig.java
    │   │   ├── RedisConfig.java
    │   │   └── AsyncConfig.java
    │   ├── controller/                # REST controllers
    │   │   ├── StorageController.java
    │   │   ├── BillingController.java
    │   │   ├── LifecycleController.java
    │   │   ├── VersioningController.java
    │   │   ├── TaggingController.java
    │   │   ├── AuditController.java
    │   │   ├── AdminController.java
    │   │   ├── AuthController.java
    │   │   └── BackupController.java
    │   ├── service/                   # Business logic
    │   │   ├── StorageService.java
    │   │   ├── BillingService.java
    │   │   ├── LifecycleService.java
    │   │   ├── AsyncUploadService.java
    │   │   ├── VirusScanService.java
    │   │   ├── BackupService.java
    │   │   ├── MeteringService.java
    │   │   ├── TaggingService.java
    │   │   └── AuthService.java
    │   ├── entity/                    # JPA entities
    │   ├── enums/                     # Domain enumerations
    │   ├── repository/                # Spring Data JPA repos
    │   ├── scheduler/                 # Cron schedulers
    │   │   ├── BillingScheduler.java
    │   │   ├── LifecycleScheduler.java
    │   │   └── PartitionMaintenanceScheduler.java
    │   ├── security/                  # JWT filter & user details
    │   ├── filter/                    # Rate limiting filter
    │   └── dto/                       # Request/response DTOs
    ├── src/main/resources/
    │   ├── application.properties
    │   └── application.properties.example
    └── pom.xml
```

---

## Troubleshooting

### ClamAV connection refused on startup

ClamAV takes 2–5 minutes to download virus definitions on first run. The backend will log `Connection refused` until the daemon is ready. 
Wait for the container to finish initializing before uploading files. Check status with:

```bash
docker logs <clamav_container_id>
# Look for: "Listening daemon: PID: N"
```

---

### Uploads failing with `Maximum upload size exceeded`

Spring Boot's default multipart limit is 1 MB. Add these to `application.properties`:

```properties
spring.servlet.multipart.max-file-size=-1
spring.servlet.multipart.max-request-size=-1
```

---

### `LazyInitializationException` in backup or billing

Ensure `@Transactional(readOnly = true)` is present on any service method that traverses JPA relationships outside the original transaction.
The backup service in particular must fetch objects with `JOIN FETCH` on the bucket and user associations.

---

### Lombok compilation errors (`NoSuchFieldException: TypeTag`)

Update Lombok to `1.18.36` or later in both the `<dependency>` block and the `annotationProcessorPaths` entry in `maven-compiler-plugin`. 
The older `1.18.26` version is incompatible with Java 17's compiler internals.

```xml
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <version>1.18.36</version>
</dependency>
```

---

### Account locked after testing

```sql
UPDATE users
SET failed_login_attempts = 0, locked_until = NULL
WHERE username = 'your_username';
```

---

### MinIO bucket not found errors for backup

The `backup-storage` bucket must be created manually before the backup feature is used:

```bash
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/backup-storage
```

---

## License

This project is for educational and portfolio purposes.

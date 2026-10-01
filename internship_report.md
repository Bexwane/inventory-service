# Internship Project Report

---

## Title Page

| | |
|---|---|
| **Report Title** | Design and Development of a Warehouse Management System (WMS) Inventory Service |
| **Submitted By** | Shubham Singh |
| **Organization** | Addverb Technologies |
| **Duration** | 18 May 2026 – 17 June 2026 (1 Month) |
| **Department** | WES |
| **Reporting Manager** | Piyush Kumar |
| **Mentor / Guide** | Hardik Jain, Jaimik Chauhan, Piyush Kumar |
| **Date of Submission** | 18 June 2026 |

---

## Declaration

I, Shubham Singh, hereby declare that this report titled **"Design and Development of a Warehouse Management System (WMS) Inventory Service"** is an authentic record of work undertaken by me during my internship at Addverb Technologies from 18 May 2026 to 17 June 2026.

This report has been written by me and has not been submitted elsewhere for any other purpose. All information obtained from external sources has been duly acknowledged.

**Signature:** ___________________________  
**Name:** Shubham Singh  
**Date:** 18 June 2026

---

## Certificate of Completion

*To be signed and stamped by the reporting manager or HR department of Addverb Technologies.*

This is to certify that **Shubham Singh** has successfully completed his internship at **Addverb Technologies** from **18 May 2026 to 17 June 2026** and has worked on the project **"Warehouse Management System (WMS) Inventory Service"**.

During this period, his conduct and performance were found to be satisfactory.

**Authorized Signatory:** ___________________________  
**Name:** Piyush Kumar  
**Designation:** Software Architect  
**Organization:** Addverb Technologies  
**Date:** ___________________________

---

## Acknowledgements

I would like to express my sincere gratitude to **Addverb Technologies** for providing me with the opportunity to work on a meaningful and technically challenging project during this internship.

I am deeply thankful to my mentors, **Hardik Jain, Jaimik Chauhan, and Piyush Kumar**, and my reporting manager, **Piyush Kumar**, for their consistent guidance, constructive feedback, and technical support throughout the duration of the internship.

I also acknowledge the support of the engineering and operations teams at Addverb Technologies, whose insights into real-world warehouse operations helped shape the design decisions of this project.

---

## Table of Contents

1. Abstract / Executive Summary
2. Introduction
   - 2.1 About Addverb Technologies
   - 2.2 Context and Background
3. Objectives of the Internship
4. Project Scope and Description
5. System Architecture
   - 5.1 High-Level Architecture
   - 5.2 Technology Stack
6. Database Design
   - 6.1 Schema Overview
   - 6.2 Database Migrations (Flyway)
7. Backend Implementation
   - 7.1 Application Layers
   - 7.2 Authentication and Authorization
   - 7.3 Core Inventory Operations
   - 7.4 Idempotency Mechanism
   - 7.5 Rate Limiting
   - 7.6 Kafka Event Publishing and SAP Integration
   - 7.7 Scheduled Recovery Job
   - 7.8 Advanced Edge-Case Testing
8. Frontend Implementation
   - 8.1 Application Structure and Routing
   - 8.2 Authentication Context and Token Management
   - 8.3 Pages and Modules
9. API Reference
10. Security Design
11. System Failsafes and Fallbacks
12. Infrastructure and Deployment
13. Challenges Faced and Solutions
14. Learning Outcomes
15. Conclusion
16. Appendices

---

## 1. Abstract / Executive Summary

This report documents the design, development, and implementation of a **Warehouse Management System (WMS) Inventory Service** undertaken as part of a one-month internship at Addverb Technologies.

The project involved building a production-grade, full-stack inventory service capable of managing core warehouse operations including stock receipt (putaway), stock picking, inventory reservation, and SAP ERP integration. The backend was developed using **Java 17** and **Spring Boot 4**, and the frontend was built as a **React** single-page application.

Key engineering challenges addressed include: ensuring **idempotency** of all write operations using Redis, implementing **role-based access control (RBAC)** with granular per-user permissions, designing an **append-only audit ledger** of all stock movements, and integrating asynchronous **Kafka-based event publishing** to a downstream SAP system with a built-in retry recovery mechanism.

The final system is a deployable, containerized service with a responsive web interface designed for both desktop workstations and mobile warehouse scanners.

---

## 2. Introduction

### 2.1 About Addverb Technologies

Addverb Technologies is a robotics and automation company that designs and deploys intelligent warehouse automation solutions. As an organization operating at the intersection of hardware robotics and enterprise software, Addverb builds systems that manage large-scale warehouse operations for clients across manufacturing and logistics sectors.

### 2.2 Context and Background

Modern warehouses face significant operational complexity — thousands of SKUs moving between locations daily, strict traceability requirements, and integration demands with enterprise systems like SAP. Manual or spreadsheet-based tracking is error-prone and does not scale.

The goal of this internship project was to design and build from scratch a **digital inventory service** — a core subsystem of a WMS — capable of tracking real-time stock levels, supporting picker and putaway workflows, enforcing access control, and publishing stock movement events to a SAP-compatible downstream system.

---

## 3. Objectives of the Internship

The objectives set at the beginning of the internship were:

1. Design and implement a RESTful inventory service backend using Spring Boot.
2. Implement secure user authentication using stateless JWT tokens.
3. Build a granular, per-user permission model (not purely role-based).
4. Implement core WMS workflows: stock receipt (putaway), stock picking, reservation, and short-pick handling.
5. Ensure data integrity through optimistic locking and idempotency controls.
6. Publish stock movement events asynchronously to Kafka, representing integration with a downstream SAP ERP system.
7. Build a responsive React frontend suitable for both desktop managers and mobile warehouse workers.
8. Containerize all infrastructure dependencies for reproducible local development.

---

## 4. Project Scope and Description

The system is titled the **enterprise-inventory** service and is structured as a full-stack application with a clearly separated backend and frontend.

**In scope:**
- User management: create, deactivate, activate, and update users with role and permission assignments.
- Batch permission updates: update permissions for multiple users in a single atomic transaction.
- Inventory putaway: receive stock into a bin location, optionally within a container (tote/pallet).
- Batch putaway: a multi-container, multi-item put-away flow submitted as a single transaction.
- Inventory picking: two-phase pick workflow (reserve then confirm) with short-pick support.
- Reservation release: cancel a reserved pick and restore available stock.
- Global inventory view: paginated, searchable, and sortable inventory table with column controls.
- Personal activity log: each user can view their own stock movement history with SAP sync status.
- SAP event publishing: each confirmed putaway or pick triggers a Kafka message to a downstream SAP topic.
- SAP sync recovery: a scheduled job retries any stock movements that failed to sync to SAP.

**Out of scope (not implemented):**
- Physical barcode scanner hardware integration (UI gestures are cosmetic placeholders).
- Multi-warehouse / multi-site support.
- SAP consumer implementation (only the producer/publisher side is built).

---

## 5. System Architecture

### 5.1 High-Level Architecture

```
+------------------------------------------------------+
|                React Frontend (Vite)                 |
|    Login | Dashboard | Receive | Pick | Users        |
+------------------------+-----------------------------+
                         |  REST (HTTP/JSON) via Axios
                         |  JWT Bearer Token Auth
+------------------------v-----------------------------+
|           Spring Boot 4 Backend (Port 8080)          |
|                                                      |
|  Filters:  CorrelationId > RateLimit > JwtAuth       |
|  Controllers: Auth | Inventory | User                |
|  Services:   InventoryService | UserService          |
|              AuthService      | IdempotencyService   |
|              TokenBlacklistService                   |
|              InventoryEventPublisher (Kafka)         |
|  Jobs:       SapSyncRecoveryJob                      |
|  Entities:   InventoryJpaEntity | StockMovement      |
|              UserEntity         | IdempotencyKey     |
|              TokenBlacklist     | ReservationLock    |
+------+----------------------+------------------------+
       |                      |
       | JPA / JDBC           | Spring Data Redis
+------v------+       +-------v-----------------------+
| PostgreSQL  |       | Redis                         |
| (System of  |       | (High-Speed Cache)            |
|  Record)    |       |                               |
|  inventory  |       |  * Idempotency fast-path      |
|  users      |       |  * JWT blacklist cache        |
|  fallbacks  |       |  * Reservation lock cache     |
+-------------+       +-------------------------------+
       |
       | Kafka Producer (Spring Kafka)
+------v--------------------------------------------+
| Kafka / Redpanda Broker                           |
|  Topics:                                          |
|   wms.putaway.confirmed                           |
|   wms.pick.confirmed                              |
|   wms.pick.short                                  |
+---------------------------------------------------+
         (consumed by downstream SAP integration)
```

All infrastructure services (PostgreSQL, Redis, Kafka) are containerized via `docker-compose.yml`.

### 5.2 Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Backend Framework | Spring Boot | 4.0.6 |
| ORM | Spring Data JPA / Hibernate | (managed by Boot) |
| Database | PostgreSQL | 15 |
| In-Memory Store | Redis (via Lettuce client) | 7 |
| Message Broker | Apache Kafka (Redpanda) | v23.2.19 |
| Database Migrations | Flyway | (managed by Boot) |
| Security | Spring Security + JJWT | JJWT 0.12.5 |
| Rate Limiting | Bucket4j + Caffeine | 8.1.0 |
| Code Reduction | Lombok | (managed by Boot) |
| Frontend Language | JavaScript (JSX) | — |
| Frontend Framework | React | — |
| Frontend Build Tool | Vite | 8.x |
| HTTP Client | Axios | — |
| Icons | Lucide React | — |
| Containerization | Docker Compose | 3.8 |

---

## 6. Database Design

### 6.1 Schema Overview

The database (`inventory_db`) contains the following core tables:

#### `users`
Stores all warehouse system users.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | Auto-generated |
| `employee_id` | VARCHAR(50) UNIQUE | e.g. `EMP-001` |
| `username` | VARCHAR(50) UNIQUE | Login credential |
| `password_hash` | VARCHAR(255) | BCrypt-encoded |
| `role` | VARCHAR(50) | Organizational role label |
| `is_active` | BOOLEAN | Soft delete / deactivation flag |

#### `user_permissions`
Stores the actual access permissions per user. This is a flat join table that replaced role-based mappings in Migration V6.

| Column | Type | Notes |
|---|---|---|
| `user_id` | UUID FK to users | Cascade delete |
| `permissions` | VARCHAR(50) | `CAN_PICK`, `CAN_PUTAWAY`, `CAN_MANAGE_INVENTORY`, `CAN_MANAGE_USERS` |

#### `inventory_items`
The live inventory record for each SKU at a specific location and container combination.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `sku` | VARCHAR(100) | Product barcode |
| `location_id` | UUID | Bin/rack UUID |
| `container_id` | UUID (nullable) | Tote/pallet UUID |
| `qty_on_hand` | INTEGER | Total physical stock; CHECK >= 0 |
| `qty_reserved` | INTEGER | Stock reserved for pending picks |
| `lot_number` | VARCHAR(50) | Batch/lot tracking |
| `expiry_date` | TIMESTAMPTZ | For FEFO-capable queries |
| `version` | BIGINT | Optimistic locking version |
| `created_at` | TIMESTAMPTZ | Auto-set |
| `updated_at` | TIMESTAMPTZ | Auto-updated |

Note: `qty_available = qty_on_hand - qty_reserved` is computed as a `@Transient` field in the entity and is never persisted to the database.

**Indexes:** `idx_inventory_sku` on `sku`; `idx_inventory_location` on `(location_id, container_id)`.

#### `stock_movements`
An immutable, append-only audit ledger. Marked `@Immutable` in JPA — no UPDATE queries are ever issued on existing records.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `movement_type` | VARCHAR(20) | `RECEIVE`, `RESERVE`, `PICK`, `RELEASE`, `ADJUST_UP`, `ADJUST_DOWN` |
| `sku` | VARCHAR(100) | |
| `from_location_id` | UUID (nullable) | Source location |
| `to_location_id` | UUID (nullable) | Destination location |
| `container_id` | UUID (nullable) | |
| `qty` | INTEGER | |
| `reference_id` | VARCHAR(100) | Task ID (e.g. `PICK-555123`) |
| `reference_type` | VARCHAR(50) | `PUTAWAY_TASK` or `PICK_TASK` |
| `performed_by` | UUID | User who performed the action |
| `occurred_at` | TIMESTAMPTZ | Auto-set at creation |
| `synced_to_sap` | BOOLEAN | Whether the Kafka event was acknowledged |

**Indexes:** `idx_movement_sku`; `idx_movement_ref` on `(reference_id, reference_type)`; `idx_movement_sap_sync` on `(synced_to_sap, occurred_at)` — used by the SAP sync recovery job.

#### `token_blacklist`
Stores revoked JWTs (logout) as a fallback persistent store.

| Column | Type | Notes |
|---|---|---|
| `jti` | UUID PK | The JWT unique ID |
| `expires_at` | TIMESTAMPTZ | Token expiration time |

#### `idempotency_keys`
Stores idempotency responses to prevent duplicate operations in case Redis goes down.

| Column | Type | Notes |
|---|---|---|
| `key` | VARCHAR(255) PK | The idempotency key |
| `response_payload` | JSONB | Cached successful response |
| `expires_at` | TIMESTAMPTZ | Key expiration time |

#### `reservation_locks`
Stores active reservation locks tying a Task ID to a specific item reservation.

| Column | Type | Notes |
|---|---|---|
| `task_id` | VARCHAR(255) | Part of Composite PK |
| `sku` | VARCHAR(255) | Part of Composite PK |
| `location_id` | UUID | Part of Composite PK |
| `container_id` | UUID (nullable) | Optional container |
| `qty` | INTEGER | Locked quantity |
| `expires_at` | TIMESTAMPTZ | Lock expiration time |

### 6.2 Database Migrations (Flyway)

All schema changes are version-controlled through Flyway migrations applied automatically on startup:

| Migration | Description |
|---|---|
| `V1__init_inventory_schema.sql` | Creates `users`, `inventory_items`, `stock_movements` tables and all initial indexes |
| `V2__add_performed_by_index.sql` | Adds index on `stock_movements.performed_by` to support personal activity log queries |
| `V4__add_rbac_tables.sql` | Creates `permissions` and `role_permissions` tables; seeds initial role-to-permission mappings |
| `V5__add_test_workers.sql` | Seeds test worker accounts for development |
| `V6__migrate_to_user_permissions.sql` | Migrates from role-based to per-user `user_permissions` table; drops the old RBAC mapping tables |
| `V7__add_redis_fallback_tables.sql` | Adds persistent fallback tables for token blacklist, idempotency keys, and reservation locks to ensure zero data loss during Redis amnesia events |

---

## 7. Backend Implementation

### 7.1 Application Layers

The backend follows a layered architecture:

- **Web Layer** (`web/controller`): REST controllers handle HTTP concerns only — request parsing, response building, and `@PreAuthorize` annotation-driven access control.
- **Application Layer** (`application`): Services containing all business logic. No HTTP or persistence concerns.
- **Infrastructure Layer** (`infrastructure`): JPA entities, repositories, Spring Security configuration, Kafka publisher, Redis integrations, filters, and scheduled jobs.

### 7.2 Authentication and Authorization

**Authentication** is stateless and JWT-based.

On login (`POST /api/v1/auth/login`):
1. Spring Security's `AuthenticationManager` validates the username/password against the BCrypt-encoded hash.
2. On success, `JwtUtil` generates two RSA-signed tokens:
   - **Access token**: 1-hour lifetime. Carries `userId`, `roles` (the user's permissions list), and `typ: access` claim.
   - **Refresh token**: 24-hour lifetime. Carries `typ: refresh` claim only.
3. The frontend stores both tokens in `localStorage`.

On every subsequent request:
- `JwtAuthenticationFilter` extracts the `Bearer` token, checks the Redis blacklist, validates the RSA signature and claims, and populates the `SecurityContextHolder`.

On logout (`POST /api/v1/auth/logout`):
- The token is added to a Redis-backed blacklist via `TokenBlacklistService`, with a TTL equal to the token's remaining lifetime so the Redis entry is automatically cleaned up.

On token expiry:
- The Axios HTTP client in the frontend automatically retries any `401` response using the stored refresh token via a response interceptor, obtaining a new access token transparently.

**Authorization** uses Spring Security's `@PreAuthorize("hasAuthority('...')")` method-level annotations. The four available permissions are:

| Permission | Access Granted To |
|---|---|
| `CAN_PUTAWAY` | Receive stock, batch putaway endpoints |
| `CAN_PICK` | Reserve, confirm, and release pick endpoints |
| `CAN_MANAGE_INVENTORY` | Global inventory view, personal activity log |
| `CAN_MANAGE_USERS` | All user management endpoints; also required for actuator access |

Permissions are stored per user in the `user_permissions` table and are encoded into the JWT at login time. The `role` field on the `users` table serves as an organizational label only — actual endpoint access is determined solely by `user_permissions` entries.

**RSA Key Generation:** `JwtUtil` generates a 2048-bit RSA key pair at application startup using `KeyPairGenerator`. Tokens are signed with the private key and verified with the public key, making the signing key never shareable while the verification key can be distributed to consumers.

### 7.3 Core Inventory Operations

All core operations are implemented in `InventoryService` and exposed through `InventoryController`.

**Three-Part Exact Match Lookup**

A custom JPQL query (`findExactMatch`) is used throughout the service to locate an inventory record by the unique combination of `(sku, locationId, containerId)`. This design supports multiple containers of the same SKU in the same physical location.

**Receive Stock (`POST /api/v1/inventory/receive`)**

1. Looks up an existing inventory record by exact match.
2. If none exists, creates a new `InventoryJpaEntity` with `qtyOnHand = 0`.
3. Calls `entity.receiveStock(amount)` — validates amount > 0 and increments `qtyOnHand`.
4. Saves to the database.
5. Writes an immutable `StockMovementJpaEntity` of type `RECEIVE` to the audit ledger.
6. Publishes an `InventoryEvent(STOCK_RECEIVED)` via Spring's `ApplicationEventPublisher`. This event is picked up by `InventoryEventPublisher` after the transaction commits (via `@TransactionalEventListener(AFTER_COMMIT)`), which then sends a Kafka message to the `wms.putaway.confirmed` topic asynchronously.

**Batch Putaway (`POST /api/v1/inventory/receive/batch`)**

Accepts a `BatchPutawayDTO` containing a task ID, a source location, and a list of containers each containing items with destination locations. The entire batch is processed within a single idempotency-guarded transaction. Each item follows the same receive-stock flow described above.

**Reserve Stock (`POST /api/v1/inventory/pick/reserve`)**

1. Finds the inventory record by exact match. Throws `InsufficientStockException` if not found.
2. Calls `entity.reserveStock(qty)` — validates `qty > 0` and that `qty <= qtyAvailable`. Increments `qtyReserved`.
3. Writes a `RESERVE` movement to the audit ledger.

**Confirm Pick (`POST /api/v1/inventory/pick/confirm`)**

1. Finds the inventory record.
2. Calls `entity.confirmPick(reservedQty, actualQty)`. This method:
   - Validates `actualQty >= 0` and `actualQty <= reservedQty`.
   - Decrements `qtyOnHand` by `actualQty` and `qtyReserved` by `reservedQty`.
   - Correctly handles short picks: if `actualQty < reservedQty`, the worker took less than reserved, and the reserved quantity is fully released while only actual stock is consumed.
3. Writes a `PICK` movement to the audit ledger.
4. Publishes a `PICK_CONFIRMED` event. If it is a short pick, also publishes a `SHORT_PICK` event with a `discrepancy` field.

**Release Reservation (`POST /api/v1/inventory/pick/release`)**

Used when a pick is cancelled before confirmation. Decrements `qtyReserved` without touching `qtyOnHand`, and writes a `RELEASE` movement to the audit ledger.

**Optimistic Locking**

`InventoryJpaEntity` carries a `@Version Long version` field. Hibernate uses this to detect concurrent modifications — if two transactions attempt to update the same inventory record simultaneously, the second one will throw `OptimisticLockingFailureException`, which is caught by `GlobalExceptionHandler` and returned as a `409 CONFLICT` response.

### 7.4 Idempotency Mechanism

`IdempotencyService` guarantees that submitting the same request twice with the same `X-Idempotency-Key` header produces the same result without re-executing the operation.

**Implementation (Hybrid Postgres + Redis Cache):**

1. **Fast Path:** The service first checks Redis for the cached result. If found, it returns immediately.
2. **Slow Path:** If Redis is empty or down, it checks the `idempotency_keys` table in PostgreSQL.
3. **Atomic Reservation:** If the key is truly new, it atomically saves a `__PENDING__` sentinel record to PostgreSQL using `saveAndFlush`. This guarantees zero sync issues or race conditions.
4. **Completion:** After the operation succeeds, the Postgres record is updated with the serialized JSON response, and the result is asynchronously pushed to Redis for fast future lookups.
5. **Concurrent duplicate request:** If a second request hits the DB while `__PENDING__` is active, it throws a `DataIntegrityViolationException`, mapped to a `409 Conflict`.

### 7.5 Rate Limiting

`RateLimitingFilter` implements a two-tier rate limiting strategy using **Bucket4j** (token bucket algorithm) and **Caffeine** (in-memory cache with eviction):

- **Tier 1 — IP-based (Login/Refresh endpoints):** 10 attempts per minute per client IP. On breach, returns `429 Too Many Requests` with a `X-Rate-Limit-Retry-After-Seconds` header.
- **Tier 2 — Per-user (All authenticated endpoints):** 60 requests per minute per authenticated username, with an initial burst allowance of 20. Adds `X-Rate-Limit-Remaining` header on every successful request.

Each tier uses a separate Caffeine cache with 10-minute access-based expiry and limits of 10,000 (user) and 50,000 (IP) entries to prevent memory exhaustion.

### 7.6 Kafka Event Publishing and SAP Integration

`InventoryEventPublisher` listens for Spring application events (fired post-transaction commit) and publishes them to Kafka topics.

**Topics:**

| Topic | Triggered By |
|---|---|
| `wms.putaway.confirmed` | Every successful stock receipt |
| `wms.pick.confirmed` | Every confirmed pick |
| `wms.pick.short` | Every short pick (actual qty < reserved qty) |

**Payload structure (JSON, all topics):**
```json
{
  "eventType": "STOCK_RECEIVED",
  "sku": "SKU-ABC-123",
  "locationId": "uuid",
  "containerId": "uuid or null",
  "qty": 10,
  "taskId": "TSK-1A2B3C4D",
  "occurredAt": "2026-06-01T10:00:00Z"
}
```

**Reliability:** The Kafka producer is configured with `acks=all` and `enable.idempotence=true`. On successful Kafka acknowledgement, the `synced_to_sap` column on the corresponding `stock_movements` record is set to `true` via `movementRepository.markSyncedToSap()`.

### 7.7 Scheduled SAP Sync Recovery Job

`SapSyncRecoveryJob` runs every 5 minutes (configurable via `wms.sap-sync.retry-interval-ms`). It queries for `stock_movements` records where `synced_to_sap = false` and `occurred_at` is older than 5 minutes (to avoid racing with in-flight async publishes). It re-publishes up to 50 unsynced movements per cycle using synchronous Kafka sends with a 10-second timeout, marking each successful one as synced. Failed retries are logged as errors.

### 7.8 Advanced Edge-Case Testing

To guarantee the reliability and resilience of the system, a comprehensive suite of automated tests was built targeting specific edge-cases and concurrency scenarios:

1. **Optimistic Locking (Concurrency):** Tested using Spring Boot's Mockito support to simulate two warehouse workers attempting to pick the exact same item simultaneously, verifying that the system successfully aborts the second transaction with an `ObjectOptimisticLockingFailureException`.
2. **Idempotency (Network Retries):** Unit tests on the `IdempotencyService` verifying that duplicate requests properly return cached JSON payloads without triggering core business logic, and that concurrent duplicates receive a `409 Conflict`.
3. **Rate Limiting:** Integration tests against the `RateLimitingFilter` validating that clients exceeding 60 requests per minute properly receive a `429 Too Many Requests` HTTP response, protecting the API from denial-of-service or bug-driven request flooding.
4. **Token Blacklisting (Security):** Tests ensuring that JWTs belonging to logged-out users are identified within the Redis blacklist and subsequently blocked with a `401 Unauthorized` response.
5. **Scheduled Job Recovery:** Tests simulating Kafka broker downtime, proving the `SapSyncRecoveryJob` will continually query unsynced messages and automatically republish them when the connection is restored.
6. **Batch Partial Failure (Atomicity):** Testing the `@Transactional` boundaries of the batch putaway endpoints to ensure that a data error on the 50th item of a batch properly rolls back the insertion of the preceding 49 items.
7. **JWT Cryptography:** Verifying that dynamically generated RSA-2048 keys successfully sign and verify tokens, and strictly rejecting tokens forged with an alternative key (`SignatureException`).
8. **Distributed Tracing & Log Injection:** Testing the `CorrelationIdFilter` to ensure UUIDs are properly generated and propagated, and verifying that malicious header payloads attempting log injection (e.g. `123\n[ERROR] Hack`) are aggressively rejected.
9. **Global Exception Mapping:** Verifying that deep application and database errors are reliably caught by the `GlobalExceptionHandler` and safely mapped to standardized JSON responses, echoing the trace ID without leaking stack traces.

---

## 8. Frontend Implementation

### 8.1 Application Structure and Routing

The frontend is a Vite-powered React single-page application. Routing is handled by React Router v6. The application has five routes:

| Path | Component | Access |
|---|---|---|
| `/login` | `Login` | Public |
| `/` | `Dashboard` | Protected (any authenticated) |
| `/receive` | `Receive` | Protected (`CAN_PUTAWAY`) |
| `/pick` | `Pick` | Protected (`CAN_PICK`) |
| `/users` | `Users` | Protected (`CAN_MANAGE_USERS`) |

`ProtectedRoute` wraps all authenticated routes, redirecting unauthenticated users to `/login` while preserving their intended destination via React Router's `state.from` location object.

The `AppLayout` component wraps all routes and renders a global logout button and, for users with `CAN_MANAGE_USERS`, a navigation link to the Users administration page.

### 8.2 Authentication Context and Token Management

`AuthContext.jsx` provides application-wide authentication state via React Context. On mount, it reads the `accessToken` from `localStorage`, decodes the JWT payload (base64 decode of the token's second segment), and hydrates the `permissions` array — meaning users who refresh the page are not logged out.

Computed boolean flags (`canPick`, `canPutaway`, `canManageInventory`, `canManageUsers`) are derived from the permissions array and available to all components via the `useAuth()` hook. These flags are used to conditionally render UI elements and navigation slots.

**Axios interceptors** (`api.js`) handle token lifecycle transparently:
- Every outgoing request automatically attaches `Authorization: Bearer {accessToken}`.
- Any `401` or `403` response (excluding the login endpoint itself) triggers an automatic token refresh using the stored `refreshToken`. On success, the original request is retried. On refresh failure, both tokens are cleared and the user is redirected to `/login`.

### 8.3 Pages and Modules

**Login (`/login`)**  
Standard username/password form. On success, both tokens are stored in `localStorage` and the user is redirected to the dashboard.

**Dashboard (`/`)**  
The central workspace. On wide screens (1400px and above), it renders a two-column layout: the left column shows the Receive and/or Pick forms based on the user's permissions, and the right column shows the live inventory table. On narrower screens, only the inventory table is shown; the Receive and Pick forms are accessed via the bottom navigation bar.

Dashboard features:
- Server-side paginated inventory table (10 to 100 items per page, adjustable via range slider).
- Debounced SKU/Location search (300ms delay before API call).
- Column-level sorting (ascending/descending) on: SKU, Location, Container, Qty On Hand, Qty Reserved, Available.
- Toggle visibility of individual table columns.
- Text size toggle (Small / Medium / Large) for warehouse workers with varying display needs.
- Adjustable form panel width on desktop via a range slider.
- "My Activity Log" tab showing the logged-in user's personal stock movement history with SAP sync status indicators.

**Receive (`/receive`)**  
A multi-step batch putaway workflow:
1. Generate a Task ID (calls `POST /api/v1/inventory/tasks/generate`).
2. Enter/scan the source location UUID.
3. Add one or more containers via inline UUID input.
4. For each active container, add items (SKU + destination location UUID + quantity).
5. Submit the entire batch in one atomic request (`POST /api/v1/inventory/receive/batch`).

In-progress state is persisted to `sessionStorage`, so a page refresh within the same browser tab does not lose work. Session-scoped storage also prevents data leakage between different workers on shared warehouse terminals.

**Pick (`/pick`)**  
A two-step picking workflow:

1. **Reserve (Step 1):** Worker enters the task ID, SKU, quantity, source location UUID, and optionally a container ID. Submits `POST /api/v1/inventory/pick/reserve`. On success, Step 2 is shown.
2. **Confirm (Step 2):** Worker enters the actual quantity found at the bin. Short picks (actual quantity less than reserved) are explicitly supported. Submits `POST /api/v1/inventory/pick/confirm`. A Cancel button is available to release the reservation without confirming a pick.

Step state and form values are persisted to `sessionStorage` to survive page refreshes.

**Users (`/users`)**  
Accessible only to users with `CAN_MANAGE_USERS`. Features:
- List all users with username, employee ID, organizational role, custom permissions, and active/inactive status.
- Create a new user: employee ID, username, password, role (which pre-fills default permissions), and custom permission overrides.
- Edit an existing user: update employee ID, username, role, and individual permissions.
- Activate or deactivate a user (soft delete pattern).
- Batch permission update: select multiple users via checkbox, choose permissions to overwrite or merge, submitted as a single atomic API request.

**Activity Log (embedded in Dashboard)**  
Displays the logged-in user's own stock movements in a paginated table. Columns: Date/Time, Action type (PUTAWAY / RESERVE / PICK / RELEASE), SKU, Qty, Location, Container, Task ID, and SAP sync status.

**Bottom Navigation Bar**  
On mobile/tablet widths, a floating bottom navigation component provides three-position gesture-based navigation:
- Left slot: Putaway (navigates to `/receive`) — shown as locked with a lock icon if the user lacks `CAN_PUTAWAY`.
- Centre slot: Dashboard (navigates to `/`) — also toggles table visibility when already on `/`.
- Right slot: Pick (navigates to `/pick`) — shown as locked if the user lacks `CAN_PICK`.

The component supports drag-to-slide gesture navigation and a swipe-up gesture on each slot as a placeholder for future hardware barcode scanner integration.

---

## 9. API Reference

All endpoints are prefixed with `/api/v1`. All write endpoints (except user management and pick release) require an `X-Idempotency-Key` header. All endpoints except `/auth/login`, `/auth/refresh`, and `/actuator/health` require `Authorization: Bearer {token}`.

### 9.1 Authentication Endpoints

| Method | Endpoint | Permission | Description |
|---|---|---|---|
| POST | `/auth/login` | Public | Authenticate and receive access + refresh tokens |
| POST | `/auth/refresh` | Public | Exchange refresh token for a new access token |
| POST | `/auth/logout` | Any authenticated | Blacklist the current access token |

**POST /auth/login**
*Request:*
```json
{
  "username": "admin",
  "password": "securepassword"
}
```
*Response (200 OK):*
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
  "expiresIn": 3600
}
```

### 9.2 Inventory Endpoints

| Method | Endpoint | Permission | Description |
|---|---|---|---|
| POST | `/inventory/tasks/generate` | Any authenticated | Generate a unique task ID (`TSK-XXXXXXXX`) |
| POST | `/inventory/receive` | `CAN_PUTAWAY` | Receive stock for a single SKU |
| POST | `/inventory/receive/batch` | `CAN_PUTAWAY` | Submit a full batch putaway task |
| POST | `/inventory/pick/reserve` | `CAN_PICK` | Reserve stock for picking |
| POST | `/inventory/pick/confirm` | `CAN_PICK` | Confirm pick (supports short picks) |
| POST | `/inventory/pick/release` | `CAN_PICK` | Release a reservation without picking |
| GET | `/inventory` | `CAN_MANAGE_INVENTORY` | Get paginated, searchable, sortable inventory |
| GET | `/inventory/movements/me` | `CAN_MANAGE_INVENTORY` | Get the current user's movement history |

**POST /inventory/receive**
*Headers:* `X-Idempotency-Key: <uuid>`
*Request:*
```json
{
  "taskId": "TSK-A1B2C3D4",
  "sku": "SKU-1001",
  "qty": 50,
  "locationId": "123e4567-e89b-12d3-a456-426614174000",
  "containerId": null
}
```
*Response (200 OK):*
```json
{
  "sku": "SKU-1001",
  "qtyOnHand": 50,
  "qtyReserved": 0,
  "qtyAvailable": 50,
  "locationId": "123e4567-e89b-12d3-a456-426614174000",
  "containerId": null,
  "version": 1
}
```

**POST /inventory/pick/reserve**
*Headers:* `X-Idempotency-Key: <uuid>`
*Request:*
```json
{
  "taskId": "TSK-E5F6G7H8",
  "sku": "SKU-1001",
  "qty": 10,
  "locationId": "123e4567-e89b-12d3-a456-426614174000",
  "containerId": null
}
```
*Response (200 OK):*
```json
{
  "sku": "SKU-1001",
  "qtyOnHand": 50,
  "qtyReserved": 10,
  "qtyAvailable": 40,
  "locationId": "123e4567-e89b-12d3-a456-426614174000",
  "containerId": null,
  "version": 2
}
```

**POST /inventory/pick/confirm**
*Headers:* `X-Idempotency-Key: <uuid>`
*Request:*
```json
{
  "taskId": "TSK-E5F6G7H8",
  "sku": "SKU-1001",
  "reservedQty": 10,
  "actualQty": 8,
  "locationId": "123e4567-e89b-12d3-a456-426614174000",
  "containerId": null
}
```

### 9.3 User Management Endpoints

| Method | Endpoint | Permission | Description |
|---|---|---|---|
| GET | `/users` | `CAN_MANAGE_USERS` | List all users |
| POST | `/users` | `CAN_MANAGE_USERS` | Create a new user |
| PUT | `/users/{id}` | `CAN_MANAGE_USERS` | Update user details and permissions |
| PATCH | `/users/{id}/activate` | `CAN_MANAGE_USERS` | Activate a user |
| PATCH | `/users/{id}/deactivate` | `CAN_MANAGE_USERS` | Deactivate a user |
| PATCH | `/users/batch/permissions` | `CAN_MANAGE_USERS` | Batch update permissions (OVERWRITE or UPDATE mode) |

**POST /users**
*Request:*
```json
{
  "employeeId": "EMP-007",
  "username": "jdoe",
  "password": "TempPassword123!",
  "role": "WORKER",
  "permissions": ["CAN_PICK", "CAN_PUTAWAY"]
}
```

### 9.4 Standard Response Envelopes

**Paginated Response (e.g., GET /inventory)**
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 50,
  "totalElements": 150,
  "totalPages": 3,
  "isLast": false
}
```

**Error Response**
All error responses (400, 401, 403, 404, 409, 500) return a consistent JSON body:
```json
{
  "error": "INSUFFICIENT_STOCK",
  "message": "Inventory record missing for SKU SKU-1001",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```
Every request and response carries an `X-Correlation-ID` header for distributed tracing. The correlation ID is also injected into every log line via MDC (Mapped Diagnostic Context).

---

## 10. Security Design

| Concern | Implementation |
|---|---|
| Password storage | BCrypt with cost factor 12 |
| Token signing | RSA 2048-bit key pair generated at startup |
| Token revocation | Hybrid Postgres persistent store + Redis cache blacklist |
| Session management | `SessionCreationPolicy.STATELESS` — no server-side HTTP sessions |
| Secret management | All credentials injected via environment variables; no hardcoded secrets in version-controlled files |
| Input validation | Jakarta Bean Validation (`@Valid`) on all request DTOs; UUID format enforced on client and server |
| Log injection prevention | `CorrelationIdFilter` validates client-supplied correlation IDs against `[a-zA-Z0-9-]{1,64}` before placing in MDC |
| SQL injection prevention | All queries use JPA named parameters; no string-concatenated native SQL |
| Rate limiting | IP-based rate limiting on auth endpoints; per-user rate limiting on all authenticated APIs |
| Security response headers | `X-Frame-Options: DENY`, HSTS with `includeSubDomains`, `Referrer-Policy: no-referrer`, Content-Type-Options |
| Error information disclosure | `include-message: never` and `include-stacktrace: never` in server error configuration |
| Actuator exposure | Only `health`, `metrics`, `info` endpoints exposed; all actuator access requires `CAN_MANAGE_USERS` |
| Concurrency safety | Optimistic locking (`@Version`) on all inventory records |
| Idempotency | Hybrid Postgres absolute locking with Redis cache fast-path |
| Shared terminal safety | In-progress workflow state stored in `sessionStorage` (tab-scoped), not `localStorage` |

### 10.1 Vulnerabilities Mitigated

Because warehouse environments often involve shared terminals and high turnover, the system was designed defensively to mitigate several major OWASP vulnerabilities:

1. **SQL Injection (SQLi):** Prevented system-wide. The backend strictly uses Spring Data JPA / Hibernate which automatically employs prepared statements and parameterized queries. There is zero raw string-concatenated SQL anywhere in the codebase.
2. **Session Hijacking / Replay Attacks:** Mitigated through a strictly stateless architecture. JWTs are signed with robust RSA-2048 cryptographic checks, preventing mathematical forgery. If a token is suspected to be compromised, the hybrid Postgres/Redis blacklist guarantees instant, persistent revocation upon logout.
3. **Cross-Site Scripting (XSS):** Prevented by architectural design. The backend only accepts and returns strict `application/json`, which modern browsers do not execute as HTML. The frontend is built in React, which automatically sanitizes and escapes all variable bindings before rendering them to the DOM.
4. **Brute Force / Denial of Service (DoS):** Mitigated by two-tier API rate limiting (via Bucket4j). Login routes are strictly limited by IP address, and authenticated API routes are limited per-user, preventing attackers from overwhelming the server or automating password guessing.
5. **Log Injection (CRLF):** Prevented via strict input validation. The `CorrelationIdFilter` aggressively sanitizes incoming headers using a strict regex (`[a-zA-Z0-9\-]{1,64}`) before adding them to the server logs, preventing malicious payloads from corrupting log monitoring tools.
6. **Broken Access Control (IDOR):** Mitigated by strict per-user permissions. Every single API endpoint enforces `@PreAuthorize` checks to cryptographically verify the exact permissions (e.g., `CAN_PICK`, `CAN_PUTAWAY`) attached to the caller's JWT, ensuring users cannot access or execute operations outside their explicit organizational scope.

---

## 11. System Failsafes and Fallbacks

To ensure maximum uptime and data integrity in a fast-paced warehouse environment, several automated failsafes and fallbacks were engineered into the system:

1. **Network Retry Fallback (Idempotency):** If a worker's scanner loses connection after sending a request but before receiving the response, their device will automatically retry the request. The Redis `SET NX` idempotency cache acts as a failsafe, intercepting the duplicate request and returning the cached successful response without corrupting inventory counts.
2. **Concurrent Modification Failsafe (Optimistic Locking):** If two warehouse workers scan and pick the exact same item from the exact same bin at the exact same millisecond, the database's `@Version` column acts as a failsafe. The first transaction commits, while the second is safely aborted with a `409 Conflict`, forcing the worker to refresh their view rather than driving inventory into negative numbers.
3. **Kafka Broker Downtime Fallback (SAP Recovery):** If the downstream Kafka broker crashes, the primary API transactions will still succeed. The system falls back to the `SapSyncRecoveryJob`, which acts as an asynchronous safety net to sweep the database for unsynced events and republish them once the broker recovers.
4. **Brute Force Failsafe (Rate Limiting):** If a malicious actor or a malfunctioning client script spams the API, the Bucket4j rate limiter acts as a failsafe. It temporarily blocks the offending IP address (for login routes) or user account (for authenticated routes) with a `429 Too Many Requests` error, preventing database overload.
5. **Partial Batch Failsafe (Transactional Rollback):** If a worker submits a batch of 50 putaway tasks and the 50th task contains invalid data, the `@Transactional` wrapper acts as a failsafe, automatically rolling back the entire batch to prevent the database from entering an inconsistent, partially-completed state.
6. **Compromised Token Failsafe (Redis Blacklist):** When a user logs out, their active JWT is placed into a Redis blacklist. If a malicious actor attempts to use that copied token, the `JwtAuthenticationFilter` checks Redis and rejects the request as unauthorized.
7. **Infrastructure Outage Failsafe (Amnesia Resistance):** If the Redis cache server restarts and loses its in-memory data (Amnesia), the system does not fail open or break. Both the token blacklisting service and the idempotency service transparently fall back to querying their respective System of Record tables in PostgreSQL, ensuring zero data loss and uninterrupted API execution.

---

## 12. Infrastructure and Deployment

The entire infrastructure stack is defined in `docker-compose.yml`.

| Service | Image | Port | Purpose |
|---|---|---|---|
| `wms-postgres` | `postgres:15-alpine` | 5432 | Primary relational database |
| `wms-redis` | `redis:7-alpine` | 6379 | Idempotency cache and token blacklist |
| `wms-kafka` | `redpanda v23.2.19` | 9092, 19092 | Kafka-compatible message broker (no ZooKeeper required) |

The application backend is started via `./mvnw spring-boot:run` with required environment variables. Flyway automatically applies all pending database migrations on startup. Seed user accounts are created on first run, with passwords supplied via `SEED_ADMIN_PASSWORD`, `SEED_SUPERVISOR_PASSWORD`, and `SEED_WORKER_PASSWORD` environment variables.

The frontend runs via Vite dev server on port 5173, with API calls proxied to `localhost:8080`.

The HikariCP database connection pool is configured with a maximum of 20 connections, minimum idle of 5, and a pool name of `wms-hikari-pool` for observability in thread dumps and metrics.

---

## 13. Challenges Faced and Solutions

**Challenge 1: Idempotency under concurrent load**  
*Problem:* A naive cache-check-then-set approach allows two simultaneous requests with the same key to both observe a cache miss and both execute the operation, defeating idempotency. Relying purely on Redis also risks amnesia.  
*Solution:* The system uses PostgreSQL as the absolute lock coordinator. It atomically reserves the key in Postgres using `saveAndFlush` before executing the operation, safely throwing a `409 Conflict` on concurrent requests. Redis is used purely as a high-speed read-through cache for the final responses.

**Challenge 2: Evolving from role-based to per-user permissions**  
*Problem:* The initial design derived permissions from roles, which was too coarse — some workers needed only `CAN_PICK`, not `CAN_PUTAWAY`.  
*Solution:* A Flyway migration (V6) was written to create a `user_permissions` join table, migrate all existing role-permission assignments into it, and drop the old RBAC mapping tables. The JWT now encodes the actual per-user permissions, giving fine-grained, individually configurable access.

**Challenge 3: SAP synchronization reliability**  
*Problem:* If the Kafka broker is temporarily unavailable when a pick or putaway is confirmed, the event is lost and SAP is never notified.  
*Solution:* Each `stock_movements` record carries a `synced_to_sap` flag, set to `true` only on confirmed Kafka acknowledgement. A scheduled `SapSyncRecoveryJob` runs every 5 minutes to find and retry any unsynced movements, providing at-least-once delivery guarantees.

**Challenge 4: Shared terminal data isolation**  
*Problem:* `localStorage` persists across browser sessions and tabs. On a shared warehouse terminal, a worker's in-progress pick data would be visible to the next worker who opens the same browser.  
*Solution:* All in-progress workflow state (Pick step, Receive batch) was migrated from `localStorage` to `sessionStorage`, which is scoped to the browser tab and is automatically cleared when the tab is closed.

**Challenge 5: Batch putaway UX — replacing browser dialogs**  
*Problem:* The initial implementation used browser `prompt()` and `alert()` dialogs for container ID entry and error feedback, which cannot be styled and may be blocked in enterprise browser configurations.  
*Solution:* Replaced all browser dialogs with React state-driven inline UI: an input field with Add/Cancel controls (supporting Enter key), and styled error banners for all feedback.

**Challenge 6: IP Spoofing bypassing Rate Limiter**  
*Problem:* During vulnerability testing, it was discovered that an attacker could completely bypass the brute-force protection on the login endpoint by rotating fake IP addresses inside the `X-Forwarded-For` HTTP header.  
*Solution:* The `RateLimitingFilter` was patched to completely ignore client-supplied proxy headers and strictly rely on the framework-level remote address provided by Tomcat/Nginx (`request.getRemoteAddr()`), effectively closing the exploit.

**Challenge 7: Reservation Theft Exploit**  
*Problem:* A business logic vulnerability was identified where a malicious user could execute a `confirmPick` operation against the global pool of reserved stock without actually holding a valid reservation themselves.  
*Solution:* Instead of altering the complex JPA schema, the system was patched by leveraging Redis to implement a distributed "Reservation Lock". When a user reserves stock, a cryptographically secure key is written to Redis tied specifically to their `TaskId`. The `confirmPick` operation now validates the presence of this lock before proceeding, neutralizing the theft vector with zero database schema disruption.

**Challenge 8: Log Injection (CWE-117) via Tracing Headers**  
*Problem:* To support distributed tracing, the system accepts an `X-Correlation-ID` header from clients. However, vulnerability auditing revealed that an attacker could inject newline characters (`\n`) and fake log formats into this header, effectively forging fake system logs or hiding malicious activity in the server logs.  
*Solution:* Implemented a strict sanitization regex (`[a-zA-Z0-9\-]{1,64}`) in the `CorrelationIdFilter`. Any incoming header failing this validation is aggressively discarded and replaced with a newly generated secure server-side UUID, neutralizing the log injection attack while preserving trace functionality.

**Challenge 9: Stateless JWT Revocation (Session Hijacking Mitigation)**  
*Problem:* JWTs are stateless by design. If a user actively logs out, their token remains mathematically valid. Storing revoked tokens purely in Redis risks "Cache Amnesia" (where a Redis restart clears the blacklist, silently restoring access to stolen tokens).  
*Solution:* Engineered a hybrid stateful-stateless architecture. When a user logs out, the token's unique ID (`jti`) is written permanently to PostgreSQL (`token_blacklist`), and also pushed to Redis. The `JwtAuthenticationFilter` checks the high-speed Redis cache first. If Redis is down or missing the validity key, it falls back to the PostgreSQL System of Record, ensuring stolen tokens remain permanently blocked without adding load to the DB during normal operation.

**Challenge 10: Service Layer Maintainability and Infrastructure Isolation**  
*Problem:* Over time, the core business methods in `InventoryService` (`confirmPick`, `reserveStock`) became bloated and difficult to understand due to interwoven infrastructure logic (e.g., Redis lock fallback mechanisms, cache amnesia detection, and Postgres database fallbacks). Furthermore, the service was tightly coupled to the web layer by unpacking DTOs sequentially.  
*Solution:* Executed a structural refactoring pass guided by the Single Responsibility Principle (SRP). Extracted all complex caching and fallback logic into dedicated, isolated helper methods (`acquireReservationLock`, `verifyReservationLock`, `releaseReservationLock`). Simplified function signatures by passing Domain Transfer Objects (DTOs) directly. This reduced the length of complex business methods by over 50% and made the core inventory flow readable like plain English, without sacrificing robust security and caching fallbacks.

---

## 14. Learning Outcomes

This internship provided a deep dive into what it takes to build a true "enterprise-grade" system. I learned that enterprise code isn't just about making things work; it is about making systems safe, maintainable, and resilient when things go wrong.

**Backend Engineering & Enterprise Practices:**
- **Java & Clean Code Practices:** Learned to write clean, readable Java code. A major focus was the Single Responsibility Principle—ensuring each class does one thing well—which makes the codebase much easier for a team to maintain.
- **Maven / Spring Boot:** Used Spring Boot as the foundation because it provides robust, production-ready defaults, and Maven to reliably manage all project dependencies.
- **Spring MVC & REST APIs:** Designed clean, stateless REST APIs that act as the standard communication bridge between the frontend and backend, forming the foundation of microservices basics.
- **Spring Data JPA & JDBC:** Used Spring Data JPA for easy management of database records as Java objects, and learned about Spring Data JDBC (using RowMapper / ResultSetExtractor) for scenarios requiring raw, high-performance query control.
- **Spring Security:** Implemented robust security to protect the system, ensuring that only users with specific permissions can access sensitive REST endpoints.
- **Logging & Debugging:** Learned that good logging is critical for enterprise systems. Tracing and structured logging allow developers to track down exactly where and why a process failed in production.
- **JUnit / Mocking:** Built automated tests to prove the code works. Writing tests is what separates a fragile prototype from enterprise-grade software, ensuring new changes don't break existing features.

**Evaluating OpenStack & Database Technologies:**
To make the system enterprise-grade, different storage tools were evaluated for specific use cases:
- **Redis:** Used as a high-speed cache to prevent duplicate requests (idempotency) and to manage security token blacklists instantly.
- **MongoDB:** Explored as an option for flexible, document-based storage where data structures need to change rapidly.
- **MSSQL / MySQL & PostgreSQL:** Explored relational databases (ultimately choosing PostgreSQL) to guarantee strict, ACID-compliant transactions, which is mandatory when inventory data must be perfectly reliable.

**Development Tools:**
To build, debug, and verify this system, I gained hands-on experience with industry-standard tools:
- **VS Code / IntelliJ:** Used as the primary development environments to write, navigate, and debug code efficiently.
- **Postman:** Used to simulate a client, allowing me to manually test and verify REST API responses before the frontend was built.
- **DBeaver:** Used as a universal database tool to look directly into the database tables, run SQL queries, and verify that data was saving correctly.

**System Security & Robustness:**
- **Protection from Attacks:** Learned to constantly think defensively about edge cases and vulnerabilities to make the system highly robust. Implemented protections like stateless JWT blacklisting to prevent session hijacking, and strict correlation ID sanitization to block log-injection attacks.
- **Traffic Control & Rate Limiting:** Implemented IP-based and user-based rate limiting (using Bucket4j) to protect the API from brute-force login attacks or runaway warehouse scanner scripts, ensuring the backend never crashes under heavy, unexpected load.

**Scalability & Performance:**
- **Designing for Scale:** Scalability had to be kept in mind at all times. By designing the REST APIs to be completely stateless and relying on Redis for fast-path caching, the backend can easily scale horizontally across multiple servers as warehouse operations grow.
- **Event-Driven Integration (Apache Kafka):** Learned how enterprise systems communicate asynchronously at scale. Integrated Kafka to publish inventory updates to downstream ERP systems, which prevents our fast WMS from being bottlenecked by a slower downstream SAP server, and built a resilient scheduled job (`SapSyncRecoveryJob`) to automatically retry failed messages.

**System Design & Project-Specific Engineering:**
- **Concurrency & Idempotency:** Learned how to safely handle multiple workers trying to reserve the same inventory item at the exact same time. Used Optimistic Locking (in JPA) and built a robust Idempotency system to guarantee inventory numbers are always perfectly accurate and tasks are never double-submitted.
- **Database Migrations (Flyway):** Gained experience treating the database schema like code. Used Flyway to write incremental SQL scripts that safely evolve the database structure (such as adding fallback tables) without losing existing production data.
- **Maintainable Code:** Focused heavily on writing short, self-documenting, and maintainable code by aggressively decoupling complex infrastructure logic (like locks and caches) from the core business service layers.

**Frontend Engineering:**
- Building a responsive, permission-aware React application.
- Designing mobile-first interaction patterns including gesture-based navigation suitable for warehouse scanner terminals.

---

## 15. Conclusion

This internship project resulted in a fully functional, highly scalable, and production-ready Warehouse Management System. The system successfully implements core warehouse workflows—such as receiving and picking inventory—while incorporating strict engineering safeguards to ensure enterprise-grade reliability. 

Key achievements include building a system that guarantees absolute data accuracy even when multiple workers operate simultaneously, enforcing robust security measures to protect against common cyber attacks, and ensuring seamless communication with downstream enterprise resource planning (ERP) systems. The architecture was designed to be highly resilient, capable of automatically recovering from unexpected infrastructure outages or network drops without losing data or halting warehouse operations.

Beyond simply making the system work, this project provided deep, hands-on experience in defensive system design. It highlighted the critical importance of writing clean, easily maintainable code, safely evolving the database structure over time, and thinking critically about scalability and performance under heavy load.

Ultimately, the internship at Addverb Technologies was a highly valuable experience that bridged academic software engineering concepts with the rigorous, practical demands of building secure, robust, and dependable software in the fast-paced warehouse automation domain.

---

## 16. Appendices

### Appendix A: Seed User Accounts

The following accounts are created on first startup, with passwords injected via environment variables:

| Username | Employee ID | Role | Permissions |
|---|---|---|---|
| admin | EMP-001 | MANAGER | CAN_PICK, CAN_PUTAWAY, CAN_MANAGE_INVENTORY, CAN_MANAGE_USERS |
| supervisor | EMP-002 | SUPERVISOR | CAN_PICK, CAN_PUTAWAY, CAN_MANAGE_INVENTORY |
| worker1 | EMP-003 | PICKER_ONLY | CAN_PICK |
| worker2 | EMP-004 | WORKER | CAN_PICK, CAN_PUTAWAY |
| worker3 | EMP-005 | PICKER_ONLY | CAN_PICK |
| worker4 | EMP-006 | WORKER | Inactive account (no login allowed) |

### Appendix B: Kafka Topics

| Topic | Event Type | Intended Consumer |
|---|---|---|
| `wms.putaway.confirmed` | `STOCK_RECEIVED` | SAP Goods Receipt integration |
| `wms.pick.confirmed` | `PICK_CONFIRMED` | SAP Goods Issue integration |
| `wms.pick.short` | `SHORT_PICK` | SAP discrepancy / exception management |

### Appendix C: Key Configuration Properties

| Property | Default Value | Description |
|---|---|---|
| `jwt.expiration-ms` | 3,600,000 (1 hour) | Access token lifetime |
| `jwt.refresh-expiration-ms` | 86,400,000 (24 hours) | Refresh token lifetime |
| `spring.datasource.hikari.maximum-pool-size` | 20 | Database connection pool size |
| `wms.sap-sync.retry-interval-ms` | 300,000 (5 minutes) | SAP recovery job run interval |
| `spring.kafka.producer.acks` | all | Kafka producer durability setting |

### Appendix D: Key Source File Index

```
inventory-service/
├── docker-compose.yml                         PostgreSQL, Redis, Kafka containers
├── pom.xml                                    Maven dependencies
├── src/main/resources/
│   ├── application.yml                        Application configuration
│   └── db/migration/                          Flyway SQL migrations V1 through V7
├── src/main/java/com/enterprise/inventory/
│   ├── config/
│   │   ├── AppProperties.java                 Typed configuration properties
│   │   ├── DataInitializer.java               Seed user accounts on first startup
│   │   └── SecurityConfig.java                Spring Security filter chain
│   ├── controller/
│   │   ├── AuthController.java                Login, refresh, logout endpoints
│   │   ├── InventoryController.java           All inventory endpoints
│   │   └── UserController.java                User management endpoints
│   ├── dto/                                   Request and response DTOs
│   ├── entity/
│   │   ├── IdempotencyKeyJpaEntity.java       Postgres fallback for Idempotency
│   │   ├── InventoryJpaEntity.java            Inventory entity with domain methods
│   │   ├── ReservationLockJpaEntity.java      Postgres fallback for Reservation Locks
│   │   ├── StockMovementJpaEntity.java        Immutable audit ledger entity
│   │   ├── TokenBlacklistJpaEntity.java       Postgres fallback for Token Blacklist
│   │   └── UserEntity.java                    User entity with permission set
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java        Structured error responses
│   │   └── InsufficientStockException.java    Domain exception
│   ├── filter/
│   │   ├── CorrelationIdFilter.java           Request tracing via MDC
│   │   └── RateLimitingFilter.java            Bucket4j two-tier rate limiting
│   ├── messaging/
│   │   ├── InventoryEvent.java                Spring domain event record
│   │   └── InventoryEventPublisher.java       Kafka producer for WMS events
│   ├── repository/                            JPA Repositories
│   ├── scheduling/
│   │   └── SapSyncRecoveryJob.java            Scheduled Kafka retry job
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java       Per-request JWT verification filter
│   │   ├── JwtUtil.java                       JWT generation and validation (RSA)
│   │   └── TokenBlacklistService.java         Hybrid token revocation (DB + Redis)
│   └── service/
│       ├── AuthService.java                   User authentication business logic
│       ├── IdempotencyService.java            Hybrid idempotency (DB + Redis)
│       ├── InventoryService.java              Core inventory business logic
│       └── UserService.java                   User management business logic
└── frontend/src/
    ├── App.jsx                                Root router and application layout
    ├── AuthContext.jsx                        Auth state and token management
    ├── api.js                                 Axios client with interceptors
    ├── pages/
    │   ├── Login.jsx                          Login form
    │   ├── Dashboard.jsx                      Live inventory table and embedded forms
    │   ├── Receive.jsx                        Multi-step batch putaway workflow
    │   ├── Pick.jsx                           Two-phase reserve-then-confirm pick workflow
    │   ├── Users.jsx                          User and permission management
    │   └── ActivityLog.jsx                    Personal stock movement history
    └── components/
        ├── BottomNav.jsx                      Mobile gesture-based navigation bar
        └── SwipeScan.jsx                      Scanner gesture component
```

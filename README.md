# Enterprise Inventory Service

A robust, highly concurrent Warehouse Management & Inventory System.

## Tech Stack
- **Backend**: Java, Spring Boot, Spring Security (JWT)
- **Frontend**: React, Vite
- **Database**: PostgreSQL (System of record)
- **Caching & Locks**: Redis (Rate limiting, Idempotency, Token Blacklist)
- **Event Streaming**: Redpanda/Kafka (Async integrations)

## Demo / Screenshots

Here are some previews of the running application:

### Admin Inventory View
![Admin Inventory View](demo/admin-inventory-view.png)

### Admin Activity Logs
![Admin Activity Logs](demo/admin-activity-logs.png)

### Permission Management Screen for Admin
![Permission Management Screen for Admin](demo/permission-management-screen-for-admin.png)

### Worker with Putaway and Picking Permissions
![Worker with Putaway and Picking Permissions](demo/worker-with-putaway-and-picking-permissions.png)

### Worker Interface with Picking Permissions
![Worker Interface with Picking Permissions](demo/worker-interface-with-picking-permissions.png)

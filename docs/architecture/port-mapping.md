# Port Mapping — T3Nexus (Local Dev)

## Application Services

| Service            | Port | Ghi chú                                      |
|--------------------|------|----------------------------------------------|
| `web-gateway`      | 8090 | BFF gateway, entry point cho Angular SPA     |
| `identity-service` | 8001 |                                              |
| `customer-service` | 8002 |                                              |
| `notification-service` | 8003 |                                          |
| `oauth2-service`   | 8004 | Spring Authorization Server                  |
| `email-worker`     | —    | Kafka consumer thuần, không expose HTTP port |

## Infrastructure

| Service        | Port | Ghi chú                                              |
|----------------|------|------------------------------------------------------|
| PostgreSQL (identity-service)     | 5432 | DB: `identity_db`     |
| PostgreSQL (customer-service)     | 5433 | DB: `customer_db`     |
| PostgreSQL (notification-service) | 5434 | DB: `notification_db` |
| PostgreSQL (oauth2-service)       | 5435 | DB: `oauth2_db`       |
| Redis          | 6379 | Session store (web-gateway + identity-service)       |
| Kafka          | 9092 | Message broker                                       |

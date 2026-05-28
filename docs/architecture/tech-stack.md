# Tech Stack — t3n-nexus

## Backend & Runtime

| Hạng mục | Công nghệ                | Ghi chú                                |
|----------|--------------------------|----------------------------------------|
| Runtime  | Java 21, Spring Boot 4.x | Virtual threads, record types          |
| Build    | Maven                    | Multi-module monorepo                  |
| REST API | Spring Web MVC           | Các service expose REST endpoint       |
| Security | Spring Security          | OAuth2 Resource Server tại mỗi service |

## Frontend

| Hạng mục     | Công nghệ                      | Ghi chú                                        |
|--------------|--------------------------------|------------------------------------------------|
| Framework    | Angular                        | 3 app: storefront, seller-portal, admin-portal |
| Monorepo     | NX                             | Shared libs, build cache, affected-only CI     |
| Auth pattern | Authorization Code Flow + PKCE | httpOnly cookie qua web-gateway                |

## Databases & Storage

| Hạng mục       | Công nghệ             | Dùng ở đâu                                                               |
|----------------|-----------------------|--------------------------------------------------------------------------|
| Relational     | MySQL                 | 12 services — 1 schema / service                                         |
| Document       | MongoDB               | chat-service                                                             |
| Search         | Elasticsearch         | search-service                                                           |
| Cache / KV     | Redis                 | cart (guest session), notification pub/sub, idempotency keys, ABAC cache |
| Object Storage | MinIO (S3-compatible) | Proof of delivery, invoice PDF                                           |
| Data Warehouse | *(chưa chỉ định)*     | reporting-service                                                        |

## Messaging & Streaming

| Hạng mục          | Công nghệ                        | Dùng ở đâu                                    |
|-------------------|----------------------------------|-----------------------------------------------|
| Event streaming   | Kafka                            | Domain events, Saga choreography              |
| Schema management | Confluent Schema Registry (Avro) | Tránh breaking change producer/consumer       |
| CDC               | Debezium                         | Outbox pattern — đọc MySQL binlog, push Kafka |
| Task queue        | RabbitMQ                         | Email/push notification (fire-and-forget)     |
| MQTT broker       | EMQX                             | Chat, shipper location tracking               |

## Infrastructure Middleware

| Hạng mục        | Công nghệ                        | Dùng ở đâu                                             |
|-----------------|----------------------------------|--------------------------------------------------------|
| API Gateway     | Spring Cloud Gateway             | Entry point, routing, rate limiting                    |
| BFF             | Spring (web-gateway)             | Session cookie, token exchange cho 3 Angular app       |
| Workflow engine | Temporal                         | Long-running flows: return, seller onboarding, dispute |
| Rule engine     | Drools                           | pricing-service — shipping fee, commission             |
| RPC             | gRPC (grpc-spring-boot-starter)  | pricing-service interface                              |
| Scheduler       | Quartz + Spring Batch            | scheduler-service                                      |
| WebSocket       | Spring WebSocket + Redis Pub/Sub | websocket-gateway                                      |
| IAM             | Spring Authorization Server      | oauth2-service                                         |

## Architecture Patterns

| Category    | Patterns                                                                |
|-------------|-------------------------------------------------------------------------|
| Design      | DDD, CQRS, Vertical Slice, Hexagonal Architecture                       |
| Distributed | Saga (choreography), Transactional Outbox, Event Sourcing, Event-Driven |
| Auth        | Authorization Code Flow + PKCE, ABAC                                    |
| Concurrency | Redis atomic (Lua script), Kafka partition ordering, Bloom Filter       |

## Observability & Operations

| Hạng mục            | Công nghệ                                      |
|---------------------|------------------------------------------------|
| Distributed tracing | OpenTelemetry → Jaeger                         |
| Metrics             | Prometheus → Grafana                           |
| Logging             | ELK Stack (structured logs với traceId/spanId) |
| Container           | Docker Compose (local dev + demo)              |
| IaC                 | Terraform (demo environment trên AWS)          |

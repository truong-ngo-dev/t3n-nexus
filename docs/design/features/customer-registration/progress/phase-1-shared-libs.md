# Phase 1 — Shared Libs

**Status:** `COMPLETED`
**Started:** 2026-05-14
**Completed:** 2026-05-14

## Checklist

- [x] `common-domain`: `AggregateRoot`, `DomainEvent`
- [x] `common-web`: `ApiResponse`, `GlobalExceptionHandler`
- [x] `observability-starter`: OTel + structured logging
- [x] `outbox-starter`: Outbox auto-config

## Verify

Mỗi lib build thành công và được install vào local repository.

```bash
./mvnw -pl services/libs/common-domain clean install
./mvnw -pl services/libs/common-web clean install
./mvnw -pl services/libs/observability-starter clean install
./mvnw -pl services/libs/outbox-starter clean install
```

## Session Log

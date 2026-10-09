# ADR-006 — gRPC cho pricing-service

**Status:** Accepted

## Context

`pricing-service` (Drools rule engine) được gọi synchronous bởi `cart-service` và `fulfillment-service` để tính shipping fee và commission. Đây là computational service thuần túy — input facts → output numbers, không có side effect, không có state.

Pattern này khác với các sync call còn lại trong hệ thống (`cart→promotion`, `web-gateway→oauth2`) là business logic với rich error states.

Alternatives: REST/JSON (consistent với các sync call còn lại, ít overhead hơn).

## Decision

`pricing-service` expose **gRPC interface** thay vì REST.

- `cart-service` → gRPC → `pricing-service` (tính tổng giỏ hàng)
- `fulfillment-service` → gRPC → `pricing-service` (tính phí ship khi phân công shipper)
- Các sync call còn lại giữ REST

## Consequences

**+** Strongly typed contract via protobuf — phù hợp với Drools input/output schema  
**+** Performance tốt hơn JSON cho computational service được gọi tần suất cao  
**+** Showcase gRPC bên cạnh REST — demonstrate đúng tool cho đúng use case  
**+** `cart-service` vừa có gRPC client (pricing) vừa REST client (promotion) — intentional contrast  
**−** Thêm protobuf schema file cần quản lý  
**−** Không dùng Spring MVC conventions — cần grpc-spring-boot-starter dependency  
**−** Debugging khó hơn REST (không dùng curl trực tiếp được)

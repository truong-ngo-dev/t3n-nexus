# ADR-003 — Chat dùng MQTT (EMQX)

**Status:** Accepted

## Context

Chat Customer ↔ Seller cần real-time messaging. Hệ thống đã có WebSocket Gateway cho in-app notification — câu hỏi là có dùng chung infrastructure không.

Alternatives đã xem xét:
- **STOMP over WebSocket**: tái dùng WebSocket Gateway, nhưng không có QoS, không có offline delivery, kém hơn cho mobile
- **REST polling**: đơn giản nhất nhưng latency cao, không real-time

Ngoài chat, shipper app cần publish GPS location liên tục (~1s/interval) — high-frequency, mobile-native, cần QoS.

## Decision

Dùng **EMQX** làm MQTT broker cho Chat BC và shipper location tracking.

- Chat topic: `chat/conversation/{conversationId}`, `chat/user/{userId}/unread`
- Shipper location: `shipper/location/{shipperId}`
- EMQX xử lý toàn bộ connection management, không đi qua `api-gateway` hay `web-gateway`
- `chat-service` persist messages vào MongoDB, trigger `notification-service` khi recipient offline

## Consequences

**+** QoS 1 built-in — guaranteed delivery, không cần implement retry tầng application  
**+** Offline delivery — tin nhắn queue khi recipient disconnect  
**+** Native mobile support — MQTT client library trên Android/iOS  
**+** Scale tốt hơn WebSocket cho high-frequency GPS updates  
**−** Thêm 1 broker (EMQX) bên cạnh Kafka + RabbitMQ  
**−** Browser cần MQTT over WebSocket (EMQX support sẵn — không phải custom implement)  
**−** WebSocket Gateway và EMQX phục vụ hai use case khác nhau — cần phân biệt rõ cho dev mới

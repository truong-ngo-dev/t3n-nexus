# Deployment Strategy — t3n-nexus

## Môi Trường

| Môi trường     | Infrastructure                | Mục đích                                    |
|----------------|-------------------------------|---------------------------------------------|
| Local dev      | Docker Compose, slice profile | Phát triển hàng ngày, unit/integration test |
| Demo / Staging | Single EC2 + Docker Compose   | Demo, load test, showcase pattern           |
| Production     | *(không deploy)*              | Dự án học — không có production target      |

---

## Demo Environment — AWS

**Không dùng EKS** — control plane $0.10/hr = $73/tháng kể cả khi idle, không justify cho môi trường chỉ bật khi cần.

**Approach:** Single EC2 instance (Spot) + Docker Compose.

| Instance      | Spec              | Spot ~price | Ghi chú           |
|---------------|-------------------|-------------|-------------------|
| `m6i.2xlarge` | 8 vCPU, 32GB RAM  | ~$0.12/hr   | Đủ cho demo slice |
| `m6i.4xlarge` | 16 vCPU, 64GB RAM | ~$0.23/hr   | Gần full stack    |

**Chi phí ước tính (20h demo/tháng):**

- EC2 Spot: ~$2–5
- EBS 100GB gp3 (persistent, tồn tại khi instance stopped): $8
- Data transfer + misc: ~$1
- **Tổng: ~$12/tháng**

**Workflow:**

```
terraform apply                          # spin up ~2 phút
docker compose --profile demo up -d
# demo / load test
terraform destroy                        # hoặc stop instance, giữ EBS
```

EBS giữ toàn bộ data khi instance stopped — start lại tiếp tục, không mất state.

Spot instance có thể bị reclaim nhưng không ảnh hưởng vì bạn control thời điểm bật/tắt.
Nếu cần ổn định hơn: `m6i.2xlarge` on-demand ~$7.68 cho 20h/tháng.

---

## Local Dev — Docker Compose Profiles

Không bật toàn bộ 23 services cùng lúc. Làm việc theo slice:

| Profile             | Services                                            | RAM ước tính |
|---------------------|-----------------------------------------------------|--------------|
| `order-flow`        | order + inventory + payment + Kafka + MySQL + Redis | ~3.5GB       |
| `search-flow`       | catalog + search + Elasticsearch + MySQL            | ~2.5GB       |
| `chat-flow`         | chat + MongoDB + EMQX                               | ~1.5GB       |
| `notification-flow` | notification + websocket-gateway + Redis + RabbitMQ | ~2.5GB       |

Máy dev: X1 Carbon Gen 6 (16GB RAM) — chạy thoải mái từng slice, không chạy full stack.

---

## IaC

Terraform quản lý toàn bộ demo environment: EC2, Security Group, EBS, Elastic IP.
Script start/stop instance riêng để giữ EBS khi không dùng.

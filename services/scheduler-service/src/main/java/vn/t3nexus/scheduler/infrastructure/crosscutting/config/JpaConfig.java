package vn.t3nexus.scheduler.infrastructure.crosscutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

// OutboxJpaConfiguration (outbox-starter) chỉ @EnableJpaRepositories cho OutboxEventRepository —
// KHÔNG @EntityScan cho OutboxEvent. Entity đó nằm ngoài base package @SpringBootApplication quét mặc
// định (vn.t3nexus.lib.outbox vs vn.t3nexus.scheduler) — thiếu khai báo này thì Hibernate không biết
// OutboxEvent là managed type, outboxEventRepository bean fail lúc khởi tạo
// ("Not a managed type: class vn.t3nexus.lib.outbox.OutboxEvent"). Cùng pattern mọi service khác dùng
// outbox-starter đã có (identity/oauth2/catalog/inventory/order-service) — thiếu sót lúc bootstrap
// module scheduler-service, giờ mới lộ ra khi thật sự chạy app.
@Configuration
@EntityScan({"vn.t3nexus.scheduler", "vn.t3nexus.lib.outbox"})
public class JpaConfig {
}

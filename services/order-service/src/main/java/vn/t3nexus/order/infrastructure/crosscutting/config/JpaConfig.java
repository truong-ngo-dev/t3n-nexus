package vn.t3nexus.order.infrastructure.crosscutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan({"vn.t3nexus.order", "vn.t3nexus.lib.outbox", "vn.t3nexus.lib.eventsourcing"})
public class JpaConfig {
}

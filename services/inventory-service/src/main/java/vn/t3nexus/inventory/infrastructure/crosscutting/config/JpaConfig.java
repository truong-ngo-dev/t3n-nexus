package vn.t3nexus.inventory.infrastructure.crosscutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan({"vn.t3nexus.inventory", "vn.t3nexus.lib.outbox"})
public class JpaConfig {
}

package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan({"vn.t3nexus.catalog", "vn.t3nexus.lib.outbox"})
public class JpaConfig {
}

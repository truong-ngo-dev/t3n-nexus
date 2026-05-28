package vn.t3nexus.identity.infrastructure.cross_cutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan({"vn.t3nexus.identity", "vn.t3nexus.lib.outbox"})
public class JpaConfig {
}

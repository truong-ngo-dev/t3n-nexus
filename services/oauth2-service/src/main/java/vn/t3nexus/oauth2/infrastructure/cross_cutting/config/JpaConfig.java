package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan({"vn.t3nexus.oauth2", "vn.t3nexus.lib.outbox"})
public class JpaConfig {
}

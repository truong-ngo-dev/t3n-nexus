package vn.t3nexus.auditeventstest;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import vn.t3nexus.lib.outbox.OutboxEvent;

/**
 * Ứng dụng tối thiểu cho test tích hợp của module audit-events.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = OutboxEvent.class)
public class TestApplication {
}

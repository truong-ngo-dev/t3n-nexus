package vn.t3nexus.outboxtest;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import vn.t3nexus.lib.outbox.OutboxEvent;

/**
 * Ứng dụng tối thiểu cho test tích hợp của starter. Nằm ngoài gói {@code vn.t3nexus.lib.outbox} để cơ chế quét kho dữ
 * liệu mặc định của Boot không đăng ký trùng kho mà starter đã tự đăng ký, giống một dịch vụ thật.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = OutboxEvent.class)
public class TestApplication {
}

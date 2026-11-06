package vn.t3nexus.audit.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

/**
 * Cấu hình Web và nạp GlobalExceptionHandler từ common-web để xử lý ngoại lệ HTTP thống nhất.
 */
@Configuration
@Import(GlobalExceptionHandler.class)
public class WebConfig {
}

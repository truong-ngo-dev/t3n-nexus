package vn.t3nexus.inventory.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

// GlobalExceptionHandler (common-web) nằm ngoài base package @SpringBootApplication quét mặc định
// (vn.t3nexus.lib.web.commons.exception vs vn.t3nexus.inventory) — không @Import tường minh thì mọi
// DomainException rơi vào default error handling của Spring Boot (500) thay vì status đúng như
// thiết kế (404/409/400...).
@Configuration
@Import(GlobalExceptionHandler.class)
public class WebConfig {
}

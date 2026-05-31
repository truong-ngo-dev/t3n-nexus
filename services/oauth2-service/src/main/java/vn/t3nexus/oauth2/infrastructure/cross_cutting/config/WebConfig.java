package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

@Configuration
@Import(GlobalExceptionHandler.class)
public class WebConfig {
}

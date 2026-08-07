package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

// "/login" không còn map ở đây — chuyển sang LoginPageController vì template cần
// đọc "email" (query param của flow "unverified") từ Model, không phải "${param.email}"
// trực tiếp (Thymeleaf 3.1 chặn object instantiation khi th:attr đọc thẳng implicit
// object "param" — xem login.html).
@Configuration
@Import(GlobalExceptionHandler.class)
public class WebConfig {
}

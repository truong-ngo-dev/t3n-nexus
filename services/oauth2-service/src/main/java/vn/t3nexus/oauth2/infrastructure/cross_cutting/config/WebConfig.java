package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

@Configuration
@Import(GlobalExceptionHandler.class)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("login");
    }
}

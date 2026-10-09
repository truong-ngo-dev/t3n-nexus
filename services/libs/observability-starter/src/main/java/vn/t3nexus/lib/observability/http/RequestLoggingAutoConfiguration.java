package vn.t3nexus.lib.observability.http;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.t3nexus.lib.observability.logging.MdcTaskDecorator;

/**
 * Đăng ký bộ lọc ghi dòng hoàn tất cho mỗi yêu cầu (chỉ ứng dụng servlet) và bộ truyền ngữ cảnh log cho executor do Spring quản lý.
 * Tắt bộ lọc bằng {@code observability.logging.http.enabled=false}.
 */
@AutoConfiguration
public class RequestLoggingAutoConfiguration {

    /** Ngay sau bộ lọc quan sát/trace của Boot (HIGHEST_PRECEDENCE + 1) để dòng log có trace.id, và trước Spring Security để phủ cả 401. */
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * Lớp lồng riêng để Spring không phải nạp {@code FilterRegistrationBean} hay API servlet khi ứng dụng không có servlet
     * (worker, ứng dụng reactive); điều kiện lớp được đọc từ siêu dữ liệu trước khi nạp lớp.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({Filter.class, OncePerRequestFilter.class, FilterRegistrationBean.class})
    @ConditionalOnProperty(prefix = "observability.logging.http", name = "enabled", matchIfMissing = true)
    @EnableConfigurationProperties(HttpLoggingProperties.class)
    static class ServletRequestLogging {

        @Bean
        FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter(HttpLoggingProperties properties) {
            FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(
                    new RequestLoggingFilter(properties, new BodySanitizer(properties.sanitizerOptions())));
            registration.setOrder(FILTER_ORDER);
            return registration;
        }
    }
}

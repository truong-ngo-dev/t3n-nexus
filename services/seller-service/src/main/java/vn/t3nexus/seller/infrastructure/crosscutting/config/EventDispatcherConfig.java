package vn.t3nexus.seller.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.service.EventHandler;

import java.util.List;

/**
 * Cấu hình khởi tạo bộ phát sự kiện miền nội bộ.
 */
@Configuration
public class EventDispatcherConfig {

    /**
     * Khởi tạo EventDispatcher từ danh sách các EventHandler đã đăng ký trong ứng dụng.
     */
    @Bean
    public EventDispatcher eventDispatcher(List<EventHandler<?>> eventHandlers) {
        return new EventDispatcher(eventHandlers);
    }
}

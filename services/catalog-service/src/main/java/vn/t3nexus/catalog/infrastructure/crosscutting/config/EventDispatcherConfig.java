package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.service.EventHandler;

import java.util.List;

@Configuration
public class EventDispatcherConfig {

    @Bean
    public EventDispatcher eventDispatcher(List<EventHandler<?>> eventHandlers) {
        return new EventDispatcher(eventHandlers);
    }
}

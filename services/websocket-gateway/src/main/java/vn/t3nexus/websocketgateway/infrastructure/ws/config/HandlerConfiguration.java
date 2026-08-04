package vn.t3nexus.websocketgateway.infrastructure.ws.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import vn.t3nexus.websocketgateway.infrastructure.ws.handler.InAppWebSocketHandler;

import java.util.Map;

@Configuration
public class HandlerConfiguration {

    @Bean
    public HandlerMapping wsHandlerMapping(InAppWebSocketHandler wsHandler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/inapp", wsHandler));
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

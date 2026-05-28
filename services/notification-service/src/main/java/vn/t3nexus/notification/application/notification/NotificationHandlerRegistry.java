package vn.t3nexus.notification.application.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationHandlerRegistry {

    private final Map<String, NotificationEventHandler<?>> handlers;

    public NotificationHandlerRegistry(List<NotificationEventHandler<?>> all) {
        this.handlers = all.stream()
                .collect(Collectors.toMap(NotificationEventHandler::supportedEventType, Function.identity()));
    }

    public NotificationEventHandler<?> get(String eventType) {
        return handlers.get(eventType);
    }
}

package vn.t3nexus.notification.domain.notification;

import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class NotificationPayload implements ValueObject {

    private final String              title;
    private final String              body;
    private final Map<String, Object> attributes;

    private NotificationPayload(String title, String body, Map<String, Object> attributes) {
        this.title      = Objects.requireNonNull(title, "title must not be null");
        this.body       = Objects.requireNonNull(body,  "body must not be null");
        this.attributes = attributes != null
                ? Collections.unmodifiableMap(attributes)
                : Collections.emptyMap();
    }

    public static NotificationPayload of(String title, String body, Map<String, Object> attributes) {
        return new NotificationPayload(title, body, attributes);
    }

    public static NotificationPayload of(String title, String body) {
        return new NotificationPayload(title, body, null);
    }

    public String              getTitle()      { return title; }
    public String              getBody()       { return body; }
    public Map<String, Object> getAttributes() { return attributes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationPayload that)) return false;
        return Objects.equals(title, that.title)
                && Objects.equals(body, that.body)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, body, attributes);
    }
}

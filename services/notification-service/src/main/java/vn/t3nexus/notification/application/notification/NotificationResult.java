package vn.t3nexus.notification.application.notification;

import vn.t3nexus.notification.domain.notification.NotificationInbox;
import vn.t3nexus.notification.domain.notification.NotificationLog;

import java.util.List;

public record NotificationResult(
        List<NotificationLog>   logs,
        List<NotificationInbox> inboxes
) {
    public static NotificationResult of(List<NotificationLog> logs, List<NotificationInbox> inboxes) {
        return new NotificationResult(logs, inboxes);
    }

    public static NotificationResult emailOnly(NotificationLog log) {
        return new NotificationResult(List.of(log), List.of());
    }

    public static NotificationResult empty() {
        return new NotificationResult(List.of(), List.of());
    }
}

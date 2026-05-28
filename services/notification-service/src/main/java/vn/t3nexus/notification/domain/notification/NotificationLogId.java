package vn.t3nexus.notification.domain.notification;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class NotificationLogId extends AbstractId<String> implements Id<String> {

    private NotificationLogId(String value) {
        super(value);
    }

    public static NotificationLogId of(String id) {
        return new NotificationLogId(id);
    }
}

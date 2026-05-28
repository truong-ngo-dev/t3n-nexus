package vn.t3nexus.notification.domain.notification;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class NotificationInboxId extends AbstractId<String> implements Id<String> {

    private NotificationInboxId(String value) {
        super(value);
    }

    public static NotificationInboxId of(String id) {
        return new NotificationInboxId(id);
    }
}

package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

/** Mã phiên ứng dụng: UUID v7 (ADR-0001). Đây là {@code sid} đi trong hợp đồng danh tính (ADR-0003 mục 4). */
public final class ClientSessionId extends AbstractId<UUID> implements Id<UUID> {

    private ClientSessionId(UUID value) {
        super(value);
    }

    public static ClientSessionId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("ClientSessionId value is required");
        return new ClientSessionId(value);
    }

    public static ClientSessionId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

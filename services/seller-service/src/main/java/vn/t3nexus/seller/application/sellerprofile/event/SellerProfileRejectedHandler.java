package vn.t3nexus.seller.application.sellerprofile.event;

import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRejected;

import java.util.Objects;

/**
 * Xử lý sự kiện từ chối hồ sơ người bán bằng cách lưu vào hộp thư.
 */
@Component
public class SellerProfileRejectedHandler implements EventHandler<SellerProfileRejected> {

    private final OutboxEventStore outboxEventStore;

    public SellerProfileRejectedHandler(OutboxEventStore outboxEventStore) {
        this.outboxEventStore = Objects.requireNonNull(outboxEventStore, "outboxEventStore");
    }

    @Override
    public void handle(SellerProfileRejected event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<SellerProfileRejected> getEventType() {
        return SellerProfileRejected.class;
    }
}

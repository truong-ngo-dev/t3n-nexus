package vn.t3nexus.seller.application.sellerprofile.event;

import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileApproved;

import java.util.Objects;

/**
 * Xử lý sự kiện duyệt hồ sơ người bán bằng cách lưu vào hộp thư.
 */
@Component
public class SellerProfileApprovedHandler implements EventHandler<SellerProfileApproved> {

    private final OutboxEventStore outboxEventStore;

    public SellerProfileApprovedHandler(OutboxEventStore outboxEventStore) {
        this.outboxEventStore = Objects.requireNonNull(outboxEventStore, "outboxEventStore");
    }

    @Override
    public void handle(SellerProfileApproved event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<SellerProfileApproved> getEventType() {
        return SellerProfileApproved.class;
    }
}

package vn.t3nexus.seller.application.sellerprofile.event;

import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;

import java.util.Objects;

/**
 * <p>Ghi {@link SellerProfileSubmitted} vào hộp thư (ADR-0008) để bộ chuyển hộp thư đưa lên chủ đề
 * <code>seller.profile.submitted</code>. Một handler cho cả nộp lần đầu và nộp lại, vì hai trường hợp phát cùng một loại event.</p>
 *
 * <p>Chạy đồng bộ trong giao dịch của use case (qua <code>EventDispatcher</code>): <code>OutboxEventStore.store</code> đòi có
 * giao dịch, và handler ném lỗi thì rollback cả hồ sơ lẫn hàng hộp thư. Handler chỉ làm đúng một việc; không ghi kiểm toán, vì
 * kiểm toán của feature này chỉ ghi quyết định của quản trị.</p>
 */
@Component
public class SellerProfileSubmittedHandler implements EventHandler<SellerProfileSubmitted> {

    /** Cổng ghi hộp thư; từ chối khi không có giao dịch đang chạy (ADR-0008). */
    private final OutboxEventStore outboxEventStore;

    /**
     * @throws NullPointerException nếu <code>outboxEventStore</code> là <code>null</code>
     */
    public SellerProfileSubmittedHandler(OutboxEventStore outboxEventStore) {
        this.outboxEventStore = Objects.requireNonNull(outboxEventStore, "outboxEventStore");
    }

    @Override
    public void handle(SellerProfileSubmitted event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<SellerProfileSubmitted> getEventType() {
        return SellerProfileSubmitted.class;
    }
}

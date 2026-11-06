package vn.t3nexus.catalog.application.category.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.category.CategoryChanged;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

/** Ghi {@code EVT-CAT-03} (danh mục đổi) vào hộp thư, cùng giao dịch với dữ liệu và bản ghi kiểm toán. */
@Component
@RequiredArgsConstructor
public class CategoryChangedOutboxHandler implements EventHandler<CategoryChanged> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(CategoryChanged event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<CategoryChanged> getEventType() {
        return CategoryChanged.class;
    }
}

package vn.t3nexus.catalog.application.attributetemplate.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateChanged;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

/** Ghi {@code EVT-CAT-03} (mẫu đổi) vào hộp thư, cùng giao dịch với dữ liệu và bản ghi kiểm toán. */
@Component
@RequiredArgsConstructor
public class AttributeTemplateChangedOutboxHandler implements EventHandler<AttributeTemplateChanged> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(AttributeTemplateChanged event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<AttributeTemplateChanged> getEventType() {
        return AttributeTemplateChanged.class;
    }
}

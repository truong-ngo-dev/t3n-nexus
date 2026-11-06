package vn.t3nexus.lib.audit;

import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cổng ghi nhận hành động kiểm toán vào kho lưu trữ hộp thư outbox.
 * <br>Bảo đảm tính hợp lệ của hành động và các khóa dữ kiện trước khi ghi.
 */
public class AuditRecorder {

    private final OutboxEventStore store;
    private final AuditActionCatalog catalog;
    private final String sourceService;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public AuditRecorder(
            OutboxEventStore store,
            AuditActionCatalog catalog,
            String sourceService,
            Clock clock,
            IdGenerator idGenerator
    ) {
        if (!AuditContract.isSourceService(sourceService)) {
            throw new IllegalArgumentException("sourceService");
        }
        this.store = Objects.requireNonNull(store, "store");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.sourceService = sourceService;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public void record(String action, AuditActor actor, AuditTarget target, Map<String, Object> details) {
        Optional<AuditActionDeclaration> optDecl = catalog.find(action);
        if (optDecl.isEmpty()) {
            throw new IllegalArgumentException("Hành động chưa khai báo: " + action);
        }
        AuditActionDeclaration decl = optDecl.get();

        Map<String, Object> sanitizedDetails = AuditDetailsPolicy.strict(details);

        for (String k : sanitizedDetails.keySet()) {
            if (!decl.detailKeys().contains(k)) {
                throw new IllegalArgumentException("Khóa dữ kiện chưa khai báo cho hành động: " + k);
            }
        }

        String eventId = idGenerator.generate().toString();
        AuditActionRecorded event = new AuditActionRecorded(
                eventId,
                clock.instant(),
                sourceService,
                actor,
                action,
                target,
                sanitizedDetails
        );

        store.store(event);
    }
}

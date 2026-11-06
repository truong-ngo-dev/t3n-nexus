package vn.t3nexus.audit.application.auditentry;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryRepository;
import vn.t3nexus.lib.audit.AuditDetailsPolicy;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;

/**
 * Use case xử lý ghi nhận một bản ghi kiểm toán mới.
 * <br>Bảo đảm tính bất biến, khử trùng lặp sự kiện và ghi nhận chỉ số đo lường qua Micrometer.
 */
@Service
public class RecordAuditEntry implements CommandHandler<RecordAuditEntry.Command, RecordAuditEntry.Result> {

    private static final Logger log = LoggerFactory.getLogger(RecordAuditEntry.class);

    private final AuditEntryRepository auditEntryRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public RecordAuditEntry(
            AuditEntryRepository auditEntryRepository,
            IdGenerator idGenerator,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.auditEntryRepository = auditEntryRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        AuditEntry entry = AuditEntry.record(idGenerator.generate(), clock.instant(), command.record());
        boolean inserted = auditEntryRepository.insertIfAbsent(entry);

        if (inserted) {
            meterRegistry.counter("audit.entries.recorded", "sourceService", entry.sourceService()).increment();
            if (command.record().droppedDetails() != null) {
                for (AuditDetailsPolicy.Dropped dropped : command.record().droppedDetails()) {
                    meterRegistry.counter("audit.details.keys_dropped", "reason", dropped.reason().name()).increment();
                    log.warn("AUDIT_DETAILS_KEY_DROPPED sourceEventId={} key={} reason={}",
                            entry.sourceEventId(), dropped.key(), dropped.reason());
                }
            }
            log.info("AUDIT_ENTRY_RECORDED entryId={} action={} sourceService={}",
                    entry.id(), entry.action(), entry.sourceService());
        } else {
            meterRegistry.counter("audit.entries.duplicate").increment();
            log.info("AUDIT_DUPLICATE_IGNORED sourceEventId={}", entry.sourceEventId());
        }

        return new Result(inserted);
    }

    public record Command(AuditRecord record) {}

    public record Result(boolean recorded) {}
}

package vn.t3nexus.audit.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.t3nexus.audit.application.auditentry.RecordAuditEntry;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.audit.AuditRecordReader;
import vn.t3nexus.lib.audit.InvalidAuditRecordException;
import vn.t3nexus.lib.eventconsumer.EventEnvelopeConsumer;
import vn.t3nexus.lib.eventconsumer.NonRetryableEventException;

/**
 * Bộ lắng nghe Kafka nhận sự kiện kiểm toán từ các bounded context khác.
 * <br>Chuyển đổi sự kiện sang bản ghi kiểm toán và gọi use case ghi nhận.
 */
@Component
public class AuditActionRecordedListener {

    private final EventEnvelopeConsumer consumer;
    private final RecordAuditEntry recordAuditEntry;

    public AuditActionRecordedListener(EventEnvelopeConsumer consumer, RecordAuditEntry recordAuditEntry) {
        this.consumer = consumer;
        this.recordAuditEntry = recordAuditEntry;
    }

    @KafkaListener(topics = "${app.audit.topic}")
    public void on(String body) {
        consumer.consume(body, envelope -> {
            AuditRecord record;
            try {
                record = AuditRecordReader.read(envelope);
            } catch (InvalidAuditRecordException e) {
                throw new NonRetryableEventException(e.getMessage(), e);
            }
            recordAuditEntry.handle(new RecordAuditEntry.Command(record));
        });
    }
}

package vn.t3nexus.lib.eventconsumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuyển thông điệp hết lượt (hoặc không thể thử lại) sang một chủ đề hàng chờ lỗi của dịch vụ nhận (ADR-0010), giữ nguyên
 * khóa, giá trị và phân vùng do nhà sản xuất chọn theo khóa. Tiêu đề thêm: chủ đề gốc ({@code kafka_dlt-original-topic}),
 * phân vùng, offset gốc, và {@value #ERROR_CLASS_HEADER} (tên lớp lỗi). Không chép thông báo lỗi hay dấu vết ngăn xếp: thông báo của thư viện bên thứ ba có thể chứa dữ liệu
 * của người dùng.
 * <br>Sau khi gửi thành công: log ERROR mã {@value #CODE} và tăng bộ đếm {@value #METER}.
 */
final class DeadLetteringRecoverer implements ConsumerAwareRecordRecoverer {

    static final String CODE = "EVENT_DEAD_LETTERED";
    static final String METER = "events.dead_lettered";
    static final String ERROR_CLASS_HEADER = "x-dlq-error-class";

    private static final Logger log = LoggerFactory.getLogger(DeadLetteringRecoverer.class);

    private final DeadLetterPublishingRecoverer delegate;
    private final MeterRegistry meters;

    DeadLetteringRecoverer(KafkaOperations<Object, Object> template, String dlqTopic, MeterRegistry meters) {
        this.delegate = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(dlqTopic, -1));
        this.delegate.setExceptionHeadersCreator((headers, exception, isKey, names) ->
                headers.add(ERROR_CLASS_HEADER, errorClass(exception).getBytes(StandardCharsets.UTF_8)));
        this.meters = meters;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception exception) {
        try {
            delegate.accept(record, consumer, exception);
        } catch (RuntimeException publishFailure) {
            log.error("[EventConsumer] EVENT_DEAD_LETTER_FAILED topic={} partition={} offset={} eventId={} error={} publishError={}",
                    record.topic(), record.partition(), record.offset(), eventId(record), errorClass(exception),
                    publishFailure.getClass().getName());
            throw publishFailure;
        }
        log.error("[EventConsumer] {} topic={} partition={} offset={} eventId={} error={}",
                CODE, record.topic(), record.partition(), record.offset(), eventId(record), errorClass(exception));
        if (meters != null) {
            meters.counter(METER, "topic", record.topic()).increment();
        }
    }

    /** Tên lớp lỗi của handler, bỏ lớp bọc của listener. */
    static String errorClass(Throwable exception) {
        Throwable t = exception;
        while (t instanceof ListenerExecutionFailedException && t.getCause() != null) {
            t = t.getCause();
        }
        return t.getClass().getName();
    }

    private static final Pattern EVENT_ID_IN_BODY = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]{1,100})\"");

    /**
     * Mã event lấy từ tiêu đề {@code id} do bộ chuyển hộp thư đặt; thiếu thì thử đọc trường {@code eventId} trong nội dung
     * (chỉ trích đúng một giá trị ngắn, không đọc gì khác); không có thì "unknown".
     */
    static String eventId(ConsumerRecord<?, ?> record) {
        Header h = record.headers().lastHeader("id");
        if (h != null) {
            return new String(h.value(), StandardCharsets.UTF_8);
        }
        if (record.value() instanceof String body) {
            Matcher m = EVENT_ID_IN_BODY.matcher(body);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "unknown";
    }
}

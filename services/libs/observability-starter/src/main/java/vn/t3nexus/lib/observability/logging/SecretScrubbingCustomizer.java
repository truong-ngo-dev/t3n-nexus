package vn.t3nexus.lib.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Lưới an toàn cuối ở đầu ra log JSON: che token {@code Bearer}, JWT và mã băm bcrypt còn sót trong {@code message}, {@code error.message} và
 * {@code error.stack_trace}. Lớp phòng thủ thứ nhất vẫn là các kiểu giá trị tự che và quy ước không đưa bí mật vào log.
 */
public class SecretScrubbingCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.applyingValueProcessor(
                JsonWriter.ValueProcessor.of(String.class, SecretPatterns::scrub)
                        .whenHasPath(path -> {
                            String name = path.toUnescapedString();
                            return name.equals("message") || name.equals("error.message") || name.equals("error.stack_trace");
                        }));
    }
}

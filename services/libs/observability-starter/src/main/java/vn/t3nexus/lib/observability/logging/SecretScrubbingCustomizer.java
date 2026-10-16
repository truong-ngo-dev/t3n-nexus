package vn.t3nexus.lib.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

/**
 * Lưới an toàn cuối ở đầu ra log JSON: che token {@code Bearer}, JWT và mã băm bcrypt còn sót trong {@code message}, {@code error.message} và
 * {@code error.stack_trace}. Lớp phòng thủ thứ nhất vẫn là các kiểu giá trị tự che và quy ước không đưa bí mật vào log.
 * <br>Khi gỡ lỗi có thể tắt riêng lớp này bằng {@link #REVEAL_PROPERTY} để thấy nguyên giá trị trong message; cờ chỉ được phép ở profile
 * {@code dev} (kiểm ở {@code ObservabilityEnvironmentPostProcessor}). Việc che nội dung yêu cầu HTTP ({@code BodySanitizer}) không bị ảnh hưởng.
 */
public class SecretScrubbingCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    public static final String REVEAL_PROPERTY = "observability.logging.reveal-secrets";

    private final boolean reveal;

    public SecretScrubbingCustomizer(Environment environment) {
        this.reveal = environment.getProperty(REVEAL_PROPERTY, Boolean.class, false);
    }

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        if (reveal) return;
        members.applyingValueProcessor(
                JsonWriter.ValueProcessor.of(String.class, SecretPatterns::scrub)
                        .whenHasPath(path -> {
                            String name = path.toUnescapedString();
                            return name.equals("message") || name.equals("error.message") || name.equals("error.stack_trace");
                        }));
    }
}

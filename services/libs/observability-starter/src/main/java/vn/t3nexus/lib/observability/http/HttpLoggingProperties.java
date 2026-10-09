package vn.t3nexus.lib.observability.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Set;

/**
 * Cấu hình log yêu cầu HTTP ({@code observability.logging.http.*}). Nạp một lần lúc khởi động.
 *
 * @param enabled        bật bộ lọc ghi dòng hoàn tất
 * @param maxBodyBytes   số byte tối đa đệm từ nội dung yêu cầu để ghi khi lỗi
 * @param maxValueLength độ dài tối đa của một giá trị chuỗi trong nội dung ghi ra
 * @param maxArrayItems  số phần tử tối đa giữ lại của một mảng
 * @param noBodyStatuses mã trạng thái 4xx không ghi nội dung (lỗi không liên quan tới đầu vào của người gọi)
 * @param maskExact      tên trường (chữ thường) luôn che khi khớp đúng
 * @param maskContains   mảnh tên (chữ thường) che khi tên trường chứa nó
 * @param ignorePaths    tiền tố đường dẫn không ghi dòng hoàn tất
 */
@ConfigurationProperties("observability.logging.http")
public record HttpLoggingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("4096") int maxBodyBytes,
        @DefaultValue("200") int maxValueLength,
        @DefaultValue("3") int maxArrayItems,
        @DefaultValue({"401", "403", "404", "405", "429"}) Set<Integer> noBodyStatuses,
        @DefaultValue({"otp", "pin", "pass", "cvc", "cvv"}) Set<String> maskExact,
        @DefaultValue({"password", "passwd", "secret", "token", "authorization", "apikey", "api_key", "credential", "cardnumber"})
        List<String> maskContains,
        @DefaultValue("/actuator") List<String> ignorePaths) {

    public BodySanitizer.Options sanitizerOptions() {
        return new BodySanitizer.Options(maxBodyBytes, maxValueLength, maxArrayItems, 1000, maskExact, maskContains);
    }
}

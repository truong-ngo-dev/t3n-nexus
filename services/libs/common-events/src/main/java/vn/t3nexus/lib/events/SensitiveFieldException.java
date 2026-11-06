package vn.t3nexus.lib.events;

/**
 * Nội dung event có trường mang tên thuộc loại bí mật hoặc dữ liệu định danh. Là lỗi lập trình của bên phát.
 * Thông báo nêu đường dẫn của trường, không bao giờ nêu giá trị.
 */
public class SensitiveFieldException extends IllegalArgumentException {

    private final String fieldPath;

    public SensitiveFieldException(String fieldPath) {
        super("Trường nhạy cảm không được có trong nội dung event: " + fieldPath);
        this.fieldPath = fieldPath;
    }

    public String getFieldPath() {
        return fieldPath;
    }
}

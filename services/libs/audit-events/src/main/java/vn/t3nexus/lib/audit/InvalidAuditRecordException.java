package vn.t3nexus.lib.audit;

/**
 * Ngoại lệ ném ra khi cấu trúc bản ghi kiểm toán không hợp lệ.
 */
public class InvalidAuditRecordException extends RuntimeException {

    private final String field;

    public InvalidAuditRecordException(String field) {
        super("Bản ghi kiểm toán không hợp lệ: " + field);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

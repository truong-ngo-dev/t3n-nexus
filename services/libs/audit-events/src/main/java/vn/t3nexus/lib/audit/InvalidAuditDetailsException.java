package vn.t3nexus.lib.audit;

/**
 * Ngoại lệ ném ra khi dữ kiện kiểm toán bổ sung không thỏa mãn chính sách nghiêm ngặt.
 */
public class InvalidAuditDetailsException extends IllegalArgumentException {

    public InvalidAuditDetailsException(String message) {
        super(message);
    }
}

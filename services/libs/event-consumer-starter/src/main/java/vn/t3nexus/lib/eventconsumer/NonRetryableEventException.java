package vn.t3nexus.lib.eventconsumer;

/**
 * Handler ném khi event đến được nhưng vô nghĩa với nó (không thể xử lý dù thử lại bao nhiêu lần). Bộ nhận chuyển thẳng
 * sang hàng chờ lỗi, không thử lại. Lỗi tạm (cơ sở dữ liệu, mạng) thì ném lỗi thường để được thử lại.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

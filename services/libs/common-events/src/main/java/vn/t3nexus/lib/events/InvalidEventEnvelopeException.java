package vn.t3nexus.lib.events;

/**
 * Thông điệp nhận được không phải một khung event hợp lệ (không phải JSON, không phải đối tượng, sai kiểu, thiếu
 * trường bắt buộc). Thử lại không làm nó đúng lên: bộ nhận chuyển thẳng sang hàng chờ lỗi.
 * <br>Thông báo chỉ nêu tên trường, không bao giờ chép nội dung thông điệp hay thông báo của thư viện bên thứ ba
 * (có thể chứa dữ liệu của người dùng).
 */
public class InvalidEventEnvelopeException extends RuntimeException {

    public InvalidEventEnvelopeException(String message) {
        super(message);
    }
}

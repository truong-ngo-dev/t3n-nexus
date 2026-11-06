package vn.t3nexus.lib.upload;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Cổng tới kho đối tượng (ADR-0013). Cài đặt thật dùng MinIO; test dùng bản giả trong bộ nhớ.
 * Mọi lỗi hạ tầng ném {@link ObjectStoreException}.
 */
public interface ObjectStore {

    /** Form POST đã ký: ép khóa đúng một giá trị, {@code Content-Type} đúng loại, dung lượng 1..maxSize, hết hạn sau ttl. */
    PostForm presignPost(String bucket, String key, String contentType, long maxSize, Duration ttl);

    /** Thông tin đối tượng; rỗng nếu chưa có. */
    Optional<StoredObject> stat(String bucket, String key);

    /** Đọc tối đa {@code length} byte đầu của đối tượng. */
    byte[] readHead(String bucket, String key, int length);

    /** Xóa đối tượng; không có thì coi như xong. */
    void delete(String bucket, String key);

    /** Tạo bucket nếu chưa có; đặt policy đọc công khai nếu {@code publicRead}. */
    void ensureBucket(String bucket, boolean publicRead);

    record PostForm(String url, Map<String, String> fields) {
    }

    record StoredObject(long size, String contentType) {
    }
}

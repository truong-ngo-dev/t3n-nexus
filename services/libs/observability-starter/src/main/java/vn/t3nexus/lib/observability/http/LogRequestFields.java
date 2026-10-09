package vn.t3nexus.lib.observability.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đặt trên method của controller xử lý đường dẫn nhạy cảm: khi nội dung yêu cầu được ghi vào log (lỗi), chỉ các trường cấp cao nhất
 * liệt kê ở đây được giữ, trường khác (kể cả trường thêm sau này) bị bỏ. Ví dụ {@code @LogRequestFields("email")} cho đăng ký để mật khẩu
 * không bao giờ có trong log. Không có chú thích thì chỉ áp dụng lớp che theo tên trường.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogRequestFields {

    /** Các trường cấp cao nhất được phép ghi. */
    String[] value();
}

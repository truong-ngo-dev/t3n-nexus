package vn.t3nexus.lib.webbff;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Đặt trên lớp khởi động của mỗi BFF web; kèm {@code bff.app-name}, client OAuth2 và {@code bff.routes.*} trong cấu hình. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(WebBffConfiguration.class)
public @interface EnableWebBff {
}

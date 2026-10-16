package vn.t3nexus.oauth2.infrastructure.security.oauth2.store;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin cho {@code LoginRequestDetails}: {@code details} của token đã xác thực được lưu trong thuộc tính của bản ghi
 * ủy quyền, nên {@code JdbcOAuth2AuthorizationService} cần biết kiểu để ghi và đọc lại.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public abstract class LoginRequestDetailsMixin {
}

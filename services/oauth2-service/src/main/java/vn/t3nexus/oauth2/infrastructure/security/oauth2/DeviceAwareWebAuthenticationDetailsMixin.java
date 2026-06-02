package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin cho DeviceAwareWebAuthenticationDetails.
 * Cần thiết để JdbcOAuth2AuthorizationService serialize/deserialize đúng type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public abstract class DeviceAwareWebAuthenticationDetailsMixin {
}

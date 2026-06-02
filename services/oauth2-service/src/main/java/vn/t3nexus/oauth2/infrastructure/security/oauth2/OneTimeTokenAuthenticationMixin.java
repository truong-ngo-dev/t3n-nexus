package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import com.fasterxml.jackson.annotation.*;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Jackson mixin for OneTimeTokenAuthentication.
 * Required because CoreJacksonModule does not register this type in BasicPolymorphicTypeValidator.
 * Registered manually via OAuth2AuthorizationServerConfig so JdbcOAuth2AuthorizationService
 * can round-trip MFA sessions through the oauth2_authorizations table.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonAutoDetect(
        fieldVisibility  = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class OneTimeTokenAuthenticationMixin {

    @JsonCreator
    OneTimeTokenAuthenticationMixin(
            @JsonProperty("principal")   Object principal,
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities) {
    }
}

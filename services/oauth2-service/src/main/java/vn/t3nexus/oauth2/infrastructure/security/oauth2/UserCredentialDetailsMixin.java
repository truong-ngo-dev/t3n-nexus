package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import com.fasterxml.jackson.annotation.*;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Jackson mixin for UserCredentialDetails.
 * UserCredentialDetails extends Spring Security's User but adds userId/email/mfaEnabled fields
 * that UserDeserializer does not know about. Without this mixin, polymorphic deserialization
 * from JdbcOAuth2AuthorizationService fails because UserCredentialDetails is not in CoreJacksonModule's
 * allowIfSubType list (only the User base class handler is registered there).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonAutoDetect(
        fieldVisibility    = JsonAutoDetect.Visibility.ANY,
        getterVisibility   = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class UserCredentialDetailsMixin {

    @JsonCreator
    UserCredentialDetailsMixin(
            @JsonProperty("userId")               String userId,
            @JsonProperty("email")                String email,
            @JsonProperty("password")             String password,
            @JsonProperty("enabled")              boolean enabled,
            @JsonProperty("accountNonExpired")    boolean accountNonExpired,
            @JsonProperty("credentialsNonExpired") boolean credentialsNonExpired,
            @JsonProperty("accountNonLocked")     boolean accountNonLocked,
            @JsonProperty("authorities")          Collection<? extends GrantedAuthority> authorities,
            @JsonProperty("mfaEnabled")           boolean mfaEnabled) {
    }
}

package vn.t3nexus.oauth2.infrastructure.security.oauth2.validator;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

/**
 * Bắt buộc tham số {@code state} ở yêu cầu cấp mã (KT-30, ADR-0005): Spring Authorization Server không ép tham số này, nên thiếu
 * {@code state} thì bên gọi mất lớp chống giả mạo yêu cầu chéo trang của luồng mã ủy quyền. Lỗi được trả về qua chuyển hướng tới
 * {@code redirect_uri} đã đăng ký ({@code invalid_request}), nối sau các kiểm tra mặc định về {@code redirect_uri} và {@code scope}.
 */
public final class StateRequiredValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";

    /** Bộ kiểm tra mặc định của Spring (redirect_uri, scope) cộng thêm việc bắt buộc state. */
    public static Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> withDefaults() {
        return OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
                .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR)
                .andThen(new StateRequiredValidator());
    }

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        if (StringUtils.hasText(request.getState())) return;

        String redirectUri = StringUtils.hasText(request.getRedirectUri()) ? request.getRedirectUri()
                : context.getRegisteredClient().getRedirectUris().iterator().next();
        OAuth2AuthorizationCodeRequestAuthenticationToken result = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                request.getAuthorizationUri(), request.getClientId(), (Authentication) request.getPrincipal(), redirectUri,
                request.getState(), request.getScopes(), request.getAdditionalParameters());
        result.setAuthenticated(true);
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 Parameter: " + OAuth2ParameterNames.STATE, ERROR_URI),
                result);
    }
}

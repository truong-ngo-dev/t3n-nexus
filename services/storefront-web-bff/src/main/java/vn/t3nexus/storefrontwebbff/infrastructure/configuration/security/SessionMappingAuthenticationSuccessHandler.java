package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Sau khi đăng nhập thành công (kể cả xác thực im lặng, `tech.md` T6): đặt lại hạn phiên BFF (mỗi lần lưu token mới), rồi ánh xạ hai chiều
 * {@code sid} ↔ phiên BFF qua {@link SessionMappingStore}. {@code sid} đọc từ token truy cập; thiếu thì không ánh xạ, ghi cảnh báo và vẫn
 * cho đăng nhập xong. Nếu {@code sid} đang trỏ tới một phiên BFF khác thì phiên cũ bị xóa (một {@code sid} một phiên BFF).
 */
@Slf4j
@Component
public class SessionMappingAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final AccessTokenClaims                      claims;
    private final SessionMappingStore                    mappings;
    private final ReactiveSessionRepository<? extends Session> sessions;
    private final ServerCsrfTokenRepository              csrfTokens;
    private final ServerAuthenticationSuccessHandler     delegate;

    public SessionMappingAuthenticationSuccessHandler(
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
            AccessTokenClaims claims,
            SessionMappingStore mappings,
            ReactiveSessionRepository<? extends Session> sessions,
            ServerCsrfTokenRepository csrfTokens,
            @Value("${app.login.success-redirect-uri:/}") String successRedirectUri) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.claims                     = claims;
        this.mappings                   = mappings;
        this.sessions                   = sessions;
        this.csrfTokens                 = csrfTokens;
        this.delegate                   = new RedirectServerAuthenticationSuccessHandler(successRedirectUri);
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return delegate.onAuthenticationSuccess(webFilterExchange, authentication);
        }

        return authorizedClientRepository
                .loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), authentication, webFilterExchange.getExchange())
                .cast(OAuth2AuthorizedClient.class)
                .flatMap(client -> webFilterExchange.getExchange().getSession().flatMap(session -> {
                    session.setMaxIdleTime(mappings.sessionTtl());
                    String sid = claims.text(client.getAccessToken().getTokenValue(), AccessTokenClaims.SID);
                    if (sid == null) {
                        log.warn("[SessionMapping] sid claim missing from access token — mapping skipped");
                        return Mono.<Void>empty();
                    }
                    return mappings.link(sid, session.getId())
                            .flatMap(previous -> {
                                log.info("[SessionMapping] sid={} re-linked, removing previous BFF session {}", sid, previous);
                                return sessions.deleteById(previous);
                            });
                }))
                // Phiên BFF mới (kể cả xác thực im lặng): xoay mã CSRF; mã cũ thuộc về phiên đã mất (`tech.md` T4)
                .then(Mono.defer(() -> csrfTokens.generateToken(webFilterExchange.getExchange())
                        .flatMap(csrf -> csrfTokens.saveToken(webFilterExchange.getExchange(), csrf))))
                .then(delegate.onAuthenticationSuccess(webFilterExchange, authentication));
    }
}

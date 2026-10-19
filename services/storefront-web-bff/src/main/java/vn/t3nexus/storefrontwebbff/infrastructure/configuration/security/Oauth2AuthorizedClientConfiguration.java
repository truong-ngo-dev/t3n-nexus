package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.util.LinkedMultiValueMap;

import java.time.Duration;

@Configuration
public class Oauth2AuthorizedClientConfiguration {

    @Bean
    public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    /**
     * Bộ quản lý ủy quyền client của BFF (thay bean mặc định mà {@code TokenRelay} dùng, `tech.md` T5): yêu cầu làm mới kèm {@code sid}
     * đọc từ token truy cập hiện có (Xác thực cần {@code sid} để biết phiên nào), độ lệch đồng hồ
     * {@code app.security.client.clock-skew} (5 giây, thay 60 giây mặc định), bọc bởi {@link SessionAuthorizedClientManager}
     * (tuần tự hóa theo phiên, phân loại lỗi).
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository registrations,
            ServerOAuth2AuthorizedClientRepository clients,
            AccessTokenClaims claims,
            SessionMappingStore mappings,
            @Value("${app.security.client.clock-skew:5s}") Duration clockSkew) {

        WebClientReactiveRefreshTokenTokenResponseClient refreshClient = new WebClientReactiveRefreshTokenTokenResponseClient();
        refreshClient.addParametersConverter(grantRequest -> {
            var parameters = new LinkedMultiValueMap<String, String>();
            String sid = claims.text(grantRequest.getAccessToken().getTokenValue(), AccessTokenClaims.SID);
            if (sid != null) parameters.add(AccessTokenClaims.SID, sid);
            return parameters;
        });

        ReactiveOAuth2AuthorizedClientProvider provider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken(refresh -> refresh.accessTokenResponseClient(refreshClient).clockSkew(clockSkew))
                .build();

        DefaultReactiveOAuth2AuthorizedClientManager manager = new DefaultReactiveOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(provider);
        return new SessionAuthorizedClientManager(manager, mappings);
    }
}

package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Kiểm một {@code id_token_hint} (đăng xuất): token phải do chính máy chủ này ký (chữ ký, issuer). <b>Không kiểm hạn</b>: chuẩn OIDC
 * cho phép dùng ID token đã hết hạn làm gợi ý khi đăng xuất, và ID token của ta chỉ sống bằng access token (15 phút).
 */
public interface IdTokenHintVerifier {

    /** Claim của token nếu chữ ký và issuer đúng; rỗng nếu không phải JWT, ký sai hoặc sai issuer. */
    Optional<Jwt> verify(String idTokenHint);
}

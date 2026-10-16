package vn.t3nexus.oauth2.domain.login_session;

/** Mã client đã đăng ký của một ứng dụng (Cửa hàng, Người bán, Vận hành, Shipper). */
public record ClientId(String value) {

    public ClientId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ClientId value is required");
    }
}

package vn.t3nexus.oauth2.domain.session;

public interface OAuthSessionExpiryService {

    /**
     * Tìm tất cả OAuthSession có IDP session không còn tồn tại,
     * xóa chúng cùng OAuth2Authorization tương ứng,
     * và trả về một event tổng hợp để dispatch.
     */
    SessionsBulkExpiredEvent expireOrphaned();
}

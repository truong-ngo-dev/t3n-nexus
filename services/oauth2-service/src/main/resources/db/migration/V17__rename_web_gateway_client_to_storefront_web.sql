-- Feature 06 bước 4b: đổi tên BFF kênh web của ứng dụng Cửa hàng từ "web-gateway" thành "storefront-web-bff" (ADR-0006).
-- Client OAuth2: mã công khai web-gateway -> storefront-web, mã nội bộ web-gateway-client-id -> storefront-web-client-id.
-- Đổi tại chỗ (giữ cài đặt, bí mật, hạn token); không sửa V4, V9, V10, V11 vì Flyway so tổng kiểm các migration đã chạy.
-- Dòng tham chiếu mã nội bộ cũ (phiên ứng dụng, bản ghi ủy quyền, đồng ý) đổi theo; chưa có thì không đổi gì.
-- Phạm vi token dịch vụ của oauth2-service-internal: webgw.internal -> storefront.internal.

UPDATE oauth2_registered_client
SET id            = 'storefront-web-client-id',
    client_id     = 'storefront-web',
    client_name   = 'Storefront Web BFF',
    redirect_uris = replace(redirect_uris, '/code/web-gateway', '/code/storefront-web')
WHERE id = 'web-gateway-client-id';

UPDATE client_sessions
SET client_id = 'storefront-web-client-id'
WHERE client_id = 'web-gateway-client-id';

UPDATE oauth2_authorization
SET registered_client_id = 'storefront-web-client-id'
WHERE registered_client_id = 'web-gateway-client-id';

UPDATE oauth2_authorization_consent
SET registered_client_id = 'storefront-web-client-id'
WHERE registered_client_id = 'web-gateway-client-id';

UPDATE oauth2_registered_client
SET scopes = replace(scopes, 'webgw.internal', 'storefront.internal')
WHERE client_id = 'oauth2-service-internal';

-- Route web-gateway's OAuth2 callback through api-gateway (single entry point, adr/009-mobile-gateway.md)
-- redirect_uri cũ: http://localhost:8090/login/oauth2/code/web-gateway (thẳng web-gateway, trước khi có api-gateway)
-- redirect_uri mới: http://localhost:8000/web/login/oauth2/code/web-gateway (qua api-gateway, strip /web)
-- post_logout_redirect_uris giữ nguyên — trỏ thẳng Angular, không qua gateway nào.
UPDATE oauth2_registered_client
SET redirect_uris = 'http://localhost:8000/web/login/oauth2/code/web-gateway'
WHERE client_id = 'web-gateway';

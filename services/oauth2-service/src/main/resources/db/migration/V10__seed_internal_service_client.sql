-- Seed: RegisteredClient cho service-to-service call (oauth2-service → web-gateway back-channel
-- revoke). client_credentials grant — không có user, không có redirect flow.
-- Đổi client_secret qua env/config thật trước khi lên môi trường thật (dev default để test local).
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_id_issued_at,
    client_secret,
    client_secret_expires_at,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'oauth2-service-internal-client-id',
    'oauth2-service-internal',
    CURRENT_TIMESTAMP,
    '{noop}changeme-internal-client-secret',
    NULL,
    'oauth2-service (internal, service-to-service)',
    'client_secret_basic',
    'client_credentials',
    NULL,
    NULL,
    'webgw.internal',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"}}'
);

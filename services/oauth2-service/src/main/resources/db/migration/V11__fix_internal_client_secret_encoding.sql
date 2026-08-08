-- V10 seed nhầm client_secret dạng '{noop}...' — SecurityConfiguration.passwordEncoder() của
-- oauth2-service trả về BCryptPasswordEncoder TRẦN (không phải DelegatingPasswordEncoder), không
-- hiểu prefix {noop} chút nào, nên client_credentials grant cho client này luôn fail
-- "invalid_client" ngay ở bước xác thực client, trước khi chạm tới scope/grant type nào cả.
--
-- Cùng loại lỗi client "web-gateway" (V4) từng dính — đã được vá thủ công trực tiếp trong DB
-- (client_secret hiện tại của web-gateway là bcrypt hash thật, không khớp '{noop}secret' còn ghi
-- trong V4) mà không có migration nào ghi lại — nay sửa đúng cách qua migration cho client mới.
--
-- Hash bcrypt (cost 10) của 'changeme-internal-client-secret', generate bằng chính
-- BCryptPasswordEncoder(10) — verify khớp trước khi đưa vào migration.
UPDATE oauth2_registered_client
SET client_secret = '$2a$10$33kovGAVuMcVbO9xMxLrD.hvo7snDx3GN.Gz2IKIu4e9VpQopH2nK'
WHERE client_id = 'oauth2-service-internal';

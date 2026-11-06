-- Nạp bản sao trạng thái người bán cho catalog-service (feature 09), chạy TAY, ngoài Flyway.
-- Dùng khi người bán đã được duyệt TRƯỚC khi catalog có bộ nhận seller.profile.approved (dữ liệu dev có sẵn): thiếu bản sao thì
-- catalog từ chối tạo nháp (SELLER_NOT_APPROVED) và đăng (SELLER_NOT_APPROVED trong danh sách điều kiện thiếu).
-- Chạy lại vô hại: chỉ ghi khi version lớn hơn bản đã lưu (như bộ nhận), nên không ghi đè bản sao mới hơn do sự kiện thật.
--
--   docker exec -i <container-postgres-catalog> psql -U <user> -d catalog_db \
--     -v account='<mã tài khoản người bán, UUID>' [-v version=0] < catalog-seller-state.sql
--
-- `version` mặc định 0: sự kiện thật (version hồ sơ >= 1) luôn ghi đè được. Script không chứa mật khẩu hay dữ liệu cá nhân.
\set ON_ERROR_STOP on
\if :{?account}
\else
  \echo 'LỖI: thiếu -v account=<UUID>'
  \quit 64
\endif
\if :{?version}
\else
  \set version 0
\endif

INSERT INTO seller_state (account_id, status, version, updated_at)
VALUES (:'account'::uuid, 'APPROVED', :version, now())
ON CONFLICT (account_id) DO UPDATE
   SET status = EXCLUDED.status, version = EXCLUDED.version, updated_at = EXCLUDED.updated_at
 WHERE seller_state.version < EXCLUDED.version;

SELECT 'seed-catalog-seller-state: ' || account_id || ' ' || status || ' v' || version AS result
  FROM seller_state WHERE account_id = :'account'::uuid;

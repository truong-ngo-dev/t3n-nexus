-- Dữ liệu khởi tạo: tài khoản Quản trị (oauth2-service, feature 04).
-- Chạy tay một lần khi dựng môi trường, SAU KHI Flyway đã tạo bảng. Không phải migration, không có trong mã dịch vụ.
-- Chạy lại vô hại: email đã có thì không thêm, không ghi đè mật khẩu.
--
--   psql -v email='quantri@t3nexus.com.vn' -v hash='<chuỗi bcrypt>' [-v domain='t3nexus.com.vn'] -f admin.sql
--
--   email  : email Quản trị, phải thuộc miền nội bộ (chuẩn hóa chữ thường, cắt khoảng trắng).
--   hash   : mật khẩu ĐÃ BĂM bcrypt (dạng $2a$10$...), không phải mật khẩu thô. Cách băm: xem README.md.
--   domain : miền nội bộ, mặc định t3nexus.com.vn; phải là một trong các miền của INTERNAL_EMAIL_DOMAINS của dịch vụ.
--
-- Lỗi ở bất kỳ kiểm tra nào thì dừng, mã thoát khác 0, không ghi gì.

\set ON_ERROR_STOP on

-- Thiếu tham số thì coi như rỗng; các kiểm tra bên dưới sẽ từ chối.
\if :{?email}
\else
  \echo 'seed-admin: thiếu -v email=...'
  \set email ''
\endif
\if :{?hash}
\else
  \echo 'seed-admin: thiếu -v hash=...'
  \set hash ''
\endif
\if :{?domain}
\else
  \set domain 't3nexus.com.vn'
\endif

-- Kiểm tra đầu vào (tính trước, chưa ghi gì).
SELECT lower(btrim(:'domain')) AS domain_norm,
       (lower(btrim(:'domain')) ~ '^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$') AS domain_ok
\gset

SELECT lower(btrim(:'email')) AS email_norm,
       -- đúng miền nội bộ hoặc miền con của nó, không so khớp chuỗi con (giống ConfiguredInternalEmailPolicy)
       (lower(btrim(:'email')) ~ ('^[^@[:space:]]+@([^@[:space:]]+\.)?' || replace(:'domain_norm', '.', '\.') || '$')) AS email_ok,
       (:'hash' ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$') AS hash_ok
\gset

\if :domain_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'seed-admin: domain không hợp lệ'; END $$;
\endif
\if :email_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'seed-admin: email rỗng hoặc không thuộc miền nội bộ (INV-ATH-07)'; END $$;
\endif
\if :hash_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'seed-admin: hash không phải chuỗi bcrypt; không nhập mật khẩu thô (INV-ATH-02)'; END $$;
\endif

-- Một câu ghi duy nhất, nên tự nó là một giao dịch. id là UUID v7: 48 bit mili giây hiện tại ghép vào UUID ngẫu nhiên,
-- bật hai bit 52, 53 của byte 6 để nibble phiên bản từ 0100 (v4) thành 0111 (v7); Postgres 16 chưa có uuidv7().
WITH ins AS (
    INSERT INTO user_accounts (id, email, password_hash, role, registration_method, status, mfa_enabled, created_at, updated_at)
    VALUES (encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid())
                                           placing substring(int8send((extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3)
                                           FROM 1 FOR 6), 52, 1), 53, 1), 'hex')::uuid,
            :'email_norm', :'hash', 'ADMIN', 'CREDENTIAL', 'ACTIVE', false, now(), now())
    ON CONFLICT (email) DO NOTHING
    RETURNING id
)
SELECT count(*) AS created FROM ins
\gset

\if :created
  \echo 'seed-admin: created - đã tạo tài khoản Quản trị' :email_norm
\else
  -- Đã có tài khoản email này: chỉ báo vai hiện có, không sửa (đổi vai trái INV-ATH-09).
  SELECT role AS existing_role FROM user_accounts WHERE email = :'email_norm'
  \gset
  \set msg 'seed-admin: exists - đã có tài khoản email này, không thêm, role=' :existing_role
  \echo :msg
\endif

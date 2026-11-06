-- Khởi tạo hai tài khoản cho cơ sở dữ liệu Nhật ký kiểm toán (ADR-0009). Chạy một lần khi cơ sở dữ liệu mới được tạo, bằng
-- tài khoản quản trị của bộ chứa (docker-compose dùng chung tệp này cho môi trường dev; test dùng chính tệp này).
--   audit_owner: chủ cơ sở dữ liệu và chủ bảng; chỉ dùng để chạy migration. Không phải superuser.
--   audit_app:   tài khoản chạy dịch vụ; migration chỉ cấp INSERT, SELECT. Không sở hữu gì, không tạo được đối tượng.
-- Mật khẩu dưới đây chỉ dùng cho dev và test.
CREATE ROLE audit_owner LOGIN PASSWORD 'audit_owner' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE audit_app LOGIN PASSWORD 'audit_app' NOSUPERUSER NOCREATEDB NOCREATEROLE;
ALTER DATABASE audit_db OWNER TO audit_owner;

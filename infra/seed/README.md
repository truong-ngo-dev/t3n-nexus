# Dữ liệu khởi tạo (seed)

Script chạy **tay** khi dựng môi trường, ngoài Flyway. Không chứa mật khẩu hay hash thật; mỗi môi trường dùng một mật khẩu riêng.

## `admin.sql`: tài khoản Quản trị của `oauth2-service`

Thứ tự: dịch vụ đã khởi động một lần (Flyway tạo bảng `user_accounts`) → chạy script. Chạy lại vô hại: email đã có thì không thêm, không ghi đè mật khẩu.

### 1. Băm mật khẩu (ở máy của bạn, mật khẩu thô không vào script)

Chọn mật khẩu theo luật: 8 đến 64 ký tự, chỉ `A-Za-z0-9@$`, có chữ và số. Băm bcrypt hệ số 10:

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'MatKhauCuaBan1' | cut -d: -f2 | sed 's/^\$2y/$2a/'
```

Kết quả là chuỗi 60 ký tự dạng `$2a$10$...`, giá trị của `hash`. Lần đầu Docker tải image và in tiến trình ra stderr: đừng gộp stderr vào lệnh (`2>&1`), nếu không tiến trình lẫn vào hash (script sẽ từ chối vì không phải bcrypt).

### 2. Chạy script (không cần cài `psql`, dùng container Postgres đang chạy)

```bash
docker exec -i <tên-container-postgres> psql -U <user> -d <database> \
  -v email='quantri@t3nexus.com.vn' -v hash='<chuỗi bcrypt>' < admin.sql
```

Tham số `-f` thay bằng `< admin.sql` vì tệp nằm ngoài container. Tham số tùy chọn `-v domain='...'` (mặc định `t3nexus.com.vn`) phải là một trong các miền của biến môi trường `INTERNAL_EMAIL_DOMAINS` của dịch vụ; email Quản trị không thuộc miền đó sẽ bị từ chối.

Script in `seed-admin: created ...` khi tạo, `seed-admin: exists ... role=...` khi đã có (không sửa dòng đó, kể cả khi vai không phải `ADMIN`). Lỗi (thiếu tham số, email ngoài miền nội bộ, hash không phải bcrypt) thì dừng với mã thoát khác 0 và không ghi gì.

## `catalog.sql`: bộ mẫu cấu trúc danh mục của `catalog-service`

Dữ liệu demo: 10 thương hiệu, 13 mẫu thuộc tính (35 tùy chọn), cây danh mục 3 ngành / 6 cấp hai / 13 lá, 57 dòng gán thuộc tính cho lá. Có bộ này thì người bán chọn được danh mục lá, điền thuộc tính, chọn thương hiệu ngay sau khi dựng môi trường. Nội dung đầy đủ và lý do: `docs/stage/c1a-listing/06-category-seed-data/tech.md` T2.

Thứ tự: dịch vụ đã khởi động một lần (Flyway tạo bảng) → chạy script. Không tham số. Chạy vào cơ sở dữ liệu của `catalog-service` (`catalog_db`), **không** vào cơ sở dữ liệu của dịch vụ khác.

```bash
docker exec -i <tên-container-postgres-catalog> psql -U <user> -d catalog_db < catalog.sql
```

Một giao dịch: hoặc đủ cả bộ mẫu, hoặc không có gì. Script in:

- `seed-catalog: created - đã nạp bộ mẫu: brand=10 ...` khi đã nạp;
- `seed-catalog: exists - ...` khi `brand`, `attribute_template` hoặc `category` đã có dòng: **không thêm gì** (kể cả khi Quản trị đã tự tạo dữ liệu hay đã đổi, ngừng dùng một mục mẫu; script không "bổ sung phần thiếu" để không nạp lại thứ họ đã bỏ). Mã thoát 0;
- lỗi `seed-catalog: thiếu bảng ...` khi chưa chạy Flyway hoặc nhầm cơ sở dữ liệu; mã thoát khác 0, không ghi gì.

Dữ liệu khởi tạo không phát event và không ghi kiểm toán (như tài khoản Quản trị). Sau khi nạp, dữ liệu là dữ liệu sống bình thường: Quản trị đổi qua API của `catalog-service`. Không có migration chứa dữ liệu này.

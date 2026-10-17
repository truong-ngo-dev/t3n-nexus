# Feature 04: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho cả BC hoặc mọi BC nằm ở ADR; file này ghi chi tiết riêng của feature.
> **Chốt `2026-10-17` (bước 2 của `plan.md`).** Điểm T3 và T5 đã chạy thử bằng test ở bước 4; T2 (lệnh băm) và dạng chạy qua stdin ở README đã kiểm khi chạy thật ở bước 6.

## Điều kiện của môi trường (đã kiểm)

- Postgres của dự án là bản 16 (`infra/docker-compose.yml`): **chưa có** hàm `uuidv7()` (có từ bản 18).
- Máy phát triển **không có** `psql` và `htpasswd`; có `docker`, `openssl` (không băm bcrypt được) và JDK 21. `psql` dùng qua container Postgres (`docker exec`).
- Dịch vụ băm bằng `BCryptPasswordEncoder(10)` **không có tiền tố** `{bcrypt}`: cột `password_hash` lưu chuỗi thô dạng `$2a$10$...` (ADR-0002). Hash trong script phải cùng dạng.

## T1. Hình thức script: tệp `.sql` chạy bằng `psql -v` (R5, R7)

- **Quyết định:** một tệp `infra/seed/admin.sql`, tham số truyền bằng `psql -v email=... -v hash=... [-v domain=...]`, tham chiếu trong tệp bằng `:'email'` (psql tự thoát dấu nháy).
  Đầu tệp đặt `\set ON_ERROR_STOP on` để lỗi đầu tiên dừng cả tệp (mã thoát khác 0), và phần ghi chỉ là **một câu SQL duy nhất** (tự nó là một giao dịch), đặt sau mọi kiểm tra.
  Chạy: `docker exec -i <postgres> psql -U ... -d ... -v ... -f - < infra/seed/admin.sql` (máy vận hành không cần cài `psql`).
- **Loại:** SQL thuần không tham số (mật khẩu băm phải nằm trong tệp, trái Q1); migration Flyway (chạy cùng mọi môi trường, hash vào repo); bộ nạp trong mã (người dùng đã loại, `flow.md` mục 1); `\copy` từ tệp CSV (thêm một tệp chứa hash).
- **Lý do:** tham số ở dòng lệnh mô phỏng đúng cách vận hành thật, và `ON_ERROR_STOP` cùng giao dịch đảm bảo "lỗi thì không ghi gì" mà không cần mã.
- Không truyền đủ tham số: script coi tham số thiếu là rỗng (`\if :{?email}` rồi `\set`, kèm một dòng `\echo` nêu thiếu gì), các kiểm tra ở T3 từ chối giá trị rỗng. Đã chạy thử bằng test (KT-06).

## T2. Cách tạo mật khẩu băm ở ngoài hệ thống (R2, R7, R9)

- **Quyết định:** băm bằng bcrypt **hệ số 10** (cùng hệ số với dịch vụ để không bị `upgradeEncoding` băm lại ngay lần đăng nhập đầu), tiền tố `$2a$` hoặc `$2y$`/`$2b$` (Spring chấp nhận cả ba). Cách làm sẵn có trên máy dự án, ghi ở README:
  chạy một container có `htpasswd` (`docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" '<mật khẩu>'`, bỏ phần `:` đầu, đổi `$2y$` thành `$2a$` nếu muốn) hoặc `jshell` với `spring-security-crypto` trong kho Maven cục bộ. README chỉ ghi **một** cách chính; cách còn lại là phương án dự phòng.
- **Loại:** thêm công cụ băm vào mã dịch vụ (thêm bề mặt, trái "không có mã ứng dụng mới"); băm trong SQL bằng `pgcrypto` (`crypt(..., gen_salt('bf', 10))`): đưa **mật khẩu thô** vào câu SQL, tức vào lịch sử lệnh và log của Postgres, trái INV-ATH-02; dùng web băm trực tuyến (gửi mật khẩu ra ngoài).
- **Lý do:** mật khẩu thô chỉ tồn tại ở máy người vận hành trong lúc băm; hash là thứ duy nhất đi vào script.
- Kiểm tra bằng cách đăng nhập thật ở bước 6; chưa có gì chứng minh cách băm trong README sinh ra chuỗi `BCryptPasswordEncoder` chấp nhận cho tới lúc đó.

## T3. Kiểm đầu vào trước khi ghi (R2, R3, R4)

- **Quyết định:** ba kiểm, đều dừng bằng lỗi, đặt trước câu ghi:
  1. **Email thuộc miền nội bộ:** sau chuẩn hóa, `lower(btrim(email))` kết thúc bằng `@` + `domain` (hoặc `.` + `domain` cho miền con, giống `ConfiguredInternalEmailPolicy`). `domain` là tham số có mặc định `t3nexus.com.vn` (trùng mặc định của `app.internal-email-domains`); một miền duy nhất, không danh sách.
  2. **Hash đúng dạng bcrypt:** khớp `^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$` (60 ký tự). Mật khẩu thô hay chuỗi cụt không khớp.
  3. **Email không rỗng** sau chuẩn hóa.
- **Loại:** kiểm hệ số đúng 10 (cost khác vẫn đăng nhập được và `upgradeEncoding` tự nâng, không đáng chặn); kiểm miền bằng danh sách như dịch vụ (script không đọc cấu hình, hai danh sách dễ lệch hơn một giá trị); ràng buộc `CHECK` mới trên bảng (đổi lược đồ vì dữ liệu khởi tạo, và INV-ATH-07 là luật của đường đăng ký, không phải luật bảng).
- **Cách dừng (đã chạy thử, `2026-10-17`):** các biểu thức tính bằng `SELECT ... \gset`, rồi `\if` trên từng cờ; nhánh sai chạy một khối `DO $$ BEGIN RAISE EXCEPTION '<lý do>'; END $$;` để `ON_ERROR_STOP` dừng với thông báo rõ và mã thoát 3. Cách này chạy được, không cần chuyển sang `set_config`.
- **Miền:** `domain` được chuẩn hóa và phải khớp `^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$` trước khi ghép vào biểu thức chính quy (dấu `.` được thoát). Email khớp `^[^@\s]+@([^@\s]+\.)?<miền>$`: đúng miền hoặc miền con, không so khớp chuỗi con (cùng luật `ConfiguredInternalEmailPolicy`). `INTERNAL_EMAIL_DOMAINS` của dịch vụ có thể là danh sách nhiều miền, script chỉ nhận **một**: `domain` phải là một trong số đó (README nêu).
- Miền lệch cấu hình dịch vụ (R4) không phát hiện được lúc chạy script; KT-11 giữ hai mặc định khớp nhau.

## T4. Chuẩn hóa email và chạy lại (R1, R3)

- **Quyết định:** email đưa vào là `lower(btrim(:'email'))`, cùng luật với `chk_user_accounts_email_norm`. Câu ghi dùng `INSERT ... ON CONFLICT (email) DO NOTHING`; script đọc số dòng ghi (qua `RETURNING` và `\gset`) rồi in "Đã tạo Quản trị" hoặc "Đã có tài khoản email này, không thêm". Khi đã có, in thêm vai của dòng hiện có để người chạy thấy nếu nó không phải `ADMIN`.
- **Loại:** `ON CONFLICT DO UPDATE` (ghi đè mật khẩu đã đổi ở C1b, vi phạm R1); xóa rồi chèn (mất `id` và phiên đang gắn với tài khoản); `INSERT ... WHERE NOT EXISTS` (đua giữa hai lần chạy cùng lúc làm lỗi trùng khóa thay vì không làm gì).
- **Lý do:** ràng buộc duy nhất của cơ sở dữ liệu là chốt cuối cho INV-ATH-01 (cùng cách `register` đã làm); `DO NOTHING` làm lần chạy lặp vô hại.
- Dòng đã có không phải `ADMIN` chỉ được **báo**, không sửa: sửa vai trái INV-ATH-09 và tự nâng quyền. Với miền nội bộ, đăng ký công khai không tạo được dòng như vậy nên chỉ xảy ra khi ai đó chèn tay.

## T5. UUID v7 dựng bằng SQL trên Postgres 16 (R6, ADR-0001)

- **Quyết định:** ghép 48 bit mili giây hiện tại vào 6 byte đầu của một UUID ngẫu nhiên rồi đặt bit phiên bản về 7:
  `encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid()) placing substring(int8send((extract(epoch from clock_timestamp()) * 1000)::bigint) from 3) from 1 for 6), 52, 1), 53, 1), 'hex')::uuid`.
  `gen_random_uuid()` đặt phiên bản 4 (`0100`) ở nibble cao của byte 6, bật hai bit 52, 53 cho ra `0111` (phiên bản 7); hai bit biến thể giữ nguyên `10`. Biểu thức đã chạy thật trên Postgres 16: KT-07 xanh (phiên bản 7, biến thể đúng, phần thời gian trong khoảng chạy).
- **Loại:** `gen_random_uuid()` thẳng (UUID v4, trái ADR-0001 cho mã thực thể mới, dù chỉ một dòng); chờ nâng Postgres lên 18; sinh UUID v7 ngoài script và truyền thêm một tham số (người vận hành phải có công cụ sinh, thêm một chỗ nhập sai).
- **Lý do:** ADR-0001 chọn v7 cho mọi thực thể; một dòng seed không đáng phá luật, và biểu thức trên đủ ngắn để nằm trong script. Khi dự án nâng lên Postgres 18 thì thay bằng `uuidv7()`; ghi ở `deferred.md` nếu lúc đó còn dùng script này.

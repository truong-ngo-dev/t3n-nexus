# Feature 04: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần
thường, người rà), điều cần trao đổi (nếu có), và cách biết bước đã xong. Chọn cách thực hiện khi bắt đầu từng bước.

Quy tắc giống feature 01 đến 03: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `flow.md` mục 6 trước khi viết script; quyết định kỹ thuật đặt ngay trước bước cần dùng nó.
Feature này **không có mã ứng dụng mới**: sản phẩm là một script SQL chạy tay và các test chứng minh nó. Nên không có miền, kho hay use case để dựng.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu viết **trước khi code**; cột cuối điền **sau khi làm xong**
(quy trình: `docs/global/4.convention/work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Chọn email, băm mật khẩu ở ngoài hệ thống | Mật khẩu thô không vào script; cách băm bằng bcrypt ghi ở README (`tech.md` T2) | Tài liệu vận hành, không có mã | `infra/seed/README.md` (bước 4); lệnh băm đã kiểm khi chạy thật (bước 6) |
| Chạy script với tham số, sau khi Flyway tạo bảng | Một tệp SQL chạy bằng `psql -v`, ngoài Flyway (`tech.md` T1) | Script `infra/seed/admin.sql` | `infra/seed/admin.sql`, `SeedAdminScriptTest` (bước 3, 4) |
| Kiểm đầu vào: thiếu tham số, ngoài miền nội bộ, không phải bcrypt | Kiểm trong script trước khi ghi, dừng bằng lỗi (`tech.md` T3) | Cùng script (khối kiểm đầu vào) | `admin.sql` (khối kiểm); KT-04, 05, 06, 11 (bước 4) |
| Email đã có thì không làm gì | `INSERT ... ON CONFLICT (email) DO NOTHING` trên email đã chuẩn hóa (`tech.md` T4) | Cùng script (câu ghi duy nhất) | `admin.sql` (câu ghi duy nhất); KT-02, 03 (bước 4) |
| Thêm một dòng vai Quản trị với UUID v7 | UUID v7 dựng bằng SQL vì Postgres 16 chưa có hàm sẵn (`tech.md` T5) | Cùng script (câu ghi duy nhất) | `admin.sql`; KT-01, 07 (bước 4) |
| Quản trị đăng nhập, token mang vai | Không làm gì mới; chỉ chứng minh luồng feature 02 nhận ra vai | Test đầu-cuối (không có thành phần mới) | `LoginFlowIntegrationTest` (KT-08), `UserAccountApiTest` (KT-09); không có thành phần mới (bước 5) |

## Điều kiện bắt đầu

Nghiệp vụ đã chốt ở `analysis/user-account.md` mục 6 và Q1: tài khoản Quản trị là dữ liệu khởi tạo, thỏa INV-ATH-01, 02, 06, 07, là đường duy nhất tạo tài khoản nội bộ (INV-ATH-11); mật khẩu ban đầu không nằm trong mã.
Cơ chế (script SQL chạy tay ngoài Flyway) đã chốt ở `flow.md` mục 1; không bàn lại.

Feature 01 và 02 xong (bảng `user_accounts`, luật email nội bộ, đăng nhập thật). Feature 05, 06 chưa cần: đăng nhập thử bằng client HTTP có cookie.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 4 và 6: mỗi rủi ro R1 đến R9 đã có kịch bản test hoặc lý do không cần chưa; mỗi INV (01, 02, 06, 07, 11) có ít nhất một test; S4 có test ở mức API.

**Trạng thái:** Đã rà `2026-10-17`. Ba sửa: thêm KT-11 (R4, giữ mặc định miền của script khớp dịch vụ); đổi nhãn KT-06 (không còn gắn R5, R5 không cần test); thu hẹp KT-10 thành quét migration; KT-02 kiểm cả thông báo của script.

Xong khi: người rà đồng ý danh sách KT-01 đến KT-11; thêm hoặc bớt thì sửa `flow.md` mục 6.

### Bước 2. Chốt giải pháp kỹ thuật (`tech.md`) · Người rồi Agent

Ghi các lựa chọn nhỏ nhưng có thật, mỗi cái có phương án đã loại:
- **T1** hình thức script: tệp `.sql` chạy bằng `psql -v` (biến, `\set`, khối kiểm), thay vì SQL thuần không tham số hay migration.
- **T2** cách tạo mật khẩu băm ở ngoài hệ thống (công cụ nào có sẵn trên máy vận hành; cost 10 khớp `BCryptPasswordEncoder(10)` của dịch vụ; tiền tố `$2a$` hay `$2y$` mà Spring chấp nhận).
- **T3** kiểm đầu vào: biểu thức chính quy cho bcrypt; miền nội bộ là tham số thứ ba có mặc định `t3nexus.com.vn` (R4).
- **T4** chuẩn hóa email (`lower(btrim(...))`) và `ON CONFLICT (email) DO NOTHING` thay cho "xóa rồi chèn" hay `DO UPDATE` (R1).
- **T5** dựng UUID v7 bằng SQL trên Postgres 16.

**Trạng thái:** Đã viết `tech.md` `2026-10-17` (T1 đến T5, kèm điều kiện môi trường đã kiểm: Postgres 16 chưa có `uuidv7()`, máy không có `psql` và `htpasswd`, dịch vụ băm bcrypt hệ số 10 không có tiền tố `{bcrypt}`). Ba điểm chưa chạy thử, thử ở bước 3 và 4: biểu thức UUID v7 (T5), cách dừng bằng `\if` (T3), lệnh băm trong README (T2).

Xong khi: `tech.md` có T1 đến T5 kèm lý do và phương án loại; mỗi hướng giải ở bảng đầu tệp này trỏ tới đúng mục.

### Bước 3. Viết test cho script (đỏ trước) · Agent, Người rà

Viết test KT-01 đến KT-07 và KT-11 chạy **chính tệp script** bằng `psql` trong container Postgres của test (Flyway chạy trước), đọc bảng để kiểm. Chạy khi chưa có script: test đỏ vì không tìm thấy tệp.

**Trạng thái:** Đã viết `2026-10-17`: `SeedAdminScriptTest` (`src/test/java/vn/t3nexus/oauth2/seed/`), 9 test cho KT-01 đến 07 và 11 (KT-02 có thêm ca dòng sẵn có không phải `ADMIN`). Mỗi test dùng một cơ sở dữ liệu riêng trong Postgres của test, chạy Flyway từ trống, chép tệp script vào container và chạy bằng `psql -v`; KT-11 đọc mặc định miền từ `application.properties` để so với script. Chạy lần đầu: đỏ ở `@BeforeAll` với "thiếu script khởi tạo Quản trị" (`infra/seed/admin.sql` chưa có). Phần dựng test (Flyway riêng, `psql` trong container) chưa được kiểm cho tới khi có script ở bước 4.

Xong khi: tám test tồn tại và đỏ đúng lý do (thiếu script), không phải lỗi dựng test.

### Bước 4. Viết script và README · Agent, Người rà

Tạo `infra/seed/admin.sql` theo `tech.md`; thêm README (hoặc mục trong `infra/README.md`) nêu: thứ tự chạy, ba tham số, cách băm, mỗi môi trường một mật khẩu riêng, không để mật khẩu thô hay hash thật vào repo.

**Trạng thái:** Đã làm `2026-10-17`: `infra/seed/admin.sql` và `infra/seed/README.md`. 9 test xanh ngay lần chạy đầu. Phá thử ba chỗ, mỗi chỗ làm đúng test tương ứng đỏ rồi khôi phục (so byte với bản gốc): bỏ kiểm bcrypt (KT-05, 06 đỏ), bỏ kiểm miền (KT-04, 06 đỏ), đổi `DO NOTHING` thành `DO UPDATE` (KT-02 hai test đỏ). Chưa kiểm: lệnh băm `htpasswd` và dạng chạy `docker exec -i ... < admin.sql` của README (test dùng `-f` trong container); kiểm ở bước 6.

Xong khi: KT-01 đến KT-07 và KT-11 xanh; phá thử từng kiểm đầu vào (bỏ kiểm bcrypt, bỏ kiểm miền, đổi `DO NOTHING` thành `DO UPDATE`) thì test tương ứng đỏ.

### Bước 5. Test S4 đầu-cuối · Agent, Người rà

KT-08, 09: chạy script trên Postgres của test, rồi đi luồng đăng nhập thật (PKCE, đổi mã) bằng tài khoản Quản trị và đọc `role`, `sid` trong token; sai mật khẩu bị từ chối; đăng ký công khai bằng miền nội bộ và đăng ký gửi kèm `role` = `ADMIN`. KT-10: quét `db/migration` không có `INSERT INTO user_accounts`.

**Trạng thái:** Đã làm `2026-10-17`. KT-08: hai test thêm vào `LoginFlowIntegrationTest` (script chạy trên cơ sở dữ liệu của ứng dụng; đăng nhập thật cho token `role` = `ADMIN`, `sub` đúng mã tài khoản, `sid` UUID v7; sai mật khẩu về `/login?error`, không mở phiên). KT-09: không viết mới vì `UserAccountApiTest` đã có hai ca (gửi kèm `role` = `ADMIN` ra vai `USER`; email miền nội bộ trả `EMAIL_RESERVED`), chạy lại xanh. KT-10: một test trong `SeedAdminScriptTest` quét `db/migration`. Chạy: `LoginFlowIntegrationTest` 40, `SeedAdminScriptTest` 10, `UserAccountApiTest` 5, đều xanh. Phá thử: script đặt vai `USER` thì KT-08 đỏ (đúng một test); thêm một migration chèn `user_accounts` thì KT-10 đỏ (các test script khác cũng đỏ vì bảng không còn trống, chấp nhận). Đã xóa migration thử và khôi phục script (so byte).

Xong khi: ba test xanh; phá thử (cho script đặt vai `USER`) thì KT-08 đỏ.

### Bước 6. Chạy thật · Người

Chạy script vào cơ sở dữ liệu dev (cổng 5432, dịch vụ cổng 8004, profile `dev`) bằng `psql` ngoài container, với email và hash do người chạy chọn; xem hàng vừa tạo; chạy lần hai; thử đăng nhập bằng curl có cookie. **Ghi vào cơ sở dữ liệu dev:** báo trước, và hỏi giữ hay xóa dòng thử sau khi xong.

**Trạng thái:** Đã chạy `2026-10-17` vào cơ sở dữ liệu dev (container `infra-postgres-oauth2-1`, cổng 5435, trước đó 0 tài khoản) và dịch vụ thật (profile `dev`, cổng 8004), với email `quantri-thu@t3nexus.com.vn` và mật khẩu thử. Kết quả: (1) lệnh băm của README chạy, ra chuỗi 60 ký tự `$2a$10$...`; (2) chạy script bằng stdin (`docker exec -i ... < admin.sql`) tạo đúng một dòng `ADMIN`, `ACTIVE`, `CREDENTIAL`, mã UUID v7; chạy lần hai (email viết hoa, hash khác) in "exists", không thêm, không đổi; (3) đăng nhập qua PKCE bằng curl: token `sub` đúng mã trong cơ sở dữ liệu, `role` = `ADMIN`, có `sid`; (4) sai mật khẩu về `/login?error`; (5) đăng ký công khai email `@t3nexus.com.vn` trả `EMAIL_RESERVED`, gửi kèm `role` = `ADMIN` ra `USER`; (6) log không có hash, mật khẩu thô hay lỗi (78 INFO, 1 WARN). **Phát hiện:** lần chạy đầu tôi gộp stderr vào lệnh băm nên tiến trình tải image lẫn vào hash; script từ chối đúng (R2 chạy thật), README đã thêm cảnh báo không gộp stderr. Đã dọn: xóa hai tài khoản thử, phiên và bản ghi ủy quyền của chúng (cơ sở dữ liệu dev về 0 tài khoản như lúc đầu); dịch vụ đã dừng.

Xong khi: các ca trong `flow.md` ra đúng kết quả; log của dịch vụ không có lỗi bất thường khi đăng nhập Quản trị; không có mật khẩu thô hay hash thật trong repo (`git status` sạch phần đó).

### Bước 7. Đóng feature · Agent, Người rà

Cập nhật `service.md` (mục "Dữ liệu khởi tạo" đổi Dự kiến thành Đã làm, kèm cách chạy), `data.md` mục 3 (script thay cho "chi tiết ở feature 04"), `deferred.md` (chỉ ghi phần đã có chỗ trong phạm vi mà làm đơn giản, nếu có), cột cuối của bảng "Từ luồng đến giải pháp", điều học được ở `../00-features.md` và trạng thái hàng 04 ở đó; `flow.md` đổi trạng thái sang Đã làm.

**Trạng thái:** Đã làm `2026-10-17`: `service.md` (mục "Dữ liệu khởi tạo" đổi Đã làm, kèm cách chạy), `data.md` (mục 3 và dòng trạng thái đầu tệp; sửa luôn đoạn về `oauth_sessions` bị lặp ở mục 2 do feature 03), `deferred.md` (hai phần hoãn: UUID v7 khi nâng Postgres 18, nhiều miền nội bộ), cột cuối bảng luồng ở đầu tệp này, điều học được và hàng 04 ở `../00-features.md`; `flow.md` đổi sang Đã làm. `api.yaml` không đổi vì feature không thêm API.

Xong khi: không còn chỗ nào trong tài liệu lệch với script và test.

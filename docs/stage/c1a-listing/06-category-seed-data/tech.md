# Feature 06: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC đã có: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7). **Feature này không cần ADR mới.** Cơ chế "script chạy tay ngoài Flyway" lấy từ feature 04 của C0 (`c0-foundation/04-seed-admin/tech.md`); nội dung bảng và quy tắc lấy từ feature 05 (`05-category-structure/tech.md`, `data.md`).

## Câu hỏi đã chốt

| # | Câu hỏi | Quyết định | Chốt |
|---|---|---|---|
| Q1 | Nạp bằng cách nào: script SQL chạy tay, gọi API quản trị, hay bộ nạp trong mã? | **Script SQL chạy tay**, ngoài Flyway | `2026-11-04` (theo `00-features.md`: giống feature 04 của C0) |
| Q2 | `name_key` ghi sẵn hay sinh từ mã Java? | **Ghi sẵn** trong script, kèm test đối chiếu `NameKey.of` | `2026-11-04` (người dùng đồng ý) |
| Q3 | Cỡ và nội dung bộ mẫu | Như T2: 3 ngành, 6 cấp hai, 13 lá, 13 mẫu, 10 thương hiệu | `2026-11-04` (người dùng đồng ý, giữ đúng yêu cầu nghiệp vụ) |
| Q4 | Nơi chạy thật | Postgres cách ly; cơ sở dữ liệu dev chỉ khi người dùng cho phép riêng | `2026-11-04` (người dùng đồng ý) |
| Q5 | Chạy lại khi đã có dữ liệu | **Không nạp gì** nếu một trong ba bảng cấu trúc đã có dòng (T3) | Đề xuất, chưa bị phản đối |

## T1. Cơ chế: một tệp SQL, một giao dịch

**Quyết định.** `infra/seed/catalog.sql`, chạy bằng `psql` vào `catalog_db` sau khi Flyway tạo bảng. Cấu trúc tệp (cùng phong cách `admin.sql`: chú thích đầu tệp nêu cách chạy, `\set ON_ERROR_STOP on`):

1. Kiểm các bảng có mặt (`to_regclass`): `brand`, `attribute_template`, `attribute_option`, `category`, `category_attribute_assignment`. Thiếu thì in thông báo nêu bảng thiếu và dừng với mã khác 0 (R4).
2. `BEGIN`; lấy khóa tư vấn mức giao dịch (`pg_advisory_xact_lock` với một hằng số riêng của script) để hai lần chạy đồng thời nối đuôi nhau (R3).
3. Kiểm ba bảng `brand`, `attribute_template`, `category` đều trống. Có dòng thì in "đã có, không thêm" rồi `COMMIT` rỗng, mã thoát 0 (R1, R2).
4. Ghi theo thứ tự phụ thuộc: thương hiệu → mẫu → tùy chọn → danh mục cấp một, hai, ba → dòng gán. Mỗi nhóm một câu `INSERT … SELECT` từ **bảng tạm** (T4).
5. `COMMIT`; in số dòng từng bảng.

Dừng khi kiểm tra thất bại dùng `\if` trong `psql` (hoặc `DO` với `RAISE EXCEPTION` cho lỗi, `\echo` rồi `\quit` cho trường hợp "đã có"), theo kiểu `admin.sql`. Chi tiết cú pháp để lúc viết, kiểm bằng test KT-02 đến KT-05.

**Phương án đã loại.**
- *Gọi API quản trị bằng một script (curl, Node).* Đi qua miền và có kiểm toán, nhưng cần token `ADMIN` thật (dựng cả Xác thực), sinh bản ghi kiểm toán và `EVT-CAT-03` cho dữ liệu khởi tạo, và chạy lại phải tự tìm theo tên. Dữ liệu khởi tạo thuộc môi trường, không phải thao tác của một người; feature 04 của C0 cũng không đi qua API.
- *Bộ nạp trong mã (Runner theo profile).* Đưa dữ liệu demo vào mã đưa lên mọi môi trường; trái cách làm của feature 04 của C0 ("không có bộ nạp trong mã").
- *Migration Flyway.* Dữ liệu mẫu sẽ chạy ở cả môi trường thật.

**Đánh đổi chấp nhận.** Script không đi qua miền nên không có sẵn các kiểm tra của miền; bù bằng test dựng lại dữ liệu qua chính hàm kiểm của miền (T6). Không phát event: Tìm kiếm (C1b) phải dựng chỉ mục ban đầu từ cơ sở dữ liệu, không trông chờ `EVT-CAT-03` cho dữ liệu mẫu.

## T2. Nội dung bộ mẫu

Nguồn duy nhất của nội dung; script, test và tài liệu đều khớp bảng này.

### Thương hiệu (10)

`Coolmate`, `Uniqlo`, `Nike`, `Adidas`, `Samsung`, `Apple`, `Xiaomi`, `Sony`, `Lock&Lock`, `Sunhouse`.

`name_key` là tên chuẩn hóa: `coolmate`, `uniqlo`, `nike`, `adidas`, `samsung`, `apple`, `xiaomi`, `sony`, `lock&lock`, `sunhouse`. Hàm khóa tên chỉ cắt khoảng trắng, gộp khoảng trắng giữa, bỏ dấu, chữ thường; không loại bỏ ký tự khác nên `&` giữ nguyên.

### Mẫu thuộc tính (13) và tùy chọn (35)

| # | `name` | Tên hiển thị | Kiểu | Đơn vị | Gợi ý | Tùy chọn (`value` → tên hiển thị, theo thứ tự) |
|---|---|---|---|---|---|---|
| 1 | `color` | Màu sắc | `SINGLE_SELECT` | | Chọn màu chính của sản phẩm | `den` Đen, `trang` Trắng, `xam` Xám, `do` Đỏ, `xanh-duong` Xanh dương, `xanh-la` Xanh lá, `vang` Vàng, `hong` Hồng |
| 2 | `size_clothing` | Kích cỡ quần áo | `SINGLE_SELECT` | | | `s` S, `m` M, `l` L, `xl` XL, `xxl` XXL |
| 3 | `material` | Chất liệu | `MULTI_SELECT` | | Chọn các chất liệu chính | `cotton` Cotton, `polyester` Polyester, `denim` Denim, `linen` Vải lanh, `silicone` Silicone, `nhua` Nhựa |
| 4 | `storage` | Dung lượng bộ nhớ | `SINGLE_SELECT` | | | `64gb` 64 GB, `128gb` 128 GB, `256gb` 256 GB, `512gb` 512 GB, `1tb` 1 TB |
| 5 | `ram` | RAM | `SINGLE_SELECT` | | | `4gb` 4 GB, `6gb` 6 GB, `8gb` 8 GB, `12gb` 12 GB, `16gb` 16 GB |
| 6 | `origin` | Xuất xứ | `SINGLE_SELECT` | | | `viet-nam` Việt Nam, `trung-quoc` Trung Quốc, `nhat-ban` Nhật Bản, `han-quoc` Hàn Quốc, `thai-lan` Thái Lan, `my` Mỹ |
| 7 | `screen_size` | Kích thước màn hình | `NUMBER` | inch | Đo theo đường chéo | |
| 8 | `battery_capacity` | Dung lượng pin | `NUMBER` | mAh | | |
| 9 | `warranty_months` | Thời gian bảo hành | `NUMBER` | tháng | | |
| 10 | `capacity_liter` | Dung tích | `NUMBER` | lít | | |
| 11 | `is_waterproof` | Chống nước | `BOOLEAN` | | | |
| 12 | `release_date` | Ngày ra mắt | `DATE` | | | |
| 13 | `model` | Mã model | `TEXT` | | Mã model do nhà sản xuất công bố | |

Phủ sáu kiểu nhập (N8): chọn một (1, 2, 4, 5, 6), chọn nhiều (3), số (7 đến 10), đúng/sai (11), ngày (12), chữ (13). Số tùy chọn: 8 + 5 + 6 + 5 + 5 + 6 = 35. Thứ tự tùy chọn là `sort_order` bắt đầu từ 0 theo bảng. Hint để trống là cột rỗng (`NULL`).

### Cây danh mục (3 / 6 / 13) và dòng gán (57)

`sort_order` bắt đầu từ 0 theo thứ tự trong bảng, tính trong cùng cha. Cột "Gán" nêu `mẫu` (**B** bắt buộc, không đánh dấu là không bắt buộc) và, nếu có, ràng buộc riêng; thứ tự trong ô là `display_order` bắt đầu từ 0.

| Cấp 1 | Cấp 2 | Lá (cấp 3) | Gán (thứ tự) |
|---|---|---|---|
| Thời trang | Thời trang nam | Áo thun nam | `color` B, `size_clothing` B, `material` {`maxSelections` 2}, `origin` |
| | | Áo sơ mi nam | `color` B, `size_clothing` B, `material` {`maxSelections` 2}, `origin` |
| | | Quần jean nam | `color` B, `size_clothing` B, `material` {`maxSelections` 2}, `origin` |
| | Thời trang nữ | Áo thun nữ | `color` B, `size_clothing` B, `material` {`maxSelections` 2}, `origin` |
| | | Váy nữ | `color` B, `size_clothing` B, `material` {`maxSelections` 2}, `origin` |
| Điện tử | Điện thoại | Smartphone | `color` B, `storage` B, `ram` B, `screen_size` B {`min` 3, `max` 8}, `battery_capacity` {`min` 1000, `max` 10000, `integerOnly` true}, `warranty_months` B {`min` 0, `max` 60, `integerOnly` true}, `release_date` {`inputPrecision` MONTH, `minDate` 2015-01-01}, `is_waterproof`, `origin`, `model` B {`maxLength` 50} |
| | | Điện thoại phổ thông | `color` B, `battery_capacity` {`min` 1000, `max` 10000, `integerOnly` true}, `warranty_months` B {`min` 0, `max` 60, `integerOnly` true}, `model` B {`maxLength` 50}, `origin` |
| | Phụ kiện điện thoại | Ốp lưng | `color` B, `material` {`maxSelections` 2}, `model` B {`maxLength` 50} |
| | | Sạc và cáp | `color` B, `warranty_months` {`min` 0, `max` 36, `integerOnly` true}, `origin` |
| | Âm thanh | Tai nghe | `color` B, `battery_capacity` {`min` 100, `max` 10000, `integerOnly` true}, `is_waterproof`, `warranty_months` B {`min` 0, `max` 36, `integerOnly` true}, `origin` |
| | | Loa bluetooth | `color` B, `battery_capacity` {`min` 100, `max` 20000, `integerOnly` true}, `is_waterproof`, `warranty_months` B {`min` 0, `max` 36, `integerOnly` true} |
| Nhà cửa và đời sống | Đồ dùng nhà bếp | Nồi cơm điện | `color` B, `capacity_liter` B {`min` 0.5, `max` 10}, `warranty_months` B {`min` 0, `max` 36, `integerOnly` true}, `origin` |
| | | Bình giữ nhiệt | `color` B, `capacity_liter` B {`min` 0.1, `max` 5}, `origin` |

Tổng dòng gán: 5 × 4 + 10 + 5 + 3 + 3 + 5 + 4 + 4 + 3 = **57**. Dòng gán không ràng buộc ghi `{}`.

**Vì sao nội dung này đủ nghiệp vụ.** Mỗi lá có `color` (chọn một, trục biến thể, N7); áo có thêm `size_clothing`, điện thoại có `storage` và `ram`: tới hai trục chọn một, đúng giới hạn tối đa hai trục của biến thể. Chọn nhiều (`material`) để feature 07 có ca nhiều giá trị. Dòng không bắt buộc lẫn bắt buộc để kiểm "chỉ ép khi đăng". `minDate` dùng ngày tuyệt đối vì cách ghi mốc tương đối do feature 07 diễn giải khi kiểm giá trị sản phẩm.

## T3. Chạy lại và dữ liệu có sẵn

**Quyết định.** Một trong ba bảng `brand`, `attribute_template`, `category` đã có dòng thì script **không thêm gì** (R1, R2). Bảng tùy chọn và dòng gán không cần kiểm riêng vì chúng chỉ tồn tại kèm mẫu và danh mục.

**Phương án đã loại.**
- *Thêm phần thiếu theo khóa tự nhiên (`ON CONFLICT DO NOTHING`).* Nghe giống "an toàn khi chạy lại" nhưng nạp lại thứ Quản trị đã chủ động bỏ (đã ngừng dùng thì vẫn còn đó, đã đổi tên hiển thị thì thêm bản trùng nghĩa theo khóa cũ không còn khớp). Với danh mục còn khó hơn: tên đổi thì khóa tên khác, bộ mẫu thêm một danh mục thứ hai cùng nghĩa. Dữ liệu khởi tạo chỉ nên chạy một lần trên cơ sở dữ liệu trống.
- *Bỏ từng nhóm đã có, nạp nhóm còn trống.* Gán thuộc tính phụ thuộc cả danh mục lẫn mẫu; nạp một nửa cho bộ không nhất quán (mẫu có mà gán thiếu hoặc ngược lại).

Hai lần chạy đồng thời: khóa tư vấn trong giao dịch (T1 bước 2) làm lần hai chờ rồi thấy "đã có".

## T4. Mã định danh và quan hệ giữa các nhóm

**Mã định danh.** UUID v7 sinh trong SQL bằng hàm thuần tạm (`pg_temp`), cùng công thức của `admin.sql` (48 bit mili giây ghép UUID ngẫu nhiên, đặt bit phiên bản 7). Postgres 16 chưa có `uuidv7()`. Các hàng chèn cùng một câu `INSERT` dùng cùng một mili giây nên thứ tự không đoán trước được; thứ tự hiển thị đã có cột `sort_order`, không dựa vào thứ tự mã.

**Quan hệ.** Mã sinh ở lúc chạy, nên các nhóm sau tham chiếu nhóm trước bằng **khóa tự nhiên trong bảng tạm**: mẫu theo `name`, tùy chọn theo `(name mẫu, value)`, danh mục theo đường dẫn tên `Cấp1/Cấp2/Lá`. Cách làm: đầu script tạo bảng tạm chứa nội dung T2 (kèm cột `id` được gán một lần), rồi các `INSERT … SELECT` đọc từ đó; bảng tạm mất khi hết phiên.

**Phương án đã loại.** *Mã cố định ghi cứng trong script:* mọi môi trường chung một bộ mã, mất tính UUID v7 theo thời gian tạo, và dễ lẫn môi trường. *Hàm sinh mã trong cơ sở dữ liệu dùng chung:* cần migration, trái "ngoài Flyway".

## T5. Giá trị cột chung

| Cột | Giá trị |
|---|---|
| `status` | `ACTIVE` mọi nơi (N9) |
| `version` | `0` (đúng quy ước "tạo = 0", `data.md` mục 1) |
| `created_at`, `updated_at` | `now()` của giao dịch, mọi hàng cùng giá trị |
| `sort_order`, `display_order` | Bắt đầu từ 0 theo thứ tự trong bảng T2 |
| `constraints` | JSON chỉ chứa các trường có giá trị; `{}` khi không ràng buộc; tên trường đúng `maxLength`, `min`, `max`, `integerOnly`, `inputPrecision`, `minDate`, `maxDate`, `maxSelections`; `inputPrecision` là `YEAR`, `MONTH` hoặc `DAY` |
| `name_key` | Ghi sẵn (T2), bằng kết quả của hàm khóa tên (Q2) |
| `hint`, `unit` | `NULL` khi không có |

Số trong `constraints` ghi bằng số JSON (không chuỗi) để đọc lại qua ánh xạ của dịch vụ (`min`, `max` là số thập phân, `scale` tối đa 4).

## T6. Chứng minh dữ liệu mẫu đúng nghiệp vụ

Script ghi thẳng bảng nên không có gì buộc nó theo quy tắc miền. Ba lớp kiểm:

1. **Cơ sở dữ liệu tự bắt** những gì ràng buộc của `V1` bao: tên trùng, dạng mã (`ck_..._format`), cấp (`ck_category_level`), khóa ngoại cha và mẫu, `unit` chỉ ở kiểu số. KT-05 chứng minh rollback.
2. **Test dựng lại qua miền** (KT-06, KT-07): đọc dữ liệu đã nạp qua các hàm của miền, không viết lại quy tắc trong test.
   - `NameKey.of(name)` cho `name_key` của thương hiệu và danh mục.
   - `AttributeTemplate.validName`, và dạng giá trị tùy chọn, cho mã.
   - `CategoryAttributeAssignment.create(...)` cho từng dòng gán, truyền kiểu nhập của mẫu và `constraints` đọc từ JSON: nó từ chối `constraints` sai hình theo kiểu (400 `ATTRIBUTE_CONSTRAINTS_INVALID` ở API) nên là kiểm chung với API.
   - `AttributeTemplate#isUsable()` cho mẫu được gán; cấp của danh mục có gán là ba.
   - Cây: đếm con theo cha để kiểm N1.
3. **Kiểm qua API** (KT-08, KT-09) bằng nền `CatalogApiTestBase` của feature 05: dữ liệu mẫu đọc ra đúng và sống bình thường dưới thao tác quản trị. Khác với lớp 2, lớp này bắt lỗi ở chỗ nối giữa dữ liệu thô và đọc cây (thứ tự, bộ lọc "dùng được").

**Phá thử bắt buộc** (cách làm của feature 05): sửa tệp script ở bản sao để cố tình sai rồi chạy test, mỗi lỗi phải làm một test đỏ: một `name_key` sai dấu; một mẫu chọn bỏ hết tùy chọn; một dòng gán vào danh mục cấp hai; `constraints` của mẫu chữ có `min`; một lá bỏ hết mẫu chọn một; một tên danh mục trùng khóa trong cùng cha; bỏ khóa tư vấn (hai lần chạy đồng thời phải đỏ); script ghi một dòng `outbox_events`.

## T7. Cách chạy và kiểm

- **Chạy trong test:** Testcontainers Postgres, ứng dụng khởi động một lần để Flyway tạo bảng (hoặc áp `V1` thẳng bằng `psql`), chép **chính** `infra/seed/catalog.sql` vào container bằng `copyFileToContainer`, chạy `psql -v ON_ERROR_STOP=1 -f` bằng `execInContainer`. Đường dẫn tệp tính từ thư mục mô-đun (`../../infra/seed/catalog.sql`).
- **Chạy cho người vận hành:** như `admin.sql`: `docker exec -i <container-postgres> psql -U <user> -d <database> < catalog.sql`, không cần cài `psql`. Không tham số.
- **Chạy thật (KT-11):** ngăn xếp cách ly `probe-` (như feature 05 bước 8, bản chỉ Postgres và ứng dụng, không Kafka): ứng dụng khởi động trên cơ sở dữ liệu trống, chạy script, gọi API thật. Không chạm cơ sở dữ liệu dev.
- **Cơ sở dữ liệu dev (`catalog_db` trong `infra-postgres-catalog-1`):** có lịch sử migration cũ nên phải dựng lại trước khi chạy script (xóa dữ liệu dev, cần người dùng cho phép đúng đối tượng). Đã làm `2026-11-04` (xem `plan.md`); không nằm trong các bước kiểm chứng của feature này.

## T8. Kết quả · Đã làm (`2026-11-04`)

`infra/seed/catalog.sql` nạp đúng bộ mẫu T2 (10/13/35/22/57). `CatalogSeedTest` 12 ca xanh trên Postgres Testcontainers (KT-01 đến KT-10: script chạy bằng `psql` trong container, dựng lại qua hàm của miền `NameKey`, `AttributeTemplate`, `Category`, `CategoryAttributeAssignment`, đọc qua API, quét migration). Phá thử 10/10 lỗi cố ý làm test đỏ (danh sách ở `plan.md` bước 3). Chạy thật KT-11 trên ngăn xếp cách ly: 19/19 mục đạt. Cả bộ catalog 326 ca xanh.

## T9. Rủi ro đã biết

| Rủi ro | Xử lý |
|---|---|
| Script lệch miền khi quy tắc đổi | Test chạy mỗi lần build (R8) |
| Người vận hành chạy nhầm vào cơ sở dữ liệu khác | Kiểm bảng có mặt trước khi ghi (R4) |
| `name_key` ghi sẵn lệch hàm khóa tên | KT-06 |
| Tìm kiếm (C1b) không nhận event cho dữ liệu mẫu | Ghi ở `deferred.md`; chỉ mục ban đầu dựng từ cơ sở dữ liệu |
| Dữ liệu mẫu ảnh hưởng test của feature khác | Test của feature 05 và các feature sau dùng cơ sở dữ liệu trống tự dựng, không đọc bộ mẫu |

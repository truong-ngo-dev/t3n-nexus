# Danh mục hàng: dữ liệu

> **Trạng thái:** Sống · **Cập nhật:** `2026-11-06` · Phần cấu trúc (thương hiệu, mẫu thuộc tính, tùy chọn, danh mục, dòng gán, hộp thư event) **Đã làm** theo migration `V1` của feature 05; bảng sản phẩm và biến thể (mục 10) **Đã làm** ở feature 07 bằng migration `V2`; bảng ảnh **Đã làm** bằng migration `V3` (feature 08); bảng `seller_state` và cột `published_at` của `product` ở migration `V4` **Đã làm** (feature 09). Giải pháp: [`tech.md` của feature 05](../../stage/c1a-listing/05-category-structure/tech.md) T1.
> Cơ sở dữ liệu quan hệ riêng của BC: Postgres 16, cơ sở dữ liệu `catalog_db`. Hộp thư event chuyển bằng CDC nên cần `wal_level=logical` và một tài khoản chủ cơ sở dữ liệu vừa chạy migration vừa chạy dịch vụ (cùng cách `seller-service`); đã khai báo trong `infra/docker-compose.yml` (`postgres-catalog`, cổng dev `5436`); container dev dựng lại sau khi gộp `V1` là việc riêng, chưa làm.

## 1. Quy ước chung

- Khóa chính là `UUID` v7 sinh ở ứng dụng (ADR-0001); thời điểm là `TIMESTAMPTZ`; trạng thái là `VARCHAR(20)` kèm `CHECK`.
- Mọi aggregate sửa được (trừ biến thể, mục 10) có `version BIGINT NOT NULL` ánh xạ `@Version`, **bắt đầu từ 0**, tăng một mỗi lần cập nhật; chạy ngầm, người gọi không gửi (ADR-0011).
- Ràng buộc duy nhất có tên `uq_<bảng>_<ý nghĩa>`; lớp trình bày đổi lỗi theo tên (mục 8).
- **Khóa tên** (`name_key`, chỉ thương hiệu và danh mục): tên đã chuẩn hóa để so trùng (bỏ khoảng trắng đầu cuối và gộp khoảng trắng giữa, bỏ dấu tiếng Việt với `đ` thành `d`, chữ thường). Cột tên giữ nguyên dạng nhập để hiển thị.
- **Mã kỹ thuật** (`attribute_template.name`, `attribute_option.value`): không chuẩn hóa mà bắt buộc đúng dạng, có `CHECK` ở cơ sở dữ liệu; nên so trùng thẳng trên chính cột, không có khóa phụ.
- Không có xóa mềm hay xóa cứng cho mẫu, tùy chọn, thương hiệu (INV-CAT-12, 21); chỉ danh mục xóa cứng được (INV-CAT-05, 98).

## 2. `brand` (AGG-CAT-03)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính |
| `name` | `VARCHAR(100)` | Tên hiển thị |
| `name_key` | `VARCHAR(100)` | Khóa so trùng |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE` |
| `version` | `BIGINT` | |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Ràng buộc: `pk_brand (id)`; `uq_brand_name_key (name_key)` (INV-CAT-20); `ck_brand_status`.

## 3. `attribute_template` (AGG-CAT-02)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính |
| `name` | `VARCHAR(100)` | Mã kỹ thuật `^[a-z0-9]+(_[a-z0-9]+)*$`; đặt lúc tạo, **không có lệnh đổi** |
| `display_name` | `VARCHAR(100)` | Tên hiển thị; sửa được, áp ngay mọi nơi |
| `hint` | `VARCHAR(200)` | Gợi ý nhập cho người bán; tùy chọn |
| `input_type` | `VARCHAR(20)` | `SINGLE_SELECT`, `MULTI_SELECT`, `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`; **không đổi** (INV-CAT-12) |
| `unit` | `VARCHAR(20)` | Đơn vị; chỉ `NUMBER`; không đổi |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE` |
| `version` | `BIGINT` | Tăng cả khi tùy chọn của mẫu thay đổi |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Ràng buộc: `pk_attribute_template`; `uq_attribute_template_name (name)` (INV-CAT-10); `ck_attribute_template_name_format`; `ck_attribute_template_input_type`; `ck_attribute_template_status`; `ck_attribute_template_unit (unit IS NULL OR input_type = 'NUMBER')`.

## 4. `attribute_option`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính; sản phẩm lưu giá trị theo mã này |
| `template_id` | `UUID` | Mẫu sở hữu |
| `value` | `VARCHAR(100)` | Mã kỹ thuật `^[a-z0-9]+([_-][a-z0-9]+)*$`; đặt lúc thêm, không đổi |
| `display_value` | `VARCHAR(100)` | Tên hiển thị; sửa được |
| `sort_order` | `INT` | Thứ tự chuẩn trong mẫu |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE` |
| `created_at` | `TIMESTAMPTZ` | |

Ràng buộc: `pk_attribute_option`; `fk_attribute_option_template (template_id) REFERENCES attribute_template ON DELETE RESTRICT`; `uq_attribute_option_template_value (template_id, value)` (INV-CAT-11); `ck_attribute_option_value_format`; `ck_attribute_option_status`. Tùy chọn là thực thể con của mẫu: ghi từng dòng (thêm, đổi trạng thái, đổi thứ tự), không xóa-rồi-chèn-lại.

## 5. `category` (AGG-CAT-01)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính |
| `parent_id` | `UUID` | Cha; rỗng ở cấp một; không đổi sau khi tạo |
| `level` | `SMALLINT` | 1, 2, 3; cấp ba là lá |
| `name` | `VARCHAR(100)` | Tên hiển thị |
| `name_key` | `VARCHAR(100)` | Khóa so trùng trong cùng cha |
| `sort_order` | `INT` | Thứ tự giữa các anh em cùng cha; không duy nhất |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE`; ngừng dùng không cascade xuống con |
| `version` | `BIGINT` | Tăng khi đổi tên, sắp xếp, trạng thái, danh sách gán |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Ràng buộc: `pk_category`; `fk_category_parent (parent_id) REFERENCES category ON DELETE RESTRICT` (không xóa cha còn con, kể cả khi đua nhau với việc thêm con); `ck_category_level (level BETWEEN 1 AND 3)`; `ck_category_root_level ((level = 1) = (parent_id IS NULL))`; `ck_category_status`.

**Tên duy nhất trong cùng cha (INV-CAT-02)** cần hai chỉ mục duy nhất từng phần, vì cấp một có `parent_id` rỗng nên không so được bằng chỉ mục thường:
- `uq_category_parent_name_key (parent_id, name_key) WHERE parent_id IS NOT NULL`
- `uq_category_root_name_key (name_key) WHERE parent_id IS NULL`

Chỉ mục tra cứu: `idx_category_parent (parent_id, sort_order)` (đọc con theo cha, kiểm "còn con").

**"Dùng được" (INV-CAT-97):** chính nó và mọi tổ tiên `ACTIVE`. Cây tối đa ba cấp nên tổ tiên là cha và ông, đọc bằng tối đa hai lần tra theo khóa chính; không có bảng riêng cho tổ tiên.

## 6. `category_attribute_assignment`

Dòng gán mẫu cho danh mục lá; thuộc aggregate danh mục, ghi thay toàn bộ trong một giao dịch.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `category_id` | `UUID` | Danh mục lá (cấp ba); kiểm ở miền |
| `template_id` | `UUID` | Mẫu |
| `required` | `BOOLEAN` | Bắt buộc khi đăng sản phẩm (chỉ ép khi mẫu dùng được) |
| `display_order` | `INT` | Thứ tự hiển thị |
| `constraints` | `JSONB` | Ràng buộc riêng theo danh mục; hình theo kiểu nhập của mẫu (mục dưới); không rỗng, `{}` khi không ràng buộc |

Ràng buộc: `pk_category_attribute_assignment (category_id, template_id)` (mỗi mẫu một lần cho một danh mục, INV-CAT-03); `fk_caa_category (category_id) ... ON DELETE CASCADE`; `fk_caa_template (template_id) ... ON DELETE RESTRICT`.

**Hình của `constraints`:** mẫu chỉ kiểm định dạng chung theo kiểu nhập (chữ, số, ngày, đúng sai); quy tắc cụ thể của ngành hàng (độ dài tối đa, khoảng giá trị, số lựa chọn tối đa...) khai báo ở dòng gán này, khi gán mẫu cho danh mục lá. Áp những quy tắc đó lên giá trị của sản phẩm là việc kiểm khi đăng sản phẩm (feature 07). `TEXT`: `maxLength`; `NUMBER`: `min`, `max`, `integerOnly`; `MULTI_SELECT`: `maxSelections`; `DATE`: `inputPrecision` (`YEAR`, `MONTH`, `DAY`), `minDate`, `maxDate`; `SINGLE_SELECT`, `BOOLEAN`: không có. Trường không áp dụng cho kiểu của mẫu bị từ chối (400 `ATTRIBUTE_CONSTRAINTS_INVALID`). Miền kiểm hình trước khi lưu; lưu trữ chỉ chuyển đổi, không diễn giải.

## 7. `outbox_events` · hộp thư event

Hình bảng chung của thư viện `outbox-starter` (cùng tên và kiểu cột, chép từ `libs/outbox-starter/src/main/resources/db/outbox-schema.sql`, không sửa). Ghi cùng giao dịch với dữ liệu và bản ghi kiểm toán (R12); Debezium đọc (ADR-0008).

## 8. Ánh xạ tên ràng buộc sang lỗi (ADR-0011)

| Ràng buộc | Mã lỗi (409, trừ ghi chú) |
|---|---|
| `uq_category_parent_name_key`, `uq_category_root_name_key` | `CATEGORY_NAME_TAKEN` |
| `uq_attribute_template_name` | `ATTRIBUTE_TEMPLATE_NAME_TAKEN` |
| `uq_attribute_option_template_value` | `ATTRIBUTE_OPTION_VALUE_TAKEN` |
| `uq_brand_name_key` | `BRAND_NAME_TAKEN` |
| `fk_category_parent`, vi phạm khi **xóa** cha còn con | `CATEGORY_HAS_CHILDREN` |
| `fk_category_parent`, vi phạm khi **chèn** con dưới cha vừa bị xóa (đua nhau với xóa) | `CATEGORY_NOT_FOUND` (404): cha không còn nữa |
| `uq_variant_product_combination` | `VARIANT_COMBINATION_TAKEN` (feature 07) |
| `uq_variant_seller_sku` | `VARIANT_SKU_TAKEN` (feature 07) |
| Vi phạm khóa lạc quan (`@Version`) | `CONCURRENT_UPDATE` |

Cùng một khóa ngoài có hai nghĩa tùy câu lệnh bị vi phạm (xóa hay chèn); bộ xử lý phân biệt bằng câu lệnh SQL của lỗi, không bằng nội dung thông báo của máy chủ. Ràng buộc không có trong bảng thì lỗi hệ thống 500, để lộ chứ không đoán. Mỗi ràng buộc trên có test phá thử (bỏ ràng buộc thì test đỏ).

## 9. Dữ liệu khởi tạo

Bộ mẫu demo (10 thương hiệu, 13 mẫu và 35 tùy chọn, 22 danh mục, 57 dòng gán) nạp bằng script `infra/seed/catalog.sql` (feature 06), chạy tay sau Flyway, một giao dịch; không có trong migration. Chỉ nạp khi `brand`, `attribute_template`, `category` đều trống. Nội dung: `docs/stage/c1a-listing/06-category-seed-data/tech.md` T2.

## 10. Sản phẩm, biến thể và ảnh (AGG-CAT-04, AGG-CAT-05) · Đã làm

Thêm bằng migration `V2__create_product_variant.sql` ở feature 07 và `V3__create_product_image.sql` ở feature 08; không sửa `V1`. Giải pháp: [`tech.md` của feature 07](../../stage/c1a-listing/07-product-variant/tech.md) T1 và [`tech.md` của feature 08](../../stage/c1a-listing/08-product-image/tech.md) T2. **Không có khóa ngoại từ sản phẩm sang danh mục, thương hiệu, mẫu thuộc tính** (tham chiếu giữa aggregate bằng mã, kiểm ở ứng dụng). Bảng ảnh: đã làm bằng migration `V3`.

### `product`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính |
| `seller_id` | `UUID` | Chủ (mã tài khoản người bán, từ token); không đổi |
| `category_id` | `UUID` | Danh mục lá lúc tạo; không đổi |
| `brand_id` | `UUID` | Rỗng được |
| `title` | `VARCHAR(200)` | 5 đến 200 ký tự |
| `description` | `TEXT` | Chữ thuần, tối đa 5.000 ký tự; rỗng được |
| `status` | `VARCHAR(20)` | `DRAFT`, `PUBLISHED`, `UNPUBLISHED`; feature 07 chỉ ghi `DRAFT` |
| `version` | `BIGINT` | Số **dùng chung** cho sản phẩm và biến thể (INV-CAT-36, ADR-0012): tăng mỗi lần sản phẩm hay biến thể đổi |
| `published_at` | `TIMESTAMPTZ` | Thời điểm đăng đầu tiên, rỗng với nháp chưa từng đăng |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Ràng buộc: `pk_product`; `ck_product_status`; `ck_product_title_length`; `ck_product_description_length`; `ck_product_published_at CHECK ((status = 'DRAFT') = (published_at IS NULL))`; `idx_product_seller (seller_id, created_at DESC, id)`.

### `product_attribute` và `product_attribute_option`

Giá trị thuộc tính của sản phẩm. Hai bảng cha-con: cha giữ một dòng cho mỗi cặp (sản phẩm, mẫu), con giữ các tùy chọn đã chọn.

| `product_attribute` | Kiểu | Ghi chú |
|---|---|---|
| `product_id`, `template_id` | `UUID` | Khóa chính cả hai cột |
| `axis` | `BOOLEAN` | Thuộc tính này là **trục biến thể** (kiểu chọn một, tối đa 2 dòng một sản phẩm, kiểm ở miền) |
| `value` | `VARCHAR(255)` | Giá trị rời của chữ, số, đúng/sai, ngày, dạng chuỗi chuẩn hóa; rỗng với kiểu chọn |

| `product_attribute_option` | Kiểu | Ghi chú |
|---|---|---|
| `product_id`, `template_id`, `option_id` | `UUID` | Khóa chính cả ba cột; `option_id` là **mã tùy chọn** của mẫu (không lưu chữ). Với dòng cha làm trục, các dòng này là tập giá trị của trục |

Ràng buộc: `pk_product_attribute`; `fk_pa_product (product_id) → product ON DELETE CASCADE`; `ck_product_attribute_axis_no_value (NOT axis OR value IS NULL)`; `pk_product_attribute_option`; `fk_pao_attribute (product_id, template_id) → product_attribute ON DELETE CASCADE`. Không có khóa ngoại tới `attribute_template` hay `attribute_option` (khác aggregate). Chuẩn hóa chuỗi: số `toPlainString` bỏ số 0 thừa, tối đa 4 chữ số thập phân; đúng/sai `true`/`false`; ngày `yyyy`, `yyyy-MM` hoặc `yyyy-MM-dd` theo độ chính xác; chữ cắt khoảng trắng đầu cuối. Thay danh sách thuộc tính là xóa rồi ghi lại các dòng của sản phẩm đó trong một giao dịch.

### `variant`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính; các BC khác tham chiếu bằng mã này |
| `product_id` | `UUID` | Sản phẩm; không đổi |
| `seller_id` | `UUID` | Sao từ sản phẩm, bất biến; có để ràng buộc SKU duy nhất theo người bán |
| `combination_key` | `VARCHAR(80)` | Các mã tùy chọn của tổ hợp sắp theo mã mẫu tăng dần, nối bằng dấu phẩy (tối đa 73 ký tự); rỗng cho biến thể mặc định (INV-CAT-44) |
| `price` | `BIGINT` | Đồng; 1 đến 1.000.000.000 (INV-CAT-40) |
| `sku` | `VARCHAR(50)` | 1 đến 50 ký tự; rỗng được |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE` |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | Biến thể **không có `version` riêng**: mọi lệnh biến thể tuần tự qua dòng sản phẩm (ADR-0012) |

Ràng buộc: `pk_variant`; `fk_variant_product (product_id) → product ON DELETE RESTRICT` (xóa nháp ở feature 09 xóa biến thể tường minh trước); `uq_variant_product_combination (product_id, combination_key)` (INV-CAT-92); `uq_variant_seller_sku (seller_id, sku) WHERE sku IS NOT NULL` (INV-CAT-96); `ck_variant_price`; `ck_variant_sku_length`; `ck_variant_status`; `idx_variant_product (product_id)`.

### `variant_combination_item`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `variant_id`, `template_id` | `UUID` | Khóa chính cả hai cột: mỗi biến thể một giá trị cho mỗi trục |
| `option_id` | `UUID` | Mã tùy chọn được chọn |

Ràng buộc: `pk_variant_combination_item`; `fk_vci_variant (variant_id) → variant ON DELETE CASCADE`. **Không có khóa ngoại tới giá trị trục của sản phẩm:** "tổ hợp nằm trong tập trục" (INV-CAT-94) và "không rút giá trị trục đang được biến thể dùng" (INV-CAT-33) do ứng dụng kiểm dưới khóa dòng sản phẩm (ADR-0012), không do cơ sở dữ liệu.

### `product_image` (AGG-CAT-04)

Ảnh của sản phẩm (entity của AGG-CAT-04). Ghi chú: tối đa 9 ảnh không có CHECK, giữ bằng khóa dòng sản phẩm; viết lại cả danh sách khi thứ tự hoặc tập ảnh đổi; khóa ngoại tới `product` CASCADE.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính |
| `product_id` | `UUID` | Thuộc sản phẩm; xóa sản phẩm thì xóa ảnh theo |
| `object_key` | `VARCHAR(200)` | Khóa đối tượng trên kho đối tượng |
| `content_type` | `VARCHAR(20)` | `image/jpeg`, `image/png`, `image/webp` |
| `size_bytes` | `BIGINT` | Dung lượng thật của tệp |
| `position` | `INTEGER` | Thứ tự hiển thị, từ 0; 0 là ảnh chính |

Ràng buộc và chỉ mục: `pk_product_image (id)`; `fk_product_image_product (product_id) REFERENCES product (id) ON DELETE CASCADE`; `uq_product_image_position (product_id, position)`; `uq_product_image_object_key (object_key)`; `ck_product_image_position (position >= 0)`; `ck_product_image_size (size_bytes > 0)`; `idx_product_image_product (product_id)`.

### `upload_slot`

Chỗ tải lên đã cấp (mẫu của `upload-starter` ADR-0013, dùng xong thì xóa, không có khóa ngoại).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | Khóa chính, mã chỗ cấp |
| `purpose` | `VARCHAR(40)` | Mục đích tải lên (`product-image`...) |
| `account_id` | `UUID` | Người xin chỗ |
| `target_id` | `UUID` | Đối tượng gắn ảnh (mã sản phẩm) |
| `object_key` | `VARCHAR(200)` | Khóa đối tượng do dịch vụ sinh |
| `content_type` | `VARCHAR(40)` | Kiểu tệp đã khai |
| `declared_size` | `BIGINT` | Dung lượng khai báo |
| `expires_at` | `TIMESTAMPTZ` | Thời điểm hết hạn |
| `created_at` | `TIMESTAMPTZ` | Thời điểm tạo |

Ràng buộc và chỉ mục: `pk_upload_slot (id)`; `uq_upload_slot_object_key (object_key)`; `ck_upload_slot_size (declared_size > 0)`; `idx_upload_slot_target (target_id)`; `idx_upload_slot_expires (expires_at)`.

### `seller_state`

Bản sao trạng thái người bán (không phải aggregate): cập nhật từ `seller.profile.approved`/`rejected`, ghi đè khi `version` mới hơn bằng một câu upsert có điều kiện, không khóa ngoại (tài khoản thuộc bounded context khác).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `account_id` | `UUID` | Khóa chính |
| `status` | `VARCHAR(20)` | `APPROVED`, `REJECTED` |
| `version` | `BIGINT` | Phiên bản hồ sơ người bán |
| `updated_at` | `TIMESTAMPTZ` | Thời điểm cập nhật |

Ràng buộc: `pk_seller_state (account_id)`; `ck_seller_state_status CHECK (status IN ('APPROVED', 'REJECTED'))`; `ck_seller_state_version CHECK (version >= 0)`.

### Chống đua

- **Khóa dòng:** lệnh thêm, sửa, ngừng, bật biến thể và lệnh sửa danh sách thuộc tính (có thể đổi trục) đọc dòng `product` bằng `SELECT … FOR UPDATE`, rồi mới đếm, kiểm, ghi; tối đa 50 biến thể giữ ở đây vì đếm không phải tính duy nhất (ADR-0012).
- **Khóa lạc quan:** lệnh chỉ sửa tiêu đề, mô tả, thương hiệu dùng `@Version` của `product` (ADR-0011).
- **Chốt cuối ở lưu trữ:** `uq_variant_product_combination` và `uq_variant_seller_sku` (SKU còn phải chặn đua giữa **hai sản phẩm khác nhau** của cùng người bán, nơi khóa dòng một sản phẩm không bao được).

## 11. Chưa có

- Cấu hình khám phá theo danh mục (thuộc tính nào lọc được, sắp xếp): C1b, thêm vào dòng gán thuộc tính.

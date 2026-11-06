# Feature 05: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC đã có: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7), [ADR-0003](../../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) (hợp đồng danh tính), [ADR-0008](../../../global/2.architecture/adr/0008-event-outbox-and-consumption.md) (hộp thư), [ADR-0011](../../../global/2.architecture/adr/0011-optimistic-lock-and-storage-uniqueness.md) (ràng buộc duy nhất ở lưu trữ, khóa lạc quan chạy ngầm; ADR ghi sẵn "Danh mục hàng dùng lại ở feature 05"). **Feature này không cần ADR mới.** Cơ chế bản ghi kiểm toán: feature 02; mẫu "sự kiện miền → event handler cùng giao dịch": feature 04.

## Câu hỏi cần chốt trước khi viết `plan.md`

Q1 đến Q10 lấy từ `flow.md` mục 6. Cột "Đề xuất" là hướng tôi nghiêng; **Q2, Q3, Q4, Q6, Q9 đổi hợp đồng hoặc cần sửa `analysis` nên cần bạn xác nhận**, các câu còn lại là chi tiết thi công.

| # | Câu hỏi | Đề xuất | Hệ quả nếu khác |
|---|---|---|---|
| Q1 | Sơ đồ dữ liệu, cách lưu cây | **Đã chốt `2026-11-01` (bỏ bảng bao đóng).** Cây lưu bằng `parent_id` và `level`; tổ tiên của một nút tối đa hai bước theo `parent_id`. Chi tiết T1 | Bảng bao đóng: một bảng, ba lệnh chèn riêng, một chỗ có thể lệch dữ liệu; chỉ đáng khi cần "mọi con cháu" bằng một câu ở chặng sau |
| Q2 | Cách kiểm vai `ADMIN` | **Trong use case, qua `CatalogActor.requireAdmin()`**, cùng cách với feature 03, 04 của seller; test đơn vị chứng minh R6 không cần Spring. Controller chỉ dựng người thực hiện từ token | Chú thích `@PreAuthorize` ở lớp trình bày (như `service.md` mục 4 đang ghi): đơn giản hơn nhưng R6 chỉ kiểm được qua tầng web; `service.md` mục 4 được sửa theo lựa chọn khi đóng feature |
| Q3 | Có phát `EVT-CAT-03` khi chưa có bên nhận | **Có**, cho danh mục (kể cả xóa) và mẫu thuộc tính. Ghi hộp thư cùng giao dịch là chỗ khó thêm về sau; bên nhận (Tìm kiếm, C1b) đến sau không phải sửa phía phát. Thương hiệu không phát (theo `brand.md`) | Chưa phát: ít việc hơn, nhưng C1b phải quay lại sửa mọi use case |
| Q4 | Hành động kiểm toán cho **xóa danh mục** và **bật lại** | **Thêm `CATEGORY_DELETED`; bật lại ghi `_UPDATED`** (dữ kiện `change` nói rõ là bật lại). S9 đòi mọi thao tác quản trị tra cứu được nên xóa không thể không có dấu vết. **Cần sửa `category.md` mục 4, 6 trước** (thứ tự analysis → service → code) | Không có `CATEGORY_DELETED`: xóa không để lại dấu vết riêng, trái S9 |
| Q5 | Ngừng hoặc bật lại cái đã ở trạng thái đó | **409 `INVALID_STATE`**, không kiểm toán, không event, cùng cách với duyệt hồ sơ | Thành công không đổi gì: thân thiện hơn cho thao tác lặp, nhưng phải quyết có ghi kiểm toán không |
| Q6 | Ngừng tùy chọn cuối cùng của mẫu đã được gán | **Cho phép.** Mẫu trở thành "không dùng được" (`isUsable` sai), sản phẩm cũ giữ giá trị; thuộc tính bắt buộc không còn ép điền. `INV-CAT-11` sửa lời: "ít nhất một tùy chọn đang dùng" là điều kiện lúc **gán** và lúc **thêm**, không phải lúc ngừng. Khớp `attribute-template.md` mục 3 ("ngừng dùng tùy chọn không làm sản phẩm mất giá trị") | Từ chối: Quản trị không thể thôi dùng giá trị cuối (ví dụ một màu ngừng bán) mà không ngừng cả mẫu, ảnh hưởng mọi danh mục khác |
| Q7 | Điều kiện xóa danh mục khi chưa có sản phẩm | Feature này kiểm **nửa còn con** (INV-CAT-05). Nửa "đã từng có sản phẩm" (INV-CAT-98) thêm ở feature 07 khi bảng sản phẩm có; ghi `deferred.md` của feature 05, và feature 07 phải có test chứng minh | Chặn xóa tạm thời mọi danh mục: không đúng nghiệp vụ |
| Q8 | Khóa lạc quan | **Theo ADR-0011:** cột `version` chạy ngầm (`@Version`) cho danh mục, mẫu, thương hiệu; yêu cầu sửa **không** mang phiên bản; đua nhau trả 409 `CONCURRENT_UPDATE`. Phiên bản trong `EVT-CAT-03` là phiên bản sau thay đổi (`version() + 1`, như feature 04) | Bắt gửi phiên bản mọi lệnh: không có nhu cầu nghiệp vụ với quản trị sửa cấu trúc chung |
| Q9 | Hình dạng API | Đường dẫn T6. **Đọc cây, thuộc tính, thương hiệu yêu cầu đăng nhập** (`RM-CAT-01` đến `03`: "đã đăng nhập"). Cây trả một lần cả ba cấp (kích thước nhỏ). Tên tối đa 100 ký tự, gợi ý tối đa 200, thứ tự là danh sách mã đầy đủ của anh em | Công khai cho đọc: cần khi Guest duyệt hàng (C1b); mở sau là thay đổi nhỏ |
| Q10 | Khóa dữ kiện kiểm toán | **Chỉ hành vi và giá trị của hành vi** (chốt hướng ở feature 04). Chi tiết T4 | Thêm dữ liệu ngoài hành vi: lặp dữ liệu đã có |

**Trạng thái (`2026-11-01`):** Q1 đã chốt (bỏ bảng bao đóng). Q9 đã chốt: mọi đường cần đăng nhập, chưa có đường công khai. Q2, Q3, Q4, Q6 được chấp nhận theo đề xuất; `category.md` (hành động `CATEGORY_DELETED`, bật lại ghi `_UPDATED`), `attribute-template.md` (`INV-CAT-11` không kiểm lúc ngừng tùy chọn) và `audit-service/analysis.md` mục 8 đã sửa cho khớp. Q5, Q7, Q8, Q10 theo đề xuất, chưa bị phản đối. **Mọi câu "đề xuất" là hướng khởi đầu: ở mỗi bước làm, mã hiện có được đối chiếu với đặc tả trước khi viết, và chỗ lệch được đưa lại cho người quyết** (`plan.md`).

## T1. Dữ liệu: migration gốc đầu tiên · Dự kiến

Một migration `V1` cho cấu trúc của feature này và một cho hộp thư (hình bảng chung của thư viện `outbox-starter`, chép nguyên). Khóa là `uuid` (ADR-0001), thời điểm là `timestamptz`. Bảng sản phẩm, biến thể thuộc feature 07, thêm bằng migration của feature đó.

| Bảng | Cột chính | Ràng buộc có tên |
|---|---|---|
| `brand` | `id`, `name`, `name_key`, `status`, `version`, `created_at`, `updated_at` | `uq_brand_name_key (name_key)`; `chk_brand_status` |
| `attribute_template` | `id`, `name`, `display_name`, `hint` (≤ 200), `input_type`, `unit` (≤ 20), `status`, `version`, thời điểm | `uq_attribute_template_name`; `ck_attribute_template_name_format` (`^[a-z0-9]+(_[a-z0-9]+)*$`); `chk_attribute_template_input_type` (sáu kiểu); `chk_attribute_template_unit` (chỉ `NUMBER` có đơn vị) |
| `attribute_option` | `id`, `template_id`, `value`, `display_value`, `sort_order`, `status` | FK `template_id` `RESTRICT`; `uq_attribute_option_template_value (template_id, value)`; `ck_attribute_option_value_format` (`^[a-z0-9]+([_-][a-z0-9]+)*$`) |
| `category` | `id`, `parent_id`, `level`, `name`, `name_key`, `sort_order`, `status`, `version`, thời điểm | FK `parent_id` tự tham chiếu `RESTRICT` (chặn xóa cha còn con, kể cả đua nhau); `chk_category_level (1..3)`; `chk_category_root_level ((level = 1) = (parent_id IS NULL))`; **hai chỉ mục duy nhất từng phần**: `uq_category_parent_name_key (parent_id, name_key) WHERE parent_id IS NOT NULL` và `uq_category_root_name_key (name_key) WHERE parent_id IS NULL` (cấp một không có cha nên không so được bằng chỉ mục thường) |
| `category_attribute_assignment` | `category_id`, `template_id`, `required`, `display_order`, `constraints` (`jsonb`, không rỗng) | PK `(category_id, template_id)` (mẫu một lần cho một danh mục, INV-CAT-03); FK `category_id` `CASCADE` (dòng gán thuộc aggregate danh mục), FK `template_id` `RESTRICT` |

- **Khóa tên (`name_key`, chỉ thương hiệu và danh mục):** hàm thuần `NameKey.of(text)` của miền: bỏ khoảng trắng đầu cuối và gộp khoảng trắng giữa, bỏ dấu tiếng Việt bằng `StringUtils.removeAccent` của `common-utils` (xử lý `đ`), chữ thường. Cột tên giữ nguyên dạng hiển thị; cột khóa chỉ để so trùng (INV-CAT-02, 20). **Tên mẫu và giá trị tùy chọn** không dùng khóa này: bắt buộc đúng dạng mã (miền kiểm, 400 `CATALOG_FIELD_INVALID`; cơ sở dữ liệu có `CHECK`) và so trùng thẳng (INV-CAT-10, 11).
- **"Dùng được" của danh mục (INV-CAT-97):** chính nó và mọi tổ tiên `ACTIVE`. Cây tối đa ba cấp nên tổ tiên là cha và ông, đọc bằng tối đa hai lần tra theo khóa chính; hàm này thuộc kho danh mục (`isUsable(categoryId)`), feature 07 gọi khi tạo, đăng lại sản phẩm.
- **`sort_order`:** không duy nhất (trạng thái trung gian khi sắp lại); thứ tự đúng do lệnh sắp xếp gửi cả danh sách anh em.
- **Loại:** bảng bao đóng (thêm bảng, lệnh chèn riêng và rủi ro lệch khi tổ tiên chỉ cần hai bước); cột `COALESCE(parent_id, hằng)` trong một chỉ mục duy nhất (hằng ma thuật, khó đọc hơn hai chỉ mục từng phần); xóa mẫu hay thương hiệu (INV-CAT-12, 21: không xóa).

## T2. Miền · Dự kiến

- **Mã định danh:** use case sinh UUID v7 qua cổng `IdGenerator` (`common-domain`), truyền vào aggregate (như `SellerProfile.submit`). Mọi lớp `*Id` bọc `UUID`.
- **`Category`:** `createRoot`, `createChild(parent...)` (cha cấp ba thì `MAX_DEPTH_EXCEEDED`; cha đóng vẫn tạo được), `rename`, `reorder`, `deactivate`, `activate`, `replaceAssignments`, `markDeleted`. `level` và `parentId` không đổi sau khi tạo. `replaceAssignments` chỉ ở cấp ba; mẫu lặp trong danh sách bị từ chối; mỗi dòng kiểm ràng buộc riêng hợp với kiểu nhập của mẫu. Mẫu **mới thêm** phải dùng được (use case kiểm vì cần đọc mẫu); mẫu đã gán nay ngừng dùng vẫn giữ, sửa được.
- **`AttributeTemplate`:** `create` (kiểu và đơn vị bất biến), `updateDetails`, `deactivate`, `activate`, `addOption` (kiểu chọn, mẫu đang dùng, `value` chưa có), `updateOptionDisplayValue`, `deactivateOption`, `activateOption`, `reorderOptions` (đủ mọi tùy chọn đúng một lần), `isUsable()`. Mọi thay đổi tùy chọn tăng phiên bản của mẫu.
- **`Brand`:** `create`, `rename`, `deactivate`, `activate`. Không có slug.
- **Người thực hiện:** `CatalogActor(accountId, role)` dựng từ token ở controller; `requireAdmin()` ném `CATALOG_ROLE_NOT_ALLOWED` (403) khi vai khác `ADMIN`. Mọi use case ghi gọi nó đầu tiên (Q2).
- **Sự kiện miền (mỗi aggregate một loại, mang loại thay đổi):** `CategoryChanged(categoryId, version, change, ...)`, `AttributeTemplateChanged(templateId, version, change, ...)`, `BrandChanged(brandId, version, change, ...)`. `change` là `CREATED`, `RENAMED`, `REORDERED`, `ASSIGNMENTS_REPLACED`, `DEACTIVATED`, `ACTIVATED`, `DELETED`, `DETAILS_UPDATED`, `OPTION_ADDED`, ... Aggregate phát bằng `addDomainEvent`; phiên bản trong event là `version() + 1` cho aggregate đã lưu, và `0` khi tạo mới (Hibernate bắt đầu ở 0, như feature 04).
- **Mã lỗi (`ErrorCode` theo quy ước chung, mã chữ là tên hằng):** `CATEGORY_NOT_FOUND`, `CATEGORY_MAX_DEPTH_EXCEEDED`, `CATEGORY_NOT_LEAF`, `CATEGORY_NAME_TAKEN`, `CATEGORY_HAS_CHILDREN`, `CATEGORY_INVALID_ORDER`, `ATTRIBUTE_TEMPLATE_NOT_FOUND`, `ATTRIBUTE_TEMPLATE_NAME_TAKEN`, `ATTRIBUTE_TEMPLATE_INACTIVE`, `ATTRIBUTE_OPTION_NOT_FOUND`, `ATTRIBUTE_OPTION_VALUE_TAKEN`, `ATTRIBUTE_INVALID_OPTION_ORDER`, `ATTRIBUTE_INPUT_TYPE_NOT_SELECT`, `ATTRIBUTE_ASSIGNMENT_DUPLICATED`, `ATTRIBUTE_CONSTRAINTS_INVALID`, `BRAND_NOT_FOUND`, `BRAND_NAME_TAKEN`, `CATALOG_INVALID_STATE` (Q5), `CATALOG_FIELD_INVALID` (400, nêu tên trường), `CATALOG_ROLE_NOT_ALLOWED` (403), `CONCURRENT_UPDATE` (409). Thống nhất với seller: 404 không có, 409 vi phạm quy tắc hoặc trạng thái, 400 dữ liệu vào sai.
- **Loại:** để Spring kiểm vai bằng chú thích ngay trong lớp trình bày (Q2); đưa quy tắc "tên duy nhất" vào miền bằng cách tải mọi anh em và so từng tên (O(n) mỗi lần tạo và không thay chỉ mục duy nhất, ADR-0011).

## T3. Use case · Dự kiến

Mỗi use case ghi (`@Transactional`), cùng khuôn: `actor.requireAdmin()` → kiểm sớm qua kho khi cần (tên đã dùng, để trả lỗi rõ: ADR-0011 mục 2) → đọc aggregate → gọi hành vi miền → `saveAndFlush` (vi phạm ràng buộc nổ ngay trong giao dịch) → `eventDispatcher.dispatchAll(...)` → `clearDomainEvents()`. Không `@Cacheable`/`@CacheEvict`: C1a chưa có bộ nhớ đệm (`service.md` mục 8).

- Danh mục: tạo, đổi tên, sắp xếp (đủ mọi anh em, đúng một lần), ngừng, bật lại, xóa (chỉ kiểm con, Q7), thay danh sách thuộc tính.
- Mẫu: tạo, sửa chi tiết, ngừng, bật lại, thêm / sửa nhãn / ngừng / bật lại / sắp xếp tùy chọn.
- Thương hiệu: tạo, đổi tên, ngừng, bật lại.
- Đọc (không giao dịch ghi, không event): cây cho người dùng (chỉ nhánh `ACTIVE` đến lá, `RM-CAT-01`), cây đầy đủ cho quản trị (kèm trạng thái và cờ "dùng được"), thuộc tính được gán của một lá (chỉ mẫu dùng được, kèm tùy chọn đang dùng, `RM-CAT-02`), danh sách thương hiệu đang dùng có tìm theo tên và phân trang (`RM-CAT-03`), danh sách mẫu và chi tiết mẫu cho quản trị.
- **Event handler (cùng giao dịch):** `CategoryChangedOutboxHandler`, `AttributeTemplateChangedOutboxHandler` ghi `EVT-CAT-03` (T5); `CategoryChangedAuditHandler`, `AttributeTemplateChangedAuditHandler`, `BrandChangedAuditHandler` ghi `EVT-CAT-04` (T4), `getOrder` thấp hơn handler hộp thư để lỗi kiểm toán rollback cả hai.

## T4. Dấu vết kiểm toán · Dự kiến

Khai báo trong danh mục hành động của `catalog-service` (`AuditActionCatalog`, như `AuditConfig` của seller). Đích: `CATEGORY`, `ATTRIBUTE_TEMPLATE`, `BRAND`; người thực hiện: mã tài khoản và vai quản trị.

| Hành động | Khi | Dữ kiện (`details`) |
|---|---|---|
| `CATEGORY_CREATED` | Tạo | `name`, `parentId` (nếu có), `level` |
| `CATEGORY_UPDATED` | Đổi tên, sắp xếp, thay thuộc tính, bật lại | `change`; với `RENAMED`: `oldName`, `newName`; với `REORDERED`: `parentId`; với `ASSIGNMENTS_REPLACED`: `count` |
| `CATEGORY_DEACTIVATED` | Ngừng | (chỉ đích) |
| `CATEGORY_DELETED` | Xóa (Q4) | `name`, `parentId` (sau khi xóa không còn chỗ tra tên, nên ghi ở đây) |
| `ATTRIBUTE_CREATED`, `_UPDATED`, `_DEACTIVATED` | Tạo; sửa chi tiết, tùy chọn, bật lại; ngừng mẫu | `name`, `inputType` (khi tạo); `change` (+ `optionId`, `value` với thay đổi tùy chọn); chỉ đích |
| `BRAND_CREATED`, `_UPDATED`, `_DEACTIVATED` | Tạo; đổi tên, bật lại; ngừng | `name` (khi tạo); `change` (+ `oldName`, `newName`); chỉ đích |

Mọi hành động còn mang `version` (phiên bản sau thay đổi), theo bảng hành động ở `audit-service/analysis.md` mục 8. Tên danh mục, mẫu, thương hiệu là dữ liệu quản trị, không nhạy cảm. Khóa dữ kiện phải qua chính sách `strict` của `AuditDetailsPolicy`; nếu một khóa bị chặn thì đổi tên khóa, ghi lại ở đây khi gặp. Hành động hay khóa chưa khai báo làm thư viện ném lỗi và rollback (E17 của `flow.md`), được bắt bằng test.

## T5. Hộp thư: `EVT-CAT-03` · Dự kiến

- **Danh mục:** khóa thông điệp là mã danh mục, phát khi tạo, đổi tên, sắp xếp, ngừng, bật lại, thay thuộc tính, xóa. Nội dung: `categoryId`, `version`, `change`. **Mẫu thuộc tính:** khóa là mã mẫu, phát khi đổi cách danh mục dùng mẫu (đổi chi tiết, ngừng, bật lại, thêm hay đổi tùy chọn); nội dung: `templateId`, `version`, `change`. Thương hiệu: không phát.
- Mã chủ đề và tên event ghi vào `5. event-catalog.md` khi đóng feature (mục `catalog` của danh mục event hiện là hợp đồng cũ, thay bằng hợp đồng này; không có bên nhận ở C1a).
- Hộp thư cùng giao dịch với dữ liệu và kiểm toán (R12); hình bảng và bộ chuyển theo feature 01.

## T6. API và bảo mật · Dự kiến

| Việc | Phương thức, đường dẫn | Ghi chú |
|---|---|---|
| Cây (người dùng) | `GET /api/categories` | Đã đăng nhập; chỉ nhánh dùng được |
| Thuộc tính của một lá | `GET /api/categories/{id}/attributes` | Đã đăng nhập |
| Thương hiệu | `GET /api/brands?keyword=&page=&size=` | Đã đăng nhập; đang dùng; `size` tối đa 50 |
| Cây đầy đủ, dòng gán | `GET /api/admin/categories/tree`, `GET /api/admin/categories/{id}/assignments` | `ADMIN` |
| Ghi danh mục | `POST /api/admin/categories` (`name`, `parentId?`), `PUT .../{id}` (`name`), `PUT .../reorder` (`parentId?`, `categoryIds`), `POST .../{id}/deactivate`, `.../activate`, `DELETE .../{id}`, `PUT .../{id}/attributes` (danh sách dòng gán) | `ADMIN`; tạo trả 201 kèm `id`, `version` |
| Ghi mẫu | `/api/admin/attribute-templates` (tạo, sửa chi tiết, `deactivate`, `activate`) và `.../{id}/options` (thêm, sửa nhãn, `deactivate`, `activate`, `PUT .../options/order`); `GET` danh sách, chi tiết | `ADMIN` |
| Ghi thương hiệu | `/api/admin/brands` (tạo, `PUT` đổi tên, `deactivate`, `activate`); `GET` danh sách, chi tiết | `ADMIN` |

- **Bảo mật:** `SecurityConfig` bắt đăng nhập mọi đường (không còn `permitAll`); vai kiểm trong use case (Q2). Người thực hiện dựng từ `Jwt` như seller.
- **Lỗi từ ràng buộc (ADR-0011 mục 3):** bộ xử lý ánh xạ **tên ràng buộc** sang mã lỗi: `uq_category_parent_name_key`, `uq_category_root_name_key` → `CATEGORY_NAME_TAKEN`; `uq_attribute_template_name` → `ATTRIBUTE_TEMPLATE_NAME_TAKEN`; `uq_attribute_option_template_value` → `ATTRIBUTE_OPTION_VALUE_TAKEN`; `uq_brand_name_key` → `BRAND_NAME_TAKEN`; FK cha `RESTRICT` → `CATEGORY_HAS_CHILDREN` khi vi phạm lúc xóa cha còn con, và `CATEGORY_NOT_FOUND` (404) khi vi phạm lúc chèn con dưới cha vừa bị xóa (phân biệt bằng câu lệnh SQL của lỗi); `OptimisticLockingFailureException` → `CONCURRENT_UPDATE`. Ràng buộc không biết thì 500, để lộ chứ không đoán.
- UUID sai ở đường dẫn: 400 `INVALID_PARAMETER` do `common-web` xử lý chung.

## T7. Quan sát và hạ tầng · Dự kiến

- Log theo `logging.md`, cùng mẫu feature 04: một dòng INFO ở mỗi thao tác ghi (thao tác, mã đích, mã người thực hiện, kết quả), không log dữ liệu thừa; bộ đếm kết quả theo thao tác.
- **Chạy thật (phần F):** ngăn xếp cách ly tiền tố `probe-` (Postgres, Kafka, Kafka Connect, ứng dụng; không `audit-service`, không Redis, không MinIO), không chạm hạ tầng dev. Cơ sở dữ liệu dev của catalog có lịch sử migration cũ, dựng lại khi bạn muốn chạy nó; xóa dữ liệu dev báo trước, nêu đúng đối tượng.
- **Phạm vi dịch vụ ở feature này:** chỉ cấu trúc danh mục. Phần sản phẩm, biến thể, ảnh, bản chụp tìm kiếm thuộc feature 07, 08 và không nằm trong bản dựng của feature này (cách thực hiện ở `docs/draft/_so-sanh-catalog.md` mục 8, 9).

## T8. Kiểm chứng · Dự kiến

Viết từ kịch bản S9, R6, R10, R12 và INV (không từ mã). Test miền sẵn có của danh mục, mẫu, thương hiệu được chỉnh theo hợp đồng mới trước, rồi mới viết thêm.

| # | Kiểm chứng | Cách |
|---|---|---|
| KT-01 | Miền: ba cấp, lá, tên duy nhất theo khóa, gán thuộc tính (cấp ba, lặp, mẫu mới), mẫu (kiểu và đơn vị bất biến, tùy chọn, `isUsable`), thương hiệu; vai `ADMIN` | Đơn vị, không Spring |
| KT-02 | Hàm khóa tên: bỏ dấu, `đ`, hoa thường, khoảng trắng; so với trùng khác dấu | Đơn vị |
| KT-03 | Use case với kho giả: thứ tự `requireAdmin` → kiểm sớm → lưu → event; event mang `version() + 1`; kiểm toán và hộp thư đúng hành động, đúng dữ kiện | Đơn vị |
| KT-04 | Migration trên Postgres thật: đủ bảng, tên ràng buộc đúng; từng ràng buộc duy nhất, FK, `CHECK` có hiệu lực (phá thử bỏ từng cái thì test đỏ) | Testcontainers |
| KT-05 | API: 401, 403 vai thường, 404, 400, 409 từng mã; không có `permitAll` | Testcontainers + HTTP |
| KT-06 | R10, đua nhau trên Postgres thật: 20 luồng cùng tên (cấp một, dưới một cha, mẫu, tùy chọn, thương hiệu) đúng một thành công; hai luồng sửa cùng một danh mục: một `CONCURRENT_UPDATE` | Testcontainers |
| KT-07 | R12: ghi hộp thư hay kiểm toán lỗi (hành động chưa khai báo) thì rollback, dữ liệu giữ nguyên, không hàng hộp thư | Testcontainers |
| KT-08 | Dữ liệu: xóa danh mục còn con bị từ chối kể cả khi đua nhau với tạo con (FK) | Testcontainers |
| KT-09 | Chạy thật: một thao tác quản trị ra thông điệp kiểm toán và `EVT-CAT-03` đúng khóa, đúng nội dung; thao tác lỗi không ra gì | Ngăn xếp cách ly |

Phá thử bằng đột biến sau mỗi bước (bỏ kiểm vai, bỏ kiểm cấp, bỏ khóa tên, bỏ `version() + 1`, bỏ handler kiểm toán...), như feature 04.

**Kết quả KT-09 · Đã làm (`2026-11-04`).** Ngăn xếp cách ly `probe-catalog`, token ký thật, không chạm hạ tầng dev. Nhóm A (Postgres và ứng dụng): 66 mục đạt. Nhóm B: 18 mục đua nhau 20 luồng đạt (một 201 cho cùng tên; 15 vòng xóa cha đua nhau tạo con không mồ côi) và 15 mục Kafka đạt (thông điệp thật trên `catalog.category.changed`, `catalog.attribute-template.changed`, `audit.action.recorded`, đúng khóa, đúng nội dung, thao tác lỗi không ra gì). Chưa kiểm: Connect dừng rồi chạy lại, `audit-service` nhận và lưu (feature 13).

## T9. Thứ tự làm · Dự kiến

1. **Nền:** vô hiệu hóa phần ngoài phạm vi; `V1` gốc, hộp thư; kiểu mã UUID; `NameKey`.
2. **Mẫu thuộc tính** (độc lập, các thứ khác phụ thuộc nó).
3. **Thương hiệu** (nhỏ, độc lập).
4. **Cây danh mục và gán thuộc tính.**
5. **Đọc, API, bảo mật, bộ xử lý lỗi.**
6. **Chạy thật** trên ngăn xếp cách ly.
7. **Đóng:** `data.md`, `api.yaml`, `service.md` ("Đã làm" cho phần của feature 05), `5. event-catalog.md`, `00-features.md`, `deferred.md`.

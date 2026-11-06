# Feature 05: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần thường, người rà), và cách biết bước đã xong. Chọn cách thực hiện (tự viết hay giao `agy`) khi bắt đầu từng bước, không chốt trước.

Quy tắc giống feature 01 đến 04: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `tech.md` T8 và `flow.md` trước khi viết mã (đỏ trước, phá thử sau); quyết định kỹ thuật đặt ngay trước bước cần dùng nó; một lượt `agy` làm hoặc mã chính hoặc test, không cả hai.

**Quy tắc riêng của feature này: đối chiếu mã hiện có trước khi viết.** Dịch vụ đã có mã cho danh mục, mẫu thuộc tính, thương hiệu, viết kỹ về trạng thái. Trước mỗi bước 4 đến 7, đọc phần mã tương ứng, so với `flow.md` và `tech.md`, ghi kết quả vào file tạm `docs/draft/_so-sanh-catalog.md` (mục 11 trở đi; tài liệu chính thức không nhắc mã cũ, `work-process.md` mục 8). Chỗ khớp thì giữ và chỉnh tối thiểu; chỗ lệch ghi rõ "theo mã" hay "theo tài liệu" và đưa người quyết khi nó đổi hành vi; chỗ mã có mà tài liệu chưa có thì hỏi trước khi mang vào. Đặc tả giao `agy` viết sau bước đối chiếu, không trước.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu viết **trước khi code**; cột cuối điền **sau khi làm xong** (`work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Xác thực token, dựng người thực hiện | Resource server như seller; `CatalogActor(accountId, role)` dựng từ `Jwt` ở controller; `SecurityConfig` bắt đăng nhập mọi đường (`tech.md` T6) | `infrastructure/crosscutting/config`: `SecurityConfig`; `presentation`: bộ dựng người thực hiện | Đã làm (`2026-11-04`): `SecurityConfig` bắt đăng nhập mọi `/api/**`, đường khác từ chối kể cả với `ADMIN`; `CatalogActorResolver` dựng người thực hiện từ `Jwt`. KT-05; KT-09 (token không có, khóa lạ, hết hạn, rác đều 401) |
| Kiểm vai `ADMIN` (ghi) | `CatalogActor.requireAdmin()` ném `CATALOG_ROLE_NOT_ALLOWED`; mọi use case ghi gọi đầu tiên (Q2) | `domain`: `CatalogActor`; `application`: use case ghi | Đã làm (`2026-11-04`): `CatalogActor.requireAdmin()` ở dòng đầu mọi use case ghi, 403 `CATALOG_ROLE_NOT_ALLOWED`, không đọc không ghi kho. KT-03, KT-05, KT-09 |
| Kiểm dữ liệu vào | Kiểm ở request (Bean Validation) cho hình dạng; độ dài, rỗng, kiểu nhập ở miền, lỗi nêu tên trường `CATALOG_FIELD_INVALID` (`tech.md` T2) | `presentation/*/model`; `domain` | Đã làm (`2026-11-04`): hình dạng ở request, độ dài, rỗng, kiểu nhập ở miền, lỗi nêu tên trường `CATALOG_FIELD_INVALID`. Chạy thật lộ ra `PUT .../attributes` bỏ `required` hoặc `displayOrder` trả 400 `MALFORMED_REQUEST`; đã đổi sang kiểu bọc kèm mặc định |
| Đọc thực thể, khóa tên chuẩn hóa | `NameKey.of` (bỏ dấu qua `StringUtils.removeAccent`, chữ thường, gộp khoảng trắng); kiểm sớm qua kho để trả lỗi rõ (`tech.md` T1, T3) | `domain`: `NameKey`; kho từng aggregate | Đã làm (`2026-11-04`), **lệch có chủ đích**: `NameKey` dùng cho thương hiệu và danh mục; tên mẫu và giá trị tùy chọn là mã kỹ thuật người quản trị gõ, bất biến, so trùng thẳng, không có khóa (chốt `2026-11-02`) |
| Quy tắc miền (cấp, lá, kiểu nhập, tùy chọn, gán) | Hành vi của aggregate theo `tech.md` T2; mẫu mới thêm phải dùng được, kiểm ở use case vì cần đọc mẫu | `domain`: `Category`, `AttributeTemplate`, `Brand`; `application`: `ReplaceCategoryAttributes` | Đã làm (`2026-11-04`): `Category`, `AttributeTemplate`, `Brand`; `ReplaceCategoryAttributes` đọc mẫu để kiểm mẫu dùng được, không lặp, ràng buộc theo kiểu nhập. KT-01, KT-03 |
| Lưu, ràng buộc duy nhất làm chốt cuối | `saveAndFlush`; chỉ mục duy nhất có tên, `@Version` (`tech.md` T1, Q8) | `infrastructure/persistence`, adapter kho; `V1` | Đã làm (`2026-11-04`): adapter `saveAndFlush` trong giao dịch bắt buộc; chỉ mục duy nhất có tên và `@Version` trong `V1`. KT-04, KT-06 (20 luồng cùng tên: đúng một thắng, cả trên ứng dụng thật) |
| Phát sự kiện miền | Aggregate `addDomainEvent` (`CategoryChanged`, `AttributeTemplateChanged`, `BrandChanged`) mang `version() + 1`; use case `dispatchAll` rồi `clearDomainEvents` (`tech.md` T2, T3) | `domain`: ba lớp event; `application`; `EventDispatcherConfig` | Đã làm (`2026-11-04`): `CategoryChanged`, `AttributeTemplateChanged`, `BrandChanged` mang `version` sau thay đổi (0 khi tạo); một loại event cho mỗi aggregate, kèm `Change`. KT-03 |
| Ghi hộp thư `EVT-CAT-03` | Handler đồng bộ cùng giao dịch gọi `OutboxEventStore.store`; thương hiệu không có (`tech.md` T5, Q3) | `application/*/event`: hai handler hộp thư | Đã làm (`2026-11-04`): hai handler hộp thư (danh mục, mẫu), thương hiệu không có. KT-07 (lỗi ghi thì rollback), KT-09 (thông điệp thật trên Kafka) |
| Ghi hộp thư kiểm toán `EVT-CAT-04` | Handler đồng bộ cùng giao dịch gọi `AuditRecorder`; hành động và khóa dữ kiện khai báo một chỗ (`tech.md` T4, Q10) | `application/*/event`: ba handler kiểm toán; `AuditConfig`; `pom.xml`: `audit-events` | Đã làm (`2026-11-04`): ba handler kiểm toán chạy trước handler hộp thư (`getOrder` 50); `AuditConfig` khai báo `CATEGORY_*`, `ATTRIBUTE_*`, `BRAND_*` và khóa dữ kiện. KT-07, KT-09 |
| Chuyển hộp thư lên chủ đề | Connector theo feature 01; chỉ cấu hình tên chủ đề cho `catalog` ở bước chạy thật | Hạ tầng; chạy thật ở bước 8 | Đã làm (`2026-11-04`) trên ngăn xếp cách ly (connector `outbox-connector.sh register catalog`). **Chưa làm ở hạ tầng dev**: container `postgres-catalog` đã khai báo trong compose dev nhưng chưa dựng lại sau khi gộp `V1`, connector dev chưa đăng ký |
| Đọc cây, thuộc tính được gán, thương hiệu | Truy vấn đọc riêng ở tầng ứng dụng; cây người dùng chỉ nhánh dùng được, cây quản trị đầy đủ kèm cờ "dùng được" (`tech.md` T3) | `application`: use case đọc | Đã làm (`2026-11-04`): `GetCategoryTree`, `GetAdminCategoryTree`, `GetCategoryAttributes`, `GetCategoryAssignments`, `GetBrand`, `ListBrands`, `ListAdminBrands`. KT-05 |
| Lỗi từ ràng buộc và đua nhau | Bộ xử lý ánh xạ **tên ràng buộc** sang mã lỗi; `OptimisticLockingFailure` → `CONCURRENT_UPDATE`; ràng buộc lạ → 500 (`tech.md` T6, ADR-0011) | `presentation`: bộ xử lý lỗi của catalog | Đã làm (`2026-11-04`): `PersistenceConflictExceptionHandler` ánh xạ theo tên ràng buộc; `fk_category_parent` có hai nghĩa (chèn con dưới cha vừa xóa → 404, xóa cha còn con → 409) phân biệt bằng loại câu lệnh. KT-06, KT-08, nhóm B (18 mục) |
| Dữ liệu và mã định danh | `V1` gốc, hộp thư theo thư viện; UUID v7 qua `IdGenerator` (`tech.md` T1, T2) | `src/main/resources/db/migration`; `crosscutting/config` | Đã làm (`2026-11-04`): `V1__create_catalog_structure.sql` thay mười chín migration cũ; UUID v7 qua `IdGenerator`; thư viện ULID đã gỡ. KT-04 |

## Điều kiện bắt đầu

Đã chốt, không bàn lại: ADR-0001, ADR-0003, ADR-0008, ADR-0011 (Accepted); feature 01 (hộp thư), 02 (bản ghi kiểm toán), 04 (mẫu sự kiện miền → handler cùng giao dịch) đã đóng; Q1, Q2, Q3, Q4, Q6, Q9 chốt `2026-11-01`; Q5, Q7, Q8, Q10 theo đề xuất, chưa bị phản đối. Cơ sở dữ liệu dev của catalog: người dùng cho phép xóa và dựng lại dữ liệu (ý định, `2026-11-01`); **chưa xóa gì**, báo đúng đối tượng trước khi làm.

**Không sửa thư viện dùng chung.** Chỉ thêm phụ thuộc `audit-events` và `common-utils` vào `catalog-service`; không sửa `libs`. Vì vậy không phải chạy lại test của service khác.

**Phần ngoài phạm vi trong cây mã** (sản phẩm, biến thể, bản chụp tìm kiếm, kho ảnh, bộ nhớ đệm, và test của chúng) được vô hiệu hóa bằng cách thêm tiền tố `//~ ` mỗi dòng (script đảo được; cách làm ở file tạm mục 8). Danh sách tệp đã comment ghi vào mục "Triển khai" của bước 3 để feature 07, 08 hoàn lại.

**Ghi hạ tầng dev:** không có thêm. Kiểm thử dùng Testcontainers và stack riêng tiền tố `probe-` (Postgres, Kafka, Connect; không Redis, MinIO, `audit-service`), không dùng cơ sở dữ liệu và Kafka dev. Cần Docker chạy được. Máy thiếu bộ nhớ: chạy thật chia nhỏ từng nhóm, ứng dụng `-Xmx384m`, bị dừng thì không tự chạy lại. Không commit.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 4 và `tech.md` T8: mỗi đường không suôn sẻ E1 đến E19 có KT hoặc lý do không cần chưa; KT-09 là ca chạy thật (bước 8), còn lại nằm trong Maven. Quyết định bước nào giao `agy`.

**Đối chiếu E1 đến E19 với KT** (đề xuất `2026-11-01`, **người đồng ý `2026-11-04`**):

| Đường | KT kiểm | Ghi chú |
|---|---|---|
| E1 tên trùng | KT-01, KT-02, KT-05 | KT-02 là hàm khóa tên; KT-05 qua API |
| E2 đua nhau cùng tên | KT-06 | Cấp một, dưới một cha, mẫu, tùy chọn, thương hiệu |
| E3 danh mục dưới cấp ba | KT-01, KT-05 | |
| E4 danh sách thuộc tính sai (không lá, mẫu mới không dùng được, lặp, ràng buộc) | KT-01, KT-03, KT-05 | |
| E5 xóa còn con / đã có sản phẩm | KT-01, KT-08 | Nửa sản phẩm chờ feature 07 (`deferred.md`) |
| E6 xóa mẫu / thương hiệu | KT-05 | Không có đường xóa: gọi trả 404 hoặc 405 |
| E7 đổi kiểu nhập, đơn vị | KT-01, KT-05 | |
| E8 tùy chọn sai | KT-01, KT-05 | |
| E9 ngừng cha, con giữ cờ | KT-01, KT-03 | Cờ "dùng được" tính từ cây |
| E10 tạo con dưới cha đang ngừng | KT-01 | |
| E11 ngừng / bật lại lặp | KT-01, KT-05 | |
| E12 vai thường gọi ghi | KT-03, KT-05 | Không đọc, không ghi kho |
| E13 không token, token sai | KT-05 (không token); KT-09 (chữ ký, hết hạn) | MockMvc `jwt()` không kiểm chữ ký |
| E14 mã không có, sai định dạng | KT-05 | |
| E15 dữ liệu vào sai | KT-01, KT-05 | |
| E16 ghi hộp thư lỗi giữa chừng | KT-07 | Rollback cả dữ liệu |
| E17 hành động kiểm toán chưa khai báo | KT-03, KT-07 | Lỗi lập trình, thư viện ném |
| E18 Kafka hay bộ chuyển hỏng | KT-07 (hàng nằm lại); KT-09 | |
| E19 chưa có bên nhận `EVT-CAT-03` | KT-09 | Chủ đề có thông điệp, không ai nhận |

Xong khi: mọi đường E1 đến E19 có ít nhất một KT; bảng đối chiếu được người đồng ý.

**Đề xuất giao `agy`:** bước 4, 5, 6, 7 (hai lượt riêng cho mỗi bước: lượt một test, lượt hai mã). Tự làm: bước 2 (tài liệu), bước 3 (nền, `V1`, script comment), bước 8 (chạy thật), bước 9 (đóng); riêng phần khóa tên, đua nhau và rollback trong bước 4 đến 7 tôi viết đặc tả kỹ rồi tự rà diff.

### Bước 2. `data.md`, `api.yaml`, chi tiết `service.md` · Người, Agent soạn

Viết trước mã (`work-process.md` mục 3.3): `data.md` (năm bảng, ràng buộc có tên, `V1` theo `tech.md` T1; hộp thư theo thư viện); `api.yaml` (các đường ở `tech.md` T6, request, mã lỗi); `service.md` bổ sung chi tiết feature 05 (khóa tên, `CatalogActor`, ba sự kiện miền, handler, mã lỗi) và sửa mục 4 cho khớp cách kiểm vai (Q2).

Xong khi: ba tệp khớp `tech.md` T1 đến T6; `service.md` không còn mô tả bảng bao đóng hay slug.

**Đối chiếu với mã trước khi viết** (kết quả ở `docs/draft/_so-sanh-catalog.md` mục 11): giữ đường dẫn; ngừng dùng bằng `POST .../deactivate` cho cả bốn loại (mã đang dùng `DELETE` cho mẫu, tùy chọn, thương hiệu); lệnh ghi trả tài nguyên kèm `version`; thân gán thuộc tính là `{ "items": [...] }`; gộp hai đường đọc thương hiệu thành một; `size` tối đa 50; chưa mang đường "mẫu chưa gán" vào C1a.

**Trạng thái:** Đã làm `2026-11-01` (`data.md`, `api.yaml`, `service.md`, `category.md`). Bộ ràng buộc riêng theo danh mục (`constraints`) **để bước 6 so với mã rồi chốt**: ràng buộc chung chỉ kiểm hình thức, quy tắc cụ thể khai báo lúc tạo danh mục hoặc sản phẩm; hiện `data.md`, `api.yaml` viết theo mã làm chỗ giữ.

### Bước 3. Nền: phạm vi, `V1`, mã định danh · Người

Việc: (1) script đảo được thêm `//~ ` vào tệp ngoài phạm vi (bảng 8 của file tạm), chạy, ghi danh sách tệp; (2) bỏ khỏi `pom.xml` và cấu hình Redis, Caffeine, MinIO, Kafka producer không dùng, ULID; thêm `audit-events`, `common-utils`; (3) `V1` cấu trúc và hộp thư, xóa các migration `V1` đến `V19` cũ (nhớ đây là thay đổi cấu trúc, không chạm cơ sở dữ liệu dev); (4) `IdGenerator` UUID v7 như các service khác; (5) `NameKey` và test của nó (KT-02); (6) `AuditConfig` rỗng, `EventDispatcherConfig`, `SecurityConfig` bắt đăng nhập.

**Đối chiếu trước:** `application.properties`, `CacheConfig`, `SecurityConfig`, `IdGeneratorConfig`, `JpaConfig`, `PersistenceConflictExceptionHandler`, `pom.xml`.

Xong khi: dịch vụ biên dịch; test của KT-02 xanh; KT-04 (migration trên Postgres thật, Testcontainers) xanh với từng ràng buộc phá thử đỏ.

**Triển khai (`2026-11-01`):** xong phần nền; kiểu mã UUID chuyển dần ở bước 4–6 (xem "Lệch kế hoạch" dưới).
- **Vô hiệu hóa bằng `//~ `:** 115 tệp Java trong `services/catalog-service/src`. Thư mục: `domain/{product,variant}`, `application/{product,variant}` (cả `event`, `search_sync`), `presentation/{product,variant,dev,search}`, `infrastructure/persistence/{product,variant}`, `infrastructure/adapter/{repository/product,repository/variant,storage}`, `infrastructure/crosscutting/cache`, ba tệp cấu hình `CacheConfig`, `MinioConfig`, `MinioProperties`; thêm hai tệp kiểm giá trị thuộc tính của sản phẩm (`AttributeTemplateDomainService`, `AttributeConstraintChecker`) và test của chúng (`ProductAttributeValidationTest`, `AttributeConstraintCheckerTest`) cùng test `product/`, `variant/`. **Hoàn lại ở feature 07, 08:** trong các thư mục và tệp trên, gỡ đúng tiền tố `//~ ` ở đầu mỗi dòng của tệp có dòng đầu bắt đầu bằng `//~ ` (gỡ rồi `git status` sạch từng byte, đã thử).
- **Bỏ hẳn (không comment):** chú thích và lời gọi bộ nhớ đệm trong 17 use case giữ lại; `CategoryRepository.hasProductReference` và lần kiểm trong `DeleteCategory` (nửa "đã từng có sản phẩm" của INV-CAT-98 chờ feature 07, ghi ở bước 9).
- **`pom.xml`, cấu hình:** bỏ cache, Redis, Caffeine, Kafka, MinIO, `rate-limiter-starter`, `spring-kafka-test`; thêm `audit-events`, `common-utils`; bỏ khối Redis, Kafka, MinIO trong `application*.properties`. Thư viện ULID **còn** đến cuối bước 6.
- **`V1__create_catalog_structure.sql`** thay mười chín migration cũ (đã xóa khỏi cây, còn trong git); chưa chạm cơ sở dữ liệu dev. `NameKey` + `NameKeyTest` (14 ca); `CatalogSchemaConstraintTest` (21 ca, Postgres Testcontainers cách ly). Phá thử bỏ từng ràng buộc (mười một lần: duy nhất thương hiệu, `CHECK` đơn vị, duy nhất tùy chọn, hai `CHECK` cấp, FK cha, hai chỉ mục tên, FK mẫu của dòng gán, duy nhất hộp thư, `CASCADE`) đều làm test đỏ.
- **Cấu hình:** `SecurityConfig` bắt đăng nhập mọi `/api/**`; `AuditConfig` rỗng; `IdGeneratorConfig` thêm `IdGenerator` UUID v7 (và `Clock`); `JpaConfig` khai báo kho JPA rõ ràng (hộp thư tự khai báo riêng).
- **Lệch kế hoạch có chủ đích:** (1) đổi kiểu mã sang UUID chạm 71 tệp và trùng với việc viết lại từng aggregate, nên làm ở bước 4, 5, 6 cho từng aggregate (hiện `ddl-auto=validate` sẽ không khởi động với `V1` mới; chưa có test khởi động ứng dụng). (2) `PersistenceConflictExceptionHandler` viết lại ở bước 7 như ghi sẵn. (3) `EventDispatcherConfig` giữ nguyên, đã đủ.
- **Kết quả:** 74 test của catalog xanh (39 cũ còn lại, 35 mới). Không chạy test của dịch vụ khác vì không sửa thư viện dùng chung.

### Bước 4. Mẫu thuộc tính và tùy chọn · Agent

**Đối chiếu trước:** miền `AttributeTemplate`, `AttributeOption`, `InputType`; mười một use case; phần lưu trữ; controller. Test sẵn có (`AttributeTemplateTest`, `CreateAttributeTemplateTest`) chỉnh trước theo hợp đồng mới (UUID, khóa tên, `version`, sự kiện), rồi thêm KT-01, KT-03, KT-05, KT-06 (cho mẫu và tùy chọn), KT-07. Đột biến sau bước.

Xong khi: test xanh; đột biến (bỏ kiểm vai, bỏ khóa tên, bỏ `version() + 1`, bỏ handler kiểm toán) làm test đỏ; cột "Triển khai" của các dòng liên quan điền xong.

**Triển khai (`2026-11-01`):** xong phần miền, use case, lưu trữ, API; **KT-05, KT-06, KT-07 dời sang bước 7**. Lý do: ba kiểm chứng này cần dựng cả ứng dụng với Postgres thật, mà `ddl-auto=validate` đòi mọi thực thể (thương hiệu, danh mục) đã khớp `V1`; hai aggregate đó chuyển kiểu ở bước 5, 6.
- Đối chiếu ghi ở `docs/draft/_so-sanh-catalog.md` mục 12 (14 điểm, không có điểm mã-có-mà-tài-liệu-chưa-có).
- **Chốt thêm `2026-11-02`:** tên mẫu và giá trị tùy chọn là mã kỹ thuật bắt buộc đúng dạng (tên: chữ thường không dấu, chữ số, `_`; giá trị: thêm `-`), sai thì 400, không tự sửa; bỏ cột `name_key` của mẫu và `value_key` của tùy chọn, so trùng thẳng trên `name` và `value` (`uq_attribute_template_name`, `uq_attribute_option_template_value`, kèm `ck_..._format`). Đã sửa `analysis`, `service.md`, `data.md`, `api.yaml`, `tech.md`, `V1`, mã và test; cả bộ catalog 101 ca xanh, ba đột biến thêm (tên cho phép `-`, giá trị cấm `-`, bỏ kiểm độ dài) đều làm test đỏ.
- Mới: `CatalogActor`, `CatalogErrorCode`, `CatalogFieldException`, `ChangeContext` (dùng chung cho bước 5, 6); `AttributeTemplateChanged` (một loại event mang `change`); `TemplateUpdater` (khuôn chung lệnh sửa mẫu); handler kiểm toán (`getOrder` 50) và hộp thư; `AuditConfig` khai báo `ATTRIBUTE_CREATED/UPDATED/DEACTIVATED`; `CatalogActorResolver`; controller mới dùng `POST .../deactivate`, kết quả trả mẫu kèm `version`.
- Mã mẫu và tùy chọn đã sang UUID; ánh xạ lưu trữ không còn phản xạ. Mã danh mục còn tham chiếu mẫu qua phép đổi chuỗi-UUID tạm ở `CategoryMapper` và ba use case đọc, gỡ ở bước 6.
- Test: `AttributeTemplateTest` 25 ca (KT-01), `AttributeTemplateUseCaseTest` 12 ca với danh mục hành động thật của `AuditConfig` (KT-03), cả bộ catalog 95 ca xanh. Đột biến 18 chỗ (bỏ kiểm vai ở bốn use case, `+ 1`, kiểm tên sớm, khóa tên, trùng tùy chọn, guard trạng thái, guard kiểu, sắp xếp đủ, đơn vị, đổi hành động kiểm toán, bỏ hộp thư, bỏ phát event, `isUsable`): 17 làm test đỏ; còn lại bỏ `clearDomainEvents` không đỏ vì mỗi lần gọi nạp aggregate mới, chấp nhận.

### Bước 5. Thương hiệu · Agent

Cùng khuôn bước 4, nhỏ hơn: không có `EVT-CAT-03`, không có slug. **Đối chiếu trước:** `Brand`, tám use case, lưu trữ, controller, `BrandTest`, `BrandNameUniqueTest`.

**Triển khai (`2026-11-03`):** xong phần miền, use case, lưu trữ, API.
- Đối chiếu ghi ở `docs/draft/_so-sanh-catalog.md` mục 13 (14 điểm, không có điểm mã-có-mà-tài-liệu-chưa-có). Đã cân nhắc lại slug với người dùng: giữ khóa tên cho thương hiệu và danh mục (tham chiếu bằng mã, chưa có URL thân thiện); mã nhập tay bất biến chỉ dành cho dữ liệu làm khóa trong dữ liệu (tên mẫu, giá trị tùy chọn).
- Mới: `BrandChanged` (một loại event mang `change`: `CREATED`, `RENAMED`, `DEACTIVATED`, `ACTIVATED`), `BrandUpdater` (khuôn lệnh ngừng và bật), `BrandView`, `BrandKeyword`, `BrandChangedAuditHandler` (`getOrder` 50; thương hiệu không ra hộp thư); `AuditConfig` khai báo `BRAND_CREATED/UPDATED/DEACTIVATED`. Gộp `SearchBrandsForSeller` vào `ListBrands`; `UpdateBrand` thành `RenameBrand`.
- `BrandId` sang UUID; entity có `@Version` và `name_key`, adapter `saveAndFlush`. Đường dẫn `GET /api/brands` cho mọi người đã đăng nhập, `/api/admin/brands/**` cần `ADMIN`, ngừng bằng `POST .../deactivate`; kết quả ghi kèm `version`; `size` trần 50.
- Test: `BrandTest` 13 ca (KT-01), `BrandUseCaseTest` 17 ca với danh mục hành động thật của `AuditConfig` (KT-03); cả bộ catalog 93 ca chạy, riêng `CatalogSchemaConstraintTest` chưa chạy được lần này vì Docker không bật (bảng `brand` của `V1` không đổi ở bước này). Đột biến 21 chỗ (khóa tên khi đổi tên, hai guard trạng thái, trạng thái lúc tạo, `+ 1` phiên bản, độ dài, kiểm tên trùng khi tạo và đổi tên, kiểm vai ở mọi use case, chỉ lấy `ACTIVE`, chuẩn hóa từ khóa, bốn chỗ ở kiểm toán), đỏ hết sau khi bổ sung hai test cho hai đột biến sống sót lần đầu (từ khóa có dấu, loại đích kiểm toán).
- KT-05, KT-06, KT-07 của thương hiệu làm ở bước 7 cùng các aggregate khác.

### Bước 6. Cây danh mục và gán thuộc tính · Agent, Người rà

**Đối chiếu trước:** `Category`, `CategoryLevel`, `CategoryAttributeAssignment`, `AttributeConstraints`, mười hai use case, lưu trữ và adapter, `CategoryTest`. Mang vào: cấp ba cố định, gán thay toàn bộ, ràng buộc riêng theo kiểu nhập. Không mang: slug, ảnh, khám phá, bảng bao đóng, bộ kiểm giá trị `AttributeConstraintChecker` (thuộc feature 07).

**Người rà trước khi giao:** danh sách thuộc tính thay toàn bộ (mẫu cũ ngừng dùng được giữ), "dùng được" qua cha và ông, xóa chỉ kiểm con (Q7).

Xong khi: KT-01, 03, 05, 06, 07, 08 xanh cho danh mục; đột biến (bỏ kiểm cấp, bỏ kiểm lá, bỏ khóa tên, bỏ FK cha) làm test đỏ.

**Triển khai (`2026-11-03`):** xong phần miền, use case, lưu trữ, API; **KT-05, KT-06, KT-07, KT-08 dời sang bước 7** (cần dựng cả ứng dụng với Postgres thật; xong bước này mọi thực thể đã khớp `V1`).
- Đối chiếu ghi ở `docs/draft/_so-sanh-catalog.md` mục 14 (16 điểm, không có điểm mã-có-mà-tài-liệu-chưa-có).
- **`constraints` chốt theo mã:** mẫu chỉ kiểm định dạng chung theo kiểu nhập; dòng gán giữ quy tắc cụ thể của ngành hàng (tám trường theo kiểu, kiểm hình khi gán); bộ áp lên giá trị sản phẩm kiểm khi đăng thuộc feature 07. Đã bỏ chữ "chưa chốt" ở `data.md`, `api.yaml`. Bỏ `discovery`.
- Bỏ: slug, ảnh, khám phá (`Discovery`, `FilterConfig`, `SortConfig`), bảng bao đóng (ba lớp lưu trữ), `CategoryUpdatedEvent`, `CategoryUpdatedHandler`, `UpdateCategory`. Gỡ các chỗ đổi chuỗi-UUID tạm ở bước 4 và thư viện ULID (`pom.xml`, `IdGeneratorConfig`).
- Mới: `CategoryChanged` (một loại event mang `change`: `CREATED`, `RENAMED`, `REORDERED`, `ASSIGNMENTS_REPLACED`, `DEACTIVATED`, `ACTIVATED`, `DELETED`), `CategoryUpdater` (khuôn lệnh ngừng và bật), `CategoryView`, `AssignmentView`, `RenameCategory`, `ReplaceCategoryAttributes`, `GetCategoryAssignments`, hai handler (kiểm toán `getOrder` 50 và hộp thư `catalog.category.changed`); `AuditConfig` khai báo `CATEGORY_CREATED/UPDATED/DEACTIVATED/DELETED`.
- `CategoryId` sang UUID; entity có `@Version` và `name_key`, cột `required`; adapter `saveAndFlush` rồi thay danh sách gán; `hasInactiveAncestor` đi lên theo khóa chính. Sắp xếp chỉ ghi cái đổi chỗ. Đường dẫn và phản hồi theo `api.yaml`.
- Test: `CategoryTest` 31 ca (KT-01), `CategoryUseCaseTest` 45 ca với danh mục hành động thật của `AuditConfig` (KT-03); cả bộ catalog 161 ca chạy, riêng `CatalogSchemaConstraintTest` chưa chạy được lần này vì Docker không bật (`V1` không đổi ở bước này). Đột biến 51 chỗ (khóa tên khi đổi tên, hai guard trạng thái, kiểm lá, kiểm gán trùng, gán không thay mà cộng dồn, trạng thái lúc tạo, `+ 1` phiên bản, độ dài, cấp bốn, lá, các ràng buộc theo kiểu nhập, kiểm tên trùng khi tạo và đổi tên, xếp cuối, ba kiểm khi sắp xếp, ba kiểm khi xóa, ba kiểm khi gán, kiểm vai ở mọi use case, cây công khai và quản trị, thuộc tính người bán, sáu chỗ ở kiểm toán), đỏ hết sau khi bổ sung kiểm tra loại đích của bản ghi kiểm toán cho đột biến sống sót lần đầu.
- Ghi nhớ cho bước 9: dòng "đã từng có sản phẩm" của INV-CAT-98 vào `deferred.md` (feature 07).

### Bước 7. Đọc, API, bảo mật, bộ xử lý lỗi · Agent, Người rà

Gồm: cây hai loại, thuộc tính được gán, danh sách thương hiệu có phân trang, các đường ghi; `SecurityConfig` không còn `permitAll`; bộ xử lý ánh xạ tên ràng buộc sang mã lỗi; `CONCURRENT_UPDATE`. **Đối chiếu trước:** `CategoryController`, `AttributeTemplateController`, `BrandController`, các `GetXxx`, `SecurityConfig` cũ.

Xong khi: KT-05 xanh (401, 403, 404, 400, 409 từng mã); không đường nào truy cập được khi chưa đăng nhập.

**Triển khai (`2026-11-04`):** xong KT-04 (chạy lại) và KT-05, KT-06, KT-07, KT-08 trên Postgres thật (Testcontainers, container cách ly, không đụng cơ sở dữ liệu dev). Bước 8 còn lại KT-09 (chạy thật có Kafka).
- **Ứng dụng khởi động được lần đầu với `ddl-auto=validate`:** lộ một chỗ lệch duy nhất, cột `category.level` là `SMALLINT` nhưng thực thể khai `int`; đã ép kiểu ở thực thể (đúng `data.md`).
- **Bộ xử lý lỗi lưu trữ** `PersistenceConflictExceptionHandler` (chuyển sang `presentation`): ánh xạ tên ràng buộc sang mã lỗi như `data.md` mục 8; ràng buộc lạ thì 500. **Phát hiện khi chạy thật (KT-08):** khóa ngoài `fk_category_parent` vi phạm lúc chèn con dưới cha vừa bị xóa bị báo nhầm là `CATEGORY_HAS_CHILDREN` cho người tạo con. Đã tách theo câu lệnh SQL của lỗi: vi phạm lúc chèn là `CATEGORY_NOT_FOUND` (404), lúc xóa là `CATEGORY_HAS_CHILDREN`; ghi vào `data.md` mục 8 và `tech.md`.
- **Khung test:** `CatalogPostgresTestBase`, `CatalogApiTestBase` (MockMvc có bộ lọc bảo mật, JWT giả vai `ADMIN` hoặc `USER`, xóa sạch bảng trước mỗi ca); thêm `spring-security-test` vào `pom.xml`.
- **Chia việc với `agy` (mô hình `gemini-3.8-flash-low`, mỗi lượt chỉ viết test, đặc tả nêu tệp đọc tường minh):** lượt 1 `BrandApiTest` + `AttributeTemplateApiTest` (31 ca; 141 giây, vào 55.967, ra 21.274 token), lượt 2 `CategoryApiTest` (28 ca; 154 giây, vào 108.345, ra 18.879), lượt 3 `CatalogConcurrencyTest` + `CatalogRollbackTest` (15 ca; 162 giây, vào 111.123, ra 17.681). Cả ba lượt đạt ngay lần đầu; tôi tự sửa ba chỗ: biến vòng lặp trong lambda (lỗi biên dịch), đặc tả của tôi sai ở chỗ giả lập `OutboxEventStore.store` (cần giao dịch vì đã có `@Transactional(MANDATORY)`; sửa bằng giả lập trong một `TransactionTemplate`), và siết điều kiện của ca xóa đua nhau tạo con (lộ lỗi ở trên).
- **Test:** 313 ca trong module catalog, 0 lỗi (`BrandApiTest` 13, `AttributeTemplateApiTest` 18, `CategoryApiTest` 28, `CatalogConcurrencyTest` 9, `CatalogRollbackTest` 6, `CatalogApplicationBootTest` 4, `PersistenceConflictExceptionHandlerTest` 10, `CatalogSchemaConstraintTest` 23, cộng test đơn vị các bước trước).
- **Phá thử:** bảo mật (`/api/**` thành `permitAll`, đường khác thành chỉ cần đăng nhập, mọi người thành `ADMIN`, đổi đường dẫn, bỏ 201, bỏ `@Valid`) và các ràng buộc cơ sở dữ liệu (bỏ từng chỉ mục duy nhất tên danh mục cấp một và dưới cha, tên mẫu, tên thương hiệu; khóa ngoài cha thành `CASCADE`) đều làm test đỏ. Bốn đột biến sống sót ở lượt chạy riêng của test đua nhau và rollback, đều đã giải thích: bỏ duy nhất giá trị tùy chọn và bỏ `@Transactional` ở `TemplateUpdater` bị bắt bởi `CatalogSchemaConstraintTest` và `AttributeTemplateApiTest`; nuốt lỗi ở hai bộ xử lý kiểm toán và hộp thư là đột biến tương đương, vì lỗi đi qua proxy `@Transactional(MANDATORY)` của `OutboxEventStore` đã đánh dấu giao dịch chỉ được hoàn tác (kết cục vẫn là `UnexpectedRollbackException` và hoàn tác hết).

### Bước 8. Chạy thật · Người

Ngăn xếp `probe-catalog` (Postgres, Kafka, Connect, ứng dụng) như feature 04; nhóm A (luồng thành công và lỗi từng đường), nhóm B (đua nhau trên Postgres thật, 20 luồng). KT-09: một thao tác ra thông điệp kiểm toán và `EVT-CAT-03` đúng khóa, đúng nội dung. Không chạm hạ tầng dev; không xóa dữ liệu dev trừ khi người yêu cầu đúng đối tượng.

**Triển khai (`2026-11-04`):** xong nhóm A, nhóm B và KT-09; ngăn xếp đã gỡ. Không chạm hạ tầng dev (không động tới `infra-*`, không động tới cơ sở dữ liệu dev); **việc xóa và tạo lại dữ liệu dev catalog chưa làm**, vì người dùng chưa chỉ đúng đối tượng.
- Ngăn xếp cách ly `probe-catalog` (tên dự án, mạng, cổng riêng, chỉ nghe `127.0.0.1`): Postgres `wal_level=logical`, Kafka, Connect (hình ảnh `infra-kafka-connect` đã có sẵn), `catalog-service` chạy từ tệp jar với `-Xmx384m`, máy chủ khóa công khai thử cấp token RS256 ký thật. Không dựng `audit-service`, Redis, MinIO. Dựng tuần tự (Postgres và ứng dụng trước, rồi Kafka, rồi Connect) vì máy thiếu bộ nhớ; không tiến trình nào bị dừng giữa chừng. Connector đăng ký bằng `infra/debezium/outbox-connector.sh register catalog`.
- **Nhóm A (66 mục đạt)**, HTTP thật: token không có, ký bằng khóa lạ, hết hạn, rác đều 401; vai thường ghi 403; đường ngoài `/api` 401 rồi 403 kể cả với `ADMIN`; thương hiệu, mẫu, tùy chọn, danh mục (ba cấp, đổi tên, sắp xếp, ngừng, bật lại, xóa, thay danh sách gán, ràng buộc) mỗi đường thành công và từng mã lỗi; tiếng Việt có dấu đi và về đúng; sau đó hàng hộp thư trong cơ sở dữ liệu khớp (mỗi lệnh thành công đúng một hàng kiểm toán, danh mục và mẫu thêm một hàng `catalog.*`, thương hiệu không có hàng riêng).
- **Nhóm B · đua nhau (18 mục đạt)**, 20 yêu cầu HTTP đồng thời: cùng tên danh mục cấp một và dưới cha, tên mẫu, tên thương hiệu đều đúng một 201 và 19 lần 409 đúng mã; cùng giá trị tùy chọn đúng một 201; đổi tên cùng danh mục và thương hiệu chỉ có 200 hoặc `CONCURRENT_UPDATE` và `version` cuối bằng số lần thành công; thay danh sách gán đua nhau không bao giờ trộn hai danh sách; 15 vòng xóa cha đua nhau tạo con, không mồ côi, không 500, đúng `CATEGORY_NOT_FOUND` hoặc `CATEGORY_HAS_CHILDREN`.
- **KT-09 · Kafka (15 mục đạt):** danh mục ra đúng hai thông điệp trên `catalog.category.changed` (`version` 0 `CREATED`, rồi 1 `RENAMED`), khóa là mã danh mục, nội dung chỉ `categoryId`, `version`, `change`, `occurredAt` (không lộ tên); mẫu một thông điệp trên `catalog.attribute-template.changed`; thương hiệu không có thông điệp `catalog.*`; kiểm toán trên `audit.action.recorded` khóa là mã đối tượng, đúng người thực hiện, đúng hành động; bốn thao tác lỗi không ra thông điệp nào; số thông điệp khớp số hàng hộp thư.
- **Phát hiện khi chạy thật:** (1) `PUT .../attributes` bỏ qua `required` hoặc `displayOrder` trả 400 `MALFORMED_REQUEST` dù `api.yaml` ghi mặc định `false` và `0` (request khai kiểu nguyên thủy); test MockMvc không thấy vì luôn gửi đủ trường. Đã đổi sang kiểu bọc kèm mặc định, thêm test hồi quy (`CategoryApiTest`, phá thử làm test đỏ). (2) Cổng quản lý của ứng dụng là cổng chính cộng 10000, nên cổng chính phải nhỏ hơn 55535; một số dải cổng Windows bị Hyper-V giữ chỗ (55139 đến 55538 và 55619 đến 55818 trên máy này) nên chọn cổng ngoài các dải đó.
- Lỗi của kịch bản (không phải của hệ thống): Kafka quảng bá `localhost:59092` nên đọc từ trong container phải dùng cổng nội bộ `kafka:29092`; lần chạy đầu đọc rỗng làm các ca "không rò rỉ" đạt rỗng, đã thêm điều kiện "đã đọc được thông điệp" cho chúng.
- Chưa kiểm (ghi nhận, không phải lỗi): Connect dừng rồi chạy lại, độ trễ từ ghi đến Kafka, nhiều quyết định song song qua Kafka, `audit-service` nhận và lưu (feature 13 kiểm đầu-cuối).

### Bước 9. Đóng · Người, Agent

Điền cột "Triển khai"; `deferred.md` (nửa "đã từng có sản phẩm" của xóa danh mục, chờ feature 07; bộ nhớ đệm đọc cho C1b, C5, mã cũ lấy lại từ git, thư mục `crosscutting/cache` còn dạng comment); `service.md` chuyển "Dự kiến" sang "Đã làm" cho phần của 05; `5. event-catalog.md` thay mục `catalog` bằng hợp đồng `EVT-CAT-03`; `00-features.md` đóng feature 05 kèm bài học; liệt kê chỗ lệch đã biết ở trạng thái bước 9 (như feature 04). Ghi mã commit để feature 07, 08 lấy lại mã sản phẩm và danh sách tệp đã comment.

**Triển khai (`2026-11-04`):** xong. Cột "Triển khai" điền đủ mười ba dòng; `deferred.md` mới (ba mục); `service.md`, `data.md`, `api.yaml` chuyển phần của 05 sang **Đã làm**; `5. event-catalog.md` thay mục `catalog` bằng hợp đồng `EVT-CAT-03` (hai chủ đề); `00-features.md` đóng feature 05 kèm bài học.
- **Mã sản phẩm để feature 07, 08 lấy lại:** người dùng tự commit nên mã commit **chưa có**; điền vào đây ngay sau khi commit: `<mã commit>`. Cách hoàn lại: gỡ đúng tiền tố `//~ ` ở đầu mỗi dòng của tệp có dòng đầu bắt đầu bằng `//~ ` (danh sách thư mục ở "Triển khai" của bước 3). Chỗ nối khi hoàn lại: `CategoryRepository.hasProductReference` và lần kiểm trong `DeleteCategory` (đã bỏ hẳn, thêm lại ở feature 07).
- **Chỗ lệch đã biết ở trạng thái đóng:** (1) bước 1 (đối chiếu E1 đến E19 với KT) còn chờ người rà, nhưng mọi đường đã có ít nhất một KT chạy xanh. (2) Dữ liệu dev của catalog: **đã dựng lại `2026-11-04`** (feature 06, sau khi người dùng cho phép đúng `catalog_db` trong `infra-postgres-catalog-1`); còn cần đăng ký connector `catalog` ở Connect dev khi muốn chạy hộp thư trên hạ tầng dev. (3) `api.yaml` và `service.md` khớp mã ở thời điểm đóng; `service.md` mục 5 còn nhãn Dự kiến cho phần bên nhận `EVT-CAT-03` (C1b) và các event của sản phẩm. (4) Chưa kiểm: Connect dừng rồi chạy lại, độ trễ ghi đến Kafka, `audit-service` nhận và lưu (ghi ở `deferred.md`).
- **Số liệu cuối:** 314 test của catalog xanh (không chạy bốn lớp test cũ của `oauth2-service` nối cơ sở dữ liệu dev, không sửa thư viện dùng chung); phá thử thương hiệu 21/21, danh mục 51/51; chạy thật nhóm A 66/66, nhóm B 18/18, KT-09 15/15.

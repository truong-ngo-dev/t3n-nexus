# Feature 03: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7), [ADR-0008](../../../global/2.architecture/adr/0008-event-outbox-and-consumption.md) và [ADR-0010](../../../global/2.architecture/adr/0010-dlq-per-consumer-service.md) (hộp thư, nhận event, hàng chờ lỗi), [ADR-0003](../../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) (hợp đồng danh tính). Quyết định mới của feature này: ADR-0011 (khóa lạc quan và tính duy nhất ở lưu trữ), viết ở bước 3 của `plan.md`, Accepted sau khi chạy thật. Nhãn: **Dự kiến** (chưa có mã), **Đã làm** (có mã và kiểm chứng).

## Câu hỏi cần chốt trước khi viết `plan.md`

| # | Câu hỏi | Đề xuất | Hệ quả nếu khác |
|---|---|---|---|
| Q1 | Chủ hồ sơ lấy từ đâu khi chưa có mô-đun phân quyền (feature 02 đã bỏ kiểm token) | **Đã chốt `2026-10-26`:** giải mã và kiểm chữ ký token ngay ở feature này (`spring-boot-starter-oauth2-resource-server`, `jwk-set-uri` như `catalog-service`), mã tài khoản lấy từ `sub`. **Vai là quy tắc nghiệp vụ, không phải phân quyền:** lớp trình bày đọc `role` từ token thành một giá trị `SellerActor` truyền vào use case, và **miền tự kiểm** "chỉ `USER` nộp và sửa hồ sơ" (T8); không `@PreAuthorize`. Mô-đun phân quyền (ABAC) vẫn hoãn | Không kiểm token thì phải nhận mã tài khoản từ tham số của người gọi: ai cũng nộp hồ sơ thay người khác, và quy tắc "chỉ chủ hồ sơ sửa" (INV-SEL-05) không có chỗ đứng. Không nên |
| Q2 | Người dùng sửa hồ sơ của chính mình có phải gửi kèm phiên bản đã xem không | **Đã chốt `2026-10-26`: không.** Phiên bản là cơ chế chạy ngầm (`@Version`); chỉ có nghĩa nghiệp vụ ở quyết định của quản trị (feature 04, INV-SEL-06). Lệnh sửa là `PATCH` một phần nên hai thiết bị sửa hai trường khác nhau đều được giữ; hai sửa chạm nhau đúng lúc thì một bên nhận 409 `CONCURRENT_UPDATE` (thử lại) | Kèm `expectedVersion`: chặn được ghi đè trên cùng một trường nhưng giao diện phải giữ và gửi phiên bản, trong khi người bán sửa dữ liệu tự khai của chính mình không có nhu cầu nghiệp vụ đó |
| Q3 | `Q-SEL-01`: cá nhân có bắt buộc khai mã số thuế không | **Đã chốt `2026-10-26`: tùy chọn** như `analysis.md`; có khai thì kiểm định dạng, nhưng khóa định danh của cá nhân vẫn là số căn cước | Bắt buộc: đổi bảng 4.2 của analysis, khóa định danh cá nhân đổi sang mã số thuế, cần kiểm chứng quy định thuế trước |
| Q4 | Hồ sơ của tôi khi chưa nộp trả gì | **Đã chốt `2026-10-26`: 404** `SELLER_PROFILE_NOT_FOUND`; giao diện hiểu là "chưa là người bán" (nhất quán với `AUDIT_ENTRY_NOT_FOUND`; 4xx chỉ ghi INFO) | Trả 200 với `data` rỗng: giao diện không phải bắt lỗi, nhưng phân biệt "chưa có" với "lỗi" khó hơn |

**Trạng thái:** Q1 đến Q4 đã chốt `2026-10-26` (Q2 đổi so với đề xuất ban đầu: không bắt gửi phiên bản).

## T1. Dịch vụ `seller-service` · Dự kiến

- **Quyết định:** dịch vụ Spring Boot mới `services/seller-service`, gói gốc `vn.t3nexus.seller`, bốn tầng theo `ddd-structure.md` (miền, ứng dụng, hạ tầng, trình bày), cổng `8011`. Servlet, JPA và Flyway trên Postgres 16 riêng của dịch vụ, hộp thư event bằng `outbox-starter`, xác thực token bằng resource server (T8). Chưa nhận event nào, chưa gọi BC khác (`service.md` mục 5).
- **Gói:** `domain/sellerprofile` (aggregate, giá trị, cổng kho, dịch vụ miền kiểm trùng), `application/sellerprofile` (use case), `infrastructure/persistence` (entity JPA, mapper, kho), `infrastructure/crosscutting/config`, `presentation/sellerprofile` (bộ điều khiển, DTO, bộ xử lý lỗi).
- **Kết nối cơ sở dữ liệu:** một tài khoản đủ quyền trên cơ sở dữ liệu riêng (không cần tách hai vai như Nhật ký: bảng này được sửa). Mật khẩu đọc từ biến môi trường.
- **Loại:** nhét hồ sơ vào Xác thực (`analysis.md` mục 2: tài khoản đăng nhập phình ra dữ liệu nhạy cảm).

## T2. Miền: aggregate và giá trị · Dự kiến

Một aggregate `SellerProfile` (`service.md` AGG-SEL-01). Giá trị bất biến tự kiểm khi dựng, ném lỗi miền nêu **tên trường**, không nêu giá trị:

| Giá trị | Chuẩn hóa | Kiểm |
|---|---|---|
| `ShopName` | Hiển thị: bỏ khoảng trắng đầu cuối, gộp khoảng trắng liên tiếp. Khóa so sánh: từ bản hiển thị, `NFD` bỏ dấu, `đ`/`Đ` thành `d`, chữ thường | 3 đến 60 ký tự (bản hiển thị) |
| `IdentityNumber` (số căn cước) | Bỏ khoảng trắng | Đúng 12 chữ số |
| `TaxCode` (mã số thuế) | Bỏ khoảng trắng; dạng `0123456789-001` thành 13 chữ số liền | 10 hoặc 13 chữ số |
| `Phone` | Bỏ khoảng trắng, dấu chấm, gạch; `+84` hoặc `84` đầu thành `0` | `0` rồi đầu số di động Việt Nam (`03`, `05`, `07`, `08`, `09`) và đủ 10 chữ số |
| `Address` | Bỏ khoảng trắng thừa | 10 đến 300 ký tự |
| `LegalInfo` | Gộp các trường trên theo loại hình (`analysis.md` bảng 5.1) | Đủ trường bắt buộc theo loại hình (INV-SEL-08); tính `identityKey` (cá nhân: số căn cước; hộ, doanh nghiệp: mã số thuế) |
| `Contact` | Tên người liên hệ, `Phone`, địa chỉ liên hệ tùy chọn | Tên 2 đến 100 ký tự |

- **Người thực hiện (`SellerActor`):** giá trị gồm mã tài khoản và vai (`USER`, `ADMIN`), dựng ở lớp trình bày từ token. Mọi hành vi của chủ hồ sơ nhận `SellerActor` và **từ chối khi vai không phải `USER`** (`SellerRoleNotAllowedException`, 403 `SELLER_ROLE_NOT_ALLOWED`; INV-SEL-05): đây là quy tắc nghiệp vụ "tài khoản nội bộ không bán" (`analysis.md` mục 3), không phải kiểm quyền truy cập. Miền không biết JWT.
- **Hành vi trong feature này:** `submit`, `updateShopInfo`, `updateLegalInfo`, `resubmit` (và `restore` dựng từ lưu trữ). Mỗi hành vi kiểm ma trận hành động × trạng thái của `analysis.md` (INV-SEL-02, 10) và tăng `version` khi có đổi. `approve`, `reject` là feature 04.
- **Giờ:** đồng hồ là đầu vào của hàm hành vi (`service.md` mục 2), không phải cổng; test dùng giờ cố định.
- **Dịch vụ miền `SellerProfileUniqueness`:** nhận kho, trả "trùng chủ", "trùng tên", "trùng khóa định danh" **trước** khi lưu (báo sớm để thông báo rõ); không thay ràng buộc ở lưu trữ (T4).
- **Tính chất an toàn:** `toString` của mọi giá trị định danh (`IdentityNumber`, `TaxCode`, `Address` kinh doanh, `LegalInfo`) che hoặc không in giá trị, để lỡ log nguyên đối tượng cũng không rò (INV-SEL-11).
- **Loại:** để thư viện bean validation kiểm trong aggregate (tên trường và quy tắc theo loại hình khó diễn đạt bằng chú thích); lưu khóa so sánh tên tính ở cơ sở dữ liệu (`unaccent` là extension, phụ thuộc môi trường).

## T3. Lưu trữ · Dự kiến

Bảng `seller_profiles` (migration V1) và `outbox_events` (mẫu của `outbox-starter`, V2):

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `uuid` | Khóa chính; UUID v7 |
| `owner_account_id` | `uuid` | Không rỗng, **duy nhất** (INV-SEL-01) |
| `status` | `varchar(20)` | `CHECK` thuộc `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `seller_type` | `varchar(20)` | `CHECK` thuộc ba loại hình |
| `legal_name`, `representative_name` | `varchar` | `representative_name` bắt buộc khi `BUSINESS` (`CHECK`) |
| `identity_number` | `varchar(12)` | Không rỗng |
| `tax_code` | `varchar(13)` | Bắt buộc khi `HOUSEHOLD`, `BUSINESS` (`CHECK`) |
| `business_address` | `varchar(300)` | Không rỗng |
| `identity_key` | `varchar(13)` | Không rỗng; **chỉ mục duy nhất một phần** `WHERE status <> 'REJECTED'` (INV-SEL-09) |
| `shop_name` | `varchar(60)` | Không rỗng |
| `shop_name_key` | `varchar(60)` | Không rỗng, **duy nhất** (INV-SEL-04) |
| `description` | `varchar(500)` | Tùy chọn |
| `contact_name`, `contact_phone`, `contact_address` | `varchar` | `contact_address` tùy chọn |
| `rejection_reason` | `varchar(500)` | Bắt buộc khi `REJECTED` (`CHECK`); chưa ghi ở feature này |
| `submitted_at` | `timestamptz` | Không rỗng |
| `reviewed_at`, `reviewed_by` | `timestamptz`, `uuid` | Bắt buộc khi `APPROVED` hoặc `REJECTED` (`CHECK`); chưa ghi ở feature này |
| `version` | `bigint` | Không rỗng; khóa lạc quan (T4) |
| `created_at`, `updated_at` | `timestamptz` | Không rỗng |

- **Kho:** `SellerProfileJpaEntity` + `SellerProfileMapper` + `SellerProfilePersistenceAdapter` (cài cổng kho của miền); entity không lộ ra ngoài hạ tầng. Cổng ghi của miền `SellerProfileRepository`: `save`, `findByOwner` (nạp aggregate để sửa), `existsByShopNameKey`, `existsEffectiveIdentityKey(trừ chủ)`, `existsByOwner`. **Tìm kiếm tách riêng** (bài học feature 02): đọc theo khóa (`findByOwner`) ở cổng kho của miền; truy vấn có bộ lọc và phân trang (hồ sơ chờ duyệt, feature 04) đi qua cổng `SellerProfileQuery` ở tầng ứng dụng, bộ lọc và con trỏ không nằm ở miền.
- **Chỉ mục:** ba ràng buộc duy nhất ở trên đã đủ tra theo chủ, tên, khóa định danh; không thêm chỉ mục khác ở C1a (hồ sơ chờ duyệt cần chỉ mục theo `status, submitted_at`: feature 04).
- **Dữ liệu định danh:** lưu nguyên văn ở C1a; mã hóa cột là điểm nối chưa làm (`analysis.md` mục 13). Che trong log, event, danh sách (T7).
- **Loại:** bảng riêng cho thông tin pháp lý (hai bảng một aggregate buộc hai lần ghi và khóa chéo mà không thêm quy tắc nào).

## T4. Khóa lạc quan và tính duy nhất khi đua nhau · Dự kiến (ADR-0011)

- **Khóa lạc quan chạy ngầm (Q2):** cột `version` ánh xạ `@Version`; mọi thay đổi nội dung hồ sơ tăng một. **Người bán không gửi và không thấy phiên bản**: lệnh sửa không có `expectedVersion`. Khi hai giao dịch cùng đọc một bản rồi cùng ghi, cập nhật của giao dịch sau thất bại (`ObjectOptimisticLockingFailureException`) và bộ xử lý lỗi đổi thành 409 `CONCURRENT_UPDATE` ("có thay đổi đồng thời, thử lại"). Hai thiết bị sửa hai trường khác nhau tuần tự đều được giữ vì `PATCH` chỉ đổi trường được gửi.
- **Phiên bản có nghĩa nghiệp vụ ở feature 04:** chi tiết hồ sơ cho quản trị trả `version`, lệnh duyệt và từ chối gửi lại đúng số đã xem, lệch thì 409 `STALE_VERSION` (INV-SEL-06). Cùng một cột; mọi thay đổi nội dung đều tăng nó. Điểm nối: nếu sau này có thay đổi kỹ thuật làm tăng phiên bản mà nội dung không đổi (ví dụ migration dữ liệu), tách thêm cột `revision` nghiệp vụ.
- **Tính duy nhất:** ba ràng buộc ở T3 là chốt cuối. Use case kiểm sớm qua kho để trả thông báo rõ, nhưng hai yêu cầu đua nhau vẫn có thể cùng qua phép kiểm sớm; khi đó người sau bị cơ sở dữ liệu từ chối.
- **Xử lý vi phạm:** dùng `saveAndFlush` để vi phạm nổ ngay trong giao dịch của use case; ngoại lệ lan khỏi use case, **cả giao dịch rollback** (kể cả hàng hộp thư), rồi bộ xử lý lỗi ở lớp trình bày đổi theo **tên ràng buộc** (`uq_seller_profiles_owner`, `uq_seller_profiles_shop_name_key`, `uq_seller_profiles_identity_key_effective`) thành `SELLER_PROFILE_EXISTS`, `SHOP_NAME_TAKEN`, `IDENTITY_ALREADY_REGISTERED`. Ràng buộc lạ thì 500 (lỗi lập trình, không đoán).
- **Loại:** bắt `DataIntegrityViolationException` trong use case rồi thử tiếp (làm hỏng giao dịch Postgres, phải rollback cả use case: đã gặp ở feature 02); khóa bi quan `SELECT ... FOR UPDATE` trên một hàng không tồn tại (không khóa được "chưa có"); khóa tư vấn theo tên (thêm cơ chế khi ràng buộc đã đủ).

## T5. Hộp thư event: `SellerProfileSubmitted` · Dự kiến

- **Sự kiện:** `SellerProfileSubmitted` cài `DomainEvent`; `aggregateType` = `SellerProfile`, `aggregateId` = **mã tài khoản chủ** (khóa phân vùng theo `service.md` mục 5, để thứ tự đúng theo từng người bán), chủ đề `seller.profile.submitted`. Nội dung (`payload`): `accountId`, `profileId`, `submittedAt`. **Không** mang loại hình, thông tin pháp lý, tên (INV-SEL-11); `SensitiveFieldPolicy.strict()` của khung từ chối thêm nếu lỡ.
- **Khi nào ghi:** use case `SubmitSellerProfile` và `ResubmitSellerProfile`, sau khi `saveAndFlush` thành công, trong cùng giao dịch (`OutboxEventStore.store` đòi giao dịch, ADR-0008). Sửa gian hàng, sửa pháp lý, đọc: không phát event.
- **Hạ tầng:** bảng `outbox_events` theo mẫu `db/outbox-schema.sql`; connector `outbox-connector.sh register seller` (feature 01), đăng ký **trước** khi ghi hàng đầu tiên (T9).
- **Bên nhận:** chưa ai ở C1a. Bản ghi `SELLER_PROFILE_SUBMITTED` cho Nhật ký **không** gửi: `analysis.md` mục 6 CAP-SEL-01 bước 5 chỉ ghi quyết định của quản trị.
- **Loại:** phát sự kiện khi sửa gian hàng (chưa ai cần, thêm chủ đề vô ích); phát sau commit bằng gọi Kafka trực tiếp (ADR-0008).

## T6. API · Dự kiến

| Điểm cuối | Use case | Thành công | Ghi chú |
|---|---|---|---|
| `POST /api/seller/profile` | Nộp (`CMD-SEL-01`) | 201 + hồ sơ | Không có `Idempotency-Key`; nộp lặp bị `SELLER_PROFILE_EXISTS` (INV-SEL-01) |
| `GET /api/seller/profile` | Hồ sơ của tôi (`RM-SEL-01`) | 200 + hồ sơ | 404 khi chưa có (Q4) |
| `PATCH /api/seller/profile/shop-info` | Sửa thông tin gian hàng (`CMD-SEL-02`) | 200 + hồ sơ | Một phần: trường vắng thì giữ nguyên, `null` tường minh xóa trường tùy chọn (`description`, địa chỉ liên hệ) |
| `PATCH /api/seller/profile/legal-info` | Sửa pháp lý, loại hình (`CMD-SEL-03`) | 200 + hồ sơ | Một phần: gộp vào giá trị hiện tại rồi kiểm lại toàn bộ `LegalInfo` theo loại hình (đổi loại hình thì phải đủ trường của loại mới) |
| `POST /api/seller/profile/resubmit` | Sửa và nộp lại (`CMD-SEL-04`) | 200 + hồ sơ | **Toàn bộ** trường (sửa mọi thứ rồi nộp lại), không kèm phiên bản |

- **Chủ hồ sơ** luôn là mã tài khoản trong token; không có tham số mã hồ sơ hay mã tài khoản trong đường dẫn và nội dung (E10).
- **Hồ sơ trả về:** `id`, `status`, `sellerType`, `legalInfo` (đủ trường: người này là chủ), `shop` (`shopName`, `description`, `contact`), `rejectionReason` (nếu có), `submittedAt`. **Không trả `version`** cho chủ hồ sơ (Q2); chi tiết cho quản trị ở feature 04 mới trả. Bọc `ApiResponse` chung của `common-web`.
- **Mã lỗi** (định dạng lỗi chung, trường `code` là hằng của `SellerErrorCode`): 400 `LEGAL_INFO_REQUIRED` (kèm tên trường thiếu), `INVALID_IDENTITY_NUMBER`, `INVALID_TAX_CODE`, `SELLER_FIELD_INVALID` (tên trường: tên quá ngắn, số điện thoại, địa chỉ, mô tả); 404 `SELLER_PROFILE_NOT_FOUND`; 409 `SELLER_PROFILE_EXISTS`, `SHOP_NAME_TAKEN`, `IDENTITY_ALREADY_REGISTERED`, `LEGAL_INFO_LOCKED`, `INVALID_STATE`, `CONCURRENT_UPDATE` (`STALE_VERSION` thuộc feature 04); 401, 403 `SELLER_ROLE_NOT_ALLOWED` do miền (T2); 401 do lớp bảo mật.
- **`api.yaml`, `data.md`:** viết ở bước 3 của `plan.md`, trước mã (`work-process.md` mục 3.3).

## T7. Dữ liệu định danh không rò · Dự kiến

- **Log:** use case chỉ log mã hồ sơ, mã tài khoản, loại thao tác, mã lỗi; không log đối tượng giá trị. Nội dung yêu cầu không được log nguyên văn: các tên trường `identityNumber`, `taxCode`, `businessAddress`, `legalName` thêm vào danh sách che của `BodySanitizer` của `observability-starter` (nếu thiếu), có test.
- **Thông báo lỗi:** `LEGAL_INFO_REQUIRED` và các lỗi định dạng nêu tên trường; **không nêu giá trị**. `MethodArgumentNotValidException` mặc định của `common-web` chỉ nêu tên trường và thông điệp mặc định, nhưng thông điệp của ràng buộc có thể chèn giá trị: dùng thông điệp cố định.
- **Event, `toString`:** T5 và T2.
- **Test:** chụp log toàn bộ ca nộp và ca lỗi, khẳng định không có số căn cước, mã số thuế, địa chỉ mẫu (KT-12).

## T8. Danh tính và vai · Dự kiến (Q1 đã chốt)

- **Xác thực (ai gọi):** `spring-boot-starter-oauth2-resource-server`, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` như `catalog-service` (trỏ thẳng tới Xác thực, không qua cổng). Bộ lọc bảo mật chỉ đòi `authenticated()` cho mọi đường dưới `/api/seller/**`, **không kiểm vai**. Lớp trình bày đọc `sub` và `role` thành `SellerActor` (mã tài khoản, vai) rồi truyền vào use case; **use case và miền không biết token**.
- **Vai (được làm gì) là nghiệp vụ:** miền tự kiểm "chỉ `USER` nộp và sửa" (T2). Không `@PreAuthorize`, không bộ chuyển claim thành quyền. Lý do: đây là quy tắc của hồ sơ người bán, không phụ thuộc cách phân quyền chung nào sau này; test đơn vị của miền chứng minh được mà không cần Spring Security.
- **Điểm nối:** ABAC của mô-đun phân quyền sau này thêm một lớp kiểm quyền truy cập ở trình bày; không thay quy tắc nghiệp vụ này. Feature 04 dùng cùng cách cho "chỉ `ADMIN` duyệt".
- **Kiểm thử:** test tích hợp dùng `SecurityMockMvcRequestPostProcessors.jwt()` (không cần Xác thực chạy); xác minh chữ ký thật thuộc phép chạy thật (T9, bước 8).
- **Loại:** nhận mã tài khoản trong nội dung hoặc tiêu đề (Q1); `@PreAuthorize` cho vai (đây là nghiệp vụ, không phải phân quyền: người chốt `2026-10-26`); `security-starter` dùng chung (chưa có nhu cầu của BC thứ hai ở feature này).

## T9. Hạ tầng dev · Dự kiến

- **Cơ sở dữ liệu:** thêm `postgres-seller` vào `infra/docker-compose.yml` (Postgres 16, cổng `5441`, `command: postgres -c wal_level=logical`, cần để Debezium đọc). **Ghi hạ tầng dev: báo trước.**
- **Connector:** `DB_HOST=postgres-seller DB_NAME=seller_db ... infra/debezium/outbox-connector.sh register seller` **trước** khi ghi hàng đầu tiên (nếu không, hàng nằm ngoài khe sao chép: ADR-0008 mục 7). Ghi vào Kafka Connect dev: báo trước.
- **Chủ đề Kafka:** `seller.profile.submitted` tự tạo ở dev; không tạo tay.
- **Cổng truy cập, route, giao diện:** ngoài phạm vi (feature 10, 11); kiểm thử gọi thẳng dịch vụ, tự dựng token thử.
- **Chạy thật (phần D):** stack Docker cô lập tiền tố `probe-` như feature 01, 02 (Postgres có `wal_level=logical`, Kafka, Connect, Xác thực hoặc nguồn khóa công khai thử), không chạm Kafka và Postgres dev.

## T10. Quan sát · Dự kiến

| Mã log | Mức | Khi nào | Nội dung |
|---|---|---|---|
| `SELLER_PROFILE_SUBMITTED` | INFO | Nộp hoặc nộp lại thành công | mã hồ sơ, mã tài khoản, `resubmit` đúng/sai |
| `SELLER_PROFILE_UPDATED` | INFO | Sửa gian hàng hoặc pháp lý thành công | mã hồ sơ, phần được sửa (`shop`, `legal`) |
| `SELLER_CONFLICT` | INFO | Từ chối vì trùng hoặc xung đột đồng thời | mã lỗi (không giá trị, không nói bên trùng là ai) |

Bộ đếm (Micrometer): `seller.profile.submitted{kind=first|resubmit}`, `seller.profile.conflict{code}`. Mã truy vết lấy từ ngữ cảnh log của `observability-starter`.

## T11. Kiểm chứng · Dự kiến

Test viết từ bảng này và `flow.md` mục 4 trước khi viết mã (đỏ trước, phá thử sau).

| # | Kiểm | Cấp | Đường |
|---|---|---|---|
| KT-01 | Giá trị: chuẩn hóa và kiểm định dạng (tên gian hàng bỏ dấu và `đ`, số điện thoại `+84`, mã số thuế có gạch, độ dài biên); lỗi nêu tên trường, không nêu giá trị | Đơn vị | E5, E6, E7, E18 |
| KT-02 | `LegalInfo`: đủ trường theo ba loại hình, khóa định danh đúng theo loại hình; cá nhân khai thêm mã số thuế vẫn lấy số căn cước làm khóa | Đơn vị | E5, Q3 |
| KT-03 | Aggregate: nộp tạo `PENDING_REVIEW`, phiên bản 1; ma trận hành động × trạng thái cho `updateShopInfo`, `updateLegalInfo`, `resubmit` ở cả ba trạng thái (dựng `APPROVED` và `REJECTED` bằng `restore`); sửa không đổi gì không tăng phiên bản; **vai không phải `USER` bị từ chối ở cả bốn hành vi** (`SellerRoleNotAllowedException`), kiểm trước mọi kiểm khác | Đơn vị | E8, E11, E12, E14 |
| KT-04 | Use case nộp: trùng chủ, trùng tên (khác dấu, hoa, khoảng trắng), trùng khóa định danh (không nêu bên trùng); khóa định danh của hồ sơ `REJECTED` không chặn | Đơn vị (kho giả) | E1 đến E4 |
| KT-05 | Use case sửa và nộp lại: ngữ nghĩa `PATCH` một phần (vắng thì giữ, `null` xóa trường tùy chọn, hai lần sửa hai trường khác nhau đều còn), kiểm lại trùng khóa lúc nộp lại, xóa lý do, ghi hộp thư | Đơn vị (kho giả) | E13, E15 |
| KT-06 | Lưu trữ (Postgres thật, Testcontainers): migration chạy, `CHECK` chặn trạng thái và loại hình lạ, thiếu người đại diện của doanh nghiệp; ba ràng buộc duy nhất có tên đúng; **chỉ mục một phần**: hai hồ sơ cùng khóa định danh mà một cái `REJECTED` thì được, cả hai hiệu lực thì bị chặn, và hồ sơ `REJECTED` chuyển lại `PENDING_REVIEW` khi khóa đã bị người khác dùng thì bị chặn | Tích hợp | E4, E15, T3 |
| KT-07 | **Đua nhau**: 20 luồng cùng nộp (cùng chủ; khác chủ cùng tên; khác chủ cùng khóa định danh) cho đúng một hồ sơ, lỗi còn lại đúng mã; không hàng hộp thư của hồ sơ thua | Tích hợp | E1 đến E3, E17 |
| KT-08 | **Khóa lạc quan chạy ngầm**: hai thao tác sửa cùng đọc một bản và cùng ghi đồng thời, đúng một thành công, một `CONCURRENT_UPDATE`, dữ liệu cuối đúng của người thắng; hai sửa hai trường khác nhau tuần tự đều giữ | Tích hợp | E13 |
| KT-09 | Hộp thư: nộp và nộp lại ghi đúng một hàng khớp khung (chủ đề, khóa là mã tài khoản, payload ba trường, không dữ liệu định danh); sửa gian hàng, sửa pháp lý, đọc không ghi hàng; rollback không để hàng | Tích hợp | E16, E17, T5 |
| KT-10 | API: từng điểm cuối thành công và mã lỗi (400, 404, 409 theo `code`); chủ hồ sơ lấy từ `sub`; nội dung không nhận mã tài khoản; **cô lập giữa hai tài khoản** (A nộp, B gọi "hồ sơ của tôi" và sửa thì 404, không thấy và không đổi hồ sơ của A); `ADMIN` gọi "hồ sơ của tôi" thì 404 (không có hồ sơ, đọc không kiểm vai) | Tích hợp (MockMvc + `jwt()`) | E1 đến E15 |
| KT-11 | Danh tính và vai: không token 401; đường lạ mặc định từ chối; `ADMIN` nộp và sửa bị miền từ chối (`SELLER_ROLE_NOT_ALLOWED`, 403), không tạo hồ sơ, không hàng hộp thư; kiểm cả ở test đơn vị của miền (không cần Spring Security) | Đơn vị + tích hợp | E8, E9 |
| KT-12 | Rò dữ liệu: chụp log và thông báo lỗi của mọi ca, không có số căn cước, mã số thuế, địa chỉ mẫu | Tích hợp | E18, T7 |
| KT-13 | Chạy thật: nộp qua HTTP với token ký thật → hàng hộp thư → connector → `seller.profile.submitted`; token sai chữ ký và token hết hạn bị 401 (chữ ký thật chỉ kiểm được ở đây, MockMvc `jwt()` bỏ qua); đua nhau trên cơ sở dữ liệu thật; khởi động lại giữa chừng không mất event | Chạy thật (stack cô lập) | S4, R12, E9, E16 |

**Phá thử (sau khi xanh):** bỏ một ràng buộc duy nhất, bỏ `@Version`, cho phép nộp ngoài giao dịch, bỏ bước kiểm lại khóa lúc nộp lại, in giá trị vào thông báo lỗi: mỗi lần phải có ít nhất một test đỏ.

**Kết quả KT-13 · Đã làm một phần (`2026-10-27`).** Chạy trên stack cô lập `probe-` (Postgres `wal_level=logical`, Kafka, Connect, nguồn khóa công khai thử, token RS256 ký thật); hạ tầng dev không bị đụng. Đạt:

- Flyway tạo `seller_profiles` và `outbox_events`; connector `seller-outbox-connector` ở trạng thái RUNNING.
- Nộp qua HTTP trả 201; đúng một hàng hộp thư; đúng một thông điệp trên `seller.profile.submitted`, khóa là mã tài khoản, payload chỉ có `accountId`, `profileId`, `submittedAt`.
- Token sai chữ ký, hết hạn, sửa payload sau khi ký, thiếu và rác đều 401; `ADMIN` nộp 403, không tạo hồ sơ.
- Đua nhau, 20 luồng đồng thời, mỗi ca đúng một hồ sơ và một hàng hộp thư: cùng chủ cho 1×201 và 19×409 `SELLER_PROFILE_EXISTS`; khác chủ cùng tên gian hàng cho 19 lần `SHOP_NAME_TAKEN`; khác chủ cùng số căn cước cho 1×201 và 19×409 `IDENTITY_ALREADY_REGISTERED`.
- Log của dịch vụ không chứa số căn cước, tên, địa chỉ hay số điện thoại mẫu.

Chưa kiểm trên stack thật: dừng rồi chạy lại Connect giữa chừng (event không mất, không trùng) và số đo độ trễ. Lý do: máy thiếu bộ nhớ nên các lần chạy có Kafka và Connect bị dừng. Phần này dựa vào cơ chế hộp thư và Debezium đã kiểm ở feature 01, vì feature 03 không thêm mã ở tầng đó.

## T12. Thứ tự làm · Dự kiến

Chi tiết ở `plan.md`. Mạch: (1) ADR-0011 và `api.yaml`, `data.md`; (2) miền và test đơn vị; (3) lưu trữ, migration, ràng buộc, test đua nhau; (4) hộp thư và use case; (5) API và bảo mật; (6) hạ tầng dev (báo trước) và chạy thật; (7) phá thử, đóng feature. Phần miền và lưu trữ giao `agy` được (khung chặt); đua nhau và bảo mật tự rà.

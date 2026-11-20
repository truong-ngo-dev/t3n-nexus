# Feature 04: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC đã có: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7), [ADR-0003](../../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) (hợp đồng danh tính), [ADR-0008](../../../global/2.architecture/adr/0008-event-outbox-and-consumption.md) (hộp thư), [ADR-0011](../../../global/2.architecture/adr/0011-optimistic-lock-and-storage-uniqueness.md) (khóa lạc quan, `STALE_VERSION`). **Feature này không cần ADR mới:** khóa lạc quan có nghĩa nghiệp vụ ở quyết định của quản trị đã nằm trong ADR-0011; hợp đồng bản ghi kiểm toán là của feature 02. Nhãn: **Dự kiến** (chưa có mã), **Đã làm** (có mã và kiểm chứng).

## Câu hỏi cần chốt trước khi viết `plan.md`

Q1 đến Q7 lấy từ `flow.md` mục 6. Cột "Đề xuất" là hướng tôi nghiêng; **Q1 và Q5 đổi phạm vi hoặc hợp đồng nên cần bạn xác nhận**, các câu còn lại là chi tiết thi công.

| # | Câu hỏi | Đề xuất | Hệ quả nếu khác |
|---|---|---|---|
| Q1 | Người bán đổi tên gian hàng sau khi được duyệt: Danh mục có phải biết ngay không | **Đã chốt `2026-10-31`: không cần, vì `SellerProfileApproved` không mang tên gian hàng.** Danh mục chỉ giữ bản sao trạng thái (mã tài khoản, đã duyệt hay chưa, phiên bản nhận; `catalog-service/service.md` mục 2) để kiểm điều kiện đăng và độ hiển thị, không dùng tên. Không có event đổi tên, không có mục hoãn. Đã sửa `analysis.md` mục 8.1, `service.md` mục 5 và `5. event-catalog.md` cho khớp | Mang tên trong event: bản sao Danh mục cũ khi người bán đổi tên, buộc thêm event đổi tên (một chủ đề, một lớp event, sửa `UpdateShopInfo` của feature 03) trong khi chưa ai dùng tên đó. Nếu sau này giao diện người mua cần "Bán bởi ...", đó là nhu cầu của feature hiển thị, quyết lúc đó |
| Q2 | Ai ghi hộp thư kết quả và bản ghi kiểm toán | **Đã chốt `2026-10-31`: event handler, theo đúng quy ước đang có trong dự án.** Aggregate phát domain event, use case lưu rồi `eventDispatcher.dispatchAll(...)`, mỗi `EventHandler` làm một việc trong **cùng giao dịch** (bộ phát chạy đồng bộ, handler ném lỗi thì rollback cả giao dịch: `EventDispatcher`, `catalog-service` và `oauth2-service` đã dùng). Use case chỉ làm việc nghiệp vụ của nó; một handler ghi hộp thư, một handler ghi kiểm toán (T2, T3) | Use case gọi thẳng `OutboxEventStore` và `AuditRecorder` (cách feature 03 đã làm cho `Submit`, `Resubmit` đến `2026-11-19`, sau đó đã đồng bộ): chạy được nhưng use case gánh việc phụ và lệch quy ước của các service khác |
| Q3 | Mã lỗi khi hai quản trị đua nhau, và khi hồ sơ đã bị sửa | **Đã chốt `2026-10-31`.** Lệnh duyệt và từ chối đọc hồ sơ bằng `SELECT ... FOR UPDATE` nên hai quản trị (hoặc quản trị và chủ hồ sơ) xếp hàng ở dòng đó. Thứ tự kiểm, toàn bộ trong miền: vai → lý do (từ chối) → trạng thái (`INVALID_STATE`) → phiên bản đã xem (`STALE_VERSION`). Với lệnh quản trị chỉ còn hai mã 409 này; `CONCURRENT_UPDATE` không còn xảy ra vì khóa dòng đã xếp hàng (chủ hồ sơ sửa đúng lúc bị chặn rồi thất bại vì phiên bản đã tăng, theo đường của feature 03) | Chỉ khóa lạc quan, không khóa dòng: quản trị đua nhau nhận `CONCURRENT_UPDATE` mơ hồ phải thử lại. Kiểm phiên bản trước trạng thái: hồ sơ đã duyệt xong mà gửi bản cũ nhận `STALE_VERSION`, không biết đã có người quyết định |
| Q4 | Phân trang danh sách | Số trang và kích thước (`page` từ 0, `size` mặc định 20, tối đa 50), sắp `submitted_at` tăng dần rồi `id` để ổn định; trả `totalElements`. Không lọc ở C1a | Con trỏ: đúng cho dữ liệu lớn, thừa cho hàng chờ nhỏ (`analysis.md` mục 12) và khó cho giao diện nhảy trang |
| Q5 | Chi tiết hồ sơ không còn chờ duyệt | **Trả 200 kèm `status`, `reviewedAt`, `rejectionReason`** cho bất kỳ trạng thái nào; lệnh duyệt và từ chối mới trả `INVALID_STATE`. Đổi `flow.md` mục 2 theo (hiện ghi 409) | 409 ở chi tiết: quản trị mở từ liên kết cũ chỉ thấy lỗi, không biết ai đã quyết định thế nào |
| Q6 | Đường dẫn và nội dung | Tiền tố `/api/admin/` như `scheduler-service`. `GET /api/admin/seller-profiles`, `GET /api/admin/seller-profiles/{profileId}`, `POST .../{profileId}/approve` nội dung `{"version": n}`, `POST .../{profileId}/reject` nội dung `{"version": n, "reason": "..."}`. **Đã chốt `2026-10-31`: phiên bản nằm trong nội dung**, vì phiên bản đã xem là một phần của nghiệp vụ (INV-SEL-06) nên thuộc về lệnh, không phải điều kiện của giao thức; lệch trả 409 `STALE_VERSION`, thiếu trả 400. Không dùng `ETag`/`If-Match` | ETag + `If-Match` (RFC 9110; lệch: 412, thiếu: 428) đúng chuẩn và độ phức tạp thêm không đáng kể, nhưng quy tắc nghiệp vụ chuyển ra header; cổng truy cập và BFF hiện chưa cho header lạ đi qua nên phải mở thêm; chỉ đáng khi cần dùng chung cơ chế với dịch vụ khác |
| Q7 | Khóa dữ kiện kiểm toán | **Chỉ nêu hành vi và giá trị audit của hành vi** (chốt hướng `2026-10-31`). Duyệt (`SELLER_PROFILE_APPROVED`): `viewedVersion`, `sellerAccountId`. Từ chối (`SELLER_PROFILE_REJECTED`): hai giá trị trên cộng `rejectionReason`. Chi tiết khai báo ở T3 | Thêm giá trị ngoài hành vi (tên gian hàng, dữ liệu hồ sơ): lặp dữ liệu có sẵn ở hồ sơ, và có thể đổi sau |

**Trạng thái:** Q1, Q2, Q3, Q5, Q6, Q7 đã chốt `2026-10-31` (Q2: event handler cùng giao dịch; Q3: khóa dòng khi duyệt, miền giữ và so phiên bản đã xem; Q5: chi tiết trả 200 kèm `status` cho mọi trạng thái; Q6: phiên bản trong nội dung; Q7: chỉ hành vi và giá trị audit của nó). Q4 (phân trang) theo đề xuất, chưa bị phản đối.

## T1. Miền: hai hành vi, phiên bản đã xem, vai quản trị · Đã làm

- **Miền giữ phiên bản nạp, chỉ đọc.** Lệnh duyệt mang theo phiên bản đã xem và INV-SEL-06 là quy tắc nghiệp vụ có tên, nên miền tự so. `restore` thêm tham số `Long version`, `SellerProfileMapper.toDomain` truyền `entity.getVersion()`, aggregate có `version()` **chỉ đọc**; không có hàm cập nhật phiên bản sau khi lưu. Trường là của riêng `SellerProfile`, **không sửa `AbstractAggregateRoot`** (lớp gốc dùng chung của `common-domain`, có `private Long version` không accessor, và 28 lớp ở nhiều service kế thừa nó); sửa lớp gốc buộc chạy lại test của mọi service đó. Không mâu thuẫn ADR-0011: "chạy ngầm" áp cho **chủ hồ sơ** (không gửi, không thấy); quyết định của quản trị kèm phiên bản đã xem là chủ ý của ADR (`service.md` mục 2). Test đang dựng aggregate bằng `restore` phải sửa theo (tăng một tham số).
- **`SellerActor.requireAdmin()`:** từ chối khi vai không phải `ADMIN` bằng `SELLER_ROLE_NOT_ALLOWED` (403), cùng mã với vai không được phép của chủ hồ sơ. **Nhất quán với `tech.md` feature 03 T8** (vai là quy tắc nghiệp vụ, không `@PreAuthorize`): miền kiểm cho `approve` và `reject`, use case đọc kiểm cho danh sách và chi tiết. Hệ quả: `service.md` mục 4 đang viết "chú thích ở lớp trình bày" cho bốn điểm cuối quản trị là lệch quyết định `2026-10-26`; sửa ở bước đóng feature.
- **`approve(actor, viewedVersion, eventId, now)`** và **`reject(actor, viewedVersion, reason, eventId, now)`** (`eventId` do use case sinh qua cổng sinh mã, như `submit` nhận `id`), thứ tự kiểm (Q3): vai quản trị; với `reject`, lý do (cắt khoảng trắng đầu cuối; trống: `REJECTION_REASON_REQUIRED` 400; quá 500: `SELLER_FIELD_INVALID` nêu tên trường `reason`, INV-SEL-03); trạng thái `PENDING_REVIEW` (khác: `INVALID_STATE`); `viewedVersion` bằng `version()` (lệch: `STALE_VERSION` 409, INV-SEL-06). Dòng đã bị `FOR UPDATE` khóa nên `version()` vừa nạp là số cuối cùng đến hết giao dịch (T2). Thành công: đổi `status`, ghi `reviewedAt` (= giờ truyền vào, cắt về micro), `reviewedBy` (= mã tài khoản quản trị), `rejectionReason` (chỉ khi từ chối), `updatedAt`. Không chạm `legalInfo`, `shopInfo`, `submittedAt`.
- **Hồ sơ `APPROVED` rồi bị từ chối, hay ngược lại:** ma trận (`analysis.md` mục 5) chỉ cho từ `PENDING_REVIEW`; ba trạng thái × hai hành động đều có test.
- **Domain event do aggregate phát:** `approve` thêm `SellerProfileApproved`, `reject` thêm `SellerProfileRejected` bằng `addDomainEvent` (lớp gốc có sẵn). Phiên bản trong event là phiên bản **sau** quyết định, bằng `version() + 1`: quy tắc "mỗi thay đổi nội dung hồ sơ tăng phiên bản một" (`service.md` mục 2) là của miền, và lưu trữ (`@Version`) cũng tăng một mỗi lần cập nhật; KT-03 kiểm hai số này luôn bằng nhau sau khi lưu, nên lệch là test đỏ. Event còn mang thêm các giá trị chỉ dành cho handler kiểm toán (người thực hiện, `viewedVersion`, lý do từ chối) ở dạng trường Java, **không** đưa vào `getPayload` (T4).
- **Mã lỗi mới:** `STALE_VERSION` (409), `REJECTION_REASON_REQUIRED` (400) thêm vào `SellerErrorCode`; `STALE_VERSION` vào `CONFLICT_CODES` của `SellerExceptionHandler` để có log `SELLER_CONFLICT` và bộ đếm.
- **Loại:** để tầng lưu trữ so phiên bản (`saveDecision(profile, expectedVersion)`): INV-SEL-06 nằm ngoài miền, test đơn vị không chứng minh được, thứ tự kiểm bị tách hai tầng. Hàm công khai `applyPersistedVersion` hoặc cổng lưu trả phiên bản mới để lấy số cho event: thêm phương thức ghi hoặc đổi chữ ký cổng cho thứ miền tự tính được (`version() + 1`). Dùng thời điểm sửa cuối thay số phiên bản: hai sửa cùng micro giây vẫn lọt, số tăng đơn điệu không.

## T2. Use case và event handler: một giao dịch, ba thứ ghi · Đã làm

`ApproveSellerProfile` và `RejectSellerProfile` (`@Transactional`) chỉ làm việc nghiệp vụ; hộp thư và kiểm toán do handler làm (Q2). Thứ tự trong giao dịch:

1. `actor.requireAdmin()`; thiếu phiên bản: 400 (kiểm sớm, chưa đọc kho).
2. `repository.findByIdForUpdate(id)` (`SELECT ... FOR UPDATE`; không có: 404 `SELLER_PROFILE_NOT_FOUND`). Khóa dòng xếp hàng các giao dịch đua nhau.
3. `profile.approve(actor, viewedVersion, eventId, now)` hoặc `reject(...)`: miền kiểm vai, lý do, trạng thái, phiên bản đã xem và phát domain event (T1).
4. `repository.save(profile)` (`saveAndFlush`; khóa lạc quan chạy ngầm vẫn là chốt cuối).
5. `eventDispatcher.dispatchAll(profile.getDomainEvents())` rồi `profile.clearDomainEvents()` (cùng cách `PublishProduct` của `catalog-service`).

Handler đăng ký trong `application/sellerprofile/event/`, mỗi handler một việc, đều chạy trong giao dịch của use case:

| Handler | Event | Việc |
|---|---|---|
| `SellerProfileApprovedHandler` | `SellerProfileApproved` | `OutboxEventStore.store(event)` (T4) |
| `SellerProfileRejectedHandler` | `SellerProfileRejected` | `OutboxEventStore.store(event)` (T4) |
| `SellerProfileApprovedAuditHandler` | `SellerProfileApproved` | `AuditRecorder.record(...)` (T3) |
| `SellerProfileRejectedAuditHandler` | `SellerProfileRejected` | `AuditRecorder.record(...)` (T3) |

Cần thêm `EventDispatcherConfig` cho `seller-service` (một bean `EventDispatcher` nhận danh sách handler, như `catalog-service`). Vì sao cần cả khóa dòng lẫn so phiên bản: khóa dòng chỉ sống trong giao dịch này, nên chỉ chặn hai giao dịch **chồng nhau**. Nó không biết quản trị đã xem bản nào ở yêu cầu HTTP trước đó (người bán sửa giữa lúc quản trị đọc và lúc bấm duyệt); đó là việc của `viewedVersion` (R4, S7).

Bước 5 sau bước 4 để lỗi khóa hay ràng buộc nổ trước khi tốn việc ghi hộp thư; bộ phát chạy đồng bộ và fail-fast nên một handler ném lỗi (kể cả hành động hay khóa kiểm toán chưa khai báo) thì rollback cả giao dịch (E11, E12). Không bắt rồi thử lại trong use case (bài học feature 02: làm hỏng giao dịch Postgres). `Submit`, `Resubmit` của feature 03 đang gọi thẳng `OutboxEventStore`; không đổi trong feature này (xem `plan.md` bước 8).

- **Loại:** use case gọi thẳng hộp thư và kiểm toán (lệch quy ước, use case gánh việc phụ); handler chạy sau commit hoặc consumer Kafka đọc lại event để ghi kiểm toán: có thể commit xong mà ghi kiểm toán lỗi, trái INV-SEL-07 và R12; hai giao dịch: mất dấu vết nếu giao dịch thứ hai hỏng. Phát event kiểm toán bằng gọi Kafka trực tiếp: trái ADR-0008.

- **Ghi chú sau khi làm:** `ApproveSellerProfile.Result`, `RejectSellerProfile.Result` trả thêm `version` sau quyết định (`profile.version() + 1`), vì hồ sơ chỉ giữ phiên bản đã nạp; bộ điều khiển dùng số này cho phản hồi và log.

## T3. Dấu vết kiểm toán · Đã làm

- **Phụ thuộc:** thêm `audit-events` vào `pom.xml` của `seller-service` (hiện chưa có). Cấu hình tự động của thư viện dựng `AuditRecorder` khi có `OutboxEventStore` và một `AuditActionCatalog`.
- **Khai báo hành động** (lớp cấu hình `AuditConfig` ở `infrastructure/crosscutting/config`, một bean `AuditActionCatalog`):

| Hành động | Khóa dữ kiện cho phép |
|---|---|
| `SELLER_PROFILE_APPROVED` | `viewedVersion`, `sellerAccountId` |
| `SELLER_PROFILE_REJECTED` | `viewedVersion`, `sellerAccountId`, `rejectionReason` |

- **Ai gọi:** hai handler kiểm toán (T2), không phải use case. Handler đọc người thực hiện, `viewedVersion`, mã chủ hồ sơ, lý do (từ chối) từ domain event.
- **Bản ghi:** người thực hiện = (mã tài khoản quản trị, `ADMIN`); đối tượng = (`SELLER_PROFILE`, mã hồ sơ); `sourceService` lấy từ `spring.application.name` = `seller-service` (khớp mẫu hợp lệ của hợp đồng). `viewedVersion` là phiên bản quản trị gửi; `sellerAccountId` là chủ hồ sơ.
- **Không có dữ liệu định danh:** các khóa trên không bị chính sách cấm; `AuditDetailsPolicy.strict` ném lỗi nếu khóa lạ hay quá 500 ký tự, và đó là lỗi lập trình (E12).
- **Lý do từ chối** vào bản ghi kiểm toán (nơi giữ lịch sử, `analysis.md` mục 6) nhưng **không** vào `SellerProfileRejected`. Hồ sơ cũng giữ lý do gần nhất cho người bán đọc ở "Hồ sơ của tôi".
- **Kiểm chứng hợp đồng không cần `audit-service`:** test đọc hàng hộp thư bằng `AuditRecordReader` của thư viện; đọc được và khớp hành động, đối tượng, dữ kiện là bằng chứng bản ghi đúng hợp đồng (KT-05).

## T4. Hộp thư: `SellerProfileApproved`, `SellerProfileRejected` · Đã làm

| Lớp event | Chủ đề | Khóa thông điệp | Payload |
|---|---|---|---|
| `SellerProfileApproved` | `seller.profile.approved` | mã tài khoản chủ hồ sơ | `accountId`, `profileId`, `version`, `occurredAt` |
| `SellerProfileRejected` | `seller.profile.rejected` | mã tài khoản chủ hồ sơ | `accountId`, `version`, `occurredAt` |

- Theo mẫu `SellerProfileSubmitted`: cài `DomainEvent`, `aggregateType` = `SellerProfile`, `aggregateId` = mã tài khoản chủ, `getPayload` trả bản đồ bất biến. `version` là phiên bản **sau** quyết định (`version() + 1`, T1), khác `viewedVersion` là số quản trị đã xem trước quyết định; người thực hiện, `viewedVersion`, lý do từ chối là trường Java của event cho handler kiểm toán, **không** nằm trong `getPayload`; `occurredAt` = `reviewedAt`.
- **Không mang** thông tin pháp lý, lý do từ chối, mã quản trị (`SensitiveFieldPolicy.strict()` của khung chặn thêm nếu lỡ). Khớp `5. event-catalog.md`, không cần sửa catalog.
- **Hạ tầng:** `outbox_events` và connector `seller` đã có từ feature 03; chủ đề tự tạo ở dev.
- **Bên nhận:** chưa ai ở C1a (`flow.md` mục 1).

## T5. Đọc: danh sách và chi tiết · Đã làm

- **Danh sách (`RM-SEL-02`)** qua cổng đọc `PendingSellerProfileQuery` ở tầng ứng dụng (không nằm ở miền, đúng `tech.md` feature 03 T3). Cài ở hạ tầng bằng truy vấn đọc riêng, không nạp aggregate. Lọc `status = 'PENDING_REVIEW'`, sắp `submitted_at, id`.
- **Danh sách chỉ có thông tin cơ bản (chốt `2026-10-31`):** truy vấn chỉ chọn `id`, `seller_type`, `shop_name`, `submitted_at`; **không chọn** `identity_number`, `tax_code`, `business_address`, `legal_name`, không che cũng không có bản che. Không có `version` (chi tiết đã trả để quyết định). Lý do: kiểm INV-SEL-11 bằng cấu trúc (trường không tồn tại ở danh sách) thay vì bằng kỷ luật; số đã che không giúp chọn hồ sơ nên không đáng giữ (`analysis.md` mục 10).
- **Chỉ mục (migration V3):** `CREATE INDEX idx_seller_profiles_pending_queue ON seller_profiles (submitted_at, id) WHERE status = 'PENDING_REVIEW'`. Chỉ mục một phần vì hàng chờ nhỏ so với bảng, đã bàn ở `data.md`; `data.md` ghi `(status, submitted_at)`, sửa theo dạng một phần.
- **Chi tiết (`RM-SEL-03`)** `GetSellerProfileForReview`: `actor.requireAdmin()`, rồi `findById` và trả đủ trường qua `AdminSellerProfileResponse` (gồm `ownerAccountId`, `version`, `status`, `reviewedAt`, `reviewedBy`, `rejectionReason`, mọi trường pháp lý và gian hàng). Mọi trạng thái (Q5). Không ghi gì, không event, không log nội dung.
- **Loại:** cùng `SellerProfileResponse` của chủ hồ sơ cho cả hai (thêm trường `version` làm rò cho chủ hồ sơ, vi phạm quyết định Q2 feature 03); nạp dữ liệu định danh vào danh sách rồi che hoặc bỏ ở tầng trình bày (một lỗi che là rò); dòng danh sách mang bản che của số căn cước và mã số thuế (`analysis.md` mục 10 đã bỏ).

## T6. API và bảo mật · Đã làm

| Điểm cuối | Use case | Thành công | Ghi chú |
|---|---|---|---|
| `GET /api/admin/seller-profiles?page&size` | Danh sách chờ duyệt (`RM-SEL-02`) | 200 `{items, page, size, totalElements}` | `size` ngoài 1 đến 50: 400 |
| `GET /api/admin/seller-profiles/{profileId}` | Chi tiết (`RM-SEL-03`) | 200 | 404 khi không có; mã không phải UUID: 400 |
| `POST /api/admin/seller-profiles/{profileId}/approve` | Duyệt (`CMD-SEL-05`) | 200 hồ sơ sau quyết định | Nội dung `{"version": n}`; thiếu `version`: 400 `SELLER_FIELD_INVALID` nêu tên trường |
| `POST /api/admin/seller-profiles/{profileId}/reject` | Từ chối (`CMD-SEL-06`) | 200 hồ sơ sau quyết định | Nội dung `{"version": n, "reason": "..."}` |

- **Bảo mật:** `SecurityConfig` thêm `/api/admin/seller-profiles/**` vào nhóm `authenticated()`; `anyRequest().denyAll()` giữ nguyên. Vai không kiểm ở bộ lọc mà ở miền hoặc use case (T1). `SellerActorResolver` dùng nguyên.
- **Bộ điều khiển riêng** `AdminSellerProfileController` (không nhét vào `SellerProfileController`: khác tiền tố, khác nhóm người dùng, khác kiểu phản hồi). Log và bộ đếm đặt ở bộ điều khiển theo cách của feature 03 để thống nhất; việc chuyển sang use case đã ghi là sai lệch đã biết ở `plan.md` feature 03, không sửa ở đây.
- **Mã lỗi thêm cho feature này** (cộng vào `api.yaml`): 400 `REJECTION_REASON_REQUIRED`, 409 `STALE_VERSION`; các mã khác dùng lại. `INVALID_STATE` và `CONCURRENT_UPDATE` đã có; ở bốn điểm cuối quản trị `CONCURRENT_UPDATE` không còn xuất hiện (Q3).
- **Phản hồi quản trị:** `status`, `version` luôn có. Thông báo lỗi nêu tên trường, không nêu giá trị, riêng `reason` không bao giờ lặp lại trong thông báo lỗi (có thể chứa chữ nhạy cảm do quản trị gõ).

- **Ghi chú sau khi làm:** thư viện dùng chung `common-web` không xử lý `MethodArgumentTypeMismatchException` (rơi vào nhánh 500); `SellerExceptionHandler` thêm handler riêng trả 400 `SELLER_FIELD_INVALID`, nêu tên tham số, không lặp lại giá trị. Không sửa thư viện dùng chung vì ảnh hưởng mọi service.

## T7. Dữ liệu định danh không rò · Đã làm

- **Log:** use case và bộ điều khiển chỉ log mã hồ sơ, mã quản trị, phiên bản, kết quả. **Lý do từ chối không vào log** (chữ tự do). `reason` thêm vào danh sách che của `BodySanitizer` nếu nội dung yêu cầu có thể bị log nguyên văn (kiểm bằng KT-08).
- **Event:** T4. **Danh sách:** T5 (trường không tồn tại). **Chi tiết:** đủ trường chỉ cho quản trị, đúng INV-SEL-11.
- **Kiểm toán:** T3 chỉ mang mã, số phiên bản và lý do từ chối.

## T8. Quan sát · Đã làm

| Mã log | Mức | Khi nào | Nội dung |
|---|---|---|---|
| `SELLER_PROFILE_APPROVED` | INFO | Duyệt thành công | mã hồ sơ, mã quản trị, phiên bản sau |
| `SELLER_PROFILE_REJECTED` | INFO | Từ chối thành công | mã hồ sơ, mã quản trị, phiên bản sau (không lý do) |
| `SELLER_CONFLICT` | INFO | `STALE_VERSION`, `CONCURRENT_UPDATE` | mã lỗi |

Bộ đếm: `seller.profile.decision{result=approved|rejected}`; `seller.profile.conflict{code}` đã có, thêm nhãn `STALE_VERSION`.

## T9. Hạ tầng và chạy thật · Đã làm

- **Hạ tầng dev:** không thêm gì; `postgres-seller` và connector `seller` đã khai báo ở feature 03 (container dev chưa khởi động, ở `deferred.md` feature 03).
- **Chạy thật (phần D)**, stack cô lập tiền tố `probe-` như feature 03, nhưng **chia nhỏ** vì máy thiếu bộ nhớ (cả bộ bị dừng sáu lần khi chạy feature 03): Postgres, Kafka, Connect, `seller-service` với `-Xmx384m`, nguồn khóa công khai thử; **không dựng `audit-service`**. Bằng chứng phía phát: một lần duyệt ra đúng hai thông điệp, `seller.profile.approved` (khóa là mã tài khoản) và `audit.action.recorded`; từ chối ra `seller.profile.rejected` cộng bản ghi kiểm toán; đua nhau 20 luồng duyệt cùng hồ sơ cho đúng một 200.
- **Phần chưa chạy trong feature này:** `audit-service` nhận và đọc lại bản ghi (S8 phía nhận) thuộc feature 13 (kiểm đầu-cuối); hợp đồng đã kiểm bằng `AuditRecordReader` (T3) và feature 02. Sẽ ghi vào `deferred.md` của feature này nếu bạn xác nhận cách chia này.

## T10. Kiểm chứng · Đã làm

Test viết từ bảng này và `flow.md` mục 4 trước khi viết mã (đỏ trước, phá thử sau).

| # | Kiểm | Cấp | Đường |
|---|---|---|---|
| KT-01 | Miền: `approve`, `reject` từ cả ba trạng thái (dựng `APPROVED`, `REJECTED` bằng `restore`); thứ tự kiểm vai → lý do → trạng thái → phiên bản đã xem (ca chứng minh thứ tự: hồ sơ đã `APPROVED` kèm phiên bản cũ nhận `INVALID_STATE`, không nhận `STALE_VERSION`); `viewedVersion` khớp thì qua, lệch thì `STALE_VERSION`; vai `USER` và vai rỗng bị từ chối; lý do trống, toàn khoảng trắng, đúng 500, 501; ghi `reviewedAt`, `reviewedBy`, lý do; không chạm trường khác; `restore` mang phiên bản và `version()` đọc đúng | Đơn vị | E1 đến E5, E6 |
| KT-02 | Use case và handler (kho giả, `EventDispatcher` thật, hộp thư và bộ ghi kiểm toán giả): duyệt phát đúng một domain event, handler hộp thư ghi một event kết quả (mang phiên bản nạp + 1) và handler kiểm toán ghi một lần đúng tham số (`viewedVersion` = phiên bản đã xem); từ chối tương tự, lý do chỉ ở kiểm toán; use case không gọi thẳng hộp thư hay bộ ghi kiểm toán; hồ sơ không có: 404; thiếu phiên bản: 400, chưa đọc kho; hồ sơ nạp có phiên bản khác `viewedVersion` thì `STALE_VERSION`, không lưu, không ghi event, không ghi kiểm toán; bộ ghi kiểm toán ném lỗi thì lan ra (không nuốt) | Đơn vị | E4, E5, E11, E12 |
| KT-03 | Lưu trữ (Postgres thật): `findByIdForUpdate` khóa dòng (giao dịch thứ hai chờ tới khi giao dịch đầu kết thúc); sau `save`, phiên bản trong cơ sở dữ liệu bằng phiên bản nạp + 1, đúng bằng phiên bản mà domain event mang (nếu lệch là đỏ); mapper nạp `version` vào aggregate; `CHECK` chặn `APPROVED` thiếu `reviewed_by` và `REJECTED` thiếu lý do; chỉ mục V3 tồn tại và truy vấn hàng chờ dùng đúng thứ tự; sau khi một hồ sơ chuyển `REJECTED`, tài khoản khác nộp được cùng khóa định danh (khóa được nhả) | Tích hợp | T1, T5, E15 |
| KT-04 | **Đua nhau:** 20 luồng cùng duyệt một hồ sơ cùng phiên bản: đúng một 200, còn lại 409 `INVALID_STATE` (không có `CONCURRENT_UPDATE` vì khóa dòng xếp hàng), đúng hai hàng hộp thư; một duyệt một từ chối cùng lúc: đúng một thành công; duyệt đua với chủ hồ sơ sửa gian hàng: hoặc chủ sửa trước (duyệt nhận `STALE_VERSION`), hoặc duyệt trước (chủ nhận `CONCURRENT_UPDATE` theo đường feature 03), không mất sửa đổi nào im lặng; **sửa giữa lúc xem và lúc duyệt** (9:00 xem, 9:02 sửa, 9:05 duyệt kèm phiên bản cũ): `STALE_VERSION`, đây là ca chứng minh R4 mà khóa dòng một mình không bắt được | Tích hợp | E1, E3 |
| KT-05 | Hộp thư: duyệt và từ chối mỗi lần ghi đúng **hai** hàng; payload khớp bảng T4; hàng kiểm toán đọc được bằng `AuditRecordReader` (hành động, đối tượng, dữ kiện đúng); rollback (lỗi giữa chừng) không để hàng nào và không đổi trạng thái; sau khi duyệt, chủ hồ sơ sửa thông tin gian hàng thì không có hàng hộp thư mới (E16) | Tích hợp | E11, E12, E16, T3, T4 |
| KT-06 | Đọc: danh sách chỉ `PENDING_REVIEW`, cũ nhất trước, `id` phá hòa, phân trang đúng và `size` bị chặn; **JSON danh sách chỉ có đúng bốn khóa** `id`, `sellerType`, `shopName`, `submittedAt` (không số căn cước, mã số thuế, địa chỉ kinh doanh, tên pháp lý, kể cả bản che; không `version`); chi tiết đủ trường cho mọi trạng thái | Tích hợp | Q4, Q5, E14 |
| KT-07 | API (MockMvc + `jwt()`): bốn điểm cuối thành công; 401 không token; 403 cho `USER` và vai rỗng ở **cả bốn** điểm cuối và không đọc kho; 404 mã lạ; 400 mã không phải UUID, thiếu `version`, thiếu hay quá dài `reason`; 409 `INVALID_STATE`, `STALE_VERSION`; quản trị mở hồ sơ rồi chủ sửa rồi duyệt bản cũ: 409, hồ sơ không đổi | Tích hợp | E1 đến E8 |
| KT-08 | Rò dữ liệu: chụp log của mọi ca; không có số căn cước, mã số thuế, địa chỉ, lý do từ chối; `SellerProfileRejected` và `SellerProfileApproved` chỉ có đúng trường ở T4 | Tích hợp | E14, T7 |
| KT-09 | Chạy thật (stack cô lập, chia nhỏ như T9): duyệt, từ chối, đua nhau trên cơ sở dữ liệu thật; token ký thật; đếm thông điệp đúng trên cả hai chủ đề | Chạy thật | S2, S7, R12 |

**Phá thử (sau khi xanh):** bỏ kiểm `viewedVersion` ở miền, bỏ `FOR UPDATE` (đọc thường), đảo thứ tự kiểm (phiên bản trước trạng thái), bỏ `requireAdmin` ở một lệnh, bỏ lần ghi kiểm toán, ghi kiểm toán ngoài giao dịch (`REQUIRES_NEW`), bỏ `@Version`, thêm `business_address` vào truy vấn danh sách, in lý do vào log: mỗi lần phải có ít nhất một test đỏ.

**Kết quả KT-09 · Đã làm (`2026-10-31`).** Stack cô lập `probe-seller`, token ký thật, không chạm hạ tầng dev. Nhóm A (Postgres, JWKS thử, ứng dụng): 47 mục đạt, gồm 20 quản trị cùng duyệt một hồ sơ cho đúng một 200 và 19 lần `INVALID_STATE`. Nhóm B (thêm Kafka và Connect, chạy tuần tự): 20 mục đạt, thông điệp thật trên `seller.profile.approved`, `seller.profile.rejected`, `audit.action.recorded`, mỗi quyết định đúng một thông điệp kết quả và một bản ghi kiểm toán. Chưa kiểm: nhiều quyết định song song qua Kafka, `audit-service` nhận và lưu (đều ở `deferred.md`).

## T11. Thứ tự làm · Đã làm

Chi tiết ở `plan.md`. Mạch: (1) `api.yaml`, `data.md`, `service.md` cho phần của feature (trước mã); (2) miền: phiên bản, `requireAdmin`, `approve`, `reject`, test đơn vị; (3) lưu trữ: ánh xạ phiên bản, migration V3, truy vấn danh sách, test; (4) hộp thư, khai báo kiểm toán, hai use case; (5) đọc và API quản trị, bảo mật; (6) chạy thật (báo trước nếu đụng hạ tầng dev); (7) phá thử, đóng feature. Phần miền và lưu trữ giao `agy` được (khung chặt); đua nhau, bảo mật, dữ liệu định danh tự rà.

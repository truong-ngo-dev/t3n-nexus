# Feature 09: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC đã có: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7), [ADR-0008](../../../global/2.architecture/adr/0008-event-outbox-and-consumption.md) (hộp thư, nhận ít nhất một lần), [ADR-0010](../../../global/2.architecture/adr/0010-dlq-per-consumer-service.md) (hàng chờ lỗi theo dịch vụ nhận), [ADR-0011](../../../global/2.architecture/adr/0011-optimistic-lock-and-storage-uniqueness.md), [ADR-0012](../../../global/2.architecture/adr/0012-parent-row-lock-for-child-operations.md) (khóa dòng sản phẩm), [ADR-0014](../../../global/2.architecture/adr/0014-full-state-events.md) (sự kiện trạng thái đầy đủ, quy tắc phát). Nghiệp vụ: `catalog-service/analysis/product.md` (CAP-CAT-07, 08), `0.overview.md` (INV-CAT-91, 95).

## Câu hỏi đã chốt

| # | Câu hỏi | Quyết định | Chốt |
|---|---|---|---|
| Q1 | Sự kiện trạng thái đầy đủ (D6) | **Đồng ý.** Hai sự kiện `EVT-CAT-01`, `EVT-CAT-02`; chủ đề `catalog.product.snapshot`, `catalog.sellable-unit.changed`; ảnh mang khóa đối tượng; quy tắc phát và so `version` ở ADR-0014 | `2026-11-06` (người dùng) |
| Q2 | `version` của `EVT-CAT-02`; nháp bị xóa có phát không | `version` của sản phẩm, bên nhận so theo từng đơn vị; **nháp không phát gì**, không có cờ "đã bị bỏ" ở C1a | `2026-11-06` (người dùng) |
| Q3 | Tạo nháp có cần người bán đã duyệt | **Có** (`INV-CAT-30`): `CreateProduct` đọc bản sao; chưa duyệt hoặc chưa có bản sao thì 403 `SELLER_NOT_APPROVED`. Lệnh sửa trên sản phẩm có sẵn không kiểm lại (ở C1a "Được duyệt" không thu hồi) | `2026-11-06` (theo đề xuất, người dùng đồng ý) |
| Q4 | Phát lại hàng loạt khi người bán mất duyệt | **C1b.** Ở C1a trạng thái "Được duyệt" không thu hồi được (`seller-service` `analysis.md`: chỉ C1b thêm "Bị khóa"), nên `visible` ≡ `status = PUBLISHED`; vẫn tính qua hàm `isPubliclyVisible` để C1b chỉ thêm điều kiện | `2026-11-06` |
| Q5 | Sửa sản phẩm đã đăng | Có hiệu lực ngay (đã cắt duyệt, B1), phát sự kiện. Thêm đúng một quy tắc mới: **không bỏ ảnh cuối của sản phẩm `PUBLISHED`** (`INV-CAT-34`, `PRODUCT_LAST_IMAGE`). Không thêm quy tắc "luôn có biến thể hoạt động" (`analysis` không có): sản phẩm đã đăng hết biến thể hoạt động vẫn `visible`, người mua thấy hết hàng; ghi nhận cho C1b | `2026-11-06` |
| Q6 | Báo thiếu điều kiện khi đăng | 409 `PRODUCT_NOT_PUBLISHABLE`, mảng `errors` của `ApiResponse` chứa **mọi** điều kiện thiếu dạng `CODE` hoặc `CODE:templateId`. Bộ xử lý lỗi riêng của catalog (ngoại lệ `PublishNotAllowedException` mang danh sách), vì bộ xử lý chung không có chỗ cho dữ liệu kèm | `2026-11-06` |
| Q7 | Bản sao trạng thái người bán | Bảng `seller_state(account_id, status, version, updated_at)`; ghi đè theo `version` hồ sơ bằng một câu `INSERT ... ON CONFLICT DO UPDATE ... WHERE version < mới`, nên nhận lặp, sai thứ tự không cần bảng khử trùng theo mã sự kiện | `2026-11-06` |
| Q8 | Cách nhận | `event-consumer-starter` (như `audit-service`): `@KafkaListener` trên `seller.profile.approved`, `seller.profile.rejected`, nhóm `catalog-service`, `EventEnvelopeConsumer`; hàng chờ lỗi theo ADR-0010. Phong bì mang `accountId`, `profileId`, `version`, `occurredAt` | `2026-11-06` |
| Q9 | Cách ghi sự kiện sản phẩm | Một thành phần ứng dụng `ProductEventRecorder` gọi `OutboxEventStore.store` trong giao dịch của use case; mọi use case sản phẩm, biến thể, ảnh gọi nó sau khi lưu. Bản chụp cần biến thể và bản sao người bán nên dựng ở tầng ứng dụng, không phải sự kiện miền của aggregate | `2026-11-06` |
| Q10 | Dữ liệu dev | Người bán đã duyệt trước khi catalog có consumer sẽ thiếu bản sao: script `infra/seed/catalog-seller-state.sql` (tham số mã tài khoản) nạp tay; không tự phát lại sự kiện | `2026-11-06` |
| Q11 | Kiểm chứng sự kiện | Testcontainers Kafka cho consumer và hộp thư; chạy thật ngăn xếp cách ly `probe-` với Kafka, Connect (connector `catalog`), MinIO, Postgres; bên nhận cuối là một consumer đọc topic. Không chạm `infra-*`, Kafka dev | `2026-11-06` |

## T1. Dữ liệu: migration `V4` · Dự kiến

`V4__publish_product.sql` (không sửa `V1` đến `V3`):

| Thay đổi | Chi tiết |
|---|---|
| `product.published_at TIMESTAMPTZ` | Thời điểm đăng đầu tiên, không đổi sau đó. `ck_product_published_at CHECK ((status = 'DRAFT') = (published_at IS NULL))`: nháp chưa từng đăng thì rỗng, đã đăng hoặc đã gỡ thì có |
| `seller_state` | `account_id UUID`, `status VARCHAR(20)` (`APPROVED`, `REJECTED`), `version BIGINT`, `updated_at TIMESTAMPTZ`; `pk_seller_state (account_id)`; `ck_seller_state_status`; `ck_seller_state_version (version >= 0)`. Không khóa ngoại (tài khoản thuộc BC khác) |

Hộp thư (`outbox_events`) đã có từ `V1`.

## T2. Miền · Dự kiến

- `Product`: thêm `publishedAt`; `publish(now)` (từ `DRAFT` hoặc `UNPUBLISHED`, đặt `publishedAt` lần đầu, `recordChange`), `unpublish(now)` (từ `PUBLISHED`), `assertDeletable()` (`DRAFT` và `publishedAt` rỗng), `isPublished()`. Sai trạng thái: 409 `CATALOG_INVALID_STATE`. `removeImage` từ chối khi `PUBLISHED` và là ảnh cuối (`PRODUCT_LAST_IMAGE`). `reconstitute` thêm một dạng nhận `publishedAt`; dạng cũ giữ nguyên (publishedAt rỗng).
- `SellerState` (record: tài khoản, trạng thái, phiên bản) và cổng `SellerStateRepository` (`find(accountId)`, `applyDecision(accountId, status, version)` trả có ghi đè hay không). `isApproved()`.
- `PublishPolicy` (hàm thuần): mở rộng `MissingPublishConditions` của 07, 08 thêm hai mã `SELLER_NOT_APPROVED` (không có bản sao hoặc không `APPROVED`) và `CATEGORY_NOT_USABLE` (danh mục không phải lá dùng được, hoặc có tổ tiên ngừng dùng; dùng lại kiểm của `CreateProduct`, `INV-CAT-97`). Trả đủ danh sách một lượt.
- `isPubliclyVisible(product, sellerState)`: hàm thuần duy nhất; ở C1a là `PUBLISHED` và người bán `APPROVED`.
- Mã lỗi mới: `SELLER_NOT_APPROVED` 403, `PRODUCT_NOT_PUBLISHABLE` 409, `PRODUCT_NOT_DELETABLE` 409, `PRODUCT_LAST_IMAGE` 409.

## T3. Use case và khóa · Dự kiến

| Use case | Việc | Khóa |
|---|---|---|
| `PublishProduct` | `requireSeller`; khóa dòng; kiểm trạng thái; đọc biến thể, danh mục, thuộc tính gán, `seller_state`; `PublishPolicy`; thiếu thì ném `PublishNotAllowedException`; `publish`; lưu; `ProductEventRecorder` (đăng) | khóa dòng |
| `UnpublishProduct` | `requireSeller`; khóa dòng; `unpublish`; lưu; ghi sự kiện (gỡ) | khóa dòng |
| `DeleteDraft` | `requireSeller`; khóa dòng; `assertDeletable`; xóa biến thể, tổ hợp, chỗ tải lên của sản phẩm, ảnh, thuộc tính, sản phẩm; sau commit `discard` đối tượng ảnh. Không sự kiện | khóa dòng |
| `RecordSellerDecision` | Nhận quyết định duyệt; `applyDecision` | không (câu lệnh có điều kiện phiên bản) |
| `CreateProduct` (sửa) | thêm kiểm `seller_state` đã `APPROVED` trước khi tạo | không |
| `UpdateProduct`, `AddVariant`, `UpdateVariant`, `DeactivateVariant`, `ActivateVariant`, `ConfirmImage`, `ReorderImages`, `RemoveImage` (sửa) | sau khi lưu gọi `ProductEventRecorder.onChanged(product, variants, changedVariantIds)` | như 07, 08 |

`ProductEventRecorder` (bảng quy tắc của ADR-0014): sản phẩm chưa từng đăng hoặc đang `UNPUBLISHED` và lệnh không phải gỡ thì không phát; `PUBLISHED` thì phát `EVT-CAT-01` và `EVT-CAT-02` cho các biến thể đổi; đăng, gỡ, đăng lại phát cả hai cho mọi biến thể. Các lệnh chỉ đổi giá, ảnh, tiêu đề (không đổi `active`) không phát `EVT-CAT-02`.

Sự kiện là hai lớp `DomainEvent` (`ProductSnapshotEvent`, `SellableUnitChangedEvent`) theo mẫu `CategoryChanged`, ghi qua `OutboxEventStore.store` (bắt buộc có giao dịch, R12), khóa phân vùng theo mã sản phẩm, chủ đề là khóa định tuyến của sự kiện (T5).

## T4. Nhận quyết định duyệt · Dự kiến

`SellerDecisionListener` (infrastructure/messaging, như `AuditActionRecordedListener`): hai `@KafkaListener` (`seller.profile.approved`, `seller.profile.rejected`) gọi `EventEnvelopeConsumer.consume`; đọc `accountId`, `version` từ phong bì; phong bì thiếu trường hoặc kiểu sai ném `NonRetryableEventException` (vào hàng chờ lỗi ngay); lỗi cơ sở dữ liệu thử lại có backoff. Cấu hình `spring.kafka.bootstrap-servers`, `app.catalog.seller-topics.*`, thêm phụ thuộc `event-consumer-starter` và Spring Kafka vào catalog.

## T5. API, sự kiện, bảo mật · Dự kiến

| Điểm cuối | Vai | Phản hồi |
|---|---|---|
| `POST /api/seller/products/{id}/publish` | `USER` | 200 sản phẩm (như `GET`), `PUBLISHED` |
| `POST /api/seller/products/{id}/unpublish` | `USER` | 200 sản phẩm, `UNPUBLISHED` |
| `DELETE /api/seller/products/{id}` | `USER` | 200 (không nội dung) |

`ProductResponse` thêm `publishedAt`. `missingPublishConditions` thêm hai mã mới (GET hiển thị chúng cho cả nháp, nên người bán thấy trước khi bấm Đăng). Chủ đề là khóa định tuyến cố định trong lớp sự kiện (`catalog.product.snapshot`, `catalog.sellable-unit.changed`), như `EVT-CAT-03`; không cấu hình. Connector Debezium `catalog` (`infra/debezium/outbox-connector.sh`) định tuyến theo `routing_key` của hộp thư. Không `permitAll`; vai kiểm trong use case như 07.

## T6. Kiểm toán · Dự kiến

Không ghi kiểm toán (hành động của người bán, không phải quản trị; Q2 của 07 giữ).

## T7. Kiểm chứng · Dự kiến

Test viết từ `flow.md` mục 4 trước khi viết mã; sau mỗi bước, phá thử (script chạy qua `bash -c`, luôn kèm ca đối chứng). Mức: đơn vị (miền, `PublishPolicy`, quy tắc phát), Testcontainers (migration, API với hộp thư, Kafka consumer, đua nhau), chạy thật (ngăn xếp cách ly với Kafka, Connect, MinIO). Danh sách KT-01 đến KT-09 ở `flow.md` mục 6.

Đột biến bắt buộc: bỏ kiểm trạng thái ở đăng; `PublishPolicy` dừng ở điều kiện đầu; bỏ `SELLER_NOT_APPROVED`; bỏ `CATEGORY_NOT_USABLE`; bỏ khóa dòng ở đăng (ca 20 luồng đỏ); `applyDecision` ghi đè bất kể phiên bản (ca sai thứ tự đỏ); phát khi nháp (ca E13 đỏ); không phát khi đăng; không phát `EVT-CAT-02` cho biến thể đã ngừng khi đăng; `EVT-CAT-02` mang giá; xóa nháp đã từng đăng được; bỏ `PRODUCT_LAST_IMAGE`; hộp thư ghi ngoài giao dịch (rollback còn hàng); bỏ `CreateProduct` kiểm bản sao; bỏ từng ràng buộc `V4`.

## T8. Thứ tự làm · Dự kiến

1. Rà kịch bản và ADR-0014 (đã viết cùng `flow.md`, `tech.md`).
2. `V4`, miền (`Product` publish/unpublish/xóa, `SellerState`, `PublishPolicy`, mã lỗi) và test (KT-01, KT-02, KT-05). **Giao `agy`.**
3. `seller_state` lưu trữ, `SellerDecisionListener`, tự cấu hình Kafka; test với Kafka thật (KT-07).
4. Use case đăng, gỡ, xóa nháp, sửa `CreateProduct`, API và bộ xử lý lỗi (KT-04, KT-06). **Giao `agy`.**
5. `ProductEventRecorder` và nối vào tám use case của 07, 08 (KT-03). **Giao `agy` bảng quy tắc và test; Người rà.**
6. Test API hộp thư, đua nhau, phá thử (KT-06, KT-08).
7. Chạy thật (KT-09): Kafka, Connect, connector `catalog`, MinIO, đọc topic.
8. Đóng: `deferred.md`, `service.md`, `data.md`, `api.yaml`, `event-catalog.md`, `00-features.md`; ADR-0014 giữ Accepted nếu các phép kiểm đạt.

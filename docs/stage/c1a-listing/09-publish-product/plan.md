# Feature 09: kế hoạch

> **Trạng thái:** Đã làm `2026-11-06` · Nguồn: [`flow.md`](flow.md), [`tech.md`](tech.md) (Q1 đến Q11 đã chốt), ADR-0014 (Accepted `2026-11-06`). Người dùng đang cần xong catalog nhanh để làm giao diện nên mỗi bước chạy liền, rà ở cuối.

## Từ luồng đến giải pháp

| Bước của luồng                          | Hướng giải                                                              | Gộp vào thành phần                                                         | Triển khai                                                                                                                                                                    |
|-----------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Đăng, đăng lại (3.1, 3.2)               | Khóa dòng; `PublishPolicy`; `Product.publish`; ghi sự kiện              | `PublishProduct`, `Product`, `PublishPolicy`                               | Đã làm: `PublishProduct`, `Product.publish`, `PublishPolicy` (`MissingPublishConditions`, 5 mã); `PublishUseCaseTest`, `ProductPublishApiTest`, KT-09 (đúng 1 trong 20 luồng) |
| Báo thiếu điều kiện                     | `PublishNotAllowedException` mang danh sách; bộ xử lý lỗi riêng         | `PublishNotAllowedException`, advice của catalog                           | Đã làm: `PublishNotAllowedException` + `PublishNotAllowedExceptionHandler` (`errors` mang đủ danh sách); API và chạy thật                                                     |
| Gỡ (3.2)                                | `Product.unpublish`; ghi sự kiện `visible=false`                        | `UnpublishProduct`                                                         | Đã làm: `UnpublishProduct`, `Product.unpublish`; sự kiện `visible=false`                                                                                                      |
| Xóa nháp (3.3)                          | Xóa tường minh biến thể, chỗ tải lên, ảnh; discard đối tượng sau commit | `DeleteDraft`, `ProductRepository.delete`                                  | Đã làm: `DeleteDraft`, `ProductRepository.delete`, `VariantRepository.deleteByProductId`, hủy đối tượng ảnh sau commit                                                        |
| Nhận kết quả duyệt (3.4)                | `@KafkaListener` + upsert theo phiên bản                                | `SellerDecisionListener`, `RecordSellerDecision`, `seller_state`           | Đã làm: `SellerDecisionListener`, `RecordSellerDecision`, `seller_state` (upsert theo phiên bản); `SellerDecisionListenerKafkaTest`, KT-09 (Kafka thật + hàng chờ lỗi)        |
| Tạo nháp cần người bán đã duyệt         | `CreateProduct` đọc `seller_state`                                      | `CreateProduct`                                                            | Đã làm: `CreateProduct` kiểm `seller_state`; `SELLER_NOT_APPROVED` 403                                                                                                        |
| Sự kiện khi sửa sản phẩm hiển thị (3.5) | `ProductEventRecorder` gọi từ tám use case                              | `ProductEventRecorder`, `ProductSnapshotEvent`, `SellableUnitChangedEvent` | Đã làm: `ProductEventRecorder`, `ProductSnapshotEvent`, `SellableUnitChangedEvent`, nối vào mười use case; `ProductEventRecorderTest`, KT-09 (Debezium, Kafka thật)           |
| Bỏ ảnh cuối của sản phẩm đã đăng        | `Product.removeImage` từ chối                                           | `Product`                                                                  | Đã làm: `Product.removeImage` từ chối ảnh cuối khi `PUBLISHED` (`PRODUCT_LAST_IMAGE`)                                                                                         |
| Lưu `published_at`, `seller_state`      | `V4`; entity và adapter                                                 | `V4`, `ProductJpaEntity`, `SellerStateJpaEntity`                           | Đã làm: `V4__publish_product.sql`, `ProductJpaEntity.publishedAt`, `SellerStateJpaEntity`; `PublishSchemaConstraintTest`                                                      |

## Điều kiện bắt đầu

Feature 07, 08 đã đóng (catalog ở `V3`); feature 04 đã phát `seller.profile.approved/rejected` (chạy thật). D6 và các điểm con đã chốt `2026-11-06`. **Không khởi động `catalog-service` trên `catalog_db` dev**, không chạm Kafka dev, MinIO dev: kiểm chứng bằng Testcontainers và ngăn xếp `probe-`. `catalog_db` dev sẽ áp `V4` khi người dùng khởi động lần tới.

Test chạy được: `./mvnw -o -q -pl libs/upload-starter,catalog-service -am test -Dtest='vn/t3nexus/catalog/**/*Test,vn/t3nexus/lib/upload/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` từ `services/`, `rm -rf catalog-service/target/classes/db` trước mỗi lần. Phá thử bằng script chạy qua `bash -c`, luôn kèm ca đối chứng không đổi gì. Chỉ chạy một lần Maven tại một thời điểm trên cùng `target`.

## Các bước

### Bước 1. Rà kịch bản · Người
`flow.md` mục 4 với mục 6; ADR-0014 viết cùng. **Triển khai:** xong khi viết tài liệu (`2026-11-06`).

### Bước 2. `V4`, miền, test · Agent
`V4`; `Product.publish/unpublish/assertDeletable`, `publishedAt`; `SellerState`, `SellerStateRepository` (cổng), `PublishPolicy`, `isPubliclyVisible`, mã lỗi; `PRODUCT_LAST_IMAGE` (KT-01, KT-02, KT-05).

### Bước 3. Lưu trữ `seller_state`, nhận sự kiện · Người + Agent
Entity, adapter, `RecordSellerDecision`, `SellerDecisionListener`, cấu hình Kafka; test với Kafka thật (KT-07).

### Bước 4. Use case và API · Agent
`PublishProduct`, `UnpublishProduct`, `DeleteDraft`, `CreateProduct` kiểm bản sao, ba điểm cuối, `PublishNotAllowedException` và advice, `ProductResponse.publishedAt` (KT-04, KT-06 phần không sự kiện).

### Bước 5. Sự kiện · Agent + Người rà
`ProductSnapshotEvent`, `SellableUnitChangedEvent`, `ProductEventRecorder` và nối vào tám use case (KT-03, KT-06 phần hộp thư).

### Bước 6. Đua nhau, phá thử · Người
KT-08; phá thử theo `tech.md` T7.

### Bước 7. Chạy thật · Người
KT-09: Postgres, Kafka, Connect (connector `catalog`), MinIO, ứng dụng, token ký thật; gửi sự kiện duyệt vào topic, tạo, đăng, sửa, gỡ, đăng lại, đọc hai topic. Gỡ sạch.

### Bước 8. Đóng · Người, Agent
`deferred.md` (đã viết); `service.md`, `data.md`, `api.yaml`, `event-catalog.md` chuyển nhãn; cột "Triển khai"; `00-features.md` đóng 09 kèm bài học.

## Kết quả (`2026-11-06`)

- **Test:** catalog 569 ca (41 lớp) và `upload-starter` 43 ca xanh, 0 lỗi (số cuối `2026-11-06`). Mới ở 09: `PublishUseCaseTest` 11, `ProductEventRecorderTest` 8, `SellerDecisionListenerKafkaTest` 4 (Kafka embedded + Postgres), `ProductPublishApiTest` 14 (gồm 20 luồng đăng, đăng đua với sửa và bỏ ảnh, vòng đời sự kiện trong hộp thư), `PublishSchemaConstraintTest` 12.
- **Phá thử:** 21 lỗi cố ý, 20 đỏ, 1 lỗi tương đương (E3: bỏ điều kiện `changedUnits` rỗng, vòng lặp bên dưới đã lọc theo `changedUnits`). Một lỗi sống sót lượt đầu là lỗ hổng thật của test: bỏ kiểm phiên bản ở câu upsert `seller_state` mà test vẫn xanh vì sự kiện cũ hơn đến *trước* bản trùng; sửa thứ tự gửi (sự kiện cũ đến sau cùng) thì lỗi đỏ, ca đối chứng xanh.
- **Chạy thật KT-09:** 26/26 trên ngăn xếp cách ly (Postgres, Kafka, Connect với connector `catalog`, ứng dụng, token ký thật): sự kiện duyệt gửi vào topic thật làm bản sao thành `APPROVED:3`; sự kiện cũ hơn không kéo lùi; sự kiện hỏng vào `catalog-service.dlq`; một vòng đời ra đúng 5 bản chụp và 3 sự kiện đơn vị, khóa là mã sản phẩm, `version` đơn điệu, đơn vị không có giá, bản chụp có khóa đối tượng ảnh và không có SKU; nháp và sửa khi đã gỡ không ra gì; 20 luồng đăng ra đúng 1 lần. Nhật ký không có 500; một dòng `ERROR` là log chủ đích khi đưa sự kiện hỏng vào hàng chờ lỗi. Ngăn xếp gỡ sạch. Không chạm `infra-*`, Kafka dev, MinIO dev, `catalog_db` dev.
- **Bắt được khi rà diff và chạy test:** (1) `PublishProduct` trả `version` trước khi lưu thay vì `nextVersion()`; (2) agy đoán sai gói `SellerState` (`domain.seller` thay vì `domain.product`) và tên API trong `PublishUseCaseTest` (`SellerState.reconstitute`, `confirmImage`, `NO_IMAGES`...): đã bỏ bản test sai, tự viết lại với chữ ký thật; (3) `GetMyProduct` và `UpdateProduct` tự dựng `ProductAssembler` nên phải truyền thêm kho trạng thái người bán; (4) `ProductAssembler` chưa là bean Spring nên mọi `@SpringBootTest` hỏng ngữ cảnh; (5) `product_image` không thêm cột `published_at` nên helper của `ProductSchemaConstraintTest` vi phạm `ck_product_published_at`; (6) `VariantApiTest.noSuchRoutes` coi `DELETE` sản phẩm là không có đường: nay có (xóa nháp).

## Chỗ lệch đã biết

- `catalog_db` dev **chưa áp `V4`** (ứng dụng áp khi người dùng khởi động lần tới; cần Kafka dev đang chạy để bộ nhận hoạt động, thiếu Kafka thì bộ nhận chỉ báo cảnh báo kết nối) và **chưa có connector `catalog` đăng ký trên Connect dev**: chưa có sự kiện nào ra khỏi dev đến khi đăng ký bằng `infra/debezium/outbox-connector.sh register catalog`.
- Môi trường dev có người bán đã duyệt từ trước: nạp bản sao bằng `infra/seed/catalog-seller-state.sql` (xem `deferred.md`).
- Bên nhận `EVT-CAT-01`, `EVT-CAT-02` (Tìm kiếm, Tồn kho) chưa có; `inventory-service` còn nghe các chủ đề cũ `catalog.variant.*`, `catalog.product.*` (viết lại ở C2a, ADR-0014 hệ quả).

## Bài học

- **Giao `agy` kèm danh sách API thật:** hai lần agy viết test dựa trên API đoán (tên lớp, gói, phương thức) dù đặc tả đã nêu "chỉ dùng cái có thật". Đặc tả cho test nên kèm chữ ký thật hoặc đoạn `setUp` cần sao chép nguyên văn; việc test dựng nhiều dữ liệu miền thì Người viết trực tiếp nhanh hơn lặp lại.
- **Test chứng minh thứ tự phải đặt sự kiện "xấu" ở cuối:** sự kiện cũ hơn gửi trước bản trùng không phân biệt được "bỏ qua" với "ghi đè rồi bản trùng ghi lại".
- **Đổi chữ ký hàm dùng chung kéo theo nhiều nơi tự dựng:** `ProductAssembler` được ba use case `new` nội bộ; thêm tham số buộc sửa từng nơi và test. Làm nó thành bean và tiêm vào sẽ gọn hơn (việc dọn dẹp sau).
- **`pkill` agy không đủ:** tiến trình agy treo sau khi hủy giữ worktree ("Device or resource busy"); dùng `taskkill` theo PID Windows rồi `git worktree prune`.

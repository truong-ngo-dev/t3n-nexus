# Feature 02: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC nằm ở [ADR-0008](../../../global/2.architecture/adr/0008-event-outbox-and-consumption.md) (hộp thư, nhận event) và [ADR-0009](../../../global/2.architecture/adr/0009-audit-log-immutability.md) (bất biến ở lưu trữ, Accepted). Nhãn: "Đã làm" là đã có mã và kiểm chứng ở bước ghi trong ngoặc của `plan.md`; "Hoãn" là phần có chỗ trong phạm vi nhưng làm sau (`deferred.md`). `Q-AUD-01` đã chốt `2026-10-24`: bản ghi không hết hạn ở C1a.

## T1. Dịch vụ `audit-service` · Đã làm (bước 1 đến 8)

- **Quyết định:** một dịch vụ Spring Boot mới `services/audit-service`, gói gốc `vn.t3nexus.audit`, bốn tầng theo `ddd-structure.md` (miền, ứng dụng, hạ tầng, trình bày). Servlet (không reactive), JPA và Flyway trên Postgres 16 riêng của dịch vụ, bộ nhận event dùng `event-consumer-starter`. Không Redis, không phát event, không gọi BC khác (`service.md` mục 1, 8).
- **Kết nối cơ sở dữ liệu:** hai bộ thông tin (ADR-0009): `spring.flyway.*` là tài khoản chủ bảng chạy migration; `spring.datasource.*` là tài khoản chỉ `INSERT`/`SELECT` chạy dịch vụ. Mật khẩu đọc từ biến môi trường.
- **Loại:** nhét vào một dịch vụ có sẵn (Người bán hay Danh mục): ranh giới sai (`analysis.md` mục 2), ai sửa dữ liệu thì sửa được dấu vết.

## T2. Hợp đồng bản ghi `AuditActionRecorded` · Đã làm (bước 2)

Nằm trong `payload` của khung thông điệp chung (ADR-0008, `tech.md` T4 của feature 01). Nhật ký sở hữu hợp đồng (OHS-PL).

| Trường | Kiểu và ràng buộc | Nguồn trong khung |
|---|---|---|
| Mã sự kiện nguồn | Chuỗi 1 đến 100 ký tự; là `eventId` của khung; duy nhất ở Nhật ký (INV-AUD-02) | `eventId` |
| Thời điểm xảy ra | Thời điểm UTC, giờ BC phát (INV-AUD-03) | `occurredAt` |
| Mã truy vết | Tùy chọn | `traceId` |
| Phiên bản hợp đồng | Số nguyên từ 1 | `schemaVersion` |
| `sourceService` | Chuỗi 1 đến 64 ký tự `[a-z0-9-]` | `payload` |
| `actor.accountId` | UUID; bắt buộc trừ khi `role` là `SYSTEM` | `payload` |
| `actor.role` | `^[A-Z][A-Z0-9_]{0,31}$` (`ADMIN`, `USER`, `SYSTEM`, ...); bản chụp lúc làm (INV-AUD-05) | `payload` |
| `action` | `^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$`, tối đa 100 ký tự: có ít nhất hai đoạn `ĐỐI_TƯỢNG_HÀNH_ĐỘNG` (INV-AUD-07); không kiểm ý nghĩa | `payload` |
| `target.type` | `^[A-Z][A-Z0-9_]{0,63}$` | `payload` |
| `target.id` | Chuỗi 1 đến 100 ký tự, không toàn khoảng trắng; **chỉ mã** | `payload` |
| `details` | Tùy chọn; phẳng, giới hạn ở T5 | `payload` |

Khung đặt `aggregateType` = `target.type` và `aggregateId` = `target.id` để khóa thông điệp là mã đối tượng (`service.md` mục 5). Bên nhận **chỉ đọc `payload.target`**, không đọc hai trường kia của khung. Chủ đề chung `audit.action.recorded`, loại `AuditActionRecorded`. Tiến hoá chỉ thêm trường; đổi nghĩa thì tăng `schemaVersion`.

**Loại:** bản ghi do Nhật ký tự suy từ sự kiện nghiệp vụ của BC khác (`analysis.md` mục 5.0); mỗi BC một chủ đề (Nhật ký phải biết thêm chủ đề mỗi lần có BC mới).

## T3. Thư viện bên phát `audit-events` · Đã làm (bước 2)

- **Quyết định:** mô-đun mới `services/libs/audit-events`, gói `vn.t3nexus.lib.audit`, gồm:
  - `AuditContract`: tên chủ đề, các quy tắc định dạng ở T2, giới hạn ở T5. **Một nơi duy nhất** cho cả bên phát và bên nhận.
  - `AuditActionRecorded` (cài `DomainEvent`), `AuditActor`, `AuditTarget`.
  - `AuditDetailsPolicy`: hàm thuần kiểm dữ kiện bổ sung (T5), dùng hai chế độ: nghiêm (bên phát: vi phạm thì ném lỗi lập trình) và nới (bên nhận: bỏ phần vi phạm, trả danh sách khóa bị bỏ để cảnh báo).
  - `AuditActionCatalog` và `AuditActionDeclaration`: BC phát khai báo các hành động của mình và khóa dữ kiện được phép của từng hành động (`analysis.md` mục 5.1).
  - `AuditRecordReader` và `AuditRecord`: bên nhận đọc khung đã giải mã thành bản ghi, kiểm đủ trường theo T2, lọc dữ kiện bằng chế độ nới; lỗi ném `InvalidAuditRecordException` nêu **tên trường**, không nêu giá trị. Đặt cạnh bên dựng để hợp đồng khép hai chiều ở một nơi.
  - `AuditRecorder`: `record(action, actor, target, details)`; kiểm hành động và khóa đã khai báo, dựng `AuditActionRecorded`, gọi `OutboxEventStore.store` (bắt buộc có giao dịch, ADR-0008). Cấu hình tự động chỉ bật khi có bean `OutboxEventStore`.
- **Phụ thuộc:** `common-domain`, `common-events` (dùng lại `SensitiveFieldPolicy.strict()` làm danh sách tên khóa cấm, một nguồn duy nhất). `outbox-starter` là phụ thuộc **tùy chọn**: `audit-service` dùng thư viện này cho hợp đồng và chính sách mà không kéo theo hộp thư (nếu kéo, nó sẽ đòi bảng `outbox_events` mà Nhật ký không có).
- **Vì sao:** BC phát chỉ gọi một hàm; hợp đồng và danh sách khóa cấm không lệch giữa hai bên; thêm hành động mới ở BC phát là việc của BC đó (khai báo trong catalog của nó), Nhật ký không đổi.
- **Loại:** mỗi BC tự dựng JSON (hợp đồng lệch nhau, quên kiểm khóa); đặt hợp đồng trong `common-events` (làm khung chung biết về nghiệp vụ Nhật ký).

## T4. Nhận và ghi (`CMD-AUD-01`) · Đã làm (bước 5)

- **Bộ nhận:** `AuditActionRecordedListener` ở hạ tầng, `@KafkaListener` trên chủ đề `audit.action.recorded` (thuộc tính `app.audit.topic`), nhóm tiêu thụ `audit-service`; gọi `EventEnvelopeConsumer.consume` rồi use case `RecordAuditEntry` (ADR-0008: ack sau khi use case commit).
- **Use case, một giao dịch:** (1) đọc khung bằng `AuditRecordReader` (kiểm hợp đồng theo T2: thiếu trường, sai kiểu, `action` sai định dạng, `schemaVersion` < 1): `InvalidAuditRecordException` được đổi thành `NonRetryableEventException` nêu **tên trường**, không chép nội dung → hàng chờ lỗi ngay (mô-đun `audit-events` không phụ thuộc starter nhận); (2) dữ kiện đã được reader lọc ở chế độ nới, use case ghi cảnh báo cho mỗi khóa bị bỏ; (3) dựng `AuditEntry` (mã UUID v7 từ cổng sinh mã, `recordedAt` từ đồng hồ của dịch vụ cắt về micro giây); (4) kho thêm bản ghi bằng `INSERT ... ON CONFLICT (source_event_id) DO NOTHING`, trả về có thêm hay không; trùng thì ghi log mức INFO và đếm, không lỗi.
- **Lỗi tạm** của cơ sở dữ liệu lan ra khỏi use case và do bộ xử lý lỗi của starter nhận thử lại (1 giây nhân đôi, 3 lần, rồi hàng chờ lỗi).
- **Khử trùng** theo kiểu 1 của `idempotency-layering.md` (ràng buộc duy nhất trên mã sự kiện nguồn); thêm một dòng cho Nhật ký vào bảng áp dụng của tài liệu đó khi đóng feature. Test dùng `DeliveryHarness` của starter nhận.
- **Cổng ghi và cổng đọc tách nhau (chỉnh `2026-10-26`):** miền có `AuditEntryRepository`, kế thừa `Repository<AuditEntry, AuditEntryId>` của `common-domain` (`AuditEntry` là `AbstractAggregateRoot` với mã `AuditEntryId`, ADR-0001): `findById` kế thừa, `save` chỉ gọi `insertIfAbsent`, `delete` ném `UnsupportedOperationException` (INV-AUD-01; phương thức tồn tại vì giao diện gốc đòi, nhưng không bao giờ chạy được); tìm kiếm có bộ lọc đi qua `AuditEntryQuery` (tầng ứng dụng, cài bởi `AuditEntryQueryAdapter`) cùng với `AuditEntryFilter` và `AuditCursor`, theo quy ước tách ghi và đọc của `ddd-structure.md`.
- **Kho dùng JDBC thuần** (`NamedParameterJdbcTemplate`), không JPA entity: bảng chỉ thêm và đọc, câu `INSERT ... ON CONFLICT` và so sánh cặp ở T7 viết thẳng bằng SQL, còn ánh xạ entity kéo theo cơ chế cập nhật mà bảng không có.
- **Loại:** kiểm trùng bằng `SELECT` trước rồi `INSERT` (đua giữa hai luồng); bắt `DataIntegrityViolationException` (làm hỏng giao dịch của Postgres, phải rollback cả use case); JPA entity cho bảng bất biến.

## T5. Dữ kiện bổ sung (`AuditDetailsPolicy`) · Đã làm (bước 2)

| Quy tắc | Giá trị | Vi phạm |
|---|---|---|
| Số khóa | Tối đa 10 | Giữ 10 khóa đầu theo thứ tự tên tăng dần (xác định), bỏ phần còn lại |
| Tên khóa | `^[a-z][A-Za-z0-9]{0,39}$` | Bỏ khóa |
| Tên khóa thuộc loại định danh | So khớp tên đầy đủ, không phân biệt hoa thường và dấu phân cách, theo `SensitiveFieldPolicy.strict()` (mật khẩu, token, email, số điện thoại, địa chỉ, số căn cước, mã số thuế, ...) | Bỏ khóa |
| Kiểu giá trị | Chuỗi, số hữu hạn, đúng/sai. Đối tượng lồng nhau và mảng không được | Bỏ khóa |
| Độ dài giá trị chuỗi | Tối đa 500 ký tự (đủ cho lý do từ chối) | Bỏ khóa (không cắt ngắn: cắt làm đổi chứng cứ) |
| Giá trị rỗng (`null`) | Coi như vắng khóa | Không cảnh báo |

Bên nhận ở chế độ nới: bản ghi vẫn lưu, mỗi khóa bị bỏ có một dòng WARN `AUDIT_DETAILS_KEY_DROPPED` kèm **tên khóa và mã lý do** (`FORBIDDEN_NAME`, `BAD_KEY`, `NOT_FLAT`, `TOO_LONG`, `TOO_MANY`), không giá trị. Bên phát ở chế độ nghiêm: bất kỳ vi phạm nào ném lỗi lập trình trước khi ghi hộp thư, nên đường bình thường không bao giờ làm bên nhận phải bỏ khóa; chế độ nới là lớp phòng thủ thứ hai.

## T6. Lưu trữ · Đã làm (bước 4)

Bảng `audit_entries` (migration V1, tài khoản chủ bảng):

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `uuid` | Khóa chính; UUID v7 (ADR-0001) |
| `source_event_id` | `varchar(100)` | Không rỗng, **duy nhất** (INV-AUD-02) |
| `occurred_at` | `timestamptz` | Không rỗng; cắt về micro giây trước khi ghi |
| `recorded_at` | `timestamptz` | Không rỗng |
| `actor_account_id` | `uuid` | Rỗng chỉ khi `actor_role = 'SYSTEM'` (ràng buộc `CHECK`) |
| `actor_role` | `varchar(32)` | Không rỗng |
| `action` | `varchar(100)` | Không rỗng |
| `target_type` | `varchar(64)` | Không rỗng |
| `target_id` | `varchar(100)` | Không rỗng |
| `details` | `jsonb` | Không rỗng, mặc định `{}`; không lọc theo nội dung ở C1a |
| `source_service` | `varchar(64)` | Không rỗng |
| `trace_id` | `varchar(64)` | Tùy chọn |

**Chỉ mục** (bốn, `service.md` mục 7): `(occurred_at DESC, id DESC)`; `(actor_account_id, occurred_at DESC, id DESC)`; `(target_type, target_id, occurred_at DESC, id DESC)`; `(action, occurred_at DESC, id DESC)`. Cột `occurred_at` cắt về micro giây vì con trỏ phân trang (T7) so sánh chính xác cặp `(occurred_at, id)`; Java lưu nano giây, Postgres lưu micro giây, không cắt thì con trỏ lệch.

**Bất biến (ADR-0009):** hàm trigger `audit_entries_reject_change()` ném lỗi; trigger `BEFORE UPDATE OR DELETE` theo hàng và `BEFORE TRUNCATE` theo câu lệnh; `REVOKE ALL ... FROM PUBLIC`; `GRANT INSERT, SELECT` cho tài khoản dịch vụ. Tài khoản dịch vụ do script khởi tạo cơ sở dữ liệu tạo (T10), migration chỉ cấp quyền cho nó.

**Tăng trưởng:** bảng chỉ tăng; phân vùng theo tháng và lưu lạnh là điểm nối chưa làm (`analysis.md` mục 12, `Q-AUD-01`).

**Loại:** cột `jsonb` được lập chỉ mục để lọc theo dữ kiện (mở rộng bề mặt tra cứu khi chưa có nhu cầu); lưu `source_event_id` kiểu `uuid` (mã event của hộp thư là chuỗi, không bảo đảm là UUID với mọi bên phát).

## T7. Tra cứu và API · Đã làm (bước 6)

| Điểm cuối | Mô tả |
|---|---|
| `GET /api/audit/entries` | Danh sách, mới nhất trước, phân trang theo con trỏ |
| `GET /api/audit/entries/{id}` | Chi tiết một bản ghi |

Không có điểm cuối nào khác; mọi phương thức ngoài `GET` không được ánh xạ (INV-AUD-01).

**Tham số của danh sách** (đều tùy chọn, kết hợp bằng VÀ): `from` (thời điểm, **gồm**), `to` (**không gồm**), `actorId` (UUID), `action` (khớp đúng), `targetType` và `targetId` (`targetId` chỉ dùng kèm `targetType`, khớp đúng), `limit` (mặc định 50, tối đa 200), `cursor`.

**Trả về:** bọc `ApiResponse` chung của `common-web` (`success`, `data`, `message`, `errors`, `code`, `traceId`); `data` là `{ "items": [...], "nextCursor": "..." | null }`. Mỗi phần tử danh sách: `id`, `occurredAt`, `recordedAt`, `actor {accountId, role}`, `action`, `target {type, id}`, `sourceService`. **Chi tiết** thêm `sourceEventId`, `details`, `traceId`. Danh sách bỏ `details` và `traceId` cho nhẹ (RM-AUD-02 là chỗ xem đầy đủ).

**Sắp xếp và con trỏ:** `ORDER BY occurred_at DESC, id DESC`. Con trỏ là chuỗi không trong suốt (base64url của `<occurred_at tính bằng micro giây>.<id>`). Trang sau là `WHERE (occurred_at, id) < (:t, :id)` (so sánh cặp, dùng được chỉ mục giảm dần), lấy `limit + 1` bản ghi để biết còn trang kế. Không dùng độ lệch (offset): bản ghi chèn thêm giữa các lần gọi không làm lặp hay sót; con trỏ không gắn với bộ lọc (lọc đổi giữa chừng là lỗi của người gọi, kết quả vẫn hợp lệ).

**Lỗi (định dạng lỗi chung của `common-web`, trường `code` là tên hằng của `AuditErrorCode`):** 400 `AUDIT_LIMIT_INVALID` (`limit` ngoài `[1, 200]`), `AUDIT_CURSOR_INVALID` (`cursor` không giải mã được), `AUDIT_TIME_RANGE_INVALID` (`from` sau `to`), `AUDIT_FILTER_INVALID` (`targetId` không kèm `targetType`), `AUDIT_PARAMETER_INVALID` (tham số sai kiểu: `limit=abc`, `actorId` không phải UUID, `from` sai định dạng; do một bộ xử lý riêng của dịch vụ, vì lỗi kiểu tham số của Spring nếu không bắt sẽ thành 500). Không có kết quả thì trả danh sách rỗng, không phải lỗi. Mã bản ghi không có: 404 `AUDIT_ENTRY_NOT_FOUND`. Mã số `50001` đến `50006`.

**Loại:** phân trang theo độ lệch (`OFFSET`: chậm dần khi sâu, lặp và sót khi có bản ghi mới); trả `details` trong danh sách (tăng kích thước khi chưa cần); trả tổng số bản ghi (cần đếm cả bảng).

## T8. Quyền và kiểm token · Hoãn (người chốt `2026-10-24`)

- **Quyết định:** ở feature này `audit-service` **không kiểm token và không kiểm vai**: không `@PreAuthorize`, không `security-starter`, không phụ thuộc Spring Security. `INV-AUD-06` (chỉ Quản trị tra cứu) và phép kiểm quyền (trước đây KT-13) hoãn tới khi có mô-đun phân quyền; test quyền viết sau khi mô-đun đó có.
- **Rủi ro chấp nhận:** ai gọi được cổng của dịch vụ thì đọc được nhật ký. Giảm thiểu: dịch vụ chỉ chạy trong mạng dev với dữ liệu thử, **chưa có route ở cổng truy cập** (feature 10 thêm route sau khi có kiểm quyền); không đưa ra ngoài dev khi chưa có phân quyền.
- **Điểm nối:** use case, kho và bộ nhận không biết người gọi, API không nhận mã người gọi, nên thêm phân quyền sau chỉ là thêm cấu hình bảo mật và chú thích ở lớp trình bày. Khi làm: giải mã token chỉ cần thuộc tính chuẩn `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`; phần phải viết là bộ đổi claim `role` thành quyền `ROLE_<role>` (Spring chỉ đổi `scope`) và chặn mặc định; dung sai đồng hồ 5 giây và kiểm `iss` là tuỳ chọn siết chặt (mặc định của Spring: 60 giây, không kiểm `iss`). `security-starter` (phần hoãn của C0) chưa làm.
- **Loại:** làm `security-starter` ngay ở feature này (chưa có nhu cầu kiểm chứng quyền ở C1a, người chọn hoãn).

## T9. Quan sát · Đã làm (bước 5 và 6)

| Mã log | Mức | Khi nào | Nội dung |
|---|---|---|---|
| `AUDIT_ENTRY_RECORDED` | INFO | Thêm được bản ghi | mã bản ghi, hành động, `sourceService` (không có dữ kiện bổ sung) |
| `AUDIT_DUPLICATE_IGNORED` | INFO | Nhận lặp | mã sự kiện nguồn |
| `AUDIT_DETAILS_KEY_DROPPED` | WARN | Bỏ một khóa dữ kiện | tên khóa, mã lý do (không giá trị) |
| `EVENT_DEAD_LETTERED` | ERROR | Thông điệp vào hàng chờ lỗi (starter nhận, ADR-0008) | chủ đề, mã event, tên loại lỗi |

Bộ đếm (Micrometer, qua `spring-boot-starter-actuator`): `audit.entries.recorded{sourceService}`, `audit.entries.duplicate`, `audit.details.keys_dropped{reason}`. Mã truy vết của thông điệp được đưa vào ngữ cảnh log bởi starter nhận. Log không bao giờ chứa `details`.

## T10. Hạ tầng dev · Đã làm (bước 4 và 8)

- **Cơ sở dữ liệu:** thêm `postgres-audit` vào `infra/docker-compose.yml` (Postgres 16, cổng `5440`, **không cần `wal_level=logical`** vì Nhật ký không phát event). Tài khoản quản trị của bộ chứa chỉ để chạy tệp khởi tạo `infra/audit/init-audit-roles.sql` (`docker-entrypoint-initdb.d`), tệp tạo `audit_owner` (chủ cơ sở dữ liệu, chạy migration, không phải superuser) và `audit_app` (chạy dịch vụ). Test dùng đúng tệp này nên dev và test không lệch. Mật khẩu trong tệp chỉ dùng cho dev và test. Cổng HTTP của dịch vụ: `8010`.
- **Chủ đề Kafka:** `audit.action.recorded` do cơ chế tự tạo chủ đề của môi trường dev tạo lần đầu có thông điệp; không tạo tay.
- **Cổng truy cập, route, giao diện:** ngoài phạm vi (feature 10, 12); kiểm thử gọi thẳng dịch vụ.
- **Bên phát thử cho phần D:** một ứng dụng thử tạm (một điểm cuối gọi `AuditRecorder` trong giao dịch thật, hộp thư và Debezium như feature 01) thay cho BC thật; dựng trong stack Docker cô lập, không chạm Kafka và Postgres dev, đã gỡ sau khi đo. Container `postgres-audit` của compose dev đã khai báo và kiểm cú pháp, nhưng chưa khởi động.

## T11. Kiểm chứng · Đã làm (bước 3 đến 8), trừ KT-13 hoãn

Test viết từ bảng dưới, đỏ trước, phá thử sau.

| KT | Điều kiểm | Cách |
|---|---|---|
| KT-01 | Ghi hợp lệ: đủ trường lưu đúng, `recordedAt` do dịch vụ đặt | Tích hợp (Postgres Testcontainers, qua use case) |
| KT-02 | Nhận lặp: hai lần tuần tự và 20 luồng song song cùng mã sự kiện nguồn cho đúng một bản ghi | Tích hợp; bộ khung `DeliveryHarness` |
| KT-03 | Thiếu trường, sai kiểu, hành động sai định dạng, `schemaVersion` < 1: không lưu, vào `audit-service.dlq` ngay không thử lại, thông điệp kế vẫn lưu | Tích hợp (Kafka trong tiến trình + Postgres) |
| KT-04 | Hành động lạ đúng định dạng được lưu | Tích hợp |
| KT-05 | Dữ kiện: mọi tên trong danh sách `strict` bị bỏ, lồng nhau, quá 10 khóa, khóa hỏng, giá trị quá dài; cảnh báo chỉ có tên khóa và mã lý do; chế độ nghiêm của bên phát ném lỗi | Đơn vị |
| KT-06 | Người làm là bản chụp (lưu đúng vai và mã từ event, không tra lại); `SYSTEM` không cần mã tài khoản, vai khác thiếu mã bị từ chối | Đơn vị, tích hợp |
| KT-07 | Bất biến: tài khoản dịch vụ bị từ chối `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER TABLE` (quyền); tài khoản chủ bảng bị từ chối `UPDATE`, `DELETE`, `TRUNCATE` (trigger); tài khoản dịch vụ vẫn `INSERT` và `SELECT`; dịch vụ khởi động với hai tài khoản | Tích hợp (Postgres Testcontainers, hai tài khoản) |
| KT-08 | Bên phát: rollback không để lại hàng hộp thư; ngoài giao dịch bị từ chối; hành động hay khóa chưa khai báo bị từ chối; hàng hộp thư đọc lại được bằng chính bộ nhận của Nhật ký (hợp đồng khép hai chiều) | Tích hợp (Postgres Testcontainers) |
| KT-09 | Lọc: từng điều kiện và kết hợp; không kết quả thì rỗng; `from` gồm, `to` không gồm | Tích hợp |
| KT-10 | Thứ tự và phân trang: ba bản ghi cùng thời điểm xảy ra; duyệt hết bằng cỡ trang 1, 2, 7 ra đúng tập đầy đủ, không lặp, không sót; chèn bản ghi mới giữa các lần gọi không làm lặp hay sót | Tích hợp |
| KT-11 | Giới hạn: `limit` ngoài `[1, 200]`, `cursor` hỏng, `from` sau `to`, `actorId` hỏng, `targetId` thiếu `targetType` đều 400 | Tích hợp (MockMvc) |
| KT-12 | Chi tiết đủ trường kể cả `details`, `traceId`; mã không có thì 404 | Tích hợp |
| KT-13 | **Hoãn** (INV-AUD-06): quyền theo vai, viết khi có mô-đun phân quyền | — |
| KT-14 | Không có đường ghi: ngoài `GET` không có ánh xạ nào; `POST`, `PUT`, `PATCH`, `DELETE` bị từ chối | Tích hợp |
| KT-15 | Chuỗi thật: hộp thư của bên phát thử → Debezium → Kafka → Nhật ký → cơ sở dữ liệu → API; đo độ trễ; khởi động lại Nhật ký giữa chừng không mất; phá thử "ack trước commit" với handler thật (chưa kiểm ở feature 01) | Chạy thật (stack cô lập) |

**Phá thử** (mỗi cái phải làm đúng KT tương ứng đỏ): bỏ `ON CONFLICT` (KT-02); bỏ trigger (KT-07, tài khoản chủ bảng); cấp thêm `UPDATE` cho tài khoản dịch vụ (KT-07); bỏ tie-break `id` (KT-10); đổi `<` thành `<=` ở điều kiện con trỏ (KT-10); bỏ lọc tên khóa cấm (KT-05); bỏ cắt micro giây (KT-10).

Đối chiếu đường không suôn sẻ ở `flow.md` mục 4: E1 → KT-15 và feature 01; E2 → KT-02; E3 → KT-03; E4 → KT-04; E5, E6 → KT-05; E7 → KT-01; E8 → KT-07, 14; E9 → KT-10; E10 → KT-06; E11 → test thử lại của starter nhận (feature 01) và KT-15; E12 → KT-10, 11.

## T12. Số đo · Đã làm (bước 8)

Khối lượng thấp (giả định vài trăm bản ghi mỗi ngày, `analysis.md` mục 14), không nằm trên đường nóng, nên chỉ có hai số đo nhẹ. Đo `2026-10-25` trên stack cô lập (Postgres 16, Kafka 3.9, Debezium qua Kafka Connect; bên phát là ứng dụng thử gọi `AuditRecorder` thật, Nhật ký chạy thật bằng `audit_app`).

| Số đo | Kết quả |
|---|---|
| Độ trễ ghi hộp thư → bản ghi nằm trong cơ sở dữ liệu Nhật ký | 0,92 đến 0,99 giây (5 lần liên tiếp, đã gồm bước hỏi lại 0,2 giây); lần đầu sau khi mọi thứ vừa khởi động khoảng 2,9 giây |
| Bảng 100.356 dòng (60 MB cả chỉ mục), trang đầu 50 mục | 0,20 giây ở lần gọi đầu (chưa khởi động nóng); các lần sau 13 đến 25 ms |
| Trang giữa (sau dòng thứ 50.000), con trỏ | 25 ms |
| Lọc theo người làm / đối tượng / hành động | 20 / 15 / 13 ms |
| `EXPLAIN (ANALYZE)` của năm dạng truy vấn | Cả năm dùng `Index Scan`, không có quét tuần tự; thời gian thực thi 0,10 đến 0,28 ms |

Hành động chiếm 20% bảng dùng chỉ mục `occurred` rồi lọc (0,28 ms); hành động hiếm dùng `ix_audit_entries_action` (3 ms, tên không tồn tại). Con số này chỉ nói bảng cỡ 100 nghìn dòng; chưa đo cỡ lớn hơn.

## Câu hỏi cần người chốt

Đã chốt `2026-10-24`:

| # | Câu hỏi | Kết luận |
|---|---|---|
| Q1 | Kiểm token ở `audit-service` (T8) | **Bỏ qua phân quyền ở feature này** (người chốt); `security-starter` và kiểm vai làm khi có mô-đun phân quyền |
| Q2 | Hợp đồng, chính sách dữ kiện và bộ ghi của bên phát ở mô-đun mới `audit-events` (T3) | Theo đề xuất: mô-đun mới `audit-events`, `outbox-starter` là phụ thuộc tùy chọn |
| Q3 | Giới hạn dữ kiện bổ sung (T5) | Theo đề xuất, **giữ tối đa 10 khóa** (người chốt); tên khóa `^[a-z][A-Za-z0-9]{0,39}$`; giá trị chuỗi tối đa 500 ký tự; kiểu chuỗi, số, đúng/sai |
| Q4 | Danh sách có trả `details` và `traceId` không, cỡ trang (T7) | Theo đề xuất: danh sách bỏ hai trường đó, chi tiết có đủ; cỡ trang mặc định 50, tối đa 200 |

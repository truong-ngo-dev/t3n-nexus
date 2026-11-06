# Feature 01: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC nằm ở [ADR-0008](../../../global/2.architecture/adr/0008-event-outbox-and-consumption.md) (Accepted `2026-10-24`); file này ghi chi tiết riêng của feature và các câu hỏi đã chốt. Nhãn: "Đã làm" là phần đã có mã và đã kiểm (bước 3 đến 7 của `plan.md`); nội dung gốc là đề xuất người đã chấp nhận ở mục "Câu hỏi" cuối file (`2026-10-23`).

## Kết quả thử (bước 2 của `plan.md`, `2026-10-24`)

Chạy bằng một stack thử riêng (Postgres 16 `wal_level=logical`, Kafka 3.9 một nút, Kafka Connect với Debezium 2.7.3) dựng và gỡ hoàn toàn bằng Docker, **không chạm Kafka và Postgres dev**; bảng đúng hình T2; ba connector thử đặt cấu hình như cấu hình mẫu hiện có, chỉ khác điều được nêu. Chưa viết thư viện.

| Ca | Kết quả |
|---|---|
| 1. Hàng chèn sau khi connector chạy (cột `payload` dạng văn bản chứa JSON) | Ra bus đúng: chủ đề lấy từ `routing_key`, khóa là `aggregate_id`, giá trị là JSON đã mở rộng (tiếng Việt có dấu giữ nguyên), tiêu đề `id` là `event_id` |
| 2. Giao dịch rollback | Không có thông điệp |
| 3. Hai event cùng giao dịch, cùng thực thể, **hai chủ đề khác nhau** | Mỗi event vào chủ đề của nó; **không có thứ tự giữa hai chủ đề** (Kafka chỉ bảo đảm trong một phân vùng của một chủ đề) |
| 4. Hàng ghi **trước** khi connector tồn tại, `snapshot.mode=no_data` | **Bị sót**: không bao giờ ra bus, chủ đề của nó không được tạo |
| 5. Cùng ca 4, `snapshot.mode=initial` | Hàng cũ ra bus, nhưng **mọi hàng đang có trong bảng** ra lại (kể cả hàng đã gửi trước đó); thứ tự theo lần quét bảng, không theo thứ tự ghi. Với hộp thư giữ hàng đến C2b, tạo lại connector bằng `initial` phát lại cả lịch sử |
| 6. Chuyển đổi dữ liệu mặc định của Kafka Connect | **Mọi thông điệp bị bọc** thành `{"schema":..., "payload":...}` cho cả khóa và giá trị, vì cấu hình mức worker là `schemas.enable=true` (biến môi trường cùng ý nghĩa khai ở docker-compose dev không có tác dụng). Đặt `key.converter.schemas.enable=false` và `value.converter.schemas.enable=false` ở connector **chỉ có tác dụng khi connector cũng khai tên lớp** `key.converter` và `value.converter`; có đủ thì khóa là `"agg-4"` và giá trị là JSON trần |
| 7. Hai connector cùng `topic.prefix` | Connector thứ hai kẹt ở bước quét (đăng ký chỉ số JMX trùng tên, thử 12 lần mỗi 5 giây), khe sao chép của nó không hoạt động; đặt `topic.prefix` khác nhau thì chạy ngay. Ghi nhận: mỗi connector một `topic.prefix` và một `slot.name` riêng |
| 8. Xóa connector | Khe sao chép **không bị xóa theo**; phải xóa tay (`pg_drop_replication_slot`), nếu không Postgres giữ nhật ký giao dịch mãi |
| 9. Ràng buộc duy nhất `event_id` | Chèn lần hai cùng `event_id` bị từ chối |
| 10. Cách đăng ký connector hiện có | Hướng dẫn trong `infra/README.md` dùng `POST /connectors` từng tệp bằng tay: chạy lần hai báo lỗi đã tồn tại; không có script |

**Kết luận từ ca thử:**
- Ba điểm "Chưa kiểm" của T1 đã có kết luận: cột văn bản mở rộng đúng (ca 1); khóa và chủ đề đúng (ca 1); hàng ghi trước connector bị sót với `no_data` và phát lại cả bảng với `initial` (ca 4, 5). **Cách an toàn: đăng ký connector trước khi dịch vụ phát đầu tiên ghi hàng, dùng `no_data`**; tạo lại connector khi bảng đã có dữ liệu phải chấp nhận phát lại (bên nhận khử trùng) hoặc đặt lại vị trí có chủ đích.
- Bảo đảm thứ tự chỉ trong một chủ đề (ca 3): sửa T8.
- Cấu hình connector mẫu phải khai cả tên lớp converter (ca 6): sửa T11.
- Script đăng ký phải có thêm bước dọn khe sao chép khi gỡ (ca 8) và kiểm `topic.prefix` không trùng (ca 7).

## Kết quả chạy thật (bước 7 của `plan.md`, `2026-10-24`)

Stack thử riêng như bước 2 (Postgres 16 `wal_level=logical`, Kafka 3.9, Kafka Connect + Debezium 2.7.3), thêm một cổng Kafka ra máy chủ; dựng bằng Docker và gỡ hoàn toàn, **không chạm Kafka và Postgres dev**. Bảng hộp thư tạo từ mẫu migration của `outbox-starter`; connector đăng ký bằng `outbox-connector.sh` **trước** khi ghi hàng đầu tiên. Ứng dụng thử gồm `OutboxEventStore` thật ở phía ghi (mỗi event một giao dịch riêng) và một listener dùng `event-consumer-starter` thật ở phía nhận, ghi mọi lần xử lý ra tệp để đếm. Kịch bản chạy bằng một script bash; **30 kiểm tra đạt**, trong đó KT-04, 08, 09 và 10a đạt ở các lượt liên tiếp.

| Ca | Kết quả |
|---|---|
| KT-04 | Chủ đề đúng, khóa là mã thực thể (dạng JSON có nháy), khung đủ `schemaVersion`, `eventType`, `aggregateId`; không bọc `{schema,payload}` |
| KT-08 | 30 event một thực thể đến đủ, đúng thứ tự |
| KT-09 | `traceId` có ở hàng hộp thư, ở bên nhận, và trong log bên nhận |
| KT-06 (ghép) | Event hỏng chạy đúng 4 lần (1 + 3 thử lại), vào `<dịch vụ>.dlq` (ADR-0010) với tiêu đề `x-dlq-error-class`, ERROR `EVENT_DEAD_LETTERED`; event trước và sau nó vẫn xử lý (`setCommitRecovered(true)` hoạt động, offset vẫn tiến) |
| KT-01 (ghép) | Giao dịch rollback: không hàng hộp thư, không event |
| KT-10a | Restart Kafka Connect khi mới nhận 7 trong 120 event: đủ 120, đúng thứ tự lần đầu, không trùng |
| KT-10b | Dừng connector, ghi 20 event, chạy lại: đối chứng (lúc dừng không có gì đến) đạt; 20 event tích lại đến đủ, đúng thứ tự |
| KT-10c | Dừng Kafka, ghi 10 event, chạy lại: connector tự chạy lại, 10 event đến đủ, đúng thứ tự |
| Giết bên nhận | Giết khi mới xử lý 16 trong 40 event rồi chạy lại: đủ 40, đúng thứ tự, không xử lý lại event nào (`AckMode.RECORD`) |

**Phá thử:** giảm số lần thử lại từ 3 xuống 1 làm ca hàng chờ lỗi đỏ ("999 chạy 2 lần").

**Phát hiện mới:**
- Khóa thông điệp ra bus là JSON có dấu nháy (T4).
- Độ trễ khởi động lạnh 11 đến 14 giây (T13).

**Chưa kiểm** (không nằm trong lượt này):
- Phá thử "ack trước commit": module nhận và ứng dụng thử không có giao dịch cơ sở dữ liệu thật ở handler. Ca giết bên nhận chỉ cho thấy `AckMode.RECORD` không để mất event; ca "ack trước commit làm mất event" kiểm ở feature 02 và 04 khi có handler thật.
- Bốn lớp test của `oauth2-service` nối vào cơ sở dữ liệu dev không chạy (332 test còn lại đạt); `identity-service` (`secrets-only`) và `oauth2-service` (`off`) chỉ kiểm cấu hình bằng đọc khóa thuộc tính.
- Các connector hiện có phát thông điệp bọc `{schema,payload}` (cấu hình mức worker); chưa kiểm bộ nhận hiện có đọc dạng nào.

**Hồi quy sau khi đổi `outbox-starter`:** test của identity-service (1), catalog-service (76) và `outbox-starter` (10) đạt; inventory, order, scheduler không có test, chỉ biên dịch.

## T1. Cách chuyển hộp thư ra bus · Đã làm (theo hướng đã có ở `4. communication.md`)

- **Quyết định:** đọc nhật ký giao dịch của cơ sở dữ liệu (CDC) bằng Debezium trên Kafka Connect, một connector cho mỗi cơ sở dữ liệu của BC phát, bộ biến đổi `EventRouter` đưa hàng hộp thư thành thông điệp: chủ đề lấy từ cột chủ đề, khóa lấy từ mã thực thể, giá trị là khung event.
- **Vì sao:** ghi hàng hộp thư là bước duy nhất trong giao dịch của BC; không có tiến trình nào của BC phải nhớ "đã gửi chưa", nên không có đua giữa nhiều bản của dịch vụ. Hạ tầng Kafka Connect đã có sẵn trong môi trường dev.
- **Loại:** bộ gửi quét bảng định kỳ (poller) trong chính dịch vụ: đơn giản, không cần connector, nhưng phải khóa hàng khi nhiều bản chạy, độ trễ theo chu kỳ quét, tải đọc lặp lên bảng. Giữ làm phương án lui nếu CDC gây khó khi chạy thật.
- **Đã kiểm `2026-10-24` (mục "Kết quả thử"):** `EventRouter` mở rộng cột nội dung dạng văn bản; khóa và chủ đề đúng. `snapshot.mode=no_data` **sót** hàng ghi trước lúc connector tạo, nên connector phải đăng ký trước khi BC ghi hàng đầu tiên. Khởi động lại connector, dừng connector hoặc Kafka rồi chạy lại: đạt (KT-10, mục "Kết quả chạy thật").

## T2. Bảng hộp thư · Đã làm

- **Quyết định:** mỗi BC phát có một bảng `outbox_events` trong cơ sở dữ liệu của mình, do migration của BC tạo (thư viện cung cấp mẫu, không tự tạo bảng). **Một hình bảng chung cho mọi BC**: `id` (khóa kỹ thuật tăng dần), `event_id` (UUID v7, mã event, **ràng buộc duy nhất**), `aggregate_type`, `aggregate_id` (thành khóa thông điệp), `event_type`, `routing_key` (chủ đề), `payload` (văn bản chứa JSON), `occurred_on`, `created_at`, `trace_id`, `span_id`. Chỉ mục theo `created_at` (chỗ dọn về sau).
- **Vì sao:** cùng một hình bảng thì mọi connector dùng cùng một cấu hình `EventRouter` và thư viện dùng một entity cho mọi BC; đổi tên hay kiểu cột là đổi cả hai chỗ ở mọi BC. Ràng buộc duy nhất trên `event_id` bảo đảm một event không vào hộp thư hai lần; cùng mã đó là `sourceEventId` mà Nhật ký dùng để khử trùng. UUID v7 sắp xếp theo thời gian (ADR-0001).
- **Loại:** khóa chính UUID v7 thay khóa kỹ thuật, `jsonb` cho nội dung, đổi tên `routing_key` thành `topic` (đẹp hơn về chữ nhưng buộc đổi entity và cấu hình connector của mọi BC mà không thêm khả năng nào; người chốt `2026-10-24` sau khi xem mã hiện có). Nội dung dạng văn bản không kiểm hợp lệ JSON ở lưu trữ; bộ dựng khung (T4) luôn ghi JSON hợp lệ nên chấp nhận.
- **Giữ hàng:** hàng ở lại sau khi đã chuyển (xem lại được event đã gửi, cho điều tra); dọn qua Lập lịch ở C2b (roadmap). Hệ quả: bảng chỉ tăng ở C1a, chấp nhận ở quy mô học tập; ghi `deferred.md` khi đóng feature.

## T3. Ghi cùng giao dịch (R12) · Đã làm

- **Quyết định:** cổng ghi hộp thư (`EventOutbox` trong miền ứng dụng, cài ở hạ tầng) **bắt buộc có giao dịch đang chạy**; không có thì ném lỗi lập trình (E1). Use case giữ giao dịch ở lớp hạ tầng theo quy ước dự án; thay đổi dữ liệu và ghi hộp thư cùng giao dịch đó.
- **Dựng khung:** aggregate gom event của nó; use case lưu aggregate rồi chuyển các event qua cổng. Khung (T4) dựng ở thư viện để mọi BC cùng một hình.
- **Loại:** mặc định "tham gia giao dịch hoặc tự mở nếu chưa có" (im lặng phá R12 khi use case quên giao dịch); phát trực tiếp lên bus sau commit (mất event nếu tiến trình chết giữa commit và gửi).
- **Kiểm:** KT-01 (rollback không để lại hàng), KT-02 (commit để lại đúng một hàng mỗi event), KT-03 (gọi ngoài giao dịch bị từ chối).

## T4. Khung thông điệp · Đã làm

- **Giá trị:** JSON trần (không bọc `schema`, xem T11) gồm `eventId`, `eventType`, `occurredAt`, `aggregateId`, `aggregateType`, `schemaVersion` (số nguyên, bắt đầu 1), `traceId` và `spanId` (cả hai tùy chọn), `payload` (đối tượng của BC phát). Khóa thông điệp: `aggregateId`, ghi dạng JSON có dấu nháy (`"K4"`, không phải `K4`) vì connector dùng `JsonConverter` cho khóa; thứ tự và phân vùng không bị ảnh hưởng, nhưng bên nhận đọc khóa thông điệp phải biết (đo ở mục "Kết quả chạy thật").
- **Tiến hoá:** chỉ thêm trường, không đổi tên hay xóa; đổi nghĩa thì tăng `schemaVersion` và phát song song hai phiên bản cho tới khi hết bên nhận cũ (`tech-stack.md`). Bên nhận bỏ qua trường lạ.
- **Hợp đồng riêng của Nhật ký:** `AuditActionRecorded` là một `payload` đặt trong khung này; hợp đồng bên trong thuộc `audit-service/analysis.md` mục 8.2. Khung không biết nội dung.
- **Quy tắc nội dung:** không dữ liệu định danh, không bí mật. Thư viện kiểm tên trường ở mọi độ sâu khi dựng khung; vi phạm là lỗi lập trình (`SensitiveFieldException`, thông báo nêu đường dẫn như `payload.items[1].accessToken`, không nêu giá trị). So khớp theo **tên đầy đủ**, không phân biệt hoa thường và dấu phân cách (`access_token` = `accessToken`), không theo chuỗi con (`tokenCount` hợp lệ); không kiểm giá trị. Hai chính sách: `secretsOnly` (bí mật theo `logging.md`: `password`, `passwordHash`, `token`, `accessToken`, `refreshToken`, `idToken`, `otp`, `secret`, `clientSecret`, `authorization`, `cardNumber`, `apiKey`) và `strict` (thêm dữ liệu định danh theo INV-AUD-04: `identityNumber`, `nationalId`, `citizenId`, `idCard`, `taxCode`, `taxId`, `address`, `phone`, `phoneNumber`, `email`). **Mặc định `strict`**; BC có event cần mang email (thông báo) chọn `secretsOnly` tường minh, BC có event buộc mang bí mật chọn `none` (không kiểm) và ghi lý do cạnh cấu hình. Chọn bằng thuộc tính `app.outbox.sensitive-field-policy` = `strict` | `secrets-only` | `off` (giá trị lạ thì dịch vụ không khởi động; `off` ghi cảnh báo lúc khởi động).
- **Bên nhận:** `EventEnvelopeReader` bỏ qua trường lạ ở mọi mức, từ chối (`InvalidEventEnvelopeException`) khi không phải JSON, không phải đối tượng, sai kiểu, hoặc thiếu hay rỗng `eventId`, `eventType`, `aggregateType`, `aggregateId`, `occurredAt`, `schemaVersion` (từ 1), `payload`. `traceId` được phép vắng. Thông báo nêu tên trường, không chép nội dung thông điệp.
- **Loại:** đặt mã event và loại vào tiêu đề (header) thông điệp (tách khỏi giá trị nhưng mỗi bên nhận phải đọc hai nơi; khung trong giá trị thì một nơi, dễ ghi log và phát lại); mỗi BC tự định khung (nhiều dạng khác nhau).

## T5. Bộ nhận dùng chung · Đã làm

- **Quyết định:** một starter phía nhận (`event-consumer-starter`, tách khỏi starter phía phát) cấu hình sẵn: giải mã khung thành đối tượng, đưa `traceId` và `spanId` vào ngữ cảnh log, **commit offset sau mỗi bản ghi (`AckMode.RECORD`), tức sau khi listener trả về; vì vậy handler của BC chạy trọn giao dịch của nó bên trong lời gọi `consume`**, nhóm tiêu thụ đặt theo tên dịch vụ, vị trí bắt đầu `earliest` cho nhóm mới (không bỏ sót event cũ của chủ đề).
- **Handler:** hàm nhận khung đã giải mã; BC không viết lại phần Kafka. Lỗi nghiệp vụ không thể thử lại (event vô nghĩa) ném loại lỗi riêng để vào hàng chờ lỗi ngay.
- **Loại:** để mỗi BC tự cấu hình bộ nhận (đã dẫn tới nhiều bản cấu hình giống nhau, khác tham số vô tình).

## T6. Thử lại, hàng chờ lỗi, cảnh báo · Đã làm

- **Thử lại:** lỗi tạm thử lại giãn cách tăng dần (khởi đầu 1 giây, nhân đôi, trần 30 giây, tối đa 3 lần thử lại sau lần xử lý đầu, tức tối đa 4 lần xử lý; cấu hình được). Trong lúc thử lại, phân vùng đó dừng (giữ thứ tự theo thực thể); phân vùng khác không bị ảnh hưởng.
- **Hàng chờ lỗi:** chủ đề `<spring.application.name>.dlq` (một hàng chờ lỗi mỗi dịch vụ nhận, ghi đè bằng `app.event-consumer.dlq-topic`; đổi từ `<chủ đề gốc>.dlq` ngày `2026-10-26`, ADR-0010), giữ nguyên khóa và giá trị (phân vùng do nhà sản xuất chọn theo khóa), thêm tiêu đề `x-dlq-error-class` (tên loại lỗi của handler, bỏ lớp bọc của listener) cùng các tiêu đề chủ đề gốc (`kafka_dlt-original-topic`), phân vùng, offset gốc. Không chép thông báo lỗi hay dấu vết ngăn xếp vì thông báo của thư viện bên thứ ba có thể chứa dữ liệu người dùng (bài học feature 09 của C0); log lỗi cũng chỉ nêu tên loại lỗi.
- **Lỗi không thử lại:** sai khung, thiếu trường bắt buộc, loại lỗi riêng của handler: vào hàng chờ lỗi ngay.
- **Cảnh báo ở C1a:** ghi ERROR có mã `EVENT_DEAD_LETTERED` kèm chủ đề, mã event (nếu đọc được) và tên loại lỗi; tăng bộ đếm `events.dead_lettered` theo chủ đề. Định tuyến cảnh báo thành thông báo cho người trực chưa có hạ tầng, nên cảnh báo hiện là log và bộ đếm (đúng nghĩa "không bỏ qua im lặng" ở mức dev); nối với kênh thông báo là điểm nối (ghi `deferred.md` khi đóng).
- **Xử lý lại từ hàng chờ lỗi:** thủ công, bằng công cụ của Kafka; không có tác vụ tự xử lại. Handler khử trùng nên chạy lại an toàn.
- **Loại:** thử lại vô hạn (một thông điệp hỏng chặn cả phân vùng); bỏ qua im lặng sau số lần thử (mất event); hàng chờ lỗi trong bảng cơ sở dữ liệu của bên nhận (thêm bảng, không giúp gì cho trường hợp cơ sở dữ liệu là thứ hỏng).

## T7. Khử trùng ở bên nhận · Đã làm

- **Quyết định:** theo `idempotency-layering.md`: khử trùng sống còn thì dùng ràng buộc ở cơ sở dữ liệu; không có bảng "đã xử lý" chung do thư viện giữ. Ba kiểu được chọn theo hệ quả khi trùng:
  1. **Ràng buộc duy nhất trên mã event** khi mỗi event tạo ra một bản ghi (Nhật ký: `sourceEventId`; ghi bỏ qua khi trùng).
  2. **Giá trị tuyệt đối kèm phiên bản** khi event mang trạng thái (bản sao trạng thái người bán ở Danh mục: chỉ nhận khi phiên bản lớn hơn; trùng và sai thứ tự đều không thay đổi gì).
  3. **Tự khử trùng** khi thao tác đặt giá trị tuyệt đối.
- **Thư viện cung cấp:** bộ khung kiểm thử cho handler: phát cùng một event hai lần và kiểm kết quả như một lần (KT-05); mọi BC nhận event phải có ít nhất một test dùng bộ khung này.
- **Loại:** bảng `processed_events` chung ở thư viện (thêm một lần ghi mỗi event cho cả ba trường hợp trong khi hai trong ba đã có cách rẻ hơn và đúng hơn); Redis làm trọng tài (mất khóa thì mất tính đúng; `idempotency-layering.md`).
- **Đã chốt:** Q1 (không có bảng chung).

## T8. Thứ tự theo thực thể · Đã làm

- **Quyết định:** khóa thông điệp là mã thực thể, nên các event của một thực thể **trong cùng một chủ đề** cùng phân vùng và đúng thứ tự ghi (đã thử, ca 3). **Event của cùng thực thể ở hai chủ đề khác nhau không có thứ tự với nhau.** Hai hệ quả: (1) event mà bên nhận cần nhận đúng thứ tự (đổi trạng thái của cùng một thực thể) nên cùng một chủ đề, phân biệt bằng `eventType` trong khung; (2) khi event buộc ở hai chủ đề, bên nhận dựa vào phiên bản hoặc giá trị tuyệt đối. Không bảo đảm thứ tự giữa thực thể khác nhau, và mất thứ tự khi một thông điệp đi qua hàng chờ lỗi rồi được xử lý lại; vì vậy handler không dựa vào thứ tự (E7, T7). Việc chọn chủ đề cho từng event thuộc feature của BC (03, 04, 05, 09).
- **Kiểm:** KT-08 (nhiều event một thực thể đến đúng thứ tự).

## T9. Mã truy vết · Đã làm

- **Quyết định:** `traceId` và `spanId` lấy từ ngữ cảnh của yêu cầu lúc ghi hộp thư, đi trong khung, bộ nhận đưa vào ngữ cảnh log của handler. Handler gọi thao tác bất đồng bộ mới phải tự truyền ngữ cảnh (`MdcTaskDecorator` của starter quan sát).
- **Kiểm:** KT-09 (mã truy vết trong log của bên phát và bên nhận trùng).

## T10. Thư viện và cấu trúc mã · Đã làm

- **Phía phát:** starter `outbox-starter` cung cấp cổng ghi hộp thư (T3), dựng khung (T4), mẫu migration (Postgres), kiểm tên trường nhạy cảm.
- **Phía nhận:** starter mới `event-consumer-starter` (T5, T6, bộ khung kiểm thử T7).
- **Hợp đồng khung** nằm ở thư viện sự kiện dùng chung, một bản duy nhất.
- **Cấu hình:** chủ đề, số lần thử, giãn cách là thuộc tính nạp lúc khởi động.

## T11. Hạ tầng dev · Đã làm

- **Cơ sở dữ liệu phát:** cần `wal_level=logical` và một khe sao chép (replication slot) riêng cho connector. Cơ sở dữ liệu cho Người bán và Nhật ký chưa có trong môi trường dev; thêm ở feature đầu dùng chúng (02 cho Nhật ký, 03 cho Người bán), kèm connector của Người bán. Nhật ký không phát event nên không cần connector.
- **Connector:** một tệp cấu hình mỗi BC phát, đăng ký bằng script chạy lại được (`PUT /connectors/<tên>/config`, không tạo trùng). Thông tin kết nối đọc từ biến môi trường, không viết cứng mật khẩu vào tệp. Mỗi connector có `topic.prefix` và `slot.name` riêng (ca 7). Khai **cả** `key.converter` và `value.converter` (lớp JSON) kèm `schemas.enable=false` để thông điệp là JSON trần, không bọc (ca 6). `snapshot.mode=no_data`; đăng ký connector trước khi BC ghi hàng đầu tiên (ca 4). Script gỡ connector cũng xóa khe sao chép (ca 8).
- **Rủi ro khe sao chép:** connector dừng lâu thì cơ sở dữ liệu giữ nhật ký giao dịch không dọn được, đầy đĩa. Dev chấp nhận; ghi lại để biết khi cơ sở dữ liệu dev đầy bất thường. Giới hạn kích thước giữ nhật ký là điểm nối.

## T12. Kiểm chứng · Đã làm

| KT | Điều kiểm | Cách |
|---|---|---|
| KT-01 | Rollback không để lại hàng hộp thư | Tích hợp (Postgres thật) |
| KT-02 | Commit để lại đúng một hàng mỗi event, đủ cột; ghi lần hai cùng `event_id` bị từ chối bởi ràng buộc duy nhất | Tích hợp |
| KT-03 | Ghi hộp thư ngoài giao dịch bị từ chối | Tích hợp |
| KT-04 | Hàng hộp thư thành thông điệp đúng chủ đề, khóa, khung | Chạy thật (Postgres, Kafka Connect, Kafka) |
| KT-05 | Cùng event hai lần (kể cả giao lại sau khi handler đã commit mà chưa ack), handler mẫu cho kết quả như một lần; bộ khung có thêm ca phát đảo thứ tự hai event cùng thực thể để BC dùng khi handler mang trạng thái | Bộ khung kiểm thử, tích hợp (Kafka thật) |
| KT-06 | Lỗi tạm: thử lại rồi thành công; hết 3 lần: vào hàng chờ lỗi giữ khóa và giá trị, tiêu đề chỉ có tên loại lỗi (không có thông báo của thư viện), có log ERROR mã `EVENT_DEAD_LETTERED` và bộ đếm tăng một | Tích hợp |
| KT-07 | Thông điệp sai khung hoặc thiếu trường vào hàng chờ lỗi ngay (không thử lại) và không chặn thông điệp sau cùng phân vùng | Tích hợp |
| KT-08 | Nhiều event một thực thể đến đúng thứ tự | Chạy thật |
| KT-09 | Mã truy vết nối log hai bên | Chạy thật |
| KT-10 | Khởi động lại connector giữa chừng, và dừng connector (hoặc bus) rồi chạy lại sau khi hàng đã tích: không mất, đến đủ theo thứ tự, bên nhận khử trùng | Chạy thật |
| KT-11 | Tên trường nhạy cảm trong nội dung bị từ chối ở dựng khung, ở mọi độ sâu | Đơn vị |
| KT-12 | Bên nhận giải mã được khung có trường lạ (tiến hoá chỉ thêm trường) và bỏ qua chúng; khung thiếu trường bắt buộc bị từ chối | Đơn vị |

Phá thử cho KT-01 (bỏ yêu cầu giao dịch thì KT-03 đỏ), KT-06, KT-07 theo thói quen của C0. Đối chiếu đường không suôn sẻ ở `flow.md` mục 4: E1 → KT-03; E2 → KT-08; E3 → KT-10; E4 → KT-10; E5 → KT-07; E6 → KT-06; E7 → ca đảo thứ tự của bộ khung ở KT-05 (do BC dùng); E8 → KT-11; E9 → KT-09.

## T13. Số đo · Đã đo một phần

Độ trễ từ ghi hộp thư đến bên nhận xử lý xong, đo ở stack thử với 5 event đơn lẻ: **khoảng 0,5 đến 2 giây** khi đã chạy ổn định; **11 đến 14 giây** cho event đầu tiên sau khi connector hoặc nhóm tiêu thụ mới khởi động (khởi động lạnh). Đủ cho "vài giây đến vài phút" của Nhật ký (`audit-service/analysis.md`). Chưa đo dưới tải; số thật đo ở feature 13 cùng chuỗi đầu-cuối.

## Câu hỏi cần người chốt

Đã chốt `2026-10-23` (người theo đề xuất, riêng Q4 đổi số lần thử):

| # | Câu hỏi | Kết luận |
|---|---|---|
| Q1 | Có bảng `processed_events` chung ở thư viện không (T7)? | Không; mỗi BC dùng ràng buộc hoặc phiên bản của mình, thư viện chỉ cho bộ khung kiểm thử |
| Q2 | Chạy thật feature này bằng gì khi chưa có BC nào phát event? | Một ứng dụng thử nhỏ (cơ sở dữ liệu, một use case ghi hộp thư, một handler mẫu) chạy với Kafka Connect thật; sau đó 03 và 04 là lần chạy thật đầu tiên với BC thật. **Thực hiện:** ứng dụng thử là mô-đun tạm dựng ngoài cây mã chính và xóa sau khi chạy; chạy trên stack Docker cô lập (không dùng Kafka và Postgres dev) |
| Q3 | Hàng hộp thư có giữ lại sau khi chuyển không (T2)? | Giữ, đúng roadmap (dọn ở C2b) |
| Q4 | Giãn cách và số lần thử (T6) | 1 giây, nhân đôi, trần 30 giây, **3 lần** |
| Q5 | Giữ `spanId` trong khung không (T4)? | **Giữ** (người chốt `2026-10-24`, đổi lại đề xuất ban đầu là bỏ). `spanId` tùy chọn như `traceId`: `traceId` nối log, `spanId` để bên nhận đặt làm span cha khi nối cây trace; cột `span_id` của hộp thư (T2) cũng còn được điền |

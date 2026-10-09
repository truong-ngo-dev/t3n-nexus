# search-service

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-09-30`
>
> Tầng kỹ thuật: **cách hiện thực** nghiệp vụ ở [`analysis.md`](./analysis.md), cùng giải pháp cho NFR và các vấn đề
> tích hợp (idempotency, concurrency, độ tin cậy). Không định nghĩa lại nghiệp vụ.

---

## 1. Tổng quan kỹ thuật

Hiện thực read-model tìm kiếm và duyệt sản phẩm: nhận dữ liệu từ catalog và tồn kho qua Kafka, giữ trong một kho tìm
kiếm, trả lời truy vấn công khai. Nghiệp vụ đầy đủ: [`analysis.md`](./analysis.md) §1–§3.

- **Loại service:** read-model. Không có aggregate, không có lệnh ghi từ client.
- **Stack:** Java 21, Spring Boot, Elasticsearch (kho tìm kiếm, `requirement.md` §2.5 và §4), Kafka (consumer), Redis
  (micro-cache kết quả). Chưa ghim phiên bản Elasticsearch (`TQ-02`).
- **Kiến trúc trong service:** hexagonal · **Module:** `domain` (bộ dựng truy vấn, chiến lược xếp hạng) · `application`
  (use case tìm kiếm, projector) · `infrastructure` (client Elasticsearch, consumer Kafka, cache, kho metadata trong bộ
  nhớ) · `presentation` (REST công khai).

---

## 2. Ánh xạ domain → code

### 2.1 Aggregate

**N/A — read-model** (analysis §6). Đơn vị dữ liệu của service là **một document cho mỗi đơn vị bán được** (`T-01`).

### 2.2 Thuật ngữ khác tên

| Thuật ngữ (analysis) | Tên trong code |
|---|---|
| Thẻ sản phẩm | `ProductCard` |
| Đơn vị đại diện | `representative` (kết quả `inner_hits` đầu tiên của nhóm sản phẩm) |
| Bộ lọc | `Facet` |
| Từ khoá | `q` |
| Độ tươi | độ lệch `now − stockSyncedAt` / `now − catalogSyncedAt` |

### 2.3 Trạng thái

| Trạng thái (analysis) | Biểu diễn | Giá trị |
|---|---|---|
| Được xem công khai (`INV-CAT-044`) | field `visible`, mức sản phẩm, **catalog ghi**, search không tự suy | `true` / `false` |
| Đang mở | field `active`, mức đơn vị, catalog ghi | `true` / `false` |
| Còn hàng | field `inStock`, mức đơn vị, tồn kho ghi | `true` / `false` |
| Mua được | predicate dùng chung `purchasable` trong bộ dựng truy vấn | `active AND inStock` |
| Hết hàng liên tục | field `unavailableSince`, mức đơn vị, **search tự suy** khi ghi (`T-04`) | thời điểm bắt đầu không mua được, hoặc `null` |

---

## 3. Đảm bảo quy tắc

> Mọi quy tắc của service này là **quy tắc tra cứu**, loại Liên tục, đảm bảo ở tầng **query** (bộ dựng truy vấn) hoặc
> **projection** (lúc ghi). Cột Test: `—` = **chưa có test** (việc còn nợ).

| INV | Loại | Tầng | Cơ chế | Test |
|---|---|---|---|---|
| `INV-SRC-01` | Liên tục | Query + Projection | `VisibilityFilter` luôn thêm `visible = true` vào mọi truy vấn, không có đường bỏ. Projection ghi `visible` từ snapshot của catalog (kết quả `INV-CAT-044`); document mới chỉ có vùng tồn kho (chưa có `visible`) bị loại tự nhiên | — |
| `INV-SRC-02` | Liên tục | Query | Sản phẩm không có đơn vị `purchasable` vẫn nằm trong kết quả; thẻ đánh dấu hết hàng khi đơn vị đại diện không `purchasable` | — |
| `INV-SRC-03` | Liên tục | Query | Khoá sắp xếp đầu của "Phổ biến" là `purchasable` giảm dần; bộ lọc "chỉ hiện hàng còn" là điều kiện `purchasable`; ngưỡng ngừng hiển thị là điều kiện `unavailableSince` rỗng hoặc mới hơn `now − N` (`T-04`, N cấu hình) | — |
| `INV-SRC-04` | Liên tục | Query | Gom nhóm theo `productId`; đơn vị đại diện lấy bằng `inner_hits` (size 1) sắp theo độ khớp từ khoá, `purchasable` giảm dần, `price` tăng dần, chỉ trong các document đã khớp bộ lọc (`T-07`) | — |
| `INV-SRC-05` | Liên tục | Projection + Query | Mỗi đơn vị bán được một document nên mọi điều kiện lọc thuộc tính phân loại là các mệnh đề trên **cùng document** (`T-01`) | — |
| `INV-SRC-06` | Liên tục | Query | `FacetPlanner` chọn tập bộ lọc theo ngữ cảnh: danh mục lá → thuộc tính lọc được của lá (lấy từ kho metadata); danh mục cấp trên hoặc chỉ có từ khoá → thương hiệu, giá, còn hàng, danh mục con | — |
| `INV-SRC-07` | Liên tục | Query | `post_filter` cho các lựa chọn của người mua, cộng với aggregation riêng cho từng bộ lọc mang **mọi bộ lọc khác trừ chính nó** (`T-06`); chỉ giá trị có trong kết quả mới được trả | — |
| `INV-SRC-08` | Liên tục | Query | Số đếm = `cardinality` của `productId` (`T-06`); nhãn ghép từ kho metadata lúc trả response; giá trị chọn là định danh (`T-10`) | — |
| `INV-SRC-09` | Liên tục | Query + Projection | `multi_match` trên `name`, `brandName`, `attrText`, `description` với boost giảm dần; mỗi field text có thêm bản bỏ dấu (`TQ-05`) | — |
| `INV-SRC-10` | Liên tục | Query | Truy vấn từ khoá không xét trạng thái đóng của danh mục | — |
| `INV-SRC-11` | Liên tục | Query | Danh mục mở rộng thành các danh mục lá con bằng cây trong bộ nhớ (`T-05`); danh mục không "dùng được" trong cây thì không có đường duyệt | — |
| `INV-SRC-12` | Liên tục | Query | Mọi xếp hạng đi qua `RankingStrategy`; API không đổi khi thêm tín hiệu (`T-09`) | — |
| `INV-SRC-13` | Liên tục | Query | **Đang chờ quyết định:** không thể vừa gom theo sản phẩm vừa phân trang bằng con trỏ với xếp hạng theo độ liên quan (`T-08`, `TQ-01`, `Q-SRC-14`). Thiết kế tạm: phân trang có giới hạn độ sâu | — |

---

## 4. Commands

**N/A — không có lệnh ghi từ client** (analysis §3, §4). Dữ liệu chỉ vào qua consumer (§7.2).

---

## 5. Queries

| RM (analysis) | Handler | API | Quyền | Nguồn đọc | Điều kiện hiển thị | Phục vụ NFR |
|---|---|---|---|---|---|---|
| `RM-SRC-01` | `SearchProducts` | `GET /api/search/products?q=…&brandId=…&price=…&attr=…&sort=…&size=…&page=…` | Public | Micro-cache (§12.1) → Elasticsearch | `INV-SRC-01`…`04`, `05`, `09`, `10`, `12`, `13` | §12.3 dòng tìm kiếm, tải |
| `RM-SRC-02` | `SearchProducts` (có `categoryId`, không `q`) | `GET /api/search/products?categoryId=…` | Public | Như trên; cây danh mục từ kho metadata | Như `RM-SRC-01`, cộng `INV-SRC-11` | Như trên |
| `RM-SRC-03` | `FacetBuilder` (chạy chung truy vấn với `SearchProducts`, trả trong cùng response ở trường `facets`) | Cùng endpoint | Public | Như trên | `INV-SRC-06`…`08` | Như trên |

---

## 6. Use cases — tham gia

| Feature | Vai trò | Bước của service này | Bước bù trừ | Publishes |
|---|---|---|---|---|
| *(chưa có feature design cho Phase B)* | Consumer / query | Nhận snapshot catalog, sự kiện tồn kho; trả truy vấn | Không có — read-model, không tham gia saga nào | — |

---

## 7. Integration contract

### 7.1 Publishes

**N/A — không phát event** (analysis §8.1).

### 7.2 Consumes

> Ánh xạ dữ liệu → §8. Idempotency và xử lý sai thứ tự của projection: xem §8.1 (nguồn duy nhất).

| Topic | Event | Handler | Consumer group | Hiện thực | Idempotency (dedup theo) | Sai thứ tự | Retry / DLQ | Test |
|---|---|---|---|---|---|---|---|---|
| `catalog.product.search-snapshot` | `ProductSearchSnapshotEvent` (gom `EVT-CAT-041`…`045`, `051`…`053`) | `CatalogSnapshotProjector` | `search-catalog` | Projection §8 | Xem §8.1 | Xem §8.1 | Thử lại có backoff, quá số lần → DLQ (`3.technical/dlq-implementation-notes.md`) | — |
| `catalog.category.updated` | `CategoryUpdatedEvent` (`EVT-CAT-031`) | `CategoryMetadataRefresher` | `search-metadata` | Kho metadata (§8.1) | Làm mới toàn bộ cây, idempotent tự nhiên | Bản làm mới sau luôn ghi đè | Như trên | — |
| *(chưa đặt tên — `TQ-03`)* | Snapshot metadata catalog: thuộc tính, giá trị chuẩn, thương hiệu | `CatalogMetadataProjector` | `search-metadata` | Kho metadata | Theo version của đối tượng | Bỏ bản có version cũ hơn | Như trên | — |
| *(chưa có — `TQ-03`)* | Mức tồn kho theo đơn vị bán được (`Q-SRC-05`) | `StockProjector` | `search-stock` | Projection §8 | Xem §8.1 | Xem §8.1 | Như trên | — |

`EVT-CAT-046`, `EVT-CAT-054` không cần xử lý (chỉ xảy ra với bản nháp chưa từng xuất hiện, analysis §8.2). Service **không**
consume các domain event mỏng của catalog (`ProductPublished`, `VariantPriceChanged`…): xem `T-02`.

### 7.3 Sync calls

| Chiều | Đối tác | Giao thức | Endpoint | Timeout | Retry | Khi đối tác lỗi |
|---|---|---|---|---|---|---|
| Outbound | Elasticsearch | HTTP | Truy vấn tìm kiếm, ghi document | 300ms cho truy vấn *(đề xuất, `TD-01`)* | Không retry truy vấn (làm tăng tải đúng lúc quá tải) | Circuit breaker; trả bản cache cũ còn trong 60s nếu có, không thì 503 kèm `Retry-After` |
| Outbound | Redis | RESP | Micro-cache | 20ms | Không | Bỏ qua cache, đi thẳng Elasticsearch (cache là tối ưu, không phải điều kiện) |
| Inbound | web-gateway | REST | `/api/search/**` (công khai) | | | |

---

## 8. Projection

### 8.1 Ánh xạ event → dữ liệu

Mỗi document có **hai vùng ghi độc lập**, mỗi vùng một chủ sở hữu, kèm version riêng. Search chỉ ghi vùng của nguồn tương ứng và
vùng dẫn xuất của chính nó. Danh sách field đầy đủ và mapping: [`data.md`](data.md).

| Event nguồn | Từ BC | Ghi vào | Field / thao tác | Guard (idempotency + sai thứ tự) | Phục vụ |
|---|---|---|---|---|---|
| `ProductSearchSnapshotEvent` | catalog | `products`, vùng **catalog** | Upsert mỗi đơn vị bán được trong snapshot: `visible`, `name`, `description`, `brandId`, `brandName`, `categoryId`, `price`, `active`, `attrKeys`, `attrText`, `thumbnail`, `publishedAt`, `catalogVersion`; tính `listingQuality`. Sau đó xoá các document của cùng `productId` có `catalogVersion` thấp hơn (đơn vị đã bị gỡ khỏi sản phẩm) | Chỉ ghi khi `catalogVersion` mới **lớn hơn** bản đang lưu; ngược lại bỏ qua | `RM-SRC-01`, `02`, `03`; `INV-SRC-01`, `04`, `05`, `09` |
| Mức tồn kho theo đơn vị (`Q-SRC-05`) | tồn kho | `products`, vùng **tồn kho** | Upsert theo `skuId`: `inStock`, `stockVersion`, `stockSyncedAt` | Chỉ ghi khi `stockVersion` mới lớn hơn | `RM-SRC-01`, `02`, `03`; `INV-SRC-02`, `03` |
| (cả hai luồng trên) | — | `products`, vùng **dẫn xuất** | Sau khi ghi vùng của mình, tính lại `unavailableSince` từ `active AND inStock` hiện có trong document, trong **cùng một** thao tác ghi (`T-04`) | Kế thừa guard của luồng đang ghi | `INV-SRC-03` |
| `CategoryUpdatedEvent`, snapshot metadata | catalog | **Kho metadata trong bộ nhớ** (không phải Elasticsearch) | Cây danh mục kèm cờ "dùng được"; thuộc tính lọc được theo danh mục lá; nhãn thuộc tính và giá trị chuẩn; tên thương hiệu để hiển thị | Bản mới nhất theo version thắng | `RM-SRC-02`, `03`; `INV-SRC-06`, `08`, `11` |

Quy tắc chung: document được tạo bởi luồng nào đến trước. Document chỉ có vùng tồn kho (chưa có `visible`) không bao giờ xuất
hiện trong kết quả (`INV-SRC-01`).

### 8.2 Rebuild

- **Khi nào cần:** đổi mapping hoặc cách phân tích văn bản; hỏng dữ liệu; thêm field mới.
- **Cách:** tạo index mới, đổi alias ghi sang index mới để consumer ghi song song vào cả hai, phát lại toàn bộ snapshot từ catalog
  và mức tồn kho từ inventory (replay do hai BC nguồn cung cấp), kiểm số document và mẫu, rồi đổi alias đọc. Truy vấn không bị
  ngắt. Guard version làm cho việc phát lại chồng lên sự kiện đang chạy vẫn an toàn.
- **Thời gian và ảnh hưởng:** phụ thuộc tốc độ replay của hai BC nguồn (catalog: `TD-03` đang chạy đồng bộ trong một request,
  chưa phù hợp). Ước tính chưa có (`TD-01`).

---

## 9. Concurrency

| ID | Điểm tranh chấp | Ai tranh chấp | Cơ chế | Thứ tự khoá | Khi xung đột | Phục vụ |
|---|---|---|---|---|---|---|
| `C-01` | Hai luồng cùng ghi một document (snapshot catalog và mức tồn kho) | `CatalogSnapshotProjector`, `StockProjector` | Ghi bằng update có script: mỗi luồng chỉ đụng vùng của mình, kiểm version vùng đó và tính lại vùng dẫn xuất trong cùng thao tác; Elasticsearch tự thử lại khi xung đột phiên bản | — | Thử lại tối đa `retry_on_conflict`; quá số lần thì đẩy DLQ | `INV-SRC-03` |
| `C-02` | Cùng một sản phẩm nhận nhiều snapshot | Consumer sau khi rebalance | Cùng khoá `productId` → cùng partition → tuần tự; nhận lại bản đã xử lý bị bỏ qua nhờ guard version | — | Bỏ qua | `INV-SRC-01` |
| `C-03` | Xoá đơn vị bị gỡ song song với snapshot mới hơn của cùng sản phẩm | `CatalogSnapshotProjector` | Không xảy ra trong một sản phẩm (tuần tự theo partition). Lệnh xoá dùng điều kiện `catalogVersion` thấp hơn chứ không xoá theo danh sách, nên không xoá nhầm bản mới | — | — | `INV-SRC-05` |
| `C-04` | Rebuild song song với luồng live | Consumer ghi vào hai index | Cả hai index cùng áp guard version; kết quả cuối giống nhau bất kể thứ tự | — | — | §8.2 |

---

## 10. Độ tin cậy

| Hạng mục | Thiết kế | Phục vụ |
|---|---|---|
| Không phát event | Không cần outbox | §7.1 |
| Consumer lỗi lặp lại | Thử lại có backoff rồi DLQ; cảnh báo khi DLQ tăng; phát lại DLQ sau khi sửa nguyên nhân | §7.2 |
| Cách ly độ tươi | Tồn kho và catalog dùng consumer group và topic **riêng**, để đợt replay hay đợt cập nhật lớn của catalog không làm chậm tồn kho (`T-12`) | 12.3 độ tươi còn hàng |
| Elasticsearch không sẵn sàng | Consumer dừng, Kafka giữ dữ liệu, chạy tiếp khi hồi phục. Phía truy vấn dùng circuit breaker và bản cache cũ (§7.3) | 12.3 sẵn sàng |
| Phục hồi dữ liệu | Toàn bộ dữ liệu dựng lại được bằng replay từ hai BC nguồn (§8.2); không có dữ liệu nào chỉ tồn tại ở search | §8.2 |
| Resilience | Timeout và circuit breaker cho §7.3 | §7.3 |

---

## 11. Job định kỳ

**N/A — không có job.** Ngưỡng ngừng hiển thị sản phẩm hết hàng lâu ngày là điều kiện lúc truy vấn (`T-04`), không cần quét. Đối
soát định kỳ giữa search và hai BC nguồn là mong muốn nhưng chưa làm: `TD-02`.

---

## 12. NFR → giải pháp

| Kỳ vọng (analysis §12.3) | Giải pháp | Chi tiết | Kiểm chứng |
|---|---|---|---|
| Tìm kiếm + lọc P99 < 200ms cho từng lần | Ngân sách độ trễ bên dưới; truy vấn lọc theo dạng `filter` (không tính điểm); chỉ tính aggregation của bộ lọc khả dụng; giới hạn `size`; micro-cache | §12.1 | Chưa đo — `TD-01` |
| Tải ~3.200 req/s thường, ~10.000 req/s cao điểm *(giả định)* | Service không giữ trạng thái, scale ngang; micro-cache và single-flight giảm số truy vấn thật xuống Elasticsearch; Elasticsearch thêm replica | §12.1 | Load test kịch bản duyệt danh mục và tìm kiếm ở hai mức tải — `TD-01` |
| ~5.000.000 đơn vị bán được | Một index, số shard chọn theo đo (điểm khởi đầu 3 primary + 1 replica, *đề xuất, chưa đo*); alias để rebuild | [`data.md`](data.md) | `TD-01` |
| Độ tươi còn hàng ≤ 5s | Ngân sách bên dưới; consumer group riêng; `stockSyncedAt` để đo độ lệch thật | §12.2 | Đo `now − stockSyncedAt` P99 |
| Độ tươi chặn / ngừng bán, nội dung, giá ≤ 30s | Snapshot đi cùng pipeline outbox của catalog; còn dư địa lớn so với 30s | §12.2 | Đo `now − catalogSyncedAt` P99 |
| Sẵn sàng thấp hơn luồng đặt hàng | Không gọi đồng bộ sang BC nào lúc truy vấn; circuit breaker + bản cache cũ | §7.3 | Kịch bản chết Elasticsearch |

**Ngân sách độ trễ một truy vấn không trúng cache** *(đề xuất, chưa đo)*: gateway ~10ms · kiểm tra tham số và dựng truy vấn ~5ms ·
Elasticsearch (truy vấn cộng aggregation) 80–120ms · ghép nhãn từ kho metadata và dựng response ~10ms → 105–145ms, chừa ~55ms cho đuôi
phân phối. Trúng cache: dưới 10ms.

**Ngân sách độ tươi còn hàng** *(đề xuất)*: sự kiện tồn kho tới consumer ≤ 1,5s · Elasticsearch làm mới (refresh) 1s · micro-cache ≤ 2s
→ ≤ 4,5s, trong ngưỡng 5s. Thành phần đầu phụ thuộc tồn kho (`Q-SRC-05`). Đây là lý do micro-cache **không được đặt TTL dài hơn 2s** cho
trang có thông tin còn hàng.

### 12.1 Cache (tóm tắt)

| Cache gì | Tầng | Key | TTL | Invalidate khi | Phục vụ |
|---|---|---|---|---|---|
| Kết quả trang truy vấn | Redis | Chuẩn hoá `(q, categoryId, bộ lọc đã sắp thứ tự, sort, page, size)` | 2s | Không invalidate; hết hạn là đủ | 12.3 tải, độ trễ; giới hạn bởi độ tươi còn hàng |
| Gộp truy vấn trùng đồng thời (single-flight) | Trong tiến trình | Như trên | Chỉ trong lúc đang chạy | — | 12.3 tải |
| Kho metadata (cây danh mục, nhãn, thương hiệu) | Bộ nhớ tiến trình | Theo loại đối tượng | Làm mới bằng sự kiện | Nhận sự kiện metadata | `INV-SRC-06`, `08`, `11` |

Không cache kết quả cá nhân hoá: hiện chưa có (`Q-SRC-10`); khi có thì key phải gồm định danh người dùng hoặc tách phần cá nhân hoá ra
khỏi phần cache được.

### 12.2 Observability

- **Metric:** độ trễ truy vấn P50/P99 theo loại (có/không từ khoá, có/không bộ lọc); thời gian `took` của Elasticsearch; tỉ lệ trúng
  micro-cache; **lag của từng consumer group**, đặc biệt `search-stock`; `now − stockSyncedAt` và `now − catalogSyncedAt` P99; số
  phần tử DLQ; tỉ lệ truy vấn không có kết quả.
- **Span:** truy vấn (cache → Elasticsearch → ghép response); mỗi lần ghi projection.
- **Alert:** `now − stockSyncedAt` P99 vượt 5s quá 1 phút; lag `search-stock` tăng liên tục; DLQ tăng; P99 truy vấn vượt 200ms.

---

## 13. Quyền & danh tính

| Hạng mục | Thiết kế | Phụ thuộc |
|---|---|---|
| Danh tính caller | Không dùng. Kết quả giống nhau cho Guest và người đã đăng nhập (analysis §4). Khi có cá nhân hoá: `SearchContext` mang `customerId` hoặc `guestId` (chưa dùng) | `Q-SRC-10` |
| Quyền sở hữu | N/A | — |
| Role | Không có; toàn bộ endpoint công khai, cần `permitAll` ở cả web-gateway và service | web-gateway |
| Chặn lạm dụng truy vấn | Trần `size`; trần độ sâu trang; độ dài tối đa của `q`; số giá trị tối đa mỗi bộ lọc; số bộ lọc tối đa mỗi truy vấn — con số chốt ở `TQ-09` | `Q-SRC-09` |
| Giới hạn tần suất | Dựa vào giới hạn theo IP ở api-gateway. Ngưỡng hiện tại rất chặt cho luồng duyệt hàng: `TD-03` | api-gateway |

---

## 14. Quyết định kỹ thuật

| ID | Câu hỏi | Chọn | Phương án bị loại (vì sao) | Phục vụ |
|---|---|---|---|---|
| `T-01` | Đơn vị document | **Một document cho mỗi đơn vị bán được**, gom theo `productId` lúc truy vấn | *Một document mỗi sản phẩm, mảng biến thể phẳng:* lọc thuộc tính khớp sai tổ hợp, vi phạm `INV-SRC-05`. *Một document mỗi sản phẩm với biến thể dạng `nested`:* lọc đúng, nhưng mỗi thay đổi tồn kho của một đơn vị làm ghi lại **cả khối** sản phẩm, trong khi tồn kho là nguồn ghi nóng nhất | `INV-SRC-05` |
| `T-02` | Dữ liệu catalog vào bằng gì | **Snapshot trạng thái đầy đủ** (state-transfer) theo `productId`, kèm version tăng đơn điệu; tồn kho là sự kiện riêng mang giá trị tuyệt đối | *Consume các domain event mỏng của catalog:* mỗi sự kiện chỉ mang ID nên search phải gọi ngược catalog, hoặc catalog phải thêm event béo cho từng loại thay đổi; cập nhật từng phần trên nhiều topic lại gặp sai thứ tự. *CDC thẳng bảng catalog:* lộ schema nội bộ, consumer tự ghép nhiều bảng | §8.1, `INV-SRC-01`, `05` |
| `T-03` | Chống sai thứ tự và trùng | **Guard version theo từng vùng** (`catalogVersion`, `stockVersion`), ghi có điều kiện; không bảng dedupe | *Versioning ngoài của Elasticsearch:* áp cho **cả document**, mà document có hai chủ sở hữu với hai nhịp version, nên bên này chặn nhầm bên kia. *Dùng timestamp:* phụ thuộc đồng hồ nhiều máy. *Bảng dedupe theo eventId:* thêm kho, không chịu được sai thứ tự | §8.1, `C-01` |
| `T-04` | Ẩn hàng hết lâu ngày | **Lưu `unavailableSince` mức đơn vị**, tính lại mỗi lần ghi bằng script; ẩn bằng điều kiện lúc truy vấn. Sản phẩm biến mất khi **mọi** đơn vị đều quá ngưỡng | *Job quét đặt cờ mức sản phẩm:* cần cả job lẫn tính tổng hợp xuyên document. *Cờ mức sản phẩm cập nhật khi tồn kho đổi:* mỗi sự kiện phải ghi lại mọi đơn vị anh em. Lưu ý: điều kiện ở mức đơn vị làm đơn vị quá ngưỡng bị loại luôn khỏi việc chọn giá dự phòng ở `INV-SRC-04` | `INV-SRC-03` |
| `T-05` | Duyệt danh mục con | **Mở rộng thành các danh mục lá lúc truy vấn**, bằng cây trong bộ nhớ; document chỉ lưu danh mục lá | *Lưu đường dẫn tổ tiên trong document:* cha của danh mục không bao giờ đổi (`INV-CAT-032`) nên đường dẫn ổn định, nhưng ghi document lúc nhận snapshot phải có cây danh mục sẵn, tạo phụ thuộc thứ tự giữa hai topic. Xem lại nếu một danh mục cấp cao có quá nhiều lá | `INV-SRC-11` |
| `T-06` | Bộ lọc và số đếm | **`post_filter`** cho lựa chọn của người mua + aggregation mỗi bộ lọc mang mọi bộ lọc khác trừ chính nó; số đếm bằng **`cardinality` trên `productId`** | *Aggregation trên toàn bộ kết quả sau lọc:* bộ lọc thương hiệu thu hẹp về đúng lựa chọn, người mua không thấy lựa chọn khác. *Đếm số document:* đếm đơn vị thay vì sản phẩm. `cardinality` là xấp xỉ khi tập rất lớn: `TQ-04` | `INV-SRC-07`, `08` |
| `T-07` | Đơn vị đại diện và gom nhóm | **`collapse` theo `productId`** + `inner_hits` (size 1) có sắp xếp riêng | *Gom trong ứng dụng:* tải nhiều document hơn cần và phải tự xử lý phân trang. Theo tài liệu Elasticsearch, aggregation **không** bị ảnh hưởng bởi collapse và `total` của kết quả đếm document chưa gom, nên số sản phẩm lấy từ `cardinality` (`T-06`) | `INV-SRC-04` |
| `T-08` | Phân trang | **`page`/`size` có trần độ sâu** (tạm thời, chờ `Q-SRC-14`); `size` và độ sâu bị chặn để không vượt cửa sổ kết quả của Elasticsearch (mặc định 10.000). Thứ tự tất định: thêm `skuId` làm khoá sắp xếp cuối. Tham số thời gian trong hàm suy giảm độ mới được **cố định** ở trang đầu và mang theo qua các trang | *Con trỏ `search_after`:* theo tài liệu Elasticsearch, `search_after` cùng `collapse` **chỉ được hỗ trợ khi sắp xếp và gom nhóm trên cùng một field và không được có khoá sắp xếp phụ**. Xếp hạng theo độ liên quan cần nhiều khoá nên không dùng được. *Bỏ collapse để dùng con trỏ:* mất gom theo sản phẩm (`INV-SRC-04`, `08`). *`nested` để bỏ collapse:* xem `T-01`. *Khoá sắp xếp tĩnh mức sản phẩm để dùng chung field với collapse:* mất độ liên quan của từ khoá | `INV-SRC-13`, `TQ-01` |
| `T-09` | Mở rộng xếp hạng | **`RankingStrategy`** với hai điểm móc: đóng góp vào truy vấn và xếp lại sau khi có kết quả; mỗi response mang `searchRequestId` | *Gắn công thức vào truy vấn:* tín hiệu hành vi hay cá nhân hoá có thể phải xếp lại ngoài Elasticsearch. *Thêm `searchRequestId` sau khi có tracking:* dữ liệu trước đó không nối được với lượt bấm | `INV-SRC-12` |
| `T-10` | Nhãn và định danh | Document lưu **định danh** cho lọc; nhãn hiển thị ghép từ kho metadata lúc trả response. Riêng chữ để khớp từ khoá (`attrText`, `brandName`) phải có trong document | *Gộp nhãn vào khoá lọc:* đổi nhãn làm hỏng liên kết bộ lọc đã lưu. *Dịch định danh sang nhãn lúc truy vấn để khớp từ khoá:* không làm được, chữ phải có sẵn trong index. Hệ quả: đổi tên thương hiệu hay nhãn giá trị chuẩn không cập nhật phần chữ trong document cho tới khi có snapshot mới (`TQ-03`, `Q-SRC-06`) | `INV-SRC-08`, `09` |
| `T-11` | Giảm tải Elasticsearch | **Micro-cache TTL 2s** kết hợp gộp truy vấn trùng đồng thời | *TTL dài (vài chục giây):* vi phạm độ tươi 5s của còn hàng. *Không cache:* mọi request thành một truy vấn thật ở mức cao điểm ~10.000 req/s | 12.3 tải, độ tươi |
| `T-12` | Cách ly độ tươi | Consumer group và topic **riêng** cho tồn kho và catalog | *Một luồng chung:* đợt cập nhật hoặc replay lớn của catalog xếp hàng trước sự kiện tồn kho | 12.3 độ tươi còn hàng |
| `T-13` | Rebuild không ngắt | **Alias đọc và alias ghi tách riêng**, đổi alias sau khi dựng xong | *Xoá và dựng lại tại chỗ:* ngắt truy vấn. *Dựng ở một cụm khác:* tốn hạ tầng | §8.2 |

---

## 15. Chưa chốt (kỹ thuật)

| ID | Câu hỏi | Ảnh hưởng | Phụ thuộc |
|---|---|---|---|
| `TQ-01` | **`search_after` không dùng được cùng `collapse` khi xếp hạng nhiều khoá** (`T-08`). Nếu analysis giữ `INV-SRC-13` nguyên nghĩa (không lặp, không sót), phải chọn một trong: (a) chấp nhận mức tốt nhất có thể trong giới hạn độ sâu; (b) bỏ gom theo sản phẩm ở tầng lưu trữ; (c) khoá sắp xếp tĩnh mức sản phẩm. Cần kiểm lại trên phiên bản Elasticsearch được chọn | `INV-SRC-13`, §5 | `Q-SRC-14` |
| `TQ-02` | Phiên bản Elasticsearch, và các tính năng phụ thuộc phiên bản (collapse với rescore và định tuyến shard) | §1, `T-07` | Hạ tầng |
| `TQ-03` | Tên topic và payload của snapshot metadata catalog và của sự kiện tồn kho; bootstrap kho metadata khi khởi động | §7.2, §8.1 | catalog `Q-CAT-04`, tồn kho `Q-SRC-05` |
| `TQ-04` | `cardinality` là xấp xỉ khi tập lớn (ngưỡng chính xác tối đa 40.000). Số đếm sản phẩm có được phép xấp xỉ | `INV-SRC-08` | `Q-SRC-13` |
| `TQ-05` | Chọn bộ phân tích tiếng Việt và bỏ dấu (plugin cộng đồng, hay `asciifolding`/ICU), đo chất lượng trên dữ liệu thật | `INV-SRC-09` | — |
| `TQ-06` | Cách lưu và lọc thuộc tính số và ngày theo khoảng | `INV-SRC-06`, §8.1 | `Q-SRC-02` |
| `TQ-07` | "Giá giảm dần" xếp theo giá đơn vị đại diện (giá trên thẻ) hay giá cao nhất của sản phẩm. Với `collapse`, xếp giá giảm dần lấy đơn vị đắt nhất làm nhóm đầu, lệch với thẻ hiển thị "Từ X" | `INV-SRC-04`, `12` | `Q-SRC-12` |
| `TQ-08` | Chính sách bản cache cũ khi Elasticsearch lỗi: thời hạn tối đa, và có đánh dấu cho người mua không | §7.3 | — |
| `TQ-09` | Con số trần: `size`, độ sâu, độ dài `q`, số giá trị mỗi bộ lọc, số bộ lọc mỗi truy vấn | §13 | `Q-SRC-09` |

---

## 16. Lệch giữa thiết kế và code

**Chưa rà code.** Service này chưa có triển khai được đối chiếu trong bản draft này.

---

## 17. Dependencies

- **Services:** catalog-service (snapshot, cây danh mục và metadata), inventory-service (mức tồn kho — chưa có), web-gateway
  (định tuyến công khai `/api/search/**`).
- **Infrastructure:** Elasticsearch (index `products` cùng alias đọc và ghi), Kafka (các topic ở §7.2 và consumer group
  `search-catalog`, `search-metadata`, `search-stock`), Redis (micro-cache).

---

## 18. Nợ kỹ thuật

> Việc đã biết, đã quyết hoãn có chủ đích. Trả xong thì xoá dòng.

| ID | Hạng mục | Hiện trạng | Hệ quả nếu chưa trả | Vì sao hoãn | Trả khi nào | Liên quan |
|---|---|---|---|---|---|---|
| `TD-01` | Kiểm chứng NFR bằng số đo | Mọi con số ở §12 (ngân sách độ trễ, độ tươi, shard, timeout) là đề xuất chưa đo | Không chứng minh được P99 < 200ms, ~10.000 req/s, độ tươi 5s | Chưa có môi trường và dữ liệu 5M | Trước khi chốt NFR; cần cho flash sale | §12 |
| `TD-02` | Đối soát định kỳ với hai BC nguồn | Chưa có; lệch dữ liệu (event bị mất) chỉ được phát hiện khi người mua báo | Dữ liệu trong search lệch mà không ai biết | Chưa có dữ liệu để chọn cách so sánh và tần suất | Trước go-live | §11, §10 |
| `TD-03` | Giới hạn theo IP ở api-gateway | Ngưỡng 300 req/60s chung cho mọi path rất chặt cho luồng duyệt và tìm kiếm; cách xác định IP sau load balancer chưa xác nhận | Người dùng chung NAT bị 429 oan; đợt flash sale bị chặn | Đang chạy local | Trước khi triển khai môi trường thật; xem catalog `TD-09` | §13, `rate-limiting-layers.md` |

---

## Tài liệu liên quan

- [`analysis.md`](analysis.md) — nghiệp vụ (nguồn sự thật cho quy tắc)
- [`data.md`](data.md) — schema *(chưa tạo)*
- [`api.yaml`](api.yaml) — hợp đồng API *(chưa tạo)*
- [`../catalog-service/service.md`](../catalog-service/service.md) — bên phát snapshot; `TQ-04` ADR về mô hình hai nhánh event

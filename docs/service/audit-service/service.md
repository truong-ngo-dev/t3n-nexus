# Nhật ký kiểm toán: service (`audit-service`)

> **Trạng thái:** Khung (bước 3 của C1a) · **Cập nhật:** `2026-10-23` · **Mã BC:** `AUD`
>
> Chỉ ghi kết luận, ở mức aggregate. Mỗi mục có nhãn **Dự kiến**, **Đã làm** hoặc **Hoãn**. Feature 02 (`2026-10-25`) đã làm các mục của C1a; còn **Dự kiến** là phần do BC khác phát (dòng sự kiện của Người bán, Danh mục hàng, C1b) và đường qua BFF, cổng truy cập. Chi tiết (trường, bảng, cột, chữ ký hàm, mã lỗi) chốt ở bước thiết kế feature và nằm ở mã, `data.md`, `api.yaml`. Nghiệp vụ và lý do: [`analysis.md`](analysis.md).

## 1. Vai trò

Nhận bản ghi hành động từ các BC khác theo một hợp đồng riêng, lưu bất biến, cho Quản trị tra cứu. Không phát sự kiện, không gọi BC khác, không biết nghiệp vụ của bên phát.

## 2. Aggregate

### AGG-AUD-01 Bản ghi kiểm toán (`AuditEntry`) · Đã làm

**Trách nhiệm:** giữ một dòng nhật ký: một hành động đã thành công của một người làm lên một đối tượng.
**Bất biến giữ trong aggregate:** INV-AUD-03, 04, 05, 07. INV-AUD-01, 02, 09 ở mục 3. INV-AUD-06 ở mục 4 (quyền); INV-AUD-08 là việc không làm (không có tác vụ dọn).

**Thuộc tính**

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi (ADR-0001) | UUID v7: sắp xếp theo thời gian, dùng làm thứ tự phụ khi phân trang |
| `sourceEventId` | Mã event | Có | Duy nhất (INV-AUD-02) | Mã event của BC phát; chìa khóa khử trùng |
| `occurredAt` | Thời điểm | Có | Giờ của BC phát (INV-AUD-03) | Dùng để sắp xếp và lọc |
| `recordedAt` | Thời điểm | Có | Giờ Nhật ký lưu | Cho thấy độ trễ |
| `actor` | Giá trị | Có | `accountId` (bỏ trống khi hệ thống) và `role` (`USER`, `ADMIN`, `SYSTEM`, ...); bản chụp tại thời điểm làm (INV-AUD-05) | |
| `action` | Chuỗi | Có | Dạng `ĐỐI_TƯỢNG_HÀNH_ĐỘNG` chữ hoa, dấu gạch dưới; chỉ kiểm định dạng, không kiểm ý nghĩa (INV-AUD-07) | |
| `target` | Giá trị | Có | `type` và `id`; chỉ mã (INV-AUD-03) | |
| `details` | Cặp khóa-giá trị | Không | Phẳng, giới hạn số khóa, độ dài khóa và giá trị; không có khóa thuộc loại dữ liệu định danh (INV-AUD-04) | Lý do từ chối thuộc loại này được phép |
| `sourceService` | Chuỗi | Có | | BC phát |
| `traceId` | Chuỗi | Không | | Nối với log của yêu cầu gốc |

**Hành vi**

| Hành vi | Khi nào chạy | Tiền điều kiện | Hậu điều kiện | Event | Chặng |
|---|---|---|---|---|---|
| Tạo (`record`, hàm tạo) | Nhận `AuditActionRecorded` | Đủ trường bắt buộc, `action` đúng định dạng | Có một bản ghi; `details` đã lọc khóa không hợp lệ | | C1a |

Aggregate **không có hành vi sửa hay xóa**. Hàm tạo là hành vi duy nhất.

**Cổng (do miền khai báo, cài đặt ở hạ tầng):** kho bản ghi (kế thừa kho gốc của `common-domain`: đọc theo mã; `save` chỉ thêm và bỏ qua khi đã có `sourceEventId`; `delete` không hỗ trợ); cổng sinh mã (ADR-0001). **Cổng tìm kiếm** (theo bộ lọc và con trỏ) do tầng ứng dụng khai báo, tách khỏi cổng ghi của miền; bộ lọc và con trỏ phân trang là chi tiết của phía đọc, không thuộc miền. Chính sách dữ kiện bổ sung (`DetailsPolicy`) là hàm thuần của miền: kiểm hình dạng, loại khóa bị cấm, trả về dữ kiện đã lọc kèm danh sách khóa bị bỏ để cảnh báo.

## 3. Nghiệp vụ liên aggregate

Nghiệp vụ chạm cả tập hợp bản ghi hoặc hạ tầng nhận, không thuộc riêng một bản ghi. Cột "Cơ chế": **ràng buộc lưu trữ**, **khử trùng**, **bảo đảm giao hàng**.

| Nghiệp vụ | Tham gia | Cơ chế | Nhất quán | Bất biến | Chặng | Nhãn |
|---|---|---|---|---|---|---|
| Bản ghi chỉ thêm, không sửa, không xóa | Bản ghi (tập hợp) | Ba lớp: không có thao tác sửa/xóa trong mã; vai cơ sở dữ liệu của dịch vụ chỉ có quyền `INSERT` và `SELECT` trên bảng; trigger từ chối `UPDATE` và `DELETE` làm lớp phòng thủ thứ hai. Vai chạy migration tách khỏi vai chạy dịch vụ | Cùng giao dịch | INV-AUD-01 | C1a | Đã làm |
| Một sự kiện nguồn chỉ cho một bản ghi | Bản ghi (tập hợp) | Khử trùng: ràng buộc duy nhất trên `sourceEventId`; ghi bỏ qua khi trùng, không báo lỗi | Cùng giao dịch | INV-AUD-02 | C1a | Đã làm |
| Mọi hành động thành công ở BC phát đến được nhật ký | BC phát, Bus, Bản ghi | BC phát ghi vào hộp thư event cùng giao dịch với hành động; bus giao ít nhất một lần; Nhật ký thử lại, hết số lần thì đưa vào hàng chờ lỗi và cảnh báo | Cuối cùng; chậm vài giây đến vài phút, không mất | INV-AUD-09 | C1a | Đã làm |

Lưu ý: hàng chờ lỗi nghĩa là bản ghi **chưa vào nhật ký**; phải có người xử lý (alert), không bỏ qua im lặng.

## 4. Use case

| Use case | Năng lực | Chặng | Nhãn |
|---|---|---|---|
| Ghi bản ghi kiểm toán khi nhận `AuditActionRecorded` (`CMD-AUD-01`) | CAP-AUD-01 | C1a | Đã làm |
| Tra cứu danh sách: lọc khoảng thời gian, người làm, hành động, đối tượng; mới nhất trước; phân trang theo con trỏ (`occurredAt`, `id`), giới hạn cỡ trang (`RM-AUD-01`) | CAP-AUD-02 | C1a | Đã làm |
| Chi tiết một bản ghi, đủ trường kể cả dữ kiện bổ sung và mã truy vết (`RM-AUD-02`) | CAP-AUD-02 | C1a | Đã làm |

**Quyền (dự kiến: chú thích ở lớp trình bày, mặc định từ chối):** cả hai truy vấn: vai `ADMIN` (INV-AUD-06). **Hoãn ở feature 02:** chưa kiểm token và vai cho tới khi có mô-đun phân quyền. Không có điểm cuối ghi qua API: bản ghi chỉ vào qua bus.

## 5. Event

### Phát

Không — Nhật ký không phát sự kiện nào ở C1a.

### Nhận

| Event | Từ BC | Hành động đi kèm ở C1a | Hiện thực | Chặng | Nhãn |
|---|---|---|---|---|---|
| `AuditActionRecorded` | Người bán | `SELLER_PROFILE_APPROVED`, `SELLER_PROFILE_REJECTED` | `CMD-AUD-01` | C1a | Dự kiến |
| `AuditActionRecorded` | Danh mục hàng | `CATEGORY_*`, `ATTRIBUTE_*`, `BRAND_*` (tạo, sửa, ngừng dùng) | `CMD-AUD-01` | C1a | Dự kiến |
| `AuditActionRecorded` | Xác thực | Khóa, mở tài khoản | `CMD-AUD-01` | C1b | Dự kiến |
| `AuditActionRecorded` | Danh mục hàng | Chặn, mở sản phẩm | `CMD-AUD-01` | C1b | Dự kiến |

**Hợp đồng bản ghi** (do Nhật ký sở hữu; nội dung nghiệp vụ ở `analysis.md` mục 8.2): mã event, thời điểm xảy ra, mã truy vết, BC nguồn, người làm (mã tài khoản, vai), hành động, đối tượng (loại, mã), dữ kiện bổ sung. Phiên bản hợp đồng là `schemaVersion` của khung thông điệp chung (ADR-0008); tiến hoá chỉ thêm trường, không đổi tên hay xóa. Một chủ đề dùng chung cho mọi BC phát (tên dự kiến `audit.action.recorded`). Khóa phân vùng: mã đối tượng, để thứ tự đúng theo từng đối tượng.

**Nhận lặp, sai thứ tự:** khử trùng theo `sourceEventId` (mục 3). Thứ tự giữa các bản ghi không ảnh hưởng đúng sai (mỗi bản ghi độc lập); sắp xếp theo `occurredAt` khi đọc.

**Lỗi nhận:** bản ghi sai định dạng hoặc thiếu trường bắt buộc không lưu, chuyển sang hàng chờ lỗi và cảnh báo, không chặn các bản ghi sau. Lỗi tạm (cơ sở dữ liệu) thử lại có backoff.

## 6. Giao diện giữa BC

| Giao diện | Hướng | Nội dung | Chặng | Nhãn |
|---|---|---|---|---|
| Hợp đồng bản ghi | Mọi BC phát gửi, Nhật ký nhận | `AuditActionRecorded` (mục 5); Nhật ký sở hữu hợp đồng (OHS-PL); BC phát tự khai báo hành động và khóa dữ kiện của mình ở `analysis.md` của nó | C1a | Dự kiến |
| Hợp đồng danh tính | Xác thực công bố, Nhật ký đọc từ token | Vai `ADMIN` để cho phép tra cứu (ADR-0003) | C1a | Hoãn (kiểm token và vai chờ mô-đun phân quyền) |
| API cho ứng dụng Vận hành | Ứng dụng gọi qua BFF và cổng truy cập | `RM-AUD-01`, `RM-AUD-02` | C1a | Đã làm (API); đường qua BFF và cổng là feature 10, 12 |
| Thành phần dựng sự kiện cho BC phát | Thư viện nhỏ dùng chung | Dựng `AuditActionRecorded` đúng hợp đồng và ghi vào hộp thư event của BC phát cùng giao dịch; kiểm khóa dữ kiện mà BC phát khai báo | C1a | Đã làm |

## 7. Khái niệm kỹ thuật

| Khái niệm | Kết luận | ADR |
|---|---|---|
| Mã định danh thực thể | UUID v7 sinh trong miền qua cổng sinh mã, lưu kiểu `uuid` | ADR-0001 |
| Bất biến ở lưu trữ | Vai cơ sở dữ liệu chỉ `INSERT`, `SELECT`; trigger chặn `UPDATE`, `DELETE`; vai migration tách riêng; chuỗi băm chống sửa là điểm nối chưa làm | ADR-0009 |
| Nhận sự kiện | Ít nhất một lần, khử trùng bằng ràng buộc duy nhất; thử lại có backoff, hàng chờ lỗi và cảnh báo; không dựa vào thời hạn giữ của bus | ADR-0008 |
| Phân trang | Con trỏ trên (`occurredAt`, `id`), không dùng độ lệch; cỡ trang có trần | Chờ |
| Dữ kiện bổ sung | Phẳng, giới hạn số khóa, độ dài khóa và giá trị; khóa mang tên loại dữ liệu định danh bị bỏ và cảnh báo (nêu tên khóa, không nêu giá trị) | Quy ước `logging.md` |
| Tăng trưởng | Bảng chỉ tăng; bốn chỉ mục (thời gian, người làm, đối tượng, hành động); phân vùng theo tháng và lưu lạnh là điểm nối chưa làm | Chờ |
| Phân quyền | Chú thích vai `ADMIN` ở lớp trình bày; ABAC hoãn tới cổng Vận hành; kiểm vai hoãn ở feature 02 tới khi có mô-đun phân quyền | Quyết định `2026-10-22` |
| Định dạng event | JSON; nâng Avro kèm Schema Registry là hướng sau | `tech-stack.md` |

## 8. Hạ tầng cần ở C1a

Cơ sở dữ liệu quan hệ riêng của dịch vụ (hai vai: chạy migration và chạy dịch vụ), bộ nhận sự kiện từ bus kèm hàng chờ lỗi, cổng truy cập và route, quan sát theo quy ước chung. Dịch vụ **không** có hộp thư event của riêng nó (không phát sự kiện), nhưng mỗi BC phát cần hộp thư event của mình. Chưa cần Redis.

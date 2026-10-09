# Phân tích yêu cầu phi chức năng (NFR)

## 1. Ba lớp

| Lớp                      | Nội dung                                          | Trạng thái                                     |
|--------------------------|---------------------------------------------------|------------------------------------------------|
| **1. Mục tiêu thiết kế** | Quy mô tham khảo từ sàn thực tế, suy ra từng bước | Số chọn, kèm nhãn nguồn gốc                    |
| **2. Năng lực hạ tầng**  | Máy local đáp ứng tới đâu; cloud sẽ tới đâu       | Local: **đo**. Cloud: ước tính, đo ngắn nếu có |
| **3. Mức chứng minh**    | Mỗi yêu cầu được gắn nhãn đã chứng minh tới đâu   | Do lớp 1 và 2 quyết                            |

**Nguyên tắc:** mã được viết để đáp ứng lớp 1. Kiểm thử chỉ tới trần của lớp 2. Phần nằm trên trần không được trình bày như
đã chứng minh (xem §5).

## 2. Ràng buộc của bằng chứng

| Ràng buộc                                              | Hệ quả                                                              |
|--------------------------------------------------------|---------------------------------------------------------------------|
| Một máy 32 GB, Docker trên WSL2 (mặc định lấy 50% RAM) | Đặt giới hạn RAM/CPU rõ cho từng container; ghi vào điều kiện đo    |
| Công cụ sinh tải chạy cùng máy                         | Số tuyệt đối bị lệch; ưu tiên so sánh trước/sau và tỷ lệ            |
| Không có người dùng thật                               | Tải giả lập phải có hình dạng thật (hàng nóng, tỷ lệ đọc/ghi)       |
| Một người, 2–4 giờ/ngày                                | Mỗi điểm nóng cần một kịch bản đo chạy lại bằng một lệnh            |
| File `.vhdx` của WSL2 chỉ phình, không tự co           | Chừa ≥ 100 GB trống; ghi vị trí; dọn có chủ đích                    |
| Ngân sách gần bằng 0                                   | Cloud chỉ dùng chạy ngắn để kiểm lại mô hình (cuối dự án), không đo toàn bộ |

## 3. Lớp 1: mục tiêu thiết kế

### 3.1 Cách chọn

Dự án mang tính **mô phỏng và học tập**, không cạnh tranh với sàn thật. Vì vậy số liệu thiết kế:

1. **Tham khảo từ sàn thực tế** (ưu tiên sàn Việt Nam) làm khung bậc độ lớn.
2. **Chọn số tương đương** với số tìm được, hoặc **suy luận logic từ các số khác** khi không có số trực tiếp.
3. **Tránh số không thực tế:** không chọn số vượt xa mức lớn nhất tìm được của sàn Việt Nam cùng loại, và không chọn số mâu
   thuẫn với số khác trong bộ (ví dụ số người dùng mỗi ngày không thể ít hơn nhiều so với số đơn mỗi ngày).

Mỗi số mang một trong ba nhãn nguồn gốc:

| Nhãn            | Nghĩa                                                                              |
|-----------------|------------------------------------------------------------------------------------|
| **Tương đương** | Lấy gần một số có nguồn của sàn thực tế                                            |
| **Suy ra**      | Tính từ số khác trong bộ; các hệ số dùng ghi rõ, hệ số nào là giả định cũng ghi rõ |
| **Giả định**    | Không có căn cứ trực tiếp; ghi lý do hợp lý, và đổi được mà không đổi cấu trúc     |

### 3.2 Tham chiếu thực tế đã tìm được

| Số liệu                 | Giá trị                                                                         | Nguồn                            | Độ tin cậy                       |
|-------------------------|---------------------------------------------------------------------------------|----------------------------------|----------------------------------|
| Tiki, lượt truy cập web | 33,7 triệu/tháng (7/2019)                                                       | Thống kê lưu lượng, qua tìm kiếm | Trung bình (cũ)                  |
| Tiki, flash sale        | Lưu lượng tăng 10 lần; "vài nghìn đơn trong một phút"                           | Case study Google Cloud          | Trung bình (định tính)           |
| Tiki, kho hàng          | Hơn 10 triệu sản phẩm                                                           | Case study Google Cloud          | Trung bình                       |
| Tiki, đỉnh sự kiện 2015 | 20.000 yêu cầu/giây                                                             | Slide Tiki 2015                  | Trung bình (cũ 10 năm)           |
| GMV Việt Nam 2024       | Shopee 65% (~10,4 tỷ USD), TikTok Shop 28%, Lazada 6%, Tiki 1% (~200 triệu USD) | Momentum Works, qua báo          | Trung bình                       |
| Shopee toàn cầu 2025    | 13,9 tỷ đơn gộp (≈ 38 triệu/ngày)                                               | Báo cáo Sea Limited              | Cao (nhưng không riêng Việt Nam) |

Không tìm thấy: đơn/ngày thật của Shopee Việt Nam và Tiki; yêu cầu/giây đỉnh của Shopee; người dùng đồng thời của Tiki.
Số Amazon, Alibaba không dùng (ngoài phạm vi Việt Nam).

### 3.3 Tải (đồng thời cao)

| Đại lượng                          | Số chọn                                  | Nhãn        | Cách có                                                                                                                                          |
|------------------------------------|------------------------------------------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| Lượt truy cập/ngày                 | ~1 triệu                                 | Tương đương | Tiki 33,7 triệu/tháng ≈ 1,1 triệu/ngày                                                                                                           |
| Đơn/ngày                           | **50.000** (≈ 0,6/giây trung bình)       | Tương đương | Tiki ước ~30.000 (từ GMV 200 triệu USD, giả định giá trị đơn 15–20 USD); lấy số tròn cao hơn một chút. Tỷ lệ đơn/lượt ≈ 5%, hợp lý               |
| Đồng thời, thường ngày             | **20.000**                               | Suy ra      | 1 triệu lượt × 5 phút ÷ 86.400 ≈ 3.500 trung bình; hệ số đỉnh 3 ≈ 10.000–12.000; làm tròn lên 20.000 để dư. Giả định: phiên 5 phút, hệ số đỉnh 3 |
| Đọc/giây, thường ngày              | **8.000**                                | Suy ra      | 20.000 × 1 yêu cầu/2,5 giây. Giả định: tần suất yêu cầu mỗi người dùng                                                                           |
| Đồng thời, flash sale              | **200.000**                              | Suy ra      | ×10 theo Tiki; Tiki nói lưu lượng, ta áp cho đồng thời                                                                                           |
| Đọc/giây, flash sale               | **80.000**                               | Suy ra      | ×10 từ 8.000; phần lớn do bộ đệm và biên hấp thụ                                                                                                 |
| Đơn tạo ra/giây, flash sale        | **100**                                  | Tương đương | "vài nghìn đơn/phút" = 33–83/giây, làm tròn lên 100                                                                                              |
| Suất mặt hàng hoặc voucher nóng    | **1.000**                                | Giả định    | Hợp lý nếu hết sau ~10 giây ở 100 đơn/giây; số suất thật của sàn không công bố                                                                   |
| Yêu cầu/giây vào một mặt hàng nóng | **2.000**                                | Suy ra      | 100 đơn thành công/giây × 10 lần thử mỗi đơn thành công × hệ số đỉnh 2. **Hai hệ số (10 và 2) là giả định**; số này không có nguồn thực tế       |
| Hình dạng tải                      | Zipf: vài mặt hàng nhận phần lớn yêu cầu | Giả định    | Phổ biến trong thương mại điện tử, chưa có số của sàn Việt Nam                                                                                   |
| Đăng ký/giây, đỉnh | **~1** | Suy ra | 5.000 đăng ký/ngày dồn trong ~12 giờ hoạt động ≈ 0,12/giây, hệ số đỉnh 3 ≈ 0,4/giây, làm tròn lên 1. Giả định: 5.000 đăng ký/ngày (3 triệu tài khoản chia 2 năm), 12 giờ hoạt động. Đăng ký không tăng đột biến lúc flash sale: tải của flash sale nằm ở đăng nhập và các thao tác ngay trước giờ mở |
| Đăng nhập/giây, thường ngày đỉnh | **~5** | Suy ra | 150.000 người đăng nhập mới mỗi ngày ≈ 1,7/giây, hệ số đỉnh 3. Giả định: phiên dài nên mỗi người đăng nhập mới khoảng một lần mỗi ngày |
| Đăng nhập/giây, flash sale | **~700** | Giả định | 200.000 đồng thời × 20% cần đăng nhập mới trong 1 phút ≈ 40.000 ÷ 60. Hai tỷ lệ 20% và 1 phút là giả định. Mỗi lần đăng nhập tốn CPU để băm mật khẩu nên cần đo bằng bộ đo nền |
| Làm mới token/giây | **~22 thường ngày; ~220 flash sale** | Suy ra | Số phiên đồng thời ÷ thời hạn token truy cập. Giả định: thời hạn 15 phút |

Thử đến điểm gãy: với mặt hàng nóng, tăng tải tới **8.000 yêu cầu/giây**. Đây là trần thử, không phải mục tiêu thiết kế.

### 3.4 Dữ liệu

| Đại lượng    | Số chọn                                                       | Nhãn        | Cách có                                                                                       |
|--------------|---------------------------------------------------------------|-------------|-----------------------------------------------------------------------------------------------|
| SKU          | **5 triệu**                                                   | Tương đương | Tiki hơn 10 triệu sản phẩm; lấy một nửa cho sàn cỡ vừa                                        |
| Đơn tích lũy | **18 triệu**                                                  | Suy ra      | 50.000 × 365 (một năm)                                                                        |
| Shipper      | **~2.000**                                                    | Suy ra      | 50.000 đơn ÷ 25 đơn/shipper/ngày. Hệ số 25 là giả định                                        |
| Điểm GPS     | **~400/giây trong ca** (≈ 11,5 triệu dòng/ngày, ~1,2 GB/ngày) | Suy ra      | 2.000 shipper × 1 điểm/5 giây, ca 8 giờ. Tần suất 5 giây là lựa chọn thiết kế, không có nguồn |
| Tài khoản Người dùng | **~3 triệu** | Suy ra | 50.000 đơn × 30 ÷ 1,5 đơn/người/tháng ≈ 1 triệu người mua hoạt động mỗi tháng, nhân 3 (có tài khoản ngủ). Hai hệ số 1,5 và 3 là giả định |
| Người đăng nhập mỗi ngày | **~150.000** | Suy ra | 1 triệu lượt ÷ 2 lượt/người ≈ 500.000 người, 30% đăng nhập. Hai hệ số là giả định. Lớn hơn nhiều so với ~40.000 người mua mỗi ngày nên không mâu thuẫn với số đơn |
| Bản ghi phiên | **≤ ~400.000** | Suy ra | Người đăng nhập trong 24 giờ, cộng đỉnh flash sale |
| Lịch sử đăng nhập | **~15 triệu dòng** (≈ 4–5 GB) | Giả định | ~165.000 dòng/ngày (kể cả ~10% thất bại) × giữ 90 ngày, ~300 byte/dòng. Thời hạn giữ và kích thước dòng là giả định |
| Thiết bị | **~1,5 triệu** | Giả định | 1 triệu người hoạt động × 1,5 thiết bị mỗi người |

Chat, thông báo, sự kiện đơn: chưa tính; chỉ liên quan khi làm thời gian thực, xét khi tới đó.

### 3.5 Độ trễ (P99)

Nhãn **Giả định**: ngưỡng phổ biến cho thao tác tương ứng, chưa có số P99 của sàn Việt Nam. Tiki công bố API chính 55 ms nhưng đó không phải P99.

| Thao tác                      | Mục tiêu |
|-------------------------------|---------- |
| Tìm kiếm có lọc               | < 200 ms |
| Xem chi tiết sản phẩm         | < 100 ms |
| Thêm vào giỏ                  | < 200 ms |
| Thanh toán (phản hồi đồng bộ) | < 2 s |
| Vị trí shipper → người mua    | < 500 ms |
| Thông báo trong ứng dụng      | < 1 s |

### 3.6 Chưa tính

| Đại lượng                                 | Lý do                                                 |
|-------------------------------------------|-------------------------------------------------------|
| Ghi/giây thường ngày                      | Chưa có cách suy ra từ số có nguồn; tính khi có số đo |
| Thời lượng flash sale                     | Không có số có nguồn                                  |
| Yêu cầu giữa các dịch vụ nội bộ (fan-out) | Phụ thuộc kiến trúc, tính khi chọn công nghệ          |

## 4. Lớp 2: năng lực hạ tầng

### 4.1 Local (cần đo)

Không đoán; đo bằng **bộ đo nền** trước khi đo ứng dụng: điểm cuối rỗng, ghi một dòng, băm một mật khẩu, thao tác bộ đệm, một truy vấn
tìm kiếm. Trần của bộ đo nền là giới hạn của máy; phần chênh với ứng dụng là giới hạn do mã. Ba phần đầu đo trước tiên; bộ đệm
và tìm kiếm đo khi hạ tầng đó được bật.

| Đại lượng                                | Trần máy (bộ đo nền) | Trần ứng dụng | Ghi chú |
|------------------------------------------|----------------------|---------------|---------|
| Đọc/giây                                 | _chưa đo_            | _chưa đo_     |         |
| Ghi/giây                                 | _chưa đo_            | _chưa đo_     |         |
| Băm mật khẩu/giây                        | _chưa đo_            | _chưa đo_     | Điểm nóng đăng nhập (làn sóng trước flash sale) |
| Thao tác giữ chỗ trên mặt hàng nóng/giây | _chưa đo_            | _chưa đo_     | Điểm nóng đồng thời cao |
| Truy vấn tìm kiếm ở quy mô dữ liệu       | _chưa đo_            | _chưa đo_     | Điểm đo dữ liệu lớn     |

Dung lượng dữ liệu ước lượng (theo kích thước dòng, chưa đo): 5 triệu SKU ≈ 5 GB cơ sở dữ liệu + 4–8 GB chỉ mục tìm kiếm;
18 triệu đơn ≈ 15–30 GB; GPS ≈ 1,2 GB/ngày thiết kế (cần phân vùng theo ngày và thời hạn giữ). Quy mô đo nhỏ hơn theo §4.3.

### 4.2 Cloud (ước tính)

Năng lực ≈ số thể hiện × thông lượng đo được trên mỗi thể hiện. **Chỉ đáng tin nếu mở rộng ngang tuyến tính**, nên
kiểm bằng §5.3. Cloud chỉ dùng cuối dự án: cụm thiết yếu, chạy ngắn rồi tắt, để lấy một hai điểm đo kiểm lại mô hình. Chi phí chạy
và nơi đo chọn khi tới lúc đó.

### 4.3 Quy mô đo

| Dữ liệu            | Thiết kế         | Đo local        | Lý do          |
|--------------------|------------------|-----------------|----------------|
| SKU                | 5 triệu          | 3–5 triệu       | Vừa đĩa và RAM |
| Đơn                | 18 triệu         | 10 triệu        | Vừa đĩa        |
| Shipper            | 2.000            | 200             | Giữ tỷ lệ 1/10 |
| Tài khoản          | 3 triệu          | 1 triệu         | Vừa đĩa; sinh dữ liệu bằng giá trị băm mật khẩu tính trước |
| Người dùng giả lập | 20.000 / 200.000 | 500–2.000 luồng | Giới hạn máy   |

Quy mô cụ thể trong khoảng (3 hay 5 triệu SKU) chọn theo đĩa trống khi tới lúc đo.

Giới hạn RAM container cơ sở dữ liệu và tìm kiếm **nhỏ hơn** tập dữ liệu (ví dụ 2 GB mỗi bên). Nếu toàn bộ dữ liệu nằm gọn
trong bộ nhớ đệm, truy vấn nhanh dù chỉ mục kém và điểm đo dữ liệu lớn mất giá trị.

## 5. Lớp 3: mức chứng minh

### 5.1 Ba nhãn cho mỗi yêu cầu

| Nhãn             | Nghĩa                                                                                     |
|------------------|-------------------------------------------------------------------------------------------|
| **Đã đo**        | Có số đo trong điều kiện ghi rõ                                                           |
| **Suy ra**       | Có số đo và mô hình (ví dụ nút cổ chai là CPU, thông lượng tăng tuyến tính theo thể hiện) |
| **Chỉ thiết kế** | Mã viết để đáp ứng nhưng chưa có số đo                                                    |

Báo cáo ghi nhãn cạnh mỗi con số. Cấm viết số "chỉ thiết kế" như số đã đo. (Nhãn mức chứng minh này khác nhãn nguồn gốc ở
§3.1: nguồn gốc nói số mục tiêu từ đâu ra, mức chứng minh nói mã đã được kiểm tới đâu.)

Hai kỹ thuật dưới đây là cách có cơ sở gắn nhãn **"Suy ra"** cho phần nằm trên trần hạ tầng mà không cần máy lớn hơn.

### 5.2 Đo theo tỷ lệ

**Dùng cho:** tải toàn hệ thống và dữ liệu lớn (ví dụ 80.000 đọc/giây vượt trần máy).

Giảm tài nguyên và tải **cùng một tỷ lệ**: giới hạn container còn 1/10 tài nguyên (ví dụ 0,4 nhân thay vì 4), chạy tải 1/10
mục tiêu (8.000 đọc/giây thay vì 80.000). Nếu hệ thống đạt ngưỡng độ trễ ở đó thì có cơ sở tin 10 lần tài nguyên xử lý được
10 lần tải. Ghi rõ hệ số.

**Điều kiện đúng:** nút cổ chai tăng tuyến tính theo tài nguyên (CPU, số kết nối).

**Không dùng cho mặt hàng nóng.** Mọi yêu cầu tranh một hàng hoặc một khóa, nên thêm tài nguyên không làm tăng thông lượng
của chính khóa đó. Với điểm nóng đồng thời cao phải đo trực tiếp thông lượng tối đa của một khóa ở mức tải thiết kế.

### 5.3 Chứng minh mở rộng

**Dùng cho:** nói được "thêm máy thì xử lý được nhiều tải hơn" khi chỉ có một máy.

Chạy cùng hệ thống với 1, 2, 4 thể hiện của dịch vụ (hoặc phân vùng), đo thông lượng tối đa mỗi cấu hình:

| Cấu hình   | Thông lượng (ví dụ minh họa) | Kết luận                                       |
|------------|------------------------------|------------------------------------------------|
| 1 thể hiện | 1.000/giây                   |                                                |
| 2 thể hiện | 1.900/giây                   | Gần tuyến tính: có cơ sở nói thêm máy thì tăng |
| 4 thể hiện | 3.600/giây                   | Vẫn gần tuyến tính                             |

Nếu từ 1 lên 2 thể hiện mà thông lượng gần như đứng yên (ví dụ 1.000 lên 1.100) thì có thứ dùng chung đang chặn (cơ sở dữ
liệu, một khóa). Tìm ra **nút cổ chai ở đâu** là kết quả có giá trị, và là chỗ để giải thích khi bị hỏi xoáy.

**Điều kiện:** gắn mỗi thể hiện một lượng CPU cố định (ví dụ 1 nhân), và để cơ sở dữ liệu cùng công cụ sinh tải có nhân
riêng. Nếu không, các thể hiện tranh nhau CPU của một máy và thông lượng không tăng dù thiết kế tốt. Số thể hiện tối đa
phụ thuộc số nhân của máy; **chưa kiểm tra**, nên chưa chắc 4 thể hiện khả thi.

## 6. Đúng đắn dưới tải (không đánh đổi)

Nguồn là các ràng buộc ở `4-context-map.md`.

| Bất biến                                       | Kiểm tra sau mỗi lần chạy tải         |
|------------------------------------------------|---------------------------------------|
| Tồn khả dụng không âm; không bán vượt tồn      | Tổng giữ chỗ + tồn còn = tổng ban đầu |
| Suất flash sale và voucher không vượt giới hạn | Suất đã cấp ≤ giới hạn                |
| Một yêu cầu lặp không tạo hai kết quả          | Gửi lặp có chủ đích, đếm kết quả      |
| Công nợ shipper = thu − nộp, không âm          | Chỉ khi làm Shipper và Sổ cái         |

Một lần chạy tải **chỉ hợp lệ** khi kiểm tra sau chạy đạt. Thông lượng của lần chạy làm vỡ bất biến không được báo cáo.

## 7. Chịu lỗi và khả năng lần vết

| Yêu cầu                                                    | Mức                                                                  |
|------------------------------------------------------------|------------------------------------------------------------------------------|
| Lỗi không bị nuốt, phân biệt lỗi nghiệp vụ và lỗi hệ thống | Mọi nơi, kể cả frontend                                                      |
| Truy vết một đơn xuyên các BC                              | Một mã tương quan đi qua log và event; tìm toàn bộ dấu vết bằng một truy vấn |
| Chịu mất một thành phần phụ (chỉ mục tìm kiếm, bộ đệm)     | Luồng mua chính vẫn chạy; phục hồi được; kiểm bằng tiêm lỗi lúc đo điểm nóng đồng thời cao |
| Xử lý event lặp                                            | Mọi consumer chịu được nhận lặp                                              |
| Sẵn sàng (uptime)                                          | **Không đặt mục tiêu.** Chỉ đo phục hồi sau lỗi                              |

## 8. Khuôn bằng chứng

Mỗi số đo đưa vào báo cáo phải đủ các mục, nếu không thì không tính là bằng chứng:

1. Kịch bản (một lệnh chạy lại được) và phiên bản mã.
2. Hình dạng tải (số luồng, phân bố, thời lượng, thời gian làm nóng).
3. Điều kiện: CPU/RAM từng container, kích thước dữ liệu, có sinh tải cùng máy.
4. Kết quả: thông lượng, P50/P95/P99, tỷ lệ lỗi; **kèm kiểm tra bất biến** (§6).
5. So sánh: trước/sau quyết định thiết kế, hoặc điểm gãy.
6. Lặp ít nhất 3 lần; báo cáo khoảng, không báo một lần tốt nhất.
7. **Nhãn mức chứng minh** (§5.1) và, nếu "suy ra", mô hình dùng.
8. Giải thích: quyết định nào tạo ra khác biệt.

Nếu không đạt do tranh chấp CPU với công cụ sinh tải thì phải chứng minh nguyên nhân bằng đo CPU, không tự nới ngưỡng.

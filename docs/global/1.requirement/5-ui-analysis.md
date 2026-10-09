# Phân tích giao diện (UI)

## 1. Nguyên tắc

| # | Nguyên tắc                                                                                             | Gốc                        |
|---|--------------------------------------------------------------------------------------------------------|----------------------------|
| 1 | Giao diện suy ra từ **bên tham gia** và việc họ làm, không từ danh sách tính năng                      | Overview ý 4               |
| 2 | Thiết bị là suy ra từ ngữ cảnh làm việc của bên tham gia, không phải lựa chọn công nghệ                | Mục 2 (ngữ cảnh shipper)   |
| 3 | Mức "ổn": nhất quán, dễ đọc, bố cục theo luồng. Không tinh chỉnh thẩm mỹ                               | Overview mục 3 và 4        |
| 4 | Frontend là điểm yếu của nguồn lực, nên **chi phí duy trì mỗi portal** là tiêu chí chia                | Overview mục 5             |
| 5 | Sàn chung (bắt lỗi, log) áp dụng cả cho frontend                                                       | Overview ý 5               |
| 6 | Mỗi portal chỉ có màn hình phục vụ một việc trong bản đồ nghiệp vụ; không thêm màn hình chỉ vì "đủ bộ" | P1                         |

## 2. Bên tham gia và ngữ cảnh làm việc

| Bên                       | Làm việc ở đâu, thế nào                                                                                     | Thiết bị suy ra                                                       | Tần suất dùng         |
|---------------------------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|-----------------------|
| Khách vãng lai, người mua | Duyệt, mua; lúc nào cũng có thể                                                                             | Web đáp ứng, dùng được cả máy tính và điện thoại                      | Rải rác               |
| Người bán                 | Niêm yết, xử lý đơn, theo dõi bán; làm việc có chủ đích, nhiều dữ liệu dạng bảng                            | Chủ yếu máy tính                                                      | Hằng ngày             |
| Quản trị nền tảng         | Duyệt, cấu hình, theo dõi vận hành; nhiều bảng, nhiều bộ lọc                                                | Máy tính                                                              | Hằng ngày             |
| Nhân viên kho             | Nhận hàng, đối chiếu, xuất cho shipper; tại quầy kho                                                          | Máy tính (web); thiết bị khác (máy tính bảng, máy quét) ngoài phạm vi | Hằng ngày             |
| **Shipper**               | **Ngoài đường**: nhận đơn, bật sẵn sàng, xác nhận giao, thu COD; tay bận, ánh sáng mạnh, mạng không ổn định | **Điện thoại (bắt buộc)**                                             | Liên tục khi làm việc |

Bên thanh toán và kênh thông báo không có giao diện của nền tảng.

## 3. Chia portal

### 3.1 Tiêu chí chia

Hai bên ở chung một portal khi **đồng thời**: cùng thiết bị, cùng mức tin cậy, nhịp thay đổi gần nhau, và tách ra không
giảm chi phí. Chia riêng khi một trong các điều kiện sau đúng:
1. Khác thiết bị hoặc ngữ cảnh làm việc (shipper ngoài đường khác nhân viên ở quầy).
2. Khác mức quyền và rủi ro lộ chức năng (chức năng quản trị không được nằm trong mã người mua tải về).
3. Khác nhịp thay đổi (cửa hàng đổi thường xuyên, quản trị ít).

### 3.2 Phương án

| Phương án                | Gồm                                                                                  | Ưu                                             | Nhược                                                                         |
|--------------------------|--------------------------------------------------------------------------------------|------------------------------------------------|-------------------------------------------------------------------------------|
| A. Ba portal             | Cửa hàng (khách, người mua) · Người bán · Vận hành (quản trị + kho + shipper)          | Ít ứng dụng nhất                               | Shipper phải dùng giao diện nội bộ trên điện thoại; trái ngữ cảnh ngoài đường |
| **B. Bốn portal (chốt)** | Cửa hàng · Người bán · **Vận hành (quản trị + nhân viên kho)** · **Shipper (di động)** | Mỗi portal một ngữ cảnh; shipper đúng thiết bị | Bốn ứng dụng, tốn công hơn với frontend yếu                                   |
| C. Một cổng gộp          | Tất cả                                                                               | Một mã nguồn                                   | Lộ chức năng chéo vai; thiết bị khác nhau không dung hòa được                 |

**Chốt B**, vì A đẩy shipper vào giao diện sai ngữ cảnh (mâu thuẫn nguyên tắc 2), còn C vi phạm tiêu chí quyền. Cái giá
(bốn ứng dụng) giảm bằng cách: dùng chung bộ thành phần giao diện và quy ước chung giữa ba portal web, và **làm portal
Shipper tối giản** (vài màn hình, phục vụ đúng việc giao hàng).

### 3.3 Shipper

**Không làm web riêng cho shipper.** Cân hai nhu cầu có thể đòi web:

| Nhu cầu                                                | Ai cần                  | Thiết bị tự nhiên                                  | Chỗ đặt                  |
|--------------------------------------------------------|-------------------------|----------------------------------------------------|--------------------------|
| Xem thống kê việc, lịch sử của chính mình              | Shipper                 | Điện thoại (cuối ngày xem ngay trên máy đang dùng) | Portal Shipper (di động) |
| Xem hiệu suất của các shipper                          | Quản trị, nhân viên kho | Máy tính                                           | Portal Vận hành (web)      |

Công nợ COD không có màn hình ở mức cơ sở: công nợ, nộp tiền và đối soát COD là phần cắt tạm (`4-context-map.md` B27),
kéo vào cùng Sổ cái.

Shipper không làm việc ở máy tính, nên web riêng cho họ gần như không có người dùng và thêm một bề mặt thứ năm.

Dạng triển khai của portal Shipper (web trên điện thoại hay ứng dụng gốc) là **chọn công nghệ**, không quyết ở đây. Điều đã
chốt chỉ là nó chạy trên điện thoại, và phần nhập vị trí gọi **cùng giao diện nhập** với bộ giả lập (`4-context-map.md`
B27). Đi thử di chuyển thật làm sau.

### 3.4 Mức cơ sở và mở rộng

- **Mức cơ sở:** web cho mọi vai trừ shipper (ba portal web: Cửa hàng, Người bán, Vận hành); shipper dùng portal di động riêng.
  Cửa hàng là web đáp ứng nên người mua dùng được trên điện thoại.
- **Mở rộng (cắt tạm, P3): ứng dụng di động riêng cho các vai khác** (người mua, người bán). Điểm nối: web đáp ứng của Cửa
  hàng và Người bán đã dùng được trên điện thoại; API không phụ thuộc loại ứng dụng. Điều kiện kéo vào: lõi xong và còn
  nguồn lực. Không làm ở mức cơ sở.

## 4. Nhóm màn hình theo portal (mức tên)

### 4.1 Cửa hàng (khách vãng lai, người mua)
| Nhóm                                   | BC nguồn                 |
|----------------------------------------|--------------------------|
| Đăng ký, đăng nhập                     | Xác thực                 |
| Duyệt, tìm, lọc, xem chi tiết          | Tìm kiếm, Danh mục hàng  |
| Giỏ hàng                               | Giỏ hàng                 |
| Nhập và xem ưu đãi voucher khi đặt     | Khuyến mãi, Giỏ hàng     |
| Địa chỉ, đặt hàng, chọn thanh toán     | Khách hàng, Đơn hàng     |
| Theo dõi đơn, hủy đơn                  | Đơn hàng, Giao nhận      |
| Đánh giá                               | Đánh giá                 |
| Thông báo, chat, bản đồ vị trí shipper | Thông báo, Chat, Shipper |

### 4.2 Người bán
| Nhóm                                     | BC nguồn                   |
|------------------------------------------|----------------------------|
| Đăng ký, chờ duyệt, hồ sơ gian hàng      | Người bán                  |
| Sản phẩm, biến thể, giá, ảnh             | Danh mục hàng, Quản lý tệp |
| Tồn kho (nhập, điều chỉnh)               | Tồn kho                    |
| Đơn mới, xác nhận hoặc từ chối, bàn giao | Đơn hàng, Giao nhận        |
| Báo cáo bán hàng                         | Báo cáo                    |
| Chat với người mua                       | Chat                       |

### 4.3 Vận hành (quản trị và nhân viên kho)
| Nhóm                                                   | Vai                     | BC nguồn                          |
|--------------------------------------------------------|-------------------------|-----------------------------------|
| Duyệt người bán, quản lý tài khoản                     | Quản trị                | Người bán, Xác thực, Định danh    |
| Danh mục, thuộc tính, thương hiệu, cấu hình, vùng giao | Quản trị                | Danh mục hàng, Dữ liệu tham chiếu |
| Chặn, mở sản phẩm                                      | Quản trị                | Danh mục hàng                     |
| Phát hành và quản lý voucher                           | Quản trị                | Khuyến mãi                        |
| Nhận hàng, đối chiếu, xuất cho shipper                 | Nhân viên kho           | Kho vật lý                        |
| Xem hiệu suất của các shipper                          | Quản trị, nhân viên kho | Shipper, Giao nhận                |
| Theo dõi vận hành, báo cáo toàn sàn                    | Quản trị                | Báo cáo                           |
| Nhật ký kiểm toán                                      | Quản trị                | Nhật ký kiểm toán                 |

Việc người bán có tự phát hành voucher cho gian hàng của mình hay không chưa quyết; việc đó thuộc chi tiết BC Khuyến mãi.

Điều khiển giả lập và xem số đo **không phải màn hình sản phẩm**: là công cụ của người chạy thử (chính người làm dự án),
không phải bên tham gia của nền tảng. Phân tích cùng BC Giả lập, không tính vào bốn portal.

### 4.4 Shipper (di động)
| Nhóm                                                              | BC nguồn           |
|-------------------------------------------------------------------|--------------------|
| Đăng nhập, bật/tắt sẵn sàng                                       | Xác thực, Shipper  |
| Nhận và xem đơn được phân                                         | Giao nhận          |
| Xác nhận giao (đơn chuyển "đã thanh toán")                        | Giao nhận          |
| Giao thất bại                                                     | Giao nhận          |
| Xem thống kê việc của mình (đơn đã giao, lịch sử)                 | Shipper, Giao nhận |
| Gửi vị trí thật                                                   | Shipper            |

## 5. Yêu cầu chéo cho mọi portal

| Yêu cầu                 | Nội dung                                                                                             |
|-------------------------|------------------------------------------------------------------------------------------------------|
| Trạng thái              | Mỗi màn hình có đủ trạng thái đang tải, rỗng, lỗi nghiệp vụ, lỗi hệ thống; lỗi không bị nuốt         |
| Truy vết                | Lỗi hệ thống hiển thị mã tương quan để lần ra trong log (khớp NFR §7)                                |
| Quyền                   | Giao diện chỉ hiện chức năng theo vai, nhưng quyền thật do BC kiểm; ẩn nút không thay cho kiểm quyền |
| Nhất quán               | Một bộ thành phần, màu, cách bố cục dùng chung cho bốn portal                                        |
| Thao tác lặp            | Nút gửi chống bấm đúp; thao tác ghi gửi mã chống lặp (khớp P4 xử lý lặp)                             |
| Mạng không ổn định      | Chỉ cần cho Shipper: thao tác thất bại phải thử lại được, không mất, không ghi hai lần xác nhận giao   |
| Phản hồi thời gian thực | Thông báo, vị trí, chat dùng kênh thời gian thực; khi chưa có kênh đó làm mới bằng tải lại                                      |

## 6. Điểm sâu frontend

Cùng cách xét với P5 (đòi hỏi hay chủ động chọn): chỉ giữ điểm là **nửa còn lại** của một điểm sâu backend đã có, vì
thiếu nó thì bằng chứng backend vẫn cho người dùng thấy sai (bấm hai lần ra hai đơn, mất tin sau khi mất mạng). Khác
mục 5 (sàn áp cho mọi màn hình), mỗi điểm dưới đây có cách kiểm riêng. **Giữ cả bốn điểm**; F2 và F3 là
hai điểm tốn công nhất, cân nhắc lại khi tới lượt làm.

| #  | Điểm sâu                                                                                                                                                                                                      | Nửa của điểm sâu backend             | Gốc                          | Cách kiểm                                                                                                             |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| F1 | **Gửi lặp và dữ liệu cũ khi tranh chấp:** mã chống lặp kèm yêu cầu ghi; xử lý bị từ chối vì hết suất hoặc quá tải (thử lại có giãn cách); đếm ngược flash sale theo giờ máy chủ, không theo đồng hồ máy khách | Giữ chỗ và voucher tranh chấp  | Đòi hỏi; đồng thời cao       | Bấm đúp hoặc gửi lặp một đơn chỉ ra một kết quả; máy khách lệch giờ vẫn đếm đúng                                      |
| F2 | **Dữ liệu lớn trên giao diện:** bảng ảo hóa, phân trang theo con trỏ, tải xuống dạng luồng                                                                                                                    | Báo cáo và xuất file lớn | Đòi hỏi; dữ liệu lớn         | Bảng chục nghìn dòng cuộn mượt, bộ nhớ trình duyệt không tăng theo số dòng; xuất file lớn không nạp cả tập vào bộ nhớ |
| F3 | **Kết nối thời gian thực đúng:** nối lại với độ trễ tăng dần cộng ngẫu nhiên; loại tin trùng; giữ thứ tự; xin bù tin lỡ từ mốc cuối; gộp cập nhật trước khi vẽ (vị trí shipper)                               | Thông báo, chat, vị trí shipper | Chủ động chọn; đồng thời cao | Ngắt mạng rồi nối lại không mất và không trùng tin; luồng điểm GPS tần suất cao không làm giao diện giật              |
| F4 | **Thao tác bền khi mạng yếu (Shipper):** hàng đợi cục bộ, thử lại an toàn, không xác nhận giao hai lần                                                                                                        | Xác nhận giao, nhập vị trí           | Đòi hỏi                      | Ngắt mạng giữa lúc xác nhận giao, nối lại: đơn chuyển trạng thái đúng một lần                                         |

**Giữ ở mức sàn, không là điểm sâu:** tải trang (tách gói mã, ảnh vừa phải, bộ nhớ đệm); dựng sẵn phía máy chủ cho trang
sản phẩm (gắn với tìm kiếm trên Google, ngoài mục tiêu hiện tại); tải ảnh lên (chỉ giới hạn kích thước). Không đặt mục
tiêu tối ưu điểm Lighthouse hay vi tối ưu thời gian dựng, vì overview giữ giao diện ở mức "ổn".

**Phụ thuộc:** F1 và F4 cần phía backend chấp nhận mã chống lặp; phần đó thuộc chi tiết từng BC, không lặp ở đây. F3 cần
hợp đồng của kênh thời gian thực (đánh số tin, xin bù) từ lúc phân tích BC Thông báo và Chat.

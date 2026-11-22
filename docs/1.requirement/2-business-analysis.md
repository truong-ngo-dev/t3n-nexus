# Phân tích nghiệp vụ sơ bộ

File này liệt kê nghiệp vụ của đề tài, chọn ra những nghiệp vụ dự án sẽ làm theo yêu cầu của `1-project-overview.md`, rồi nêu
những kỹ năng thể hiện được qua các nghiệp vụ đó. Mức chi tiết: chỉ tên nghiệp vụ. Chi tiết từng nghiệp vụ phân tích khi
triển khai; chia bounded context ở file sau; làm cái nào trước do roadmap quyết.

## 1. Nghiệp vụ của đề tài

Một marketplace thương mại điện tử gồm gần như các nghiệp vụ sau (chưa chọn):

- **Khám phá:** duyệt theo danh mục · tìm và lọc · xem chi tiết sản phẩm · gợi ý, cá nhân hóa · quảng cáo trong kết quả ·
  livestream bán hàng.
- **Giao dịch:** giỏ hàng · tính giá và phí · voucher, flash sale · đặt hàng · giữ chỗ tồn kho · thanh toán trực tuyến, COD,
  trả góp · xuất hóa đơn.
- **Giao nhận:** phí vận chuyển · bàn giao · kho do nền tảng giữ · đội giao, phân công · theo dõi vị trí · xác nhận giao.
- **Sau giao dịch:** hủy đơn · trả hàng, hoàn tiền · khiếu nại, phân xử · đánh giá · điểm tích lũy.
- **Phía người bán:** đăng ký và duyệt · gian hàng · niêm yết sản phẩm · quản lý tồn kho · xử lý đơn · số dư và chi trả ·
  báo cáo bán hàng · quảng cáo.
- **Giao tiếp:** thông báo · chat người mua với người bán.
- **Vận hành nền tảng:** tài khoản, phân quyền, khóa · kiểm duyệt nội dung · đối soát tiền · báo cáo nền tảng · theo dõi hành
  vi · cấu hình.

## 2. Chọn nghiệp vụ

### 2.1 Tiêu chí chọn

| Yêu cầu của overview                                                                                                  | Dẫn tới                                                                |
|-----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| Sàn "sản phẩm phải chạy được từ đầu đến cuối" (mục 5); điều kiện dừng của T4: luồng mua hàng chính chạy qua giao diện | **Lõi**: thiếu thì luồng mua hàng không chạy được                      |
| T1, T2: mỗi kỹ thuật và bài toán phải gắn với một nghiệp vụ thật                                                      | **Mở rộng**: không bắt buộc để chạy, nhưng sinh thêm bài toán kỹ thuật |
| Ngoài mục tiêu: độ rộng tính năng, đào sâu nghiệp vụ (mục 6); "ít mà sâu" (mục 5)                                     | **Không chọn**: chỉ thêm độ rộng, ít bài toán mới                      |

### 2.2 Quyết định chung

- Một thị trường, Việt Nam; tiền tệ VND. Nhiều thị trường chỉ thêm độ rộng (thuế, tiền tệ), không thêm bài toán.
- Hai phương thức thanh toán: trực tuyến qua môi trường thử nghiệm của bên thanh toán (ràng buộc ở overview mục 4) và COD
  (phổ biến ở thị trường Việt Nam). Cả hai đi trên cùng một luồng: **giữ đơn trước, thanh toán sau**; chỉ khác lúc trả tiền
  (ngay sau khi đặt, trong hạn giữ chỗ; hay khi nhận hàng) và cách hủy khi không trả.
- Không có hãng vận chuyển thật: giao nhận là giả lập.

### 2.3 Nghiệp vụ lõi

| Nghiệp vụ             | Gồm (chỉ tên)                                                                                                      | Vì sao là lõi                                                         |
|-----------------------|--------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| Tài khoản và xác thực | Đăng ký · đăng nhập · phiên · vai và quyền                                                                         | Mọi luồng cần biết ai đang thao tác                                   |
| Người bán             | Nộp hồ sơ · duyệt hoặc từ chối · nộp lại · gian hàng                                                               | Không có người bán thì không có hàng                                  |
| Danh mục sản phẩm     | Sản phẩm, biến thể · giá · ảnh · đăng, gỡ · danh mục, thuộc tính                                                   | Thứ được mua                                                          |
| Tìm kiếm và khám phá  | Tìm văn bản · lọc · sắp xếp · xem chi tiết                                                                         | Bước đầu của luồng mua                                                |
| Giỏ hàng              | Thêm, sửa, xóa · giỏ vãng lai · gộp khi đăng nhập                                                                  | Bước của luồng mua                                                    |
| Tồn kho               | Số bán được · giữ chỗ khi đặt · nhả khi hủy hoặc quá hạn                                                           | Không giữ chỗ thì bán vượt, nghiệp vụ sai                             |
| Đơn hàng              | Đặt (giữ đơn trước, thanh toán sau) · tách theo người bán · người bán xác nhận · hủy · lịch sử                     | Trung tâm của giao dịch                                               |
| Thanh toán            | Trực tuyến: tạo thanh toán · nhận kết quả · hoàn tiền khi hủy. COD: thu tiền khi giao · hoàn tồn khi giao thất bại | Bước của luồng mua; COD cần để nghiệp vụ đúng với thị trường Việt Nam |
| Giao nhận             | Phí vận chuyển · trạng thái giao · xác nhận giao                                                                   | Đóng vòng đời đơn                                                     |
| Thông báo             | Báo trạng thái đơn, kết quả duyệt                                                                                  | Phần đóng của mọi nghiệp vụ có kết quả                                |
| Quản trị              | Duyệt người bán · khóa tài khoản · chặn sản phẩm                                                                   | Phần đóng của duyệt người bán                                         |

### 2.4 Nghiệp vụ mở rộng

| Nghiệp vụ                   | Gồm (chỉ tên)                                         | Bài toán nó sinh thêm                                     |
|-----------------------------|-------------------------------------------------------|-----------------------------------------------------------|
| Khuyến mãi                  | Flash sale suất giới hạn · voucher giới hạn suất      | Tải dồn vào một khóa; không cấp quá suất                  |
| Báo cáo                     | Doanh thu, số đơn theo ngày cho người bán và nền tảng | Truy vấn tổng hợp trên dữ liệu lớn                        |
| Hoàn trả và hoàn tiền       | Yêu cầu trả · duyệt · hoàn tiền                       | Luồng dài nhiều bên, bù trừ khi lỗi giữa chừng            |
| Số dư, chi trả, đối soát    | Giữ tiền đến khi giao xong · chi trả · đối soát       | Sổ cái, nhất quán tiền giữa các bên                       |
| Chat                        | Hội thoại người mua với người bán                     | Thời gian thực có lưu trữ, nhiều kết nối mở               |
| Theo dõi hành vi            | Ghi nhận xem, nhấp, tìm                               | Dòng sự kiện ghi liên tục, khối lượng lớn                 |
| Kho và vận hành giao hàng   | Kho nền tảng · đội giao, phân công · vị trí giao      | Ghi vị trí tần suất cao; phân công theo quy tắc           |

### 2.5 Không chọn

| Nghiệp vụ                                         | Lý do                                                          |
|---------------------------------------------------|----------------------------------------------------------------|
| Trả góp                                           | Thêm nhánh luồng, ít bài toán mới so với thanh toán trực tuyến |
| Đánh giá, điểm tích lũy                           | Độ rộng tính năng, ít bài toán kỹ thuật mới                    |
| Gợi ý, cá nhân hóa                                | Bài toán chính là học máy, ngoài trọng tâm của T1–T4           |
| Quảng cáo, livestream                             | Độ rộng tính năng; livestream là bài toán đa phương tiện riêng |
| Kiểm duyệt nội dung tự động, khiếu nại và phân xử | Đào sâu nghiệp vụ hơn mức cần                                  |
| Xuất hóa đơn, cấu hình nền tảng                   | Độ rộng tính năng                                              |

Khi dự án mở rộng, hai danh sách lõi và mở rộng có thể tăng thêm; nghiệp vụ ở bảng "không chọn" được xét lại nếu nó sinh ra
bài toán mới.

## 3. Kỹ năng thể hiện được qua các nghiệp vụ đã chọn

### 3.1 Kỹ thuật và mẫu thiết kế (T1)

| Kỹ thuật                      | Bài toán                                                                | Phát sinh ở                                       |
|-------------------------------|-------------------------------------------------------------------------|---------------------------------------------------|
| Saga, bù trừ                  | Đặt hàng qua giữ tồn, thanh toán, xác nhận; lỗi giữa chừng thì hoàn lại | Đơn hàng, Tồn kho, Thanh toán; Hoàn trả (mở rộng) |
| Transactional outbox          | Ghi dữ liệu và phát sự kiện không mất, không lệch                       | Mọi nghiệp vụ phát sự kiện                        |
| Consumer chịu nhận lặp        | Bus sự kiện giao lại một sự kiện nhiều lần                              | Mọi nghiệp vụ nhận sự kiện                        |
| CDC                           | Đồng bộ sang tìm kiếm, báo cáo mà không sửa nơi ghi                     | Tìm kiếm; Báo cáo (mở rộng)                       |
| Tách mô hình đọc (CQRS)       | Đọc nhiều hơn ghi hàng chục lần, hình dạng đọc khác ghi                 | Danh mục, Đơn hàng                                |
| Bộ đệm, chống dồn khi hết hạn | Trang sản phẩm nóng                                                     | Danh mục                                          |
| Máy tìm kiếm                  | Tìm văn bản, lọc nhiều thuộc tính                                       | Tìm kiếm                                          |
| Đẩy thời gian thực            | Trạng thái đơn, thông báo, tin nhắn đến không cần tải lại               | Thông báo; Chat (mở rộng)                         |
| Lập lịch và hàng đợi trễ      | Hủy đơn quá hạn thanh toán, nhả giữ chỗ                                 | Đơn hàng, Tồn kho                                 |
| Giới hạn tần suất             | Chống gửi dồn, bảo vệ đăng nhập                                         | Tài khoản; Khuyến mãi (mở rộng)                   |
| Nhận webhook an toàn          | Kết quả thanh toán bất đồng bộ, gửi lặp hoặc trễ                        | Thanh toán                                        |
| OAuth2, OIDC, BFF             | Nhiều ứng dụng dùng chung đăng nhập, token ngoài trình duyệt            | Tài khoản                                         |
| Sổ cái ghi kép                | Tiền đi qua nhiều bên phải khớp                                         | Số dư, chi trả (mở rộng)                          |
| Xử lý dòng sự kiện            | Gom và tổng hợp sự kiện ghi liên tục                                    | Theo dõi hành vi, Báo cáo (mở rộng)               |

### 3.2 Bài toán hệ thống lớn (T2)

| Bài toán                                                         | Chiều quy mô | Phát sinh ở                                       |
|------------------------------------------------------------------|--------------|---------------------------------------------------|
| Tranh chấp tồn kho: nhiều người mua một mặt hàng, không bán vượt | Đồng thời    | Tồn kho                                           |
| Thanh toán bên ngoài: kết quả trễ, lặp, mất phản hồi             | Đồng thời    | Thanh toán                                        |
| Gửi lặp: đặt đơn hai lần, sự kiện giao lại                       | Đồng thời    | Đơn hàng                                          |
| Nhất quán giữa các phần không có giao dịch chung                 | Đồng thời    | Đơn hàng, Tồn kho, Thanh toán                     |
| Flash sale: tải dồn vào một khóa trong vài giây                  | Đồng thời    | Khuyến mãi (mở rộng)                              |
| Voucher giới hạn suất                                            | Đồng thời    | Khuyến mãi (mở rộng)                              |
| Đặt ảo chiếm suất: đơn COD chốt tồn ngay, không qua thanh toán   | Đồng thời    | Thanh toán (COD); Khuyến mãi (mở rộng)            |
| Nhiều kết nối thời gian thực mở cùng lúc                         | Đồng thời    | Thông báo; Chat (mở rộng)                         |
| Tìm kiếm có lọc trên hàng triệu sản phẩm                         | Dữ liệu lớn  | Tìm kiếm                                          |
| Lịch sử đơn, phân trang sâu trên bảng lớn                        | Dữ liệu lớn  | Đơn hàng                                          |
| Báo cáo doanh thu trên hàng chục triệu đơn                       | Dữ liệu lớn  | Báo cáo (mở rộng)                                 |
| Dữ liệu tăng theo thời gian: phân vùng, lưu trữ cũ               | Dữ liệu lớn  | Đơn hàng; Theo dõi hành vi, vị trí giao (mở rộng) |

### 3.3 Thiết kế để mở rộng (T3)

Không lập danh sách riêng: T3 thể hiện qua cách chia ranh giới (file sau) và qua việc tách được một phần ra chạy riêng để đo,
ví dụ chỉ chạy Tồn kho và Khuyến mãi cho kịch bản flash sale.

### 3.4 Vấn đề frontend của hệ thống lớn (T4)

| Vấn đề                                              | Phát sinh ở                                                         |
|-----------------------------------------------------|---------------------------------------------------------------------|
| Dữ liệu đến bất đồng bộ                             | Đơn chờ thanh toán tự chuyển trạng thái khi bên thanh toán báo về   |
| Gửi trùng và thử lại an toàn                        | Bấm đặt hàng hai lần; mạng rớt giữa lúc thanh toán                  |
| Lỗi từng phần                                       | Trang sản phẩm vẫn hiện khi một phần phụ lỗi                        |
| Cập nhật thời gian thực                             | Thông báo, trạng thái đơn; tin nhắn (mở rộng)                       |
| Nhiều ứng dụng dùng chung nền                       | Cửa hàng, người bán, quản trị dùng chung đăng nhập, phiên, quyền    |
| Trạng thái nhiều nguồn                              | Giỏ vãng lai gộp khi đăng nhập                                      |
| Biểu mẫu lớn, lỗi máy chủ gắn vào ô                 | Hồ sơ người bán; đăng sản phẩm nhiều biến thể                       |
| Danh sách lớn: lọc, phân trang, trạng thái trên URL | Trang tìm kiếm, danh sách đơn của người bán                         |
| Hiệu năng tải trang                                 | Trang tìm kiếm và trang sản phẩm                                    |

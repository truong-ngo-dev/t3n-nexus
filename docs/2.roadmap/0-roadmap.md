# Roadmap

> Bản nháp, chờ duyệt.

## 1. File này để làm gì

File này xếp các nghiệp vụ đã chọn ở `1.requirement/2-business-analysis.md` thành từng chặng, và nói mỗi chặng chứng minh được
gì cho bốn tiêu chí T1 đến T4 của `1.requirement/1-project-overview.md`. Mức chi tiết: kết quả demo được của từng chặng. Việc cụ
thể, hạ tầng, công nghệ của một chặng viết ở `stage/<chặng>/stage.md` khi bắt đầu chặng đó, không viết trước ở đây.

## 2. Cách xếp

- **Mỗi chặng demo được.** Dừng sau chặng nào cũng có một sản phẩm chạy được, không có chặng chỉ làm nền cho chặng sau.
- **Chạy hết luồng mua hàng sớm, rồi mới đào sâu.** Sàn "sản phẩm chạy được từ đầu đến cuối" (overview mục 5) phải đạt trước
  khi đo tải.
- **Đo trên hệ thống đã chạy.** Chặng đo tải và dữ liệu lớn đứng sau các chặng dựng luồng, vì số đo chỉ có nghĩa trên luồng
  thật.
- **Chặng lớn thì tách khi bắt đầu.** Roadmap chỉ nêu chặng ở mức kết quả; lúc viết `stage.md` thấy quá lớn thì tách thành
  hai nửa (a, b), nửa đầu đi đường mỏng nhất.
- **Chỉ lên cloud ở cuối.** Làm một người, phần triển khai gom về cuối để không làm chậm phần lõi (`3-non-functional-requirement.md`
  mục 7).

Thứ tự có thể đổi khi làm; đổi thì sửa file này và ghi lý do ở mục 7.

## 3. Các chặng

| Chặng   | Tên                   | Demo được gì                                                                                                                     | Cần trước | Trạng thái |
|---------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------|-----------|------------|
| **C0**  | Nền                   | Đăng ký, đăng nhập, đăng xuất qua giao diện; một lỗi lần ra được từ log; có số đo trần của máy                                   |           | Xong       |
| **C1a** | Niêm yết              | Người bán nộp hồ sơ và được duyệt; người bán đăng sản phẩm có biến thể, giá, ảnh; hành động quản trị tra được                    | C0        | Đang làm   |
| **C1b** | Khám phá              | Khách tìm, lọc, xem chi tiết sản phẩm ở Cửa hàng; quản trị khóa tài khoản, chặn sản phẩm; người dùng xem lịch sử đăng nhập       | C1a       |            |
| **C2**  | Đặt hàng COD          | Bỏ giỏ (cả khi chưa đăng nhập), đặt COD, người bán xác nhận, giao giả lập tới đã giao hoặc thất bại, hủy và quá hạn trả đúng tồn | C1b       |            |
| **C3**  | Thanh toán trực tuyến | Trả tiền qua môi trường thử nghiệm của bên thanh toán; không trả trong hạn thì đơn tự hủy; hủy thì hoàn tiền                     | C2        |            |
| **C4**  | Thời gian thực        | Thông báo và trạng thái đơn tự cập nhật ở cả ba ứng dụng, không cần tải lại                                                      | C2        |            |
| **C5**  | Flash sale và tải cao | Một đợt flash sale có suất và giới hạn mỗi người chạy dưới tải; không bán vượt, không quá suất; có số đo                         | C2, C3    |            |
| **C6**  | Dữ liệu lớn           | Tìm kiếm trên vài triệu sản phẩm, lịch sử đơn trang sâu, bảng lớn theo thời gian; có số đo                                       | C1b, C2   |            |
| **C7**  | Lên cloud             | Phần lõi chạy trên cloud để demo rồi tắt                                                                                         | C6        |            |

C1b đi trước C2 vì phải có sản phẩm tìm được thì mới đặt được. C2 làm COD trước thanh toán trực tuyến: cả hai đi trên cùng luồng
"giữ đơn trước, thanh toán sau" (`2-business-analysis.md` mục 2.2), COD không cần bên ngoài nên dựng luồng nhanh hơn, và C3 chỉ
thêm nhánh trả tiền vào luồng đã chạy.

## 4. Mỗi chặng chứng minh được gì

Cột ghi kỹ thuật (T1), bài toán (T2, số mục ở `3-non-functional-requirement.md`) và vấn đề frontend (T4, số mục ở
`6-ui-analysis.md`). T3 không theo chặng: nó thể hiện qua ranh giới BC giữ được khi thêm chặng, và qua lát cắt chạy riêng ở C5.

| Chặng   | T1: kỹ thuật                                             | T2: bài toán                                                                                                    | T4: vấn đề frontend                                                                      |
|---------|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| **C0**  | OAuth2, OIDC, BFF; giới hạn tần suất ở đăng nhập         | Đo trần máy (mục 4.1)                                                                                           | Nhiều ứng dụng dùng chung nền (5.5)                                                      |
| **C1a** | Transactional outbox; consumer chịu nhận lặp             |                                                                                                                 | Biểu mẫu lớn, lỗi máy chủ gắn vào ô (5.7)                                                |
| **C1b** | Máy tìm kiếm; CDC; tách mô hình đọc; bộ đệm              | Chưa đo lớn; dựng đường cho 3.8                                                                                 | Danh sách lớn, trạng thái trên URL (5.8); lỗi từng phần (5.3); hiệu năng tải trang (5.9) |
| **C2**  | Saga, bù trừ; lập lịch và hàng đợi trễ                   | Tranh chấp tồn kho, đúng trước (3.1); nhất quán giữa các phần (3.6); đặt ảo chiếm suất ở COD (3.2)              | Gửi trùng và thử lại an toàn (5.2); trạng thái nhiều nguồn (5.6)                         |
| **C3**  | Nhận webhook an toàn                                     | Thanh toán bên ngoài và gửi lặp (3.5)                                                                           | Dữ liệu đến bất đồng bộ (5.1)                                                            |
| **C4**  | Đẩy thời gian thực                                       | Nhiều kết nối thời gian thực (3.7)                                                                              | Cập nhật thời gian thực (5.4)                                                            |
| **C5**  | Giới hạn tần suất dưới tải; bộ đệm chống dồn khi hết hạn | **Đo:** mặt hàng nóng (3.1), suất theo người (3.2), đọc dồn (3.3), làn sóng đăng nhập (3.4); lát cắt flash sale | Flash sale: đếm ngược theo giờ máy chủ, hết suất và quá tải (5.10)                       |
| **C6**  |                                                          | **Đo:** tìm kiếm (3.8), lịch sử đơn (3.9), dữ liệu tăng theo thời gian (3.11)                                   |                                                                                          |
| **C7**  |                                                          | Ghi lại khác biệt giữa số đo cục bộ và trên cloud                                                               |                                                                                          |

C5 kéo `Promotion` (nghiệp vụ mở rộng) vào trước điểm dừng, vì flash sale là chỗ chứng minh chính của chiều đồng thời ở T2.

## 5. Điểm dừng

**Sau C6** thì đủ điều kiện dừng của overview mục 2 cho phần đã chọn:

| Tiêu chí | Đạt ở đâu                                                                                             |
|----------|-------------------------------------------------------------------------------------------------------|
| T1       | Các kỹ thuật phát sinh ở nghiệp vụ lõi và ở `Promotion` có bài toán và bằng chứng (C0 đến C5)         |
| T2       | Điểm đo chiều đồng thời ở C5, điểm đo chiều dữ liệu lớn ở C6                                          |
| T3       | Lát cắt flash sale chạy riêng ở C5; các quyết định ranh giới giải thích được                          |
| T4       | Luồng mua hàng chính chạy qua giao diện từ C2; chín vấn đề frontend của file 2 mục 3.4 có ở C0 đến C4 |

Kỹ thuật chỉ phát sinh ở nghiệp vụ mở rộng (sổ cái ghi kép, xử lý dòng sự kiện) tính cho T1 khi làm tới nghiệp vụ đó, sau điểm
dừng. C7 cũng nằm sau điểm dừng: có thì tốt, không có thì bằng chứng vẫn đủ.

## 6. Sau điểm dừng

Các nghiệp vụ mở rộng còn lại, xếp theo bài toán mới chúng thêm vào:

| Nghiệp vụ                 | BC                 | Thêm bài toán gì                                                             | Cần trước |
|---------------------------|--------------------|------------------------------------------------------------------------------|-----------|
| Báo cáo                   | `Reporting`        | Báo cáo trên dữ liệu lớn (3.10); xử lý dòng sự kiện; bảng lớn trên giao diện | C6        |
| Hoàn trả và hoàn tiền     | `Returns`          | Luồng dài nhiều bên, bù trừ khi lỗi giữa chừng                               | C3        |
| Số dư, chi trả, đối soát  | `Ledger`           | Sổ cái ghi kép; tiền giữa các bên phải khớp                                  | C3        |
| Chat                      | `Chat`             | Thời gian thực có lưu trữ                                                    | C4        |
| Theo dõi hành vi          | `ActivityTracking` | Dòng sự kiện ghi liên tục, khối lượng lớn                                    | C6        |
| Kho và vận hành giao hàng | `Fulfillment`      | Vị trí tần suất cao (3.12); ứng dụng Người giao khi mạng yếu                 | C4        |

Thứ tự trong bảng chưa chốt; chọn khi tới, theo bài toán muốn học tiếp.

## 7. Quyết định đã chốt

| Câu hỏi                                     | Quyết định                                      | Lý do                                                  |
|---------------------------------------------|-------------------------------------------------|--------------------------------------------------------|
| Giữ mã chặng đã làm hay đánh số lại hết     | Giữ C0, C1a; chặng sau đánh số mới              | Thư mục `stage/` và tài liệu đã làm dùng các mã này    |
| COD hay thanh toán trực tuyến trước         | COD trước (C2), trực tuyến sau (C3)             | Cùng một luồng; COD dựng luồng không cần bên ngoài     |
| Flash sale trước hay sau điểm dừng          | Trước (C5)                                      | Là bằng chứng chính cho chiều đồng thời của T2         |
| Dữ liệu tạo ở chặng trước có phải giữ không | Không, cho tới khi phần đó làm xong ở mức chính | Dữ liệu thử; di trú dữ liệu tốn công mà không dạy thêm |

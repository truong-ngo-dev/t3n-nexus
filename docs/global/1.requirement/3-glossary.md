# Thuật ngữ

Bảng này cố định nghĩa của các thuật ngữ dùng từ context map (`4-context-map.md`) trở đi. Hai file đứng trước
(`1-project-overview.md`, `2-analysis-principles.md`) không dựa vào bảng này.

## 1. Quy ước viết

1. **Tên vai, tên bên tham gia và tên BC viết bằng tiếng Việt:** Người dùng, Quản trị, Nhân viên kho (vai); Người mua,
   Người bán, Shipper (bên tham gia); Tìm kiếm, Tồn kho… Không viết Buyer, Seller, Admin, Search.
2. **Giữ nguyên các từ mượn dev đã quen** (event, voucher, flash sale, COD, shipper, mobile, SKU, P99). Viết đúng một cách
   trong mọi file; nghĩa trong dự án ghi ở các bảng dưới.
3. **Tên BC viết hoa chữ đầu** khi chỉ BC (Giao nhận, Sổ cái), để phân biệt với danh từ thường ("giao nhận" là việc).

## 2. Phương pháp phân tích

| Thuật ngữ | Nghĩa trong dự án |
|---|---|
| BC (bounded context) | Một phần của hệ thống có thuật ngữ, ràng buộc và lý do thay đổi riêng. Chi tiết ở `2-analysis-principles.md` mục P2 |
| Năng lực | Một việc nghiệp vụ trọn vẹn trong một BC; đơn vị để xếp trạng thái |
| Phần đóng, khép kín | Các việc phụ một năng lực cần để chạy trọn (bên xử lý, thông báo kết quả, chỗ xem lịch sử) |
| Trong, Trong (tối giản) | Làm. "Tối giản" nghĩa là phần đóng có mặt nhưng đơn giản; không có nghĩa làm ẩu về kỹ thuật |
| Cắt tạm | Đã phân tích, hoãn làm, có điểm nối để quay lại |
| Ngoài | Không làm, có lý do và điều kiện xem xét lại |
| Điểm nối | Chỗ (event hay giao diện) để thêm phần cắt tạm sau này mà không đổi các BC khác |
| BC chủ | BC giữ lệnh khởi tạo và trạng thái chính của một năng lực xuyên nhiều BC |
| Điểm sâu | Chỗ đầu tư kỹ thuật vượt mức bình thường |
| Đòi hỏi, chủ động chọn | Hai gốc của điểm sâu: cách đơn giản sụp ở quy mô nào đó, hoặc người làm chọn để học (kèm lý do) |
| Đồng thời cao, dữ liệu lớn | Hai chiều quy mô của điểm sâu |
| Ràng buộc (INV) | Điều luôn phải đúng với dữ liệu của một BC (ví dụ tồn khả dụng không âm) |
| Đường không suôn sẻ | Nhánh khi việc thất bại, quá hạn, bị từ chối hoặc lặp |
| Mức tên, mức mịn | Mức tên: chỉ tên nghiệp vụ, trạng thái, lý do. Mức mịn: đủ để ra feature |

## 3. Nghiệp vụ

| Thuật ngữ | Nghĩa trong dự án |
|---|---|
| Khách vãng lai | Người chưa đăng nhập |
| Tài khoản | Đại diện cho một người trong hệ thống, do Xác thực giữ; có đúng một vai |
| Phiên | Khoảng thời gian một tài khoản được coi là đã xác thực, do Xác thực giữ; có phiên ở máy chủ xác thực và phiên của từng portal |
| Thiết bị | Tín hiệu nhận ra cùng một trình duyệt hoặc cài đặt ứng dụng của một tài khoản; do Định danh giữ để hiển thị |
| Lịch sử đăng nhập | Ghi lại các lần đăng nhập, đăng xuất và thất bại của một tài khoản, do Định danh giữ |
| Vai | Nhóm hệ thống mà tài khoản thuộc về, do Xác thực giữ: Người dùng (tài khoản tự đăng ký) hoặc vai nội bộ (Quản trị, Nhân viên kho...). Quyết định vào portal nào và thao tác thô; viết hoa "Người dùng" là chỉ vai này |
| Bên tham gia | Người đóng một phần việc trong giao dịch: Người mua, Người bán, Shipper, Quản trị, Nhân viên kho. Người mua, Người bán, Shipper không phải vai: mọi tài khoản Người dùng đều mua được, bán và giao hàng là hồ sơ năng lực |
| Hồ sơ năng lực | Hồ sơ có vòng đời và bước duyệt riêng, giữ ở BC sở hữu (Người bán, Shipper) và trỏ về tài khoản. Tài khoản chờ duyệt vẫn đăng nhập và mua bình thường |
| COD | Thanh toán tiền mặt khi nhận hàng; tiền đi qua shipper và kho, không qua bên thanh toán |
| Giữ chỗ | Trừ tạm số lượng khả dụng cho một đơn đang đặt; chốt khi đơn thành công, trả khi hủy hoặc quá hạn |
| Hạn xác nhận | Thời gian người bán phải xác nhận hoặc từ chối đơn |
| Hạn bàn giao | Thời gian người bán phải đưa hàng tới kho |
| Voucher | Mã ưu đãi, có giới hạn tổng và giới hạn mỗi người |
| Flash sale | Đợt bán ưu đãi giới hạn suất trong thời gian ngắn; ở dự án chỉ cho hàng đối tác |
| Người bán đối tác | Loại người bán gửi hàng vào kho nền tảng để kho giữ tồn theo SKU (đang cắt tạm) |
| SKU, đơn vị bán được | Một biến thể cụ thể của sản phẩm mà người mua đặt được |
| Ký quỹ | Giữ tiền trả trước đến khi giao thành công |
| Sổ cái, bút toán | Sổ ghi mọi biến động tiền; mỗi bút toán có đối ứng và chỉ bù, không sửa |
| Đối soát | So khớp số tiền hai bên (thu và nộp, thu và chi trả) |
| Portal | Một ứng dụng giao diện cho một nhóm bên tham gia (Cửa hàng, Người bán, Vận hành, Shipper) |
| Vận hành | Portal của Quản trị và Nhân viên kho: nghiệp vụ vận hành sàn (duyệt, phân xử, điều phối shipper, nhận hàng ở kho). Không chứa nghiệp vụ nhân sự hay kế toán |
| Người chạy thử | Chính người làm dự án khi dùng công cụ Giả lập; không phải bên tham gia của nền tảng |

## 4. Kỹ thuật

| Thuật ngữ | Nghĩa trong dự án |
|---|---|
| Event | Thông báo một việc đã xảy ra, BC này phát và BC khác nhận |
| Giao diện (giữa BC) | Lời gọi trực tiếp có hướng giữa hai BC, dạng còn lại của quan hệ giữa BC |
| Gọi ngược | Bên ngoài báo kết quả về cho hệ thống sau (ví dụ bên thanh toán) |
| Xử lý lặp (idempotent) | Gửi hoặc nhận cùng một yêu cầu nhiều lần chỉ ra một kết quả |
| Bù trừ | Hoàn lại các bước đã làm khi một bước giữa chừng thất bại |
| Điều phối nhiều bước | Một quy trình chia nhiều bước qua nhiều BC, có bù trừ khi lỗi |
| Quy trình chạy dài | Quy trình tạm dừng chờ người hoặc thời gian rồi tiếp tục mà không mất trạng thái |
| Nhất quán cuối cùng | Dữ liệu ở BC đọc chậm hơn nguồn một lúc, rồi khớp |
| Read model | Bản dữ liệu dựng riêng để đọc nhanh, tách khỏi nguồn ghi |
| Bất biến (dữ liệu) | Chỉ ghi thêm, không sửa |
| Bất biến (kiểm tra tải) | Điều phải đúng sau mỗi lần chạy tải |
| Mặt hàng nóng | Mặt hàng nhận phần lớn yêu cầu khi tải lệch (Zipf) |
| Thể hiện | Một bản chạy của một dịch vụ |
| Bộ đệm | Nơi giữ tạm dữ liệu để đọc nhanh |
| Mã tương quan | Mã đi cùng một yêu cầu qua log và event để lần ra dấu vết |
| Tiêm lỗi | Cố ý làm một thành phần lỗi hoặc chậm để kiểm tra cách hệ thống chịu |
| Bộ đo nền | Bộ đo trần của máy bằng điểm cuối rỗng, ghi một dòng và băm một mật khẩu, trước khi đo ứng dụng |
| P50, P95, P99 | Độ trễ mà 50%, 95%, 99% yêu cầu nhanh hơn |
| Nhật ký kiểm toán | Ghi chỉ thêm các hành động quản trị và hành động nhạy cảm |
| Giả lập | BC tạo tải, tiêm lỗi, sinh dữ liệu và mô phỏng shipper |

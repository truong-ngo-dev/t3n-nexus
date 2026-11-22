# Khung ứng dụng Quản trị: yêu cầu

## 1. Mục tiêu

Quản trị mở `ops.localhost`, đăng nhập bằng tài khoản `ADMIN`, thấy một bố cục cố định có danh sách các khu việc, và chuyển giữa
các khu mà không phải đăng nhập lại. Người không phải quản trị vào nhầm thì được nói rõ là không có quyền, không thấy gì của quản
trị.

## 2. Ai dùng

Chỉ vai `ADMIN`. Tài khoản `USER` (người mua, người bán) có thể đăng nhập được ở `ops.localhost`, vì cổng xác thực dùng chung và
`ops-web-bff` chưa chặn vai, nên giao diện phải tự xử lý trường hợp này.

## 3. Luồng

**3.1 Mở ứng dụng.** Giao diện kiểm phiên trước khi hiện bất cứ trang nào:

| Kết quả kiểm phiên         | Người dùng thấy                                                                          |
|----------------------------|------------------------------------------------------------------------------------------|
| Đang kiểm                  | Vòng xoay "Đang kiểm tra tài khoản…", chưa có menu                                       |
| Chưa đăng nhập             | Trang đăng nhập có một nút "Đăng nhập"                                                   |
| `ADMIN`                    | Bố cục đầy đủ, mở trang mặc định là hàng chờ duyệt người bán                             |

Vai khác `ADMIN` và lỗi khi kiểm phiên không thuộc luồng này, xem mục 3.7.

**3.2 Đăng nhập.** Bấm "Đăng nhập" thì đi sang trang đăng nhập chung của hệ thống, xong quay về trang mặc định.

**3.3 Mở thẳng một đường dẫn.** Dán đường dẫn một hồ sơ, tải lại trang, bấm quay lại: đều ra đúng trang đó sau khi kiểm phiên.
Chưa đăng nhập thì đăng nhập xong không bắt buộc quay đúng trang đã dán (làm được thì tốt, không phải điều kiện xong).

**3.4 Hết phiên giữa chừng.** Bất kỳ lời gọi nào nhận 401 thì đi đăng nhập lại. Biểu mẫu đang nhập dở có thể mất; chấp nhận được
vì quản trị chỉ có biểu mẫu ngắn.

**3.5 Đăng xuất.** Bấm "Đăng xuất" ở thanh trên. Bấm đúp chỉ gửi một lần. Đăng xuất thất bại thì báo và vẫn ở lại, không giả vờ
là đã đăng xuất.

**3.6 Đường dẫn không có.** Hiện trang "Không tìm thấy trang" có liên kết về trang mặc định, vẫn trong bố cục.

**3.7 Trường hợp lỗi.**

| Tình huống                                             | Người dùng thấy                                                                        |
|--------------------------------------------------------|----------------------------------------------------------------------------------------|
| Đăng nhập bằng vai khác `ADMIN`                        | Trang "Tài khoản này không có quyền quản trị" kèm nút đăng xuất; không có menu (Q1)    |
| Không kiểm được phiên (5xx, mất mạng, thân lạ)         | "Không tải được thông tin tài khoản" kèm nút Thử lại; **không** đẩy đi đăng nhập (Q2)  |

- **Vì sao chặn vai khác `ADMIN` ở giao diện.** Họ vào được chỉ vì cổng xác thực dùng chung và `ops-web-bff` chưa chặn vai.
- **Vì sao lỗi kiểm phiên không coi là chưa đăng nhập.** Đẩy một quản trị đang có phiên đi đăng nhập lại chỉ vì mạng chập chờn
  là sai, và có thể thành vòng lặp.

## 4. Quy tắc

- **Q1.** Vai khác `ADMIN` không thấy menu và không có trang nghiệp vụ nào gọi API. Service phía sau cũng từ chối, nhưng giao
  diện không dựa vào đó để chặn.
- **Q2.** Lỗi kiểm phiên không bao giờ bị hiểu thành "chưa đăng nhập".
- **Q3.** Mọi trang có đủ ba trạng thái: đang tải, rỗng (nếu là danh sách), lỗi. Không có màn hình trắng.
- **Q4.** Hai loại lỗi nói khác nhau:
  - **lỗi do người dùng** (nhập sai, trạng thái đã đổi): nói họ cần làm gì;
  - **lỗi hệ thống** (5xx, mất mạng, hết giờ): nói là lỗi hệ thống, cho thử lại, hiện mã truy vết nếu máy chủ trả về.
- **Q5.** Không hiện chữ thông báo lỗi của máy chủ; câu hiện ra do giao diện đặt theo mã lỗi.

## 5. Yêu cầu phi chức năng

- **Thiết bị:** ưu tiên máy tính (từ 1280px). Màn hẹp dưới khoảng 600px thì không vỡ bố cục: menu thu thành nút mở.
- **Tải trang:** mỗi khu việc tải mã riêng khi mở lần đầu. Gói ban đầu dưới ngưỡng cảnh báo 500 kB như hai ứng dụng kia.
- **Tiếng Việt**, giờ hiển thị theo giờ Việt Nam, dạng `dd/MM/yyyy HH:mm`.

## 6. Không làm

| Dòng                                                         | Lý do                                                                                                  |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Chặn tải mã ứng dụng với người không phải quản trị (6-ui 3.2)| Cần chặn ở cổng trước khi trả tệp tĩnh; C1a chỉ có một vai nội bộ, mã không chứa bí mật                |
| Ẩn hay hiện từng nút theo nhiều vai nội bộ                   | Chỉ có `ADMIN`; làm khi có nhiều vai nội bộ                                                            |
| Giữ dữ liệu biểu mẫu khi hết phiên                           | Biểu mẫu của quản trị ngắn                                                                             |
| Trang đăng ký tài khoản                                      | Tài khoản quản trị tạo bằng dữ liệu khởi tạo                                                           |

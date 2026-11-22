# Duyệt người bán: yêu cầu

## 1. Mục tiêu

Quản trị xử lý hồ sơ người bán theo hàng chờ:
1. Mở hàng chờ, thấy hồ sơ nộp lâu nhất ở trên cùng.
2. Mở một hồ sơ, đọc đủ thông tin pháp lý.
3. Duyệt, hoặc từ chối kèm lý do. Người bán sẽ đọc lý do này để sửa.

Quyết định phải đúng với bản hồ sơ quản trị đã đọc: người bán sửa hồ sơ trong lúc quản trị đang đọc thì quyết định đó không được
áp dụng.

BC: `Seller`. Backend đã có (feature 04 của C1a); hợp đồng ở `service/seller-service/api.yaml`, các đường `/api/admin/seller-profiles`.

## 2. Luồng

**2.1 Xem hàng chờ.**
- Bảng các hồ sơ đang chờ duyệt, nộp lâu nhất ở trên. Mỗi dòng có tên gian hàng, loại hình người bán, thời điểm nộp và đã chờ
  bao lâu.
- Mỗi trang 20 dòng, có lật trang. Trang đang xem nằm trên đường dẫn: tải lại hay bấm quay lại vẫn ở đúng trang đó.
- Hàng chờ rỗng thì nói "Không có hồ sơ nào đang chờ duyệt".
- Bấm một dòng thì mở chi tiết hồ sơ đó.

**2.2 Xem chi tiết.**
- Trang chi tiết có đủ thông tin:
  - **gian hàng:** tên, mô tả, người liên hệ, số điện thoại, địa chỉ liên hệ;
  - **pháp lý:** loại hình, tên pháp lý, người đại diện, số căn cước, mã số thuế, địa chỉ kinh doanh;
  - **thời điểm nộp**.
- Nhãn của tên pháp lý đổi theo loại hình:
  - cá nhân: "Họ tên";
  - hộ kinh doanh: "Tên chủ hộ";
  - doanh nghiệp: "Tên doanh nghiệp".
- Hồ sơ đã có quyết định vẫn xem được (mở từ nhật ký hay từ đường dẫn cũ). Khi đó trang có thêm:
  - trạng thái;
  - thời điểm quyết định;
  - lý do từ chối, nếu bị từ chối.

  Trang không có nút quyết định.

**2.3 Duyệt.**
1. Bấm "Duyệt" thì hộp xác nhận hỏi lại, có nêu tên gian hàng.
2. Xác nhận thì hồ sơ chuyển "Đã duyệt".
3. Quản trị quay về hàng chờ, thấy thông báo "Đã duyệt gian hàng X". Hồ sơ đó không còn trong hàng chờ.

**2.4 Từ chối.**
1. Bấm "Từ chối" thì một hộp hiện ô nhập lý do.
2. Lý do bắt buộc, tối đa 500 ký tự; khoảng trắng đầu và cuối không tính.
3. Gửi thì hồ sơ chuyển "Bị từ chối". Quản trị quay về hàng chờ với thông báo như khi duyệt.

**2.5 Hồ sơ đã đổi trong lúc đang xem.** Hai trường hợp, người dùng phải hiểu được khác nhau:

| Chuyện đã xảy ra                                                       | Quản trị thấy                                                                                                  |
|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Người bán sửa hồ sơ sau khi quản trị mở trang chi tiết                  | "Người bán vừa sửa hồ sơ sau khi bạn mở. Đã tải bản mới, hãy xem lại trước khi quyết định." Trang hiện bản mới; các thay đổi không được đánh dấu riêng |
| Hồ sơ đã có quyết định (quản trị khác xử lý, hoặc chính mình ở tab khác) | "Hồ sơ này đã được xử lý." Trang hiện trạng thái hiện tại, không còn nút quyết định                            |

Cả hai trường hợp đều **không** tự gửi lại quyết định. Mục đích của kiểm phiên bản là bắt quản trị đọc lại, nên tự gửi lại
với phiên bản mới là phá chính mục đích đó.

**2.6 Mạng rớt khi đang gửi quyết định.** Quản trị không biết quyết định đã tới máy chủ chưa. Giao diện tải lại hồ sơ để biết:

- **Hồ sơ đã chuyển đúng trạng thái mình chọn, do chính mình quyết:** nói "Thao tác đã được ghi nhận" rồi quay về hàng chờ như
  khi thành công. Không báo lỗi, vì thực tế đã thành công.
- **Hồ sơ đã chuyển nhưng do người khác, hoặc khác quyết định mình chọn:** như mục 2.5, dòng 2.
- **Hồ sơ vẫn chờ duyệt:** nói chưa thực hiện được, để quản trị bấm lại.
- **Tải lại cũng không được:** nói chưa rõ kết quả và cho tải lại trang.

## 3. Quy tắc

- **Q1.** Chỉ hồ sơ đang chờ duyệt mới có nút Duyệt và Từ chối.
- **Q2.** Quyết định luôn gửi kèm phiên bản của bản quản trị đang xem. Không bao giờ gửi phiên bản lấy từ một lần tải khác mà
  quản trị chưa thấy trên màn hình.
- **Q3.** Lý do từ chối không trống sau khi cắt khoảng trắng, tối đa 500 ký tự. Giao diện kiểm trước khi gửi; máy chủ vẫn kiểm lại.
- **Q4.** Bấm đúp, hay bấm lúc đang gửi, chỉ tạo một lời gọi.
- **Q5.** Số căn cước, mã số thuế, địa chỉ kinh doanh chỉ hiện ở trang chi tiết:
  - không có trong hàng chờ (máy chủ cũng không trả);
  - không có trong đường dẫn, không ghi ra console, không có trong thông báo.

## 4. Lỗi người dùng phải hiểu được

| Tình huống                                     | Mã máy chủ                    | Người dùng thấy                                                                 |
|------------------------------------------------|-------------------------------|---------------------------------------------------------------------------------|
| Người bán sửa hồ sơ sau khi quản trị mở        | 409 `STALE_VERSION`           | Như mục 2.5, dòng 1                                                             |
| Hồ sơ đã có quyết định                         | 409 `INVALID_STATE`           | Như mục 2.5, dòng 2                                                             |
| Lý do trống (giao diện để lọt)                 | 400 `REJECTION_REASON_REQUIRED` | Lỗi ngay dưới ô lý do                                                         |
| Đường dẫn trỏ tới hồ sơ không có hoặc mã sai dạng | 404 `SELLER_PROFILE_NOT_FOUND`, 400 | "Không tìm thấy hồ sơ" kèm liên kết về hàng chờ                         |
| Lỗi hệ thống khi đọc                           | 5xx, mạng                     | Câu lỗi hệ thống, mã truy vết, nút Thử lại                                      |
| Lỗi hệ thống khi gửi quyết định                | 5xx, mạng, hết giờ            | Như mục 2.6; hộp lý do giữ nguyên chữ đã nhập                                   |

## 5. Yêu cầu phi chức năng

- Hàng chờ ở C1a nhỏ (vài chục hồ sơ), nên lật trang theo số trang như máy chủ đang làm là đủ. Lật trang theo con trỏ để dành
  cho danh sách thay đổi liên tục (6-ui mục 5.8).
- Ở số trang lớn hơn tổng số trang (do hồ sơ vừa được xử lý hết, hoặc gõ tay đường dẫn) thì về trang cuối có dữ liệu, không
  hiện trang rỗng giả.

## 6. Không làm

| Dòng                                                      | Lý do                                                                          |
|-----------------------------------------------------------|--------------------------------------------------------------------------------|
| Lọc, tìm trong hàng chờ                                   | Máy chủ chưa có; hàng chờ C1a nhỏ                                              |
| Đánh dấu phần người bán vừa sửa                           | Máy chủ không trả bản cũ để so                                                 |
| Hàng chờ tự cập nhật khi có hồ sơ mới                     | Thời gian thực ở C4                                                            |
| Hiện tên quản trị đã quyết định                           | Máy chủ chỉ trả mã tài khoản; chưa có dịch vụ tra tên                          |
| Giữ hồ sơ cho một quản trị (khóa "đang xem")              | Kiểm phiên bản đã chặn quyết định sai; hai quản trị cùng mở chỉ tốn công đọc   |

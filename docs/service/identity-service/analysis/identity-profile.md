# Hồ sơ định danh

> Phạm vi: mô hình và quy tắc nghiệp vụ của hồ sơ định danh (tên hiển thị và bản sao hiển thị của tài khoản). Cách lưu và kỹ thuật ở `service.md`, `data.md`.
> Phần dùng chung của Định danh ở `0.overview.md`; thiết bị ở `device.md`; lịch sử đăng nhập ở `login-history.md`. Mã BC: `IDN`.

## 1. Định nghĩa

**Hồ sơ định danh** là tên hiển thị và dữ liệu để nhận ra một tài khoản trên giao diện. Dùng chung mã với tài khoản ở Xác thực. Mỗi tài khoản có tối đa một hồ sơ (INV-IDN-05).

## 2. Cấu trúc và tạo hồ sơ

Mỗi tài khoản có một hồ sơ, tạo khi nhận event "tài khoản đã đăng ký". Tên hiển thị mặc định lấy từ phần trước `@` của
email, người dùng sửa được. Tài khoản nội bộ được khởi tạo kèm tên. Tài khoản đã có từ trước khi Định danh bắt đầu chạy
được dựng bù hồ sơ, hoặc hồ sơ được tạo lần đầu khi cần (Q2).

Vai, email và trạng thái trong hồ sơ là **bản sao chỉ để hiển thị**, cập nhật theo event của Xác thực. Bản sao có thể chậm
hơn nguồn nên không dùng làm căn cứ quyết định.

## 3. Năng lực

Mỗi năng lực trả lời sáu câu của P3: (1) ai khởi tạo, (2) ai xử lý, (3) ai được báo, (4) đường không suôn sẻ, (5) ai xem lại lịch sử, (6) dữ liệu chảy sang đâu.

### CAP-IDN-01 Tạo hồ sơ định danh
1. Event "tài khoản đã đăng ký". 2. Định danh tạo hồ sơ với tên mặc định. 3. Không ai. 4. Event giao lại: không tạo hai hồ sơ
(INV-IDN-05); tài khoản có từ trước: dựng bù (Q2). 5. Không có. 6. Không chảy đi.

### CAP-IDN-02 Sửa tên hiển thị
1. Người dùng. 2. Định danh cập nhật. 3. Không ai. 4. Tên rỗng hoặc quá dài: từ chối. 5. Không có. 6. Hồ sơ chảy tới nơi hiển
thị tên (giao diện, danh sách quản trị).

### CAP-IDN-06 Quản trị xem người dùng
1. Quản trị. 2. Định danh liệt kê hồ sơ kèm bản sao vai, trạng thái. 3. Không ai. 4. Bản sao chậm hơn nguồn: giao diện ghi rõ là
tham khảo; khóa và mở thực hiện ở Xác thực. 5. Hành động khóa ghi ở Nhật ký kiểm toán. 6. Không chảy đi.

## 4. Bất biến (INV)

| ID | Bất biến | Lý do |
|---|---|---|
| INV-IDN-05 | Mỗi tài khoản có tối đa một hồ sơ định danh | Event giao lại không nhân bản hồ sơ |
| INV-IDN-06 | Bản sao hiển thị không được dùng để quyết định quyền | Bản sao có thể chậm hơn nguồn |

## 5. Event

Hồ sơ định danh nhận từ Xác thực: tài khoản đã đăng ký (tạo hồ sơ), khóa và mở tài khoản (cập nhật bản sao trạng thái). Không phát event.

## 6. Câu hỏi cần trả lời

| # | Câu hỏi | Vì sao đáng hỏi | Đề xuất |
|---|---|---|---|
| Q1 | Tên hiển thị có bắt buộc khi đăng ký không | Tài khoản nội bộ không có hồ sơ Khách hàng nhưng vẫn cần nhãn hiển thị | Không bắt buộc; mặc định từ email, sửa sau |
| Q2 | Tài khoản có từ trước khi Định danh chạy: dựng bù một lần, hay tạo hồ sơ khi cần lần đầu | Ảnh hưởng thời điểm danh sách quản trị có đủ người | Dựng bù một lần khi bắt đầu |

## 7. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Ảnh đại diện | **Cắt tạm** | Khi có Quản lý tệp dùng được cho hồ sơ | Nhận ra người dùng trên giao diện | Phụ thuộc BC khác | Trường tham chiếu tệp trong hồ sơ |

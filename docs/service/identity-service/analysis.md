# Phân tích nghiệp vụ: Định danh

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-10-08` · **Mã BC:** `IDN` (tiền tố cho ID)
>
> Tài liệu giữ nghiệp vụ **kèm lý do**, phản ánh trạng thái hiện tại, không ghi lịch sử thay đổi. BC này bắt đầu ở chặng
> C1b; chặng C0 chưa có Định danh. Chỗ cần chốt nằm ở mục 8.
>
> **Nguồn:** `4-context-map.md` B28, `5-ui-analysis.md` §5, `7-roadmap.md` C1b · **Liên quan:** [Xác thực](../oauth2-service/analysis.md)

## 1. Nhiệm vụ và ranh giới

Định danh giữ phần **để xem** của một tài khoản: hồ sơ định danh (tên hiển thị), thiết bị đã dùng, lịch sử đăng nhập và
danh sách phiên đang hoạt động. Người dùng dùng nó để nhận ra dấu hiệu bất thường trên tài khoản của mình; Quản trị dùng
nó để xem người dùng khi cần khóa hoặc mở tài khoản.

Nguyên tắc cắt với Xác thực: **cái gì quyết định cho đăng nhập thì ở Xác thực; cái gì chỉ để xem thì ở Định danh.** Vì thế
Định danh không bao giờ quyết định một ai đó có đăng nhập được hay không, và không BC nào dựa vào dữ liệu của Định danh
để cho phép hay từ chối một thao tác.

Định danh **không** làm:

| Việc | Thuộc | Lý do |
|---|---|---|
| Email, mật khẩu, vai, trạng thái khóa (bản gốc), phiên, token | Xác thực | Quyết định đăng nhập |
| Thu hồi phiên, bỏ tin cậy thiết bị (thực thi) | Xác thực | Thực thi là việc của nơi giữ phiên; Định danh chỉ gửi lệnh |
| Hồ sơ giao hàng, sổ địa chỉ, số điện thoại người nhận | Khách hàng (C2a) | Đổi theo lý do giao hàng |
| Hồ sơ bán hàng, hồ sơ shipper | Người bán, Shipper | Có vòng đời và bước duyệt riêng |
| Ghi hành động quản trị | Nhật ký kiểm toán | Bất biến, giữ lâu, khác lịch sử đăng nhập |

## 2. Ngôn ngữ chung

| Thuật ngữ | Định nghĩa | Nghĩa ở BC khác |
|---|---|---|
| Hồ sơ định danh | Tên hiển thị và dữ liệu để nhận ra một tài khoản trên giao diện. Dùng chung mã với tài khoản ở Xác thực | — |
| Thiết bị | Tín hiệu nhận ra cùng một trình duyệt hoặc cài đặt ứng dụng của **một tài khoản**. Cùng máy với hai tài khoản là hai thiết bị | Xác thực dùng mã thiết bị để thu hồi theo thiết bị |
| Lịch sử đăng nhập | Chuỗi các lần đăng nhập, đăng xuất, thất bại, thu hồi của một tài khoản | Nhật ký kiểm toán là khái niệm khác (hành động quản trị) |
| Phiên đang hoạt động | Phiên đã được cấp, chưa kết thúc, chưa quá hạn; suy ra từ lịch sử | Phiên gốc nằm ở Xác thực |
| Bản sao hiển thị | Email, vai, trạng thái nhận từ Xác thực để hiện trong danh sách; không dùng để quyết định | Nguồn gốc ở Xác thực |

## 3. Tác nhân

| Tác nhân | Việc |
|---|---|
| Người dùng | Xem và sửa tên hiển thị, xem thiết bị, lịch sử, phiên đang hoạt động; thu hồi phiên hoặc thiết bị của mình |
| Quản trị | Xem danh sách người dùng (kèm vai, trạng thái) để khóa hoặc mở |
| Xác thực | Nguồn event: tài khoản đã đăng ký, phiên được cấp, phiên kết thúc, đăng nhập thất bại, khóa/mở |

## 4. Mô hình

### 4.1 Hồ sơ định danh

Mỗi tài khoản có một hồ sơ, tạo khi nhận event "tài khoản đã đăng ký". Tên hiển thị mặc định lấy từ phần trước `@` của
email, người dùng sửa được. Tài khoản nội bộ được khởi tạo kèm tên. Tài khoản đã có từ trước khi Định danh bắt đầu chạy
được dựng bù hồ sơ, hoặc hồ sơ được tạo lần đầu khi cần (Q2).

Vai, email và trạng thái trong hồ sơ là **bản sao chỉ để hiển thị**, cập nhật theo event của Xác thực. Bản sao có thể chậm
hơn nguồn nên không dùng làm căn cứ quyết định.

### 4.2 Thiết bị

Thiết bị là tín hiệu nhận ra, không phải phần cứng. Mỗi thiết bị thuộc đúng một tài khoản. Ghi nhận khi một phiên được cấp
(lần đầu thấy, lần cuối thấy, địa chỉ mạng gần nhất, tên suy ra như "Chrome trên Windows"). Một thiết bị có nhiều phiên.

### 4.3 Lịch sử đăng nhập

Mỗi lần đăng nhập thành công là một dòng, có thời điểm kết thúc ghi một lần khi phiên kết thúc; mỗi lần thất bại là một
dòng. Chỉ ghi thất bại khi **tìm ra tài khoản**: email không tồn tại không vào lịch sử, để lịch sử không thành chỗ dò email
và không chứa email lạ. Lý do thất bại phân biệt được: sai mật khẩu, tài khoản khóa, chưa xác minh email, sai mã xác thực
nhiều lớp. Lịch sử giữ có thời hạn.

### 4.4 Phiên đang hoạt động và thu hồi

Danh sách phiên đang hoạt động được dựng từ lịch sử: dòng thành công chưa có thời điểm kết thúc và chưa quá hạn. Người dùng
chọn thu hồi một phiên, một thiết bị, hoặc tất cả trừ phiên hiện tại; Định danh gửi lệnh sang Xác thực. Xác thực thực thi
rồi phát event; Định danh cập nhật lịch sử theo event. Giao diện cập nhật theo kết quả của lệnh, không đợi event, vì danh
sách ở Định danh chỉ nhất quán cuối cùng.

## 5. Năng lực (dự kiến cho C1b)

Mỗi năng lực trả lời sáu câu của P3: (1) ai khởi tạo, (2) ai xử lý, (3) ai được báo, (4) đường không suôn sẻ, (5) ai xem lại
lịch sử, (6) dữ liệu chảy sang đâu.

### CAP-IDN-01 Tạo hồ sơ định danh
1. Event "tài khoản đã đăng ký". 2. Định danh tạo hồ sơ với tên mặc định. 3. Không ai. 4. Event giao lại: không tạo hai hồ sơ
(INV-IDN-05); tài khoản có từ trước: dựng bù (Q2). 5. Không có. 6. Không chảy đi.

### CAP-IDN-02 Sửa tên hiển thị
1. Người dùng. 2. Định danh cập nhật. 3. Không ai. 4. Tên rỗng hoặc quá dài: từ chối. 5. Không có. 6. Hồ sơ chảy tới nơi hiển
thị tên (giao diện, danh sách quản trị).

### CAP-IDN-03 Ghi nhận thiết bị
1. Event "phiên được cấp". 2. Định danh tìm hoặc tạo thiết bị của tài khoản, cập nhật lần cuối thấy. 3. Không ai (cảnh báo
thiết bị lạ cần Thông báo, chưa làm). 4. Event giao lại hoặc sai thứ tự: không tạo thiết bị trùng (INV-IDN-02). 5. Xem qua
danh sách thiết bị. 6. Mã thiết bị đi cùng dòng lịch sử.

### CAP-IDN-04 Ghi lịch sử đăng nhập
1. Event phiên được cấp, phiên kết thúc, đăng nhập thất bại. 2. Định danh thêm dòng; ghi thời điểm kết thúc một lần. 3. Không
ai. 4. Event giao lại: không ghi trùng (INV-IDN-04); thất bại không tìm ra tài khoản: bỏ qua (INV-IDN-03). 5. Người dùng và
Quản trị xem. 6. Không chảy đi.

### CAP-IDN-05 Xem phiên đang hoạt động và thu hồi
1. Người dùng. 2. Định danh liệt kê; thu hồi gửi lệnh sang Xác thực. 3. Người dùng thấy kết quả. 4. Thu hồi phiên đã kết thúc:
vẫn thành công, không báo lỗi; thu hồi phiên đang dùng: chặn, dùng đăng xuất. 5. Lịch sử có dòng kết thúc kèm lý do. 6. Lệnh
sang Xác thực.

### CAP-IDN-06 Quản trị xem người dùng
1. Quản trị. 2. Định danh liệt kê hồ sơ kèm bản sao vai, trạng thái. 3. Không ai. 4. Bản sao chậm hơn nguồn: giao diện ghi rõ là
tham khảo; khóa và mở thực hiện ở Xác thực. 5. Hành động khóa ghi ở Nhật ký kiểm toán. 6. Không chảy đi.

## 6. Bất biến (INV)

| ID | Bất biến | Lý do |
|---|---|---|
| INV-IDN-01 | Lịch sử chỉ ghi thêm, trừ thời điểm kết thúc của một lần đăng nhập, ghi đúng một lần | Lịch sử sửa được thì mất ý nghĩa điều tra |
| INV-IDN-02 | Mỗi thiết bị thuộc đúng một tài khoản; không có hai thiết bị cùng mã trong một tài khoản | Cùng máy hai tài khoản là hai thiết bị |
| INV-IDN-03 | Đăng nhập thất bại không tìm ra tài khoản thì không vào lịch sử | Không để lịch sử thành chỗ dò email |
| INV-IDN-04 | Mỗi (mã phiên, loại sự kiện) chỉ ghi một dòng | Event giao lại là chuyện thường |
| INV-IDN-05 | Mỗi tài khoản có tối đa một hồ sơ định danh | Event giao lại không nhân bản hồ sơ |
| INV-IDN-06 | Bản sao hiển thị không được dùng để quyết định quyền | Bản sao có thể chậm hơn nguồn |

## 7. Điểm nối với BC khác

| BC | Hướng | Nội dung | Chặng |
|---|---|---|---|
| Xác thực | Xác thực phát event | Tài khoản đã đăng ký; phiên được cấp, kết thúc; đăng nhập thất bại; khóa, mở | C1b |
| Xác thực | Định danh gọi | Thu hồi phiên; bỏ tin cậy thiết bị | C1b |
| Quản lý tệp | Hồ sơ trỏ tới | Tham chiếu ảnh đại diện | Khi kéo vào |
| Thông báo | Định danh yêu cầu | Cảnh báo đăng nhập từ thiết bị lạ | Khi kéo vào |

## 8. Câu hỏi cần trả lời

| # | Câu hỏi | Vì sao đáng hỏi | Đề xuất |
|---|---|---|---|
| Q1 | Tên hiển thị có bắt buộc khi đăng ký không | Tài khoản nội bộ không có hồ sơ Khách hàng nhưng vẫn cần nhãn hiển thị | Không bắt buộc; mặc định từ email, sửa sau |
| Q2 | Tài khoản có từ trước khi Định danh chạy: dựng bù một lần, hay tạo hồ sơ khi cần lần đầu | Ảnh hưởng thời điểm danh sách quản trị có đủ người | Dựng bù một lần khi bắt đầu |
| Q3 | Thời hạn giữ lịch sử; địa chỉ mạng lưu đầy đủ hay rút gọn | Dữ liệu cá nhân, chi phí lưu trữ | Chốt cùng bước thiết kế |
| Q4 | Cảnh báo đăng nhập từ thiết bị lạ có vào không | Cần Thông báo (sau C1b) | Cắt tạm |

## 9. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Ảnh đại diện | **Cắt tạm** | Khi có Quản lý tệp dùng được cho hồ sơ | Nhận ra người dùng trên giao diện | Phụ thuộc BC khác | Trường tham chiếu tệp trong hồ sơ |
| Cảnh báo đăng nhập từ thiết bị lạ | Chưa có ở root | Khi có Thông báo | Báo sớm khi bị chiếm tài khoản | Cần gửi thông báo | Event phiên được cấp kèm cờ thiết bị mới |
| Xuất lịch sử đăng nhập | Chưa có ở root | Không dự kiến | Quản trị điều tra sâu | Không phục vụ giao dịch | — |

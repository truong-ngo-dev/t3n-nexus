# Lịch sử đăng nhập

> Phạm vi: mô hình và quy tắc nghiệp vụ. Cách lưu, chỉ mục, phân trang thuộc nhóm kỹ thuật.
> Dựa trên: `../../oauth2-service/analysis/login-session.md` (event) và `device.md`.
> Ngày chốt: 2026-10-12.

## 1. Định nghĩa

**Lịch sử đăng nhập** là bản ghi, theo thời gian, các lần đăng nhập vào một tài khoản: thành công và thất bại. Người dùng xem để phát hiện đăng nhập lạ; quản trị xem để hỗ trợ và điều tra. Chỉ ghi thêm, không sửa (Chốt).

Lịch sử khác ba thứ dễ nhầm:

| Thứ | Khác ở đâu |
|---|---|
| Phiên (Xác thực) | Phiên chỉ giữ cái đang sống; lịch sử giữ cái đã xảy ra |
| Thiết bị (`device.md`) | Thiết bị là "đang có gì"; lịch sử là "đã xảy ra gì". Thiết bị chỉ giữ mốc cuối |
| Nhật ký bảo mật | Nhật ký bảo mật ghi **thay đổi** bảo mật của tài khoản (đổi mật khẩu, MFA, xác minh thiết bị...), chỉ quản trị xem, chưa làm. Lịch sử chỉ ghi **lần đăng nhập** |

## 2. Cấu trúc

Mỗi dòng là một lần đăng nhập. Hai loại dòng (Chốt):

| Loại | Nguồn | Khi nào ghi |
|---|---|---|
| **Thành công** | `SessionIssued` | Mở phiên người dùng |
| **Thất bại** | `LoginFailed` | Sai mật khẩu hoặc MFA, **chỉ khi tìm ra tài khoản** |

**Không ghi:** tự đăng xuất, đăng xuất từ xa, thu hồi, hết hạn, làm mới token, cấp phiên ứng dụng. Đó không phải lần đăng nhập, và trạng thái phiên không phải việc của lịch sử (Chốt).

| Thành phần | Nghĩa | Thành công | Thất bại |
|---|---|---|---|
| Mã dòng (UUID v7) | | Có | Có |
| Tài khoản | | Có | Có |
| Kết quả | Thành công hoặc thất bại | Có | Có |
| Thời điểm | | Có | Có |
| Mã phiên | Liên kết với phiên người dùng đã mở | Có | Không |
| Mã event | Chống ghi trùng khi event giao lặp | Có | Có |
| Thiết bị | Tham chiếu `Device` | Có | Không (không tạo thiết bị) |
| Bản băm mã thiết bị (`deviceKey`) | Để nhóm các lần thất bại theo thiết bị (không tạo thiết bị). Xác thực băm mã cookie rồi mới phát event; mã thô không rời Xác thực | Không | Có |
| Địa chỉ mạng | Lưu đầy đủ | Có | Có |
| Trình duyệt | Đã phân tích (trình duyệt, hệ điều hành) cùng chuỗi gốc cắt ngắn | Có | Có |

- Không có cột email: tài khoản đã xác định bằng mã (Chốt).
- Không có `ended_at`: lịch sử không theo dõi vòng đời phiên (Chốt).
- Dòng thất bại **không tạo thiết bị**; đăng nhập thất bại do tài khoản không tồn tại thì không ghi (không có tài khoản để gắn, và tránh bị dùng làm công cụ dò tài khoản).

## 3. Hành vi

| Hành vi | Khi nào | Hậu điều kiện | Event |
|---|---|---|---|
| **Ghi thành công** | Nhận `SessionIssued` | Thêm một dòng; trùng mã event thì bỏ qua | Không |
| **Ghi thất bại** | Nhận `LoginFailed` | Thêm một dòng; trùng mã event thì bỏ qua | Không |
| **Xem của mình** | Người dùng mở màn hình | Danh sách theo thời gian, mới trước; địa chỉ mạng rút gọn | Không |
| **Xem theo thiết bị** | Người dùng nhấn một thiết bị | Chỉ các dòng thành công của thiết bị đó | Không |
| **Quản trị xem** | Quản trị tra theo email | Của người dùng bất kỳ, chỉ đọc, địa chỉ mạng đầy đủ | Không |
| **Dọn** | Định kỳ, theo lô | Dòng cũ hơn 12 tháng bị xóa (cả thành công lẫn thất bại) | Không |

Không có thao tác sửa hay xóa từ người dùng (Chốt: chỉ ghi thêm).

## 4. Màn hình

| Màn hình | Nội dung |
|---|---|
| **Lịch sử đăng nhập** (người dùng) | Theo thời gian, cả thành công lẫn thất bại: kết quả, thời điểm, thiết bị (tên), địa chỉ mạng rút gọn, trình duyệt. Phân trang theo khóa, không đếm tổng |
| **Theo thiết bị** | Cùng màn hình, lọc theo thiết bị, mở từ danh sách thiết bị |
| **Quản trị** | Như trên, địa chỉ mạng đầy đủ, cả thành công lẫn thất bại |

## 5. Quy tắc

| Quy tắc | Nội dung | Lý do |
|---|---|---|
| Quy tắc | Lịch sử chỉ thêm dòng (INV-IDN-01), không sửa, không xóa theo yêu cầu người dùng | Bằng chứng cho người dùng và quản trị; người dùng sửa được thì mất ý nghĩa |
| Quy tắc | Mỗi event ghi tối đa một dòng (INV-IDN-04, chống trùng bằng mã event) | Event có thể giao lặp |
| Quy tắc | Dòng thất bại chỉ ghi khi tìm ra tài khoản (INV-IDN-03) | Không dò tài khoản, không có chủ để xem |
| Quy tắc | Người dùng chỉ xem lịch sử của mình; quản trị xem của người bất kỳ nhưng chỉ đọc | Quyền riêng tư; không ghi việc quản trị xem (thuộc nhật ký kiểm toán, ngoài phạm vi) |
| Quy tắc | Người dùng thấy địa chỉ mạng rút gọn; quản trị thấy đầy đủ | Giảm dữ liệu cá nhân hiển thị; đủ để điều tra |
| Quy tắc | Lịch sử chỉ giữ 12 tháng; dòng cũ hơn bị dọn theo lô | Dữ liệu cá nhân (địa chỉ mạng, trình duyệt) không giữ vô hạn |

## 6. Event

Lịch sử **không phát event**. Chỉ nhận:

| Event | Phát bởi | Nội dung dùng |
|---|---|---|
| `SessionIssued` | Xác thực | Mã phiên, tài khoản, thời điểm, thiết bị, địa chỉ mạng, thông tin thiết bị |
| `LoginFailed` | Xác thực | Tài khoản, thời điểm, bản băm mã thiết bị, địa chỉ mạng, trình duyệt (Xác thực chỉ phát khi tìm ra tài khoản) |

## 7. Chưa làm (ghi vào "Chưa làm" khi chuyển)

- Nhật ký bảo mật cho quản trị (đổi mật khẩu, MFA, xác minh thiết bị, khóa/mở tài khoản).
- Truy vấn chéo tài khoản theo địa chỉ mạng.
- Tra vị trí địa lý theo địa chỉ mạng.
- Cảnh báo bất thường dựa trên lịch sử (đăng nhập từ quốc gia lạ, nhiều lần thất bại).
- Người dùng đánh dấu "không phải tôi" trên một lần đăng nhập.

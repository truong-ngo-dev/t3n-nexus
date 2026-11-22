# ADR-0004: Mã một lần

> **Trạng thái:** Proposed · **Ngày:** `2026-10-13` (rà lại `2026-10-14`) · **Phạm vi:** Xác thực và Định danh (mọi mã một lần gửi qua email)
>
> Chuyển Accepted sau khi cài ở chặng đầu cần mã một lần (C3c).

## Bối cảnh

Mã một lần xác nhận hành động nhạy cảm bằng email: mã MFA khi đăng nhập, mã xác minh thiết bị (Định danh), mã xác nhận đăng xuất từ xa
(Xác thực). Khuôn nghiệp vụ chung ở `identity-service/analysis/device.md` mục 3: 6 số, hạn 5 phút, lưu bản băm, dùng một lần, một mã
còn hiệu lực cho mỗi (tài khoản, mục đích), xin mã tối đa 3 lần mỗi 5 phút, nhập sai tối đa 3 lần thì xóa mã.

Chỗ lưu và cách kiểm phụ thuộc ngữ cảnh gọi. Mã MFA nằm trong luồng đăng nhập có session HTTP. Hai mã còn lại do BFF gọi bằng token,
**không có cookie phiên** ở Xác thực lẫn Định danh nên không dùng được session.

## Quyết định

- **Mã MFA ở session**, giữ như mã hiện có (đồng bộ với luồng đăng nhập).
- **Mã xác minh thiết bị và mã đăng xuất từ xa ở Redis**, cùng một khuôn:
  - Khóa `otp:<mục đích>:<account>` giữ mã băm, số lần sai và (với mã xác minh thiết bị) bản băm mã thiết bị; hạn 5 phút; xin mã mới ghi đè mã cũ.
  - **Giới hạn xin mã (3 lần mỗi 5 phút mỗi tài khoản và mục đích) là luật nhóm 2**: dùng bộ đếm cửa sổ trượt của thư viện chung (`rate-limiter-starter`, `rate-limiting-layers.md`), gọi trực tiếp như giới hạn đăng nhập, khóa đếm tách khỏi khóa mã nên ghi đè mã không làm mất số lần đã xin. Số lần **nhập sai** nằm trong script cùng khóa mã (xem bên dưới).
  - Kiểm bằng **script Lua nguyên tử**: đọc, so, tăng số lần sai, xóa khi đúng hoặc khi sai đến lần thứ 3. Hai yêu cầu song song không
    vượt giới hạn.
  - Băm bằng **HMAC-SHA256 với khóa bí mật của server**; so sánh thời gian hằng.
  - Ngữ cảnh: khóa luôn theo (mục đích, tài khoản). Mã xác minh thiết bị còn gắn bản băm mã thiết bị lưu trong giá trị và kiểm khi nhập (xin mã cho thiết bị khác thì thay mã cũ); mã đăng xuất từ xa chỉ gắn (tài khoản, mục đích), không gắn phiên người gọi hay thiết bị đích.
- **Redis lỗi thì từ chối** (fail closed). Hai luồng Redis không có chỗ dự phòng (không có session).
- **Chuyển mã sang Thông báo** bằng event gửi email mang mã ở dạng rõ: không ghi log, không đưa vào payload nhật ký. Một lần bấm đăng
  xuất nhiều thiết bị gộp thành một thư.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Lưu mã trong session cho mọi loại | Hai luồng không có session ở nơi giữ mã |
| bcrypt cho mã | Không gian 10^6 mã thì băm chậm không bảo vệ thêm; hàng rào thật là giới hạn 3 lần sai |
| Kiểm bằng nhiều lệnh Redis rời | Hai yêu cầu song song vượt giới hạn số lần sai |
| Dự phòng bằng bảng cơ sở dữ liệu | Ngoài phạm vi hiện tại; có thể thêm sau nếu Redis lỗi trở thành vấn đề |
| Gắn mã đăng xuất từ xa với phiên người gọi | Chốt nghiệp vụ không cần (`device.md` mục 4) |

## Lý do

- Dùng lại khuôn đã có ở mã xác minh thiết bị (`RedisDeviceTrustOtpStore`: băm, 3 lần sai) và nâng lên cho đúng nghiệp vụ.
- Redis có sẵn hạn tự hết cho khóa, nên không cần job dọn.

## Hệ quả

- Redis phụ thuộc cứng cho hai luồng này: Redis chết thì không xác minh được thiết bị và không đăng xuất từ xa kèm mã được (mức hạ cấp để
  rà cùng các phụ thuộc Redis khác khi triển khai).
- Cần khóa bí mật HMAC trong cấu hình bí mật, không ở mã.

## Tham khảo

- `identity-service/analysis/device.md` mục 3, 4; `0.overview.md` (Xác thực) mục 9.

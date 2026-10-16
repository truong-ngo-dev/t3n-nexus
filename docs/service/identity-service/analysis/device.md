# Thiết bị

> Phạm vi: mô hình và quy tắc nghiệp vụ. Cách lưu, gọi đồng bộ, hiệu năng thuộc nhóm kỹ thuật.
> Dựa trên: `../../oauth2-service/analysis/login-session.md` (phiên, mã thiết bị, event).
> Cụm liên quan: `login-history.md` (lịch sử đăng nhập).
> Ngày chốt: 2026-10-12.

## 1. Định nghĩa

**Thiết bị** là một trình duyệt (một hồ sơ trình duyệt) hoặc một bản cài ứng dụng mà **một tài khoản** đã dùng để đăng nhập, nhận ra bằng mã thiết bị (cookie do Xác thực cấp, xem phiên đăng nhập). Không phải phần cứng. Cùng một máy, hai tài khoản là hai thiết bị (Chốt).

Cụm thiết bị trả lời ba câu hỏi của người dùng:
- Tài khoản của tôi đã đăng nhập từ những đâu, đang đăng nhập ở đâu?
- Thiết bị này có phải của tôi đã xác minh không?
- Tôi muốn đăng xuất một thiết bị khác.

Ba trạng thái của thiết bị đối với tài khoản (Chốt):

| Trạng thái | Cách có | Cảnh báo thiết bị lạ | Hành động nhạy cảm |
|---|---|---|---|
| Chưa biết | Lần đầu đăng nhập, hoặc xóa cookie, hoặc đổi trình duyệt | **Có** | Mật khẩu + OTP email |
| Đã biết, chưa xác minh | Đã đăng nhập thành công, chưa bấm "Xác minh thiết bị này" | Không | Mật khẩu + OTP email |
| **Đã xác minh** | Bấm "Xác minh thiết bị này", nhập OTP email | Không | Mật khẩu |

"Chưa biết" không phải trạng thái lưu: đó là chưa có thiết bị cho (tài khoản, mã thiết bị).

## 2. Cấu trúc

### 2.1 Thiết bị (gốc của aggregate `Device`)

| Thành phần | Nghĩa |
|---|---|
| Mã thiết bị (`id`, UUID v7) | Định danh trong Định danh |
| Tài khoản | Chủ thiết bị |
| `deviceKey` | Bản băm của mã thiết bị trong cookie hoặc mã cài ứng dụng. Duy nhất theo (tài khoản, `deviceKey`) |
| Tên hiển thị | Suy ra từ User-Agent lúc đăng ký (ví dụ "Chrome trên Windows"); loại (web, di động) |
| Ngày đăng ký | Lần đăng nhập thành công đầu tiên |
| Trạng thái xác minh | `verifiedAt` (rỗng = chưa xác minh) |

Aggregate `Device` gồm cả thông tin nhận ra thiết bị và trạng thái xác minh cùng luồng OTP xác minh. Trạng thái xác minh lưu ở bảng riêng (`device_verification`), vẫn thuộc aggregate (Chốt).

### 2.2 Hoạt động của thiết bị (`DeviceActivity`, bảng riêng cùng khóa với `Device`)

| Thành phần | Nghĩa | Ghi khi |
|---|---|---|
| `lastLoginAt` | Lần nhập mật khẩu gần nhất trên thiết bị | Nhận `SessionIssued` |
| `lastActiveAt` | Lần cuối có token được cấp cho thiết bị (đổi mã lấy token sau đăng nhập hoặc xác thực im lặng, làm mới) | Nhận `ClientSessionIssued` |
| `lastIpAddress` | Địa chỉ mạng của lần đăng nhập gần nhất | Nhận `SessionIssued` |

- `SessionEnded` **không** cập nhật `DeviceActivity` (Chốt). Thiết bị đã đăng xuất dừng ở lần cấp token cuối.
- Địa chỉ mạng và trình duyệt không cập nhật theo `ClientSessionIssued`: lời gọi làm mới đến từ BFF, không phải từ người dùng.
- Tách khỏi `Device` vì `Device` gần như bất biến còn `DeviceActivity` đổi liên tục (Chốt).
- Độ chính xác của `lastActiveAt` bằng hạn access token, nên giao diện hiện dạng làm tròn ("trong 1 giờ qua").

### 2.3 Trạng thái "đang đăng nhập"

Không có bản sao ở Định danh (không bộ đếm, không cờ). Lấy từ Xác thực lúc xem: truy vấn "các phiên người dùng đang sống của tài khoản", ghép theo `deviceKey` (Chốt). Xác thực lỗi hoặc chậm thì hiện "không rõ".

## 3. Hành vi

| Hành vi                  | Khi nào                                   | Tiền điều kiện                                                          | Hậu điều kiện                                                                                                                                               | Event                                |
|--------------------------|-------------------------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| **Đăng ký thiết bị**     | Nhận `SessionIssued` của thiết bị chưa có | Đăng nhập thành công                                                    | Có `Device` (chưa xác minh); `DeviceActivity` đặt `lastLoginAt` và `lastActiveAt` bằng lúc đăng nhập; **nhờ Thông báo gửi cảnh báo thiết bị lạ** (kênh gửi do Thông báo quyết định, mô hình không phụ thuộc kênh) | Không                                |
| **Ghi đăng nhập**        | Nhận `SessionIssued` của thiết bị đã có   |                                                                         | Cập nhật `lastLoginAt`, `lastIpAddress`                                                                                                                     | Không                                |
| **Ghi hoạt động**        | Nhận `ClientSessionIssued`                | Thiết bị đã có                                                          | Cập nhật `lastActiveAt` (kỹ thuật: ghi theo ngưỡng hoặc gộp lô, bàn sau)                                                                                    | Không                                |
| **Yêu cầu OTP xác minh** | Người dùng bấm "Xác minh thiết bị này"    | Đang đăng nhập **trên chính thiết bị đó** (lấy từ phiên); chưa xác minh | Gửi OTP email                                                                                                                                               | Không                                |
| **Xác minh thiết bị**    | Nhập đúng OTP                             | OTP còn hạn, chưa dùng, chưa quá 3 lần sai                              | `verifiedAt` được đặt                                                                                                                                       | `DeviceVerified`                     |
| **Gỡ xác minh**          | Người dùng chủ động                       | Đang xác minh                                                           | `verifiedAt` rỗng. Có thể kèm OTP về sau                                                                                                                    | `DeviceUnverified`                   |
| **Xóa thiết bị**         | Người dùng chọn xóa trong danh sách       | Thiết bị thuộc tài khoản; không có phiên sống                           | `Device` và `DeviceActivity` bị xóa; lịch sử đăng nhập không bị đụng tới                                                                                    | `DeviceUnverified` nếu đang xác minh |
| **Xem danh sách**        | Người dùng mở màn hình                    |                                                                         | `Device` của tài khoản + trạng thái sống từ Xác thực                                                                                                        | Không                                |

**Khuôn mã một lần** (Chốt 2026-10-12; dùng chung cho xác minh thiết bị, xác nhận đăng xuất từ xa và về sau MFA lúc đăng nhập, rút từ cách MFA và xác thực thiết bị đang làm):

- Mã 6 số, hạn 5 phút, lưu bản băm, dùng một lần.
- Một mã còn hiệu lực cho mỗi (tài khoản, mục đích); xin mã mới thì thay mã cũ.
- Giới hạn xin mã: 3 lần mỗi 5 phút mỗi tài khoản. Nhập sai tối đa 3 lần thì mã bị xóa.
- Gửi bằng sự kiện sang Thông báo, mỗi mục đích có mẫu thư riêng.
- Mã của mục đích này không dùng cho mục đích khác. Phần khác nhau giữa các mục đích: ai giữ mã và mã gắn với cái gì.
- Xác minh thiết bị: mã gắn với (tài khoản, `deviceKey`) và do Định danh giữ (thuộc `Device`).
- Khi chuyển vào tài liệu chính: viết khối này một lần ở Xác thực, các nơi khác tham chiếu.

**Kênh gửi mã (Chốt 2026-10-12):** hiện chỉ có email. Về sau thêm ứng dụng authenticator làm kênh để người dùng chọn; mô hình ở đây không phụ thuộc kênh nên khi thêm không phải đổi. Cơ chế mã một lần là một, dùng chung với xác thực nhiều lớp.

### Hiệu lực của "đã xác minh" (Chốt)

- Duy nhất một hiệu lực: hạ mức xác nhận hành động nhạy cảm từ "mật khẩu + OTP email" xuống "mật khẩu".
- **Không** bỏ qua MFA (MFA là cấu hình riêng của người dùng). Không có đặc quyền theo loại thiết bị.
- Không có trần số thiết bị xác minh. Không tự hết hạn; chỉ mất khi người dùng gỡ.
- Hành động nhạy cảm đầu tiên áp dụng: **đăng xuất từ xa** (xem mục 4). Về sau: đổi email, cấu hình OTP, thông tin khôi phục, tài khoản nhận tiền của người bán.
- Xác thực hỏi Định danh trạng thái xác minh khi cần (đồng bộ); hỏi lỗi thì coi là **chưa xác minh** (chặt hơn, an toàn). Định danh chỉ cung cấp dữ kiện, không quyết định đăng nhập.

## 4. Đăng xuất từ xa và thu hồi mọi phiên

Điểm cuối đặt ở **Xác thực** (chỉ Xác thực kiểm mật khẩu); Định danh chỉ cung cấp danh sách thiết bị và trạng thái xác minh. Phần dưới đã chốt từng ý (2026-10-12).

| # | Ý | Chốt |
|---|---|---|
| 1 | Hai thao tác trên giao diện | **Đăng xuất thiết bị này** (nút trên từng dòng) và **Đăng xuất mọi thiết bị khác** (nút chung). Cùng một điểm cuối, cùng quy tắc, cùng lý do kết thúc "bị đăng xuất từ xa" (gộp lý do như đã chốt ở phiên) |
| 2 | Thiết bị bị chọn có nhiều phiên | Kết thúc **mọi phiên người dùng còn hiệu lực của thiết bị đó** (thường chỉ một; nhiều phiên là trường hợp hiếm). Mỗi phiên bị kết thúc theo hành vi **Kết thúc** của phiên: xóa phiên người dùng và các phiên ứng dụng con, vô hiệu token, báo ứng dụng xóa token |
| 3 | Thiết bị của chính người gọi | Dòng thiết bị hiện tại đánh dấu "Thiết bị này", **không có nút** đăng xuất từ xa (dùng đăng xuất thường). "Đăng xuất mọi thiết bị khác" kết thúc mọi phiên người dùng còn hiệu lực của tài khoản **trừ phiên người dùng của người gọi**, nhận biết bằng mã trong token. Không có quy tắc riêng cho phiên cũ cùng thiết bị: nó là phiên khác mã nên bị chọn như mọi phiên khác. Phiên hết hạn do job dọn định kỳ, không xét |
| 4 | Mức xác nhận | Thiết bị của người gọi **đã xác minh**: nhập lại mật khẩu. **Chưa xác minh** (kể cả hỏi Định danh lỗi): mật khẩu + mã một lần qua email. Không đòi MFA (MFA là lớp lúc đăng nhập) |
| 5 | Thông báo cho chủ tài khoản | **Gửi email cho mọi lần** đăng xuất từ xa (kể cả khi người gọi ở thiết bị đã xác minh, vì kẻ chiếm phiên cũng có thể ở thiết bị đã xác minh). Nội dung: thiết bị nào bị đăng xuất, lúc nào, từ thiết bị nào. Nguồn là `SessionEnded` với lý do "bị đăng xuất từ xa", không thêm event; một lần bấm kết thúc nhiều phiên thì gộp thành một thư (cách gộp là việc kỹ thuật) |

**Mã một lần của hành động này (4a, 4b, 4c):**
- Xác thực giữ mã, lưu ở kho có hạn ngoài HTTP session (lời gọi đến từ BFF, không có cookie phiên của Xác thực). Gửi qua sự kiện sang Thông báo, mỗi mục đích có mẫu thư riêng.
- Quy tắc mã theo khuôn chung (mục 3, "Khuôn mã một lần"). Gắn với (tài khoản, mục đích), không cần gắn phiên người gọi hay thiết bị đích.
- Mật khẩu nhập sai ở đường này đếm bằng bộ giới hạn tần suất có sẵn, khóa riêng cho thao tác; khi giới hạn đăng nhập (CAP-ATH-02, kế hoạch feature 02, chưa làm) có thì dùng chung.

**Đã chốt từ trước:**
- Đăng xuất từ xa luôn thành công kể cả khi phiên đã kết thúc (INV-ATH-05).
- Quản trị không có thao tác thu hồi phiên của người khác; khóa tài khoản đã kết thúc mọi phiên.

## 5. Màn hình

| Màn hình | Nội dung |
|---|---|
| **Danh sách thiết bị** | Mỗi thiết bị: tên, "Đang đăng nhập" hoặc "Đăng nhập lần cuối", mốc chính "Đăng nhập lúc", dòng phụ làm tròn "hoạt động gần đây", nhãn đã xác minh, nút đăng xuất thiết bị khác, nút xóa thiết bị. Phiên đã kết thúc không nằm ở đây |
| **Quản trị** | Tra theo email; xem thiết bị và phiên sống của người dùng, chỉ đọc |

Nhấn vào một thiết bị thì chuyển sang lịch sử đăng nhập của thiết bị đó (xem `login-history.md`).

## 6. Quy tắc

| Quy tắc | Nội dung | Lý do |
|---|---|---|
| Quy tắc | Thiết bị duy nhất theo (tài khoản, `deviceKey`) (INV-IDN-02) | Không trộn thiết bị giữa các tài khoản trên cùng trình duyệt |
| Quy tắc | Chỉ đăng nhập **thành công** mới tạo thiết bị | Đăng nhập thất bại không làm đầy danh sách |
| Quy tắc | Thiết bị chưa biết gửi cảnh báo khi đăng nhập; đã biết chưa xác minh thì không cảnh báo lại | Báo đúng chỗ, không làm phiền |
| Quy tắc | Chỉ xác minh thiết bị đang đăng nhập, từ chính thiết bị đó | Kẻ có mật khẩu ở máy khác không xác minh hộ máy chủ nhà |
| Quy tắc | Xóa cookie hoặc dùng ẩn danh thì thành thiết bị mới | Hệ quả của mã thiết bị theo cookie |
| Quy tắc | Không có dọn tự động: chỉ người dùng xóa thiết bị của mình | Đơn giản, không job định kỳ; thiết bị đã biết không tự biến mất bất ngờ |
| Quy tắc | Thiết bị **không đổi tên được**: tên suy ra từ User-Agent lúc đăng ký | Tên trên dòng lịch sử và tên thiết bị không lệch vì người dùng sửa |
| Quy tắc | Nhắc xác minh chỉ một lần, ngay sau khi đăng ký thiết bị mới, bỏ qua được và không chặn việc đang làm | Người dùng biết tính năng mà không bị làm phiền |
| Quy tắc | Không dựng bản sao "đang đăng nhập" ở Định danh | Sai khi thiết bị có nhiều phiên và khi phiên hết hạn không có event |

## 7. Event

| Event | Phát bởi | Nhận bởi | Dùng để |
|---|---|---|---|
| `SessionIssued` | Xác thực | Định danh | Đăng ký thiết bị, `lastLoginAt`, `lastIpAddress`, ghi lịch sử |
| `ClientSessionIssued` | Xác thực | Định danh | `lastActiveAt` |
| `SessionEnded` | Xác thực | Không dùng cho thiết bị | (Chốt: không cập nhật `DeviceActivity`) |
| `DeviceVerified`, `DeviceUnverified` (tên chờ) | Định danh | Nhật ký bảo mật về sau | |

## 8. Chưa làm (ghi vào "Chưa làm" khi chuyển)

- Thiết bị xác minh bằng ứng dụng di động làm yếu tố (duyệt đăng nhập, khôi phục): khi có ứng dụng di động cho người dùng thường.
- Kênh mã một lần bằng ứng dụng authenticator, người dùng tự chọn kênh (cùng xác thực nhiều lớp).
- Hạn tự mất của xác minh khi có đặc quyền nặng hơn; trần mềm số thiết bị xác minh. Với tài khoản nội bộ: xem lại khi họ có hành động nhạy cảm tác động lớn dùng chung mức xác nhận này (đổi email, đổi cấu hình MFA, thao tác tiền); khi đó thêm hạn tự mất hoặc luôn đòi OTP cho nội bộ.
- Giữ vài tuần mục "Đã đăng xuất" ở danh sách thiết bị (kiểu Google).
- Khôi phục tài khoản bằng thiết bị đang đăng nhập (roadmap).
- Xác nhận hành động nhạy cảm cho các hành động khác (chọn mô hình cùng hành động đầu tiên cần).
- Gắn mã thiết bị với phần cứng (xem phiên đăng nhập).
- Dọn thiết bị tự động theo thời gian (thiết bị chưa xác minh, không phiên sống, quá N ngày): xem lại nếu danh sách ẩn danh thành vấn đề thực tế.
- Đổi tên thiết bị: nếu thêm, dòng lịch sử vẫn giữ tên tại thời điểm đăng nhập (chấp nhận); khi đó xem lại việc lịch sử mang tên riêng.
- Bản sao trạng thái xác minh ở Xác thực: chỉ xem lại nếu thành đường nóng; khi đó dùng bộ nhớ đệm ngắn hạn vô hiệu hóa bằng event.


## 9. Tham khảo từ hệ thống khác

Dùng để hình dung, không phải chứng cứ chính thức. Ngày ghi `2026-10-11`. Các nguồn là trang hỗ trợ và tin tức, chưa đối chiếu văn bản gốc và chưa ghi đường dẫn.

| Nội dung | Hệ thống | Dùng để quyết |
|---|---|---|
| "Thiết bị tin cậy" thường nghĩa là bỏ qua bước xác thực thứ hai | Google, Microsoft Entra, Okta, Auth0 | Ta **không** chọn nghĩa này: thiết bị đã xác minh chỉ hạ mức xác nhận hành động nhạy cảm (mục 3) |
| Xác minh thiết bị mới | GitHub | Gần nghĩa ta chọn |
| Thiết bị làm yếu tố hoặc có đặc quyền | Apple, Telegram | Ta không chọn; ghi ở "Chưa làm" |
| Phiên đã kết thúc không nằm trong danh sách thiết bị; chỉ có ở lịch sử | Facebook, Apple, LinkedIn | Danh sách thiết bị chỉ hiện phiên đang sống và thiết bị đã dùng |
| Giữ vài tuần mục "Đã đăng xuất" trong danh sách | Google | Ghi ở "Chưa làm" |
| Đăng xuất từ xa đòi nhập lại mật khẩu | LinkedIn; OWASP ASVS V7.5.2 | Mức xác nhận của đăng xuất từ xa (mục 4) |
| Đổi thiết bị thì bị xác thực thêm (Soft OTP phải kiểm tra lại khi dùng trên thiết bị khác thiết bị gần nhất theo Quyết định 2345/QĐ-NHNN; xác thực khuôn mặt khi đổi thiết bị) | Ngân hàng, MoMo (Việt Nam) | Người dùng ở Việt Nam quen với việc đổi thiết bị phải xác thực thêm |
| Vân tay trình duyệt do client tính không đáng tin làm mã thiết bị | Okta đã bỏ kiểu này | Mã thiết bị là cookie do máy chủ cấp (`login-session.md`) |

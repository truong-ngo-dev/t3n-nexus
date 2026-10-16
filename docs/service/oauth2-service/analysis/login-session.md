# Phiên đăng nhập

> Phạm vi: mô hình và quy tắc nghiệp vụ của phiên. Cách lưu, giao dịch, hiệu năng, liên kết với Spring thuộc nhóm kỹ thuật.
> Ngày chốt: 2026-10-11.

## 1. Định nghĩa

**Phiên đăng nhập** đại diện cho một phiên làm việc của người dùng với hệ thống: từ lúc nhập đúng mật khẩu trên một thiết bị, trong suốt thời gian người đó đăng nhập và thao tác ở các ứng dụng (Cửa hàng, Người bán, Vận hành, Shipper), đến khi phiên kết thúc. Đây là nơi duy nhất quyết định người đó còn được coi là đã xác thực hay không (Chốt).

"Người dùng" viết thường ở đây là mọi tài khoản, kể cả vai nội bộ; không phải vai Người dùng (viết hoa) trong glossary.

Phiên đăng nhập có **hai khái niệm chính**:

| Khái niệm | Là gì | Nhãn |
|---|---|---|
| **Phiên người dùng** | Phần đại diện cho lần nhập mật khẩu trên một thiết bị: ai, trên thiết bị nào, từ lúc nào, sống tới bao giờ. Giữ vòng đời của cả phiên đăng nhập | Chốt |
| **Phiên ứng dụng** | Phần đại diện cho việc người dùng thao tác ở **một ứng dụng** trong phiên đó. Một phiên người dùng có một hay nhiều phiên ứng dụng, mỗi ứng dụng tối đa một | Chốt |

Phiên đăng nhập là **một aggregate** (tên cũng là tên aggregate): phiên người dùng là gốc, phiên ứng dụng là thực thể con (Chốt 2026-10-11). Điều kiện xem lại: đăng xuất riêng một ứng dụng mà vẫn giữ phiên người dùng; hạn hoặc chính sách riêng từng ứng dụng; phiên ứng dụng có trạng thái riêng đáng kể; tranh chấp ghi đo được. Lý do một aggregate: cả ba bất biến sau phải giữ nhất quán cùng nhau.
- Mỗi (phiên người dùng, ứng dụng) có tối đa một phiên ứng dụng.
- Phiên người dùng kết thúc thì mọi phiên ứng dụng của nó kết thúc ngay.
- Phiên ứng dụng không sống lâu hơn phiên người dùng.

"Thiết bị" ở đây là một trình duyệt hay một ứng dụng cài đặt, không phải phần cứng. Một thiết bị có thể có nhiều phiên người dùng (đăng nhập lại khi phiên cũ còn sống) và một tài khoản có thể có nhiều phiên người dùng trên nhiều thiết bị cùng lúc.

## 2. Cấu trúc

### 2.1 Phiên người dùng (gốc)

| Thành phần | Ý nghĩa | Nhãn |
|---|---|---|
| Mã phiên | Định danh phiên trong miền và trong event. Cấp ở miền, không lấy từ hạ tầng | Chốt |
| Tài khoản | Chủ của phiên | Chốt |
| Thiết bị | Mã nhận ra thiết bị mà phiên người dùng trên đó, để Định danh ghép "đang đăng nhập" với thiết bị. Mã do Xác thực cấp, ngẫu nhiên, đặt vào cookie của trình duyệt (bản cài ứng dụng di động tự sinh một lần khi cài); phiên chỉ lưu bản băm | Chốt (xem "Mã thiết bị" bên dưới) |
| Thời điểm mở | Lúc nhập mật khẩu đúng; là "đăng nhập lúc" khi hiển thị | Chốt |
| Hạn phiên | Mốc hết hạn của phiên, tính từ lúc mở, **không bao giờ kéo dài** dù đang dùng. Chặn phiên bị đánh cắp dùng mãi. Là hạn duy nhất của phiên | Chốt có một hạn; con số ở 3.4 |

Phiên người dùng không lưu địa chỉ mạng và thông tin thiết bị lúc mở (Chốt 2026-10-11): hai thứ này chỉ mang trong `SessionIssued` cho Định danh, vì Xác thực không có hành vi nào đọc chúng. Không có thời điểm kết thúc: phiên kết thúc thì xóa, vì Xác thực chỉ giữ phiên đang sống (lịch sử thuộc Định danh).

### 2.2 Phiên ứng dụng (con)

| Thành phần | Ý nghĩa | Nhãn |
|---|---|---|
| Mã phiên ứng dụng | Đi trong hợp đồng danh tính để ứng dụng và dịch vụ khác nhận ra phiên | Chốt |
| Ứng dụng | Client đã đăng ký mà phiên này thuộc về; cũng là nơi giữ token để báo xóa khi phiên kết thúc | Chốt |
| Liên kết token | Trỏ tới bản ghi cấp token (mã làm mới, mã truy cập) của phiên ứng dụng này. **Đổi được**: khi ứng dụng xin cấp lại token (hết hạn refresh hoặc mất token) thì phiên ứng dụng giữ nguyên mã và chuyển liên kết sang bản ghi mới; bản ghi cũ bị thay, không để hai bộ token cùng sống | Chốt có mặt; cách lưu là kỹ thuật |
| Thời điểm mở | Lúc phiên ứng dụng được tạo | Chốt |

Phiên ứng dụng **không có hạn riêng**; cả phiên người dùng lẫn phiên ứng dụng **không giữ mốc hoạt động** (Chốt 2026-10-12): phiên ứng dụng sống khi và chỉ khi phiên người dùng sống, còn "hoạt động gần đây" của thiết bị do Định danh giữ nhờ `ClientSessionIssued` (`identity-service/analysis/device.md`).

## 3. Hạn và chính sách

### 3.1 Nguyên tắc

1. Phiên người dùng là nơi duy nhất quyết định sống hay chết (Chốt).
2. Mọi token có hạn = min(TTL của loại token, phần còn lại của phiên người dùng) (Chốt hướng; xem 3.3).
3. Hết hạn là **suy ra** từ hạn, không phải một trạng thái, không có hành vi và không có event (Chốt).
4. Access token hạn ngắn và cố định; refresh token xoay vòng: mỗi lần làm mới cấp cái mới, cái cũ vô hiệu và bị từ chối nếu đưa lại (Chốt 2026-10-11). **Không** phát hiện dùng lại refresh token cũ để kết thúc phiên: thư viện không có sẵn (bản ghi cấp token cập nhật tại chỗ, token cũ chỉ bị từ chối như token rác) và với kiến trúc BFF giữ token phía máy chủ, lợi ích thấp so với chi phí tự xây và báo động giả.

### 3.2 Một hạn của phiên, một TTL của refresh token

| | Của ai | Đổi thế nào |
|---|---|---|
| **Hạn phiên** | Phiên người dùng | Cố định từ lúc mở, không đổi |
| **TTL refresh token** | Cấu hình theo vai (3.4) | Mỗi lần cấp refresh token mới (làm mới, hoặc xác thực im lặng cấp lại token): hạn token = min(TTL refresh token, hạn phiên − bây giờ) |

Phiên người dùng **không có hạn không hoạt động** và không có hạn trượt (Chốt). "Cửa sổ trượt" chỉ là TTL của refresh token: ứng dụng bỏ không quá TTL này thì mất token, rồi xin lại qua xác thực im lặng bằng phiên người dùng và được cấp token mới, miễn là phiên còn hạn.

Hệ quả đã chấp nhận: phiên không tự hết vì bỏ không; nó chỉ hết ở hạn phiên hoặc khi bị kết thúc. Với vai nội bộ, hạn phiên ngắn (10 giờ) là giới hạn cho trường hợp máy bỏ quên.

### 3.3 Cắt hạn token theo phiên

Khi cấp access token hoặc refresh token, nếu phần còn lại của phiên người dùng ngắn hơn TTL của loại token thì hạn token = phần còn lại của phiên. Token không bao giờ sống lâu hơn phiên. Lý do: nếu để nguyên TTL thì phiên đã hết hạn tuyệt đối vẫn còn token dùng được thêm tới một TTL, làm hạn tuyệt đối mất ý nghĩa (Keycloak làm theo hướng cắt; Okta để token sống tiếp tới hết hạn).

### 3.4 Con số đề xuất

Chính sách theo **vai của tài khoản**, không theo ứng dụng. Cửa hàng, Người bán, Shipper đều là vai Người dùng, nên cùng một chính sách; theo ứng dụng thì phiên người dùng dùng chung nhiều ứng dụng không biết lấy hạn của ứng dụng nào.

| Tham số | Vai Người dùng | Vai nội bộ | Môi trường dev (mọi vai) |
|---|---|---|---|
| TTL access token | 15 phút | 15 phút | 1 phút |
| TTL refresh token | 7 ngày | 30 phút | 3 phút |
| Hạn phiên | 30 ngày | 10 giờ | 10 phút |
| TTL mã ủy quyền | 5 phút | 5 phút | 5 phút |

Con số đã chốt (2026-10-11); nguồn là thông lệ (mặc định Keycloak: access 5 phút, idle 30 phút, max 10 giờ), **chưa dựa vào dữ liệu thật của dự án**.

Cột dev rút ngắn để thử được cả ba mốc trong một lần ngồi thử: access token hết sau 1 phút (thấy làm mới), refresh token hết sau 3 phút bỏ không (thấy SSO cấp lại token), hạn phiên sau 10 phút (thấy token bị cắt theo phiên rồi phải nhập lại mật khẩu). Cột này chỉ áp dụng ở profile `dev`, thỏa cùng hai ràng buộc bên dưới.

Ràng buộc cấu hình (kiểm khi khởi động):
- TTL refresh token ≥ 2 lần TTL access token. Ngắn hơn thì ứng dụng có thể mất token giữa hai lần làm mới.
- Hạn phiên > TTL refresh token.

## 4. Hành vi

| Hành vi | Khi nào | Tiền điều kiện | Hậu điều kiện | Event |
|---|---|---|---|---|
| **Mở** | Nhập đúng mật khẩu | Tài khoản đang hoạt động | Có phiên người dùng (hạn phiên được đặt); chưa có phiên ứng dụng vì chưa có bản ghi cấp token | `SessionIssued` |
| **Cấp phiên ứng dụng** (sau khi đăng nhập bằng mật khẩu, hoặc đăng nhập chung và xác thực im lặng khi ứng dụng xin token mà phiên người dùng còn hiệu lực) | Lúc đổi mã lấy token; chưa biết mã phiên ứng dụng, tra theo ứng dụng | Phiên người dùng còn hiệu lực | Ứng dụng chưa có phiên thì tạo một phiên ứng dụng; đã có (ví dụ khi mất token ở ứng dụng) thì giữ nguyên phiên, đổi liên kết token sang bản ghi mới và bỏ bản ghi cũ; token cấp có hạn đã cắt (3.2, 3.3) | `ClientSessionIssued` |
| **Làm mới phiên ứng dụng** (làm mới token) | Ứng dụng gửi refresh token kèm mã phiên ứng dụng | Phiên người dùng còn hiệu lực; mã phiên ứng dụng thuộc phiên này | Giữ nguyên phiên ứng dụng và liên kết token (cập nhật tại chỗ); cấp refresh token mới có hạn = min(TTL refresh token, hạn phiên − bây giờ) | `ClientSessionIssued` |
| **Kết thúc** | Một trong bốn lý do ở bảng bên dưới | Không | Xóa phiên người dùng và mọi phiên ứng dụng; vô hiệu mọi token; báo từng ứng dụng xóa token của mình. Thành công cả khi phiên đã kết thúc hoặc đã hết hạn | `SessionEnded` kèm lý do |
| **Hết hạn** | Khi có người hỏi hoặc dọn theo lô | | Không có hành vi; dòng quá hạn dọn định kỳ | Không |

### Mã thiết bị (Chốt 2026-10-11)

- Thiết bị là một trình duyệt (một hồ sơ trình duyệt) hoặc một bản cài ứng dụng, không phải phần cứng. Hai hồ sơ Chrome trên cùng máy, hoặc Chrome và Firefox, là hai thiết bị; hai cửa sổ cùng một hồ sơ là một thiết bị.
- Mã do Xác thực cấp, ngẫu nhiên, đặt vào cookie của trình duyệt ngay khi trình duyệt vào trang đăng nhập, **trước** khi nhập mật khẩu, để đăng nhập thất bại cũng mang được mã. Phiên và dữ liệu chỉ lưu bản băm. Ứng dụng di động tự sinh mã một lần khi cài, giữ trong vùng lưu trữ an toàn.
- Cookie còn sau khi đăng xuất. Xóa cookie, hoặc dùng cửa sổ ẩn danh, thì thành thiết bị mới (ẩn danh: mỗi lần mở là một thiết bị mới, đã kiểm trên mã cũ cũng cho kết quả này).
- Một cookie dùng chung cho nhiều tài khoản đăng nhập trên cùng trình duyệt, nhưng thiết bị được khóa theo (tài khoản, mã thiết bị) nên không trộn thiết bị giữa các tài khoản.
- Mã thiết bị không tự cấp quyền gì: ai lấy được cookie thì giả được thiết bị "đã biết", nhưng vẫn phải có mật khẩu; ảnh hưởng chỉ là nhận ra thiết bị và mức xác nhận hành động nhạy cảm khi thiết bị đã xác minh.
- Không có dọn thiết bị tự động: người dùng tự xóa thiết bị trong danh sách (`identity-service/analysis/device.md`). Thuộc tính cookie (tên, hạn, cờ bảo mật) là kỹ thuật, bàn sau.

### Lý do kết thúc (Chốt 2026-10-11)

| Lý do | Khi nào | Phạm vi kết thúc |
|---|---|---|
| Tự đăng xuất | Người dùng đăng xuất trên chính phiên | Chỉ phiên đó |
| Bị đăng xuất từ xa | Đăng xuất từ xa một thiết bị khác, hoặc thu hồi mọi phiên khác (gộp vì hành vi trên mỗi phiên giống nhau) | Các phiên bị chọn |
| Tài khoản bị khóa | Quản trị khóa tài khoản | Mọi phiên của tài khoản, có hiệu lực ngay |
| Đổi mật khẩu | Người dùng đổi mật khẩu | Mọi phiên **trừ phiên đang đổi** |

Hết hạn không có lý do vì không có event. Đổi mật khẩu chưa nằm trong C0; quy tắc ghi sẵn cho khi nó được làm. Phát hiện dùng lại refresh token không triển khai nên chưa có lý do "nghi ngờ lộ token" (mục 3.1, điểm 4).

Truy vấn:
- Các phiên người dùng đang sống của một tài khoản (Định danh dùng để hiện "đang đăng nhập" theo thiết bị).
- Phiên còn hiệu lực hay không (mọi nơi xác nhận phiên).

## 5. Quy tắc và bất biến

| Quy tắc | Nội dung | Lý do |
|---|---|---|
| INV-ATH-05 | Đăng xuất luôn thành công | Người dùng đạt trạng thái chưa đăng nhập dù lý do gì |
| INV-ATH-08 | Phiên hết hạn hoặc bị kết thúc thì không còn hiệu lực | Mất ý nghĩa của hạn và đăng xuất nếu vẫn dùng được |
| INV-ATH-10 | Mỗi (phiên người dùng, ứng dụng) có tối đa một phiên ứng dụng đang sống | Đăng nhập chung không nhân bản phiên; kết thúc một phiên là đủ cắt quyền |
| Mới | Phiên ứng dụng không sống lâu hơn phiên người dùng | Cắt phiên người dùng là cắt hết |
| Mới | Hạn phiên không bao giờ kéo dài | Phiên bị đánh cắp không dùng mãi được |
| Mới | Token không sống lâu hơn phiên người dùng (3.3) | Như trên |
| Quy tắc | Nhiều thiết bị: một tài khoản có nhiều phiên người dùng cùng lúc, không giới hạn số; phiên rác dọn nhờ hết hạn | Người dùng dùng nhiều thiết bị là chuyện thường |
| Quy tắc | Xác thực **không** quyết ai được vào ứng dụng nào: đăng nhập đúng thì luôn có phiên, qua bất kỳ ứng dụng đã đăng ký; vai chỉ đưa vào hợp đồng danh tính và mỗi nơi nhận tự kiểm, mặc định từ chối | Xác thực chỉ giữ tài khoản và phiên; đặt quyền vào đây thì mỗi ứng dụng mới phải sửa nó |
| Quy tắc | Đăng nhập chung: vào ứng dụng thứ hai khi phiên người dùng còn hiệu lực thì chỉ thêm phiên ứng dụng, không hỏi lại mật khẩu; đăng nhập ở thiết bị khác tạo phiên người dùng khác | Người dùng không phải đăng nhập lại ở từng ứng dụng |
| Quy tắc | Xác thực chỉ giữ phiên đang sống; phiên đã kết thúc và lịch sử thuộc Định danh | Giữ Xác thực gọn và nhanh |

## 6. Event

| Event | Khi nào | Nội dung chính |
|---|---|---|
| `SessionIssued` | Chỉ khi **mở** phiên người dùng (đăng nhập đúng mật khẩu, và MFA nếu có). Định danh dùng để đặt `lastLoginAt` của thiết bị | Mã phiên, tài khoản, thời điểm, thiết bị, địa chỉ mạng, thông tin thiết bị |
| `ClientSessionIssued` | **Mỗi lần cấp token** cho một phiên ứng dụng: lúc đổi mã lấy token (sau đăng nhập, xác thực im lặng) và lúc làm mới. Định danh dùng để đặt `lastActiveAt` của thiết bị | Mã phiên, tài khoản, ứng dụng, thời điểm, thiết bị (không mang địa chỉ mạng: lời gọi đến từ BFF) |
| `SessionEnded` | Khi kết thúc | Mã phiên, tài khoản, lý do (không mang hoạt động gần nhất) |

Không có event riêng cho hết hạn. **Chốt 2026-10-11:** `lastLoginAt` của thiết bị do `SessionIssued` ghi, `lastActiveAt` do `ClientSessionIssued` ghi (mỗi lần cấp token); `SessionEnded` không cập nhật `lastActiveAt`, nên `lastActiveAt` của thiết bị đã đăng xuất dừng ở lần cấp token cuối, không cần mang mốc qua `SessionEnded`. Truy vấn các phiên đang sống vẫn dùng để biết thiết bị nào đang có phiên.

## 7. Phạm vi bảo vệ

Phiên đăng nhập giả định thiết bị của người dùng không bị chiếm hoàn toàn. Rủi ro lớn nhất còn lại là **kẻ xấu chiếm được trình duyệt đang đăng nhập** (hoặc lấy được phiên của nó) (Chốt 2026-10-12).

**Chấp nhận:** trong hạn phiên, kẻ đó làm được mọi việc mà người dùng làm được **mà không phải xác nhận lại**. Không có biện pháp phía máy chủ chặn hoàn toàn điều này.

**Giảm thiểu bằng:**

| Lớp | Tác dụng |
|---|---|
| Hạn phiên cố định, không kéo dài | Chặn phiên bị chiếm dùng mãi |
| Hành động nhạy cảm đòi nhập lại mật khẩu (đăng xuất từ xa là hành động đầu tiên; sau này đổi email, cấu hình xác thực, tài khoản nhận tiền) | Kẻ chỉ có phiên không khóa được chủ ra, không đổi được thông tin đăng nhập |
| Đăng xuất từ xa, khóa tài khoản | Chủ tài khoản hoặc Quản trị cắt phiên ngay |
| Danh sách thiết bị, lịch sử đăng nhập, cảnh báo thiết bị lạ, thông báo khi có đăng xuất từ xa | Chủ nhìn thấy dấu hiệu bất thường |
| Token ở phía máy chủ ứng dụng (BFF), trình duyệt chỉ giữ mã phiên mờ | Mã đăng nhập không lộ ra phía trình duyệt |

**Ngoài phạm vi:** thiết bị của người dùng bị cài phần mềm theo dõi, ghi phím, quay màn hình hoặc chiếm toàn bộ máy. Bảo vệ thiết bị đầu cuối không phải việc của Xác thực.

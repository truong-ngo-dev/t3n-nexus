# Xác thực: service (`oauth2-service`)

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-17` · **Mã BC:** `ATH`
>
> Chỉ ghi kết luận, ở mức aggregate. Mỗi mục có nhãn **Dự kiến** hoặc **Đã làm**; **Đã làm** là phần của feature 01 (Đăng ký) và 02 (Đăng nhập và phiên), còn lại là Dự kiến. Cấu trúc lớp, chữ
> ký hàm, lớp lỗi nằm ở mã. Aggregate Tài khoản đăng nhập đã chi tiết hóa theo feature 01; aggregate Phiên chi tiết hóa ở feature 02.

## 1. Vai trò

Giữ tài khoản đăng nhập (email, mật khẩu, vai, trạng thái), xác thực người dùng, giữ phiên cho mọi ứng dụng, và công bố
hợp đồng danh tính cho các BC khác đọc.

## 2. Aggregate

### AGG-ATH-01 Tài khoản đăng nhập (`UserAccount`) · Đã làm: thuộc tính, đăng ký, xác thực mật khẩu; còn lại Dự kiến

**Trách nhiệm:** giữ danh tính đăng nhập của một người: email, mật khẩu băm, một vai, trạng thái.
**Bất biến giữ:** INV-ATH-01, 02, 04, 06, 07, 09.
**Tập vai ở C0:** `USER` (Người dùng, công khai), `ADMIN` (Quản trị, nội bộ). Vai không đổi sau khi tạo. Thêm vai nội bộ khác bằng dữ liệu.

**Thuộc tính**

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi (ADR-0001) | Sinh qua cổng sinh mã của miền, truyền vào lúc tạo |
| `email` | Email | Có | Chuẩn hóa chữ thường, tối đa 254 ký tự, duy nhất (INV-ATH-01); tài khoản công khai không thuộc miền nội bộ (INV-ATH-07) | Định danh đăng nhập |
| `passwordHash` | Chuỗi băm | Có | Tự mô tả thuật toán và tham số; không bao giờ ở dạng đọc được (INV-ATH-02) | Chỉ nhận từ bộ băm |
| `role` | Vai | Có | `USER` hoặc `ADMIN`; không đổi sau khi tạo (INV-ATH-06, 09) | Đăng ký công khai luôn `USER` |
| `status` | Trạng thái | Có | `ACTIVE` ở C0; thêm `LOCKED` (C1b) và `PENDING` khi có xác minh email | Xác thực là nguồn sự thật duy nhất |
| `createdAt` | Thời điểm | Có | Đặt lúc tạo, không đổi | |
| `updatedAt` | Thời điểm | Có | Đổi khi thuộc tính khác đổi | |

**Tạm:** bảng và aggregate còn giữ `registrationMethod` (`CREDENTIAL`, `OAUTH`), `mfaEnabled` và trạng thái `PENDING` ngoài thiết kế
C0; chúng giữ cho các luồng chưa thiết kế lại (đăng nhập mạng xã hội, xác thực nhiều lớp) tiếp tục chạy và được thiết kế lại ở
feature đăng nhập và C3c (xác thực nhiều lớp). Đăng ký công khai luôn đặt `CREDENTIAL`, `false`, `ACTIVE`.

**Hành vi**

| Hành vi | Khi nào chạy | Tiền điều kiện | Hậu điều kiện | Event | Lỗi nghiệp vụ | Chặng |
|---|---|---|---|---|---|---|
| Đăng ký (`register`) | Khách vãng lai gửi email và mật khẩu | Email hợp lệ, chưa dùng, không thuộc miền nội bộ; mật khẩu hợp lệ | Có tài khoản vai `USER`, trạng thái `ACTIVE`, mật khẩu ở dạng băm | `UserAccountRegistered` | Email không hợp lệ (`EmailInvalid`); email thuộc miền nội bộ (`EmailReserved`); mật khẩu không hợp lệ (`PasswordInvalid`); email đã dùng (`EmailTaken`) | C0 |
| Xác thực mật khẩu (`verifyPassword`, nhận bộ băm qua tham số) | Đăng nhập | Tài khoản `ACTIVE` | Trả đúng hoặc sai; không phân biệt nguyên nhân sai (INV-ATH-03) | | | C0 |
| Khóa (`lock`) | Quản trị khóa | Tài khoản `ACTIVE` | Trạng thái `LOCKED`, hiệu lực ngay | `UserAccountLocked` | | C1b |
| Mở (`unlock`) | Quản trị mở | Tài khoản `LOCKED` | Trạng thái `ACTIVE` | `UserAccountUnlocked` | | C1b |
| Đổi mật khẩu (`changePassword`) | Người dùng đổi | Mật khẩu cũ đúng; mật khẩu mới hợp lệ | Mật khẩu băm mới | `PasswordChanged` | Mật khẩu cũ sai; mật khẩu mới không hợp lệ | C1b |
| Bật, tắt yếu tố xác thực | Người dùng cấu hình | Xác thực lại gần đây | Cờ bật hoặc tắt; bí mật được giữ hoặc xóa | | | C3c |

**Cổng (do miền khai báo, cài đặt ở hạ tầng):** kho tài khoản (lưu; kiểm email đã dùng); bộ băm mật khẩu; cổng sinh mã
(ADR-0001); chính sách miền email nội bộ (INV-ATH-07). Đồng hồ là đầu vào của hàm tạo, không phải cổng của aggregate.

### AGG-ATH-02 Phiên đăng nhập (`LoginSession`) · Đã làm: mở phiên, cấp và làm mới phiên ứng dụng, xác nhận, kết thúc trong miền (feature 02); use case tự đăng xuất (feature 03), lý do kết thúc khác ở C1b, C3c

**Trách nhiệm:** giữ một phiên đăng nhập đang hoạt động của một tài khoản: phiên người dùng (gốc của aggregate, một lần nhập mật khẩu trên một thiết bị) cùng các phiên ứng dụng bên dưới nó (thực thể con, mỗi ứng dụng người dùng vào một phiên).
**Bất biến giữ:** INV-ATH-05, 08, 10.
**Vì sao một aggregate, không hai:** ba bất biến đều ràng buộc phiên người dùng với các phiên con của nó. Mỗi (phiên người dùng, ứng dụng) có tối đa một phiên ứng dụng (INV-ATH-10); phiên ứng dụng không sống lâu hơn phiên người dùng; kết thúc phiên người dùng là kết thúc mọi phiên con trong một thao tác ngay lập tức (INV-ATH-05, 08). Đặt chung thì ba điều này nằm trong một ranh giới giao dịch, không cần chính sách phối hợp giữa hai aggregate. Số phiên con nhỏ (tối đa bằng số ứng dụng, 4), nên nạp cả aggregate không tốn. Tranh chấp duy nhất là hai ứng dụng xin phiên cùng lúc trên một phiên người dùng, hiếm và được chặn bằng ràng buộc duy nhất ở cơ sở dữ liệu. Điều kiện xem lại và lập luận đầy đủ: `analysis/login-session.md` mục 1.
**Vòng đời:** chỉ tồn tại khi đang hoạt động; lý do kết thúc đi trong event `SessionEnded`; hết hạn suy ra từ hạn phiên, không lưu trạng thái và không phát event.

**Thuộc tính của phiên người dùng** (mức khái niệm; chi tiết hóa ở feature 02)

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi (ADR-0001) | Mã phiên người dùng |
| `userAccountId` | Mã tài khoản | Có | Tài khoản tồn tại | Chủ phiên |
| `deviceKey` | Bản băm mã thiết bị | Không | | Mã thiết bị là cookie do Xác thực cấp trước khi nhập mật khẩu; chỉ lưu bản băm; mã thô không rời Xác thực (cả event cũng chỉ mang bản băm) |
| `idpSessionId` | Mã cookie đăng nhập của Spring | Có | Cập nhật khi mã session Spring đổi (chống chiếm phiên); **không đi trong token, event, log** | Liên kết kỹ thuật tới session Spring (ADR-0003 mục 1, 4); là giá trị bí mật |
| `issuedAt` | Thời điểm | Có | | Lúc nhập mật khẩu |
| `expiresAt` | Thời điểm | Có | Sau `issuedAt`; đặt lúc mở, không bao giờ bị kéo dài | Hạn phiên duy nhất; TTL theo vai của tài khoản (`analysis/login-session.md` mục 3.4) |

Địa chỉ mạng và thông tin thiết bị **không lưu** trong phiên: chỉ đi trong event `SessionIssued`.

**Thuộc tính của phiên ứng dụng** (thực thể con)

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi | Mã phiên ứng dụng, đi trong hợp đồng danh tính (`sid`) |
| `clientId` | Mã client | Có | Client đã đăng ký của ứng dụng; mỗi phiên người dùng chỉ có một phiên ứng dụng cho mỗi client (INV-ATH-10) | Nơi giữ mã đăng nhập của phiên này (BFF), để báo xóa khi kết thúc |
| `authorizationId` | Liên kết token | Có | Trỏ tới bản ghi cấp token | **Đổi được**: khi ứng dụng xin cấp lại token thì phiên giữ nguyên mã, đổi liên kết sang bản ghi mới, bản cũ bị thay trong cùng giao dịch |
| `issuedAt` | Thời điểm | Có | Không trước `issuedAt` của phiên người dùng | |

Phiên người dùng và phiên ứng dụng không giữ mốc hoạt động; "hoạt động gần đây" của thiết bị do Định danh giữ (nhận `ClientSessionIssued`). Phiên ứng dụng không có hạn riêng.

**Hành vi**

| Hành vi | Khi nào chạy | Tiền điều kiện | Hậu điều kiện | Event | Lỗi nghiệp vụ | Chặng |
|---|---|---|---|---|---|---|
| Mở phiên (`open`) | Đăng nhập bằng mật khẩu thành công | Tài khoản `ACTIVE` | Có phiên người dùng (hạn phiên đặt theo TTL của vai); chưa có phiên ứng dụng vì chưa có bản ghi ủy quyền | `SessionIssued` | | C0 |
| Cấp phiên ứng dụng (`issueClientSession`) | Đổi mã lấy token: sau đăng nhập bằng mật khẩu, hoặc xác thực im lặng bằng phiên người dùng còn hiệu lực. Chỉ gọi lúc token đã được cấp (điểm móc, cùng giao dịch với bản ghi ủy quyền) | Phiên người dùng còn hiệu lực; tài khoản `ACTIVE` | Theo `clientId`: chưa có phiên ứng dụng cho client đó thì tạo; đã có thì dùng lại, giữ mã (`sid`) và đổi liên kết sang bản ghi ủy quyền mới, trả `sid` thực tế (để use case đối chiếu với `sid` đã nằm trong token) và mã bản ghi cũ để xóa cùng giao dịch (INV-ATH-10); token cấp có hạn đã cắt theo hạn phiên | `ClientSessionIssued` (mã event lấy từ `jti`) | Phiên hết hạn (`SessionExpired`) | C0 |
| Làm mới phiên ứng dụng (`refreshClientSession`) | Làm mới token: BFF gửi refresh token kèm `sid`. Chỉ gọi lúc token mới đã được cấp | Phiên người dùng và phiên ứng dụng (`sid`) còn hiệu lực | Theo `sid`: không tạo, không đổi liên kết (Spring cập nhật cùng bản ghi tại chỗ); refresh token mới có hạn bằng nhỏ hơn của TTL refresh token và phần hạn phiên còn lại | `ClientSessionIssued` (mã event lấy từ `jti`) | Phiên hết hạn (`SessionExpired`); phiên ứng dụng không thuộc phiên người dùng (`ClientSessionMismatch`, ví dụ `sid` không khớp) | C0 |
| Xác nhận (`confirm`) | Có bên hỏi phiên còn hiệu lực không | Không có | Trả phiên đang hoạt động hoặc không hợp lệ; hết hạn suy ra từ hạn tại lúc hỏi (INV-ATH-08) | | | C0 |
| Kết thúc (`end`) | Tự đăng xuất; bị đăng xuất từ xa; tài khoản bị khóa; đổi mật khẩu | Không có | Phiên người dùng và mọi phiên ứng dụng không còn hiệu lực cùng lúc; token vô hiệu; thành công cả khi đã kết thúc (INV-ATH-05) | `SessionEnded` kèm lý do (không mang mốc hoạt động) | | C0 (tự đăng xuất); C1b (tài khoản bị khóa, đổi mật khẩu); C3c (bị đăng xuất từ xa) |

**Cổng (do miền khai báo, cài đặt ở hạ tầng):** kho phiên (lưu; tìm theo mã phiên người dùng; tìm theo `sid` (phiên ứng dụng), dùng khi làm mới; tìm các phiên đang sống theo tài khoản và theo thiết bị, lọc theo hạn; tìm phiên quá hạn theo lô, dùng để dọn; xóa); cổng sinh mã (ADR-0001); cổng sinh mã thiết bị (chuỗi ngẫu nhiên 256 bit, khác mã định danh thực thể) và bộ băm mã thiết bị (SHA-256). Đồng hồ là tham số của từng hành vi. Báo các BFF xóa mã khi kết thúc là phối hợp ở use case, không thuộc aggregate.

**Hợp đồng danh tính** (`IdentityClaims`) không phải thuộc tính của aggregate: là giá trị miền gồm mã tài khoản, vai, mã phiên ứng dụng và hạn, dựng từ phiên và tài khoản lúc cấp mã (mục 6); hạ tầng chỉ ký và mã hóa nó thành mã đăng nhập.

### Dữ liệu khởi tạo (không phải aggregate) · Đã làm

Tài khoản Quản trị có sẵn: một bản ghi tài khoản vai `ADMIN`, mật khẩu băm, email miền nội bộ, tạo bằng script SQL `infra/seed/admin.sql`
(feature 04). Thỏa INV-ATH-01, 02, 06, 07, và là đường duy nhất tạo tài khoản nội bộ ở C0 (INV-ATH-11).

Cách chạy: người vận hành chạy tay một lần khi dựng môi trường, **ngoài Flyway và không có bộ nạp trong mã** (mật khẩu băm không vào repo hay migration), truyền email, mật khẩu đã băm bcrypt và miền nội bộ làm tham số. Script kiểm đầu vào trước khi ghi (email thuộc miền nội bộ, đúng dạng bcrypt) và dừng nếu sai; ghi bằng một câu `INSERT ... ON CONFLICT (email) DO NOTHING` nên chạy lại không thêm dòng và không ghi đè mật khẩu; mã tài khoản là UUID v7 dựng bằng SQL. Hướng dẫn và lý do: `infra/seed/README.md`, `stage/c0-foundation/04-seed-admin/tech.md`.

## 3. Nghiệp vụ liên aggregate

Nghiệp vụ liên quan nhiều aggregate hoặc một aggregate với cấu hình bên ngoài, không thuộc riêng aggregate nào. Cột "Cơ chế":
**chính sách** (kiểm điều kiện do miền khai báo), **dịch vụ miền** (thao tác trên nhiều aggregate), **phản ứng với event**
(aggregate này phản ứng event của aggregate kia trong cùng BC). Đăng nhập là điều phối của use case (mục 4), không nằm ở
đây.

| Nghiệp vụ | Tham gia | Cơ chế | Nhất quán | Bất biến | Chặng | Nhãn |
|---|---|---|---|---|---|---|
| Email của đăng ký công khai chưa dùng | Tài khoản (tập hợp) | Dịch vụ miền `UserAccountService`, qua kho; báo sớm, chốt cuối là ràng buộc duy nhất ở cơ sở dữ liệu | Cùng giao dịch | INV-ATH-01 | C0 | Đã làm |
| Email của đăng ký công khai không thuộc miền nội bộ | Tài khoản, cấu hình miền nội bộ | Chính sách do `UserAccountService` dùng; cấu hình đưa vào, miền không đọc trực tiếp | Cùng giao dịch | INV-ATH-07 | C0 | Đã làm |
| Khóa tài khoản kết thúc mọi phiên của nó (lý do "tài khoản bị khóa") | Tài khoản, Phiên đăng nhập | Phản ứng với `UserAccountLocked` | Cuối cùng; khóa hiệu lực ngay vì đăng nhập đọc trạng thái tài khoản | | C1b | Dự kiến |
| Đổi mật khẩu kết thúc mọi phiên trừ phiên đang đổi (lý do "đổi mật khẩu") | Tài khoản, Phiên đăng nhập | Phản ứng với `PasswordChanged` | Cuối cùng | | C1b | Dự kiến |
| Đăng xuất từ xa: một thiết bị, hoặc mọi thiết bị khác (trừ phiên người dùng của người gọi), lý do "bị đăng xuất từ xa" | Phiên đăng nhập (tập hợp) | Dịch vụ miền trên tập phiên | Cùng giao dịch cho từng phiên bị chọn | INV-ATH-05 | C3c | Dự kiến |

## 4. Use case

| Use case | Năng lực | Chặng | Nhãn |
|---|---|---|---|
| Đăng ký | CAP-ATH-01 | C0 | Đã làm |
| Đăng nhập (kiểm giới hạn số lần thử, kiểm mật khẩu, mở phiên người dùng; có phiên người dùng còn hiệu lực thì chỉ cấp phiên ứng dụng, không hỏi mật khẩu) | CAP-ATH-02, 03 | C0 | Đã làm |
| Cấp lại token: đổi mã lấy token (cấp phiên ứng dụng, kể cả xác thực im lặng) và làm mới (làm mới phiên ứng dụng); cắt hạn token theo hạn phiên | CAP-ATH-03 | C0 | Đã làm |
| Xác nhận phiên khi được hỏi | CAP-ATH-03 | C0 | Đã làm |
| Cấp mã thiết bị (trang đăng nhập cấp cookie `DEVICE_KEY` nếu chưa có, trước khi nhập mật khẩu) | CAP-ATH-02 | C0 | Đã làm |
| Ghi nhận đăng nhập thất bại (chỉ khi tìm ra tài khoản; phát `LoginFailed`) | CAP-ATH-02 | C0 | Đã làm |
| Dọn phiên quá hạn và bản ghi ủy quyền mồ côi theo lô (viết ở C0; chạy định kỳ qua Lập lịch ở C2b) | CAP-ATH-03 | C0 viết, C2b chạy | Dự kiến |
| Đăng xuất (kết thúc phiên người dùng của lần đăng nhập này tìm theo cookie đăng nhập, xóa bản ghi ủy quyền cùng một giao dịch, báo từng BFF xóa mã sau khi giao dịch xong; luôn thành công) | CAP-ATH-04 | C0 | Đã làm |
| Đăng xuất từ xa (một thiết bị, mọi thiết bị khác): kiểm mật khẩu, thêm mã một lần qua email khi thiết bị người gọi chưa xác minh; báo chủ tài khoản bằng email | | C3c | Dự kiến |
| Xin và kiểm mã một lần cho hành động nhạy cảm (khuôn dùng chung, `identity-service/analysis/device.md` mục 3) | | C3c | Dự kiến |
| Khóa, mở tài khoản | | C1b | Dự kiến |
| Bật, tắt xác thực nhiều lớp | | C3c | Dự kiến |
| Tạo tài khoản nội bộ; đổi mật khẩu | | C1b | Dự kiến |

## 5. Event

### Phát

| Event | Khi nào | Nội dung | Bên nhận | Chặng ghi · chặng nhận | Nhãn |
|---|---|---|---|---|---|
| `UserAccountRegistered` | Sau khi tạo tài khoản | Mã event, mã tài khoản, email (chuẩn hóa), vai, thời điểm; không mang tên | Định danh, Khách hàng | C0 · C1b / C2a | Đã làm (chỉ ghi nhận trong miền; chưa lưu, chưa gửi) |
| `SessionIssued` | Chỉ khi mở phiên người dùng (không phát khi cấp phiên ứng dụng hay làm mới) | Mã event, mã phiên người dùng, mã tài khoản, thời điểm, bản băm mã thiết bị, địa chỉ mạng, thông tin thiết bị | Định danh | C0 · C3c | Đã làm (chỉ ghi nhận trong miền; chưa lưu, chưa gửi) |
| `ClientSessionIssued` (tên chờ chốt) | Mỗi lần cấp token cho một phiên ứng dụng: đổi mã lấy token (sau đăng nhập, xác thực im lặng) và làm mới; phát ở điểm móc lúc token đã được cấp | Mã event, mã phiên người dùng, mã phiên ứng dụng, mã tài khoản, ứng dụng, thời điểm, bản băm mã thiết bị (không có địa chỉ mạng: lời gọi làm mới đến từ BFF) | Định danh (cập nhật hoạt động của thiết bị) | C0 · C3c | Đã làm (chỉ ghi nhận trong miền; chưa lưu, chưa gửi) |
| `SessionEnded` | Phiên người dùng kết thúc, kéo theo mọi phiên ứng dụng của nó; lý do: tự đăng xuất, bị đăng xuất từ xa, tài khoản bị khóa, đổi mật khẩu. Hết hạn không phát event | Mã event, mã phiên người dùng, mã tài khoản, lý do, thời điểm (không mang mốc hoạt động) | Định danh; Thông báo (khi lý do là bị đăng xuất từ xa, để báo chủ tài khoản) | C0 · C3c | Đã làm (chỉ ghi nhận trong miền; chưa lưu, chưa gửi) |
| `LoginFailed` | Chỉ khi tìm ra tài khoản | Mã event, mã tài khoản, lý do, địa chỉ mạng, thông tin thiết bị, bản băm mã thiết bị, thời điểm | Định danh | C0 · C3c | Đã làm (chỉ ghi nhận trong miền; chưa lưu, chưa gửi) |
| Yêu cầu gửi mã một lần | Khi cần xác nhận hành động nhạy cảm bằng email | Mã tài khoản, email, mã, mục đích | Thông báo | C3c | Dự kiến |
| `UserAccountLocked`, `UserAccountUnlocked` | Quản trị khóa hoặc mở | Mã event, mã tài khoản, người thao tác, thời điểm | Định danh, Nhật ký kiểm toán, và Phiên trong cùng BC (mục 3) | C1b | Dự kiến |
| `PasswordChanged` | Người dùng đổi mật khẩu | Mã event, mã tài khoản, thời điểm | Phiên trong cùng BC (mục 3) | C1b | Dự kiến |

Ở C0 các event này chỉ được aggregate ghi nhận trong miền, chưa lưu hay gửi đi đâu (chưa có bên nhận). Hộp thư event làm ở chặng
có bus sự kiện (roadmap C1a).

### Nhận

C0 không nhận event của BC khác. Các phản ứng với event trong cùng BC nằm ở mục 3.

## 6. Giao diện giữa BC

| Giao diện | Hướng | Nội dung | Chặng | Nhãn |
|---|---|---|---|---|
| Hợp đồng danh tính | Xác thực công bố, BC khác đọc từ mã đăng nhập | Mã tài khoản (`sub`), vai (`role`), mã phiên của phiên ứng dụng (`sid`), hạn (`exp`). Không có tên, hồ sơ, trạng thái bán. Đúng tại lúc cấp; BC khác tham chiếu chủ sở hữu bằng mã tài khoản | C0 | Đã làm (phía Xác thực) |
| Làm mới token | BFF gọi Xác thực | Refresh token kèm `sid` (mã phiên ứng dụng); Xác thực tra theo khóa chính; hết hạn phiên hoặc `sid` không khớp thì `invalid_grant` (ADR-0003 mục 8) | C0 | Đã làm (phía Xác thực; BFF gửi `sid` khi làm mới ở feature 05, 06) |
| Báo BFF xóa mã | Xác thực gọi BFF | Khi phiên kết thúc: một lời gọi nội bộ cho mỗi phiên ứng dụng của BFF, thân `{"sid": ...}`, sau khi giao dịch xong; không với tới được thì hạn token là lưới an toàn | C0 | Đã làm (phía Xác thực; bên nhận ở feature 06) |
| Đăng xuất từ xa | Người dùng gọi Xác thực (qua BFF) | Một thiết bị, hoặc mọi thiết bị khác; xác nhận bằng mật khẩu (thêm mã một lần email khi thiết bị người gọi chưa xác minh); thành công cả khi phiên đã kết thúc | C3c | Dự kiến |
| Thiết bị đã xác minh chưa | Xác thực hỏi Định danh (đọc) | Cho thiết bị của người gọi; lỗi hoặc quá hạn chờ thì coi là chưa xác minh | C3c | Dự kiến |
| Các phiên đang sống của tài khoản | Định danh hỏi Xác thực (đọc) | Theo tài khoản, để hiển thị "đang đăng nhập" theo thiết bị | C3c | Dự kiến |
| Kiểm quyền theo vai | BC khác dùng chung | Hàm đọc vai từ hợp đồng danh tính | C1b | Dự kiến |

## 7. Khái niệm kỹ thuật

| Khái niệm | Kết luận | ADR |
|---|---|---|
| Mã định danh thực thể | UUID v7 sinh trong miền qua cổng sinh mã, lưu kiểu `uuid` | ADR-0001 |
| Băm mật khẩu | bcrypt hệ số 10, chuỗi băm tự mô tả; mật khẩu tối đa 64 ký tự ASCII; số lần đăng nhập/giây tối đa đo bằng bộ đo nền | ADR-0002 |
| Mã đăng nhập và phiên hai tầng | Mã tự mô tả, các BC tự kiểm chữ ký; phiên người dùng ở nơi xác thực, phiên ứng dụng bên dưới trong cùng aggregate; một hạn phiên duy nhất, token cắt theo hạn phiên; refresh token xoay vòng, không phát hiện dùng lại | ADR-0003 |
| Mã định danh trong token | Một claim `sid` là mã phiên ứng dụng của ta (UUID v7), khác `sid` mặc định của Spring (băm mã session, không còn được sinh); mã phiên người dùng tra từ `sid`; mã cookie của Spring (`SESSION_ID`, lưu ở `idp_session_id`) chỉ nằm ở cookie và cơ sở dữ liệu của Xác thực, không vào token, event, log; mã `authorization_id` của Spring chỉ nằm ở cơ sở dữ liệu | ADR-0003 |
| Làm mới token | BFF gửi thêm `sid`; Xác thực tra phiên ứng dụng theo khóa chính, lấy `authorization_id`, đọc bản ghi ủy quyền theo khóa chính rồi so refresh token; không chỉ mục trên giá trị token; bước đổi mã ủy quyền cần cách riêng | ADR-0003 |
| Đăng xuất | Theo OIDC RP-Initiated Logout: BFF xóa phiên của nó rồi trình duyệt tới `/connect/logout` kèm `id_token_hint`; Xác thực tra bản ghi theo `sid` trong hint (kiểm chữ ký, không kiểm hạn), kết thúc phiên tìm theo cookie và xóa bản ghi ủy quyền trong một giao dịch có khóa hàng phiên (không hồi sinh khi đua với cấp token), rồi báo BFF theo `sid`; hint sai hoặc đã dùng thì về trang chủ và không động tới session nào. Sổ phiên của Spring để rỗng nên Spring không sinh `sid` (chọn nhầm khi nhiều thiết bị) và không kiểm `sid` | ADR-0003 |
| Mã một lần | Mã MFA ở session; mã xác minh thiết bị và mã đăng xuất từ xa ở Redis (khóa theo mục đích và tài khoản, băm HMAC-SHA256, kiểm nguyên tử, hạn 5 phút); Redis lỗi thì từ chối | ADR-0004 |
| Chống giả mạo yêu cầu | Bật CSRF cho chuỗi dùng cookie ở máy chủ xác thực (miễn `/oauth2/token`) và ở BFF (cookie `XSRF-TOKEN` và header); cookie phiên `HttpOnly`, `Secure`, `SameSite=Lax`; bắt buộc PKCE và `state` | ADR-0005 |
| Đăng nhập chung giữa ứng dụng | Một máy chủ xác thực cho mọi ứng dụng; trang đăng nhập tùy biến theo ứng dụng | Chờ |
| Mã thiết bị | Cookie `DEVICE_KEY` ngẫu nhiên 256 bit do Xác thực cấp khi trình duyệt vào trang đăng nhập, trước khi nhập mật khẩu; `HttpOnly`, `Secure`, `SameSite=Lax`, hạn 12 tháng gia hạn mỗi lần đăng nhập; chỉ lưu bản băm; ứng dụng di động tự sinh khi cài | ADR-0005 |
| Đăng xuất từ xa | Xóa phiên người dùng, phiên ứng dụng và mã làm mới, báo từng BFF xóa mã của nó; mã truy cập đã lộ ngoài luồng hết theo hạn ngắn; khóa khẩn: danh sách chặn nếu cần (C1b) | ADR-0003 |
| Event | Ghi nhận trong miền; hộp thư và bus làm ở chặng có bên nhận | Chờ |
| Giới hạn tần suất | Đăng ký giới hạn theo địa chỉ mạng, do cổng truy cập thực hiện (feature 06); đăng nhập đếm mọi lần thử theo (email, địa chỉ mạng) và theo email, trong bước đăng nhập, Redis lỗi thì mở; mô hình ở `docs/global/3.technical/rate-limiting-layers.md` | Chờ |

## 8. Hạ tầng cần ở C0

Cơ sở dữ liệu quan hệ và cổng truy cập. Chưa cần bus sự kiện, chưa có hộp thư event.

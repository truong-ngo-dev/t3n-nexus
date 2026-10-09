# Xác thực: service (`oauth2-service`)

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-09` · **Mã BC:** `ATH`
>
> Chỉ ghi kết luận, ở mức aggregate. Mỗi mục có nhãn **Dự kiến** hoặc **Đã làm**; **Đã làm** là phần của feature 01 (Đăng ký), còn lại là Dự kiến. Cấu trúc lớp, chữ
> ký hàm, lớp lỗi nằm ở mã. Aggregate Tài khoản đăng nhập đã chi tiết hóa theo feature 01; aggregate Phiên chi tiết hóa ở feature 02.

## 1. Vai trò

Giữ tài khoản đăng nhập (email, mật khẩu, vai, trạng thái), xác thực người dùng, giữ phiên cho mọi portal, và công bố
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
feature đăng nhập và C1b. Đăng ký công khai luôn đặt `CREDENTIAL`, `false`, `ACTIVE`.

**Hành vi**

| Hành vi | Khi nào chạy | Tiền điều kiện | Hậu điều kiện | Event | Lỗi nghiệp vụ | Chặng |
|---|---|---|---|---|---|---|
| Đăng ký (`register`) | Khách vãng lai gửi email và mật khẩu | Email hợp lệ, chưa dùng, không thuộc miền nội bộ; mật khẩu hợp lệ | Có tài khoản vai `USER`, trạng thái `ACTIVE`, mật khẩu ở dạng băm | `UserAccountRegistered` | Email không hợp lệ (`EmailInvalid`); email thuộc miền nội bộ (`EmailReserved`); mật khẩu không hợp lệ (`PasswordInvalid`); email đã dùng (`EmailTaken`) | C0 |
| Xác thực mật khẩu (`verifyPassword`, nhận bộ băm qua tham số) | Đăng nhập | Tài khoản `ACTIVE` | Trả đúng hoặc sai; không phân biệt nguyên nhân sai (INV-ATH-03) | | | C0 |
| Khóa (`lock`) | Quản trị khóa | Tài khoản `ACTIVE` | Trạng thái `LOCKED`, hiệu lực ngay | `UserAccountLocked` | | C1b |
| Mở (`unlock`) | Quản trị mở | Tài khoản `LOCKED` | Trạng thái `ACTIVE` | `UserAccountUnlocked` | | C1b |
| Đổi mật khẩu (`changePassword`) | Người dùng đổi | Mật khẩu cũ đúng; mật khẩu mới hợp lệ | Mật khẩu băm mới | `PasswordChanged` | Mật khẩu cũ sai; mật khẩu mới không hợp lệ | C1b |
| Bật, tắt yếu tố xác thực | Người dùng cấu hình | Xác thực lại gần đây | Cờ bật hoặc tắt; bí mật được giữ hoặc xóa | | | C1b |

**Cổng (do miền khai báo, cài đặt ở hạ tầng):** kho tài khoản (lưu; kiểm email đã dùng); bộ băm mật khẩu; cổng sinh mã
(ADR-0001); chính sách miền email nội bộ (INV-ATH-07). Đồng hồ là đầu vào của hàm tạo, không phải cổng của aggregate.

### AGG-ATH-02 Phiên (`Session`) · Dự kiến

**Trách nhiệm:** giữ một lần đăng nhập đang hoạt động của một tài khoản trên một portal, thuộc một phiên gốc ở nơi xác thực.
**Bất biến giữ:** INV-ATH-05, 08, 10.
**Vòng đời:** chỉ tồn tại khi đang hoạt động; lý do kết thúc đi trong event `SessionEnded`; hết hạn suy ra từ hạn, không lưu
trạng thái và không phát event.

**Thuộc tính** (mức khái niệm; chi tiết hóa ở feature 02)

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi | |
| `userAccountId` | Mã tài khoản | Có | Tài khoản tồn tại | Chủ phiên |
| `rootSessionId` | Mã định danh | Có | Cùng một giá trị cho các phiên portal của một lần đăng nhập | Phiên gốc ở nơi xác thực |
| `portal` | Mã portal | Có | Một trong các portal đã đăng ký; (`rootSessionId`, `portal`) chỉ có một phiên hoạt động (INV-ATH-10) | |
| `issuedAt`, `expiresAt` | Thời điểm | Có | `expiresAt` sau `issuedAt`; hết hạn thì không còn hiệu lực (INV-ATH-08) | |
| `ipAddress` | Địa chỉ mạng | Có | | Lúc cấp |
| `deviceInfo` | Thông tin thiết bị | Không | | Lúc cấp, để phát event cho Định danh |
| `deviceKey` | Mã thiết bị | Không | | Để thu hồi theo thiết bị (C1b) |

**Hành vi**

| Hành vi | Khi nào chạy | Tiền điều kiện | Hậu điều kiện | Event | Lỗi nghiệp vụ | Chặng |
|---|---|---|---|---|---|---|
| Cấp phiên (`issue`) | Đăng nhập thành công | Tài khoản `ACTIVE`; chưa có phiên hoạt động cho (`rootSessionId`, `portal`) | Có phiên hoạt động với hạn | `SessionIssued` | | C0 |
| Kết thúc phiên (`end`) | Đăng xuất (C0); thu hồi, khóa (C1b) | Không có | Phiên không còn hiệu lực; thành công cả khi phiên đã kết thúc (INV-ATH-05) | `SessionEnded` kèm lý do | | C0 |

### Dữ liệu khởi tạo (không phải aggregate) · Dự kiến

Tài khoản Quản trị có sẵn: một bản ghi tài khoản vai `ADMIN`, mật khẩu băm, email miền nội bộ, tạo bằng script khởi tạo.
Thỏa INV-ATH-01, 02, 06, 07.

## 3. Nghiệp vụ liên aggregate

Nghiệp vụ liên quan nhiều aggregate hoặc một aggregate với cấu hình bên ngoài, không thuộc riêng aggregate nào. Cột "Cơ chế":
**chính sách** (kiểm điều kiện do miền khai báo), **dịch vụ miền** (thao tác trên nhiều aggregate), **phản ứng với event**
(aggregate này phản ứng event của aggregate kia trong cùng BC). Đăng nhập là điều phối của use case (mục 4), không nằm ở
đây.

| Nghiệp vụ | Tham gia | Cơ chế | Nhất quán | Bất biến | Chặng | Nhãn |
|---|---|---|---|---|---|---|
| Email của đăng ký công khai chưa dùng | Tài khoản (tập hợp) | Dịch vụ miền `UserAccountService`, qua kho; báo sớm, chốt cuối là ràng buộc duy nhất ở cơ sở dữ liệu | Cùng giao dịch | INV-ATH-01 | C0 | Đã làm |
| Email của đăng ký công khai không thuộc miền nội bộ | Tài khoản, cấu hình miền nội bộ | Chính sách do `UserAccountService` dùng; cấu hình đưa vào, miền không đọc trực tiếp | Cùng giao dịch | INV-ATH-07 | C0 | Đã làm |
| Khóa tài khoản kết thúc mọi phiên của nó | Tài khoản, Phiên | Phản ứng với `UserAccountLocked` | Cuối cùng; khóa hiệu lực ngay vì đăng nhập đọc trạng thái tài khoản | | C1b | Dự kiến |
| Đổi mật khẩu kết thúc mọi phiên khác | Tài khoản, Phiên | Phản ứng với `PasswordChanged` | Cuối cùng | | C1b | Dự kiến |
| Thu hồi theo thiết bị, hoặc tất cả trừ phiên hiện tại | Phiên (tập hợp) | Dịch vụ miền trên tập phiên | Cùng giao dịch | INV-ATH-05 | C1b | Dự kiến |

## 4. Use case

| Use case | Năng lực | Chặng | Nhãn |
|---|---|---|---|
| Đăng ký | CAP-ATH-01 | C0 | Đã làm |
| Đăng nhập (kèm cấp phiên) | CAP-ATH-02, 03 | C0 | Dự kiến |
| Xác nhận phiên khi được hỏi | CAP-ATH-03 | C0 | Dự kiến |
| Đăng xuất | CAP-ATH-04 | C0 | Dự kiến |
| Thu hồi phiên (theo phiên, theo thiết bị, tất cả trừ hiện tại) | | C1b | Dự kiến |
| Khóa, mở tài khoản | | C1b | Dự kiến |
| Bật, tắt xác thực nhiều lớp | | C1b | Dự kiến |
| Tạo tài khoản nội bộ; đổi mật khẩu | | C1b | Dự kiến |

## 5. Event

### Phát

| Event | Khi nào | Nội dung | Bên nhận | Chặng ghi · chặng nhận | Nhãn |
|---|---|---|---|---|---|
| `UserAccountRegistered` | Sau khi tạo tài khoản | Mã event, mã tài khoản, email (chuẩn hóa), vai, thời điểm; không mang tên | Định danh, Khách hàng | C0 · C1b / C2a | Đã làm (chỉ ghi nhận trong miền; chưa lưu, chưa gửi) |
| `SessionIssued` | Sau khi cấp phiên | Mã event, mã phiên, mã tài khoản, phiên gốc, portal, hạn, địa chỉ mạng, thông tin thiết bị, mã thiết bị, thời điểm | Định danh | C0 · C1b | Dự kiến |
| `SessionEnded` | Đăng xuất, thu hồi, khóa. Hết hạn không phát event: Định danh suy ra từ hạn kèm trong `SessionIssued` | Mã event, mã phiên, mã tài khoản, lý do, thời điểm | Định danh | C0 · C1b | Dự kiến |
| `LoginFailed` | Chỉ khi tìm ra tài khoản | Mã event, mã tài khoản, lý do, địa chỉ mạng, thông tin thiết bị, thời điểm | Định danh | C0 · C1b | Dự kiến |
| `UserAccountLocked`, `UserAccountUnlocked` | Quản trị khóa hoặc mở | Mã event, mã tài khoản, người thao tác, thời điểm | Định danh, Nhật ký kiểm toán, và Phiên trong cùng BC (mục 3) | C1b | Dự kiến |
| `PasswordChanged` | Người dùng đổi mật khẩu | Mã event, mã tài khoản, thời điểm | Phiên trong cùng BC (mục 3) | C1b | Dự kiến |

Ở C0 các event này chỉ được aggregate ghi nhận trong miền, chưa lưu hay gửi đi đâu (chưa có bên nhận). Hộp thư event làm ở chặng
có bus sự kiện (roadmap C1a).

### Nhận

C0 không nhận event của BC khác. Các phản ứng với event trong cùng BC nằm ở mục 3.

## 6. Giao diện giữa BC

| Giao diện | Hướng | Nội dung | Chặng | Nhãn |
|---|---|---|---|---|
| Hợp đồng danh tính | Xác thực công bố, BC khác đọc từ mã đăng nhập | Mã tài khoản, vai, mã phiên, hạn. Không có tên, hồ sơ, trạng thái bán. Đúng tại lúc cấp; BC khác tham chiếu chủ sở hữu bằng mã tài khoản | C0 | Dự kiến |
| Thu hồi phiên, bỏ tin cậy thiết bị | Định danh gọi Xác thực | Mã phiên, hoặc mã thiết bị, hoặc "tất cả trừ hiện tại"; thành công cả khi phiên đã kết thúc | C1b | Dự kiến |
| Kiểm quyền theo vai | BC khác dùng chung | Hàm đọc vai từ hợp đồng danh tính | C1b | Dự kiến |

## 7. Khái niệm kỹ thuật

| Khái niệm | Kết luận | ADR |
|---|---|---|
| Mã định danh thực thể | UUID v7 sinh trong miền qua cổng sinh mã, lưu kiểu `uuid` | ADR-0001 |
| Băm mật khẩu | bcrypt hệ số 10, chuỗi băm tự mô tả; mật khẩu tối đa 64 ký tự ASCII; số lần đăng nhập/giây tối đa đo bằng bộ đo nền | ADR-0002 |
| Mã đăng nhập và phiên hai tầng | Mã tự mô tả, các BC tự kiểm chữ ký; phiên gốc ở nơi xác thực, phiên portal bên dưới | Chờ |
| Đăng nhập chung giữa portal | Một máy chủ xác thực cho mọi portal; trang đăng nhập tùy biến theo portal | Chờ |
| Đăng xuất từ xa | Web: hủy phiên ở cổng ngay; di động và mã đã cấp: hết theo hạn; khóa: danh sách chặn | Chờ |
| Event | Ghi nhận trong miền; hộp thư và bus làm ở chặng có bên nhận | Chờ |
| Giới hạn tần suất | Đăng ký giới hạn theo địa chỉ mạng, do cổng truy cập thực hiện (feature 06); mô hình ở `docs/global/3.technical/rate-limiting-layers.md` | Chờ |

## 8. Hạ tầng cần ở C0

Cơ sở dữ liệu quan hệ và cổng truy cập. Chưa cần bus sự kiện, chưa có hộp thư event.

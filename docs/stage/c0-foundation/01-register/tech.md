# Feature 01: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Mục gắn **[nâng lên ADR]** đúng cho cả BC hoặc mọi BC, nên khi chốt
> sẽ chuyển thành ADR và file này chỉ trỏ tới.

## T1. Băm mật khẩu

- **Quyết định:** bcrypt hệ số 10; luật mật khẩu (tối đa 64 ký tự ASCII) nằm dưới giới hạn 72 byte của bcrypt. Đã thành **ADR-0002**; feature này chỉ áp dụng.
- **Áp dụng cho feature:** `RawPassword` chỉ nhận `A-Za-z0-9@$`, 8 đến 64 ký tự; `PasswordHasherAdapter` băm bằng bcrypt hệ số 10;
  giá trị lưu là chuỗi bcrypt chuẩn (tự mô tả), cột `password_hash`.

## T2. Chuẩn hóa email

- **Quyết định:** cắt khoảng trắng, chuyển chữ thường không phụ thuộc ngôn ngữ, tối đa 254 ký tự; lưu **dạng chuẩn hóa**
  vào cột duy nhất. Kiểm định dạng đơn giản (một `@`, miền có dấu chấm). Giá trị gốc người dùng gõ không lưu.
- **Loại:** chỉ mục không phân biệt hoa thường trên giá trị gốc (cần nhớ dùng đúng ở mọi truy vấn); gộp bí danh Gmail.
- **Lý do:** lưu giá trị đã chuẩn hóa thì ràng buộc duy nhất thường đã đủ và mọi truy vấn tra đúng một cách.

## T3. Chống trùng và đua nhau

- **Quyết định:** ràng buộc duy nhất ở cột email chuẩn hóa là chốt cuối. Kiểm trước chỉ để báo sớm. Bắt vi phạm ràng
  buộc và đổi thành 409.
- **Loại:** khóa ứng dụng hoặc khóa nhắc việc (thêm phụ thuộc, không chắc hơn ràng buộc ở CSDL).

## T4. Gửi lặp

- **Quyết định:** đăng ký không dùng khóa yêu cầu (`Idempotency-Key`). Gửi lặp (bấm hai lần, mạng chậm) không tạo hai tài khoản nhờ
  ràng buộc email duy nhất ở CSDL (INV-ATH-01, 04); yêu cầu thừa nhận 409 `EMAIL_TAKEN`. Giao diện đã khóa nút khi đang gửi
  nên bấm đúp không sinh yêu cầu thứ hai.
- **Phân biệt:** *khóa nghiệp vụ* (mã giao dịch, khóa tự nhiên của đơn) là một phần của nghiệp vụ, bắt buộc, do miền kiểm và CSDL
  chốt duy nhất. *Mã yêu cầu* chỉ là định danh của một lần gọi để client trung thực gửi lại an toàn, không chống được lạm dụng
  vì kẻ tấn công sinh mã mới cho mỗi yêu cầu. Chống lạm dụng là việc của giới hạn tần suất (T6), CAPTCHA và lớp ngoài.
- **Loại:** phát lại phản hồi đã lưu theo mã yêu cầu (lưu ở CSDL cùng giao dịch, hay ở bộ đệm). Lợi ích chỉ là trả lại đúng 201
  thay vì 409 cho lần gửi lại sau khi mạng chậm; không yêu cầu nào đòi điều đó (R4, S2, INV-ATH-04 chỉ đòi chỉ một tài khoản),
  mà cái giá là thêm cổng, bộ đệm, dấu vân tay và mã lỗi, và phụ thuộc client sinh mã đúng cách. Nguồn (Stripe, IETF, AWS)
  mô tả cơ chế này cho thao tác không có khóa tự nhiên như thanh toán, không cho thao tác đã có quy tắc nghiệp vụ tự bảo vệ.
- **Hệ quả:** người dùng gửi lại sau khi mạng chậm có thể thấy 409 dù lần đầu đã thành công; giao diện hiển thị rõ ràng và dẫn
  sang đăng nhập khi gặp `EMAIL_TAKEN`. Cơ chế khóa yêu cầu sẽ bàn lại khi làm luồng tạo đơn (roadmap C2a).

## T5. Event

- **Quyết định:** đăng ký ở C0 không ghi và không gửi event ra ngoài. Aggregate vẫn ghi nhận event `UserAccountRegistered` trong
  miền (mã, email chuẩn hóa, vai) để chặng có bên nhận dùng; việc lưu hộp thư và đẩy ra bus làm khi chặng đó cần (roadmap C1a).
- **Hệ quả:** đường đăng ký chỉ phụ thuộc cơ sở dữ liệu; không có bảng hộp thư trong phạm vi feature.

## T6. Giới hạn tần suất

- **Quyết định:** 10 yêu cầu mỗi giờ cho mỗi địa chỉ mạng. Đây là giới hạn **nhóm 1** (bảo vệ, không phải luật nghiệp vụ): một luật theo
  đường dẫn trong cấu hình của cổng truy cập, nạp từ thuộc tính lúc khởi động. Mô hình đầy đủ (hai nhóm, cấu hình, hiệu năng, khi
  lỗi) ở `../../../global/3.technical/rate-limiting-layers.md`.
- **Địa chỉ mạng:** do cổng truy cập xác định; dịch vụ không tự đoán từ `X-Forwarded-For` của client.
- **Loại:** giới hạn theo email (kẻ xấu đổi email dễ dàng); CAPTCHA (hoãn, xem `deferred.md`).
- **Vì sao 10:** tải đăng ký thật chỉ ~1 yêu cầu/giây toàn hệ thống (NFR 3.3), nên một địa chỉ dùng chung cho cả trăm
  người vẫn có kỳ vọng dưới 1 đăng ký mỗi giờ. 10 là dư khoảng 3 lần cho người dùng thử lại vài lần. Một địa chỉ tấn công
  tối đa 240 tài khoản/ngày, tốn chưa đến 20 giây CPU băm.
- **Lưu ý:** nhiều người sau cùng một địa chỉ mạng (mạng di động dùng chung) có thể bị chặn nhầm; số cấu hình được và
  ghi nhận lại sau khi đo.

## T7. Mã tài khoản

- **Quyết định:** theo ADR-0001: UUID v7 sinh trong miền qua cổng sinh mã, lưu kiểu `uuid`.
- **Áp dụng cho feature:** `UserAccountId` bọc UUID; cột `user_accounts.id` kiểu `uuid`; `userAccountId` trong API có
  `format: uuid`; event `UserAccountRegistered` mang mã đó.
- **Ai sinh mã (tính trước):** use case gọi cổng sinh mã, cổng băm và đồng hồ, rồi truyền giá trị vào hàm tạo
  `UserAccount.register(id, email, passwordHash, now)`. Aggregate không giữ cổng nào và không tự sinh mã. Cổng sinh mã
  khai báo ở thư viện dùng chung `common-domain` cạnh bộ sinh mã cũ; cài đặt ở hạ tầng.
- **Loại:** aggregate tự sinh mã qua cổng truyền vào hàm tạo (hai cổng trong chữ ký, mà mã không cần trường nào của
  aggregate); lớp Factory riêng (chỉ có hai đường tạo, mỗi đường là một hàm tĩnh đặt tên rõ: `register`,
  `createInternal`; lớp Factory đáng có khi việc tạo cần cộng tác viên hoặc phức tạp).
- **Chỗ dùng truyền cổng qua tham số:** chỉ khi hành vi cần chính trạng thái của aggregate, ví dụ
  `verifyPassword(raw, hasher)` cần `passwordHash` (quy ước `ddd-structure.md`, mục Double Dispatch).
- **Kiểm điều kiện trước khi tạo:** email chưa dùng và không thuộc miền nội bộ cần kho và chính sách, nên do Domain Service
  `UserAccountService` (`assertRegistrable`) làm; use case gọi nó trước khi tạo.

## T8. Miền email nội bộ

- **Quyết định:** danh sách miền nội bộ đọc từ cấu hình, kiểm sau chuẩn hóa, so khớp đúng đuôi miền (không so khớp chuỗi
  con). Không cố định trong mã.
- **Lý do:** cấu hình khác nhau giữa môi trường thử và thật.

## T9. Hình dạng phản hồi

- **Quyết định:** dùng khung phản hồi chung của dự án (`success`, `data`, `message`, `errors`) và thêm trường `code` cho lỗi:
  chuỗi ổn định như `EMAIL_TAKEN` để giao diện dịch hoặc rẽ nhánh; `message` tiếng Việt nêu lý do. Thành công là
  `{ success: true, data: { userAccountId } }`. Lỗi 500 chỉ có `code` `INTERNAL_ERROR` và thông điệp chung, không lộ chi tiết
  nội bộ.
- **Loại:** trả thẳng `{ userAccountId }` và `{ code, message }` riêng cho endpoint này (khác mọi endpoint còn lại của cùng
  service và buộc frontend đọc hai kiểu).
- **Hệ quả:** `code` lấy từ tên hằng của mã lỗi nghiệp vụ nên đổi tên hằng là đổi hợp đồng; thêm `code` ở thư viện dùng
  chung là thay đổi cộng thêm, các service khác không phải sửa.

## T10. Đưa lên ADR khi chốt

T1 (băm mật khẩu) và T7 (định dạng mã) đã thành ADR-0002 và ADR-0001. Không còn mục nào chờ nâng lên ADR.

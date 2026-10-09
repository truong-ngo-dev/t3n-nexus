# ADR-0002: Băm mật khẩu

> **Trạng thái:** Accepted · **Ngày:** `2026-10-08` · **Phạm vi:** mọi BC có mật khẩu (hiện là Xác thực)

## Bối cảnh

Mật khẩu chỉ được lưu dạng băm, không bao giờ ở dạng đọc được (INV-ATH-02). Đăng nhập là điểm sâu của hệ thống: thiết kế
chịu làn sóng khoảng 700 lần đăng nhập mỗi giây trước flash sale, mỗi lần đăng nhập là một lần băm (NFR 3.3). Thuật toán
phải đủ chậm để chống dò mật khẩu, nhưng chi phí CPU và bộ nhớ của nó cũng là chi phí của làn sóng đó. Số trần thật của máy
chỉ có sau khi đo (feature 08).

## Quyết định

Dùng **bcrypt, hệ số 10**, qua `BCryptPasswordEncoder` của Spring Security.

- **Định dạng tự mô tả.** Chuỗi bcrypt chuẩn (`$2a$10$...`) đã mang phiên bản thuật toán và hệ số cùng giá trị băm, nên không
  cần cột riêng. Muốn đổi thuật toán hay hệ số thì nhận ra tài khoản cũ qua tiền tố của chuỗi đã lưu.
- **Băm lại khi đăng nhập.** Đăng nhập đúng mà hệ số lưu thấp hơn mức hiện tại thì băm lại bằng mức hiện tại (việc này làm ở
  feature đăng nhập).
- **Luật mật khẩu nằm dưới giới hạn 72 byte.** bcrypt chỉ dùng 72 byte đầu và bản Spring Security đang dùng ném lỗi khi quá
  72 byte. Luật "mật khẩu hợp lệ" ở miền chỉ nhận ký tự ASCII (`A-Za-z`, `0-9`, `@`, `$`) và tối đa 64 ký tự, nên mỗi ký
  tự là 1 byte và mật khẩu hợp lệ luôn dưới giới hạn, có dư địa an toàn cho các thư viện tính khác nhau. Không băm trước,
  không cắt bớt: mọi byte của mật khẩu hợp lệ đều tham gia vào giá trị băm.
- **Cổng của miền.** Miền chỉ biết `PasswordHasher` (băm một `RawPassword`, kiểm một giá trị người dùng gõ với một
  `PasswordHash`). Thuật toán nằm sau cổng, ở hạ tầng.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Argon2id ngay từ đầu | Mạnh hơn trước phần cứng chuyên dụng nhưng tốn bộ nhớ mỗi lần băm; làn sóng đăng nhập ~700/giây thành tốn bộ nhớ đáng kể. Có thể chuyển sau nhờ định dạng tự mô tả |
| bcrypt có băm trước bằng SHA-256 | Bỏ được giới hạn 72 byte nhưng thêm một lớp không chuẩn và tiền tố riêng; không cần khi mật khẩu thực tế ngắn |
| Cắt mật khẩu ở 72 byte | Hai mật khẩu khác nhau ở phần sau 72 byte được coi là một; người dùng không biết |
| PBKDF2 | Tuân chuẩn nhưng cùng độ chậm thì yếu hơn bcrypt trước GPU, không rẻ hơn |
| Băm không tự mô tả | Khó đổi thuật toán sau mà không phá tài khoản cũ |

## Lý do

- Đơn giản, có sẵn trong Spring Security, đã được dùng rộng rãi, hiệu năng đủ cho quy mô thiết kế.
- Giới hạn ký tự và độ dài ở miền biến một lỗi ngầm của thuật toán thành luật mật khẩu rõ ràng, và khớp với thực tế người
  dùng không đặt mật khẩu quá dài.
- Định dạng tự mô tả giữ đường lui sang Argon2id mà không phải ép người dùng đặt lại mật khẩu.

## Hệ quả

- Chỉ nhận ASCII nên không có chuyện cùng một mật khẩu có nhiều dạng mã hóa Unicode (NFC, NFD) làm đăng nhập sai khi đổi
  thiết bị. Đánh đổi: mật khẩu có chữ có dấu hoặc emoji bị từ chối, thông điệp lỗi liệt kê rõ ký tự được phép.
- Hệ số 10 là điểm khởi đầu; feature 08 đo số lần đăng nhập tối đa mỗi giây trên máy thật rồi mới chỉnh.
- Khi chuyển sang Argon2id, bộ băm nhận ra định dạng cũ qua tiền tố, kiểm tài khoản cũ bằng bcrypt và băm lại bằng Argon2id
  ở lần đăng nhập đúng kế tiếp.

## Tham khảo

- bcrypt giới hạn 72 byte: <https://en.wikipedia.org/wiki/Bcrypt#Maximum_password_length>
- OWASP Password Storage Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html>

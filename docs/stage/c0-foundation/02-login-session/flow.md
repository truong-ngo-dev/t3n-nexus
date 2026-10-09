# Feature 02: Đăng nhập và phiên

> **Trạng thái:** chưa thiết kế · **Chặng:** C0 · **Loại:** Nghiệp vụ
>
> **Nguồn:** CAP-ATH-02, 03, 05 · **Kiểm chứng bằng:** S1, S3, S4; INV-ATH-03, 06, 08, 10

Tài liệu của feature này (luồng, kế hoạch, phần hoãn, chọn giải pháp kỹ thuật, đáp ứng NFR) đặt trong thư mục này khi
bắt đầu thiết kế.

## Việc đã giao cho feature này

- Thiết kế lại phần đăng nhập, MFA, mạng xã hội theo quy ước log (`docs/global/4.convention/logging.md` mục 7): bỏ dòng ghi nguyên token ở mức WARN trong phần cấp token, ưu tiên mã tài khoản thay email ở các bộ xử lý đăng nhập thành công, thất bại, MFA, sửa mức log theo quy tắc.
- **Công cụ cấp token chỉ cho dev** để giả lập người dùng khi test tay và khi chạy thật: nhập mã tài khoản hoặc vai, nhận token ngắn hạn ký bằng cùng khóa
  như token thật. Chốt chặn: chỉ có khi profile `dev` kèm cờ tường minh, từ chối khởi động nếu bật ở production, chỉ nghe địa chỉ nội bộ, mỗi lần cấp ghi
  một dòng log (cho ai, hạn bao lâu) nhưng không ghi token. Test tự động không cần công cụ này (dùng hỗ trợ test của Spring Security để gắn danh tính giả).
  Xem `docs/global/4.convention/logging.md`, mục "Ngoại lệ có kiểm soát".

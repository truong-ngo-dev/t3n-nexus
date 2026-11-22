# Khung ứng dụng Người bán: kiểm

Theo `6.tesing/test-policy.md`. Cột "Khi": `phần` là làm lúc làm phần này, `đóng` là để tới lúc đóng feature 8.

Phần này đã có code và phần lớn đã có test (lát 11a cũ: Jest, Playwright giả ở `e2e-seller/shell.spec.ts`).

| #   | Ca                                                                                         | Bảo vệ     | Khi   | Có chưa                                      |
|-----|--------------------------------------------------------------------------------------------|------------|-------|----------------------------------------------|
| K1  | Phiên 503 hoặc mất mạng: màn lỗi kèm Thử lại, không đi đăng nhập                           | Q1         | phần  | Có: `seller.guard.spec.ts`, `shell.spec.ts`  |
| K2  | Hồ sơ 5xx, mạng, 404 mã khác, thân sai khuôn: `unknown`, không phải `none`                 | Q2, Q3     | phần  | Có: `seller-status.service.spec.ts`          |
| K3  | `ADMIN`: tới `/forbidden-account`, không vòng lặp, không nạp hồ sơ                         | srs 3.4    | đóng  | Có: `seller.guard.spec.ts`, `app.config.spec.ts` |
| K4  | Thử lại khi hết lỗi: vào lại đúng URL đang mở                                              | design 2   | đóng  | Có: `app.component.spec.ts`                  |
| K5  | Guard theo trạng thái: vào sai trạng thái thì về `/`                                       | Q4         | đóng  | Chưa có spec cho `profile-state.guard.ts`; `approvedGuard` thêm ở phần 2 |
| K6  | Đăng xuất bấm đúp chỉ một `POST`; thất bại thì mở khóa và báo                              | srs 3.2    | phần  | Có: `shell.spec.ts`                          |
| K7  | Chạy thật qua `seller.localhost:8000`: `USER` chưa có hồ sơ, `USER` chờ duyệt, `ADMIN`, đăng xuất | Toàn luồng | đóng | Mới chạy ca đầu (`2026-11-17`)           |

Không có phá thử: phần này không có ca kỹ thuật như đồng thời hay sai thứ tự.

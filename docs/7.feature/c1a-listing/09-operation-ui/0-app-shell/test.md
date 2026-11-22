# Khung ứng dụng Quản trị: kiểm

Theo `6.tesing/test-policy.md`. Cột "Khi": `phần` là làm lúc làm phần này, `đóng` là để tới lúc đóng feature 9. Thanh trên hiện
đúng chữ, bố cục đẹp thì kiểm bằng mắt.

| #   | Ca                                                                                               | Bảo vệ        | Cách                     | Khi   |
|-----|--------------------------------------------------------------------------------------------------|---------------|--------------------------|-------|
| K0  | **Thông luồng:** phiên `ADMIN` mở `/`: thấy bố cục và tới `/sellers/pending`                      | 3.1           | Playwright giả           | phần  |
| K1  | Phiên `USER` vào `/` rồi `/sellers/pending`: tới `/forbidden-account`, không vòng lặp, không có lời gọi `/api/` nào | Q1, design mục 4 | Jest guard; Playwright giả đếm lời gọi | phần |
| K2  | Kiểm phiên trả 503 hoặc mất mạng: màn lỗi kèm Thử lại, **không** điều hướng tới đăng nhập         | Q2            | Jest guard               | phần  |
| K6  | `describeError`: mỗi nhóm của `classifyFailure` ra đúng loại và câu; `traceId` có thì lấy, thân lạ thì không ném | Q4, Q5 | Jest, hàm thuần     | phần  |
| K3  | Màn lỗi phiên, bấm Thử lại khi máy chủ đã lên: vào đúng URL đang mở                              | 3.3           | Playwright giả           | đóng  |
| K4  | Khách mở `/sellers/pending`: điều hướng tới đăng nhập đúng một lần                               | 3.1           | Jest guard               | đóng  |
| K5  | Một lời gọi API bất kỳ nhận 401 giữa chừng: đi đăng nhập                                         | 3.4           | Playwright giả           | đóng  |
| K7  | Đăng xuất: bấm đúp chỉ một `POST`; thất bại thì nút mở lại và có thông báo                       | 3.5           | Playwright giả           | đóng  |
| K8  | Mở `/sellers/pending` lần đầu: mã của khu `catalog` và `audit` chưa được tải                     | Yêu cầu tải trang | Playwright giả, xem tệp `.js` đã tải | đóng |
| K9  | Chạy thật qua `ops.localhost:8000`: `ADMIN` thấy bố cục; `USER` thấy trang không có quyền; đăng xuất | Toàn luồng  | Chạy thật                | đóng  |

Không có phá thử: phần này không có ca kỹ thuật như đồng thời hay sai thứ tự.

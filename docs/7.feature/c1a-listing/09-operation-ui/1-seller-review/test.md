# Duyệt người bán: kiểm

Theo `6.tesing/test-policy.md`. Cột "Khi": `phần` là làm lúc làm phần này, `đóng` là để tới lúc đóng feature 9. Hiện đúng nhãn,
đúng định dạng ngày thì kiểm bằng mắt.

## 1. Hàm thuần (Jest)

Hàm thuần chứa quy tắc nên spec viết cùng lúc với hàm.

| #   | Ca                                                                                                     | Bảo vệ          | Khi   |
|-----|--------------------------------------------------------------------------------------------------------|-----------------|-------|
| D1  | `decision-outcome`: đủ bốn dòng của bảng đối chiếu (thiết kế 4.5), kể cả `status` đúng nhưng `reviewedBy` là người khác thì **không** phải `recorded` | srs 2.6 | phần |
| D2  | Kiểm lý do: "", "   ", 500 ký tự (đạt), 501 ký tự (không đạt), khoảng trắng đầu cuối không tính khi gửi | Q3             | phần  |
| D3  | Đọc `page` từ query: thiếu, "abc", "-1" đều thành 0                                                    | srs 2.1         | phần  |
| D4  | `parse…`: thân thiếu `version` hoặc `status` lạ thì trả `null` (thành lỗi, không hiện hồ sơ nửa vời)    | Q2              | phần  |

## 2. Thành phần và luồng với phản hồi giả

| #   | Ca                                                                                                                          | Bảo vệ         | Khi   |
|-----|-----------------------------------------------------------------------------------------------------------------------------|----------------|-------|
| L0  | **Thông luồng:** mở hàng chờ, mở một hồ sơ, duyệt, về hàng chờ với thông báo; hồ sơ đó không còn trong bảng                 | srs 2.1–2.3    | phần  |
| L1  | Lật nhanh trang 1 → 2 → 3, phản hồi trang 2 về sau trang 3: bảng hiện trang 3                                               | design 3.2     | phần  |
| L4  | Duyệt: bấm đúp nút xác nhận chỉ một `POST`; thân gửi đúng `version` của bản đang hiện                                      | Q2, Q4         | phần  |
| L5  | Duyệt nhận 409 `STALE_VERSION`: tải lại, hiện bản mới với thông báo "vừa sửa"; **không** có `POST` thứ hai; bấm Duyệt lần nữa gửi `version` mới | srs 2.5, Q2 | phần |
| L7  | Duyệt mất mạng, bản tải lại là `APPROVED` bởi chính mình: báo "đã được ghi nhận" và về hàng chờ, không báo lỗi              | srs 2.6        | phần  |
| L9  | Từ chối lỗi 504: hộp kiểm lại hồ sơ trước khi cho bấm lại                                                                   | design 4.4     | phần  |
| L13 | Không có số căn cước, mã số thuế trong URL, console, thông báo (kiểm trong lượt chạy L0)                                    | Q5             | phần  |
| L2  | `?page=5` khi chỉ có 32 hồ sơ: URL thành `?page=1`; `?page=3` khi không còn hồ sơ nào: hiện hàng chờ rỗng                  | srs mục 5      | đóng  |
| L3  | Mở chi tiết từ trang 2, bấm "‹ Hàng chờ duyệt": về `?page=1`; mở thẳng đường dẫn chi tiết rồi bấm: về `?page=0`             | design 3.3     | đóng  |
| L6  | Duyệt nhận 409 `INVALID_STATE`, bản tải lại là `APPROVED` bởi người khác: thông báo "đã được xử lý", không còn nút           | srs 2.5        | đóng  |
| L8  | Duyệt mất mạng, tải lại cũng lỗi: thông báo "chưa rõ", nút quyết định khóa, "Tải lại hồ sơ" mở khóa khi tải được            | srs 2.6        | đóng  |
| L9b | Từ chối lỗi 503: hộp vẫn mở, chữ còn nguyên, bấm lại được                                                                  | srs mục 4      | đóng  |
| L10 | Từ chối với lý do toàn khoảng trắng: không có lời gọi nào                                                                  | Q3             | đóng  |
| L11 | Hồ sơ `REJECTED` mở từ đường dẫn: có lý do từ chối, không có nút quyết định                                                | Q1             | đóng  |
| L12 | Đường dẫn `/sellers/khong-phai-uuid`: trang "không tìm thấy", không có lời gọi API                                         | srs mục 4      | đóng  |

## 3. Chạy thật (khi đóng feature 9)

Qua `ops.localhost:8000` trên ngăn xếp cách ly, cần `seller-service`, `ops-web-bff`, `oauth2-service`. Không chạm DB dev.

| #   | Ca                                                                                                                     |
|-----|------------------------------------------------------------------------------------------------------------------------|
| T1  | Nộp hai hồ sơ ở `seller-web`; quản trị thấy cả hai trong hàng chờ, cũ trước                                            |
| T2  | Duyệt hồ sơ 1: về hàng chờ, còn một hồ sơ; ở `seller-web` người bán thấy "Đã duyệt"                                     |
| T3  | Quản trị mở hồ sơ 2. Người bán sửa mô tả ở `seller-web` ("Sửa hồ sơ"; trước khi có màn này thì gọi `PUT /api/seller/seller/profile`). Quản trị bấm Từ chối và thấy thông báo "vừa sửa" kèm bản mới |
| T4  | Từ chối hồ sơ 2 kèm lý do: người bán thấy đúng lý do ở `seller-web`                                                    |
| T5  | Hai tab quản trị cùng mở một hồ sơ, tab 1 duyệt, tab 2 bấm Duyệt: tab 2 thấy "đã được xử lý"; `reviewedBy` khớp `userId` của phiên |

## 4. Phá thử (khi đóng feature 9)

Chỉ ca kỹ thuật:

| Lỗi cố ý                                   | Ca phải đỏ |
|--------------------------------------------|------------|
| Bỏ `switchMap`, dùng `mergeMap`            | L1         |

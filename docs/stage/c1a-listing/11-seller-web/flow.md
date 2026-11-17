# Feature 11: Giao diện Người bán

> **Trạng thái:** Đang thiết kế `2026-11-11` (lát 11a viết chi tiết, 11b đến 11e mức tên) · **Chặng:** C1a · **Loại:** Giao diện
>
> **Nguồn:** `5-ui-analysis.md` mục 4.2, `stage.md` (S1 đến S5), feature 03, 07, 08, 09 (API), feature 10 (cổng, BFF), ADR-0006 (nguồn gốc theo `Host`), ADR-0007 (workspace giao diện) · **Kiểm chứng bằng:** S1 đến S5 nhìn thấy qua giao diện (kiểm cuối ở feature 13)
>
> **Cùng thư mục:** `tech.md` (giải pháp, điểm cần chốt), `plan.md`, `deferred.md`.

## 1. Mục tiêu và phạm vi

Người bán mở `seller.localhost` (qua cổng), đăng nhập, nộp hồ sơ gian hàng, theo dõi kết quả duyệt, tạo sản phẩm có biến thể, giá, ảnh rồi đăng. Giao diện không cầm token: mọi lời gọi đi cùng nguồn gốc qua cổng tới `seller-web-bff` (feature 10).

Chia thành năm lát dọc; mỗi lát tự chạy và kiểm được, đóng xong mới sang lát sau.

| Lát                          | Nội dung                                                                                     | API nguồn                              | Kịch bản                    |
|------------------------------|----------------------------------------------------------------------------------------------|----------------------------------------|-----------------------------|
| **11a** Khung                | Dự án `seller-web`, phiên, guard theo vai, trạng thái người bán (chỉ đọc), bố cục, trang lỗi | Feature 10; `GET` hồ sơ của feature 03 | Nền cho S1 đến S5           |
| **11b** Hồ sơ gian hàng      | Nộp hồ sơ, xem trạng thái, xem lý do từ chối, sửa và nộp lại                                 | Feature 03                             | S4, nửa người bán của S2    |
| **11c** Sản phẩm và biến thể | Danh sách, tạo và sửa nháp, trục biến thể, mã SKU, giá                                       | Feature 07                             | S5 (nửa không đăng)         |
| **11d** Ảnh                  | Tải ảnh lên, tiến trình, thứ tự, xóa                                                         | Feature 08                             | S1 (có ảnh), S5 (thiếu ảnh) |
| **11e** Đăng sản phẩm        | Đăng, gỡ, đăng lại, xóa nháp; báo thiếu gì khi bị từ chối                                    | Feature 09                             | S1, S3, S5                  |

Ngoài phạm vi: duyệt hồ sơ (feature 12; khi thử S1, S2, S3 thì quản trị duyệt bằng gọi API trực tiếp cho tới khi có feature 12); tồn kho, đơn hàng, báo cáo, chat (chặng sau); thành phần giao diện dùng chung giữa các ứng dụng (khi có ứng dụng Vận hành, ADR-0007).

## 2. Trạng thái người bán và màn hình

Giao diện luôn ở đúng một trong các trạng thái sau; trạng thái quyết định người dùng thấy gì. Trạng thái tới từ hai nguồn: phiên (`/bff/auth/session`: vai) và hồ sơ của tôi (`GET /api/seller/seller/profile`).

| Trạng thái        | Nhận biết                                | Người dùng thấy                                                             |
|-------------------|------------------------------------------|-----------------------------------------------------------------------------|
| Đang kiểm         | Đang thăm dò phiên hoặc nạp hồ sơ        | Màn chờ, chưa hiện gì khác                                                  |
| Khách             | Phiên 401                                | Bị đưa tới đăng nhập (một lần, không vòng lặp)                              |
| Lỗi               | Phiên hoặc hồ sơ trả 5xx, mạng, khuôn lạ | Màn lỗi kèm nút thử lại; **không** coi là khách, không coi là chưa có hồ sơ |
| Tài khoản nội bộ  | Phiên có vai `ADMIN`                     | Thông báo "tài khoản này không bán hàng" kèm nút đăng xuất                  |
| Chưa là người bán | Hồ sơ 404 `SELLER_PROFILE_NOT_FOUND`     | Trang mời nộp hồ sơ (biểu mẫu ở 11b)                                        |
| Chờ duyệt         | `PENDING_REVIEW`                         | Trang trạng thái "đang chờ duyệt"                                           |
| Bị từ chối        | `REJECTED`                               | Lý do từ chối, đường sửa và nộp lại (11b)                                   |
| Đã duyệt          | `APPROVED`                               | Khu sản phẩm (11c đến 11e)                                                  |

## 3. Luồng của lát 11a

### 3.1 Khởi động
1. Trang tải; bộ khởi động chờ thăm dò phiên xong (kể cả lỗi) trước khi định tuyến đầu tiên.
2. Phiên là `USER`: nạp hồ sơ của tôi, ra một trong bốn trạng thái người bán (hoặc lỗi).
3. Phiên là khách, `ADMIN` hoặc lỗi: không nạp hồ sơ.

### 3.2 Vào trang
- Trang cần đăng nhập, khách vào: điều hướng cấp cao tới `/bff/auth/login`, BFF chuyển tới `/oauth2/authorization/seller-web`; sau đăng nhập về trang đích của ứng dụng (cấu hình ở BFF).
- `/login`: nút đi đăng nhập, hiện thông báo chung khi có `?error`, không tự chuyển.
- Mọi đường không khớp: trang "không tìm thấy" trong khung, không chuyển hướng.

### 3.3 Đăng xuất
Nút trong khung gọi đăng xuất (một lần trên cả thiết bị, feature 03); thành công thì điều hướng tới địa chỉ BFF trả về; thất bại thì báo lỗi, giữ nguyên trạng thái.

### 3.4 Lời gọi API
Đi tương đối cùng nguồn gốc, kèm `X-XSRF-TOKEN` cho phương thức ghi. 401 giữa phiên: đi đăng nhập một lần (cờ chặn vòng lặp). 403 CSRF: phục hồi một lần rồi gửi lại. 429, 503: báo lỗi, không tự thử lại.

## 4. Đường không suôn sẻ và các điểm cần rà (lát 11a)

| #  | Tình huống                                                                       | Hành vi cần có                                                                                                |
|----|----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| R1 | Phiên thăm dò 5xx hoặc mạng                                                      | Màn lỗi kèm thử lại; không đi đăng nhập, không hiện biểu mẫu nộp hồ sơ                                        |
| R2 | Hồ sơ trả 5xx hoặc mạng                                                          | Màn lỗi kèm thử lại; **không** hiểu là "chưa là người bán" (nếu hiểu nhầm, người đã có hồ sơ bị mời nộp thêm) |
| R3 | Hồ sơ 404                                                                        | Chỉ riêng mã `SELLER_PROFILE_NOT_FOUND` mới là "chưa là người bán"; 404 khác (sai đường dẫn tuyến) là lỗi     |
| R4 | Vai `ADMIN` đăng nhập ở Người bán                                                | Thông báo và đăng xuất; không có vòng lặp chuyển hướng giữa guard và trang đích                               |
| R5 | Khách vào trang cần đăng nhập, quay lại 401 liên tục                             | Đi đăng nhập đúng một lần                                                                                     |
| R6 | Mở thẳng `localhost:4202` (không qua cổng)                                       | Không hỗ trợ: phiên không có ở nguồn gốc này; chỉ ghi README                                                  |
| R7 | Đăng nhập ở Cửa hàng rồi mở Người bán                                            | Chưa đăng nhập ở Người bán (phiên theo host, ADR-0006)                                                        |
| R8 | Hồ sơ đổi trạng thái sau khi đã nạp (quản trị duyệt khi người bán đang mở trang) | Trang hiện trạng thái cũ cho tới khi tải lại hoặc làm lại thao tác; ghi nhận, không thăm dò định kỳ           |

## 5. Trạng thái sau khi xong lát 11a

- `seller-web` chạy ở cổng 4202, mở được qua `seller.localhost:8000`, đăng nhập và đăng xuất được bằng BFF thật.
- Người dùng `USER` thấy đúng một trong bốn trạng thái người bán; `ADMIN` và lỗi được xử lý đúng.
- Thêm một trang mới chỉ là thêm route và thành phần; không phải đụng phiên, bộ chặn, trạng thái người bán.

## 6. Test cần có (lát 11a)

| #     | Kiểm                                                                                                                                                                        | Cách                           |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| KT-01 | Ánh xạ hồ sơ: 200 theo `status` (ba giá trị); 404 `SELLER_PROFILE_NOT_FOUND` thành chưa là người bán; 404 mã khác, 5xx, mạng, khuôn lạ thành lỗi (R2, R3)                   | Jest, dịch vụ trạng thái       |
| KT-02 | Guard: khách đi đăng nhập một lần; `USER` vào; `ADMIN` bị chuyển tới trang thông báo mà không vòng lặp; đang kiểm hoặc lỗi thì không vào và không đi đăng nhập (R1, R4, R5) | Jest                           |
| KT-03 | Bộ khởi động: chờ phiên xong; chỉ `USER` mới nạp hồ sơ                                                                                                                      | Jest                           |
| KT-04 | Khung: nhãn tên người bán hoặc trạng thái, nút đăng xuất khóa khi đang gửi, báo lỗi khi đăng xuất thất bại                                                                  | Jest                           |
| KT-05 | Mỗi trạng thái ở mục 2 ra đúng màn hình (phiên và hồ sơ giả bằng `page.route`)                                                                                              | Playwright giả                 |
| KT-06 | Đăng nhập, xem trạng thái, đăng xuất qua `seller.localhost:8000` bằng BFF, Xác thực thật; hồ sơ lấy từ dữ liệu thật của người thử                                           | Chạy thật một lần khi đóng lát |

Từ vựng vai chỉ `USER`, `ADMIN` (ESLint của workspace chặn từ vựng khác).

## 7. Lát 11b đến 11e (mức tên; chi tiết viết khi bắt đầu từng lát)

- **11b:** biểu mẫu nộp hồ sơ theo loại hình (`INDIVIDUAL`, `HOUSEHOLD`, `BUSINESS` đòi trường pháp lý khác nhau); lỗi theo trường từ máy chủ (`SHOP_NAME_TAKEN`, `IDENTITY_ALREADY_REGISTERED`, `INVALID_IDENTITY_NUMBER`...); xem hồ sơ; sửa thông tin gian hàng; khi bị từ chối: lý do và nộp lại toàn bộ trường.
- **11c:** danh sách sản phẩm, tạo nháp, biến thể theo trục, SKU, giá; báo lỗi R9 (hai biến thể cùng tổ hợp, giá sai).
- **11d:** tải ảnh theo cơ chế ADR-0013, tiến trình, hủy, sắp xếp, xóa.
- **11e:** đăng, gỡ, đăng lại, xóa nháp; điều kiện đăng R7, R8 hiện thành thông báo nêu rõ thiếu gì.

# Hồ sơ gian hàng: kiểm

Theo `6.tesing/test-policy.md`. Cột "Khi": `phần` là làm lúc làm hai chế độ sửa (việc đang tới của phần này), `đóng` là để tới
lúc đóng feature 8. Nộp, nộp lại, xem đã có code; các ca của chúng đánh dấu "Có".

## 1. Hàm thuần (Jest)

Hàm thuần chứa quy tắc nên spec viết cùng lúc với hàm.

| #   | Ca                                                                                                                         | Bảo vệ           | Khi   | Có chưa  |
|-----|----------------------------------------------------------------------------------------------------------------------------|------------------|-------|----------|
| H1  | Kiểm theo loại hình: ô bắt buộc, độ dài, căn cước, mã số thuế, số điện thoại (bảng srs mục 4)                              | srs mục 4        | phần  | Có       |
| H2  | `buildSubmitRequest`: cắt khoảng trắng, bỏ ô tùy chọn trống, không gửi người đại diện khi không phải doanh nghiệp          | srs mục 3        | phần  | Có       |
| H3  | `formValueFromProfile`: trường vắng hoặc `null` thành chuỗi rỗng                                                           | design mục 5     | phần  | Có       |
| H4  | `validateProfileForm` theo `fieldsOf("edit-approved")`: ô pháp lý trống không báo lỗi; tên gian hàng không bị kiểm          | srs mục 3        | phần  | Có       |
| H5  | `buildShopInfoPatch`: chỉ có ô đã đổi; " Mô tả " bằng "Mô tả" (không gửi); mô tả bị xóa trắng thành `null`; không bao giờ có `shopName` | Q7, srs mục 3 | phần | Có |
| H6  | `isDirty`: `edit-pending` đổi một ô rồi đổi về thì `false`; ô người đại diện còn giá trị nhưng đang là cá nhân thì không làm `true` | design mục 2 | phần | Có |
| H7  | `mapSubmitError(error, mode)`: mỗi dòng bảng srs mục 5 ra đúng chỉ thị; `stale` ở `submit` rời về `/`, ở ba chế độ kia về `/profile`; `SHOP_NAME_LOCKED` thành `form` | srs mục 5, Q6 | phần | Có |

## 2. Thành phần và luồng

| #   | Ca                                                                                                                       | Bảo vệ         | Cách            | Khi   |
|-----|--------------------------------------------------------------------------------------------------------------------------|----------------|-----------------|-------|
| K3  | **Thông luồng `edit-pending`:** "Lưu thay đổi" khóa lúc mở; đổi một ô thì mở; lưu thì `PUT` đủ thân, về `/profile`         | srs mục 3      | Playwright giả  | phần  |
| K10 | **Thông luồng `edit-approved`:** tên gian hàng và pháp lý hiện bằng chữ; lưu thì `PATCH` chỉ có ô đã đổi                   | srs mục 3      | Playwright giả  | phần  |
| K6  | Hết giờ khi gửi: nút giữ khóa; `refresh()` chạy; về đúng `leaveTo`; **không** có lời gọi thứ hai                          | Q6             | Jest thành phần | phần  |
| K7  | Bấm đúp nút gửi: một lời gọi                                                                                             | Q5             | Playwright giả  | phần  |
| K1  | Guard: mỗi route ở bảng design mục 2 chỉ vào được ở đúng trạng thái; `loading`, `unknown` không vào, không chuyển        | srs mục 1      | Jest            | đóng  |
| K2  | Gửi khi lỗi ở hai ô: không có lời gọi; focus ô lỗi đầu theo thứ tự màn hình                                              | Q2             | Jest thành phần | đóng  |
| K4  | `SHOP_NAME_TAKEN`: đỏ dưới ô tên; sửa ô thì mất; gõ lại tên cũ thì hiện lại                                              | Q3             | Jest thành phần | đóng  |
| K5  | 503 khi gửi: dòng lỗi biểu mẫu có mã truy vết, cuộn lên dòng lỗi, ô còn nguyên, gửi lại được                             | Q4, srs mục 5  | Playwright giả  | đóng  |
| K8  | `submit` và `resubmit` thành công: `refresh()` xong trước khi chuyển trang; tới được `/profile`                          | design mục 2   | Jest thành phần | đóng  |
| K9  | Đổi doanh nghiệp → cá nhân sau khi đã gõ người đại diện: ô ẩn; thân gửi không có `representativeName`                    | srs mục 3      | Playwright giả  | đóng  |
| K11 | Trang xem và trang chủ: đúng nút theo từng trạng thái (bảng srs mục 1)                                                   | srs mục 2      | Playwright giả  | đóng  |
| K12 | `edit-pending` nhận `INVALID_STATE` (hồ sơ vừa được duyệt): snackbar, về `/profile`, thấy "Đã duyệt"                      | srs mục 5      | Jest thành phần | đóng  |
| K13 | Mở biểu mẫu sửa khi `GET` hồ sơ lỗi 503: màn lỗi trang có Thử lại; Thử lại thành công thì biểu mẫu điền sẵn               | design 6.1     | Jest thành phần | đóng  |

Playwright giả đặt ở `e2e-seller/profile.spec.ts`.

## 3. Chạy thật (khi đóng feature 8)

Qua `seller.localhost:8000` trên ngăn xếp cách ly. Không chạm DB dev. Cần backend B1 đến B3 đã xong.

| #   | Ca                                                                                                                          |
|-----|-----------------------------------------------------------------------------------------------------------------------------|
| T1  | Nộp hồ sơ hộ kinh doanh; thấy "Chờ duyệt". Sửa hồ sơ; tải lại thấy giá trị mới, vẫn "Chờ duyệt"                             |
| T2  | Tài khoản khác nộp trùng tên gian hàng: lỗi dưới ô tên                                                                      |
| T3  | Quản trị từ chối hồ sơ T1. Tải lại: thấy lý do; sửa và nộp lại; thấy "Chờ duyệt"                                           |
| T4  | Quản trị duyệt. Tải lại: thấy "Đã duyệt"; sửa mô tả được; không sửa được tên gian hàng và pháp lý                          |
| T5  | Mở "Sửa hồ sơ" khi chờ duyệt, quản trị duyệt ở chỗ khác, bấm "Lưu thay đổi": về trang xem, thấy "Đã duyệt"                  |
| T6  | Các ca còn lại của khung (`../0-app-shell/test.md` K7)                                                                      |

Không có phá thử: phần này không có ca kỹ thuật như đồng thời hay sai thứ tự.

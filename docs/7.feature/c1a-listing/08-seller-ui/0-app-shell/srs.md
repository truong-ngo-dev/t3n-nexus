# Khung ứng dụng Người bán: yêu cầu

## 1. Mục tiêu

Người dùng mở `seller.localhost`, đăng nhập bằng tài khoản đã tạo ở Cửa hàng. Ngay sau đó họ thấy mình đang ở trạng thái nào:
- chưa là người bán;
- đang chờ duyệt;
- bị từ chối (kèm lý do);
- đã được duyệt.

Mỗi trạng thái mở ra đúng những việc làm được lúc đó. Trạng thái do máy chủ quyết; giao diện chỉ hiện điều nó đã biết chắc.

## 2. Ai dùng

Vai `USER`. Một người vừa mua vừa bán dùng chung tài khoản với Cửa hàng, nhưng phiên đăng nhập tính riêng theo từng địa chỉ
(ADR-0006). Đã đăng nhập ở Cửa hàng thì sang Người bán vẫn phải đăng nhập lại; việc đăng nhập này thường chỉ là một lần chuyển
trang, vì phiên ở cổng xác thực vẫn còn.

## 3. Luồng

**3.1 Mở ứng dụng.** Giao diện kiểm hai thứ theo thứ tự: phiên, rồi hồ sơ người bán (chỉ khi phiên là `USER`).

| Kết quả                                    | Người dùng thấy                                                                     |
|--------------------------------------------|-------------------------------------------------------------------------------------|
| Đang kiểm                                  | Vòng xoay "Đang kiểm tra tài khoản…"                                                |
| Chưa đăng nhập                             | Trang đăng nhập có nút "Đăng nhập"                                                  |
| `USER`, chưa có hồ sơ                      | Trang chủ "Bắt đầu bán hàng" và nút "Nộp hồ sơ"                                     |
| `USER`, hồ sơ chờ duyệt                    | Trang chủ "Hồ sơ đang chờ duyệt" và nút "Xem hồ sơ"                                  |
| `USER`, hồ sơ bị từ chối                   | Trang chủ có lý do từ chối, nút "Sửa và nộp lại" và "Xem hồ sơ"                      |
| `USER`, hồ sơ đã duyệt                     | Trang chủ "Gian hàng đã được duyệt", nút "Quản lý sản phẩm" và "Xem hồ sơ"           |

Hai trường hợp không thuộc luồng này, xem mục 3.4: tài khoản `ADMIN`, và lỗi khi kiểm.

Thanh trên có nhãn trạng thái (ví dụ "Chờ duyệt") để người bán luôn biết mình đang ở đâu, kể cả khi ở trang khác trang chủ.

**3.2 Đăng nhập, đăng xuất, hết phiên.**
- Nút "Đăng nhập" chuyển sang trang đăng nhập chung. Đăng nhập xong thì quay về trang chủ.
- Đăng nhập thất bại thì quay về với thông báo "Đăng nhập không thành công" và nút "Thử lại".
- Lời gọi nào nhận 401 thì đi đăng nhập lại, đúng một lần.
- Bấm "Đăng xuất" đúp chỉ gửi một lần. Đăng xuất thất bại thì báo và ở lại.

**3.3 Trạng thái đổi trong lúc đang mở.** Ví dụ quản trị duyệt hồ sơ trong khi người bán đang để trang mở. Giao diện không tự
biết điều này; người bán tải lại trang, hoặc làm một thao tác, thì thấy trạng thái mới.

**3.4 Trường hợp lỗi.**

| Tình huống                                                  | Người dùng thấy                                                         |
|-------------------------------------------------------------|-------------------------------------------------------------------------|
| Đăng nhập bằng tài khoản `ADMIN`                            | "Tài khoản này không bán hàng" kèm nút đăng xuất; không có điều hướng   |
| Không kiểm được phiên hoặc hồ sơ (5xx, mất mạng, thân lạ)   | "Không tải được thông tin tài khoản" kèm nút Thử lại (Q1, Q2)           |

**Vì sao `ADMIN` là trường hợp lỗi.**
- Tài khoản nội bộ không có việc gì ở ứng dụng này.
- Họ vào được chỉ vì cổng xác thực dùng chung và `seller-web-bff` chưa chặn vai.
- Giao diện chặn để họ không thấy biểu mẫu nộp hồ sơ. Nếu thấy, họ sẽ gửi và nhận 403 `SELLER_ROLE_NOT_ALLOWED` từ máy chủ.

## 4. Quy tắc

- **Q1.** Lỗi kiểm phiên không bao giờ bị hiểu thành "chưa đăng nhập".
- **Q2.** Lỗi tải hồ sơ không bao giờ bị hiểu thành "chưa là người bán". Nếu hiểu nhầm, người đã có hồ sơ sẽ bị mời nộp thêm một
  hồ sơ nữa.
- **Q3.** Chỉ phản hồi 404 kèm mã `SELLER_PROFILE_NOT_FOUND` mới nghĩa là "chưa có hồ sơ". 404 khác (ví dụ sai đường dẫn ở BFF) là
  lỗi.
- **Q4.** Mỗi trang chỉ mở được ở trạng thái mà nó có nghĩa:
  - trang nộp hồ sơ: khi chưa có hồ sơ;
  - trang sửa và nộp lại: khi bị từ chối;
  - khu sản phẩm: khi đã duyệt.

  Vào sai trạng thái (gõ đường dẫn, bấm quay lại) thì về trang chủ.
- **Q5.** Không lưu trạng thái người bán ở bộ nhớ của trình duyệt (`localStorage`): trạng thái do máy chủ quyết và có thể đổi
  giữa hai lần mở.
- **Q6.** Lỗi hệ thống hiện mã truy vết khi máy chủ có trả về (sàn chung 6-ui mục 6).

## 5. Yêu cầu phi chức năng

- Ưu tiên máy tính; dưới khoảng 600px không vỡ bố cục.
- Mỗi khu (hồ sơ, sản phẩm) tải mã riêng khi mở lần đầu. Gói ban đầu dưới ngưỡng cảnh báo 500 kB.

## 6. Không làm

| Dòng                                                     | Lý do                                                                                    |
|----------------------------------------------------------|------------------------------------------------------------------------------------------|
| Tự cập nhật khi quản trị duyệt (3.3)                     | Cần thời gian thực (C4); thăm dò định kỳ tốn lời gọi                                     |
| Trang đăng ký tài khoản                                  | Tài khoản tạo ở Cửa hàng                                                                 |
| Liên kết "Đăng ký ở Cửa hàng" trên trang đăng nhập       | Chưa có cấu hình giữ địa chỉ Cửa hàng; gõ cứng thì sai ở môi trường khác                 |
| Quay về đúng trang đang mở sau khi đăng nhập             | Hiện luôn về trang chủ; làm khi thấy vướng                                               |

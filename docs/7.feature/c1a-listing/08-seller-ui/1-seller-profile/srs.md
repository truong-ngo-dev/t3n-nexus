# Hồ sơ gian hàng: yêu cầu

## 1. Mục tiêu

Người dùng nộp hồ sơ để trở thành người bán, rồi theo dõi kết quả duyệt. Ở mỗi trạng thái, người bán làm được đúng một việc với
hồ sơ:

| Trạng thái  | Việc được làm                                    | Sau khi gửi              |
|-------------|--------------------------------------------------|--------------------------|
| Chưa có     | Nộp hồ sơ                                        | Chờ duyệt                |
| Chờ duyệt   | Sửa toàn bộ hồ sơ (thấy nhập sai trước khi được xét) | Vẫn chờ duyệt        |
| Bị từ chối  | Sửa và nộp lại                                   | Chờ duyệt                |
| Đã duyệt    | Sửa thông tin liên hệ và mô tả gian hàng         | Vẫn đã duyệt             |

Hồ sơ được duyệt thì mới đăng được sản phẩm.

BC: `Seller`. Backend phần lớn đã có (feature 03 cũ); hợp đồng ở `service/seller-service/api.yaml`. Phần phải đổi ở backend xem
mục 7.

## 2. Luồng chung

Bốn việc ở bảng trên đi cùng một luồng, chỉ khác vài chi tiết (mục 3):

1. Từ trang chủ hoặc trang xem hồ sơ, bấm nút của việc đó.
2. Biểu mẫu mở ra. Trừ khi nộp mới, biểu mẫu điền sẵn hồ sơ hiện tại.
3. Người bán gõ. Lỗi của ô hiện khi rời ô.
4. Bấm gửi:
   - **còn lỗi:** không gửi, con trỏ tới ô lỗi đầu;
   - **hết lỗi:** nút khóa và hiện "Đang gửi…", rồi gửi.
5. Kết quả:
   - **thành công:** về trang xem hồ sơ với thông báo;
   - **lỗi:** hiện theo mục 5.

**Xem hồ sơ** (khi đã có hồ sơ, mọi trạng thái).
- Trang hiện đủ thông tin đã nộp, kể cả số căn cước, vì là hồ sơ của chính người đó.
- Thông tin chia hai khối, "Thông tin pháp lý" và "Gian hàng và liên hệ".
- Trang có nút của việc được làm ở trạng thái hiện tại. Bị từ chối thì có thêm lý do từ chối.

## 3. Khác nhau giữa bốn việc

| Điểm                       | Nộp                   | Sửa khi chờ duyệt                                       | Nộp lại                         | Sửa khi đã duyệt                                       |
|----------------------------|-----------------------|---------------------------------------------------------|---------------------------------|--------------------------------------------------------|
| Nút mở                     | "Nộp hồ sơ"           | "Sửa hồ sơ"                                             | "Sửa và nộp lại"                | "Sửa thông tin gian hàng"                              |
| Ô sửa được                 | Tất cả                | Tất cả, kể cả đổi loại hình                             | Tất cả, kể cả đổi loại hình     | Mô tả, người liên hệ, số điện thoại, địa chỉ liên hệ   |
| Ô chỉ đọc                  | —                     | —                                                       | —                               | Tên gian hàng, loại hình, thông tin pháp lý            |
| Dòng ở đầu biểu mẫu        | —                     | "Hồ sơ đang chờ duyệt. Quản trị sẽ xem bản mới nhất sau khi bạn lưu." | Lý do từ chối     | "Tên gian hàng và thông tin pháp lý đã khóa sau khi được duyệt." |
| Nút gửi                    | "Nộp hồ sơ"           | "Lưu thay đổi"                                          | "Nộp lại hồ sơ"                 | "Lưu thay đổi"                                         |
| Chưa đổi gì thì khóa nút gửi | Không               | Có                                                      | Không: nộp lại nguyên bản vẫn có nghĩa là xin xét lại | Có                         |
| Thông báo khi thành công   | "Đã nộp hồ sơ"        | "Đã lưu thay đổi"                                       | "Đã nộp lại hồ sơ"              | "Đã lưu thay đổi"                                      |

**Đổi loại hình giữa chừng** (nộp, sửa khi chờ duyệt, nộp lại):
- Ô chung giữ nguyên giá trị.
- Ô không còn hiện thì ẩn và không gửi. Ví dụ đổi từ doanh nghiệp sang cá nhân thì ô người đại diện ẩn đi.

**Sửa khi chờ duyệt.** Quản trị đang mở bản cũ thì quyết định của họ bị từ chối, và họ phải xem lại bản mới (ứng dụng Quản trị,
`1-seller-review/srs.md` mục 2.5).

**Sửa khi đã duyệt: tên gian hàng khóa.** Tên là thứ quản trị đã xét khi duyệt. Đổi tên sau khi duyệt mà không ai xem lại thì
có thể giả tên thương hiệu khác. Các sàn thật thường giới hạn đổi tên hoặc bắt duyệt lại. *Đề xuất của agent, chờ người chốt:*
nếu không khóa thì bỏ tên khỏi dòng "ô chỉ đọc", và ghi rủi ro vào mục 6.

## 4. Quy tắc

**Quy tắc nhập.** Giao diện kiểm trước khi gửi, cùng quy tắc với máy chủ. Khoảng trắng đầu và cuối không tính.

| Ô                    | Bắt buộc                                     | Quy tắc                                                                    |
|----------------------|----------------------------------------------|----------------------------------------------------------------------------|
| Loại hình            | Có                                           | Một trong ba                                                               |
| Tên pháp lý          | Có                                           | 2 đến 150 ký tự. Nhãn: "Họ tên", "Tên chủ hộ", "Tên doanh nghiệp"          |
| Người đại diện       | Chỉ doanh nghiệp                             | 2 đến 100 ký tự                                                            |
| Số căn cước          | Có                                           | 12 chữ số (khoảng trắng bỏ qua)                                            |
| Mã số thuế           | Hộ và doanh nghiệp; cá nhân tùy chọn         | 10 chữ số, hoặc 13 chữ số dạng `0123456789-001` hay liền                   |
| Địa chỉ kinh doanh   | Có                                           | 10 đến 300 ký tự. Doanh nghiệp gọi là "Địa chỉ trụ sở"                     |
| Tên gian hàng        | Có                                           | 3 đến 60 ký tự; duy nhất toàn sàn (chỉ máy chủ biết)                        |
| Mô tả gian hàng      | Không                                        | Tối đa 500 ký tự                                                           |
| Người liên hệ        | Có                                           | 2 đến 100 ký tự                                                            |
| Số điện thoại        | Có                                           | Di động Việt Nam; nhận `+84`, `84`, khoảng trắng, dấu chấm, gạch            |
| Địa chỉ liên hệ      | Không                                        | 10 đến 300 ký tự; trống thì coi như trùng địa chỉ kinh doanh               |

**Quy tắc hành xử.**
- **Q1.** Lỗi của ô chỉ hiện sau khi người dùng rời ô đó, hoặc sau lần bấm gửi đầu tiên.
- **Q2.** Bấm gửi khi còn lỗi thì không gửi, và đưa con trỏ tới ô lỗi đầu tiên theo thứ tự trên màn hình.
- **Q3.** Lỗi máy chủ của một ô hiện ngay tại ô đó. Sửa ô thì lỗi mất; gõ lại đúng giá trị cũ thì lỗi hiện lại.
- **Q4.** Gửi lỗi do hệ thống thì dữ liệu đã nhập còn nguyên, người dùng chỉ việc bấm gửi lại.
- **Q5.** Bấm đúp, hay bấm lúc đang gửi, chỉ tạo một lời gọi.
- **Q6.** Không chắc thao tác đã ghi hay chưa, hoặc máy chủ báo trạng thái hồ sơ đã đổi: không cho gửi lại ngay. Giao diện tải lại
  trạng thái, rời biểu mẫu, và nói cho người dùng đây là trạng thái hiện tại. Gửi lại mù sẽ ra lỗi khó hiểu như "đã có hồ sơ".
- **Q7.** Sửa thì chỉ ghi phần đã đổi. Ô tùy chọn bị xóa trắng thì máy chủ phải xóa giá trị cũ, không giữ lại.
- **Q8.** Số căn cước, mã số thuế không xuất hiện trong đường dẫn, console hay thông báo lỗi.

## 5. Lỗi người dùng phải hiểu được

Năm cách hiện lỗi:

| Cách                 | Trông thế nào                                                              | Dùng khi                                          |
|----------------------|----------------------------------------------------------------------------|---------------------------------------------------|
| **Lỗi dưới ô**       | Viền đỏ và chữ đỏ ngay dưới ô                                              | Lỗi gắn được vào một ô                            |
| **Dòng lỗi biểu mẫu**| Khối đỏ ở đầu biểu mẫu, trang cuộn lên khối đó                             | Lỗi của cả lần gửi; người dùng vẫn ở biểu mẫu     |
| **Thông báo nổi**    | Snackbar dưới đáy màn hình, tự tắt sau 8 giây, có nút Đóng                 | Người dùng vừa bị đưa khỏi biểu mẫu (Q6), hoặc báo thành công |
| **Màn lỗi trang**    | Thay cho nội dung trang: câu lỗi, mã truy vết, nút Thử lại                 | Không tải được dữ liệu để hiện trang              |
| **Không hiện**       | —                                                                          | Bộ chặn chung đã xử lý (401 thì đi đăng nhập)     |

Không dùng hộp thoại (popup) cho lỗi. Hộp thoại chặn cả trang và bắt người dùng bấm đóng, trong khi lỗi của biểu mẫu cần thấy
cùng lúc với ô phải sửa.

**Khi mở biểu mẫu hoặc trang xem.**

| Tình huống                          | Cách hiện        | Câu                                                          |
|-------------------------------------|------------------|--------------------------------------------------------------|
| Không tải được hồ sơ (5xx, mạng)    | Màn lỗi trang    | "Không tải được hồ sơ. Kiểm tra kết nối rồi thử lại." + mã truy vết |
| Mở biểu mẫu sai trạng thái          | Không hiện       | Guard đưa về trang chủ                                       |

**Khi gửi.**

| Tình huống                                        | Mã máy chủ                                              | Việc       | Cách hiện                          | Câu                                                                       |
|---------------------------------------------------|---------------------------------------------------------|------------|------------------------------------|---------------------------------------------------------------------------|
| Tên gian hàng đã có người dùng                    | `SHOP_NAME_TAKEN`                                       | Nộp, sửa khi chờ duyệt, nộp lại | Lỗi dưới ô tên gian hàng | "Tên gian hàng đã được sử dụng. Vui lòng chọn tên khác."                  |
| Số căn cước đã đăng ký cho hồ sơ khác             | `IDENTITY_ALREADY_REGISTERED`                           | Nộp, sửa khi chờ duyệt, nộp lại | Lỗi dưới ô số căn cước   | "Số căn cước này đã được đăng ký cho một hồ sơ khác."                     |
| Căn cước, mã số thuế sai (giao diện để lọt)       | `INVALID_IDENTITY_NUMBER`, `INVALID_TAX_CODE`           | Như trên   | Lỗi dưới ô tương ứng               | "Số căn cước không hợp lệ." / "Mã số thuế không hợp lệ."                  |
| Thiếu hoặc sai một ô (giao diện để lọt)           | `LEGAL_INFO_REQUIRED`, `SELLER_FIELD_INVALID` + tên ô   | Mọi việc   | Lỗi dưới ô đó; tên ô lạ thì dòng lỗi biểu mẫu | "<Nhãn ô> là bắt buộc." / "<Nhãn ô> không hợp lệ."              |
| Đã có hồ sơ (nộp ở tab khác)                      | `SELLER_PROFILE_EXISTS`                                 | Nộp        | Thông báo nổi, về trang chủ (Q6)   | "Trạng thái hồ sơ đã thay đổi. Đây là trạng thái hiện tại của hồ sơ bạn." |
| Hồ sơ vừa đổi trạng thái (được duyệt, bị từ chối, hoặc đã nộp lại ở tab khác) | `INVALID_STATE`             | Sửa khi chờ duyệt, nộp lại, sửa khi đã duyệt | Thông báo nổi, về trang xem hồ sơ (Q6) | Như trên                                  |
| Sửa đua nhau ở hai tab                            | `CONCURRENT_UPDATE`                                     | Mọi việc trừ nộp | Thông báo nổi, về trang xem hồ sơ (Q6) | "Hồ sơ vừa được thay đổi ở nơi khác. Đây là bản mới nhất."            |
| Đổi tên gian hàng sau khi duyệt (giao diện để lọt)| `SHOP_NAME_LOCKED` (mới, mục 7)                         | Sửa khi đã duyệt | Dòng lỗi biểu mẫu            | "Tên gian hàng đã khóa sau khi được duyệt."                               |
| Tài khoản nội bộ                                  | `SELLER_ROLE_NOT_ALLOWED`                               | Mọi việc   | Dòng lỗi biểu mẫu                  | "Tài khoản này không thể nộp hồ sơ người bán."                            |
| Hệ thống bận, mất mạng (chắc chắn chưa ghi)       | 5xx, 429, mạng                                          | Mọi việc   | Dòng lỗi biểu mẫu, mở khóa nút (Q4)| "Hệ thống đang bận hoặc mất kết nối. Dữ liệu của bạn vẫn được giữ, vui lòng thử lại sau ít phút." + mã truy vết |
| Không rõ đã ghi chưa                              | Hết giờ, 504                                            | Mọi việc   | Thông báo nổi, rời biểu mẫu (Q6)   | "Chưa xác nhận được kết quả. Đây là trạng thái hiện tại của hồ sơ bạn."   |
| Hết phiên                                         | 401                                                     | Mọi việc   | Không hiện                         | Đi đăng nhập                                                              |
| Mã lạ                                             | 4xx khác                                                | Mọi việc   | Dòng lỗi biểu mẫu, mở khóa nút     | "Không thể gửi hồ sơ. Vui lòng kiểm tra lại thông tin hoặc thử lại sau."  |

Q6 rời biểu mẫu về đâu: nộp thì về trang chủ, vì có thể chưa có hồ sơ để xem; ba việc còn lại về trang xem hồ sơ.

## 6. Không làm

| Dòng                                                         | Lý do                                                                                      |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Đánh dấu cho quản trị biết phần nào vừa sửa                  | Máy chủ không giữ bản cũ để so                                                             |
| Giữ dữ liệu biểu mẫu khi hết phiên giữa chừng                | Hiếm (phiên sống 7 ngày); chữa đúng cần lưu bản nháp                                       |
| Báo nhiều lỗi máy chủ cùng lúc                               | Máy chủ báo một ô mỗi lần (`errors[0]`); giao diện đã kiểm trước nên lỗi máy chủ hiếm       |
| Đổi tên gian hàng sau khi duyệt (qua duyệt lại)              | Cần luồng duyệt riêng cho thay đổi; làm khi có nhu cầu người bán thật                      |
| Giấy tờ đính kèm hồ sơ                                       | Ngoài phạm vi C1a                                                                          |

## 7. Việc phía backend

| #   | Việc                                                                                                                                                                                  |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| B1  | Thêm `PUT /api/seller/profile`: chỉ khi chờ duyệt; thay toàn bộ hồ sơ trong một giao dịch. Kiểm như nộp lại: pháp lý theo loại hình, tính lại khóa định danh, trùng định danh, trùng tên gian hàng. Không đổi trạng thái, không phát `SellerProfileSubmitted`. Không đổi gì thì trả hiện trạng, không tăng phiên bản |
| B2  | Bỏ `PATCH /api/seller/profile/legal-info`, use case `UpdateLegalInfo` và test của nó. `PUT` thay chỗ của nó. Kiểm không còn nơi nào gọi trước khi bỏ |
| B3  | `PATCH /api/seller/profile/shop-info` chỉ mở khi đã duyệt (bỏ chờ duyệt, vì đã có `PUT`). Đổi `shopName` thì trả 409 `SHOP_NAME_LOCKED`. Chờ người chốt việc khóa tên (mục 3) |
| B4  | Cập nhật `api.yaml`, `service.md`, `analysis.md` của `seller-service` theo B1 đến B3; bỏ `LEGAL_INFO_LOCKED` nếu không còn lệnh nào dùng |

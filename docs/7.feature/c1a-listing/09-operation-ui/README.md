# Feature 12. Ứng dụng Quản trị

> Bản thử bộ tài liệu feature mới. Chặng: C1a. Số 12 là số ở bản chia cũ (`stage/c1a-listing/00-features.md`); ở bản chia lại
> (`2.roadmap/c1a-listing.md`) đây là feature 9.

## 1. Feature này làm gì

Dựng ứng dụng web Quản trị (`ops-web`) cho vai `ADMIN`, chạy ở `ops.localhost`, đi qua `ops-web-bff` đã có. Quản trị làm được
qua giao diện ba việc mà hiện chỉ gọi được bằng API:

- duyệt hoặc từ chối hồ sơ người bán;
- quản lý danh mục, mẫu thuộc tính, thương hiệu;
- tra cứu nhật ký hành động quản trị.

Backend của cả ba đã xong (feature 02, 04, 05 của C1a). Feature này chủ yếu là giao diện, kèm vài chỗ phải sửa backend ghi ở mục 4.

## 2. Chia phần

Mỗi phần là một thư mục, mỗi thư mục có ba tài liệu:

| Tài liệu    | Trả lời câu hỏi                                                                              |
|-------------|----------------------------------------------------------------------------------------------|
| `srs.md`    | Người dùng làm được gì, quy tắc nghiệp vụ nào phải giữ, lỗi nào người dùng phải hiểu được    |
| `design.md` | Màn hình trông thế nào, bấm gì thì giao diện làm gì, gọi API nào, mã đặt ở đâu               |
| `test.md`   | Những ca trọng yếu phải kiểm, ca nào bảo vệ quy tắc hay rủi ro nào                           |

| #   | Phần                                         | Nội dung                                                                  | Cần trước | Trạng thái |
|-----|----------------------------------------------|---------------------------------------------------------------------------|-----------|------------|
| 0   | [Khung ứng dụng](0-app-shell/)               | Dự án `ops-web`, đăng nhập, chặn vai, bố cục chung, cách hiện lỗi chung   |           | Viết thử   |
| 1   | [Duyệt người bán](1-seller-review/)          | Hàng chờ duyệt, xem hồ sơ, duyệt, từ chối kèm lý do                       | 0         | Viết thử   |
| 2   | Cấu trúc danh mục                            | Cây danh mục, mẫu thuộc tính và tùy chọn, thương hiệu                     | 0         | Chưa viết  |
| 3   | Nhật ký quản trị                             | Danh sách có lọc, lật trang theo con trỏ, xem chi tiết                    | 0         | Chưa viết  |

Phần 2 lớn (khoảng 25 điểm cuối API), khi viết có thể tách tiếp thành ba thư mục con: danh mục, mẫu thuộc tính, thương hiệu.

Thứ tự làm: 0 → 1 → 3 → 2. Nhật ký làm trước danh mục vì nhỏ, và có nó thì mỗi thao tác của phần 2 kiểm được tới tận nhật ký
ngay trên giao diện.

## 3. Bài toán frontend của feature

Theo `1.requirement/6-ui-analysis.md`:

- **5.2 Gửi trùng và thử lại an toàn:** quyết định duyệt khi mạng rớt giữa chừng (phần 1).
- **5.7 Biểu mẫu, lỗi máy chủ gắn vào ô:** lý do từ chối (phần 1), các biểu mẫu danh mục (phần 2).
- **5.8 Danh sách, trạng thái trên URL:** hàng chờ duyệt (phần 1), nhật ký với con trỏ (phần 3).
- **5.5 Nhiều ứng dụng dùng chung nền:** ứng dụng thứ ba dùng lại `@t3n/auth`, `@t3n/http`, `@t3n/model` (phần 0).

## 4. Việc phía backend phát hiện khi phân tích

| Việc                                                                                                                                                                  | Phần | Ghi chú                                                                                                   |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|------|-----------------------------------------------------------------------------------------------------------|
| `audit-service` chưa kiểm token và vai (hoãn từ feature 02). `ops-web-bff` không chặn vai, nên tài khoản `USER` đăng nhập ở `ops.localhost` gọi được nhật ký          | 3    | Phải làm trước phần 3; nhật ký có tên hành động và mã tài khoản của quản trị                              |
| `ErrorInfo` của `@t3n/http` không giữ `traceId`, trong khi sàn chung (6-ui mục 6) đòi hiện mã truy vết cho lỗi hệ thống                                                | 0    | Sửa thư viện dùng chung; cả `storefront-web` và `seller-web` được lợi                                      |
| 6-ui mục 3.2 nói "không ai ngoài quản trị tải được mã của ứng dụng này"; hiện mã tĩnh phục vụ cho mọi người                                                            | 0    | Không làm ở feature này; ghi vào mục "không làm" của `0-app-shell/srs.md`                                 |

## 5. Không có tài liệu domain, DB, endpoint

Ứng dụng giao diện không có domain hay dữ liệu riêng. Hợp đồng API nó dùng là `api.yaml` của `seller-service`,
`catalog-service`, `audit-service`; giao diện chỉ đọc, không là nguồn.

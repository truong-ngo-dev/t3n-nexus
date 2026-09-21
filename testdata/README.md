# Test Data — HTTP request collections

Bộ request REST dùng để seed/test tay từng service, viết theo định dạng `.http` (không phải script tự
chạy) — mở file, đọc từng block, chạy hoặc copy ra dùng tuỳ ý.

## Format

`.http` là cú pháp chuẩn của **VS Code REST Client** (extension `humao.rest-client`) và **IntelliJ HTTP
Client** (built-in, không cần cài gì thêm khi mở project trong IntelliJ/WebStorm) — cả 2 đọc chung 1
cú pháp, không cần chọn tool trước.

- Mỗi request cách nhau bằng dòng `###`.
- Có nút ▶ **Send Request** ngay trên mỗi block khi mở trong IDE có hỗ trợ — không cần chạy cả file.
- Không có tool hỗ trợ vẫn đọc được bình thường (text thuần) — copy phần method/URL/header/body ra tự
  chuyển thành `curl` tay nếu cần.
- `@name` đặt tên 1 request để request sau tham chiếu response của nó qua
  `{{tenRequest.response.body.$.<jsonpath>}}` — id tạo ra ở request trước tự động điền vào request sau,
  không cần copy tay.
- `@baseUrl`/`@oauthUrl` khai ở đầu file — sửa 1 chỗ nếu đổi port/host.

## Cách chạy 1 file

1. Mở file `.http` trong VS Code (cài extension `REST Client`) hoặc IntelliJ/WebStorm (có sẵn).
2. Chạy request `### 0. Lấy token` trước tiên — mọi request sau tham chiếu
   `{{auth.response.body.$.access_token}}` của chính response này.
3. Chạy tuần tự từ trên xuống (nếu request sau cần id do request trước tạo ra, IDE tự gọi lại request
   trước nếu chưa chạy, hoặc bạn tự chạy tay theo thứ tự).
4. Muốn test lại từ đầu (id mới) — chạy lại từ bước 0, không cần sửa gì thủ công.

## Token dùng để test

Cả 2 file đều mượn tạm client nội bộ `oauth2-service-internal` (`client_credentials`, đã seed sẵn ở
`services/oauth2-service/src/main/resources/db/migration/V10__seed_internal_service_client.sql`) — dùng
được cho test vì các service này hiện chỉ check "JWT hợp lệ" (`anyRequest().authenticated()`), chưa phân
role Admin/Seller riêng. TTL token 300s — hết hạn thì chạy lại request `auth`.

## File trong thư mục này

| File | Service | Nội dung |
|---|---|---|
| `scheduler-service.http` | scheduler-service (8009) | Create/Start/List (filter)/Detail/Edit/Stop job ONE_OFF + RECURRING, 1 nhóm test lỗi (422/400/404), + section **Lookup** (list không filter, detail theo id thật) |
| `catalog-service.http` | catalog-service (8005) | Seed Brand → Category (L1+L2) → 2 AttributeTemplate (`color` GLOBAL + `storage` CATEGORY, +options) → gán `storage` vào category (`color` không gán được — GLOBAL tự áp dụng mọi category) → Product (dùng cả 2 attribute) → Variant (chỉ theo `storage`, attribute variantDefining) → Publish, verify qua endpoint public, + section **Lookup** |

## Section "Lookup"

Mỗi file có 1 khối cuối tên **Lookup / Danh sách**, tách riêng khỏi phần tạo mới ở trên — dùng khi đã có
sẵn data từ lần chạy trước (đổi máy, mất biến `{{createXxx...}}` của session cũ) và chỉ cần **tìm lại id
thật** để truyền vào API khác, không cần tạo lại từ đầu. Chạy request list trước, copy id ra khỏi
response, paste tay vào chỗ cần (thay `{{createXxx.response.body...}}` hoặc `<id>` placeholder).

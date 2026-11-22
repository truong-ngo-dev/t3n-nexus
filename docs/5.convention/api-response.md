# Quy ước phản hồi API

Quy ước cho mọi dịch vụ có API JSON. Trả lời: thân phản hồi có hình dạng gì, khi nào dùng mã trạng thái HTTP và khi nào dùng `code`,
lỗi kèm chi tiết ra sao, và client được phép đọc gì. Cài đặt dùng chung là `ApiResponse` trong `common-web`; log của lỗi xem
`logging.md`.

## 1. Nguyên tắc

1. **Mã trạng thái HTTP là kết quả của yêu cầu.** Thân phản hồi không lặp lại kết quả (không có `success`, không có `status`), và client không đọc thân để quyết định thành công: 2xx là thành công, mọi trạng thái khác là lỗi. Client chỉ đọc `data` khi nó dùng, và đọc `code` để rẽ nhánh khi lỗi.
2. **`code` là lý do cụ thể**, chuỗi ổn định, để client rẽ nhánh và tự đặt chữ hiển thị. Không dùng số.
3. **Chữ hiển thị cho người dùng do giao diện tự đặt theo `code`.** Client không đọc `message` của máy chủ để quyết định hay hiển thị
   (chữ có thể đổi và có thể lộ chi tiết nội bộ).
4. **Chỉ gửi những gì có.** Trường không có giá trị bị bỏ khỏi JSON, không gửi `null`.

## 2. Hình dạng

| Trường | Khi nào có | Nội dung |
|---|---|---|
| `code` | Luôn | `OK` khi thành công, ngược lại là mã lỗi (mục 4) |
| `message` | Chỉ lỗi | Một câu ngắn mô tả lỗi, tiếng Anh, cho người đọc log (mục 5) |
| `traceId` | Luôn | Mã truy vết của yêu cầu, để người dùng đọc lại cho bên rà log |
| `timestamp` | Chỉ lỗi | Thời điểm máy chủ sinh lỗi, ISO-8601 UTC (ví dụ `2026-11-21T03:14:07Z`) |
| `data` | Chỉ thành công | Tài nguyên trả về; danh sách phân trang nằm trong `data` (mục 7) |
| `error` | Chỉ lỗi cần chi tiết | Chi tiết của lỗi; một trong ba dạng ở mục 6 |

`data` và `error` loại trừ nhau.

Thành công:

```json
{ "code": "OK", "traceId": "4bf92f35", "data": { "profileId": "…", "status": "PENDING" } }
```

Lỗi không cần chi tiết (HTTP 409):

```json
{ "code": "SHOP_NAME_TAKEN", "message": "Shop name is already in use", "traceId": "4bf92f35", "timestamp": "2026-11-21T03:14:07Z" }
```

Lỗi nhiều trường (HTTP 400):

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "traceId": "4bf92f35",
  "timestamp": "2026-11-21T03:14:07Z",
  "error": [
    { "field": "shopName", "code": "TOO_SHORT", "params": { "min": 3 } },
    { "field": "contactPhone", "code": "INVALID_FORMAT" }
  ]
}
```

## 3. Mã trạng thái HTTP

Chọn theo bản chất lỗi; `code` nói rõ lý do bên trong cùng một trạng thái.

| Trạng thái | Dùng khi |
|---|---|
| 200, 201 | Thành công; 201 khi tạo mới |
| 400 | Nội dung hoặc tham số sai (kiểm hợp lệ, JSON hỏng, sai kiểu) |
| 401 | Chưa xác thực |
| 403 | Đã xác thực nhưng bị từ chối, và lỗi CSRF |
| 404 | Không có tài nguyên |
| 409 | Xung đột với trạng thái hiện tại (trùng, sai trạng thái) |
| 429 | Vượt giới hạn tần suất; kèm header `Retry-After` |
| 500 | Lỗi hệ thống; thông điệp chung chung, chi tiết chỉ có trong log |
| 502, 503, 504 | Dịch vụ phía sau không sẵn sàng |

Không bao giờ trả 200 kèm thân lỗi.

## 4. Mã `code`

- `UPPER_SNAKE`, tiếng Anh, tối đa 63 ký tự; không đổi nghĩa sau khi đã công bố (xem `naming.md`).
- Mã nghiệp vụ do từng service định nghĩa và liệt kê ở `api.yaml` của service đó.
- Mã chung do `common-web` sinh, mọi service dùng cùng một bộ:

| Mã | Khi nào |
|---|---|
| `OK` | Thành công |
| `VALIDATION_FAILED` | Kiểm hợp lệ thất bại; `error` liệt kê từng trường |
| `MALFORMED_REQUEST` | Nội dung không đọc được (JSON hỏng) |
| `INVALID_PARAMETER` | Tham số đường dẫn hoặc truy vấn sai kiểu; `message` chỉ nêu tên tham số, không lặp lại giá trị |
| `HTTP_<mã>` | Lỗi framework có sẵn mã trạng thái (405, 415…) |
| `INTERNAL_ERROR` | Lỗi hệ thống không lường trước |

## 5. `message` và `timestamp`

- `message` ngắn, một câu, dành cho người đọc log, không đưa vào giao diện. Không chứa giá trị biến đổi (đặt vào `params`), tên bảng,
  stack trace hay dữ liệu người dùng nhập. Lỗi 500 chỉ ghi chung chung.
- `timestamp` chỉ ở lỗi, sinh từ `Instant` ở máy chủ. Thành công không có để thân phản hồi không khác nhau giữa các lần gọi giống nhau.

## 6. `error`

`error` mang chi tiết không nằm gọn trong một `code`. Có ba dạng; người viết handler chọn dạng phù hợp và chỉ gán qua hàm tạo của
`ApiResponse`.

| Dạng | Khi dùng | Ví dụ |
|---|---|---|
| Mảng chi tiết | Lỗi gắn với ô nhập, có thể nhiều ô | `[ { "field": "shopName", "code": "TOO_SHORT", "params": { "min": 3 } } ]` |
| Chuỗi | Một ghi chú ngắn không cần cấu trúc | `"Profile is awaiting review"` |
| Đối tượng | Ngữ cảnh riêng của lỗi | `{ "currentStatus": "APPROVED" }` |

Phần tử của mảng chi tiết: `code` bắt buộc (cùng quy tắc mục 4); `field` có khi lỗi gắn với một ô; `params` là giá trị biến đổi để client
ghép vào câu của mình, không nhúng vào chữ.

Không đưa vào `error`: ngoại lệ (`Throwable`), thực thể, hay bất kỳ dữ liệu nội bộ nào. Client đọc `error` theo kiểu; kiểu lạ hoặc sai
cấu trúc thì coi như vắng mặt, không ném lỗi.

## 7. Trường hợp riêng

- **Danh sách phân trang:** `data` có dạng `{ "items": [...], "page": 0, "size": 20, "total": 134 }`. Không có trường siêu dữ liệu riêng
  ở ngoài `data`.
- **Tệp (xuất Excel, tải xuống):** thành công trả thẳng nội dung tệp, thông tin về tệp nằm ở header (`Content-Type`, `Content-Disposition`),
  không có thân JSON. Lỗi của cùng endpoint vẫn trả thân JSON như mục 2 kèm mã trạng thái. Client yêu cầu dạng blob phải đọc thân lỗi
  thành JSON trước khi dịch.
- **Header kèm theo:** `X-Trace-Id` ở mọi phản hồi (cùng giá trị với `traceId` ở thân); `Retry-After` ở 429.

## 8. Lý do và phương án đã loại

| Phương án | Vì sao loại |
|---|---|
| Trường `success` hoặc `status` trong thân | Trùng mã trạng thái HTTP; hai nơi cùng nói một việc thì sớm muộn lệch nhau (200 kèm `success:false`) |
| Mã lỗi dạng số | Phải có bảng tra, dễ trùng giữa các service, không đọc được trong log |
| `error` là đối tượng cố định cho mọi lỗi (như Stripe, Google) | Có lỗi chỉ cần một chuỗi, có lỗi cần mảng; chấp nhận cho `error` đổi dạng, đổi lại client phải đọc theo kiểu |
| Trường `meta` ngoài `data` | Phản hồi tệp không có thân JSON nên siêu dữ liệu phải ở header; phân trang là hình dạng của tài nguyên nên nằm trong `data` |
| `message` làm chữ hiển thị | Chữ đổi theo ngôn ngữ và có thể lộ chi tiết; giao diện tự đặt chữ theo `code` |
| Chuẩn RFC 9457 nguyên bản (`type`, `title`, `instance`) | Cần thêm `code`, `traceId`, chi tiết theo trường nên vẫn phải mở rộng; hình dạng ở trên đã gần với các nguồn (Stripe `code` và `param`, Google `reason` và `metadata`) |

## 9. Tài liệu liên quan

- `logging.md` mục về phản hồi lỗi: mức log theo loại lỗi, `error.code` ở dòng hoàn tất.
- `naming.md`: quy tắc đặt mã lỗi.
- `service/{name}/api.yaml`: mã nghiệp vụ và hình dạng cụ thể của từng endpoint.

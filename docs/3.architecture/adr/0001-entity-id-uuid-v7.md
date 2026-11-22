# ADR-0001: Định dạng mã định danh thực thể

> **Trạng thái:** Accepted · **Ngày:** `2026-10-08` · **Phạm vi:** mọi BC

## Bối cảnh

Mọi thực thể cần một mã. Mã làm khóa chính trong BC sở hữu và là khóa tham chiếu giữa các BC (đơn thuộc về tài khoản X, sản
phẩm thuộc về tài khoản X). Miền cần có mã ngay lúc tạo aggregate, để event mang được mã mà không phải đọc lại từ cơ sở
dữ liệu. Quy mô thiết kế: ~3 triệu tài khoản, ~18 triệu đơn, hàng chục triệu dòng lịch sử tham chiếu mã tài khoản (NFR 3.4).

## Quyết định

Dùng **UUID phiên bản 7** (RFC 9562).

- **Sinh ở ứng dụng, trong miền.** Aggregate nhận mã lúc tạo qua một cổng sinh mã (`IdGenerator`) do miền khai báo. Cài đặt
  ở hạ tầng, đặt trong thư viện dùng chung của dự án để mọi BC cùng một định dạng. Miền không gọi thẳng thư viện, đồng hồ
  hay bộ sinh số ngẫu nhiên.
- **Lưu bằng kiểu `uuid`** của cơ sở dữ liệu (16 byte), không lưu dạng chuỗi.
- **Value object theo thực thể:** mỗi BC có kiểu mã riêng cho thực thể của mình (ví dụ `UserAccountId`), cùng bọc một UUID, để
  không nhầm mã thực thể này với mã thực thể khác.
- **Bộ sinh đơn điệu trong một thể hiện:** cùng một mili-giây thì mã vẫn tăng dần.
- **Không dựa vào thứ tự mã để bảo đảm tính đúng.** Nhiều thể hiện sinh song song và đồng hồ có thể lệch nên thứ tự chỉ gần
  đúng. Cần thứ tự chính xác thì dùng cột thời điểm riêng.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| ULID lưu `CHAR(26)` | Tốn thêm ~11 byte mỗi dòng và ~16 byte mỗi mục chỉ mục (ước tính), phụ thuộc collation khi so sánh và sắp xếp, công cụ ít hỗ trợ |
| ULID lưu thành `uuid` 16 byte | Gọn như UUID v7 nhưng phải đổi qua lại ở biên API, và chuỗi hiển thị trong công cụ không trùng chuỗi ULID |
| UUID v4 | Ngẫu nhiên hoàn toàn: chèn rải khắp chỉ mục, nhiều tách trang, nhiều nhật ký ghi hơn, cục bộ bộ nhớ kém |
| Hàm sinh trong cơ sở dữ liệu (như `uuidv7()` từ PostgreSQL 18) | Miền không có mã lúc tạo aggregate; buộc phụ thuộc phiên bản cơ sở dữ liệu |
| Số nguyên tự tăng của cơ sở dữ liệu | Cần đi vòng tới cơ sở dữ liệu để có mã; lộ số lượng bản ghi; không dùng được làm khóa tham chiếu giữa BC mà không cần điều phối |
| Mã 64 bit kiểu Snowflake | Cần phân phối và quản lý mã máy sinh; thêm một thành phần phối hợp không cần thiết |

## Lý do

- **Ở tầng cơ sở dữ liệu,** kiểu `uuid` chỉ chiếm 16 byte, so sánh theo byte, không dính collation. Phần đầu mã là thời gian
  nên bản ghi mới nằm ở các trang bên phải của chỉ mục B-tree: ít tách trang, chỉ mục chặt hơn, ít nhật ký ghi hơn UUID v4.
- **Ở tầng miền,** sinh ở ứng dụng cho aggregate mã ngay lúc tạo, event mang được mã, gửi lại cùng một yêu cầu dễ xử lý,
  và không phụ thuộc phiên bản cơ sở dữ liệu.
- **Chuẩn chính thức** nên công cụ, thư viện, mô tả API (`format: uuid`) hiểu sẵn.

## Hệ quả

- Mã lộ thời điểm tạo (mili-giây). Không dùng quy ước này cho thứ cần mã không đoán được.
- Phải chọn một thư viện sinh UUID v7 hoặc tự cài đặt sau cổng sinh mã; JDK chỉ sinh UUID v4 sẵn.
- Dạng chữ dài 36 ký tự có gạch nối.
- Test miền tiêm bộ sinh và đồng hồ cố định.
- Phiên bản PostgreSQL không bị ràng buộc: kiểu `uuid` có sẵn từ lâu. Nâng lên bản có `uuidv7()` chỉ thêm tiện ích khi
  tạo dữ liệu trực tiếp bằng SQL.

## Tham khảo

- RFC 9562, Universally Unique IDentifiers (UUIDs): <https://www.rfc-editor.org/rfc/rfc9562>
- PostgreSQL 18, hàm `uuidv7()`: <https://thenile.dev/blog/uuidv7>

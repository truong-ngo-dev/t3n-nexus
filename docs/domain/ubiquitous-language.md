# Ubiquitous Language — t3n-nexus

Glossary định nghĩa thuật ngữ theo từng Bounded Context.
Khi cùng một từ có nghĩa khác nhau giữa các BC, định nghĩa riêng theo từng BC.

---

## Global Terms

| Thuật ngữ  | Định nghĩa                                                              |
|------------|-------------------------------------------------------------------------|
| `Seller`   | Người bán hàng trên platform — phải được approved trước khi listing     |
| `Customer` | Người mua hàng — có account, có loyalty points                          |
| `Shipper`  | Người giao hàng — đăng ký thủ công, thuộc pool của Fulfillment BC       |
| `Admin`    | Quản trị viên platform                                                  |
| `SKU`      | Stock Keeping Unit — đơn vị quản lý tồn kho, gắn với một variant cụ thể |
| `Order`    | Một giao dịch mua hàng — thuộc đúng 1 seller                            |
| `Saga`     | Chuỗi transactions phân tán có compensating actions                     |

---

## Catalog BC

| Thuật ngữ   | Định nghĩa                                                     |
|-------------|----------------------------------------------------------------|
| `Product`   | Aggregate root — thông tin sản phẩm do seller tạo              |
| `Variant`   | Biến thể của Product (màu, size...) — mỗi Variant có 1 SKU     |
| `Category`  | Phân loại sản phẩm — tree structure                            |
| `Warranty`  | Attribute của Product — thời hạn bảo hành, không phải BC riêng |
| `Published` | Trạng thái Product visible trên storefront                     |

---

## Inventory BC

| Thuật ngữ       | Định nghĩa                                |
|-----------------|-------------------------------------------|
| `Stock`         | Số lượng tồn kho của một SKU              |
| `Reservation`   | Số lượng tạm giữ cho một Order đang xử lý |
| `Available Qty` | Stock - Reservation                       |

---

## Cart BC

| Thuật ngữ    | Định nghĩa                                                |
|--------------|-----------------------------------------------------------|
| `Guest Cart` | Cart của user chưa login — lưu Redis bằng session token   |
| `Cart Merge` | Cộng dồn qty khi guest cart login vào account có sẵn cart |

---

## Order BC

| Thuật ngữ   | Định nghĩa                                                               |
|-------------|--------------------------------------------------------------------------|
| `Order`     | Aggregate — vòng đời từ CREATED đến COMPLETED/CANCELLED                  |
| `Line Item` | Một sản phẩm trong order — `skuId`, `qty`, `unitPrice` tại thời điểm đặt |
| `Prepaid`   | Thanh toán trước — áp dụng auto-cancel nếu chưa thanh toán               |
| `COD`       | Cash on Delivery — không áp dụng auto-cancel                             |

---

## Payment BC

| Thuật ngữ         | Định nghĩa                                            |
|-------------------|-------------------------------------------------------|
| `Payment`         | Aggregate — một giao dịch thanh toán cho một Order    |
| `Refund`          | Hoàn tiền — triggered bởi Return BC hoặc cancellation |
| `Payout`          | Chuyển tiền cho Seller — theo chu kỳ                  |
| `Idempotency Key` | Key để dedup — bắt buộc cho mọi payment operation     |

---

## Fulfillment BC

| Thuật ngữ    | Định nghĩa                                           |
|--------------|------------------------------------------------------|
| `Shipment`   | Aggregate — quá trình giao một Order                 |
| `Assignment` | Gán Shipper cho Shipment — do Rule Engine quyết định |
| `POD`        | Proof of Delivery — ảnh/chữ ký xác nhận giao hàng    |
| `Zone`       | Khu vực hoạt động của Shipper                        |

---

## Customer BC

| Thuật ngữ        | Định nghĩa                                   |
|------------------|----------------------------------------------|
| `Loyalty Points` | Điểm tích lũy — có expiration, không có tier |
| `Points Ledger`  | Lịch sử tích/trừ điểm — append-only          |
| `Points Entry`   | Một bản ghi trong ledger — có `expireAt`     |

---

## IAM (oauth2-service + identity-service)

| Thuật ngữ    | Định nghĩa                                                         |
|--------------|--------------------------------------------------------------------|
| `Session`    | Phiên đăng nhập — quản lý bởi oauth2-service                       |
| `Device`     | Thiết bị đã login — user có thể revoke                             |
| `UserStatus` | `ACTIVE` \| `LOCKED` \| `PENDING` — quản lý bởi identity-service   |
| `ABAC`       | Attribute-Based Access Control — policy evaluation tại mỗi service |

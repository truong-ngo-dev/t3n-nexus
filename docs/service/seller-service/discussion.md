# Seller — thảo luận về loại seller, tồn kho và vận chuyển

> **Trạng thái:** thảo luận, chưa phải `analysis.md` · **Cập nhật:** `2026-10-02`
>
> Ghi lại những gì đã bàn khi làm lõi catalog và inventory, làm đầu vào cho analysis của seller, inventory,
> warehouse, fulfillment. Phần "thống nhất sơ bộ" là hướng đang nghiêng tới, chưa xác nhận hết; phần "chưa chốt" là
> câu hỏi mở.

## 1. Bối cảnh

Docs hiện chỉ có mô tả ngắn về Seller BC (`bounded-contexts.md`): onboarding bằng Temporal, hạng seller ảnh hưởng hoa
hồng ở Pricing, chỉ số hiệu suất. Chưa có service. Câu hỏi nảy ra từ catalog và inventory: **ai nhập hàng, hàng nằm
đâu, ai đóng gói và giao**, và điều đó phụ thuộc loại seller.

## 2. Thống nhất sơ bộ

- Có **hai loại seller**: **thông thường** và **đối tác được sàn ủy quyền** (gồm cả chính sàn tự bán, `chưa xác nhận`).
- **Vận chuyển luôn đi qua sàn** ở cả hai loại.
- **Chế độ tồn kho và giao nhận do loại seller quyết định**, không chọn theo từng SKU. Mỗi đơn một seller nên tự động
  một chế độ, không phải chia đơn.
- Catalog **không quan tâm giá niêm yết ngoài một giá trị, không quan tâm tồn kho và vận chuyển**.

| | Seller thông thường | Đối tác |
|---|---|---|
| Tạo sản phẩm, đơn vị bán được | Tự tạo | Tự tạo hoặc sàn tạo giúp (`chưa chốt`) |
| Số lượng tồn | **Tự nhập, tự quản** | **Không có thao tác đặt số lượng**; bắt buộc nhập hàng qua Warehouse, Warehouse đồng bộ sang Inventory |
| Hàng nằm ở đâu | Kho seller | Kho sàn |
| Khi có đơn | Seller đóng gói và **tự mang hàng ra điểm của sàn** (điểm trung chuyển) | Kho lấy hàng, đóng gói, đặt ra cửa xuất |
| Sau khi hàng ở điểm của sàn | Phân công shipper, vận chuyển, giao (**giống nhau**) | Như bên trái |
| Hoàn hàng | Về địa chỉ seller | Về kho sàn |

## 3. Bốn câu hỏi cần tách riêng

Thường bị gộp thành "loại seller" nhưng độc lập nhau, và mỗi cái do một BC quan tâm:

| Câu hỏi | Giá trị | BC quan tâm |
|---|---|---|
| Người bán trên hồ sơ | seller, sàn, đối tác | Seller, Payment |
| Quyền sở hữu hàng | seller, sàn, đối tác | Payment |
| Nơi giữ hàng và nguồn tồn kho | kho seller, kho sàn | **Inventory** |
| Điểm lấy hàng | địa chỉ seller, điểm trung chuyển, cửa xuất kho | **Fulfillment** |

Inventory và Fulfillment không cần biết ai sở hữu hàng. Chỉ Payment cần.

## 4. Phân biệt hai loại "kho"

| | Điểm trung chuyển | Kho lưu trữ (Warehouse BC) |
|---|---|---|
| Chứa | Bưu kiện đang đi | Hàng tồn theo SKU |
| Ảnh hưởng tồn kho | Không | Có |
| Dùng bởi | Seller thông thường (mang hàng ra) | Đối tác |

## 5. Seller BC cần cung cấp gì cho BC khác

- **Loại seller**, để Inventory quyết định cho phép hay cấm thao tác đặt số lượng khi tạo `Stock`. Hiện `VariantCreated`
  chỉ mang `sellerId`, chưa mang loại seller.
- **Chính sách theo loại seller** (hoa hồng, hạn mức phạt, thời hạn bàn giao): do Seller và Pricing giữ, không viết
  cứng trong luồng huỷ đơn hay vận chuyển.
- Một cách mô hình hoá khác (chưa chọn): coi **ủy quyền là tập quyền được cấp** (gửi hàng vào kho, đẩy số tồn qua giao
  diện lập trình, nhập hàng loạt, huy hiệu chính hãng), loại seller chỉ lo hợp đồng. Chỉ cần nếu sau này có seller
  thông thường được vào kho.

## 6. Chưa chốt

1. Seller thông thường **chỉ tự mang hàng ra**, hay còn chế độ shipper đến lấy?
2. Chính sàn tự bán có xếp vào loại **đối tác** không?
3. Hỗ trợ **nâng cấp** thông thường lên đối tác (hàng đang ở seller phải nhập vào kho theo phiếu, số tự khai được đưa
   về 0 để không đếm đôi), hay coi là thao tác thủ công ngoài hệ thống?
4. **Hạn bàn giao** của seller thông thường và mức phạt khi quá hạn. Đây là bộ đếm giờ mới (quá hạn thì huỷ đơn, trả
   giữ chỗ), khác với giữ chỗ 5 phút của thanh toán; cần phối hợp với saga.
5. Ai tạo sản phẩm cho đối tác và cho chính sàn, có cần nhập hàng loạt không.
6. Các loại **ký gửi** (hàng của đối tác nằm ở kho sàn, tiền thuộc đối tác) và **đối tác đẩy số tồn từ hệ thống riêng**
   hiện nằm ngoài phạm vi vì đối tác bắt buộc đi qua Warehouse.
7. Hoàn hàng cần tách hai đường theo loại seller (Return BC).

## 7. Việc phát sinh ở BC khác

| BC | Việc |
|---|---|
| **Inventory** | Analysis nhập hàng: sổ biến động có nguồn và mã tham chiếu; chặn thao tác đặt số lượng với `Stock` có nguồn là kho; tách "chốt giữ chỗ" khỏi "trừ hàng thật" |
| **Warehouse** (Phase 2) | Chính sàn tự bán là khách hàng đầu tiên hợp lý: không cần quy trình seller gửi hàng và xác minh, tập trung học Event Sourcing và đối soát |
| **Fulfillment** | Thêm bước chờ bàn giao trước `ASSIGNED`; khái niệm chung "bưu kiện có mặt tại điểm của sàn" làm điểm hội tụ cho hai luồng |
| **Return** | Hai đường hoàn hàng |
| **Catalog** | Nên chốt `TQ-01` (khoá category sau khi có đơn vị bán được); không đổi gì khác |

## 8. Tham khảo

Các nguồn dưới đây là bài viết của bên thứ ba hoặc trang hướng dẫn mà tôi mới đọc bản tóm tắt tìm kiếm:
- [Học viện Tiki: mô hình lưu kho FBT](https://hocvien.tiki.vn/faq/gioi-thieu-mo-hinh-luu-kho-tiki-va-quy-trinh-xu-ly-don-hang/)
- [Shopee Việt Nam: phương thức vận chuyển](https://banhang.shopee.vn/edu/article/7412)
- [Fulfilled by Shopee: hướng dẫn cho người bán](https://deo.shopeemobile.com/shopee/seller/seller_cms/8253441447fff07bf2f3abd669dcb1b3/%5BMY%5D%20Fulfilled%20by%20Shopee%20-%20Seller%20Guide.pdf)
- [Amazon FBA và FBM (ZonGuru)](https://www.zonguru.com/blog/amazon-fba-vs-fbm)
- `../catalog-service/analysis.md`, `../inventory-service/service.md`, `global/2.architecture/1. bounded-contexts.md`

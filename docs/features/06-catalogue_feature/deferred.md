# Deferred — catalogue_feature

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. Ownership check (ABAC) cho Product/Variant write endpoint

**Làm khi nào:** Sau khi Seller có auth/session thật (hiện chưa có Seller service/JWT role riêng).

**Vấn đề:**
`ProductController.createProduct()`/`listProducts()` lấy `sellerId` từ `@RequestHeader("X-Seller-Id")` — client tự set, không phải từ JWT claims. `updateProduct`, `publishProduct`, `unpublishProduct`, `getImageUploadUrl`, `confirmImageUpload`, `removeImage`, và toàn bộ `VariantController` không nhận `sellerId` dưới bất kỳ hình thức nào — không có bước verify `product.sellerId` khớp caller. Không có ABAC filter nào bù đắp ở tầng khác (grep "ABAC" toàn service: 0 kết quả).

**Trạng thái hiện tại:**
`X-Seller-Id` header là **placeholder tạm thời**, chấp nhận được vì Seller chưa có auth thật để lấy `sellerId` đáng tin cậy từ đâu khác. Không phải bug cần fix ngay — sẽ tự nhiên cần sửa lại khi Seller service/JWT role được xây.

**Cần làm khi Seller auth có thật:**
- Lấy `sellerId` từ `Authentication`/JWT claims, bỏ `X-Seller-Id` header
- Thêm ownership check (`product.sellerId == caller sellerId`) trong `UpdateProduct`, `PublishProduct`, `UnpublishProduct`, `GetProductImageUploadUrl`, `ConfirmProductImageUpload`, `RemoveProductImage`, và toàn bộ command handler của `VariantController`
- Cân nhắc dùng chung cơ chế ABAC đã có ở `security-architecture.md` (in-process, không network call) thay vì tự viết check rời rạc từng handler

**Files liên quan:**
- `catalog-service`: `presentation/product/ProductController.java`, `presentation/variant/VariantController.java`, toàn bộ command handler tương ứng trong `application/product/`, `application/variant/`

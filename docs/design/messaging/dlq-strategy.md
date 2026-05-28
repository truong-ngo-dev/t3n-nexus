# Thiết Kế Dead Letter Queue — t3n-nexus

> Tài liệu này phân tích vấn đề xử lý thất bại trong hệ thống Kafka consumer,
> đưa ra framework đánh giá, và quyết định cụ thể cho từng service trong hệ thống.

---

## 1. Vấn Đề Cần Giải Quyết

Trong hệ thống này, các service giao tiếp với nhau thông qua Kafka — một hệ thống hàng đợi phân tán.
Mỗi service đóng vai trò **consumer** (người đọc) cho một hoặc nhiều luồng dữ liệu (gọi là _topic_).
Khi một consumer đọc một message và xử lý thất bại, câu hỏi đặt ra là: message đó sẽ đi về đâu?

### Hành vi mặc định khi không có DLQ

Với cấu hình `MANUAL_IMMEDIATE` (consumer tự xác nhận đã đọc thành công), nếu xử lý thất bại
và không xác nhận, Kafka sẽ giao lại message đó liên tục — dẫn đến:

```
[msg A] [msg B] [msg C] [msg D]
           ↑
           consumer thất bại, không xác nhận
           → Kafka giao lại msg B vô tận
           → msg C, msg D không bao giờ được đọc
           → toàn bộ luồng xử lý bị CHẶN tại đây
```

Kết quả là một message "hỏng" duy nhất có thể làm liệt hoàn toàn một service.

### DLQ là gì và giải quyết vấn đề này như thế nào

**Dead Letter Queue (DLQ)** — tạm dịch là _hàng đợi thư chết_ — là một topic riêng biệt dùng để
chứa những message mà consumer đã thử xử lý nhiều lần nhưng vẫn thất bại.

Thay vì để message "hỏng" chặn luồng xử lý, hệ thống sẽ:
1. Thử lại N lần với khoảng thời gian chờ giữa các lần
2. Nếu vẫn thất bại, đẩy message sang topic DLQ và **xác nhận đã đọc** message gốc
3. Luồng xử lý chính tiếp tục bình thường
4. Message trong DLQ được xử lý riêng (điều tra, sửa lỗi, phát lại)

---

## 2. Framework Phân Tích

Trước khi quyết định cần bao nhiêu DLQ topic và cấu hình như thế nào, cần trả lời bốn câu hỏi:

### 2.1 Thất bại thuộc loại nào?

Phân biệt hai loại thất bại là nền tảng của mọi quyết định:

**Thất bại tạm thời** — sẽ tự khắc phục nếu thử lại sau:
- Mất kết nối cơ sở dữ liệu (DB timeout)
- Dịch vụ phụ trợ tạm ngưng (Redis down, SMTP unavailable)
- Quá tải tức thời (rate limit hit)

**Thất bại vĩnh viễn** — thử lại bao nhiêu lần cũng không khác:
- Message có định dạng sai (không đọc được)
- Lỗi lập trình (NullPointerException do code sai)
- Dữ liệu nghiệp vụ không hợp lệ (địa chỉ email không tồn tại)

Điều quan trọng: _cấu hình thử lại chỉ có ý nghĩa với thất bại tạm thời_. Với thất bại vĩnh viễn,
thử lại 3 lần chỉ là lãng phí thời gian trước khi vào DLQ. Cần cấu hình để nhận dạng và
bỏ qua thử lại cho các loại lỗi này (`NotRetryableException`).

### 2.2 Hậu quả nghiệp vụ khi message vào DLQ là gì?

Đây là câu hỏi quan trọng nhất — quyết định mức độ ưu tiên xử lý DLQ.

| Mức độ           | Định nghĩa                                                                                                      |
|------------------|-----------------------------------------------------------------------------------------------------------------|
| **Nghiêm trọng** | Tính toàn vẹn dữ liệu bị phá vỡ hoặc một quy trình nghiệp vụ bị kẹt vĩnh viễn — hệ thống không tự phục hồi được |
| **Cao**          | Một tính năng quan trọng bị gián đoạn — người dùng bị ảnh hưởng rõ ràng nhưng hệ thống vẫn vận hành             |
| **Trung bình**   | Dữ liệu hiển thị bị lỗi thời — hệ thống vẫn chạy nhưng người dùng thấy thông tin không chính xác                |
| **Thấp**         | Người dùng không nhận được thông báo — nghiệp vụ cốt lõi không bị ảnh hưởng                                     |

### 2.3 Cần bao nhiêu DLQ topic?

Nguyên tắc phân tách: nếu **bất kỳ một** trong các yếu tố sau khác nhau giữa hai nhóm consumer,
nên tách thành DLQ riêng:

- Mức độ ưu tiên xử lý (SLA phát lại) khác nhau
- Cách phản ứng vận hành khác nhau (ai xử lý? làm gì?)
- Ngưỡng cảnh báo khác nhau

Nếu tất cả giống nhau → gom về một DLQ.

Bốn cấp độ chi tiết có thể chọn:

```
Cấp 1: 1 DLQ cho mỗi cặp (topic gốc × consumer)
        → rất nhiều topic, rất dễ trace nhưng phức tạp để quản lý

Cấp 2: 1 DLQ cho mỗi consumer group  ← điểm cân bằng phổ biến nhất
        → một service có nhiều consumer vẫn dùng chung 1 DLQ

Cấp 3: 1 DLQ cho mỗi service
        → phù hợp khi tất cả consumer của một service có cùng mức độ ưu tiên

Cấp 4: 1 DLQ toàn hệ thống
        → đơn giản nhất nhưng không thể phân loại hay cảnh báo riêng
```

### 2.4 Cách xử lý message trong DLQ như thế nào?

Có ba cách tiếp cận:

- **Thủ công**: Kỹ sư kiểm tra message, xác định nguyên nhân, phát lại thủ công. Phù hợp cho giai đoạn đầu.
- **Tự động**: Một service tự đọc DLQ, áp dụng backoff theo luỹ thừa, phát lại. Phức tạp hơn nhưng không cần can thiệp thủ công.
- **Hybrid**: Tự động phát lại tối đa N lần, sau đó cảnh báo kỹ sư.

---

## 3. Hiện Trạng — Toàn Bộ Consumer Trong Hệ Thống

Dựa trên Event Catalog, toàn bộ các luồng consumer trong hệ thống:

### Consumer nhóm Saga (quy trình phối hợp đa service)

> **Saga** là một chuỗi các bước nghiệp vụ phân tán qua nhiều service. Mỗi bước khi thành công
> phát ra một event để kích hoạt bước tiếp theo. Khi một bước thất bại, các bước trước đó
> cần được hoàn tác (compensating). Nếu bất kỳ consumer Saga nào vào DLQ mà không được
> phát lại, toàn bộ quy trình sẽ bị kẹt ở trạng thái trung gian vĩnh viễn.

| Consumer            | Đọc từ topic                     | Làm gì khi nhận được                             |
|---------------------|----------------------------------|--------------------------------------------------|
| order-service       | `inventory.reservation.created`  | Ghi nhận tồn kho đã đặt → gửi yêu cầu thanh toán |
| order-service       | `inventory.reservation.failed`   | Huỷ đơn hàng vì không đủ tồn kho                 |
| order-service       | `payment.payment.succeeded`      | Xác nhận đơn hàng → kích hoạt giao vận           |
| order-service       | `payment.payment.failed`         | Huỷ đơn hàng, giải phóng tồn kho                 |
| order-service       | `payment.refund.processed`       | Cập nhật trạng thái hoàn tiền                    |
| order-service       | `fulfillment.shipment.picked-up` | Cập nhật đơn sang trạng thái đang giao           |
| order-service       | `fulfillment.shipment.delivered` | Xác nhận giao hàng thành công                    |
| order-service       | `fulfillment.shipment.failed`    | Xử lý giao hàng thất bại                         |
| inventory-service   | `order.order.created`            | Đặt giữ tồn kho cho đơn hàng                     |
| inventory-service   | `order.order.cancelled`          | Giải phóng tồn kho đã giữ                        |
| payment-service     | `order.payment.requested`        | Thực hiện thanh toán                             |
| payment-service     | `order.order.delivered`          | Đối soát tiền mặt (COD)                          |
| payment-service     | `fulfillment.shipment.delivered` | Đối soát tiền mặt (COD) — đường thứ hai          |
| payment-service     | `return.return.completed`        | Thực hiện hoàn tiền cho khách                    |
| fulfillment-service | `order.order.confirmed`          | Phân công người giao hàng                        |
| fulfillment-service | `return.return.approved`         | Sắp xếp lấy hàng hoàn trả                        |

### Consumer nhóm Projection (cập nhật dữ liệu tìm kiếm)

> **Projection** là cơ chế cập nhật một "bản sao" dữ liệu được tối ưu cho việc đọc
> (ở đây là Elasticsearch). Khi projection thất bại, dữ liệu tìm kiếm bị lỗi thời
> nhưng dữ liệu gốc vẫn còn nguyên vẹn.

| Consumer        | Đọc từ topic                     | Cập nhật gì                                    |
|-----------------|----------------------------------|------------------------------------------------|
| search-service  | `catalog.product.published`      | Thêm sản phẩm vào index tìm kiếm               |
| search-service  | `catalog.product.unpublished`    | Xóa sản phẩm khỏi index tìm kiếm               |
| search-service  | `catalog.product.updated`        | Cập nhật thông tin sản phẩm trong index        |
| search-service  | `catalog.category.updated`       | Cập nhật danh mục trong index                  |
| search-service  | `inventory.reservation.released` | Cập nhật tình trạng tồn kho trong index        |
| search-service  | `inventory.stock.replenished`    | Cập nhật số lượng tồn kho trong index          |
| search-service  | `inventory.stock.depleted`       | Đánh dấu hết hàng trong index                  |
| search-service  | `seller.seller.rating-updated`   | Cập nhật điểm đánh giá seller trong index      |
| search-service  | `review.product.reviewed`        | Cập nhật điểm đánh giá sản phẩm trong index    |
| seller-service  | `review.seller.rated`            | Cập nhật điểm đánh giá seller trong DB nội bộ  |
| shipper-service | `review.shipper.rated`           | Cập nhật điểm đánh giá shipper trong DB nội bộ |

### Consumer nhóm State (cập nhật trạng thái nghiệp vụ phụ trợ)

| Consumer         | Đọc từ topic               | Làm gì                                     |
|------------------|----------------------------|--------------------------------------------|
| customer-service | `identity.user.registered` | Tạo hồ sơ khách hàng khi đăng ký tài khoản |
| customer-service | `order.order.confirmed`    | Ghi nhận đơn hàng đang được xử lý          |
| customer-service | `order.order.completed`    | Cộng điểm thưởng cho khách hàng            |
| review-service   | `order.order.completed`    | Mở khoá quyền viết đánh giá cho đơn hàng   |

### Consumer nhóm Notify (gửi thông báo)

> Nhóm này chỉ có một nhiệm vụ: tạo bản ghi thông báo để email-worker hoặc websocket-gateway
> gửi tới người dùng. Nếu thất bại, nghiệp vụ cốt lõi không bị ảnh hưởng —
> người dùng chỉ đơn giản là không nhận được thông báo.

notification-service đọc từ **26 topic** — nhiều nhất trong toàn hệ thống:

`identity.user.registered` · `identity.email-verification.reissued` · `inventory.stock.replenished`
· `inventory.stock.depleted` · `order.order.confirmed` · `order.order.cancelled`
· `order.order.delivered` · `order.order.completed` · `payment.refund.processed`
· `payment.payout.completed` · `fulfillment.shipment.assigned` · `fulfillment.shipment.picked-up`
· `fulfillment.shipment.in-transit` · `fulfillment.shipment.delivered` · `fulfillment.shipment.failed`
· `return.return.requested` · `return.return.approved` · `return.return.rejected`
· `return.return.completed` · `seller.seller.approved` · `seller.seller.rejected`
· `shipper.shipper.approved` · `shipper.shipper.rejected` · `customer.loyalty.earned`
· `customer.loyalty.expired` · `chat.message.received`

Toàn bộ 26 topic đều đi qua cùng một đường xử lý: `NotificationDispatchService` → `NotificationHandlerRegistry`
→ lưu `NotificationLog` + `NotificationInbox`. Không có topic nào xử lý khác biệt về mặt kỹ thuật.

### Consumer nhóm Delivery (gửi email thực tế)

email-worker đọc từ 2 topic với hai consumer group riêng biệt:

| Consumer group  | Topic                              | Loại email                                                       |
|-----------------|------------------------------------|------------------------------------------------------------------|
| email-worker-t1 | `notification.email.transactional` | Email giao dịch: xác minh tài khoản, chào mừng, đặt lại mật khẩu |
| email-worker-t2 | `notification.email.bulk`          | Email marketing, khuyến mãi                                      |

---

## 4. Phân Tích và Lựa Chọn

### 4.1 Nhóm Saga — order-service, inventory-service, payment-service, fulfillment-service

**Tại sao đây là nhóm nghiêm trọng nhất?**

Saga là chuỗi phụ thuộc tuần tự. Khi một khâu trong chuỗi vào DLQ và không được phát lại,
toàn bộ chuỗi phía sau không bao giờ được kích hoạt — dù các service phía sau vẫn hoạt động bình thường.

Đây là kịch bản minh hoạ mức độ nguy hiểm:

```
Kịch bản: payment.payment.succeeded vào order-service.dlq

Thực tế đã xảy ra:
  → payment-service đã TRỪ TIỀN của khách hàng thành công
  → order-service nhận được tin "thanh toán thành công" nhưng xử lý thất bại

Hệ quả nếu không phát lại:
  → OrderConfirmed không bao giờ được gửi
  → fulfillment-service không bao giờ nhận lệnh phân công shipper
  → notification-service không thông báo gì cho khách
  → Khách đã mất tiền nhưng đơn hàng không được giao
```

Đây là tình huống nghiêm trọng nhất trong hệ thống — không phải vì service bị lỗi,
mà vì trạng thái giữa các service trở nên **mâu thuẫn không thể tự khắc phục**.

**Một quan sát đặc biệt về COD (thanh toán khi nhận hàng):**

Topic `fulfillment.shipment.delivered` được đọc bởi **cả hai** order-service (cập nhật trạng thái đơn)
và payment-service (đối soát tiền COD). Hai service này fail độc lập — nếu payment-service có
message này trong DLQ, tiền COD sẽ không được ghi nhận vào hệ thống kế toán dù đơn hàng
đã được giao thành công. Đây là rủi ro tài chính, không chỉ là lỗi UX.

**Lựa chọn: 1 DLQ cho mỗi service, không tách theo topic.**

Lý do không tách theo từng topic (dù có tới 8 topic cho order-service):
- Tất cả 8 topic đều có cùng mức độ nghiêm trọng
- Cùng người xử lý, cùng quy trình phát lại
- Header `kafka_dlt-original-topic` trong message DLQ đã đủ để xác định topic gốc khi điều tra

| Service             | DLQ topic                 | Mức độ       | Thời gian phát lại tối đa |
|---------------------|---------------------------|--------------|---------------------------|
| order-service       | `order-service.dlq`       | Nghiêm trọng | 15 phút                   |
| inventory-service   | `inventory-service.dlq`   | Nghiêm trọng | 15 phút                   |
| payment-service     | `payment-service.dlq`     | Nghiêm trọng | 15 phút                   |
| fulfillment-service | `fulfillment-service.dlq` | Nghiêm trọng | 30 phút                   |

**Lưu ý riêng cho inventory-service trong điều kiện flash sale:**

NFR định nghĩa kịch bản 200,000 người dùng tranh mua, tương đương ~3,333 checkout/giây
trên 1 SKU. Với tốc độ này, mỗi message `order.order.cancelled` không được xử lý
(giải phóng tồn kho) sẽ dẫn đến số lượng tồn kho trong hệ thống thấp hơn thực tế.
Khi hàng nghìn người cùng checkout trong vài giây, sự sai lệch này có thể khiến
hệ thống từ chối hàng trăm đơn hàng hợp lệ.

---

### 4.2 customer-service — mức độ cao hơn tưởng

customer-service đọc từ 3 topic với mức độ hậu quả khác nhau:

**`identity.user.registered` — nghiêm trọng:**

Khi đăng ký thành công ở identity-service nhưng consumer tạo hồ sơ khách hàng thất bại,
người dùng có tài khoản nhưng không có hồ sơ. Mọi tính năng của marketplace — đặt hàng,
theo dõi đơn, tích điểm thưởng — đều phụ thuộc vào hồ sơ này. Tài khoản của người dùng
đó về mặt thực tế bị hỏng hoàn toàn.

**`order.order.completed` — mức độ cao:**

Nếu thất bại, khách hàng mua hàng thành công nhưng không được cộng điểm thưởng.
Đây là lỗi tài chính — người dùng bị thiệt quyền lợi đã cam kết.

**`order.order.confirmed` — mức độ trung bình:**

Ghi nhận trạng thái trung gian, ít ảnh hưởng tới quyền lợi trực tiếp của người dùng.

**Lựa chọn: 1 DLQ duy nhất `customer-service.dlq`.**

Dù mức độ hậu quả khác nhau giữa 3 topic, không nên tách DLQ vì:
- Lượng message nhỏ (không phải high-throughput service)
- Quy trình xử lý DLQ giống nhau (điều tra → phát lại)
- Khi có bất kỳ message nào vào DLQ cũng cần xem xét ngay — không có trường hợp nào
  được trì hoãn đến ngày hôm sau

---

### 4.3 review-service và seller/shipper-service — chuỗi projection có cascade

review-service đọc `order.order.completed` để mở khoá quyền viết đánh giá.
Nếu thất bại, người dùng mua hàng xong nhưng không thể viết review — một tính năng
quan trọng với marketplace nhưng không ảnh hưởng quyền lợi tài chính.

seller-service đọc `review.seller.rated` để cập nhật điểm đánh giá nội bộ, sau đó
phát ra event `seller.seller.rating-updated` để search-service cập nhật index.
Nếu seller-service thất bại, điểm đánh giá bị lỗi thời ở **hai nơi đồng thời**:
cơ sở dữ liệu của seller-service và index tìm kiếm. Tuy nhiên, khi phát lại message DLQ,
seller-service sẽ xử lý lại và tự phát event mới → search-service tự cập nhật theo.
Chuỗi này tự phục hồi sau một lần phát lại duy nhất.

**Lựa chọn: mỗi service 1 DLQ, mức độ trung bình.**

---

### 4.4 search-service — projection với mức độ urgency không đồng đều

9 topic đều là Projection nhưng hậu quả không hoàn toàn như nhau:

- `catalog.product.unpublished` thất bại → sản phẩm đã bị ẩn nhưng vẫn hiện trong tìm kiếm.
  Đây có thể là sản phẩm vi phạm, hàng giả, hoặc seller bị khoá — urgency cao hơn hẳn.
- `review.product.reviewed` thất bại → điểm đánh giá lỗi thời. Người dùng vẫn mua được
  hàng, chỉ thấy điểm sao hơi cũ — urgency thấp.

**Lựa chọn: 1 DLQ duy nhất `search-service.dlq`.**

Không tách theo topic vì: toàn bộ 9 topic đều đi qua cùng cơ chế ghi vào Elasticsearch,
lỗi thường đến từ Elasticsearch (service down, schema mismatch) chứ không phải từng topic riêng.
Khi Elasticsearch có vấn đề, tất cả 9 topic đều fail cùng lúc — tách DLQ không giúp gì thêm.
Header `kafka_dlt-original-topic` đủ để filter và ưu tiên phát lại `catalog.product.unpublished` trước.

---

### 4.5 notification-service — 26 topic, 1 DLQ

Đây là consumer lớn nhất hệ thống. Lý do gom tất cả 26 topic về 1 DLQ:

**Về mặt kỹ thuật:** Toàn bộ 26 topic đi qua cùng một đường xử lý duy nhất trong code
(`NotificationDispatchService.dispatch()`). Nguyên nhân thất bại thường là cơ sở dữ liệu
notification-service bị lỗi, không phải lỗi riêng của từng loại thông báo.

**Về mặt nghiệp vụ:** Tất cả 26 topic đều có cùng hậu quả khi thất bại — người dùng
không nhận được thông báo. Không có topic nào ảnh hưởng tới quy trình nghiệp vụ cốt lõi.

**Về mặt vận hành:** Với ~1,000,000 notification events/ngày (theo NFR), cần đặt ngưỡng
cảnh báo theo số lượng tích luỹ (ví dụ: cảnh báo khi > 50 message trong 1 giờ)
thay vì cảnh báo từng message. Khi có cảnh báo, kỹ sư kiểm tra DLQ và xem
`kafka_dlt-original-topic` header để xác định loại thông báo nào đang bị ảnh hưởng.

---

### 4.6 email-worker — trường hợp bắt buộc phải tách

Đây là trường hợp duy nhất trong hệ thống mà việc tách DLQ là bắt buộc.

Dù Tier1 và Tier2 dùng cùng `EmailDispatchHandler` và cùng cơ chế idempotency,
hậu quả khi thất bại hoàn toàn khác nhau:

|                           | Tier1 — email giao dịch                              | Tier2 — email marketing                |
|---------------------------|------------------------------------------------------|----------------------------------------|
| Ví dụ                     | Email xác minh tài khoản                             | Email khuyến mãi                       |
| Nếu thất bại              | Người dùng không xác minh được → không dùng được app | Người dùng không nhận email khuyến mãi |
| Mức độ                    | Cao                                                  | Thấp                                   |
| Thời gian phát lại tối đa | 1 giờ                                                | Ngày hôm sau                           |
| Cảnh báo                  | Cảnh báo ngay khi có message đầu tiên                | Báo cáo cuối ngày                      |

Nếu gom chung vào `email-worker.dlq`: 500 email marketing bị lỗi sẽ "che khuất"
1 email xác minh tài khoản bị kẹt — không thể set ngưỡng cảnh báo phù hợp cho cả hai.

Hai consumer group đã được tách biệt về mặt kỹ thuật (`email-worker-t1`, `email-worker-t2`).
DLQ cần tách tương ứng.

---

## 5. Kết Quả — 12 DLQ Topics

| Consumer             | Số topic nguồn | DLQ Topic                  | Mức độ             | Phát lại trong |
|----------------------|----------------|----------------------------|--------------------|----------------|
| order-service        | 8              | `order-service.dlq`        | Nghiêm trọng       | 15 phút        |
| inventory-service    | 2              | `inventory-service.dlq`    | Nghiêm trọng       | 15 phút        |
| payment-service      | 4              | `payment-service.dlq`      | Nghiêm trọng       | 15 phút        |
| fulfillment-service  | 2              | `fulfillment-service.dlq`  | Nghiêm trọng       | 30 phút        |
| customer-service     | 3              | `customer-service.dlq`     | Nghiêm trọng / Cao | 30 phút        |
| review-service       | 1              | `review-service.dlq`       | Trung bình         | 4 giờ          |
| seller-service       | 1              | `seller-service.dlq`       | Trung bình         | 4 giờ          |
| shipper-service      | 1              | `shipper-service.dlq`      | Trung bình         | 4 giờ          |
| search-service       | 9              | `search-service.dlq`       | Trung bình         | 2 giờ          |
| notification-service | 26             | `notification-service.dlq` | Thấp               | Cuối ngày      |
| email-worker Tier1   | 1              | `email-worker-t1.dlq`      | Cao                | 1 giờ          |
| email-worker Tier2   | 1              | `email-worker-t2.dlq`      | Thấp               | Cuối ngày      |

---

## 6. Những Điểm Cần Lưu Ý Khi Triển Khai

### 6.1 Thứ tự message được bảo toàn tự động

Trong hệ thống này, Kafka dùng `orderId` làm khoá phân vùng cho các Saga event —
đảm bảo tất cả message của cùng một đơn hàng luôn được xử lý theo đúng thứ tự.

Khi `DeadLetterPublishingRecoverer` (thư viện Spring Kafka) đẩy message vào DLQ,
nó mặc định giữ nguyên khoá phân vùng (`orderId`) từ message gốc. Nghĩa là
thứ tự Saga được bảo toàn ngay cả trong DLQ — không cần cấu hình thêm.

### 6.2 Thông tin debug đính kèm tự động

Mỗi message trong DLQ tự động có các thông tin sau được đính kèm dưới dạng header:

```
kafka_dlt-original-topic          → topic gốc message đến từ đâu
kafka_dlt-original-offset         → vị trí chính xác trong topic gốc
kafka_dlt-original-consumer-group → consumer group nào thất bại
kafka_dlt-exception-message       → thông báo lỗi
kafka_dlt-exception-stacktrace    → stack trace đầy đủ
```

Đây là lý do không cần tạo nhiều DLQ topic chỉ để lưu metadata —
tất cả thông tin cần thiết đã có sẵn trong mỗi message.

### 6.3 Vòng lặp vô hạn khi phát lại tự động

Một thất bại vĩnh viễn (ví dụ: message có định dạng sai) nếu được phát lại tự động
sẽ tạo ra vòng lặp:

```
topic gốc → thất bại → DLQ → phát lại → topic gốc → thất bại → DLQ → ...
```

Giải pháp cho giai đoạn hiện tại: phát lại thủ công sau khi kỹ sư đã xác nhận
nguyên nhân và khắc phục lỗi gốc. Nếu cần tự động hoá sau này, cần track
số lần phát lại và giới hạn tối đa.

### 6.4 Cấu hình thử lại theo đặc điểm từng nhóm

| Nhóm                 | Cấu hình thử lại            | Lý do                                                      |
|----------------------|-----------------------------|------------------------------------------------------------|
| Saga consumers       | 3 lần, mỗi lần cách 2 giây  | Càng phát hiện sớm càng tốt — Saga bị kẹt lâu là nguy hiểm |
| customer-service     | 3 lần, mỗi lần cách 2 giây  | Cùng logic — phát hiện nhanh                               |
| search-service       | 3 lần, mỗi lần cách 5 giây  | Elasticsearch đôi khi cần thêm thời gian để ổn định        |
| notification-service | 3 lần, mỗi lần cách 2 giây  | DB fail thường phục hồi nhanh                              |
| email-worker Tier1   | 3 lần, mỗi lần cách 2 giây  | Email xác minh cần phát hiện lỗi nhanh                     |
| email-worker Tier2   | 3 lần, mỗi lần cách 60 giây | SMTP server cho bulk mail thường phục hồi chậm             |

Ngoài ra, cần cấu hình bỏ qua thử lại cho lỗi định dạng message (`DeserializationException`)
— loại lỗi này không bao giờ được khắc phục bằng thử lại. Đẩy thẳng vào DLQ
thay vì lãng phí 3 lần thử vô nghĩa.

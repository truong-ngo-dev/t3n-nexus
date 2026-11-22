# Thuật ngữ

## 1. Mục đích và quy ước

File này là bảng tra cho `5-context-map.md` và mọi tài liệu sau nó. Mỗi thuật ngữ có đúng một nghĩa trong dự án; tài liệu nào
dùng khác nghĩa thì sửa tài liệu đó, không sửa nghĩa ở đây.

Quy ước ngôn ngữ:

- **Tên viết bằng tiếng Anh**: tên bounded context, khái niệm nghiệp vụ, event, kiểu quan hệ. Tên này trùng với tên trong mã,
  để đọc tài liệu và đọc mã không phải dịch qua lại.
- **Giải thích viết bằng tiếng Việt.** Khi nói hoặc viết câu tiếng Việt, dùng tên tiếng Việt ở cột "Tiếng Việt" cho tự nhiên;
  khi cần chỉ đúng một khái niệm trong mã, dùng tên tiếng Anh trong dấu `code`.
- **Giữ nguyên từ mượn đã quen**: voucher, flash sale, COD, SKU, event, webhook. Viết đúng một cách trong mọi file.

## 2. Thuật ngữ DDD

| Thuật ngữ                   | Nghĩa                                                                                                                            | Ví dụ trong dự án                                                                 |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| Bounded Context (BC)        | Một phần của hệ thống có ngôn ngữ, quy tắc và lý do thay đổi riêng. Cùng một từ có thể mang nghĩa khác nhau ở hai BC             | "Sản phẩm" ở `Catalog` là thứ được niêm yết; ở `Search` là một dòng trong chỉ mục |
| Context Map                 | Bản đồ các BC và quan hệ giữa chúng                                                                                              | `5-context-map.md`                                                                |
| Core subdomain              | Phần tạo ra giá trị khác biệt, đáng đầu tư nhiều nhất                                                                            | `Inventory`, `Ordering`                                                           |
| Supporting subdomain        | Phần cần cho nghiệp vụ nhưng không tạo khác biệt; làm đủ dùng                                                                    | `Seller`, `Shipping`                                                              |
| Generic subdomain           | Phần mọi hệ thống đều có, có thể dùng giải pháp sẵn                                                                              | `Authentication`, `Media`                                                         |
| Upstream / Downstream       | Upstream là bên cung cấp dữ liệu hoặc sự kiện; downstream là bên dùng. Thay đổi ở upstream ảnh hưởng downstream, không ngược lại | `Catalog` là upstream của `Search`                                                |
| Customer-Supplier           | Downstream nêu nhu cầu, upstream đáp ứng và cam kết giữ hợp đồng                                                                 | `Ordering` cần `Inventory` giữ chỗ                                                |
| Conformist                  | Downstream dùng nguyên mô hình của upstream, không dịch                                                                          |                                                                                   |
| Anti-corruption Layer (ACL) | Lớp dịch ở downstream để mô hình của bên kia không lọt vào mô hình của mình                                                      | `Payment` dịch phản hồi của bên thanh toán bên ngoài                              |
| Open Host Service (OHS)     | Upstream công bố một giao diện ổn định cho nhiều bên dùng                                                                        | `Authentication` cấp token cho mọi ứng dụng                                       |
| Published Language          | Hình dạng dữ liệu được công bố và giữ ổn định giữa các BC, thường là event                                                       | Event `OrderPlaced`                                                               |
| Domain Event                | Thông báo một việc nghiệp vụ đã xảy ra, đặt tên ở thì quá khứ                                                                    | `ProductPublished`, `PaymentSucceeded`                                            |
| Aggregate                   | Một cụm dữ liệu luôn được đổi cùng nhau trong một giao dịch để giữ đúng quy tắc của nó                                           | `Order` cùng các dòng hàng của nó                                                 |
| Invariant                   | Quy tắc luôn phải đúng với dữ liệu, kể cả khi tải cao                                                                            | Tồn khả dụng không âm                                                             |

## 3. Tên các BC

Danh sách đầy đủ, lý do chia và quan hệ ở `5-context-map.md`.

| Tên                | Tiếng Việt          | Trách nhiệm                                                                     |
|--------------------|---------------------|---------------------------------------------------------------------------------|
| `Authentication`   | Xác thực            | Tài khoản, vai, phiên, đăng nhập; quyết định ai được vào                        |
| `Identity`         | Định danh           | Hồ sơ hiển thị, thiết bị, lịch sử đăng nhập; chỉ để xem                         |
| `Customer`         | Khách hàng          | Sổ địa chỉ và thông tin nhận hàng của người mua                                 |
| `Seller`           | Người bán           | Hồ sơ người bán, gian hàng, duyệt                                               |
| `Catalog`          | Danh mục sản phẩm   | Sản phẩm, biến thể, giá niêm yết, ảnh, danh mục, thuộc tính; đăng và gỡ         |
| `Search`           | Tìm kiếm            | Tìm, lọc, sắp xếp trên bản dữ liệu dựng riêng để đọc                            |
| `Cart`             | Giỏ hàng            | Giỏ của người mua và khách vãng lai                                             |
| `Inventory`        | Tồn kho             | Số lượng bán được, giữ chỗ, nhả chỗ                                             |
| `Ordering`         | Đơn hàng            | Đặt hàng, tách theo người bán, vòng đời đơn, điều phối các bước                 |
| `Payment`          | Thanh toán          | Thanh toán trực tuyến qua bên ngoài, COD, hoàn tiền                             |
| `Shipping`         | Giao nhận           | Phí vận chuyển, vận đơn, trạng thái giao (giả lập)                              |
| `Notification`     | Thông báo           | Gửi và đẩy thông báo tới người dùng                                             |
| `Media`            | Quản lý tệp         | Lưu và phục vụ ảnh                                                              |
| `Promotion`        | Khuyến mãi          | Voucher, flash sale, suất giới hạn                                              |
| `Reporting`        | Báo cáo             | Số liệu tổng hợp cho người bán và nền tảng                                      |
| `Returns`          | Hoàn trả            | Yêu cầu trả hàng, duyệt, hoàn tiền                                              |
| `Ledger`           | Sổ cái              | Số dư, giữ tiền đến khi giao xong, chi trả, đối soát                            |
| `Chat`             | Chat                | Hội thoại người mua với người bán                                               |
| `ActivityTracking` | Theo dõi hành vi    | Ghi nhận xem, nhấp, tìm                                                         |
| `Fulfillment`      | Kho và giao hàng    | Kho nền tảng, đội giao, phân công, vị trí giao                                  |

## 4. Khái niệm nghiệp vụ

Nhóm theo BC sở hữu. Một khái niệm chỉ có một BC sở hữu; BC khác chỉ giữ tham chiếu hoặc bản sao để đọc.

### 4.1 `Authentication` và `Identity`

| Tên            | Tiếng Việt        | Nghĩa                                                                                                 |
|----------------|-------------------|-------------------------------------------------------------------------------------------------------|
| `Account`      | Tài khoản         | Đại diện cho một người trong hệ thống; các BC khác trỏ tới người dùng bằng mã tài khoản               |
| `Role`         | Vai               | Nhóm quyền của tài khoản; mỗi tài khoản đúng một vai: `USER` (tự đăng ký) hoặc vai nội bộ như `ADMIN` |
| `Session`      | Phiên             | Khoảng thời gian một người được coi là đã đăng nhập trên một thiết bị                                 |
| `Guest`        | Khách vãng lai    | Người chưa đăng nhập                                                                                  |
| `Device`       | Thiết bị          | Một trình duyệt hoặc một bản cài ứng dụng của một tài khoản; không phải phần cứng                     |
| `LoginHistory` | Lịch sử đăng nhập | Các lần đăng nhập của một tài khoản, chỉ thêm không sửa                                               |

Người mua, người bán không phải vai. Mọi tài khoản `USER` đều mua được; bán hàng cần thêm một hồ sơ người bán được duyệt.

### 4.2 `Customer` và `Seller`

| Tên             | Tiếng Việt          | Nghĩa                                                                              |
|-----------------|---------------------|------------------------------------------------------------------------------------|
| `Address`       | Địa chỉ nhận hàng   | Tên người nhận, số điện thoại, địa chỉ; một tài khoản có nhiều địa chỉ             |
| `SellerProfile` | Hồ sơ người bán     | Thông tin pháp lý và gian hàng của một tài khoản muốn bán; có vòng đời duyệt riêng |
| `Shop`          | Gian hàng           | Phần hiển thị công khai của người bán: tên, mô tả, liên hệ                         |
| `SellerType`    | Loại hình người bán | `INDIVIDUAL` (cá nhân), `HOUSEHOLD` (hộ kinh doanh), `BUSINESS` (doanh nghiệp)     |

### 4.3 `Catalog`, `Search`, `Media`

| Tên              | Tiếng Việt   | Nghĩa                                                                         |
|------------------|--------------|-------------------------------------------------------------------------------|
| `Product`        | Sản phẩm     | Một mặt hàng người bán niêm yết, gồm một hoặc nhiều biến thể                  |
| `Variant`        | Biến thể     | Một lựa chọn cụ thể của sản phẩm (màu, cỡ), có giá riêng                      |
| `SKU`            | SKU          | Mã của một biến thể; đơn vị mà người mua đặt và tồn kho đếm                   |
| `Category`       | Danh mục     | Cây phân loại sản phẩm                                                        |
| `Attribute`      | Thuộc tính   | Đặc điểm dùng để lọc (thương hiệu, chất liệu)                                 |
| `ListPrice`      | Giá niêm yết | Giá người bán đặt, trước mọi khuyến mãi                                       |
| `SearchDocument` | Bản tìm kiếm | Bản dữ liệu của một sản phẩm dựng riêng cho tìm kiếm; chậm hơn nguồn một chút |
| `Image`          | Ảnh          | Tệp ảnh do `Media` lưu; `Catalog` chỉ giữ tham chiếu                          |

### 4.4 `Cart`, `Inventory`, `Ordering`

| Tên               | Tiếng Việt     | Nghĩa                                                                                                             |
|-------------------|----------------|-------------------------------------------------------------------------------------------------------------------|
| `Cart`            | Giỏ hàng       | Danh sách SKU người mua định mua; chưa giữ chỗ                                                                    |
| `GuestCart`       | Giỏ vãng lai   | Giỏ của khách chưa đăng nhập; gộp vào giỏ của tài khoản khi đăng nhập                                             |
| `Stock`           | Tồn            | Số lượng còn bán được của một SKU                                                                                 |
| `Reservation`     | Giữ chỗ        | Trừ tạm tồn cho một đơn đang đặt; chốt khi đơn thành công, nhả khi hủy hoặc quá hạn                               |
| `Order`           | Đơn hàng       | Yêu cầu mua của người mua với **một** người bán; một lần đặt có hàng của nhiều người bán thì tách thành nhiều đơn |
| `Checkout`        | Lần đặt        | Một lần người mua bấm đặt hàng; có thể sinh nhiều đơn                                                             |
| `OrderStatus`     | Trạng thái đơn | Vị trí của đơn trong vòng đời, ví dụ chờ thanh toán, chờ người bán xác nhận, đang giao, hoàn tất, đã hủy          |
| `PaymentDeadline` | Hạn thanh toán | Thời gian giữ chỗ chờ thanh toán trực tuyến; quá hạn thì đơn hủy và chỗ được nhả                                  |

### 4.5 `Payment`, `Shipping`, `Notification`

| Tên               | Tiếng Việt             | Nghĩa                                                                           |
|-------------------|------------------------|---------------------------------------------------------------------------------|
| `PaymentMethod`   | Phương thức thanh toán | `ONLINE` (qua bên thanh toán bên ngoài) hoặc `COD` (trả tiền mặt khi nhận hàng) |
| `Payment`         | Thanh toán             | Một lần thu tiền cho một lần đặt, có trạng thái riêng                           |
| `Refund`          | Hoàn tiền              | Trả lại tiền đã thu khi đơn hủy hoặc trả hàng                                   |
| `PaymentProvider` | Bên thanh toán         | Dịch vụ bên ngoài xử lý thanh toán trực tuyến; dùng môi trường thử nghiệm       |
| `Webhook`         | Webhook                | Lời gọi từ bên thanh toán báo kết quả về; có thể trễ hoặc lặp                   |
| `ShippingFee`     | Phí vận chuyển         | Phí tính theo vùng giao                                                         |
| `Shipment`        | Vận đơn                | Một lần giao hàng cho một đơn; có trạng thái giao                               |
| `Notification`    | Thông báo              | Một tin gửi tới người dùng, trong ứng dụng hoặc qua email                       |

### 4.6 Nghiệp vụ mở rộng

| Tên              | Tiếng Việt         | Nghĩa                                                                     |
|------------------|--------------------|---------------------------------------------------------------------------|
| `Voucher`        | Voucher            | Mã ưu đãi có giới hạn tổng số lượt và giới hạn mỗi người                  |
| `FlashSale`      | Flash sale         | Đợt bán giá ưu đãi, số suất giới hạn, trong thời gian ngắn                |
| `Quota`          | Suất               | Số lượng được bán hoặc được dùng trong một đợt ưu đãi; tách khỏi tồn thật |
| `PerUserLimit`   | Giới hạn mỗi người | Số suất tối đa một tài khoản được nhận trong một đợt                      |
| `ReturnRequest`  | Yêu cầu trả hàng   | Người mua yêu cầu trả hàng sau khi nhận                                   |
| `Balance`        | Số dư              | Tiền của người bán đang được nền tảng giữ                                 |
| `Payout`         | Chi trả            | Chuyển tiền từ số dư cho người bán                                        |
| `LedgerEntry`    | Bút toán           | Một dòng ghi biến động tiền; chỉ thêm, sai thì ghi bút toán bù, không sửa |
| `Reconciliation` | Đối soát           | So khớp số tiền giữa hai bên                                              |
| `Conversation`   | Hội thoại          | Chuỗi tin nhắn giữa một người mua và một người bán                        |
| `Courier`        | Người giao         | Người do nền tảng quản lý, nhận và giao đơn                               |

## 5. Thuật ngữ kỹ thuật và phi chức năng

| Thuật ngữ                                  | Nghĩa                                                                                                      |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Saga                                       | Một quy trình chia nhiều bước qua nhiều BC; bước sau lỗi thì chạy bước bù để hoàn bước trước               |
| Bù trừ (compensation)                      | Bước hoàn lại việc đã làm, ví dụ nhả giữ chỗ khi thanh toán thất bại                                       |
| Outbox                                     | Ghi event vào cùng giao dịch với dữ liệu rồi mới phát, để không mất event và không lệch với dữ liệu        |
| CDC (change data capture)                  | Đọc thay đổi từ nhật ký của cơ sở dữ liệu để đồng bộ sang nơi khác mà không sửa nơi ghi                    |
| Idempotency (xử lý lặp)                    | Gửi hoặc nhận cùng một yêu cầu nhiều lần chỉ ra một kết quả                                                |
| Eventual consistency (nhất quán cuối cùng) | Dữ liệu ở nơi đọc chậm hơn nguồn một lúc rồi khớp                                                          |
| Read model                                 | Bản dữ liệu dựng riêng để đọc nhanh, tách khỏi nơi ghi                                                     |
| Mặt hàng nóng                              | Mặt hàng nhận phần lớn yêu cầu cùng lúc, ví dụ hàng flash sale                                             |
| P50, P95, P99                              | Độ trễ mà 50%, 95%, 99% yêu cầu nhanh hơn                                                                  |
| Đo nền                                     | Đo những thao tác đơn giản nhất để biết trần của máy trước khi đo ứng dụng                                 |
| Kiểm chứng theo lát cắt                    | Chỉ chạy một phần tách riêng được của hệ thống, phần khác là bản giả, để đo phần đó ở đúng quy mô thiết kế |
| Bản giả                                    | Thành phần đứng thay một phần thật trong lúc đo, hành xử theo quy tắc đã ghi                               |
| Mã truy vết                                | Mã đi theo một yêu cầu qua log và event, để lần ra toàn bộ dấu vết                                         |

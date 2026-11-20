# Người bán: service (`seller-service`)

> **Trạng thái:** Feature 03 (nộp và sửa hồ sơ) và feature 04 (duyệt, từ chối) đã làm · **Cập nhật:** `2026-10-31` · **Mã BC:** `SEL`
>
> Chỉ ghi kết luận, ở mức aggregate. Mỗi mục có nhãn **Dự kiến** hoặc **Đã làm**; mục thuộc feature 03 và 04 là **Đã làm**, mục thuộc chặng C1b là **Dự kiến**. Chi tiết (trường, bảng, cột, mã lỗi) nằm ở [`data.md`](data.md) và [`api.yaml`](api.yaml). Nghiệp vụ và lý do: [`analysis.md`](analysis.md).

## 1. Vai trò

Giữ hồ sơ người bán của một tài khoản (loại hình, thông tin pháp lý khai báo, danh tính gian hàng), chạy quy trình duyệt, và công bố kết quả duyệt cho BC khác. Không giữ tài khoản, sản phẩm hay nhật ký.

## 2. Aggregate

### AGG-SEL-01 Hồ sơ người bán (`SellerProfile`) · Đã làm (nộp, sửa, nộp lại, duyệt, từ chối)

**Trách nhiệm:** giữ hồ sơ xin bán của một tài khoản, vòng đời Chờ duyệt, Được duyệt, Bị từ chối, và các thông tin pháp lý, gian hàng.
**Bất biến giữ trong aggregate:** INV-SEL-02, 03, 05, 06, 08, 10, 11. Các bất biến chạm tập hợp hồ sơ (INV-SEL-01, 04, 09) ở mục 3. INV-SEL-07 ở mục 3.

**Thuộc tính**

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi (ADR-0001) | Sinh qua cổng sinh mã của miền |
| `ownerAccountId` | Mã tài khoản | Có | Duy nhất (INV-SEL-01), không đổi | Khóa mà BC khác dùng để tham chiếu người bán |
| `status` | Trạng thái | Có | `PENDING_REVIEW`, `APPROVED`, `REJECTED`; C1b thêm `SUSPENDED` (INV-SEL-02) | Nguồn sự thật duy nhất về người bán đã duyệt |
| `sellerType` | Loại hình | Có | `INDIVIDUAL`, `HOUSEHOLD`, `BUSINESS`; không đổi sau khi được duyệt (INV-SEL-10) | |
| `legalInfo` | Giá trị | Có | Đủ và đúng định dạng theo loại hình (INV-SEL-08): tên pháp lý, người đại diện (doanh nghiệp), số căn cước 12 chữ số, mã số thuế 10 hoặc 13 chữ số, địa chỉ kinh doanh; khóa sau khi được duyệt (INV-SEL-10) | Dữ liệu cá nhân (INV-SEL-11) |
| `identityKey` | Chuỗi | Có | Suy ra từ `legalInfo`: số căn cước (cá nhân), mã số thuế (hộ, doanh nghiệp); chuẩn hóa; chỉ hồ sơ hiệu lực giữ khóa (INV-SEL-09) | Không lộ ra ngoài BC |
| `shopName` | Giá trị | Có | Tên hiển thị 3 đến 60 ký tự; khóa so sánh sau chuẩn hóa (bỏ khoảng trắng thừa, chữ thường, bỏ dấu) duy nhất toàn sàn (INV-SEL-04) | |
| `description` | Chữ | Không | Tối đa 500 ký tự | |
| `contact` | Giá trị | Có | Người liên hệ, số điện thoại Việt Nam hợp lệ (chuẩn hóa một dạng); địa chỉ liên hệ tùy chọn, 10 đến 300 ký tự | Để trống địa chỉ thì dùng địa chỉ kinh doanh |
| `rejectionReason` | Chữ | Khi `REJECTED` | Tối đa 500 ký tự (INV-SEL-03) | Lý do lần từ chối gần nhất |
| `submittedAt`, `reviewedAt`, `reviewedBy` | Thời điểm, mã tài khoản | `reviewed*` khi đã quyết định | | Thứ tự hàng chờ; truy trách nhiệm |
| `version` | Số | Có | Bắt đầu từ 0 khi lưu mới, tăng đúng một mỗi thay đổi nội dung hồ sơ; khóa lạc quan chạy ngầm, **người bán không gửi và không thấy** (ADR-0011); quản trị quyết định kèm phiên bản đã xem (INV-SEL-06, feature 04). Miền giữ phiên bản nạp ở dạng **chỉ đọc** (`version()`), không có hàm ghi; phiên bản sau quyết định là `version() + 1` | |
| `createdAt`, `updatedAt` | Thời điểm | Có | | |

**Hành vi**

| Hành vi | Khi nào chạy | Tiền điều kiện | Hậu điều kiện | Event | Chặng |
|---|---|---|---|---|---|
| Nộp (`submit`) | Người dùng nộp lần đầu | Chưa có hồ sơ; thông tin hợp lệ | Hồ sơ `PENDING_REVIEW` | `SellerProfileSubmitted` | C1a |
| Sửa thông tin gian hàng (`updateShopInfo`) | Chủ hồ sơ sửa | `PENDING_REVIEW` hoặc `APPROVED` | Phiên bản tăng, trạng thái giữ nguyên | | C1a |
| Sửa thông tin pháp lý, loại hình (`updateLegalInfo`) | Chủ hồ sơ sửa | `PENDING_REVIEW` | Phiên bản tăng, `identityKey` tính lại | | C1a |
| Sửa và nộp lại (`resubmit`) | Chủ hồ sơ sửa sau khi bị từ chối | `REJECTED` | `PENDING_REVIEW`, xóa lý do từ chối | `SellerProfileSubmitted` | C1a |
| Duyệt (`approve`) | Quản trị duyệt | Vai `ADMIN`; `PENDING_REVIEW`; đúng phiên bản đã xem (kiểm theo thứ tự này) | `APPROVED`, ghi người và thời điểm quyết định, phiên bản tăng một | `SellerProfileApproved`, `AuditActionRecorded` (aggregate phát domain event, handler ghi hộp thư) | C1a |
| Từ chối (`reject`) | Quản trị từ chối | Vai `ADMIN`; có lý do (không trống, tối đa 500); `PENDING_REVIEW`; đúng phiên bản đã xem | `REJECTED`, ghi lý do, người và thời điểm quyết định, phiên bản tăng một | `SellerProfileRejected`, `AuditActionRecorded` | C1a |
| Khóa, mở | Quản trị | `APPROVED` / `SUSPENDED` | Đổi trạng thái | Chưa đặt tên | C1b |

**Người thực hiện (`SellerActor`):** mã tài khoản và vai, dựng ở lớp trình bày từ token; mọi hành vi của chủ hồ sơ từ chối vai không phải `USER`, duyệt và từ chối từ chối vai không phải `ADMIN` (403 `SELLER_ROLE_NOT_ALLOWED`, INV-SEL-05). Miền không biết token.

**Cổng (do miền khai báo, cài đặt ở hạ tầng):** kho hồ sơ (lưu; tìm theo chủ; báo sớm tên gian hàng và khóa định danh đã dùng); cổng sinh mã (ADR-0001). Truy vấn có bộ lọc và phân trang (hồ sơ chờ duyệt, feature 04) là cổng đọc riêng ở tầng ứng dụng (`PendingSellerProfileQuery`), không nằm ở miền. Kho còn có đọc hồ sơ để quyết định bằng khóa dòng (`SELECT ... FOR UPDATE`, feature 04). Chuẩn hóa tên gian hàng và tính khóa định danh là hàm thuần của miền. Đồng hồ là đầu vào của hàm hành vi, không phải cổng.

## 3. Nghiệp vụ liên aggregate

Nghiệp vụ chạm tập hợp hồ sơ hoặc nhiều thao tác cùng giao dịch, không thuộc riêng một hồ sơ. Cột "Cơ chế": **dịch vụ miền** (thao tác trên tập hợp), **ràng buộc lưu trữ** (chốt cuối khi đua nhau), **cùng giao dịch** (nhiều ghi trong một giao dịch).

| Nghiệp vụ | Tham gia | Cơ chế | Nhất quán | Bất biến | Chặng | Nhãn |
|---|---|---|---|---|---|---|
| Mỗi tài khoản có tối đa một hồ sơ | Hồ sơ (tập hợp) | Dịch vụ miền báo sớm qua kho; chốt cuối là ràng buộc duy nhất theo chủ ở lưu trữ | Cùng giao dịch | INV-SEL-01 | C1a | Đã làm |
| Tên gian hàng duy nhất toàn sàn | Hồ sơ (tập hợp) | Dịch vụ miền báo sớm; chốt cuối là ràng buộc duy nhất trên khóa so sánh | Cùng giao dịch | INV-SEL-04 | C1a | Đã làm |
| Khóa định danh chỉ có một hồ sơ hiệu lực | Hồ sơ (tập hợp) | Dịch vụ miền báo sớm; chốt cuối là chỉ mục duy nhất một phần chỉ tính hồ sơ chưa bị từ chối | Cùng giao dịch | INV-SEL-09 | C1a | Đã làm |
| Duyệt, từ chối đi cùng hành động quản trị gửi Nhật ký kiểm toán | Hồ sơ, hộp thư event | Đổi trạng thái hồ sơ và ghi cả sự kiện kết quả lẫn sự kiện kiểm toán vào hộp thư trong **một** giao dịch (use case lưu rồi phát domain event; event handler đồng bộ ghi hộp thư, mục 7) | Cùng giao dịch; chuyển đi sau, ít nhất một lần | INV-SEL-07 | C1a | Đã làm |

## 4. Use case

| Use case | Năng lực | Chặng | Nhãn |
|---|---|---|---|
| Nộp hồ sơ người bán (`CMD-SEL-01`) | CAP-SEL-01 | C1a | Đã làm |
| Sửa thông tin gian hàng (`CMD-SEL-02`) | CAP-SEL-02 | C1a | Đã làm |
| Sửa thông tin pháp lý, loại hình (`CMD-SEL-03`) | CAP-SEL-02 | C1a | Đã làm |
| Sửa và nộp lại (`CMD-SEL-04`) | CAP-SEL-02 | C1a | Đã làm |
| Duyệt (`CMD-SEL-05`), kèm phiên bản đã xem | CAP-SEL-03 | C1a | Đã làm |
| Từ chối (`CMD-SEL-06`), kèm phiên bản đã xem và lý do | CAP-SEL-03 | C1a | Đã làm |
| Hồ sơ của tôi (`RM-SEL-01`) | CAP-SEL-02 | C1a | Đã làm |
| Danh sách hồ sơ chờ duyệt, chỉ thông tin cơ bản, không có dữ liệu định danh (`RM-SEL-02`) | CAP-SEL-03 | C1a | Đã làm |
| Chi tiết một hồ sơ ở mọi trạng thái, đầy đủ trường kèm phiên bản (`RM-SEL-03`) | CAP-SEL-03 | C1a | Đã làm |
| Khóa, mở người bán | | C1b | Dự kiến |

**API của feature 03 · Đã làm** (chi tiết `api.yaml`): `POST /api/seller/profile` (nộp), `GET /api/seller/profile` (hồ sơ của tôi, 404 khi chưa có), `PATCH /api/seller/profile/shop-info`, `PATCH /api/seller/profile/legal-info` (sửa một phần), `POST /api/seller/profile/resubmit` (toàn bộ trường). Chủ hồ sơ luôn là `sub` của token. Mã lỗi: 400 `LEGAL_INFO_REQUIRED`, `INVALID_IDENTITY_NUMBER`, `INVALID_TAX_CODE`, `SELLER_FIELD_INVALID`; 403 `SELLER_ROLE_NOT_ALLOWED`; 404 `SELLER_PROFILE_NOT_FOUND`; 409 `SELLER_PROFILE_EXISTS`, `SHOP_NAME_TAKEN`, `IDENTITY_ALREADY_REGISTERED`, `LEGAL_INFO_LOCKED`, `INVALID_STATE`, `CONCURRENT_UPDATE` (và `STALE_VERSION` ở feature 04).

**API của feature 04 · Đã làm** (chi tiết `api.yaml`), chỉ vai `ADMIN`: `GET /api/admin/seller-profiles?page&size` (danh sách chờ duyệt, cũ nhất trước, chỉ thông tin cơ bản: `id`, `sellerType`, `shopName`, `submittedAt`; `size` 1 đến 50, mặc định 20), `GET /api/admin/seller-profiles/{profileId}` (chi tiết đủ trường kèm `version`, mọi trạng thái), `POST .../{profileId}/approve` nội dung `{"version"}`, `POST .../{profileId}/reject` nội dung `{"version", "reason"}`. Mã lỗi thêm: 400 `REJECTION_REASON_REQUIRED`; 409 `STALE_VERSION`; dùng lại `INVALID_STATE`, `SELLER_PROFILE_NOT_FOUND`, `SELLER_ROLE_NOT_ALLOWED`, `SELLER_FIELD_INVALID`. Mã hồ sơ hoặc tham số sai kiểu: 400 `SELLER_FIELD_INVALID` (nêu tên tham số, không lặp lại giá trị). Phản hồi của duyệt, từ chối trả `version` **sau** quyết định (hồ sơ chỉ giữ phiên bản đã nạp).

**Quyền (quy tắc nghiệp vụ do miền hoặc use case tự kiểm, không chú thích phân quyền; mục 7):** nộp, sửa, xem hồ sơ của tôi: vai `USER` và chủ hồ sơ; danh sách, chi tiết, duyệt, từ chối: vai `ADMIN`. Kiểm chủ hồ sơ nằm trong miền. Chính sách thuộc tính (ABAC) hoãn tới cổng Vận hành.

## 5. Event

### Phát

| Event | Khi nào | Nội dung chính | Bên nhận | Chặng | Nhãn |
|---|---|---|---|---|---|
| `SellerProfileApproved` (`EVT-SEL-01`) | Sau khi duyệt | Chủ đề `seller.profile.approved`, khóa thông điệp = mã tài khoản; nội dung `accountId`, `profileId`, `version` (sau quyết định), `occurredAt`. **Không** mang thông tin pháp lý, **không** mang tên gian hàng | Danh mục hàng | C1a | Đã làm |
| `SellerProfileRejected` (`EVT-SEL-02`) | Sau khi từ chối | Chủ đề `seller.profile.rejected`, khóa thông điệp = mã tài khoản; nội dung `accountId`, `version` (sau quyết định), `occurredAt`. **Không** mang lý do | Danh mục hàng; Thông báo (sau này) | C1a | Đã làm |
| `SellerProfileSubmitted` (`EVT-SEL-03`) | Sau khi nộp hoặc nộp lại | Chủ đề `seller.profile.submitted`, khóa thông điệp = mã tài khoản; nội dung `accountId`, `profileId`, `submittedAt`. Không mang loại hình, thông tin pháp lý, tên | Chưa ai nhận | C1a | Đã làm |
| `AuditActionRecorded` (`EVT-SEL-04`) | Sau khi duyệt hoặc từ chối | Hợp đồng bản ghi của Nhật ký kiểm toán: người thực hiện (mã tài khoản, vai), hành động `SELLER_PROFILE_APPROVED` / `SELLER_PROFILE_REJECTED`, đối tượng (loại, mã hồ sơ), dữ kiện bổ sung (phiên bản đã xem, mã tài khoản người bán, lý do từ chối), thời điểm xảy ra, mã truy vết, BC nguồn | Nhật ký kiểm toán | C1a | Đã làm |

Các event mang **trạng thái kèm phiên bản** để bên nhận chỉ chấp nhận bản mới hơn (C1b thêm sự kiện bị khóa). Ghi vào hộp thư cùng giao dịch với đổi hồ sơ (mục 3), chuyển ra bus sau. Khóa phân vùng: mã tài khoản, để thứ tự đúng theo từng người bán. Định dạng JSON ở giai đoạn này (`tech-stack.md`).

### Nhận

Không — upstream thuần ở C1a. Chỉ đọc mã tài khoản và vai từ hợp đồng danh tính của Xác thực (mục 6).

## 6. Giao diện giữa BC

| Giao diện | Hướng | Nội dung | Chặng | Nhãn |
|---|---|---|---|---|
| Hợp đồng danh tính | Xác thực công bố, Người bán đọc từ token | Mã tài khoản (`sub`) làm chủ hồ sơ, vai (`role`) để kiểm quyền. Người bán tự kiểm token, không gọi lại Xác thực (ADR-0003) | C1a | Đã làm |
| Kết quả duyệt | Người bán phát, Danh mục hàng nhận | `SellerProfileApproved`, `SellerProfileRejected`; Danh mục dựng bản sao trạng thái người bán, không gọi đồng bộ | C1a | Đã làm (phía phát, chạy thật trên Kafka) · Dự kiến (phía nhận, Danh mục) |
| Hành động quản trị | Người bán phát, Nhật ký kiểm toán nhận | `AuditActionRecorded` | C1a | Đã làm (phía phát, chạy thật trên Kafka) · Dự kiến (phía nhận chạy cùng Nhật ký kiểm toán) |
| API cho ứng dụng Người bán và Vận hành | Ứng dụng gọi qua BFF và cổng truy cập | Mục 4; danh tính từ token do BFF chuyển tiếp | C1a | Đã làm |

## 7. Khái niệm kỹ thuật

| Khái niệm | Kết luận | ADR |
|---|---|---|
| Mã định danh thực thể | UUID v7 sinh trong miền qua cổng sinh mã, lưu kiểu `uuid` | ADR-0001 |
| Danh tính và vai | Đọc `sub` và `role` từ token do Xác thực cấp; Người bán không giữ phiên | ADR-0003 |
| Hộp thư event | Ghi cùng giao dịch với đổi hồ sơ, chuyển ra bus sau (ít nhất một lần); bên nhận khử trùng theo mã event | ADR-0008 |
| Quyết định duyệt, từ chối | Đọc hồ sơ bằng `SELECT ... FOR UPDATE` để các giao dịch đua nhau xếp hàng; miền kiểm vai, lý do, trạng thái, phiên bản đã xem theo thứ tự đó. Phiên bản đã xem nằm trong nội dung yêu cầu, không dùng `ETag` | ADR-0011 |
| Hộp thư và kiểm toán của quyết định | Aggregate phát domain event, use case lưu rồi `dispatchAll`; event handler đồng bộ cùng giao dịch ghi hộp thư kết quả và bản ghi kiểm toán (một handler một việc); handler ném lỗi thì rollback cả giao dịch. Nộp và nộp lại cũng theo khuôn này: `SellerProfile.submit` và `resubmit` phát `SellerProfileSubmitted`, `SellerProfileSubmittedHandler` chỉ ghi hộp thư (không ghi kiểm toán) | ADR-0008 |
| Khóa lạc quan | Cột phiên bản chạy ngầm (người bán sửa không gửi phiên bản; đụng nhau đúng lúc thì `CONCURRENT_UPDATE`); duyệt và từ chối kèm phiên bản đã xem, lệch thì `STALE_VERSION` | ADR-0011 (Accepted) |
| Tính duy nhất khi đua nhau | Ràng buộc duy nhất ở lưu trữ làm chốt cuối: theo chủ, theo khóa so sánh tên gian hàng, chỉ mục duy nhất một phần trên khóa định danh (chỉ hồ sơ chưa bị từ chối); vi phạm thì rollback cả giao dịch, đổi mã lỗi theo tên ràng buộc | ADR-0011 (Accepted) |
| Dữ liệu định danh | Số căn cước, mã số thuế, địa chỉ kinh doanh không đưa vào danh sách trường được ghi log, không đưa vào event; danh sách chờ duyệt không có các trường này; mã hóa cột là điểm nối chưa làm | Quy ước `logging.md` |
| Phân quyền | Chủ hồ sơ lấy từ `sub` của token (xác thực bằng resource server); vai là quy tắc nghiệp vụ do miền tự kiểm, không chú thích phân quyền; ABAC hoãn tới cổng Vận hành | Quyết định `2026-10-26` |
| Định dạng event | JSON; nâng Avro kèm Schema Registry là hướng sau | `tech-stack.md` |

## 8. Hạ tầng cần ở C1a

Cơ sở dữ liệu quan hệ riêng của dịch vụ, bus sự kiện kèm hộp thư event (connector chuyển hộp thư ra bus), cổng truy cập và route cho dịch vụ, quan sát (log, truy vết) theo quy ước chung. Chưa cần Redis. Cổng Người bán và cổng Vận hành (ứng dụng giao diện, BFF, client đăng ký riêng) là feature giao diện, không thuộc dịch vụ này.

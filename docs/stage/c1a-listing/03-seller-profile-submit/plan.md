# Feature 03: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần thường, người rà), và cách biết bước đã xong. Chọn cách thực hiện (tự viết hay giao `agy`) khi bắt đầu từng bước, không chốt trước.

Quy tắc giống C0, feature 01 và 02: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `tech.md` T11 trước khi viết mã (đỏ trước, phá thử sau); quyết định kỹ thuật đặt ngay trước bước cần dùng nó; một lượt `agy` làm hoặc mã chính hoặc test, không cả hai.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu viết **trước khi code**; cột cuối điền **sau khi làm xong** (`work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Xác thực token, dựng người thực hiện (mã tài khoản, vai) | Resource server với `jwk-set-uri`; `sub` và `role` đọc thành `SellerActor`; bộ lọc chỉ đòi đã đăng nhập (`tech.md` T8) | `seller-service` hạ tầng: cấu hình bảo mật; trình bày: bộ dựng `SellerActor` | Chưa làm |
| Kiểm vai là `USER` | Quy tắc nghiệp vụ của miền, ném `SellerRoleNotAllowedException` (`tech.md` T2) | `domain`: `SellerActor` và các hành vi của `SellerProfile` | Đã làm (bước 3): `requireUser` trước mọi kiểm khác; KT-03 xanh |
| Dựng giá trị: chuẩn hóa, kiểm đủ trường theo loại hình và định dạng | Giá trị tự kiểm khi dựng, lỗi nêu tên trường (`tech.md` T2) | `domain`: `ShopName`, `IdentityNumber`, `TaxCode`, `Phone`, `Address`, `LegalInfo`, `Contact`, `ShopInfo` | Đã làm (bước 3): KT-01, 02 xanh |
| Báo sớm trùng chủ, tên, khóa định danh | Dịch vụ miền hỏi kho trước khi lưu (`tech.md` T2, T4) | `domain`: `SellerProfileUniqueness`; cổng `SellerProfileRepository` | Đã làm (bước 3): phần miền; kho thật ở bước 4, use case ở bước 5 |
| Tạo hồ sơ `PENDING_REVIEW`, phiên bản 1 | Hành vi `submit` của aggregate, giờ là đầu vào (`tech.md` T2) | `domain`: `SellerProfile` | Đã làm (bước 3): `submit`, `restore`; KT-03 xanh |
| Lưu, ràng buộc duy nhất và khóa lạc quan làm chốt cuối; vi phạm thì rollback cả giao dịch | `saveAndFlush`, ba ràng buộc có tên, `@Version`; bộ xử lý lỗi đổi theo tên ràng buộc (`tech.md` T3, T4, ADR-0011) | `infrastructure/persistence`: entity, mapper, kho; migration V1; `presentation`: `SellerRequestExceptionHandler` | Chưa làm |
| Ghi hàng hộp thư `SellerProfileSubmitted` cùng giao dịch | Aggregate phát event, use case `dispatchAll` sau `saveAndFlush`, handler gọi `OutboxEventStore.store` (`tech.md` T5; đồng bộ `2026-11-19`, bản đầu use case gọi thẳng) | `application`: `SubmitSellerProfile`, `ResubmitSellerProfile`; migration V2 (`outbox_events`) | Chưa làm |
| Sửa: chọn thao tác theo trạng thái, gộp trường được gửi vào giá trị hiện tại, báo sớm trùng (bỏ qua chính hồ sơ), không đổi thì không tăng phiên bản | Ma trận hành động × trạng thái trong aggregate; `PATCH` một phần; `@Version` ngầm bắt xung đột đồng thời (`tech.md` T2, T4) | `domain`: `SellerProfile`; `application`: `UpdateShopInfo`, `UpdateLegalInfo`, `ResubmitSellerProfile` | Chưa làm |
| Hồ sơ của tôi | Đọc theo mã tài khoản trong token, 404 khi chưa có (`tech.md` T6) | `application`: `GetMySellerProfile`; `presentation`: bộ điều khiển | Chưa làm |
| Dữ liệu định danh không rò ra log, lỗi, event | `toString` che, thông báo lỗi nêu tên trường, danh sách che của `BodySanitizer` (`tech.md` T7) | Giá trị miền; `presentation`: bộ xử lý lỗi; `observability-starter` nếu thiếu tên trường | Chưa làm |
| Chuyển hộp thư lên chủ đề `seller.profile.submitted` | Connector `seller` của feature 01; không thêm mã (`tech.md` T5, T9) | Hạ tầng dev: `postgres-seller`, script đăng ký connector | Chưa làm |

## Điều kiện bắt đầu

Đã chốt, không bàn lại: ADR-0001 (UUID v7), ADR-0008 và ADR-0010 (hộp thư, nhận event, hàng chờ lỗi), ADR-0003 (hợp đồng danh tính), Q1 (chủ hồ sơ lấy từ token, vai là quy tắc nghiệp vụ của miền), Q2 (không bắt gửi phiên bản, `@Version` chạy ngầm), Q3 (mã số thuế của cá nhân tùy chọn), Q4 (chưa có hồ sơ thì 404), cả bốn chốt `2026-10-26`. **Ghi hạ tầng dev:** thêm `postgres-seller` vào `infra/docker-compose.yml` (bước 7) và đăng ký connector `seller` vào Kafka Connect dev: **báo trước**; kiểm thử dùng Testcontainers và stack riêng có tiền tố `probe-`, không dùng cơ sở dữ liệu và Kafka dev. Cần Docker chạy được.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 4 và `tech.md` T11: mỗi đường không suôn sẻ E1 đến E18 có KT hoặc lý do không cần chưa; KT-13 là ca chạy thật (bước 7), còn lại nằm trong Maven. Xác nhận Q2 đến Q4. Quyết định bước nào giao `agy`.

Xong khi: bảng đối chiếu E1 đến E18 với KT khớp; Q2 đến Q4 ghi "Đã chốt" ở `tech.md`.

**Đối chiếu E1 đến E18 với KT** (`2026-10-26`):

| Đường | KT kiểm | Ghi chú |
|---|---|---|
| E1 đã có hồ sơ, bấm hai lần | KT-04, KT-07, KT-10 | KT-07 là ca đua nhau cùng chủ trên cơ sở dữ liệu thật |
| E2 trùng tên (khác dấu, hoa, khoảng trắng) | KT-01, KT-04, KT-07, KT-10 | KT-01 kiểm chuẩn hóa khóa tên, KT-07 kiểm đua nhau |
| E3 trùng khóa định danh | KT-04, KT-07, KT-10 | Thông báo không nêu bên trùng |
| E4 trùng khóa với hồ sơ `REJECTED` | KT-04, KT-06 | KT-06 kiểm chỉ mục một phần ở cơ sở dữ liệu (bổ sung ở bước này) |
| E5 thiếu pháp lý theo loại hình | KT-01, KT-02, KT-10 | |
| E6 sai số căn cước, mã số thuế | KT-01, KT-10 | |
| E7 độ dài, số điện thoại, địa chỉ | KT-01, KT-10 | |
| E8 `ADMIN` nộp, sửa | KT-03, KT-11 | Kiểm vai ở miền (bổ sung vào KT-03 ở bước này) |
| E9 không token, token sai | KT-11 (không token); KT-13 (sai chữ ký, hết hạn) | MockMvc không kiểm chữ ký (bổ sung vào KT-13) |
| E10 sửa hồ sơ người khác | KT-10 | Cô lập hai tài khoản (bổ sung ở bước này) |
| E11 sửa pháp lý khi `APPROVED` | KT-03, KT-10 | |
| E12 sai trạng thái | KT-03, KT-10 | |
| E13 hai sửa đồng thời | KT-05, KT-08 | KT-08 là ca đua nhau thật |
| E14 sửa không đổi gì | KT-03, KT-09 | KT-09 kiểm không ghi hàng hộp thư |
| E15 nộp lại khi khóa đã bị dùng | KT-05, KT-06 | |
| E16 Kafka hay bộ chuyển tạm hỏng | KT-09 (hàng nằm lại), KT-13 (khởi động lại, không mất) | Chỉ KT-13 chạy thật; Maven không dựng Kafka cho feature này |
| E17 rollback không để hồ sơ, không hàng hộp thư | KT-07, KT-09 | |
| E18 không rò dữ liệu định danh | KT-01, KT-12 | |

Đối chiếu thấy bốn chỗ hở, đã bổ sung vào `tech.md` T11: KT-03 thêm kiểm vai ở miền (E8); KT-06 thêm chỉ mục một phần và ca nộp lại khi khóa bị dùng (E4, E15); KT-10 thêm cô lập hai tài khoản và `ADMIN` đọc thì 404 (E10); KT-13 thêm token sai chữ ký, hết hạn (E9). Mọi đường E1 đến E18 có ít nhất một KT.

**Đề xuất giao `agy`:** bước 3, 4, 5, 6 (khung chặt; mỗi bước hai lượt riêng, lượt một viết test từ `tech.md` T11 và `flow.md`, lượt hai viết mã cho test đỏ chuyển xanh, theo quy tắc một lượt một loại việc). Tự làm: bước 2 (ADR, `data.md`, `api.yaml`), bước 7 (hạ tầng dev, chạy thật), bước 8; riêng bước 4 (đua nhau, ràng buộc) và bước 6 (bảo mật, rò dữ liệu) tôi rà diff kỹ.

**Trạng thái:** Đã làm `2026-10-26`. Đối chiếu E1 đến E18 xong; Q2 đến Q4 đã chốt (Q2 đổi so với đề xuất đầu: không bắt gửi phiên bản; Q3 tùy chọn; Q4 là 404). Sửa theo Q2: bỏ `expectedVersion` khỏi ba lệnh sửa, thêm mã lỗi `CONCURRENT_UPDATE`, `STALE_VERSION` dời sang feature 04.

### Bước 2. ADR-0011, `data.md`, `api.yaml`, chi tiết `service.md` · Người, Agent soạn

Viết ADR-0011 (khóa lạc quan và tính duy nhất ở lưu trữ, trạng thái Proposed) từ `tech.md` T4. Viết `docs/service/seller-service/data.md` (bảng T3), `api.yaml` (T6), bổ sung chi tiết vào `service.md` cho phần của feature (nhãn "Dự kiến", đổi sau): trường, mã lỗi, chủ đề, khóa thông điệp. Thêm mục `seller-service` vào `5. event-catalog.md` cho `SellerProfileSubmitted` (nhãn Dự kiến, bên nhận chưa có).

Xong khi: ba tệp khớp `tech.md` T3, T5, T6; ADR có phương án đã loại.

**Trạng thái:** Đã làm `2026-10-26`. ADR-0011 (Proposed) và dòng ở `adr/README.md`; `data.md` (bảng `seller_profiles` với 3 ràng buộc duy nhất có tên, 8 ràng buộc kiểm, `outbox_events`); `api.yaml` (5 điểm cuối, request một phần, mã lỗi); `service.md` bổ sung chi tiết feature 03 (API, event `seller.profile.submitted`, ADR-0011, phân quyền); `5. event-catalog.md` mục `seller-service` đổi từ các event cũ sang `SellerProfileSubmitted`, `SellerProfileApproved`, `SellerProfileRejected` (Approved, Rejected là Dự kiến của feature 04; `SellerRatingUpdated` giữ). Mọi nhãn vẫn "Dự kiến" tới khi có mã. Chưa chạy gì (chỉ tài liệu).

### Bước 3. Miền · Agent (`agy`), người rà

Mô-đun `services/seller-service` (khung Maven, gói gốc `vn.t3nexus.seller`, chưa có Spring Boot chạy được ngoài cấu hình tối thiểu) và `domain/sellerprofile`: `SellerActor`, giá trị (T2), `LegalInfo`, `Contact`, `SellerProfile` với `submit`, `updateShopInfo`, `updateLegalInfo`, `resubmit`, `restore`, `SellerProfileRepository` (cổng), `SellerProfileUniqueness`, `SellerErrorCode`, ngoại lệ miền. Test KT-01, 02, 03 viết **trước**, từ `flow.md` và `analysis.md`, không từ mã.

Xong khi: KT-01 đến 03 xanh; phá thử: bỏ bước bỏ dấu `đ`, bỏ kiểm vai, bỏ kiểm ma trận trạng thái, để `toString` in số căn cước làm một test đỏ mỗi lần.

**Trạng thái:** Đã làm `2026-10-26`. Khung Maven `seller-service` (chỉ phụ thuộc `common-domain` và test; phụ thuộc hạ tầng thêm ở bước 4 đến 6) và 17 lớp miền ở `domain/sellerprofile`: `SellerProfile` kế thừa `AbstractAggregateRoot<SellerProfileId>`, `SellerProfileRepository` kế thừa `Repository` gốc (`delete` ném không hỗ trợ, `save` do hạ tầng cài), giá trị bất biến tự kiểm, `SellerProfileUniqueness` (domain service hỏi kho), `SellerErrorCode` (`code()` là tên hằng). 13 tệp test, **91 ca** (KT-01, 02, 03 và kiểm trùng bằng kho giả) xanh. **Quy trình `agy`:** lượt 1 chỉ viết test từ hợp đồng (234 giây, 84.932 token vào, 22.317 ra, 0 nghĩ; `gemini-3.8-flash-low`); lượt 2 chỉ viết mã chính cho test đỏ chuyển xanh (204 giây, 220.157 vào, 11.586 ra, 0 nghĩ). Đặc tả nêu rõ mười một quy tắc DDD (domain chỉ import `java.*` và `common.domain.*`, không đặt bộ lọc hay con trỏ ở domain, kế thừa lớp gốc của dự án, mã thuộc kiểu riêng, đồng hồ là tham số), và rà tay thấy mã chính tuân thủ (không import hạ tầng, không `Instant.now`). **Lỗi của `agy` đã sửa tay:** `FakeSellerProfileRepository.save` trả `SellerProfile` trong khi `Repository.save` trả `void` (tôi không nêu kiểu trả về trong hợp đồng); ba ca của `SellerProfileTest` dùng tên gian hàng `"Cũ"`, `"A"`, `"B"` (dưới 3 ký tự, trái hợp đồng) nên lỗi ở dữ liệu test, không phải mã. **Phá thử bảy cách đều làm test đỏ:** bỏ đổi `đ` thành `d`, bỏ kiểm vai, bỏ khóa pháp lý khi `APPROVED`, `toString` số căn cước in giá trị, bỏ kiểm đúng chủ, `resubmit` không xóa lý do, khóa định danh cá nhân lấy mã số thuế. Hai lỗi của script phá thử đã sửa (dòng kết thúc CRLF làm regex không khớp; gọi `./mvnw` qua `bash`).

### Bước 4. Lưu trữ · Agent (`agy`), người rà test đua nhau

Migration V1 (`seller_profiles`, ràng buộc có tên, chỉ mục duy nhất một phần), V2 (`outbox_events` theo mẫu `outbox-starter`), entity JPA, mapper, kho cài cổng, cấu hình kết nối và Flyway. Test KT-06, 07, 08 dùng Testcontainers Postgres 16.

Xong khi: KT-06 đến 08 xanh, kể cả 20 luồng đua nhau cho đúng một hồ sơ và hai sửa đồng thời cho đúng một thành công; phá thử: bỏ từng ràng buộc duy nhất, bỏ `@Version`, bỏ điều kiện `WHERE status <> 'REJECTED'` của chỉ mục: mỗi lần có test đỏ.

**Trạng thái:** Đã làm `2026-10-26`. Tôi dựng khung (`pom.xml` thêm JPA, Flyway, Postgres, Testcontainers; `SellerServiceApplication`; `application.properties` trỏ cổng dev 5441, test ghi đè bằng container). `agy` viết hai lượt riêng, theo hợp đồng lưu trữ nêu rõ: bảng, cột, tên ràng buộc, ánh xạ cột với aggregate, và cách giữ phiên bản chạy ngầm (kho `save` bắt buộc chạy trong giao dịch của bên gọi, nạp entity đang được quản lý rồi chép trường vào đó, `saveAndFlush`, không bắt ngoại lệ). **Quy trình `agy` (`gemini-3.8-flash-low`):** lượt 1 chỉ viết 6 tệp test (159,6 giây, 105.378 token vào, 17.010 ra, 0 nghĩ); lượt 2 chỉ viết 6 tệp mã chính: migration V1, V2, entity, kho Spring Data, mapper, adapter (121,1 giây, 186.747 vào, 5.475 ra, 0 nghĩ). **Kết quả:** 40 ca lưu trữ (KT-06: 12 ca lược đồ và 21 ca kho; KT-07: 3 ca, 20 luồng mỗi ca; KT-08: 4 ca) cộng 91 ca miền, tổng 131 xanh trên Postgres 16 của Testcontainers. **Lỗi của `agy` đã sửa tay (dữ liệu test, không phải mã chính):** hai ca kiểm `CHECK` cho `status` và `seller_type` lạ không thỏa các `CHECK` khác nên Postgres báo ràng buộc khác trước (kiểm theo thứ tự tên), và ca hộp thư chèn cột `id` tự sinh với `payload` ép `jsonb`. **Phá thử bảy cách đều đỏ:** bỏ duy nhất theo chủ (4 ca), bỏ duy nhất khóa tên (3), chỉ mục bỏ `WHERE` (4), bỏ `@Version` (23), bỏ `MANDATORY` (1), truy vấn khóa hiệu lực không loại hồ sơ bị từ chối (1), `save` luôn tạo entity mới (7). **Chưa kiểm:** hàng hộp thư cùng giao dịch và rollback cả hàng hộp thư (KT-07 phần đó, KT-09, bước 5); đổi tên ràng buộc thành mã lỗi (bước 6); Postgres thật ngoài Testcontainers (bước 7).

### Bước 5. Use case và hộp thư · Agent (`agy`), người rà

`application/sellerprofile`: `SubmitSellerProfile`, `ResubmitSellerProfile`, `UpdateShopInfo`, `UpdateLegalInfo`, `GetMySellerProfile`; `SellerProfileSubmitted`; cấu hình `OutboxEventStore` và `IdGenerator`. Test KT-04, 05, 09.

Xong khi: KT-04, 05, 09 xanh; phá thử: cho phép ghi hộp thư ngoài giao dịch (ADR-0008 phải đỏ), bỏ kiểm lại khóa lúc nộp lại, ghi hàng hộp thư khi sửa gian hàng: mỗi lần có test đỏ.

**Trạng thái:** Đã làm `2026-10-26`. Tôi thêm `outbox-starter`, `common-events` vào `pom.xml` và `JpaConfig` (hộp thư tự khai báo `@EnableJpaRepositories` nên Spring Boot thôi quét kho của dịch vụ; khai báo lại). `agy` viết hai lượt riêng: lượt 1 chỉ viết 9 tệp test (255,8 giây, 123.504 token vào, 26.491 ra, 0 nghĩ), lượt 2 chỉ viết 8 tệp mã chính (123,2 giây, 134.737 vào, 6.917 ra, 0 nghĩ), `gemini-3.8-flash-low`. Mã chính: sự kiện miền `SellerProfileSubmitted` (payload ba trường), `Patch<T>` (vắng giữ, `null` xóa), năm use case (`SubmitSellerProfile`, `ResubmitSellerProfile`, `UpdateShopInfo`, `UpdateLegalInfo`, `GetMySellerProfile`), `IdGeneratorConfig`; nộp và nộp lại ghi hộp thư cùng giao dịch, sửa và đọc không ghi; đọc dùng `findByOwner` của cổng kho miền (cổng `SellerProfileQuery` để dành cho feature 04). **Kết quả:** 183 test xanh toàn mô-đun (52 ca mới: 42 đơn vị với kho giả và hộp thư giả, 8 tích hợp trên Postgres thật gồm 20 luồng đua cùng chủ và cùng tên chỉ để lại một hồ sơ và một hàng hộp thư, nộp ngoài giao dịch bị từ chối). **Lỗi tìm ra nhờ test mới, sửa tay ở miền (bước 3):** `ShopInfo.equals` so theo khóa tên nên người bán đổi riêng hoa thường hay dấu của tên gian hàng bị coi là "không đổi" và không lưu; đổi sang so theo bản hiển thị, thêm hai test miền, sửa hai test cũ của bước 3 vốn mã hóa ngữ nghĩa cũ. **Lỗi của `agy` đã sửa tay (test):** gọi `fieldNames()` và `asText()` của Jackson 2 trong khi dự án dùng Jackson 3 (`propertyNames()`, `asString()`); một ca bị nhiễm do kho giả giữ cùng đối tượng đã đổi sau lỗi (đổi thứ tự bước trong ca). **Phá thử tám cách đều đỏ:** bỏ `@Transactional` của nộp (7 ca lỗi), bỏ kiểm lại khóa lúc nộp lại, nộp lại không ghi hộp thư, bỏ kiểm vai trước định dạng, thêm trường vào payload, bỏ kiểm trùng khóa khi sửa pháp lý, sửa không đổi vẫn lưu, bỏ kiểm trùng tên khi sửa gian hàng. **Lệch nhỏ so với `flow.md` mục 3.2:** use case sửa dựng và kiểm định dạng giá trị trước, rồi mới để miền kiểm trạng thái, nên yêu cầu vừa sai định dạng vừa sai trạng thái trả 400 chứ không phải 409; chấp nhận được, test không khẳng định thứ tự này. **Chưa làm (bước 6):** log, bộ đếm, API.

### Bước 6. API, bảo mật, log · Agent (`agy`), người rà bảo mật

Bộ điều khiển và DTO cho năm điểm cuối (T6), bộ dựng `SellerActor` từ token, cấu hình resource server (chỉ đòi đăng nhập), bộ xử lý lỗi đổi tên ràng buộc và xung đột đồng thời thành mã lỗi, log và bộ đếm (T10), danh sách che của `BodySanitizer` (T7). Test KT-10, 11, 12.

Xong khi: KT-10 đến 12 xanh; phá thử: in giá trị vào thông báo lỗi, bỏ bộ xử lý lỗi một ràng buộc, nhận mã tài khoản từ nội dung yêu cầu: mỗi lần có test đỏ.

**Trạng thái:** Đã làm `2026-10-27`. Tôi thêm phụ thuộc web, resource server, `common-web`, `observability-starter`, test bảo mật vào `pom.xml`, và cấu hình `application.properties` (khóa công khai, danh sách che nội dung log, tắt hai logger Hibernate). `agy` viết hai lượt riêng (`gemini-3.8-flash-low`): lượt 1 chỉ viết 6 tệp test (231,1 giây, 91.286 token vào, 20.231 ra, 0 nghĩ; lần đầu thất bại vì đặc tả 33KB vượt giới hạn tham số dòng lệnh của Windows, nên phải để hợp đồng thành tệp trong thư mục làm việc và chỉ gửi phần đầu), lượt 2 chỉ viết 8 tệp mã mới và sửa 2 use case (233,6 giây, 237.696 vào, 10.914 ra, 0 nghĩ). **Mã chính:** `SecurityConfig` (chỉ đòi đăng nhập `/api/seller/**`, còn lại từ chối, không `@PreAuthorize`), `SellerActorResolver` (mã tài khoản từ `sub`, vai từ `role`), `PatchBodyReader` (phân biệt vắng với `null` cho `PATCH`), DTO yêu cầu và phản hồi (không có phiên bản, `toString` che), `SellerProfileController` (năm điểm cuối; log và bộ đếm đặt ở bộ điều khiển, khác chữ "use case ghi log" của T10 để không đổi hàm tạo năm use case; `UpdateShopInfo.Result` và `UpdateLegalInfo.Result` thêm `changed`), `SellerExceptionHandler` (đổi tên ràng buộc và xung đột đồng thời thành mã lỗi, ràng buộc lạ thành 500, không log thông báo gốc). **Kết quả:** 43 ca trình bày mới (đơn vị, bộ xử lý lỗi, 21 ca API qua MockMvc với `jwt()` và Postgres thật, 6 ca rò dữ liệu và log), toàn mô-đun 226 xanh. **Rò dữ liệu phát hiện ở bước này:** (1) Hibernate in nguyên thông báo Postgres, trong đó dòng `Detail: Key (identity_key)=(...)` chứa số căn cước thật: tắt hai logger `org.hibernate.orm.jdbc.error` và `SqlExceptionHelper`; (2) danh sách che nội dung yêu cầu thiếu `personName`: đã thêm. Cả hai chỉ lộ ra sau khi phá thử cho thấy ba phá thử đầu sống sót (test cũ rỗng: log HTTP dùng trường có cấu trúc nên không hiện trong văn bản console, và test bộ xử lý lỗi dựng ngoại lệ có thông báo ngắn khác Spring thật); tôi bổ sung hai test (kiểm bộ làm sạch với cấu hình thật, và gây vi phạm cơ sở dữ liệu thật rồi chụp log). **Lỗi của hợp đồng tôi và của `agy` đã sửa:** hợp đồng bảo dùng `PSQLException` nhưng driver chỉ ở phạm vi `runtime` (bỏ nhánh đó, Hibernate `ConstraintViolationException` và regex dự phòng đủ); MockMvc mặc định mã hóa ISO-8859-1 cho yêu cầu và phản hồi nên tiếng Việt vào DB sai (ép UTF-8 ở lớp cơ sở test, máy chủ thật không bị); `SecurityConfig` làm vỡ các test cũ chạy `web-application-type=none` nên chỉ áp dụng khi là ứng dụng servlet. **Phá thử tám cách đều đỏ:** log thông báo gốc của lỗi cơ sở dữ liệu, bỏ ánh xạ ràng buộc khóa định danh, cho qua đường lạ, in số căn cước vào log nộp, vai luôn là `USER`, lỗi khóa lạc quan không đổi mã, bật lại logger Hibernate, bỏ che nội dung log. **Lệch nhỏ:** bộ điều khiển đọc và kiểm kiểu thân yêu cầu trước khi use case kiểm vai, nên `ADMIN` gửi thân sai kiểu nhận 400 thay vì 403; không lộ dữ liệu, chấp nhận. **Chưa kiểm:** chữ ký token thật, token hết hạn và sai chữ ký (MockMvc `jwt()` bỏ qua giải mã: KT-13, bước 7).

### Bước 7. Hạ tầng dev và chạy thật · Người, Agent hỗ trợ

Thêm `postgres-seller` vào `infra/docker-compose.yml` (**báo trước**). Chạy thật trong stack cô lập `probe-` (Postgres `wal_level=logical`, Kafka, Connect, nguồn khóa công khai thử để ký token): nộp qua HTTP với token ký thật, kiểm hàng hộp thư và thông điệp trên `seller.profile.submitted`, đua nhau trên cơ sở dữ liệu thật, khởi động lại giữa chừng không mất event. Ghi số đo và kết quả vào `tech.md`. Đăng ký connector `seller` vào Kafka Connect dev chỉ khi người đồng ý (**báo trước**).

Xong khi: KT-13 đạt trên stack thật; stack gỡ sạch; hạ tầng dev không bị đụng ngoài phần đã báo.

**Trạng thái:** Đã làm một phần `2026-10-27`. Tôi thêm `postgres-seller` (cổng 5441) vào `infra/docker-compose.yml` và mục hướng dẫn vào `infra/README.md`; chưa dựng container dev, chưa đăng ký connector `seller` vào Kafka Connect dev. KT-13 chạy thật trên stack `probe-`: Flyway, connector, nộp ra đúng một thông điệp không dữ liệu định danh, năm kiểu token xấu bị 401, `ADMIN` bị 403, ba cuộc đua 20 luồng (cùng chủ, cùng tên, cùng số căn cước) đúng một thắng, log không rò. **Chưa kiểm:** dừng rồi chạy lại Connect giữa chừng và số đo độ trễ, vì máy thiếu bộ nhớ nên lần chạy có Kafka và Connect bị dừng; chi tiết ở `tech.md` T11. Stack thử đã gỡ sạch. Tôi đóng bước ở mức này theo quyết định của người.

### Bước 8. Phá thử tổng và đóng feature · Người

Chạy cả bộ test của `seller-service` và các mô-đun dùng chung bị ảnh hưởng. Cập nhật `service.md` (nhãn "Đã làm"), `data.md`, `api.yaml`, `5. event-catalog.md`, ADR-0011 (Accepted) khớp mã; viết `deferred.md` (chỉ phần hoãn có chỗ trong phạm vi, ví dụ mã hóa cột định danh); thêm điều học được vào `00-features.md`.

Xong khi: mọi KT xanh; tài liệu khớp mã; `00-features.md` ghi "Đã làm".

**Trạng thái:** Đã làm `2026-10-27`. **Test:** chạy `seller-service` cùng năm mô-đun nó phụ thuộc (`common-domain` 5, `common-events` 55, `common-web` 12, `observability-starter` 70, `outbox-starter` 10, `seller-service` 226): 378 ca xanh, không bỏ qua ca nào. Các dịch vụ khác dùng chung thư viện (`audit-service`, `oauth2-service`, `identity-service`) chưa chạy lại ở bước này vì feature 03 không sửa thư viện dùng chung. **Tài liệu:** `service.md` đổi nhãn "Đã làm" cho nộp, sửa gian hàng, sửa pháp lý, nộp lại, hồ sơ của tôi, `SellerProfileSubmitted`, ba nghiệp vụ duy nhất, hợp đồng danh tính, API của chủ hồ sơ (duyệt, từ chối, danh sách chờ duyệt giữ "Dự kiến"); sửa chỗ đoạn mô tả API chen giữa bảng use case. `data.md` đối chiếu với migration `V1`, `V2` rồi đổi nhãn. `api.yaml` đối chiếu với controller (đường dẫn, phương thức, mã trả về). `5. event-catalog.md` ghi `Submitted` là Đã làm. ADR-0011 chuyển Accepted, sáu mục "Cần kiểm" đã đánh dấu. Viết `deferred.md` (3 mục: mã hóa cột định danh; Connect dừng/chạy lại và độ trễ; container dev của `postgres-seller` kèm đăng ký connector). `00-features.md` ghi feature 03 "Đã làm" và thêm điều học được. **Chưa làm / còn mở:** các ca chưa kiểm ở bước 7 (nằm ở `deferred.md`); chưa chạy lại test của dịch vụ ngoài chuỗi phụ thuộc của `seller-service`; lệch nhỏ đã biết: `ADMIN` gửi thân sai kiểu nhận 400 thay vì 403, và log, bộ đếm đặt ở bộ điều khiển thay vì use case.

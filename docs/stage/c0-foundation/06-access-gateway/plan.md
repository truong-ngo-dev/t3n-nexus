# Feature 06: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần
thường, người rà), điều cần trao đổi (nếu có), và cách biết bước đã xong. Chọn cách thực hiện khi bắt đầu từng bước.

Quy tắc giống feature 01 đến 04: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `flow.md` mục 6 trước khi viết mã (đỏ trước, phá thử sau); quyết định kỹ thuật
đặt ngay trước bước cần dùng nó. Feature này **sửa hai ứng dụng có sẵn** (`api-gateway`, `web-gateway`) chứ không dựng mới, và hai ứng dụng này **chưa có hạ tầng test** (chỉ `contextLoads`),
nên có một bước dựng hạ tầng test trước khi sửa. Nhóm A đến D của `flow.md` mục 1 là các bước thi công riêng, tách được nếu cần.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu là phần thiết kế, viết **trước khi code**: hướng giải ngắn (chi tiết ở `tech.md` và ADR, không chép lại ở đây),
rồi các hướng giải gộp thành thành phần nào. Cột cuối điền **sau khi làm xong**
(quy trình: `docs/global/4.convention/work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Luật theo đường dẫn, rồi mức chung theo địa chỉ mạng | Danh sách luật nạp từ thuộc tính thành đối tượng bất biến, kiểm hợp lệ lúc khởi động; hai tầng đếm (`tech.md` T1) | Bộ lọc giới hạn tần suất ở `api-gateway` (mở rộng bộ lọc hiện có) + đối tượng chính sách | Chưa làm |
| 429 có thân lỗi chuẩn và `Retry-After` đúng | Thân JSON chung, `Retry-After` là thời gian còn lại của cửa sổ (`tech.md` T2) | Cùng bộ lọc | Chưa làm |
| Redis lỗi thì mở cửa, lệnh đếm có thời gian chờ | Thư viện đã mở cửa; thêm thời gian chờ ngắn (`tech.md` T3) | Thư viện `reactive-rate-limiter-starter` | Chưa làm |
| Địa chỉ mạng tin cậy chuyển xuống dịch vụ sau | Cổng ghi đè tiêu đề, dịch vụ sau chỉ tin tiêu đề từ cổng (`tech.md` T7) | Bộ lọc toàn cục ở `api-gateway` + cấu hình tin tiêu đề ở dịch vụ sau | Chưa làm |
| Kiểm `X-XSRF-TOKEN` cho yêu cầu ghi tới BFF, 403 có mã ổn định | Bật CSRF của Spring Security cho chuỗi phiên, kho cookie không `HttpOnly`, bộ xử lý lỗi mã `CSRF_TOKEN_INVALID` (`tech.md` T4) | Cấu hình chuỗi bảo mật `web-gateway` | Chưa làm |
| Mất cookie CSRF thì sinh lại, không đăng xuất | Gia hạn trượt cookie và điểm `GET` nhẹ sinh lại mã (`tech.md` T4) | Cùng cấu hình CSRF | Chưa làm |
| Làm mới token kèm `sid` | Bộ chuyển đổi tham số của yêu cầu làm mới thêm `sid` từ token hiện có (`tech.md` T5) | Bộ quản lý ủy quyền client của `web-gateway` | Chưa làm |
| Làm mới bị từ chối thì xóa phiên và ánh xạ | Bắt lỗi làm mới, xóa phiên, 401; gộp làm mới đồng thời nếu bước thử cho thấy cần (`tech.md` T5) | Cùng bộ quản lý + bộ xử lý lỗi | Chưa làm |
| Đăng nhập xong: ánh xạ hai chiều theo `sid`, đọc `role` | Đổi khóa từ `oss_id` sang `sid`; đọc `role` chuỗi (`tech.md` T6) | `SessionMappingAuthenticationSuccessHandler`, `AuthController` | Chưa làm |
| Đăng xuất BFF xóa ánh xạ theo `sid`, trả 202 + `Location` | Đọc `sid` từ thuộc tính người dùng OIDC (`tech.md` T6) | `WebGatewayLogoutSuccessHandler` | Chưa làm |
| Nhận báo đăng xuất `{"sid"}` và xóa phiên | Đổi trường `ossId` thành `sid`, kiểm UUID, giữ lũy đẳng (`tech.md` T6) | `SessionRevokeController` | Chưa làm |
| Ghi log hoàn tất mỗi yêu cầu, mã truy vết xuyên suốt | Bộ lọc log cho WebFlux dùng lõi của feature 07 (`tech.md` T7) | Bộ lọc log ở `api-gateway` (và `web-gateway`) | Chưa làm |

## Điều kiện bắt đầu

Những điều thuộc **nghiệp vụ** đã chốt, không bàn lại: mô hình hai tầng phiên và hợp đồng danh tính (ADR-0003), cách bảo vệ yêu cầu từ trình duyệt (ADR-0005), mô hình giới hạn tần suất hai nhóm (`rate-limiting-layers.md`),
hợp đồng báo đăng xuất `{"sid"}` (feature 03, `03-logout/tech.md` T7). Còn lại là kỹ thuật cài đặt ở hai cổng.

Feature 01 đến 04 xong (Xác thực đã chạy trên mô hình mới). Cần chạy được Redis, Postgres của Xác thực và ba dịch vụ cục bộ (`api-gateway`, `web-gateway`, `oauth2-service`) cho bước thử và bước chạy thật. Feature 05 (giao diện) chưa cần: gọi bằng curl có cookie.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 4 và 6: mỗi rủi ro R1 đến R20 đã có kịch bản test hoặc lý do không cần chưa; mỗi nhóm A đến D có test mức cổng; KT-21 đến 25 là ca cần ba dịch vụ thật (chạy ở bước "Chạy thật" và bằng script).

Xong khi: người rà đồng ý danh sách KT-01 đến KT-25; thêm hoặc bớt thì sửa `flow.md` mục 6.

### Bước 2. Thử hiện trạng của BFF với Xác thực mới · Người rồi Agent

Chạy ba dịch vụ như đang có và quan sát, **chưa sửa mã** (giống bước 1 của feature 03): (1) đăng nhập qua BFF có hoàn tất không (PKCE, `state`, đổi mã); (2) `/webgw/auth/session` trả gì (kỳ vọng `role = null`); (3) ánh xạ có được tạo không (kỳ vọng không, `oss_id` không còn); (4) làm mới token không `sid` bị Xác thực từ chối thế nào; (5) hai yêu cầu đồng thời gặp token hết hạn làm gì với mã làm mới (R11); (6) đăng xuất qua BFF và báo `{"sid"}` từ Xác thực tới BFF trả gì. Ghi cơ sở dữ liệu dev: báo trước và xóa dữ liệu thử sau.

Xong khi: bảng "Kết quả thử" ở `tech.md` điền đủ sáu ca; R11 và R17 có kết luận; các rủi ro mới (nếu có) thêm vào `flow.md` mục 4.

### Bước 3. Chốt giải pháp kỹ thuật (`tech.md`) · Người rồi Agent

Mỗi lựa chọn có phương án đã loại, dựa vào kết quả bước 2:
- **T1** mô hình luật theo đường dẫn và cách đếm hai tầng (thứ tự, yêu cầu bị chặn có tính vào mức chung không; R5).
- **T2** thân 429 và `Retry-After` đúng thời gian còn lại (R7).
- **T3** thời gian chờ cho lệnh đếm ở thư viện dùng chung (R1).
- **T4** CSRF ở BFF: cookie, gia hạn trượt, mã lỗi, điểm sinh lại mã (ADR-0005 A và C).
- **T5** làm mới kèm `sid` và xử lý từ chối, làm mới đồng thời (R11, R12).
- **T6** ánh xạ phiên theo `sid` (đổi tên khóa Redis, dọn khóa cũ), `role`, đăng xuất, nhận báo.
- **T7** địa chỉ tin cậy và bộ lọc log cho cổng (R19, R20).
- **T8** hạ tầng test cho hai cổng.

Xong khi: `tech.md` có T1 đến T8; mỗi hướng giải ở bảng đầu tệp này trỏ tới đúng mục.

### Bước 4. Hạ tầng test cho hai cổng · Agent, Người rà

Thêm vào `api-gateway` và `web-gateway`: Redis của Testcontainers dùng chung, máy chủ HTTP giả cho Xác thực và dịch vụ sau, lớp nền dựng ứng dụng thật với `WebTestClient`. Một test mẫu chứng minh bộ khung chạy (ví dụ yêu cầu qua cổng tới máy chủ giả).

Xong khi: hai bộ khung chạy xanh trong Maven; không test nào chạm Redis, Postgres hay dịch vụ cục bộ của người dùng.

### Bước 5. Nhóm A: giới hạn tần suất theo đường dẫn · Agent, Người rà

Viết KT-01 đến KT-07 (đỏ trước), rồi đối tượng chính sách, bộ lọc hai tầng, thân 429, thời gian chờ lệnh đếm (T1 đến T3), thuộc tính luật đầu `POST /auth/register`. Cập nhật `rate-limiting-layers.md` mục 8 khi xong.

Xong khi: KT-01 đến KT-07 xanh; phá thử (bỏ kiểm phương thức, bỏ chuẩn hóa dấu `/` cuối, đọc `X-Forwarded-For`, bỏ thời gian chờ) làm đúng test tương ứng đỏ.

### Bước 6. Nhóm D: BFF theo hợp đồng danh tính mới · Agent, Người rà

Viết KT-16 đến KT-20 và KT-23 (đỏ trước), rồi đổi `SessionMappingAuthenticationSuccessHandler`, `WebGatewayLogoutSuccessHandler`, `SessionRevokeController`, `AuthController` sang `sid` và `role` (T6), đặt độ lệch đồng hồ 5 giây cho bộ giải mã token dịch vụ. Không còn mã nào đọc `oss_id` hay `roles`.

Xong khi: KT-16 đến KT-20 và KT-23 xanh; `grep` không còn `oss_id`, `ossId`, `roles` trong `web-gateway`; phá thử (đọc lại `oss_id`) làm KT-16 đỏ.

### Bước 7. Nhóm C: làm mới token kèm `sid` · Agent, Người rà

Viết KT-13 đến KT-15 (đỏ trước, Xác thực giả), rồi bộ chuyển đổi tham số làm mới thêm `sid`, xử lý từ chối (xóa phiên, 401) và, nếu bước 2 cho thấy cần, gộp làm mới đồng thời (T5).

Xong khi: KT-13 đến KT-15 xanh; phá thử (bỏ `sid`, bỏ xóa phiên khi từ chối) làm test tương ứng đỏ.

### Bước 8. Nhóm B: CSRF ở BFF · Agent, Người rà

Viết KT-08 đến KT-12 (đỏ trước), rồi bật CSRF cho chuỗi phiên (cookie không `HttpOnly`, mã lỗi `CSRF_TOKEN_INVALID`, gia hạn trượt, điểm sinh lại mã), giữ `/webgw/internal/**` không CSRF (T4). Kiểm CORS cho phép header `X-XSRF-TOKEN` nếu giao diện gọi khác nguồn.

Xong khi: KT-08 đến KT-12 xanh; phá thử (tắt CSRF, bỏ mã lỗi riêng, bật CSRF cho `/webgw/internal/**`) làm test tương ứng đỏ.

### Bước 9. Log và địa chỉ tin cậy (việc giao từ feature 07) · Agent, Người rà

Viết KT-24, 25 (đỏ trước), rồi bộ lọc log cho `api-gateway` và `web-gateway` (WebFlux, truyền ngữ cảnh Reactor) dùng lõi của feature 07, ghi đè tiêu đề địa chỉ ở `api-gateway`, và cấu hình dịch vụ sau tin tiêu đề đó (T7).

Xong khi: hai test xanh bằng cách đọc log thật; phá thử (không ghi đè tiêu đề) làm KT-24 đỏ.

### Bước 10. Chạy thật · Người

Chạy `api-gateway`, `web-gateway`, `oauth2-service`, Redis và Postgres (đã có sẵn trong Docker) và gọi qua cổng 8000 bằng curl có cookie: đăng nhập qua BFF, gọi API, để token hết hạn rồi gọi tiếp (làm mới), đăng nhập hai thiết bị, đăng xuất ở BFF, báo đăng xuất từ Xác thực, 11 lần đăng ký liên tiếp (429), yêu cầu ghi thiếu CSRF. Xem log của ba dịch vụ, khóa Redis và bảng phiên sau mỗi ca. Ghi cơ sở dữ liệu dev: báo trước, hỏi giữ hay xóa dữ liệu thử.

Xong khi: KT-21, 22, 24, 25 và các ca ở `flow.md` mục 5 ra đúng kết quả; log không lỗi bất thường (không có token, mật khẩu, `id_token_hint`); không còn khóa Redis hay hàng cơ sở dữ liệu của phiên đã đăng xuất.

### Bước 11. Đóng feature · Agent, Người rà

Cập nhật `rate-limiting-layers.md` mục 8, ADR-0005 (mục "CSRF ở BFF" từ "chưa làm" sang đã làm kèm cơ chế đã chọn), `security-architecture.md` và `spring-security-logout-bff.md` nếu còn nói `oss_id` ở phía BFF (hoặc ghi vào `deferred.md`), `deferred.md`, cột cuối của bảng "Từ luồng đến giải pháp" ở trên, điều học được và hàng 06 ở `../00-features.md`, các nợ của feature 02 và 03 đã trả (BFF gửi `sid`, nhận `sid`, CSRF); `flow.md` đổi trạng thái sang Đã làm.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã và test.

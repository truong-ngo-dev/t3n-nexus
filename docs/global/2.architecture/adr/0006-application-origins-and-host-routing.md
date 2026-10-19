# ADR-0006: Nguồn gốc của các ứng dụng và định tuyến theo `Host`

> **Trạng thái:** Accepted `2026-10-18` · **Ngày:** `2026-10-17` · **Phạm vi:** `api-gateway`, `web-gateway` (nay `storefront-web-bff`, mẫu đặt tên ở mục Quyết định), `oauth2-service`, các ứng dụng web (Cửa hàng, Vận hành, về sau Người bán)
>
> Chuyển Accepted sau khi chạy thật qua cổng ở feature 06 (nhóm E) và xác nhận các điểm "Cần kiểm" ở cuối: đã làm `2026-10-18`, kết quả ở từng điểm. Phạm vi kiểm: Chrome trên Windows; Firefox và Safari chưa thử.

## Bối cảnh

Hiện mọi thứ ở **một nguồn gốc** `http://localhost:8000`, tách bằng tiền tố đường dẫn: `/auth/**` tới Xác thực, `/web/**` tới BFF của trình duyệt, `/mobile/**` giữ chỗ.
Cách này lộ ba vấn đề:

1. **Cookie dùng chung một kho.** Trình duyệt giữ cookie theo tên máy, không theo cổng hay tiền tố đường dẫn. Xác thực và BFF cùng ghi cookie tên `XSRF-TOKEN` (Xác thực
   `HttpOnly`, BFF theo ADR-0005 thì không) trên cùng máy nên ghi đè nhau khi bật CSRF ở BFF.
2. **Tiền tố gây gượng ép ở cổng.** `api-gateway` phải cắt tiền tố (`stripPrefix`), thêm `X-Forwarded-Prefix`, và BFF (lúc viết là `web-gateway`) phải khớp đường dẫn hai dạng (`(/web)?/api/...`) vì tiêu đề chuyển tiếp trả lại tiền tố.
   `{baseUrl}` và địa chỉ công khai phải gán tay (`apigateway.public-host`).
3. **Các ứng dụng giao diện là bốn ứng dụng riêng** (Cửa hàng, Người bán, Vận hành, Shipper di động; `5-ui-analysis.md`), phục vụ nhóm người dùng khác nhau với độ nhạy khác nhau (Vận hành là vai nội bộ). Chung một nguồn gốc nghĩa là
   một lỗi script ở Cửa hàng chạm được cookie và phiên của Vận hành.

## Quyết định

- **Mỗi ứng dụng web một nguồn gốc riêng theo subdomain, cùng một điểm vào.** `api-gateway` vẫn là điểm vào duy nhất, một cổng, nhưng **định tuyến theo `Host`**:

  | Host (dev) | Phục vụ | BFF kênh web |
  |---|---|---|
  | `auth.localhost` | Xác thực (đăng ký, đăng nhập, cấp mã, đổi mã, đăng xuất, JWKS), không có tiền tố | không có |
  | `shop.localhost` | Giao diện Cửa hàng **và** BFF của vai Người dùng, cùng nguồn gốc | `storefront-web-bff` |
  | `ops.localhost` | Giao diện Vận hành **và** BFF của vai nội bộ, cùng nguồn gốc (chuẩn bị chỗ; BFF và client của Vận hành làm sau) | `ops-web-bff` (về sau) |

  **Đặt tên BFF theo mẫu `<ứng dụng>-<kênh>-bff`** (`storefront-web-bff`, `storefront-mobile-bff`, `seller-web-bff`, `ops-web-bff`, `shipper-mobile-bff`); tên ứng dụng theo yêu cầu (Cửa hàng, Người bán, Vận hành, Shipper) bằng tiếng Anh kỹ thuật: `storefront` (không dùng `shop` vì trong sàn thương mại "shop" thường là gian hàng của người bán; không dùng `marketplace` vì đó là cả nền tảng). Host công khai có thể ngắn hơn tên module (`shop.` cho `storefront`). **Mã client OAuth2 là tên BFF bỏ hậu tố `-bff`** (`storefront-web`, `storefront-mobile`...): `sid` tạo theo cặp (phiên người dùng, client) nên mỗi kênh có phiên ứng dụng độc lập và đăng xuất từng kênh được.

  Trên môi trường thật, cùng cấu trúc với miền gốc thật (`auth.<miền>`, `shop.<miền>`, `ops.<miền>`); miền gốc là một cấu hình, không viết cứng.
- **Mỗi host web phục vụ cả giao diện lẫn BFF của nó, nên cùng nguồn gốc.** Giao diện gọi BFF bằng đường tương đối, không CORS giữa giao diện và BFF, JavaScript đọc được cookie `XSRF-TOKEN` của chính nguồn gốc đó (ADR-0005 vẫn đúng). Đường dẫn của BFF (`/api/**`,
  `/oauth2/authorization/**`, `/login/oauth2/code/**`, `/bff/**`) chuyển tới BFF; mọi đường dẫn còn lại của host chuyển tới máy chủ giao diện của ứng dụng đó (dev: máy chủ phát triển Angular, do feature 05 cấu hình).
- **Bỏ tiền tố `/web`, `/auth`, bỏ `X-Forwarded-Prefix`** và cặp biểu thức đường dẫn kép ở BFF. `api-gateway` giữ nguyên tiêu đề `Host` của trình duyệt và đặt `X-Forwarded-Host`, `X-Forwarded-Proto` từ yêu cầu thật (hết `apigateway.public-host`).
- **`issuer` của Xác thực là `http(s)://auth.<miền>`** (không còn `/auth`); redirect URI và `post_logout_redirect_uri` của client đã đăng ký đổi sang host của ứng dụng (`shop.localhost` cho client `storefront-web`).
- **Cookie là cookie theo máy (không đặt `Domain`)** để không chia sẻ giữa subdomain; tên vẫn đặt khác nhau giữa Xác thực và BFF làm lớp phòng thủ thứ hai. Khi có HTTPS thì dùng tiền tố `__Host-` (giải quyết luôn "Hệ quả" về tiền tố ở ADR-0005).
- **Cổng chỉ nhận `Host` trong danh sách đã cấu hình** (host lạ trả 404), để tiêu đề `Host` giả không chọn được tuyến.
- **Môi trường dev dùng `*.localhost`**, không cần sửa tệp `hosts` với trình duyệt hiện đại; danh sách host đọc từ cấu hình.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Giữ một nguồn gốc, tách bằng tiền tố; chỉ đổi tên cookie CSRF | Sửa được va chạm tên nhưng giữ nguyên hai vấn đề còn lại (tiền tố gượng ép, chung nguồn gốc giữa Cửa hàng và Vận hành) |
| Một host API chung cho mọi ứng dụng giao diện (`api.<miền>`) | Giao diện khác nguồn gốc với API: phải cấu hình CORS, cookie chéo nguồn gốc, và JavaScript không đọc được cookie CSRF của host khác |
| Mỗi ứng dụng một cổng (port) riêng, không dùng subdomain | Nhiều điểm vào, trái "một điểm vào duy nhất"; cookie vẫn chung kho vì cookie không tách theo cổng |
| Mỗi ứng dụng một miền gốc riêng | Không cần: subdomain của cùng miền gốc đã tách được nguồn gốc và cookie |
| Tên BFF theo ứng dụng không kèm kênh (`storefront-bff`) | Một ứng dụng có thể có cả bản web và di động, hai BFF khác mô hình bảo mật (cookie phiên và CSRF so với token và PKCE); thiếu kênh thì hai BFF trùng tên |
| Tên theo kênh chung (`web-gateway`, `mobile-gateway`) | Khi có ba BFF web (Cửa hàng, Người bán, Vận hành) thì `web-gateway` không còn chỉ rõ BFF nào, và "gateway" lẫn với `api-gateway` |
| Chia sẻ cookie phiên giữa các subdomain (`Domain=<miền>`) | Mất tách cookie, đúng thứ ta muốn có |

## Lý do

- Tách nguồn gốc là biện pháp rẻ để giới hạn phạm vi ảnh hưởng của một lỗi script và để mỗi ứng dụng có chính sách phiên và CSRF riêng.
- Định tuyến theo `Host` đơn giản hơn tiền tố: bỏ được các gượng ép ở cổng, địa chỉ công khai lấy từ yêu cầu thật.
- Cấu trúc này khớp môi trường thật, nên cách đã chạy ở dev không phải đổi khi triển khai.

## Hệ quả

- Đổi ở nhiều chỗ: `issuer` (claim `iss`, JWKS, `NimbusIdTokenHintVerifier`), redirect URI và trang đích đăng xuất đã đăng ký (migration V4 và V9 đã chạy, nên đổi qua `RegisteredClientSeeder` hoặc migration mới),
  `FRONTEND_URL`, `app.webgateway.base-url`, cấu hình CORS, `api.yaml` (`servers`), test đang dùng `localhost:8000/auth`, và các tài liệu nhắc `/auth`, `/web`.
- Luật giới hạn tần suất theo đường dẫn ghi theo (`Host`, phương thức, đường dẫn): `POST /auth/register` thành `POST /register` trên host `auth`.
- **Cần kiểm trước khi chuyển Accepted:**
  1. Trên localhost, `auth.localhost` và `shop.localhost` có thể bị trình duyệt coi là **hai site khác nhau** (khác `auth.<miền>` và `shop.<miền>` ở môi trường thật). Luồng đăng nhập và đăng xuất là điều hướng cấp cao nên phải đi được với `SameSite=Lax`. **Đã kiểm (Chrome và curl):** đăng nhập ở `auth`, callback về `shop`, đăng xuất đi `auth` rồi về `shop` đều chạy; cookie `STOREFRONT_SESSION` chỉ ở `shop`, `OAUTH2_SESSION` và `DEVICE_KEY` chỉ ở `auth`; `fetch` từ trang `shop` gọi BFF cùng nguồn gốc mang cookie bình thường.
  2. `*.localhost` phân giải về `::1` (IPv6) trên máy phát triển (đã kiểm bằng `ping`); các dịch vụ phải nghe được trên IPv6 hoặc cấu hình phân giải tương ứng. **Đã kiểm:** Chrome và curl vào được ba dịch vụ qua `::1` (khóa giới hạn tần suất ghi địa chỉ `0:0:0:0:0:0:0:1`), gọi bằng địa chỉ LAN cũng được.
  3. Cookie `Secure` và HTTP ở dev: cờ `Secure` hiện đặt qua biến môi trường cho Xác thực; cần giống nhau ở BFF. **Đã kiểm:** ở dev HTTP, cookie của BFF (`STOREFRONT_SESSION`, `XSRF-TOKEN`) không `Secure` vì cờ theo lược đồ của yêu cầu (`X-Forwarded-Proto` cổng đặt từ kết nối thật), và hoạt động; ở môi trường thật có TLS thì cờ tự bật. Cổng chưa có bộ kết thúc TLS đứng trước (nợ ghi ở `api-gateway/service.md`).
- Cửa hàng và Vận hành khác nguồn gốc nên tách được chính sách riêng (hạn phiên, bắt xác thực nhiều lớp cho vai nội bộ, giới hạn mạng) mà không động tới nhau.
- Thêm ứng dụng mới (Người bán, về sau Nhân sự) là thêm một host, một client đăng ký và một tuyến, không đổi cấu trúc.
- **Kênh di động có BFF riêng**, khác kênh web về mô hình bảo mật (ADR-0005): không cookie phiên (ứng dụng giữ token, gửi bằng header), không CSRF, client OAuth2 công khai bắt buộc PKCE. Ứng dụng di động không có nguồn gốc trình duyệt nên không cần subdomain theo ứng dụng; cách định tuyến khi có nhiều BFF di động (`storefront-mobile-bff`, `shipper-mobile-bff`) **chưa chốt** (một host `mobile.<miền>` kèm đường dẫn theo ứng dụng, hay mỗi ứng dụng một host); quyết định khi làm BFF di động đầu tiên.

## Tham khảo

- ADR-0003 (hợp đồng danh tính, `issuer`), ADR-0005 (CSRF, cookie phiên); `5-ui-analysis.md` (bốn ứng dụng); `service/oauth2-service/analysis/0.overview.md` (bảng "Bên nhận kiểm gì").

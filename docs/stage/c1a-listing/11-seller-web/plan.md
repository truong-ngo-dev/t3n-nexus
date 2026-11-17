# Feature 11: kế hoạch

> **Trạng thái:** Đang làm: lát 11a xong (`2026-11-17`; chạy thật một ca, các ca còn lại xem bước 9) · Nguồn: [`flow.md`](flow.md), [`tech.md`](tech.md). Chi tiết bước viết cho lát 11a; các lát sau viết khi bắt đầu.

## Cách làm

**Người** viết phần cốt lõi (khởi tạo phiên, dịch vụ trạng thái, guard, route, form, xử lý lỗi, quản lý trạng thái). **Agent (Claude)** giao đề bài mỗi lát (hợp đồng API thật, tiêu chí xong, khái niệm cần dùng), gợi ý theo bậc khi người kẹt (câu hỏi dẫn, hướng và tên API, mã giả, đoạn mã nhỏ), rồi rà mã. **`agy`** làm việc cơ học: kiểu TypeScript phản chiếu DTO, CSS lặp, cấu hình, khung test.

Quy tắc mỗi bước: test viết từ `flow.md` mục 6 trước khi viết mã khi có logic; kiểm chứng bằng thứ chạy được (Jest, Playwright), không "xem bằng mắt rồi ghi xong"; test tương xứng, không chạy cả bộ nhiều lần. Người commit.

## Từ luồng đến giải pháp (lát 11a)

| Bước của luồng                                    | Hướng giải                                                                          | Gộp vào thành phần                                         | Triển khai                          |
|---------------------------------------------------|-------------------------------------------------------------------------------------|------------------------------------------------------------|-------------------------------------|
| 3.1 Khởi động: phiên rồi hồ sơ                    | `provideAppInitializer` chờ phiên, rồi nạp hồ sơ nếu `USER` (`tech.md` T3, T4)      | `app.config.ts`                                            | Xong                                |
| Trạng thái người bán (mục 2 `flow.md`)            | `SellerStatusService`, signal sáu giá trị, 404 phân biệt theo `code` (`tech.md` T4) | `app/shared/`                                              | Xong                                |
| 3.2 Vào trang, vai                                | `sellerGuard` và `loginPageGuard` riêng của ứng dụng (`tech.md` T3)                 | `app.routes.ts`                                            | Xong                                |
| 3.2 Đăng nhập                                     | Trang `/login` như Cửa hàng; nút đi `/bff/auth/login`, đọc `?error`                 | `app/pages/login`                                          | Xong                                |
| 3.3 Đăng xuất                                     | Dùng `SessionService.logout()`                                                      | Khung ứng dụng                                             | Xong                                |
| 3.4 Lời gọi API                                   | Hai bộ chặn từ `@t3n/http`; `SellerApiService` giữ đường dẫn (`tech.md` T5)         | `app.config.ts`, `app/shared/`                             | Xong                                |
| Bố cục, trang trạng thái, không tìm thấy, `ADMIN` | Material: thanh trên, thanh bên, vùng nội dung (`tech.md` T6)                       | `app/` (khung), `app/pages/`                               | Còn trang không tìm thấy (đang tạm) |
| Cấu hình dự án và dev                             | Bỏ Karma, thêm chủ đề Material, script cổng 4202, ESLint (`tech.md` T2)             | `angular.json`, `package.json`, `eslint.config.js`, README | Xong                                |

## Điều kiện bắt đầu

Feature 03, 10 xong; API hồ sơ (`GET /api/seller/profile`) và đăng nhập qua `seller.localhost` chạy được. Dự án `seller-web` đã sinh bằng `ng generate application` (chưa chỉnh). Bước 1 đến 8 chỉ cần Node và Jest; bước 9 cần ngăn xếp cách ly (Postgres, Redis, `oauth2-service`, `seller-web-bff`, `seller-service`, cổng). **Không chạm Docker dev, Redis dev, Postgres dev.**

## Các bước của lát 11a

### Bước 1. Rà kịch bản test · Người
`flow.md` mục 4, 6: mỗi rủi ro R1 đến R8 đã có kịch bản test hoặc lý do không cần chưa.

### Bước 2. Chốt giải pháp · Người
Ba điểm cuối `tech.md` (trang đăng ký, xử lý `ADMIN`, nơi nạp trạng thái).

### Bước 3. Cấu hình dự án · Agent (việc nhỏ nên tự làm, không giao `agy`), Người rà
Theo `tech.md` T2: bỏ Karma, chủ đề Material, script, ESLint, README. Xong khi: `ng build seller-web` xanh, `npm run lint` xanh, `ng serve seller-web` chạy ở 4202.

**Trạng thái:** Xong `2026-11-11`. `angular.json` (bỏ `test`, `extract-i18n`, thêm `azure-blue.css`), `styles.scss` chép từ Cửa hàng, `index.html` (`lang="vi"`, tiêu đề), bỏ `tsconfig.spec.json` và `app.component.spec.ts` sinh sẵn, script `start:seller` và `build:seller`, ESLint (`seller-web` vào `appCode`, hai khối cấm ứng dụng import ứng dụng khác), README. `ng build seller-web` xanh; lint sạch; dev server ở 4202 trả 200 với trang mới. Ba tệp vi phạm thử (seller → storefront, storefront → seller, thư viện → seller) đều bị lint báo rồi đã xóa. Jest: 166 ca xanh, 1 bộ lỗi sẵn từ trước (`auth/skeleton.spec.ts`, xem dưới). Bỏ `azure-blue.css` chỉ giảm gói kiểu 1,8 kB (9,86 → 8,06 kB); giữ cho khớp Cửa hàng.

### Bước 3b. Nâng Angular 19 lên 22 · Agent
Người chốt `2026-11-11`: nâng cả workspace trước khi viết mã ứng dụng (Angular 19 hết hỗ trợ, không hỗ trợ chính thức Node 26). Nâng từng bậc 19 → 20 → 21 → 22 bằng `ng update`.

**Trạng thái:** Xong `2026-11-11`. Angular, Material, CDK 22.2; TypeScript 6.0; Jest 30, `jest-preset-angular` 17, `@types/jest` 30; builder `@angular/build`. Build hai ứng dụng, lint, Jest 166 xanh, Playwright bản giả 12 xanh. Bộ thật `e2e:real` chưa chạy (ghi vào DB dev).
Điều tìm thấy, cần biết khi viết mã:
- **Thay đổi mặc định ở 22:** thành phần mới mặc định `OnPush`; migration thêm `ChangeDetectionStrategy.Eager` vào mọi thành phần sẵn có để giữ hành vi cũ (mã mới của `seller-web` nên để mặc định, đừng thêm `Eager`). `HttpClient` mặc định dùng `fetch`; migration thêm `withXhr()` ở `storefront-web` để giữ XHR. **`seller-web` cũng nên dùng `withXhr()`** để cùng hành vi với Cửa hàng (bộ XSRF và bộ test đã kiểm với XHR).
- **Test XSRF:** từ Angular 20, bộ XSRF có sẵn so nguồn gốc bằng `new URL(PlatformLocation.href)`; vị trí giả mặc định của TestBed có href `//` nên không gắn `X-XSRF-TOKEN`. Test `csrf-recovery` phải cấp `MOCK_PLATFORM_LOCATION_CONFIG` kèm `startUrl`. Trình duyệt thật không bị ảnh hưởng (Playwright xanh).
- Gói đầu của `storefront-web` đạt 512 kB (cảnh báo ngân sách 500 kB, chưa quá mức lỗi 1 MB); chưa xử lý.
- Xóa `auth/skeleton.spec.ts` (kiểm placeholder đã bỏ từ feature 05).
- `package-lock.json` được tạo lại (bản cũ ghim Angular 19 gây xung đột khi cài).

### Bước 4. Khởi tạo ứng dụng · Người
`app.config.ts`: hai bộ chặn, bộ khởi động chờ phiên. Xong khi: KT-03 (phần phiên) xanh.

**Trạng thái:** Xong `2026-11-16`. `app.config.ts` có hai bộ chặn và bộ khởi động chờ phiên rồi gọi `SellerBootstrapService.load()` (nạp hồ sơ chỉ khi phiên là `USER`). `app.config.spec.ts` 9 ca xanh: chờ phiên, CSRF, 401, `USER` nạp hồ sơ, `ADMIN` và khách không nạp, phiên 503 không nạp, hồ sơ 500 thành `unknown`, hồ sơ 404 thành `none`.

### Bước 5. Dịch vụ trạng thái người bán · Người
Viết KT-01 trước (đỏ), rồi `SellerStatusService` và `SellerApiService` (hàm lấy hồ sơ). Nối vào bộ khởi động. Xong khi: KT-01, KT-03 xanh; phá thử (coi 5xx là chưa là người bán; 404 mã khác thành chưa là người bán) làm test đỏ.

**Trạng thái:** Xong `2026-11-16`. `SellerApiService` (lớp bọc HTTP, kiểm `status`, không giữ trạng thái) và `SellerStatusService` (signal sáu giá trị; `refresh()` không bao giờ phát lỗi; 404 chỉ thành `none` khi `code` là `SELLER_PROFILE_NOT_FOUND`). Spec của dịch vụ trạng thái 43 ca. Phá thử theo kế hoạch (5xx thành `none`, 404 mã khác thành `none`) làm test đỏ, khôi phục rồi xanh lại.

### Bước 6. Route và guard · Người
Viết KT-02 trước, rồi `app.routes.ts`: `/`, `/login`, `/forbidden-account` (tên chốt khi làm), `**`. Xong khi: KT-02 xanh; không có vòng lặp với `ADMIN`.

**Trạng thái:** Xong `2026-11-16`. Hai guard riêng của ứng dụng (không dùng guard dựng sẵn của `@t3n/auth`): `sellerGuard` (`USER` vào; khách đi `/bff/auth/login` một lần; `ADMIN` chuyển tới `/forbidden-account`; đang kiểm hoặc lỗi thì không vào và không đi đăng nhập) và `loginPageGuard` (đã đăng nhập thì về `/`). Chỉ xét phiên (vai), không xét hồ sơ; việc chờ hoặc lỗi hồ sơ do khung xử lý (bước 7). `/forbidden-account` không gắn guard nên `ADMIN` không vòng lặp. Spec guard 14 ca và spec bảng route 10 ca xanh.

**Điều tìm thấy:** nếu lần điều hướng đầu bị guard hủy (phiên `loading` hoặc `unknown`), router không tự thử lại; xem bước 7.

### Bước 7. Bố cục và các trang trạng thái · Người (logic), `agy` (CSS)
Khung (thanh trên, thanh bên, đăng xuất) và các màn theo `flow.md` mục 2. Xong khi: KT-04 xanh; mỗi trạng thái hiện đúng màn.

**Trạng thái:** Xong `2026-11-16`, trừ trang không tìm thấy (`NotFoundComponent` vẫn là chữ tạm). `AppComponent` có thanh trên (tên, nhãn trạng thái chỉ cho `USER`, nút đăng xuất chỉ khi đã đăng nhập) và vùng nội dung theo `checkScreen()`: `loading` (vòng quay), `error` (thông báo và nút thử lại), còn lại là `<router-outlet />`. `HomeComponent` hiện bốn màn `none`, `pending`, `rejected` (kèm lý do), `approved`. `/login` có nút đăng nhập và thông báo `?error` (không tự đăng nhập lại, R5); `/forbidden-account` chỉ có thông báo. CSS Material do `agy` làm. Spec khung 28 ca xanh; Jest cả `seller-web` 112 ca, cả workspace 281 ca.

**Điều tìm thấy:**
- `SellerStatusService.state` bắt đầu ở `loading` và chỉ `USER` mới được nạp hồ sơ, nên `checkScreen()` chỉ xét hồ sơ khi phiên là `USER`; xét cho cả khách và `ADMIN` thì họ kẹt ở màn chờ mãi.
- Sau Thử lại thành công phải điều hướng lại tới URL hiện tại **kèm `onSameUrlNavigation: 'reload'`**: lần điều hướng đầu bị hủy nên URL của router vẫn là URL cũ, router coi là cùng URL và bỏ qua, vùng nội dung trống. Jest không bắt được (chỉ kiểm có gọi `navigateByUrl`); Playwright bắt được.
- Thanh bên chưa làm: lát này chỉ một trang, chờ 11b (xem `deferred.md`).

### Bước 8. Playwright giả · `agy` (khung), Người (ca)
KT-05: mỗi trạng thái ở mục 2 bằng phiên và hồ sơ giả. Xong khi: bộ xanh.

**Trạng thái:** Xong `2026-11-16`. `playwright.seller.config.ts` (cổng 4202) và `npm run e2e:seller`; `e2e-seller/shell.spec.ts` 18 ca xanh: khách (vào `/` đi đăng nhập đúng một lần, `/login`, `/login?error`, bấm nút), `USER` bốn trạng thái kèm nhãn, lý do từ chối, `USER` mở `/login` thì về `/`, `ADMIN` (không nạp hồ sơ, không nhãn), phiên 503 và hồ sơ 500 rồi Thử lại, bấm đúp Thử lại, đăng xuất (202 kèm CSRF, bấm đúp, 503), địa chỉ lạ.

### Bước 9. Chạy thật qua cổng và đóng lát · Agent, Người
**Trạng thái:** Cập nhật tài liệu xong `2026-11-16`. Chạy thật (KT-06) mới làm **một ca** (`2026-11-17`, qua `seller.localhost:8000`, `oauth2-service` profile `dev` chạy từ IDE): đăng nhập `USER` chưa có hồ sơ, hiện "Chưa là người bán" và trang "Bắt đầu bán hàng". Các ca `USER` có hồ sơ chờ duyệt, `ADMIN`, đăng xuất, dịch vụ hồ sơ sập chưa chạy thật (Playwright dữ liệu giả đã phủ); làm khi có hồ sơ thật ở lát 11b. Trang không tìm thấy đã có nội dung thật (`2026-11-17`).

KT-06 trên ngăn xếp cách ly `probe-`: đăng nhập, xem trạng thái (`USER` chưa có hồ sơ; `USER` có hồ sơ chờ duyệt; `ADMIN`), đăng xuất. Cập nhật tài liệu: ADR-0007 (thêm dự án `seller-web`), `00-features.md` (hàng 11), `plan.md` (kết quả), `deferred.md`.

## Các lát sau

Viết chi tiết khi bắt đầu từng lát (đề bài: hợp đồng API thật, tiêu chí xong, khái niệm cần dùng):

- **11b** Hồ sơ gian hàng · **11c** Sản phẩm và biến thể · **11d** Ảnh · **11e** Đăng sản phẩm.
- Chạy thật mỗi lát một lần khi đóng; bộ Playwright đầy đủ qua cổng ở feature 13.

# Khung ứng dụng Người bán: thiết kế

Nguồn: [`srs.md`](srs.md). Phần này **đã có code** (`projects/seller-web/src/app`). Tài liệu mô tả cách nó chạy để các phần sau
dựa vào, và ghi phần phải đổi (mục 7).

## 1. Bố cục

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Người bán   [Trang chủ] [Hồ sơ] [Sản phẩm]          Chờ duyệt [Đăng xuất]│  mat-toolbar
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│                 vùng nội dung (router-outlet), rộng tối đa 960px         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

- Điều hướng ngang trong thanh trên, mỗi mục là `a mat-button` có `routerLinkActive`.
- Điều hướng chỉ hiện khi phiên là `USER`:
  - "Hồ sơ" chỉ hiện khi đã có hồ sơ;
  - "Sản phẩm" chỉ hiện khi đã duyệt (thêm ở phần 2).
- Nhãn trạng thái (bảng `STATUS_LABEL`) và nút "Đăng xuất" nằm bên phải.
- Màn chờ và màn lỗi của khung nằm trong `<main>`, ngay trên `router-outlet`.

**Vì sao giữ điều hướng ngang, không dùng thanh bên.** Người chốt ngày `2026-11-17` (`11-seller-web/deferred.md`): thanh bên chỉ
đáng khi có nhiều mục.
- Ở C1a, Người bán có ba mục, vẫn vừa thanh trên.
- Khi C2 thêm "Đơn hàng" và "Tồn kho" (năm mục) thì chuyển sang thanh bên như ứng dụng Quản trị.

## 2. Khởi động

```
Mở seller.localhost:8000/...
  │
  ├─ provideAppInitializer → SellerBootstrapService.load()
  │     1. SessionService.probe()           GET /bff/auth/session
  │     2. nếu phiên là USER:
  │        SellerStatusService.refresh()     GET /api/seller/seller/profile
  │     (cả hai luôn hoàn tất, kể cả khi lỗi; lỗi nằm trong state)
  │
  ├─ AppComponent.checkScreen():
  │     phiên loading, hoặc USER mà hồ sơ loading    → vòng xoay "Đang kiểm tra tài khoản…"
  │     phiên unknown, hoặc USER mà hồ sơ unknown    → màn lỗi + Thử lại
  │     còn lại                                       → để router-outlet hiện trang
  │
  └─ Router định tuyến, guard đọc state đã có (không gọi API)
```

**Hai dịch vụ giữ trạng thái.**

| Dịch vụ                             | Trạng thái                                                                              | Đổi khi                                                                    |
|-------------------------------------|-----------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `SessionService` (`@t3n/auth`)      | `loading` \| `anonymous` \| `authenticated` \| `unknown`                                | `probe()` lúc khởi động                                                    |
| `SellerStatusService` (ứng dụng)    | `loading` \| `none` \| `pending` \| `approved` \| `rejected` (kèm lý do) \| `unknown`   | `refresh()` lúc khởi động, sau khi nộp hồ sơ, khi bấm Thử lại; `loadProfile()` khi mở trang hồ sơ |

**Hồ sơ đầy đủ không nằm trong `SellerStatusService`.**
- Dịch vụ chỉ giữ trạng thái, vì guard và menu chỉ cần trạng thái.
- Trang hồ sơ tự giữ hồ sơ đầy đủ. Nó gọi `sellerStatus.loadProfile()`, nên trạng thái dùng chung được làm mới theo cùng phản hồi,
  không tốn thêm lời gọi.
- Lệnh đọc `SellerProfileApi.getMyProfile()` và model `SellerProfile` nằm ở `core/service/seller-profile/`, vì cả dịch vụ này lẫn
  tính năng hồ sơ cùng đọc. Lệnh ghi ở tính năng hồ sơ.

**Bấm Thử lại ở màn lỗi.**
1. Nếu phiên `unknown` thì chạy lại cả `load()`. Nếu chỉ hồ sơ `unknown` thì `refresh()`.
2. Nếu hết lỗi thì gọi `router.navigateByUrl(location.path(), { onSameUrlNavigation: 'reload' })`. Lý do: lần định tuyến đầu đã
   bị guard hủy, và router không tự thử lại.

## 3. Route và guard

| Đường dẫn            | Trang                       | Guard                                          |
|----------------------|-----------------------------|------------------------------------------------|
| `/`                  | `HomeComponent`             | `sellerGuard`                                  |
| `/login`             | `LoginComponent`            | `loginPageGuard` (đã đăng nhập thì về `/`)     |
| `/forbidden-account` | `ForbiddenAccountComponent` | không                                          |
| `/profile`           | `ProfileViewComponent`      | `sellerGuard`, `hasProfileGuard`               |
| `/profile/new`, `/profile/edit`, `/profile/resubmit`, `/profile/edit-shop` | `ProfileFormComponent`, bốn chế độ | `sellerGuard` và guard theo chế độ: bảng ở `1-seller-profile/design.md` mục 2 |
| `/products/...`      | phần 2 (`loadChildren`)     | `sellerGuard`, `approvedGuard` (thêm ở phần 2) |
| `**`                 | `NotFoundComponent`         | không                                          |

**`sellerGuard`:**
- `USER` thì cho vào;
- `ADMIN` thì chuyển `/forbidden-account`;
- khách thì điều hướng cấp cao tới `/bff/auth/login`;
- đang kiểm hoặc lỗi thì không vào. Khung đang hiện màn chờ hoặc màn lỗi.

Guard theo trạng thái hồ sơ dùng một khuôn chung `profileGuard(allowed)` ở `shared/guard/profile.guard.ts`:
- hồ sơ `loading` hoặc `unknown` thì trả `false`;
- `allowed(state)` sai thì về `/`.

Phần 2 thêm `approvedGuard = profileGuard(s => s.kind === 'approved')`.

## 4. Lời gọi API

- Mọi lời gọi `/api/...` nằm trong dịch vụ API của ứng dụng:
  - hồ sơ: `SellerProfileApi` (đọc, ở `core/service/seller-profile/`) và `SellerProfileCommandApi` (ghi, ở
    `features/seller-profile/data/`);
  - phần 2 thêm `ProductApiService`.

  Thành phần không viết chuỗi đường dẫn.
- Đường dẫn qua BFF: `/api/<đoạn chọn service>/<đường dẫn của service>`. Ví dụ `/api/seller/seller/profile`,
  `/api/catalog/seller/products`.
- Hai bộ chặn của `@t3n/http`, theo thứ tự `[unauthorizedInterceptor, csrfRecoveryInterceptor]`:
  - 401 thì đi đăng nhập một lần;
  - 403 CSRF thì lấy mã mới rồi gửi lại một lần.
- Đọc thân bằng `unwrapData(parse…)`; lệnh ghi không cần thân thì dùng `ignoreBody()`. Mọi lời gọi bọc `requestTimeout()`
  (30 giây).
- Hiểu lỗi bằng `classifyFailure`:
  - `transient`: an toàn để gửi lại;
  - `unknown-outcome`: có thể đã ghi, phải kiểm lại trước khi cho gửi lại;
  - `rejected`: trang đọc `code`.

## 5. Đăng xuất

1. Bấm "Đăng xuất": nút khóa.
2. `SessionService.logout()` gửi `POST /bff/auth/logout`.
3. Kết quả:
   - 202 kèm `Location`: trình duyệt rời trang, nút giữ khóa.
   - Thất bại: mở khóa, hiện dòng "Đăng xuất thất bại, vui lòng thử lại" dưới thanh trên.

## 6. Tệp

Theo `5.convention/frontend-structure.md`:

```
app/
  app.config.ts  app.routes.ts  app.component.*
  core/
    service/bootstrap/       seller-bootstrap.service.ts
    service/seller-profile/  seller-profile.api.ts (đọc)  seller-profile.model.ts  seller-status.service.ts  seller-status.labels.ts
    guard/                   seller.guard.ts  profile-state.guard.ts
    pages/                   login/  forbidden-account/  not-found/
  features/
    home/                    home.component.*
    seller-profile/          (phần 1; data/ giữ SellerProfileCommandApi và kiểu thân gửi lên)
```

Phần 2 thêm `features/products/` (route, trang, `data/product.api.ts`), nạp bằng `loadChildren`.

## 7. Phải đổi

| #   | Việc                                                                                                                                                         | Khi nào                |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|
| C1  | Thêm mục "Sản phẩm" ở điều hướng (chỉ khi `approved`), nút "Quản lý sản phẩm" ở trang chủ, `approvedGuard`, route `/products` bằng `loadChildren`               | Đầu phần 2             |
| C2  | Màn lỗi của khung và lỗi ở các trang hiện mã truy vết. Cần `ErrorInfo.traceId` ở `@t3n/http` (README mục 4)                                                    | Trước phần 2           |
| C3  | (Đã làm `2026-11-22`) `SellerApiService` chưa bọc `requestTimeout()` ở cả ba hàm (README mục 4). Thêm vào và thêm một ca hết giờ cho mỗi hàm | Đầu phần 1 (việc còn lại) |
| C4  | Gói ban đầu đang vượt ngưỡng cảnh báo 500 kB. Đo lại sau khi `/profile/...` chuyển sang `loadComponent`; ba trang hồ sơ đang nạp ngay từ đầu                     | Cùng C1                |

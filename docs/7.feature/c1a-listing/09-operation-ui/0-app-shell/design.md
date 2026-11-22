# Khung ứng dụng Quản trị: thiết kế

Nguồn: [`srs.md`](srs.md). Nền có sẵn:
- ADR-0006: mỗi ứng dụng một nguồn gốc theo `Host`;
- ADR-0007: cấu trúc workspace giao diện;
- `seller-web`: ứng dụng gần nhất để chép khuôn.

## 1. Bố cục chung

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ☰  Quản trị t3n                                 Quản trị   [Đăng xuất]  │  thanh trên (mat-toolbar)
├───────────────────┬──────────────────────────────────────────────────────┤
│ Người bán         │  Hàng chờ duyệt                                      │  tiêu đề trang
│   Hàng chờ duyệt  │  ──────────────────────────────────────────────────  │
│ Danh mục          │                                                      │
│   Cây danh mục    │   (nội dung của trang: bảng, chi tiết, biểu mẫu)     │
│   Mẫu thuộc tính  │                                                      │
│   Thương hiệu     │                                                      │
│ Nhật ký           │                                                      │
│   Tra cứu         │                                                      │
│                   │                                                      │
└───────────────────┴──────────────────────────────────────────────────────┘
   thanh bên 240px          vùng nội dung, rộng tối đa 1200px, lề 24px
```

**Vì sao thanh bên, không phải điều hướng ngang như `seller-web`.**
- `seller-web` chỉ có hai mục.
- Quản trị có năm mục ở C1a, và C1b thêm "Tài khoản" và "Sản phẩm". Bảy mục chia ba nhóm thì nằm ngang không vừa.
- Quản trị làm việc theo kiểu chuyển qua lại giữa các khu, nên thanh bên luôn hiện là hợp.

**Màn hẹp (dưới 600px).**
- Thanh bên chuyển sang chế độ `over`: ẩn đi, nút ☰ mở ra, chọn một mục thì tự đóng.
- Từ 600px trở lên, thanh bên ở chế độ `side`, luôn mở, và không có nút ☰.

**Khi chưa phải `ADMIN`** (khách, sai vai, đang kiểm, lỗi):
- chỉ có thanh trên, không có thanh bên;
- nội dung ở giữa màn hình.

Thành phần Material dùng: `mat-toolbar`, `mat-sidenav-container`, `mat-nav-list` với `mat-list-item`. `BreakpointObserver` của
CDK chọn chế độ thanh bên.

## 2. Khởi động: từ lúc mở trang tới lúc thấy nội dung

```
Trình duyệt mở ops.localhost:8000/sellers/pending
  │
  ├─ Angular khởi động, provideAppInitializer chạy SessionService.probe()
  │     GET /bff/auth/session
  │     (trong lúc chờ: AppComponent hiện vòng xoay "Đang kiểm tra tài khoản…")
  │
  ├─ probe xong, SessionService.state() là một trong: anonymous | authenticated | unknown
  │
  └─ Router mới bắt đầu định tuyến (vì bộ khởi động đã chờ), adminGuard đọc state:
        authenticated + ADMIN  → vào /sellers/pending
        authenticated + khác   → chuyển /forbidden-account
        anonymous              → điều hướng cấp cao tới /bff/auth/login, không vào
        unknown                → không vào; AppComponent hiện màn lỗi + nút Thử lại
```

**Vì sao chờ phiên trong bộ khởi động, không kiểm ở từng trang.**
- Guard cần biết vai ngay lần định tuyến đầu.
- Kiểm ở từng trang thì trang nào cũng có một khoảnh khắc hiện nội dung rồi mới bị đá đi.

**Thử lại khi lỗi.** Bấm "Thử lại" thì làm ba bước:
1. Gọi lại `probe()`.
2. Nếu hết lỗi thì `router.navigateByUrl(location.path(), { onSameUrlNavigation: 'reload' })`.
3. Lần định tuyến đầu đã bị guard hủy và router không tự thử lại, nên phải yêu cầu vào lại URL hiện tại. Cách này giống hệt
   `seller-web`.

## 3. Route

| Đường dẫn            | Trang                         | Guard            | Ghi chú                                    |
|----------------------|-------------------------------|------------------|--------------------------------------------|
| `/`                  | chuyển tới `/sellers/pending` | `adminGuard`     |                                            |
| `/login`             | `LoginComponent`              | `loginPageGuard` | Đã đăng nhập thì về `/`                    |
| `/forbidden-account` | `ForbiddenAccountComponent`   | không            | Không có guard vai, tránh vòng lặp (mục 4) |
| `/sellers/...`       | `loadChildren` phần 1         | `adminGuard`     | Hàng chờ duyệt, chi tiết hồ sơ             |
| `/catalog/...`       | `loadChildren` phần 2         | `adminGuard`     | Khi làm phần 2                             |
| `/audit/...`         | `loadChildren` phần 3         | `adminGuard`     | Khi làm phần 3                             |
| `**`                 | `NotFoundComponent`           | không            | Vẫn trong bố cục nếu là `ADMIN`            |

Mỗi khu là một tệp `<khu>.routes.ts` nạp bằng `loadChildren`. Mở khu lần đầu mới tải mã của khu đó.

## 4. Guard

Viết `adminGuard` trong ứng dụng, cùng khuôn với `sellerGuard` của `seller-web`. Không dùng `authenticatedGuard` của `@t3n/auth`.

```ts
export const adminGuard: CanActivateFn = () => {
  const state = inject(SessionService).state();
  switch (state.kind) {
    case 'authenticated':
      return state.session.role === 'ADMIN' ? true : inject(Router).createUrlTree(['/forbidden-account']);
    case 'anonymous':
      inject(PAGE_NAVIGATION).assign(LOGIN_URL);
      return false;
    default:
      return false; // loading hoặc unknown: AppComponent đang hiện màn chờ hoặc màn lỗi
  }
};
```

**Vì sao không dùng `authenticatedGuard` kèm `data.roles`.**
- Guard đó chuyển người sai vai về `/`.
- Mà `/` cũng cần `ADMIN`, nên `USER` sẽ bị đẩy vòng mãi.
- Trang `/forbidden-account` vì thế cũng không được có guard vai.

## 5. Thành phần dùng chung trong ứng dụng

Đặt ở `app/shared/`. Chỉ dùng trong `ops-web`. Chỉ đưa lên thư viện khi một ứng dụng thứ hai cần đúng thứ đó.

| Thành phần                         | Làm gì                                                                                                                                    |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `PageStateComponent`               | Một khối cho ba trạng thái của trang: `loading` (vòng xoay), `empty` (câu và hành động tùy chọn), `error` (câu, mã truy vết, nút Thử lại) |
| `describeError(error)` (hàm thuần) | Từ lỗi HTTP ra `{ kind: 'user' \| 'system', message, traceId? }`, dựa trên `classifyFailure` và `toErrorInfo` của `@t3n/http`             |
| `ConfirmDialogComponent`           | Hộp xác nhận chung: tiêu đề, nội dung, nút chính và nút hủy; nút chính có thể ở trạng thái đang gửi                                       |
| `NotifyService`                    | Bọc `MatSnackBar`: `success(text)` hiện 4 giây; `error(text, traceId?)` hiện tới khi bấm đóng                                             |
| `formatDateTime(iso)`              | `dd/MM/yyyy HH:mm` theo `Asia/Ho_Chi_Minh`                                                                                                |

**Cách `describeError` quyết câu chữ.**

| `classifyFailure` | Loại         | Câu mặc định                                                                                   |
|-------------------|--------------|------------------------------------------------------------------------------------------------|
| `transient`       | system       | "Hệ thống đang gặp sự cố, vui lòng thử lại."                                                   |
| `unknown-outcome` | system       | "Chưa rõ thao tác đã thực hiện hay chưa." Với thao tác ghi, trang phải tải lại dữ liệu để kiểm |
| `rejected`        | user         | Trang tự đặt câu theo `code`; không có câu riêng thì "Yêu cầu không hợp lệ."                   |
| `handled`         | (không hiện) | Bộ chặn 401 đã đưa đi đăng nhập                                                                |
| `unexpected`      | system       | "Đã xảy ra lỗi không mong muốn."                                                               |

`traceId` lấy từ thân lỗi. Cần sửa `@t3n/http` để `ErrorInfo` giữ `traceId` (README mục 4). Thứ tự: sửa thư viện kèm spec
trước, rồi mới viết `describeError`.

## 6. Thanh trên

- Bên trái là nút ☰ (chỉ màn hẹp) và tên ứng dụng "Quản trị t3n". Bấm tên thì về `/`.
- Bên phải: nhãn vai "Quản trị" và nút "Đăng xuất", chỉ hiện khi đã đăng nhập. `Session` ở `@t3n/model` chỉ có `userId` và
  `role`, chưa có tên hay thư điện tử, nên chưa hiện được tên người.

**Bấm "Đăng xuất":**
1. Nút chuyển sang khóa, chữ thành "Đang đăng xuất…".
2. `SessionService.logout()` gửi `POST /bff/auth/logout`.
3. Thành công (202 kèm `Location`) thì trình duyệt rời trang. Nút giữ khóa luôn, vì trang sắp đi.
4. Thất bại thì mở khóa nút và báo lỗi bằng `NotifyService.error("Đăng xuất thất bại, vui lòng thử lại")`.

## 7. Dự án và cấu trúc tệp

**Tạo dự án.**
1. `ng generate application ops-web`.
2. Chỉnh giống `seller-web` (T2 của feature 11):
   - bỏ Karma;
   - thêm chủ đề Material;
   - ESLint cấm import chéo giữa các ứng dụng.
3. Thêm ba script:
   - `start:ops` chạy `ng serve ops-web --host 127.0.0.1 --port 4203 --live-reload=false --hmr=false`. Cổng 4203 là mặc định
     `OPS_FRONTEND_URL` của `api-gateway`.
   - `build:ops`.
   - `e2e:ops` với `playwright.ops.config.ts`, thư mục `e2e-ops/`.
4. Ngân sách gói: lấy như hai ứng dụng kia (cảnh báo 500 kB, lỗi 1 MB).

Theo `5.convention/frontend-structure.md`. Thêm `ops-web` vào `appLayers` và `appBoundary` của `eslint.config.js`.

```
projects/ops-web/src/app/
  app.config.ts            bộ khởi động, hai bộ chặn, router
  app.routes.ts            route gốc (mục 3); chỗ duy nhất trỏ tới features
  app.component.*          thanh trên, thanh bên, màn chờ và màn lỗi phiên
  core/
    guard/admin.guard.ts
    pages/                 login/  forbidden-account/  not-found/
  shared/
    ui/                    page-state/  confirm-dialog/  notify.service.ts
    util/                  describe-error.ts  format.ts
  features/
    seller-review/         phần 1 (seller-review.routes.ts, pages/, data/)
    catalog/               phần 2
    audit/                 phần 3
```

`core/` không có `service/`: Quản trị chỉ cần phiên, mà phiên đã có ở `@t3n/auth`. Mỗi tính năng tự giữ dịch vụ API của nó ở `data/`. Thành phần trong khu gọi hàm có tên nghiệp vụ, không viết
chuỗi đường dẫn. Đường dẫn qua BFF có dạng `/api/<đoạn chọn service>/<đường dẫn của service>`, ví dụ
`/api/seller/admin/seller-profiles`.

## 8. Điều chưa kiểm

- `ops-web-bff` đã có tuyến `/bff/auth/**` như hai BFF kia (cùng thư viện `web-bff-starter`); kiểm bằng một lần đăng nhập thật ở
  bước đầu.

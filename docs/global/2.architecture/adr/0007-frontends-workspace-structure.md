# ADR-0007: Cấu trúc workspace giao diện `frontends/`

> **Trạng thái:** Accepted `2026-10-20` · **Ngày:** `2026-10-19` · **Phạm vi:** mọi ứng dụng giao diện web (Cửa hàng, Người bán, Vận hành)
>
> Chuyển Accepted sau khi feature 05 chạy thật: Jest xanh, bộ Playwright thật 11/11 xanh hai lần liên tiếp, người đã thử tay S1 và S4 bằng Chrome qua `shop.localhost:8000`.

## Bối cảnh

Giao diện cũ `web/` (Angular + Nx) mang hợp đồng cũ (vai `CUSTOMER`, route `/customer`, mọi 401 gọi đăng nhập, không CSRF). Hợp đồng danh tính (ADR-0003), bảo vệ yêu cầu (ADR-0005) và nguồn gốc theo `Host` (ADR-0006) đã đổi, nên viết lại thay vì sửa tiếp. Cần chốt cấu trúc chung để Người bán và Vận hành về sau dựng cùng kiểu.

## Quyết định

- **`frontends/` là một workspace Angular CLI nhiều dự án, không dùng Nx.** Hiện có một ứng dụng và ba thư viện logic; chưa cần đồ thị tác vụ và bộ nhớ đệm của Nx. Bàn lại khi có từ ba ứng dụng trở lên và thời gian build thành vấn đề.
- **Mỗi ứng dụng một dự án** (`projects/storefront-web`; về sau `seller-web`, `ops-web`), tên theo ứng dụng ở `5-ui-analysis.md`.
- **Thư viện chỉ chứa logic, không có thành phần giao diện**: `model` (kiểu, vai), `auth` (phiên, guard, đăng ký, đăng xuất), `http` (bộ chặn CSRF, 401, lỗi API). Dùng qua `tsconfig` `paths` trỏ vào mã nguồn (`@t3n/*` → `public-api.ts`), không build ra `dist`.
- **Hướng phụ thuộc, ESLint ép bằng `no-restricted-imports`:** `model` không import gì của dự án; `auth` chỉ import `model`; `http` import `model` và `auth`; ứng dụng import cả ba; không ai import ứng dụng.
- **Vai chỉ có `USER`, `ADMIN`** định nghĩa một chỗ ở `model`; ESLint chặn từ vựng vai cũ (`CUSTOMER`, `SELLER`, `SHIPPER`, `GUEST`).
- **Test:** Jest cho logic (thư viện, thành phần), Playwright trên Chrome đã cài (`channel: 'chrome'`) cho đầu-cuối: bộ nhanh có giả lập (`npm run e2e`) và bộ thật qua cổng (`npm run e2e:real`, ghi dữ liệu thử tiền tố `e2e-` rồi dọn).
- **Chạy dev qua cổng:** `ng serve` cổng 4201, `--host 127.0.0.1`, tắt live-reload và hmr (WebSocket không đi qua cổng được); cổng trỏ `SHOP_FRONTEND_URL=http://localhost:4201`.
- **Giao diện dùng Angular Material**, bố cục tối giản; chưa có hệ thành phần dùng chung giữa các ứng dụng.

## Đã loại

- **Nx:** xem trên.
- **Thư viện build ra `dist`:** thêm bước build, `paths` tới nguồn đủ cho quy mô này.
- **Thư viện chứa cả thành phần giao diện dùng chung:** chưa có màn hình thứ hai dùng lại; làm trước là đoán. Bàn lại khi ứng dụng thứ hai ra đời.
- **Sửa tiếp `web/`:** mang theo hợp đồng cũ; chi phí rà từng tệp thấp hơn chi phí gỡ hành vi cũ.

## Hệ quả

- Thêm ứng dụng mới là thêm một dự án vào `angular.json` và import ba thư viện sẵn có.
- Thư viện không có giao diện nên dùng chung được cho ứng dụng không phải Angular Material.
- Angular 19 chạy được trên Node 26 dù chưa hỗ trợ chính thức (kết quả thử một lần): nếu gặp lỗi lạ, thử Node LTS trước.

## Nguồn

`stage/c0-foundation/05-ui-shell/tech.md` T1 (kết quả thử và lý do chi tiết), `5-ui-analysis.md` mục 3.

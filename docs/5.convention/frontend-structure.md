# Cấu trúc mã một ứng dụng giao diện

> Áp cho mọi ứng dụng trong `frontends/projects/` (`storefront-web`, `seller-web`, `ops-web`). Chốt `2026-11-22`. Workspace và
> thư viện dùng chung: ADR-0007.

## 1. Ba tầng trong một ứng dụng, cộng thư viện dùng chung

| Tầng              | Chứa gì                                                                                         | Không chứa                                      |
|-------------------|-------------------------------------------------------------------------------------------------|-------------------------------------------------|
| `@t3n/*` (thư viện) | Thứ dùng chung giữa **nhiều ứng dụng**: `@t3n/model` (kiểu), `@t3n/auth` (phiên), `@t3n/http` (bộ chặn, hiểu lỗi) | Mã của một ứng dụng                         |
| `core/`           | Thứ của **cả ứng dụng**, chỉ một bản, có nghiệp vụ: khởi động, trạng thái dùng chung, guard, trang của khung | Thứ chỉ một tính năng dùng                      |
| `shared/`         | Thứ **dùng lại được, không biết nghiệp vụ**: thành phần giao diện, hàm tiện ích                  | Nghiệp vụ, model, dịch vụ gọi API               |
| `features/<tên>/` | Một tính năng: trang, thành phần, API, kiểu dữ liệu, hàm thuần của nó                            | Thứ tính năng khác cũng cần                     |

Ba cách tổ chức phổ biến đều đi cùng hướng này:
- Angular: chia `core`, `shared`, `features`;
- Nx: bốn loại thư viện `feature`, `ui`, `data-access`, `util`;
- Feature-Sliced Design: các lớp `app → pages → features → entities → shared`.

Điểm chung của ba cách là **luật ai được import ai** (mục 3) quan trọng hơn tên thư mục.

## 2. Cây thư mục

Ví dụ `seller-web`:

```
src/app/
├── app.config.ts  app.routes.ts  app.component.{ts,html,scss}   khung: thanh trên, màn chờ, màn lỗi
├── core/
│   ├── service/                          một thư mục con cho mỗi khái niệm
│   │   ├── bootstrap/
│   │   │   └── seller-bootstrap.service.ts
│   │   └── seller-profile/               phần dùng chung của hồ sơ người bán: lệnh đọc và trạng thái
│   │       ├── seller-profile.api.ts     SellerProfileApi: chỉ lệnh đọc, cùng các hằng địa chỉ
│   │       ├── seller-profile.model.ts   SellerProfile, parseSellerProfile, SellerProfileStatus
│   │       ├── seller-status.service.ts  trạng thái dùng chung (SellerProfileState)
│   │       └── seller-status.labels.ts   chữ hiển thị cho từng trạng thái
│   ├── guard/
│   │   ├── seller.guard.ts
│   │   └── profile-state.guard.ts
│   └── pages/                            trang của khung, không phải của tính năng
│       ├── login/  forbidden-account/  not-found/
├── shared/
│   ├── ui/                               page-state/, confirm-dialog/
│   └── util/                             describe-error.ts, format.ts
└── features/
    ├── home/
    │   └── home.component.{ts,html,scss}
    └── seller-profile/
        ├── data/
        │   ├── seller-profile-command.api.ts     SellerProfileCommandApi: nộp, sửa, nộp lại, sửa thông tin gian hàng
        │   └── seller-profile-command.model.ts   kiểu thân gửi lên
        ├── profile-form.ts  profile-form.spec.ts   hàm thuần của tính năng
        └── pages/                                  trang có route, đọc dữ liệu
            ├── profile-form/  profile-view/
```

**Bên trong một tính năng:**

| Thư mục        | Có khi                                                           |
|----------------|------------------------------------------------------------------|
| `pages/`       | Tính năng có hơn một trang. Một trang thì để thẳng ở gốc tính năng (`features/home/`) |
| `data/`        | Tính năng gọi API                                                |
| `components/`  | Một mảnh giao diện dùng lại trong tính năng, hoặc trang đã quá dài. Thành phần ở đây chỉ hiển thị: nhận vào, phát sự kiện, không gọi API |
| Hàm thuần      | Đặt ở gốc tính năng, kèm spec. Quy tắc kiểm, dựng thân gửi, hiểu lỗi nằm ở đây để kiểm bằng Jest không cần DOM |

Không tách sẵn `components/` hay `data/` khi chưa có gì để đặt vào.

## 3. Luật phụ thuộc

```
features/<A>  →  core, shared, @t3n/*        không import features/<B>
core          →  shared, @t3n/*              không import features
shared        →  @t3n/*                      không import core, features
@t3n/*        →  @t3n/* theo hướng model ← auth ← http; không import ứng dụng
ứng dụng      →  không import ứng dụng khác
```

`app.routes.ts` ở gốc `app/` là chỗ duy nhất trỏ tới `features`.

ESLint chặn các luật trên (`frontends/eslint.config.js`, hàm `appLayers`). Danh sách tính năng được đọc từ thư mục `features/`,
nên thêm tính năng mới không phải sửa cấu hình.

## 4. Model và dịch vụ đặt ở đâu

| Thứ                                                          | Chỗ                                                                   |
|--------------------------------------------------------------|-----------------------------------------------------------------------|
| Kiểu dùng chung giữa các ứng dụng (`Session`, `Role`, `ApiResponse`) | `@t3n/model`                                                  |
| Kiểu trao đổi với API (DTO) và hàm `parse…` của một tính năng | `features/<tên>/data/`                                               |
| Lời gọi API của một tính năng                                | `features/<tên>/data/<tên>.api.ts`                                    |
| Model của biểu mẫu, của màn hình                             | Cạnh thành phần hoặc hàm thuần dùng nó, trong tính năng               |
| Lệnh đọc và model mà `core` và tính năng cùng dùng           | `core/service/<khái niệm>/`, cùng thư mục với service dùng nó         |

**Model đi cùng service sở hữu nó.** Model, hàm `parse`, lời gọi API và service của một khái niệm đổi cùng nhau, nên ở cùng một
thư mục. Không có `core/model/`, `shared/model/` hay `shared/service/` chung cho cả ứng dụng: các thư mục đó dần thành chỗ đổ
model mà không biết của ai. Chỉ tạo `core/model/` khi có kiểu dùng chung trong cả ứng dụng mà không thuộc service nào.

**Lời gọi của một tài nguyên ở tính năng sở hữu nó; chỉ phần dùng chung mới xuống `core`.** Mặc định mọi lời gọi tới một tài
nguyên nằm trong một lớp `<Tài nguyên>Api` ở `features/<tên>/data/`. Khi `core` cũng cần gọi tài nguyên đó, chỉ chuyển **đúng
lời gọi `core` dùng**, kèm model của nó, xuống `core/service/<khái niệm>/`. Phần còn lại ở tính năng, trong lớp
`<Tài nguyên>CommandApi`, và lấy hằng địa chỉ từ `core` để địa chỉ chỉ nằm một chỗ.

Ví dụ hồ sơ người bán:
- `SellerStatusService` (guard, menu, khởi động) và trang hồ sơ cùng gọi `GET /profile`. Bên đầu lấy `status`, bên sau lấy cả hồ
  sơ. Vì vậy `SellerProfileApi.getMyProfile()` và `SellerProfile` nằm ở `core/service/seller-profile/`.
- Bốn lệnh ghi chỉ tính năng hồ sơ dùng. Chúng nằm trong `SellerProfileCommandApi` ở `features/seller-profile/data/`, cùng kiểu
  thân gửi lên.
- Trang xem gọi `SellerStatusService.loadProfile()`: một lời gọi vừa trả hồ sơ vừa làm mới trạng thái dùng chung.

Không dùng token để `core` đọc qua tính năng khi model nhỏ và ổn định: token thêm một tầng gián tiếp, phải mở `app.config.ts` mới
thấy chỗ nối. Token chỉ đáng khi `core` không được biết model.

Đặt tên hàm theo việc, không lặp tên tài nguyên: `submit`, `update`, `resubmit` (không phải `submitSellerProfile`).

**Dữ liệu hiển thị tách khỏi model.** Chữ hiển thị cho trạng thái (`seller-status.labels.ts`) không nằm trong tệp model, vì hai
thứ dựa trên hai tập giá trị khác nhau và đổi vì hai lý do khác nhau:
- model theo API, đổi khi API đổi;
- nhãn theo trạng thái của service, đổi khi câu chữ đổi.

## 5. Luật chuyển chỗ

Thứ gì cũng sinh ra ở nơi đầu tiên cần nó. Chỉ khi có nơi thứ hai cần **đúng** thứ đó mới chuyển xuống một tầng:

| Đang ở             | Nơi thứ hai cần là          | Chuyển tới                       |
|--------------------|-----------------------------|----------------------------------|
| `features/<A>`     | `features/<B>`              | `core/` nếu có nghiệp vụ; `shared/` nếu không |
| `core/` hoặc `shared/` của một ứng dụng | ứng dụng khác | `@t3n/*` (ADR-0007)           |

Không đưa lên trước vì "sau này chắc sẽ dùng chung".

## 6. Đặt tên tệp

| Loại                  | Tên                                    | Ví dụ                                   |
|-----------------------|----------------------------------------|-----------------------------------------|
| Thành phần            | `<tên>.component.{ts,html,scss}`       | `profile-view.component.ts`             |
| Service giữ trạng thái| `<tên>.service.ts`                     | `seller-status.service.ts`              |
| Lời gọi API           | `<tài nguyên>.api.ts`, lớp `<Tài nguyên>Api` | `seller-profile.api.ts`, `SellerProfileApi` |
| Lệnh ghi tách khỏi lệnh đọc ở `core` | `<tài nguyên>-command.api.ts`, lớp `<Tài nguyên>CommandApi` | `seller-profile-command.api.ts`, `SellerProfileCommandApi` |
| Kiểu dữ liệu          | `<tài nguyên>.model.ts`                | `seller-profile.model.ts`               |
| Chữ hiển thị          | `<khái niệm>.labels.ts`                | `seller-status.labels.ts`               |
| Guard                 | `<tên>.guard.ts`                       | `seller.guard.ts`                       |
| Route của tính năng   | `<tính năng>.routes.ts`                | `seller-profile.routes.ts`              |
| Hàm thuần             | Tên theo việc nó làm                   | `profile-form.ts`                       |
| Test                  | Cạnh tệp được kiểm, `<tệp>.spec.ts`    | `seller-profile.api.spec.ts`            |

# Hồ sơ gian hàng: thiết kế

Nguồn: [`srs.md`](srs.md). Khung, guard, dịch vụ trạng thái: [`../0-app-shell/design.md`](../0-app-shell/design.md).

Hiện code đã có nộp, nộp lại và xem hồ sơ, ở `pages/profile-form/`, `pages/profile-view/`, `shared/guard/profile.guard.ts`.
Hai chế độ sửa và cách hiện lỗi đầy đủ ở mục 6 là phần phải làm thêm. Danh sách việc ở mục 9.

## 1. Màn hình

### 1.1 Trang chủ theo trạng thái (`/`)

```
Chưa có hồ sơ                          Chờ duyệt
──────────────────────────────         ──────────────────────────────
Bắt đầu bán hàng                       Hồ sơ đang chờ duyệt
Bạn chưa có hồ sơ người bán…           Chúng tôi sẽ xem xét hồ sơ…
[ Nộp hồ sơ ]                          [ Sửa hồ sơ ]  [ Xem hồ sơ ]

Bị từ chối                             Đã duyệt
──────────────────────────────         ──────────────────────────────
Hồ sơ chưa được duyệt                  Gian hàng của bạn đã được duyệt
Lý do: │ "Ảnh căn cước mờ…"            Bạn có thể bắt đầu đăng sản phẩm.
[ Sửa và nộp lại ]  [ Xem hồ sơ ]      [ Quản lý sản phẩm ]  [ Xem hồ sơ ]   ← "Quản lý sản phẩm" có ở phần 2
```

### 1.2 Xem hồ sơ (`/profile`)

```
Hồ sơ người bán                                              [ Chờ duyệt ]
┌ Lý do từ chối (chỉ khi bị từ chối) ───────────────────────────┐
└───────────────────────────────────────────────────────────────┘
Thông tin pháp lý                        Gian hàng và liên hệ
Loại hình       Hộ kinh doanh            Tên gian hàng   Gốm Minh An
Tên chủ hộ      Nguyễn Văn An            Mô tả           Gốm thủ công…
Số căn cước     001203004567             Người liên hệ   Nguyễn Văn An
…                                        …

                              [ <nút của trạng thái> ]  [ Về trang chủ ]
```

- Nút của trạng thái: "Sửa hồ sơ", "Sửa và nộp lại", hoặc "Sửa thông tin gian hàng".
- Dòng và nhãn lấy cùng nguồn với biểu mẫu (`legalFieldsFor`, `fieldLabel`), nên hai trang không lệch nhau.
- Ô tùy chọn trống thì bỏ dòng.

### 1.3 Biểu mẫu đầy đủ (nộp, sửa khi chờ duyệt, nộp lại)

Một trang có route riêng, không dùng hộp thoại, vì bốn lý do:
- biểu mẫu dài;
- cần guard theo trạng thái;
- phải giữ dữ liệu khi lỗi;
- tải lại và nút Back phải chạy bình thường.

Lưới hai cột, dưới khoảng 600px thì về một cột.

```
<Tiêu đề theo chế độ>
┌ <dòng ở đầu theo chế độ: lý do từ chối / "đang chờ duyệt…"> ────┐
└──────────────────────────────────────────────────────────────────┘
┌ <dòng lỗi biểu mẫu, chỉ khi có> ─────────────────────────────────┐
└──────────────────────────────────────────────────────────────────┘
Loại hình *     ( ) Cá nhân   (•) Hộ kinh doanh   ( ) Doanh nghiệp

Thông tin pháp lý                                     (chỉ hiện khi đã chọn loại hình)
[ Tên chủ hộ *                                                     ]
[ Số căn cước *            ] [ Mã số thuế *                       ]
[ Địa chỉ kinh doanh *                                             ]
Gian hàng
[ Tên gian hàng *                                                  ]
[ Mô tả gian hàng (tùy chọn)                                42/500 ]
Liên hệ
[ Người liên hệ *          ] [ Số điện thoại *                    ]
[ Địa chỉ liên hệ (tùy chọn)                                       ]

                                              [ <nút gửi> ]  [ Hủy ]
```

- Doanh nghiệp thì "Tên doanh nghiệp" đi cặp với "Người đại diện" ở hàng đầu, nên số hàng không đổi khi đổi loại hình.
- Nhãn có `*` nếu bắt buộc, có "(tùy chọn)" nếu không.
- "Hủy" là liên kết: về `/` khi nộp, về `/profile` ở các chế độ khác. Không hỏi lại.

### 1.4 Biểu mẫu rút gọn (sửa khi đã duyệt)

```
Sửa thông tin gian hàng
┌ Tên gian hàng và thông tin pháp lý đã khóa sau khi được duyệt. ┐
└────────────────────────────────────────────────────────────────┘
Đã khóa:   Tên gian hàng  Gốm Minh An  ·  Loại hình  Hộ kinh doanh      (chữ thường, không phải ô nhập)
Gian hàng
[ Mô tả gian hàng (tùy chọn)                                42/500 ]
Liên hệ
[ Người liên hệ *          ] [ Số điện thoại *                    ]
[ Địa chỉ liên hệ (tùy chọn)                                       ]
                                              [ Lưu thay đổi ]  [ Hủy ]
```

Phần đã khóa hiện bằng chữ, không dùng ô nhập bị vô hiệu. Ô vô hiệu trông như sửa được mà bấm không ăn, gây khó hiểu.

## 2. Cấu hình theo chế độ

Một thành phần `ProfileFormComponent`. `mode` lấy từ `data` của route. Mọi khác biệt tra ở một bảng hằng `FORM_MODES` trong
`profile-form.ts`, nên component không có nhánh `if (mode === …)` rải rác.

| Khóa cấu hình                     | `submit`              | `edit-pending`        | `resubmit`              | `edit-approved`                                                      |
|-----------------------------------|-----------------------|-----------------------|-------------------------|----------------------------------------------------------------------|
| Route                             | `/profile/new`        | `/profile/edit`       | `/profile/resubmit`     | `/profile/edit-shop`                                                 |
| Guard (trạng thái)                | `none`                | `pending`             | `rejected`              | `approved`                                                           |
| Tải hồ sơ để điền                 | Không                 | Có                    | Có                      | Có                                                                   |
| Ô sửa được                        | Tất cả theo loại hình | Tất cả theo loại hình | Tất cả theo loại hình   | `description`, `contactPersonName`, `contactPhone`, `contactAddress` |
| Dòng ở đầu                        | —                     | `pending-note`        | lý do từ chối           | `locked-note` và khối "Đã khóa"                                      |
| Tiêu đề                           | "Nộp hồ sơ người bán" | "Sửa hồ sơ"           | "Sửa và nộp lại hồ sơ"  | "Sửa thông tin gian hàng"                                            |
| Nút gửi                           | "Nộp hồ sơ"           | "Lưu thay đổi"        | "Nộp lại hồ sơ"         | "Lưu thay đổi"                                                       |
| Khóa nút khi chưa đổi             | Không                 | Có                    | Không                   | Có                                                                   |
| Dựng thân gửi                     | `buildSubmitRequest`  | `buildSubmitRequest`  | `buildSubmitRequest`    | `buildShopInfoPatch`                                                 |
| Gọi                               | `submit` | `update` | `resubmit` | `updateShopInfo` |
| Làm mới trạng thái sau thành công | Có                    | Không                 | Có                      | Không                                                                |
| Thông báo thành công              | "Đã nộp hồ sơ"        | "Đã lưu thay đổi"     | "Đã nộp lại hồ sơ"      | "Đã lưu thay đổi"                                                    |
| Rời về khi Q6                     | `/`                   | `/profile`            | `/profile`              | `/profile`                                                           |

**Ba chỗ dễ sai.**
- **Làm mới trạng thái trước khi chuyển trang** ở `submit` và `resubmit`. Trang `/profile` có guard `hasProfileGuard`; nếu trạng
  thái dùng chung vẫn là `none` thì guard đá người dùng về `/`, dù hồ sơ vừa được tạo.
- **Đã đổi hay chưa** (`isDirty`) so **thân sẽ gửi**, không so giá trị ô:
  - `edit-pending`: so `buildSubmitRequest(original)` với `buildSubmitRequest(current)`;
  - `edit-approved`: thân là `buildShopInfoPatch(original, current)`, không rỗng nghĩa là đã đổi.

  Nhờ vậy " Tên " và "Tên" được coi là như nhau, và ô của loại hình khác đang bị ẩn không làm nút mở ra.
- **`buildShopInfoPatch`** chỉ đưa vào ô đã đổi. Ô tùy chọn bị xóa trắng thì đưa `null` (Q7). Không bao giờ có `shopName`.

## 3. Mã chính

| Tệp                         | Có gì                                                                                                                                                                                                                                                                         |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `profile-form.ts`           | Hàm thuần: `ProfileFormValue` (11 ô phẳng), `FORM_MODES`, `fieldsOf(mode, type)`, `validateProfileForm(value, fields)`, `buildSubmitRequest`, `buildShopInfoPatch`, `isDirty`, `formValueFromProfile`, `fieldLabel`, `isFieldRequired`, `fieldErrorMessage`, `mapSubmitError` |
| `profile-form.component.ts` | Nối hàm thuần với Signal Forms và Material; giữ trạng thái tải, gửi, lỗi                                                                                                                                                                                                      |
| `profile-view.component.ts` | Tải hồ sơ đầy đủ, dựng hai khối dòng, chọn nút theo trạng thái                                                                                                                                                                                                                |
| `seller-api.service.ts`     | Năm hàm API (mục 7)                                                                                                                                                                                                                                                           |

**Vì sao logic nằm ở hàm thuần.** Quy tắc kiểm, dựng thân và hiểu lỗi là phần dễ sai nhất. Để ngoài component thì kiểm bằng
Jest được, không phải dựng DOM.

**Signal Forms.**
- `form(this._model)` bọc signal giá trị; ô gắn bằng `[formField]`.
- Không đăng ký luật vào `form()`: `validateProfileForm` là nguồn sự thật duy nhất, nên `form().valid()` không được dùng.
- `matcherOf(ô)` là `ErrorStateMatcher` đọc `errorOf(ô)`, để viền đỏ và `<mat-error>` đi cùng một nguồn.

## 4. Trạng thái của component

| Signal               | Giá trị                             | Ý nghĩa                                                       |
|----------------------|-------------------------------------|---------------------------------------------------------------|
| `mode` (input)       | bốn chế độ ở mục 2                  | Chọn một cột của `FORM_MODES`                                 |
| `loadState`          | `loading` \| `loaded` \| `error`    | `submit` luôn `loaded`                                        |
| `_loadError`         | `{ traceId? }` \| null              | Cho màn lỗi trang                                             |
| `_original`          | `ProfileFormValue` \| null          | Giá trị lúc tải xong, để `isDirty` và `buildShopInfoPatch` so |
| `_model`             | `ProfileFormValue`                  | Giá trị các ô                                                 |
| `_errors` (computed) | lỗi theo ô                          | `validateProfileForm(_model(), fieldsOf(mode, loại hình))`    |
| `dirty` (computed)   | boolean                             | `isDirty(mode, _original, _model())`                          |
| `_submitAttempt`     | boolean                             | Đã bấm gửi lần nào chưa (Q1)                                  |
| `_submitting`        | boolean                             | Khóa nút gửi (Q5)                                             |
| `_formError`         | `{ message, traceId? }` \| null     | Dòng lỗi biểu mẫu                                             |
| `_serverFieldError`  | `{ field, message, value }` \| null | Lỗi máy chủ của một ô, kèm giá trị lúc bị từ chối (Q3)        |

`canSubmit = !_submitting() && (!FORM_MODES[mode].requireDirty || dirty())`. Nút gửi `[disabled]="!canSubmit()"`.

## 5. Luồng chung

```
Mở route
  ├─ guard theo FORM_MODES[mode].guard → sai trạng thái thì về /
  ├─ cần tải? ── không ─→ loaded, biểu mẫu trống
  │             └ có ──→ loading ("Đang tải hồ sơ…")
  │                       GET /api/seller/seller/profile
  │                         ├ ok  → _original = _model = formValueFromProfile(p); loaded
  │                         └ lỗi → error: màn lỗi trang + Thử lại (mục 6.1)
  │
Gõ, rời ô → _model đổi → _errors, dirty tính lại; ô đỏ khi touched hoặc đã bấm gửi
  │
Bấm gửi (onSubmit)
  ├─ !canSubmit → bỏ qua
  ├─ _submitAttempt = true; xóa _formError, _serverFieldError
  ├─ có lỗi → ô lỗi đỏ; focus ô lỗi đầu; DỪNG (nút không khóa)
  ├─ _submitting = true → nút "Đang gửi…", khóa
  ├─ body = FORM_MODES[mode].build(...)
  └─ gọi FORM_MODES[mode].call(body), có requestTimeout
       ├ 2xx → (refresh() nếu cần) → snackbar thành công → điều hướng /profile → _submitting = false
       └ lỗi → mapSubmitError(error) → mục 6.2
```

## 6. Xử lý lỗi

### 6.1 Lỗi khi tải

| Lỗi khi `GET` hồ sơ                | `loadState` | Hiện                                                                                                                    |
|------------------------------------|-------------|-------------------------------------------------------------------------------------------------------------------------|
| 5xx, mạng, hết giờ, thân sai khuôn | `error`     | Màn lỗi trang: "Không tải được hồ sơ. Kiểm tra kết nối rồi thử lại.", mã truy vết nếu có, nút "Thử lại" (gọi lại `GET`) |
| 404 `SELLER_PROFILE_NOT_FOUND`     | —           | `SellerStatusService` thành `none`; điều hướng `/`                                                                      |
| 401                                | —           | Bộ chặn đi đăng nhập                                                                                                    |

### 6.2 Lỗi khi gửi

`mapSubmitError(error, mode)` trả một chỉ thị; component chỉ làm theo. Hàm thuần, kiểm bằng Jest.

| Chỉ thị  | Từ                                                                                                                                         | Component làm                                                                     | Nút                            |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------|
| `field`  | Mã gắn được vào ô; `LEGAL_INFO_REQUIRED`, `SELLER_FIELD_INVALID` có tên ô quen                                                             | `_serverFieldError = { field, message, value hiện tại }`; focus ô đó              | Mở khóa                        |
| `form`   | Mã không gắn được vào ô (`SHOP_NAME_LOCKED`, `SELLER_ROLE_NOT_ALLOWED`, mã lạ, tên ô lạ)                                                   | `_formError = { message }`; cuộn tới dòng lỗi                                     | Mở khóa                        |
| `retry`  | `transient`: 5xx, 429, mạng, CSRF sau lần thử lại                                                                                          | `_formError = { message: "Hệ thống đang bận…", traceId }`; `_model` giữ nguyên    | Mở khóa                        |
| `stale`  | `SELLER_PROFILE_EXISTS`, `INVALID_STATE`, `CONCURRENT_UPDATE`, `SELLER_PROFILE_NOT_FOUND`; `unknown-outcome` (hết giờ, 504, 2xx sai khuôn) | `refresh()` → snackbar 8 giây có nút Đóng → điều hướng `FORM_MODES[mode].leaveTo` | **Giữ khóa** tới khi rời trang |
| `ignore` | 401                                                                                                                                        | Không làm gì                                                                      | —                              |

**Cách hiện cụ thể.**
- **Lỗi dưới ô.** `<mat-error>` trong `mat-form-field`, bật bằng `matcherOf`. Lỗi máy chủ chỉ hiện khi ô còn đúng giá trị lúc bị
  từ chối.
- **Dòng lỗi biểu mẫu.** `<p role="alert" class="form-error">` ở đầu biểu mẫu, dưới dòng của chế độ. Có mã truy vết thì thêm dòng
  nhỏ "Mã lỗi: <traceId>".
  - Sau khi đặt lỗi, gọi `scrollIntoView({ block: 'center' })`, vì người dùng đang ở cuối biểu mẫu khi bấm gửi.
  - Lần gửi sau thì dòng lỗi được xóa.
- **Thông báo nổi.** `MatSnackBar.open(message, 'Đóng', { duration: 8000 })`. Mở **sau** khi `refresh()` xong và **trước** khi
  điều hướng. Snackbar sống qua lần chuyển trang nên người dùng vẫn đọc được ở trang mới.
- **Màn lỗi trang.** Khối thay cho biểu mẫu, gồm câu, mã truy vết và nút "Thử lại". Không có biểu mẫu nào bị mất, vì chưa nhập
  gì.

Thông báo thành công cũng dùng snackbar, 4 giây, không có nút.

**Vì sao `stale` giữ khóa nút.** Thao tác có thể đã ghi, hoặc trạng thái đã đổi. Mở khóa thì người dùng bấm lại và nhận thêm một
lỗi nữa, trong khi biểu mẫu sắp biến mất.

**Câu chữ** nằm ở `profile-form.ts`, theo mã lỗi. Bảng câu: `srs.md` mục 5. `mapSubmitError` đọc nhãn ô qua `fieldLabel` để câu
"<Nhãn ô> là bắt buộc" đúng với loại hình đang chọn.

**Mã truy vết.** Lấy từ `ErrorInfo.traceId`, cần thêm vào `@t3n/http` (khung, việc C2). Chưa có thì bỏ dòng "Mã lỗi".

## 7. API

| Hàm                           | Gọi                                                | Thân gửi                                    | Thân trả        |
|-------------------------------|----------------------------------------------------|---------------------------------------------|-----------------|
| `getMyProfile()`              | `GET /api/seller/seller/profile`                   | —                                           | `SellerProfile` |
| `submit(body)`                | `POST /api/seller/seller/profile`                  | `SubmitSellerProfileRequest`                | bỏ qua          |
| `update(body)`                | `PUT /api/seller/seller/profile` (**mới**, srs B1) | `SubmitSellerProfileRequest`                | bỏ qua          |
| `resubmit(body)`              | `POST /api/seller/seller/profile/resubmit`         | `SubmitSellerProfileRequest`                | bỏ qua          |
| `updateShopInfo(patch)`       | `PATCH /api/seller/seller/profile/shop-info`       | `UpdateShopInfoRequest` không có `shopName` | bỏ qua          |

Mọi hàm bọc `requestTimeout()`. Thân trả bị bỏ qua vì sau khi gửi, trang xem tự tải lại hồ sơ.

## 8. Tệp

Theo `5.convention/frontend-structure.md`:

```
features/seller-profile/
  data/seller-profile-command.api.ts         SellerProfileCommandApi: submit, update, resubmit, updateShopInfo
  data/seller-profile-command.model.ts       SubmitSellerProfileRequest, UpdateShopInfoRequest
  profile-form.ts  profile-form.spec.ts      hàm thuần
  pages/profile-form/                        profile-form.component.{ts,html,scss} (+ spec K6)
  pages/profile-view/                        profile-view.component.{ts,html,scss}
core/service/seller-profile/
  seller-profile.api.ts                      SellerProfileApi: getMyProfile, các hằng địa chỉ
  seller-profile.model.ts                    SellerProfile, parseSellerProfile, SellerProfileStatus
  seller-status.service.ts                   trạng thái dùng chung (khung mục 2)
core/guard/profile-state.guard.ts            canSubmit, canEditPending, canResubmit, canEditApproved, hasProfile
```

Lệnh đọc ở `core` vì trạng thái dùng chung và tính năng này cùng đọc. Lệnh ghi chỉ tính năng này dùng nên ở `data/`, lấy địa chỉ từ
`core`. Trang xem đọc bằng `sellerStatus.loadProfile()`: một lời gọi vừa trả hồ sơ vừa làm mới trạng thái. Biểu mẫu đọc thẳng bằng
`SellerProfileApi.getMyProfile()`.

## 9. Việc còn lại

Đã làm `2026-11-22`:
- `FORM_MODES`, `fieldsOf`, `validateProfileForm` theo chế độ, `buildShopInfoPatch`, `isDirty`, `SHOP_NAME_LOCKED`;
- component đọc `FORM_MODES`;
- bốn route và hai guard mới;
- nút ở trang xem và trang chủ;
- lệnh đọc `SellerProfileApi.getMyProfile()` ở `core`, bốn lệnh ghi trong `SellerProfileCommandApi` ở tính năng; mọi lời gọi có
  `requestTimeout()`;
- test `phần` (H1 đến H7, K3, K6, K7, K10).

| #   | Việc                                                                                                             | Cần trước |
|-----|------------------------------------------------------------------------------------------------------------------|-----------|
| V1  | Backend B1 đến B4 (`srs.md` mục 7). Tới khi có B1, "Lưu thay đổi" khi chờ duyệt nhận lỗi và hiện dòng lỗi chung |           |
| V5  | Trang xem chia hai khối dòng "Thông tin pháp lý" và "Gian hàng và liên hệ"                                       |           |
| V6  | Cách hiện lỗi theo mục 6: cuộn tới dòng lỗi, mã truy vết                                                         | Khung C2  |
| V8  | `focusFirstError` tìm ô bằng `data-testid`. Đổi sang thuộc tính riêng (`data-field`) hoặc `viewChildren`      |           |
| V10 | Các route hồ sơ chuyển sang `loadComponent` (khung C4)                                                          |           |
| V11 | CSS của biểu mẫu (`profile-form.component.scss` còn trống), gồm khối `.form-notice` và `.locked`                |           |
| V12 | Test `đóng` ở [`test.md`](test.md)                                                                               |           |

# Duyệt người bán: thiết kế

Nguồn: [`srs.md`](srs.md). Khung, bố cục, `describeError`, `NotifyService`, `ConfirmDialogComponent`, `PageStateComponent`: xem
[`../0-app-shell/design.md`](../0-app-shell/design.md). Hợp đồng API: `service/seller-service/api.yaml`.

## 1. Màn hình

### 1.1 Hàng chờ: `/sellers/pending?page=0`

```
Hàng chờ duyệt                                                     12 hồ sơ
─────────────────────────────────────────────────────────────────────────────
 Tên gian hàng            Loại hình        Nộp lúc              Đã chờ
 ───────────────────────  ───────────────  ───────────────────  ──────────
 Gốm Bát Tràng Minh An    Hộ kinh doanh    18/11/2026 09:12     4 ngày      ← cả dòng bấm được
 Thời trang Lá Xanh       Doanh nghiệp     19/11/2026 14:40     3 ngày
 ...
─────────────────────────────────────────────────────────────────────────────
                                   Số dòng mỗi trang: 20   1–20 / 32   ‹  ›
```

- `mat-table` với `mat-paginator`. Paginator chỉ cho lật trang, không cho đổi cỡ trang: cỡ cố định 20 để URL chỉ có `page`.
- "Đã chờ" tính ở giao diện từ `submittedAt`:
  - dưới một giờ: "x phút";
  - dưới một ngày: "x giờ";
  - còn lại: "x ngày".
- Loại hình hiện bằng nhãn tiếng Việt: `INDIVIDUAL` là "Cá nhân", `HOUSEHOLD` là "Hộ kinh doanh", `BUSINESS` là "Doanh nghiệp".
- Mỗi dòng là liên kết `routerLink` tới `/sellers/:id`. Dùng liên kết chứ không bắt sự kiện bấm, để bấm chuột giữa hay Ctrl+bấm
  mở được tab mới.

### 1.2 Chi tiết: `/sellers/:id`

```
‹ Hàng chờ duyệt

Gốm Bát Tràng Minh An                                  [ Chờ duyệt ]
Nộp lúc 18/11/2026 09:12

┌ Thông báo (chỉ khi có, ví dụ "Người bán vừa sửa hồ sơ…") ──────────────────┐
└────────────────────────────────────────────────────────────────────────────┘

Gian hàng                                Pháp lý
──────────────────────────────           ──────────────────────────────
Tên gian hàng   Gốm Bát Tràng Minh An    Loại hình        Hộ kinh doanh
Mô tả           Gốm thủ công…            Tên chủ hộ       Nguyễn Văn An
Người liên hệ   Nguyễn Văn An            Số căn cước      001203004567
Điện thoại      0912345678               Mã số thuế       0123456789
Địa chỉ LH      (như địa chỉ KD)         Địa chỉ KD       12 Bát Tràng, Gia Lâm, Hà Nội

─────────────────────────────────────────────────────────────────────────────
                                                    [ Từ chối ]  [ Duyệt ]
```

- Hai cột (`display: grid`, hai cột từ 960px, một cột dưới mức đó). Mỗi cột là một danh sách nhãn và giá trị.
- Trường tùy chọn trống thì hiện "—". Riêng địa chỉ liên hệ trống thì hiện "(như địa chỉ kinh doanh)", theo `api.yaml`.
- Nhãn tên pháp lý theo loại hình (srs 2.2). Dòng "Người đại diện" chỉ có ở doanh nghiệp.
- Hồ sơ đã có quyết định thì có ba thay đổi:
  - huy hiệu đổi thành "Đã duyệt" hoặc "Bị từ chối";
  - thêm dòng "Quyết định lúc …" và khối "Lý do từ chối";
  - thanh nút không hiện.
- Liên kết "‹ Hàng chờ duyệt" quay về đúng trang hàng chờ đã đến từ đó (mục 3.3).

### 1.3 Hộp xác nhận duyệt

```
┌ Duyệt hồ sơ ─────────────────────────────────────┐
│ Duyệt gian hàng "Gốm Bát Tràng Minh An"?         │
│ Người bán sẽ đăng được sản phẩm ngay sau đó.     │
│                                [Hủy]  [Duyệt]    │
└──────────────────────────────────────────────────┘
```

### 1.4 Hộp từ chối

```
┌ Từ chối hồ sơ ───────────────────────────────────┐
│ Lý do (người bán sẽ đọc lý do này)               │
│ ┌──────────────────────────────────────────────┐ │
│ │ Ảnh căn cước mờ, tên chủ hộ không khớp…      │ │
│ └──────────────────────────────────────────────┘ │
│ Bắt buộc                                 42/500  │
│                              [Hủy]  [Từ chối]    │
└──────────────────────────────────────────────────┘
```

- `MatDialog` có `textarea` (`matInput`, `cdkTextareaAutosize`, 4 đến 10 dòng) và bộ đếm ký tự.
- Đặt `maxlength=500` trên ô để không gõ quá được. Kiểm trống sau khi cắt khoảng trắng bằng một validator riêng, vì `required`
  của Angular coi "   " là có giá trị.
- Lỗi ô hiện sau khi rời ô hoặc sau lần bấm "Từ chối" đầu.

## 2. Dịch vụ API: `SellerReviewApi`

Đặt ở `features/seller-review/data/seller-review.api.ts`. Mỗi hàm bọc `requestTimeout()` và `unwrapData(parse…)` của `@t3n/http`.

| Hàm                                   | Gọi                                                                 | Trả                      |
|---------------------------------------|---------------------------------------------------------------------|--------------------------|
| `listPending(page)`                   | `GET /api/seller/admin/seller-profiles?page={page}&size=20`         | `PendingPage`            |
| `get(id)`                             | `GET /api/seller/admin/seller-profiles/{id}`                        | `ReviewProfile`          |
| `approve(id, version)`                | `POST …/{id}/approve`, thân `{ version }`                            | `ReviewProfile` (bản sau quyết định) |
| `reject(id, version, reason)`         | `POST …/{id}/reject`, thân `{ version, reason }` (`reason` đã cắt khoảng trắng) | `ReviewProfile` |

Kiểu dữ liệu (`features/seller-review/data/seller-review.model.ts`) chép từ `PendingSellerProfileItem`, `PendingSellerProfilePage`,
`AdminSellerProfile` trong `api.yaml`. Mỗi kiểu có một hàm `parse…` trả `null` khi thân sai khuôn, cùng cách với
`seller-web`.

## 3. Hàng chờ: luồng

**3.1 Vào trang.**
1. Đọc `page` từ query. Thiếu, không phải số, hoặc âm thì coi là 0 và thay URL bằng `?page=0` (`replaceUrl`, không thêm lịch
   sử).
2. Trạng thái trang là `loading`, hiện vòng xoay ở giữa vùng bảng. Tiêu đề trang vẫn hiện.
3. Gọi `listPending(page)`.
4. Kết quả:
   - **Có dòng:** hiện bảng. Paginator lấy `totalElements` và `page`.
   - **Không dòng, `page` là 0:** `PageState` rỗng: "Không có hồ sơ nào đang chờ duyệt".
   - **Không dòng, `page` lớn hơn 0, `totalElements` lớn hơn 0:** trang đã vượt cuối. Điều hướng `replaceUrl` tới trang cuối,
     tức `ceil(totalElements / 20) - 1`. Máy chủ dùng `Page` của Spring Data nên trang vượt cuối trả danh sách rỗng kèm
     `totalElements` đúng, không trả lỗi.
   - **Lỗi:** `PageState` lỗi, câu theo `describeError`. Bấm "Thử lại" thì gọi lại đúng `page` đó.

**3.2 Lật trang.**
- Bấm ‹ hoặc › trên paginator thì `router.navigate([], { queryParams: { page } })`. Không gọi API trực tiếp: URL đổi thì luồng
  3.1 tự chạy.
- Trang theo dõi query bằng `toSignal(route.queryParamMap)` hoặc `input()` kèm `withComponentInputBinding()`, rồi gọi API bằng
  `switchMap`.

**Vì sao đi qua URL.**
- Một đường duy nhất từ URL ra dữ liệu, nên tải lại, quay lại hay dán đường dẫn đều chạy cùng một luồng (srs 2.1).
- `switchMap` hủy lời gọi cũ khi lật nhanh, nên trang 2 về muộn không đè được lên trang 3 (6-ui 5.8).

**Khi đang tải trang mới mà bảng cũ đang hiện.** Giữ bảng cũ, làm mờ nó (`opacity: .5`) và hiện thanh `mat-progress-bar` mảnh
trên đầu bảng. Không thay cả bảng bằng vòng xoay, để bảng không nhảy chiều cao mỗi lần lật.

**3.3 Mở chi tiết rồi quay lại.**
- Liên kết của dòng là `/sellers/:id`, kèm `state: { fromPage: page }` của router.
- Ở trang chi tiết, "‹ Hàng chờ duyệt" đi `/sellers/pending?page={fromPage}`. Không có `fromPage` (mở thẳng đường dẫn) thì đi
  `?page=0`.
- Không dùng `location.back()`: mở thẳng từ đường dẫn thì "back" sẽ rời ứng dụng.

## 4. Chi tiết: luồng và trạng thái

**4.1 Trạng thái của trang.** Giữ bằng signal trong component.

| Signal        | Giá trị                                                                          | Dùng để                                     |
|---------------|----------------------------------------------------------------------------------|---------------------------------------------|
| `view`        | `loading` \| `loaded` \| `not-found` \| `error`                                  | Chọn khối hiện ra                           |
| `profile`     | `ReviewProfile` \| `null`                                                        | Nội dung; **`profile().version` là phiên bản gửi khi quyết định** (Q2) |
| `notice`      | `null` \| `stale` \| `already-decided` \| `unknown-outcome`                      | Khối thông báo trên đầu                     |
| `submitting`  | `null` \| `approve` \| `reject`                                                  | Khóa cả hai nút; nút đang gửi có vòng xoay  |

`canDecide = profile()?.status === 'PENDING_REVIEW' && submitting() === null` (Q1, Q4).

**4.2 Vào trang.**
1. Đọc `id` từ route. Không phải UUID thì `view = not-found` ngay, không gọi API.
2. `view = loading`: vòng xoay giữa trang, chưa có tiêu đề.
3. Gọi `get(id)`.
   - **200:** `profile` nhận dữ liệu, `view = loaded`.
   - **404 `SELLER_PROFILE_NOT_FOUND`** hoặc **400:** `view = not-found`, có liên kết về hàng chờ.
   - **Còn lại:** `view = error`, có "Thử lại".

**4.3 Bấm "Duyệt".**

```
Bấm [Duyệt]
  └─ mở ConfirmDialog (mục 1.3)
       ├─ [Hủy] → đóng, không có gì xảy ra
       └─ [Duyệt]
            ├─ đóng hộp; submitting = 'approve'
            │    nút Duyệt: vòng xoay 18px thay chữ; nút Từ chối: khóa
            ├─ version = profile().version   ← lấy đúng bản đang hiện
            └─ POST approve(id, version)
                 ├─ 200 → NotifyService.success('Đã duyệt gian hàng "<tên>"')
                 │        → điều hướng /sellers/pending?page=<fromPage>
                 └─ lỗi → mục 4.5 với action = 'APPROVED'
```

Hộp xác nhận đóng ngay khi bấm, và trạng thái đang gửi nằm trên nút của trang. Hộp từ chối thì ngược lại (4.4).

**Vì sao duyệt xong thì quay về hàng chờ.** Quản trị làm theo hàng chờ (6-ui mục 2): xong một hồ sơ thì việc tiếp theo là hồ sơ
kế. Ở lại trang chi tiết thì lần nào cũng phải bấm thêm một lần quay về.

**4.4 Bấm "Từ chối".**

```
Bấm [Từ chối]
  └─ mở RejectDialog, truyền vào { id, version: profile().version, shopName }
       ├─ [Hủy] hoặc Esc → đóng; chữ đã gõ mất (chấp nhận: người dùng chủ động hủy)
       └─ [Từ chối]
            ├─ ô chưa hợp lệ → hiện lỗi dưới ô, đưa tiêu điểm vào ô, không gửi
            ├─ hợp lệ → submitting trong hộp: nút Từ chối có vòng xoay, ô và nút Hủy khóa,
            │           disableClose = true (không đóng bằng Esc hay bấm ra ngoài khi đang gửi)
            └─ POST reject(id, version, reason.trim())
                 ├─ 200 → đóng hộp với kết quả 'done'
                 │        → trang: NotifyService.success(...) → về hàng chờ
                 ├─ 400 REJECTION_REASON_REQUIRED → lỗi dưới ô, mở khóa, hộp vẫn mở
                 ├─ 409 (STALE_VERSION | INVALID_STATE) → đóng hộp với kết quả 'conflict'
                 │        → trang xử lý như mục 4.5
                 └─ lỗi hệ thống → hộp vẫn mở, giữ nguyên chữ, dòng lỗi đỏ trên nút kèm mã truy vết,
                                   mở khóa để bấm lại
```

**Vì sao hộp từ chối tự gửi, còn hộp duyệt thì không.**
- Hộp từ chối có dữ liệu người dùng vừa gõ. Gửi lỗi mà đóng hộp thì mất lý do (6-ui 5.7), nên hộp phải giữ chữ và tự hiện lỗi.
- Hộp duyệt không có gì để giữ.

**Ngoại lệ của hộp từ chối: lỗi hệ thống có thể đã ghi.** Lỗi hệ thống thuộc `unknown-outcome` (hết giờ, 504) thì không cho bấm
lại ngay, vì có thể đã ghi rồi. Lúc đó hộp làm như sau:
1. Khóa nút, gọi `get(id)` để kiểm.
2. Hồ sơ vẫn chờ duyệt: mở khóa cho bấm lại.
3. Hồ sơ đã đổi: đóng hộp với kết quả `conflict`, để trang xử lý như mục 4.5.

**4.5 Khi quyết định không chắc đã thành công.** Dùng chung cho duyệt và từ chối. Đầu vào là lỗi và `action`, một trong
`APPROVED` hoặc `REJECTED`:

| Lỗi                         | Làm gì                                                                                                    |
|-----------------------------|-----------------------------------------------------------------------------------------------------------|
| 409 `STALE_VERSION`         | Gọi `get(id)`, thay `profile` bằng bản mới, `notice = stale`. Không gửi lại                               |
| 409 `INVALID_STATE`         | Gọi `get(id)` rồi **đối chiếu** (dưới bảng)                                                               |
| Lỗi hệ thống (mọi loại)     | Gọi `get(id)` rồi **đối chiếu**                                                                           |
| 403 `SELLER_ROLE_NOT_ALLOWED` | Không nên xảy ra (guard đã chặn). Hiện lỗi chung                                                         |
| 401                         | Bộ chặn đã đưa đi đăng nhập                                                                               |

**Đối chiếu sau khi tải lại.**

| Bản mới                                                                         | Kết luận                                               |
|---------------------------------------------------------------------------------|--------------------------------------------------------|
| `status === action` và `reviewedBy === userId` của phiên                        | Thao tác của mình đã thành công: báo "Thao tác đã được ghi nhận", về hàng chờ |
| `status` khác `PENDING_REVIEW` (còn lại)                                        | Người khác đã xử lý: `notice = already-decided`, hiện bản mới, không còn nút   |
| `status === PENDING_REVIEW`                                                     | Chưa ghi: `submitting = null`, báo lỗi hệ thống kèm mã truy vết để bấm lại     |
| Tải lại cũng lỗi                                                                | `notice = unknown-outcome`: "Chưa rõ thao tác đã thực hiện hay chưa." kèm nút "Tải lại hồ sơ". Nút quyết định khóa tới khi tải lại được |

**Vì sao mọi lỗi hệ thống đều tải lại, không tin `classifyFailure`.**
- `classifyFailure` xếp mất mạng vào `transient` ("chưa ghi gì").
- Nhưng mạng có thể rớt **sau** khi yêu cầu đã tới máy chủ.
- Tải lại một hồ sơ rẻ, còn báo "thất bại" cho một quyết định đã ghi thì làm quản trị bấm lại và nhận 409 khó hiểu.
- Đây là bài 6-ui mục 5.2 ở dạng nhỏ: thao tác duyệt không có mã chống lặp, nhưng trạng thái hồ sơ chính là bằng chứng đã ghi
  hay chưa.

**Vì sao `STALE_VERSION` không tự gửi lại.** Xem srs mục 2.5. Mã chỉ được lấy phiên bản từ `profile()`, tức bản đang hiện trên
màn hình (Q2). Không có biến phiên bản nào khác trong component.

## 5. Bảng câu chữ

Đặt ở `features/seller-review/seller-review.messages.ts`, ánh xạ theo `code`. Thành phần không tự viết câu.

| Khóa               | Câu                                                                                          |
|--------------------|----------------------------------------------------------------------------------------------|
| `stale`            | Người bán vừa sửa hồ sơ sau khi bạn mở. Đã tải bản mới, hãy xem lại trước khi quyết định.    |
| `already-decided`  | Hồ sơ này đã được xử lý.                                                                     |
| `recorded`         | Thao tác đã được ghi nhận.                                                                   |
| `unknown-outcome`  | Chưa rõ thao tác đã thực hiện hay chưa.                                                      |
| `approved`         | Đã duyệt gian hàng "{tên}".                                                                  |
| `rejected`         | Đã từ chối gian hàng "{tên}".                                                                |
| `reason-required`  | Nhập lý do từ chối.                                                                          |
| `not-found`        | Không tìm thấy hồ sơ.                                                                        |
| `empty-queue`      | Không có hồ sơ nào đang chờ duyệt.                                                           |

## 6. Tệp

Theo `5.convention/frontend-structure.md`:

```
features/seller-review/
  seller-review.routes.ts           /sellers/pending, /sellers/:id
  seller-review.messages.ts         mục 5
  decision-outcome.ts               hàm thuần cho bảng đối chiếu ở 4.5
  waiting-time.ts                   "x phút / giờ / ngày" (hàm thuần)
  pages/
    pending-list/                   pending-list.component.{ts,html,scss}
    profile-review/                 profile-review.component.{ts,html,scss}
  components/
    reject-dialog/                  reject-dialog.component.{ts,html,scss}
  data/
    seller-review.api.ts            mục 2
    seller-review.model.ts          kiểu và hàm parse
```

`decision-outcome.ts` là chỗ logic dễ sai nhất. Viết nó thành hàm thuần
`(action, reloaded, userId) → 'recorded' | 'already-decided' | 'not-recorded'` để kiểm bằng Jest mà không cần dựng component.

## 7. Điều chưa kiểm

- `reviewedBy` có đúng bằng `userId` của phiên không: cả hai là mã tài khoản (`sub`) theo ADR-0003. Kiểm ở lần chạy thật T5 của
  [`test.md`](test.md).

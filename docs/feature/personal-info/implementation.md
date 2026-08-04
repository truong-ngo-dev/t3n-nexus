# Implementation: Personal Info

**Design**: [`design.md`](design.md)
**Status**: TODO

---

## Phases

| Phase | Tên                                    | Service / Layer    | Status |
|-------|----------------------------------------|--------------------|--------|
| 1     | DB Migration                           | identity-service   | TODO   |
| 2     | REST API — GET & PUT profile           | identity-service   | TODO   |
| 3     | REST API — Avatar upload (MinIO)       | identity-service   | TODO   |
| 4     | Frontend                               | Angular (shared)   | TODO   |

---

## Phase 1 — DB Migration

**Verify:** `SELECT column_name FROM information_schema.columns WHERE table_name='users' AND column_name='avatar_url'` trả về 1 row.

### Checklist
- [ ] Tạo Flyway migration `V9__add_avatar_url_to_users.sql`
  ```sql
  ALTER TABLE users
      ADD COLUMN avatar_url VARCHAR(512);
  ```
- [ ] Chạy migration, confirm không error

---

## Phase 2 — REST API: GET & PUT /me

**Depends on:** Phase 1 (avatar_url column phải tồn tại)

**Verify:**
```bash
# GET
curl -X GET http://localhost:{port}/api/v1/me \
  -H "Authorization: Bearer {token}"
# → 200 { userId, fullName, email, phoneNumber, avatarUrl }

# PUT
curl -X PUT http://localhost:{port}/api/v1/me \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Nguyễn Văn B","phoneNumber":"0909999999"}'
# → 200 với fullName đã update, confirm lại bằng GET
```

### Checklist

**DTO**
- [ ] `UserProfileResponse` — `userId`, `fullName`, `email`, `phoneNumber`, `avatarUrl`
- [ ] `UpdateProfileRequest` — `fullName` (NotBlank), `phoneNumber` (Pattern VN)

**Domain / Service**
- [ ] `UserAccountService.getProfile(userId)` — load `UserAccount`, map sang response
- [ ] `UserAccountService.updateProfile(userId, request)` — validate, update `fullName` + `phoneNumber`, save

**Controller**
- [ ] `MeController` — route `/api/v1/me`
- [ ] `GET /api/v1/me` — extract `userId` từ `Authentication` (JWT claims)
- [ ] `PUT /api/v1/me` — `@Valid` request body, gọi service, trả updated response

**Security**
- [ ] Confirm endpoint yêu cầu authenticated (không cho anonymous)
- [ ] userId lấy từ claims — không nhận từ request param/body

---

## Phase 3 — REST API: Avatar Upload

**Depends on:** Phase 2

**Verify:**
```bash
curl -X POST http://localhost:{port}/api/v1/me/avatar \
  -H "Authorization: Bearer {token}" \
  -F "file=@/path/to/photo.jpg"
# → 200 { avatarUrl: "http://minio:9000/user-avatars/..." }
# Confirm: GET /me trả avatarUrl mới
# Confirm: MinIO bucket user-avatars có file tại path userId/{ulid}.jpg
```

### Checklist

**MinIO setup**
- [ ] Thêm dependency `io.minio:minio` vào identity-service
- [ ] `MinioConfig` — `MinioClient` bean, bucket name `user-avatars` từ `application.yml`
- [ ] `@PostConstruct` trong `MinioConfig`: tạo bucket nếu chưa tồn tại

**Storage service**
- [ ] `AvatarStorageService.upload(userId, inputStream, contentType, size)` → trả `avatarUrl`
  - Path: `{userId}/{ulid}.{ext}`
  - Ext resolve từ `contentType`
- [ ] `AvatarStorageService.delete(avatarUrl)` — parse object key từ URL, gọi MinIO remove
- [ ] Validate content-type trong `{image/jpeg, image/png, image/webp}` — throw `UnsupportedMediaTypeException` nếu sai
- [ ] Validate size ≤ 5MB — throw `MaxUploadSizeExceededException` nếu vượt

**Domain / Service**
- [ ] `UserAccountService.updateAvatar(userId, MultipartFile)`:
  1. Load `UserAccount`, lấy `avatarUrl` cũ
  2. Upload file mới → nhận `newUrl`
  3. Update `user_accounts.avatar_url = newUrl`
  4. Nếu có `avatarUrl` cũ → `avatarStorageService.delete(oldUrl)`
  - Thứ tự: upload trước, delete sau — tránh mất ảnh nếu upload fail

**Controller**
- [ ] `POST /api/v1/me/avatar` — nhận `@RequestParam("file") MultipartFile`
- [ ] Gọi `userAccountService.updateAvatar`, trả `{ avatarUrl }`

**Exception handling**
- [ ] 413 khi file > 5MB
- [ ] 415 khi content-type không hợp lệ
- [ ] 503 khi MinIO unavailable (wrap `MinioException`)

---

## Phase 4 — Frontend

**Depends on:** Phase 2 + Phase 3 (APIs phải up)

**Verify:**
- Guest truy cập `/customer` → thấy header với nút Đăng nhập
- Customer đăng nhập → header hiển thị `UserAvatarComponent`
- Seller/Admin truy cập `/customer` → bị redirect về portal của mình
- `/customer/account/profile` khi chưa login → redirect login
- Tại profile page: sửa tên / SĐT → Save → snackbar "Đã lưu thay đổi"
- Click avatar overlay → chọn file → upload ngay → ảnh mới hiển thị trên header

---

### Step 0 — Fix AuthService (blocking, làm trước)

- [ ] Cập nhật `User` model: thêm `fullName: string`, `avatarUrl: string | null`
- [ ] Fix `AuthService.checkSession()`: parse response `{ sub, contexts, requiresProfileCompletion }` → map sang `User` → `this.user.set(user)`; on 401 → `this.user.set(null)`
- [ ] Fix `AuthService.init()`: gọi `checkSession()` internally, `catchError` → `user.set(null)`, trả về `Observable<void>` hợp lệ (hiện tại `return undefined` — broken)

---

### Step 1 — Routing

**Guards**
- [ ] Tạo `storefrontGuard`: `user === null` (guest) hoặc `role === CUSTOMER` → pass; `SELLER` → redirect `/seller`; `ADMIN` → redirect `/admin`
- [ ] Đảm bảo `authGuard` hiện có: redirect login nếu `user === null`

**Route tree**

```
/customer  → StorefrontShellComponent   canActivate: [storefrontGuard]
  ''       → (home placeholder)
  account  → AccountShellComponent      canActivate: [storefrontGuard, authGuard]
    ''     → redirect account/profile
    profile → AccountProfileComponent

/seller    → SellerPortalComponent      canActivate: [roleGuard('SELLER')]
/admin     → AdminPortalComponent       canActivate: [roleGuard('ADMIN')]
```

- [ ] Chuyển `/customer` từ `loadComponent` đơn lẻ sang `loadChildren` với child routes
- [ ] Gắn guards theo sơ đồ trên
- [ ] Bỏ `checkSession()` trong `CustomerPortalComponent.ngOnInit` (session đã load qua `init()` ở bootstrap)

---

### Step 2 — Storefront Shell + Header

**`StorefrontShellComponent`** — `src/app/customer/`
- [ ] Layout: `<app-storefront-header>` → `<router-outlet>` → (footer placeholder)
- [ ] Không có logic auth — chỉ là layout wrapper

**`StorefrontHeaderComponent`** — `libs/shared/ui/storefront-header/`
- [ ] Logo: text "T3E NEXUS", font Barlow Condensed, all-caps (dùng `web/logo.jpg` tạm nếu có)
- [ ] Right section: `@if (auth.user())` → `<app-user-avatar>` | `<button mat-stroked-button (click)="auth.login()">Đăng nhập</button>`
- [ ] Header: `height: 64px`, `border-bottom: 1px solid #E0E0E0`, `background: #FFFFFF`

**`UserAvatarComponent`** — `libs/shared/ui/user-avatar/`
- [ ] Circular img 40px: dùng `avatarUrl` nếu có, fallback initials (chữ cái đầu `fullName`)
- [ ] `mat-menu` dropdown: **Hồ sơ** → navigate `/customer/account/profile` | **Đăng xuất** → `auth.logout()`
- [ ] Sau khi upload avatar thành công (từ `PersonalInfoTabComponent`): cập nhật `auth.user()` signal → avatar trên header tự refresh

---

### Step 3 — Account Shell (sidebar dashboard)

**`AccountShellComponent`** — `src/app/customer/account/`
- [ ] Layout: `display: flex`, sidebar `240px` + `<router-outlet>` fill remaining
- [ ] Sidebar: `mat-nav-list` + `mat-list-item` với `mat-icon` prefix
  - Dashboard (placeholder, disabled)
  - Đơn hàng của tôi (placeholder, disabled)
  - Địa chỉ (placeholder, disabled)
  - **Thông tin cá nhân** — `routerLink="profile"` (active Phase 4)
  - Đăng xuất — `auth.logout()`
- [ ] Active state: `routerLinkActive` → `font-weight: 600` + `background: #F8F9FA`
- [ ] Sidebar `background: #FFFFFF`, `border-right: 1px solid #E0E0E0`

---

### Step 4 — Account Profile Page

**`AccountProfileComponent`** — `libs/shared/ui/account-profile/`
- [ ] `mat-tab-group`: **Thông tin cá nhân** | **Hồ sơ** | **Bảo mật**
- [ ] Tab "Hồ sơ": placeholder `mat-card` outlined "Đang phát triển"
- [ ] Tab "Bảo mật": placeholder `mat-card` outlined "Đang phát triển"

**`PersonalInfoTabComponent`** — `libs/shared/ui/account-profile/personal-info-tab/`
- [ ] `ngOnInit`: gọi `identityService.getMe()` → patch form
- [ ] Avatar section: circular `<img>` 96px + hidden `<input type="file" accept="image/jpeg,image/png,image/webp">` + `mat-icon-button` camera overlay
- [ ] Click overlay → trigger file input → gọi `uploadAvatar()` ngay (không preview, không confirm)
- [ ] Upload in-progress: overlay spinner thay camera icon, disable form
- [ ] Upload success: cập nhật `auth.user()` signal với `avatarUrl` mới
- [ ] Reactive form: `fullName` (required), `phoneNumber` (pattern `^0[0-9]{9}$`), `email` (disabled)
- [ ] Save button: `mat-flat-button` primary, `border-radius: 24px`, disabled khi `pristine || invalid`
- [ ] Save success: `MatSnackBar` "Đã lưu thay đổi" 3000ms
- [ ] Save error: `MatSnackBar` message lỗi từ API response

**`IdentityService`** — `libs/shared/data-access/src/lib/identity.service.ts`
- [ ] `getMe(): Observable<UserProfile>` → `GET /api/identity/v1/me`
- [ ] `updateProfile(req: UpdateProfileRequest): Observable<UserProfile>` → `PUT /api/identity/v1/me`
- [ ] `uploadAvatar(file: File): Observable<{ avatarUrl: string }>` → `POST /api/identity/v1/me/avatar` (multipart/form-data)

---

### NX boundary tags
- [ ] `libs/shared/ui/storefront-header` — `scope:shared, type:ui`
- [ ] `libs/shared/ui/user-avatar` — `scope:shared, type:ui`
- [ ] `libs/shared/ui/account-profile` — `scope:shared, type:ui`
- [ ] `libs/shared/data-access` (IdentityService thêm vào lib hiện có) — `scope:shared, type:data-access`

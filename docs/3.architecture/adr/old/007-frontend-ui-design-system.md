# ADR-007 — Frontend UI Design System

**Status:** Accepted

## Context

Frontend là Angular SPA (NX monorepo) với 3 lazy-loaded route groups: `/customer/**`, `/seller/**`, `/admin/**`. Cần chốt design system thống nhất trước khi implement component để tránh inconsistency giữa các portal và các page group.

Design reference được phân tích từ: Amerce fashion e-commerce (tfamerce.vercel.app) + SemiDash admin dashboard. Ưu tiên Angular Material tối đa — custom CSS chỉ khi Material không đáp ứng được.

**Logo:** V1 — simple interlocking X pattern, thick diagonal lines, pure black. Scales tốt từ 16px favicon đến hero size. V2 (complex layered diamond) chỉ dùng cho splash screen / hero decorative nếu cần.

## Decision

---

### 1. Framework

**Angular Material M3** — dùng toàn bộ, không mix thêm UI library khác.  
State management: `@ngrx/signals`.  
Không dùng Micro Frontend — NX tag-based boundary enforcement.

---

### 2. Design Tokens

#### Color Palette

| Token            | Giá trị                | Dùng cho                                             |
|------------------|------------------------|------------------------------------------------------|
| Primary          | `#0D0D0D` (near-black) | CTA chính, active state, selected chip               |
| On-Primary       | `#FFFFFF`              | Text trên nền primary                                |
| Background       | `#FFFFFF`              | Page background                                      |
| Surface          | `#FFFFFF`              | Card, dialog, input background                       |
| Surface-variant  | `#F8F9FA`              | Section xen kẽ, sidebar content                      |
| Outline          | `#E0E0E0`              | Card border, input border, divider                   |
| Outline-variant  | `#F0F0F0`              | Subtle separator                                     |
| On-Surface       | `#0D0D0D`              | Body text                                            |
| On-Surface-Muted | `#757575`              | Placeholder, caption, secondary text                 |
| Error            | `#D32F2F`              | Form error, badge SALE, badge Canceled, "Buy It Now" |
| On-Error         | `#FFFFFF`              |                                                      |
| Tertiary         | `#D32F2F`              | Highest-urgency CTA ("Buy It Now")                   |
| Admin-Sidebar    | `#1A1A1A`              | Seller/Admin left sidebar background                 |

#### Functional Badge Colors (status indicators)

| Badge                   | Background             | Text  |
|-------------------------|------------------------|-------|
| NEW                     | `#2E7D32` (green-800)  | white |
| SALE                    | `#D32F2F` (red-700)    | white |
| Delivery / In Stock     | `#1565C0` (blue-800)   | white |
| Pending / Low Stock     | `#E65100` (orange-900) | white |
| Completed / Paid        | `#2E7D32` (green-800)  | white |
| Canceled / Out of Stock | `#D32F2F` (red-700)    | white |
| Processing              | `#6A1B9A` (purple-800) | white |

#### Shape

| Token            | Giá trị       | Dùng cho                |
|------------------|---------------|-------------------------|
| `--shape-button` | `24px` (pill) | Tất cả buttons          |
| `--shape-card`   | `8px`         | Card, dialog            |
| `--shape-chip`   | `4px`         | Size chip, color swatch |
| `--shape-input`  | `4px`         | Form field              |
| `--shape-badge`  | `12px`        | Status badge pill       |

#### Typography

Hai font riêng biệt — brand font echo wordmark logo, plain font cho readability:

- **Brand font:** `Barlow Condensed` (Bold/Black) — Display, Headline, section title. Echo style wordmark "T3E NEXUS" trong logo: bold, geometric, condensed, all-caps.
- **Plain font:** `Inter` — Body, caption, label, form field. Neutral, high readability.

| Role                     | Font             | Size   | Weight | Transform |
|--------------------------|------------------|--------|--------|-----------|
| Display (hero)           | Barlow Condensed | `56px` | 800    | uppercase |
| Headline (section title) | Barlow Condensed | `32px` | 700    | —         |
| Title (card, dialog)     | Inter            | `20px` | 600    | —         |
| Body                     | Inter            | `14px` | 400    | —         |
| Caption                  | Inter            | `12px` | 400    | —         |
| Label (button, chip)     | Inter            | `14px` | 500    | —         |

#### Elevation / Shadow

Cards: không có shadow — dùng `1px solid #E0E0E0`. Chỉ dùng elevation cho dialog và dropdown.

---

### 3. Angular Material Theme Config

```scss
@use '@angular/material' as mat;

$theme: mat.define-theme((
  color: (
    theme-type: light,
    primary:   mat.$neutral-palette,
    tertiary:  mat.$red-palette,
  ),
  typography: (
    brand-family:  'Barlow Condensed, sans-serif',
    plain-family:  'Inter, sans-serif',
  ),
  density: (scale: 0),
));
```

---

### 4. Component Conventions

#### Buttons

| Variant             | Material Component               | Dùng cho                                |
|---------------------|----------------------------------|-----------------------------------------|
| Primary CTA         | `mat-flat-button` (primary)      | Add to Cart, Login, Save                |
| Highest-urgency CTA | `mat-flat-button` (tertiary/red) | Buy It Now                              |
| Secondary           | `mat-stroked-button`             | Cancel Order, Create Account, Quick Add |
| Text action         | `mat-button`                     | Forgot Password, Remove, link-style     |
| Icon                | `mat-icon-button`                | Wishlist, Share, qty +/-                |

Tất cả buttons dùng `border-radius: 24px` (override Material default).

#### Form Fields

`mat-form-field` với `appearance="outline"`. Không dùng floating label — dùng `placeholder` only. Password field có `mat-icon-button` suffix toggle eye.

#### Cards

`mat-card` với `appearance="outlined"`. Không có shadow. `border-radius: 8px`.

#### Chips / Size Selector

`mat-chip-listbox` + `mat-chip-option`. Selected: black filled + white text. Unselected: outlined.

#### Tabs

`mat-tab-group` với `mat-ink-bar` black. Dùng cho: product detail (Description/Reviews/Shipping), orders (All/Pending/Delivery/Completed/Canceled), product list (New Arrivals/Best Sellers/On Sale).

#### Data Table

`mat-table` + `mat-paginator` + `mat-sort`. Status column dùng `mat-chip` với màu từ bảng Functional Badge Colors.

#### Stepper (Checkout)

`mat-stepper` horizontal, 4 steps: **Login → Billing → Order → Payment**. Navigation: Back (outlined pill) left + Next (black filled pill) right.

#### Snackbar / Toast

`MatSnackBar` với duration 3000ms. Không custom thêm.

---

### 5. Layout Shells

#### Storefront (`/customer/**`)

```
┌─────────────────────────────────────────────┐
│  Announcement bar (black, 36px)             │
│  Header: Logo | Nav | Search/Wishlist/Cart  │
├─────────────────────────────────────────────┤
│  <router-outlet>                            │
│    Page content (full-width hoặc max-w)     │
├─────────────────────────────────────────────┤
│  Footer: Logo+info | Links | Newsletter     │
└─────────────────────────────────────────────┘
Mobile: bottom nav bar 5 items (Shop/Search/Account/Wishlist/Cart)
```

#### Account Section (`/customer/account/**`)

```
┌─────────────────────────────────────────────┐
│  Header                                     │
├──────────────┬──────────────────────────────┤
│  Left sidebar│  Main content area           │
│  (240px)     │  Breadcrumb + Page title     │
│  Dashboard   │  + content                  │
│  Your Orders │                             │
│  My Address  │                             │
│  Setting     │                             │
│  Logout      │                             │
├──────────────┴──────────────────────────────┤
│  Footer                                     │
└─────────────────────────────────────────────┘
```

Sidebar items: `mat-nav-list` + `mat-list-item` với icon prefix. Active: bold text + subtle background.

#### Auth Pages (`/auth/**`)

Full page, không có header/footer thông thường. Centered card (max-width 480px) trên nền white/light gray. Cùng B&W tone với storefront. Áp dụng cho: Login, Register, MFA OTP, Forgot Password, Reset Password, Email Verification.

#### Seller Portal (`/seller/**`)

```
┌──────────┬──────────────────────────────────┐
│  Dark    │  Top bar: search + user avatar   │
│  sidebar │──────────────────────────────────│
│  (#1A1A) │  Page content                   │
│  240px   │  (cards, tables, charts)        │
│          │                                 │
└──────────┴──────────────────────────────────┘
```

#### Admin Portal (`/admin/**`)

Cùng shell với Seller Portal. Sidebar items khác (user management, reports, moderation).

---

### 6. Page-Specific Patterns

#### Home Page

- Announcement bar → Header → Hero banner (full-width editorial) → Category strip (horizontal scroll) → Product tabs (New Arrivals/Best Sellers/On Sale) → Featured sections → Testimonials → Footer

#### Product List

- Filter/sort bar + product grid (4 cols desktop, 2 cols mobile)
- Product card: image + badge (NEW/SALE) + name + price + color swatches + Quick Add button

#### Product Detail

- 2 column: left = large image + thumbnail strip (5 ảnh, active có black border) | right = category label + name + price (strikethrough + red % badge) + color swatches + size chips + qty + Add to Cart (black pill) + Buy It Now (red pill) + delivery info
- Tabs bên dưới: Description | Customer Reviews | Shipping & Returns | Return Policies
- Related Products / Recently Viewed

#### Cart

- 2 column: left = product table (image + name + variant + qty control + remove) | right sticky = free ship progress + Order Summary + shipping options + Process to Checkout
- Voucher input dưới bảng
- Cart expiry countdown banner (amber)
- "You may be interested in..." section cuối

#### Checkout

`mat-stepper` horizontal 4 bước. Mỗi step là form riêng. Back/Next navigation ở dưới mỗi step.

| Step | Nội dung |
|---|---|
| 1 — Login | Confirm identity (đã login rồi thì skip) |
| 2 — Billing | Địa chỉ giao hàng, chọn địa chỉ đã lưu hoặc nhập mới |
| 3 — Order | Review items, qty, seller grouping |
| 4 — Payment | Phương thức thanh toán, confirm |

#### Order List

Left sidebar + main content. Tab filter: All Order / Pending / Delivery / Completed / Canceled. Order card (không phải table row): order number + status badge → product rows → Order Details + Cancel Order buttons.

#### Account Settings

Form 2-column grid. Sections: Information (avatar upload + personal info) + Change Password. Save button black pill.

#### Admin/Seller Dashboard

Stats row (4 KPI cards) + Sales chart + Top products table + Multi-widget row (social traffic / product stats donut / customer support feed / recent activity) + Recent Orders full table.

---

### 7. Mobile Breakpoints

| Breakpoint | Width | Thay đổi |
|---|---|---|
| Mobile | `< 768px` | Bottom nav bar, 2-col product grid, stacked layout |
| Tablet | `768px – 1024px` | 3-col product grid |
| Desktop | `> 1024px` | Full layout, 4-col product grid, sticky sidebars |

---

## Consequences

**+** Nhất quán hoàn toàn giữa 3 portal nhờ shared theme và component conventions  
**+** Angular Material cung cấp accessibility, keyboard navigation, dark mode ready miễn phí  
**+** Pill button + outlined input + B&W palette là timeless — không cần redesign theo trend  
**−** Pill border-radius phải override Material default trên mọi button — cần global style  
**−** Storefront (B&W) và Admin sidebar (dark #1A1A1A) hơi khác nhau — cần đảm bảo không confuse user  
**−** Không có reference ảnh cho Wishlist và Address book — follow Material standard list/form pattern

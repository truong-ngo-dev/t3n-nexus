# Quy ước đặt tên

Nguyên tắc: **ngôn ngữ nghiệp vụ dùng tiếng Việt, mọi thứ nằm trong mã và hệ thống dùng tiếng Anh**, và có bảng ánh xạ giữa
hai bên để tài liệu và mã gọi cùng một thứ.

## 1. Theo tầng

| Tầng | Ngôn ngữ | Ví dụ |
|---|---|---|
| Tài liệu nghiệp vụ (gốc, chặng, `analysis.md`): tên BC, vai, bên tham gia, thuật ngữ | Tiếng Việt | Xác thực, Người dùng, Phiên |
| Tên thư mục và file tài liệu | Tiếng Anh | `docs/service/oauth2-service/analysis.md` |
| Thư mục tài liệu service | Theo tên service | `docs/service/oauth2-service/` |
| Tên service (đơn vị triển khai) | Tiếng Anh, `kebab-case`, kết thúc `-service` | `oauth2-service`, `identity-service` |
| Thực thể, value object, event, lỗi nghiệp vụ, hàm trong mã | Tiếng Anh, `PascalCase` cho kiểu, `camelCase` cho hàm | `UserAccount`, `UserAccountRegistered`, `EmailTaken` |
| Mã vai | Tiếng Anh, `UPPER_SNAKE` | `USER`, `ADMIN` |
| API: đường dẫn, trường | Tiếng Anh | `/register`, `email` |
| API: mã lỗi | Tiếng Anh, `UPPER_SNAKE`; thông điệp hiển thị tiếng Việt | `EMAIL_TAKEN` |
| Cơ sở dữ liệu: bảng, cột | Tiếng Anh, `snake_case` | `user_accounts`, `password_hash` |
| Tiền tố ID trong tài liệu | Viết tắt tiếng Anh | `ATH`, `IDN` |

## 2. BC, service và thuật ngữ chính

| BC (tiếng Việt) | Tên mã | Service | Tiền tố |
|---|---|---|---|
| Xác thực | authentication | `oauth2-service` | `ATH` |
| Định danh | identity | `identity-service` | `IDN` |

Service tên `oauth2-service` vì nó là máy chủ ủy quyền theo chuẩn OAuth2 và OIDC; BC của nó vẫn là Xác thực, gồm cả tài
khoản, phiên, xác thực nhiều lớp.

| Thuật ngữ | Tên mã |
|---|---|
| Tài khoản (đăng nhập) | `UserAccount` |
| Hồ sơ định danh | `IdentityProfile` |
| Phiên | `Session` |
| Vai | `Role` |
| Thiết bị | `Device` |
| Lịch sử đăng nhập | `LoginHistory` |
| Mật khẩu hợp lệ | `RawPassword` (giá trị chưa băm), `PasswordHash` (giá trị đã băm) |
| Hợp đồng danh tính | `IdentityClaims` |

Tài khoản đăng nhập tên `UserAccount`, không phải `Account`, để không trùng với các khái niệm "tài khoản" của BC khác về sau
(ví dụ tài khoản trong Sổ cái). Cùng lý do, khóa tham chiếu tới nó từ BC khác là `userAccountId`, không phải `accountId`.

Thuật ngữ mới thêm vào bảng này khi `service.md` của BC được viết.

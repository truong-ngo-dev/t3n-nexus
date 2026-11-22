# ADR-0009: Bản ghi kiểm toán bất biến ở lưu trữ

> **Trạng thái:** Accepted `2026-10-25` · **Ngày:** `2026-10-24` · **Phạm vi:** `audit-service` (Nhật ký kiểm toán), và mọi bảng khác mà sau này cần "chỉ ghi thêm" với mức bảo đảm tương đương
>
> Chuyển Accepted sau khi chạy thật ở feature 02 của C1a (mục "Cần kiểm" cuối file đã đạt).

## Bối cảnh

Giá trị của Nhật ký là bằng chứng khi tranh chấp (`audit-service/analysis.md`, `INV-AUD-01`, R13 của `stage.md`). Bằng chứng mà người có quyền ghi vào cơ sở dữ liệu cũng sửa hay xóa được thì không còn là bằng chứng. Hai nguy cơ khác nhau cần chặn:

1. **Lỗi mã hoặc mã độc trong chính dịch vụ** (một use case sửa, xóa nhầm; một lỗ hổng cho chạy câu lệnh tùy ý): chặn ở tài khoản cơ sở dữ liệu mà dịch vụ dùng.
2. **Thao tác tay trên cơ sở dữ liệu bởi người có tài khoản đủ quyền** (kể cả vô ý khi sửa dữ liệu): chặn thêm một lớp, không dựa vào việc người đó nhớ.

Không chặn được: người có quyền quản trị cơ sở dữ liệu cố ý gỡ lớp chặn. Mức bảo đảm đó cần bằng chứng toàn vẹn (chuỗi băm, lưu bên ngoài), để ở phần chưa làm (`analysis.md` mục 12).

## Quyết định

1. **Hai tài khoản cơ sở dữ liệu tách biệt.** Tài khoản **chạy migration** là chủ bảng. Tài khoản **chạy dịch vụ** không phải chủ bảng và chỉ có quyền `INSERT` và `SELECT` trên bảng bản ghi; không có `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER`, và không có quyền tạo đối tượng. Dịch vụ kết nối bằng tài khoản thứ hai; migration chạy bằng tài khoản thứ nhất, lúc khởi động hoặc trong bước triển khai.
2. **Bộ kích hoạt (trigger) từ chối `UPDATE`, `DELETE` và `TRUNCATE`** trên bảng, cho mọi tài khoản kể cả chủ bảng. Lớp thứ hai này chặn thao tác tay của người dùng tài khoản chủ bảng; gỡ nó là một lệnh `ALTER TABLE` tường minh, nằm trong nhật ký cơ sở dữ liệu.
3. **Không có thao tác sửa hay xóa trong mã**: miền không có hành vi sửa; cổng kho chỉ có thêm, tìm, đếm; API không có điểm cuối ghi.
4. **Khử trùng không dùng cập nhật**: nhận lặp cùng mã sự kiện nguồn xử lý bằng `INSERT ... ON CONFLICT DO NOTHING` trên ràng buộc duy nhất, không bằng `UPDATE` hay xóa rồi ghi lại.
5. **Chuỗi băm không làm ở C1a.** Ghi là điểm nối: thành phần của bản ghi có thể thêm cột băm mà không đổi các quyết định trên.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Chỉ chặn ở mã (không có use case sửa, xóa) | Lỗi mã hay lỗ hổng chạy câu lệnh tùy ý vẫn sửa được; không chặn thao tác tay |
| Chỉ dùng trigger, một tài khoản có đủ quyền | Tài khoản của dịch vụ có thể `ALTER TABLE ... DISABLE TRIGGER`; lớp chặn nằm trong tay đúng thứ cần bị chặn |
| Chỉ dùng quyền tài khoản, không trigger | Thao tác tay bằng tài khoản chủ bảng (vẫn dùng khi sửa lỗi dữ liệu) không bị chặn |
| Lưu ở kho chỉ-ghi-thêm bên ngoài (đối tượng bất biến, sổ cái chuyên dụng) | Hạ tầng mới, không cần cho mức bảo đảm học tập ở C1a; giữ làm hướng khi có yêu cầu pháp lý |
| Chuỗi băm ngay từ C1a | Thêm cột, thuật toán nối, kiểm tra định kỳ trong khi chưa có yêu cầu hay sự cố giả mạo; ghi là điểm nối |

## Hệ quả

- Migration của Nhật ký chạy bằng tài khoản khác với tài khoản chạy dịch vụ: hai bộ thông tin kết nối ở cấu hình (migration và dịch vụ), mật khẩu đọc từ biến môi trường.
- Mọi thay đổi cấu trúc bảng về sau (thêm cột) làm bằng migration của tài khoản chủ bảng; thay đổi cần tạm gỡ trigger phải là migration có chủ đích, có người rà.
- Sửa lỗi dữ liệu trong nhật ký **không có đường nào**; bản ghi sai được bù bằng bản ghi mới giải thích, không sửa bản cũ.
- Kiểm thử cần cơ sở dữ liệu thật với hai tài khoản (Testcontainers), không dùng cơ sở dữ liệu trong bộ nhớ.

## Cần kiểm khi chạy thật

- ~~Tài khoản dịch vụ: `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER TABLE` đều bị từ chối bởi quyền.~~ Đạt `2026-10-25` (test tích hợp Postgres 16, kể cả gỡ trigger và tạo đối tượng).
- ~~Tài khoản chủ bảng: `UPDATE`, `DELETE`, `TRUNCATE` bị từ chối bởi trigger.~~ Đạt `2026-10-25`.
- ~~Tài khoản dịch vụ vẫn ghi thêm và đọc được (không chặn nhầm đường chạy bình thường).~~ Đạt `2026-10-25`.
- ~~Dịch vụ khởi động và chạy được với cơ sở dữ liệu mới (migration bằng tài khoản chủ bảng rồi dịch vụ bằng tài khoản còn lại).~~ Đạt `2026-10-25` trong test, và ở bước 8 trên Postgres 16 của stack cô lập: tệp `../../../infra/audit/init-audit-roles.sql` tạo hai tài khoản, Flyway chạy bằng `audit_owner` tạo bảng, dịch vụ chạy bằng `audit_app` ghi và đọc được. Chưa chạy bằng container `postgres-audit` của compose dev (container chưa được khởi động).

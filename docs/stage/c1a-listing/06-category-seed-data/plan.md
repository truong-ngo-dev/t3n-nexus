# Feature 06: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định; **Agent** viết phần thường, người rà), và cách biết bước đã xong. Quy tắc giống feature 01 đến 05: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `flow.md` mục 2 và 7; phá thử sau; không commit.

Feature nhỏ: một script, một lớp test, một mục README. Không có mã sản xuất mới, không sửa thư viện dùng chung, không ghi hạ tầng dev.

## Từ luồng đến giải pháp

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Kiểm bảng có mặt | `DO` kiểm `to_regclass` cho năm bảng, thiếu thì `RAISE EXCEPTION` nêu tên bảng (`tech.md` T1) | `infra/seed/catalog.sql` | Đã làm (`2026-11-04`). KT-04 |
| Khóa tư vấn và kiểm ba bảng trống | `pg_advisory_xact_lock`, rồi `\gset` cờ `can_seed`; có dòng thì in "exists", `COMMIT` rỗng (`tech.md` T3) | `infra/seed/catalog.sql` | Đã làm (`2026-11-04`). KT-02, KT-03 |
| Dựng nội dung bộ mẫu | Bảng tạm `ON COMMIT DROP` chứa nội dung `tech.md` T2, mã sinh bằng hàm UUID v7 tạm (`tech.md` T4) | `infra/seed/catalog.sql` | Đã làm (`2026-11-04`). KT-01, KT-10 |
| Ghi theo thứ tự phụ thuộc | Thương hiệu → mẫu → tùy chọn → danh mục (tham chiếu cha theo đường dẫn tên) → dòng gán; một giao dịch (`tech.md` T1, T5) | `infra/seed/catalog.sql` | Đã làm (`2026-11-04`). KT-01, KT-05 |
| Thông báo kết quả | `created` kèm số dòng từng bảng, hoặc `exists` | `infra/seed/catalog.sql` | Đã làm (`2026-11-04`). KT-01, KT-02 |
| Chứng minh dữ liệu đúng nghiệp vụ | Dựng lại qua hàm của miền; đọc qua API; so khóa tên với `NameKey.of` (`tech.md` T6) | `CatalogSeedTest` | Đã làm (`2026-11-04`). KT-06 đến KT-09 |
| Hướng dẫn người vận hành | Mục mới trong `infra/seed/README.md`: thứ tự chạy, cách chạy không cần `psql`, thông báo | `infra/seed/README.md` | Đã làm (`2026-11-04`) |

## Điều kiện bắt đầu

Đã chốt: Q1 đến Q4 (`tech.md`, `2026-11-04`); Q5 theo đề xuất, chưa bị phản đối. Feature 05 đã đóng (cấu trúc, API, `V1`). Cần Docker chạy được cho Testcontainers.

**Ghi hạ tầng dev:** không có. Mọi kiểm chứng chạy trên Postgres cách ly (Testcontainers; ngăn xếp `probe-` cho bước chạy thật). **Không chạy script vào `catalog_db` dev**: cơ sở dữ liệu đó còn lịch sử migration cũ, cần dựng lại trước, và việc dựng lại là xóa dữ liệu dev nên chỉ làm khi người dùng cho phép đúng đối tượng.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 5 (R1 đến R12) và mục 7 (KT-01 đến KT-11): mỗi đường không suôn sẻ có KT chưa. Cột "Chứng minh" của mục 5 đã ghi KT tương ứng; R12 chứng minh bằng chính ràng buộc "không chạy vào dev" ở đây.

Xong khi: người đồng ý bảng.

### Bước 2. Script và test · Người (script), Agent hoặc Người (test)

Việc: (1) viết `infra/seed/catalog.sql` theo `tech.md` T1 đến T5; (2) viết `CatalogSeedTest` (KT-01 đến KT-10) trên nền `CatalogApiTestBase`; (3) mục README.

Xong khi: `CatalogSeedTest` xanh; chạy cả bộ catalog không đỏ.

**Triển khai (`2026-11-04`):** xong. Ghi chú thực hiện:
- Nội dung bộ mẫu có trong một bộ sinh tạm (ngoài repo) để không gõ nhầm 57 dòng gán; tệp SQL sinh ra là sản phẩm lưu trong repo, và test độc lập đối chiếu với miền nên không tin vào bộ sinh.
- Thứ tự thực tế: script viết và chạy thử tay trên Postgres cách ly trước (lần nạp đầu đủ 10/13/35/22/57, lần hai "exists", chưa migrate thoát mã 3), rồi viết test. Lệch với "test trước" của quy tắc chung; phá thử ở bước 3 bù (mỗi lỗi cố ý phải làm test đỏ).
- Test do Claude tự viết, không giao `agy` (một lớp test, một tệp, cần đọc nhiều chữ ký miền; chi phí giao và rà không nhỏ hơn tự viết).

### Bước 3. Phá thử · Người

Sửa tệp script ở bản sao, mỗi lần một lỗi cố ý, chạy `CatalogSeedTest`: mỗi lỗi phải làm ít nhất một test đỏ. Danh sách ở `tech.md` T6 (mười lỗi). Khôi phục script sau mỗi lần.

Xong khi: không lỗi nào sống sót.

**Triển khai (`2026-11-04`):** xong, **10/10 lỗi làm test đỏ**, script khôi phục đúng bản gốc sau loạt chạy. Lỗi cố ý: `name_key` sai (`lock&lock` thành `lock-lock`); mẫu chọn bỏ hết tùy chọn (`ram`); dòng gán vào danh mục cấp hai; `constraints` của mẫu chữ có `min`; `Loa bluetooth` mất mẫu chọn một duy nhất; tên danh mục trùng khóa trong cùng cha; bỏ khóa tư vấn (hai lần chạy đồng thời); ghi một hàng `outbox_events`; mã UUID v4 thay v7; thương hiệu ngừng dùng. Số ca đỏ từ 1 đến 9 tùy lỗi.

### Bước 4. Chạy thật (KT-11) · Người

Ngăn xếp cách ly chỉ cần Postgres và ứng dụng (không Kafka): ứng dụng khởi động trên cơ sở dữ liệu trống (Flyway), chạy script bằng `psql` trong container, gọi API thật bằng token ký thật: cây, thuộc tính của một lá, thương hiệu; chạy script lần hai không đổi. Không chạm hạ tầng dev.

Xong khi: kịch bản KT-11 đạt; ngăn xếp gỡ sạch.

**Triển khai (`2026-11-04`):** xong, **19/19 mục đạt**. Ngăn xếp `probe-catalog` chỉ Postgres, ứng dụng (`-Xmx384m`, cổng 47005) và máy chủ khóa thử (cổng 56004): Flyway tạo bảng trên cơ sở dữ liệu trống; cây rỗng trước khi nạp; chạy chính `catalog.sql` bằng `psql` trong container: `created`, 10/13/35/22/57, UUID v7, hộp thư trống; cây, thuộc tính Smartphone (10 dòng, màu 8 tùy chọn, `screen_size` inch 3 đến 8, `release_date` MONTH), thương hiệu (có `Lock&Lock`) đọc đúng qua token ký thật; tạo trùng tên khác dấu hoa thường 409, vai thường ghi 403; chạy lần hai `exists`, mọi mã định danh không đổi. Đã gỡ: dừng đúng hai tiến trình của tôi, `compose down -v`; không chạm `infra-*` hay cơ sở dữ liệu dev.

### Bước 5. Đóng · Người, Agent

Điền cột "Triển khai"; `deferred.md` (Tìm kiếm C1b dựng chỉ mục ban đầu từ cơ sở dữ liệu vì seed không phát event); `service.md` mục 8 và `data.md` ghi nơi có dữ liệu khởi tạo; `00-features.md` đóng feature 06 kèm bài học.

**Triển khai (`2026-11-04`):** xong các mục trên. Số liệu cuối: 326 test của catalog xanh (314 cũ + 12 của `CatalogSeedTest`; không chạy bốn lớp test cũ của `oauth2-service` nối cơ sở dữ liệu dev, không sửa thư viện dùng chung); phá thử 10/10; chạy thật 19/19.
- **Dựng lại và nạp dev (`2026-11-04`, người dùng cho phép):** `catalog_db` trong `infra-postgres-catalog-1` (cổng 5436) đã xóa và tạo lại (dữ liệu cũ: 2 thương hiệu, 2 mẫu, 3 danh mục, 1 sản phẩm, 1 biến thể, 2 hàng hộp thư; lịch sử Flyway cũ 13 dòng); `catalog-service` khởi động một lần để Flyway áp `V1`; chạy `catalog.sql` vào đó: `created`, 10/13/35/22/57, hộp thư 0. Không động tới container hay cơ sở dữ liệu nào khác; connector `catalog` ở Connect dev chưa đăng ký.
- **Chỗ lệch đã biết ở trạng thái đóng:** (1) bước 1 chờ người rà bảng R/KT (đề xuất đã có ở `flow.md` mục 5, cột "Chứng minh"). (2) Việc dựng lại `catalog_db` dev và nạp bộ mẫu đã làm sau khi người dùng cho phép (xem bên dưới). (3) Q5 (chạy lại khi đã có dữ liệu thì không thêm gì) theo đề xuất, chưa bị phản đối.

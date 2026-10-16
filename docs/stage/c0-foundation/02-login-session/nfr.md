# Feature 02: đáp ứng NFR

> Nhãn mức chứng minh theo NFR mục 5: **Đã đo**, **Suy ra**, **Chỉ thiết kế**. Các phép đo tải, độ trễ và kích thước bảng chưa chạy
> (chờ bộ đo nền, feature 08, và bước 14 của kế hoạch): các dòng đó còn là Chỉ thiết kế. Số tải và dữ liệu lấy ở `tech.md` T2.

| NFR áp dụng | Cách đáp ứng | Cách đo | Nhãn hiện tại |
|---|---|---|---|
| Đăng nhập thường ngày ~5/giây (NFR 3.3) | Mỗi lần băm một lần, đồng bộ, theo ADR-0002 | Tải duy trì 5 yêu cầu/giây trong 5 phút | Chỉ thiết kế |
| **Đăng nhập flash sale ~700/giây** (NFR 3.3) | Không cam kết trên một máy: với ~80 ms mỗi lần băm (số giả định của feature 01, chưa đo), 700/giây cần khoảng **56 lõi CPU**, vượt xa máy dev. Mục tiêu ở C0 là **đo trần băm/giây** của máy (feature 08). Giữ đăng nhập đúng mà chịu được làn sóng là điểm sâu C5. Giới hạn số lần thử (`tech.md` T3) và giới hạn ở cổng (feature 06) là lớp bảo vệ | Trần băm của feature 08; trần thử tăng dần tới điểm gãy | Chỉ thiết kế (con số 700 chỉ để tính hệ quả) |
| Độ trễ đăng nhập (đề xuất, NFR gốc chưa có) | Chủ yếu là một lần băm; mục tiêu P99 < 500 ms ở 5 yêu cầu/giây | Cùng phép đo, đối chiếu trần băm của feature 08 | Chỉ thiết kế |
| Làm mới token ~22/giây thường ngày, ~220/giây flash sale (NFR 3.3) | Không băm. Mỗi lần: đọc phiên ứng dụng theo `sid`, đọc và ghi `oauth2_authorization` theo khóa chính, ký 2 JWT bằng khóa trong bộ nhớ (`tech.md` T4). Mục tiêu P99 < 100 ms ở 220/giây (đề xuất) | Tải làm mới ở các mức; `EXPLAIN (ANALYZE, BUFFERS)` xác nhận dùng khóa chính; số truy vấn khóa ký mỗi lần cấp token = 0 | Chỉ thiết kế |
| Bản ghi phiên ≤ ~1,5 triệu phiên người dùng, ≤ ~2 triệu phiên ứng dụng (NFR 3.4) | Chỉ mục đã chốt (ADR-0003, `_viec-con-lai.md` B3); tra khóa chính | Nạp dữ liệu cỡ này, đo độ trễ tra khóa chính, kích thước chỉ mục và bảng sau tải làm mới | Chỉ thiết kế (số lượng là giả định) |
| Đúng khi đồng thời (INV-ATH-10) | Ràng buộc duy nhất `(loginSessionId, clientId)` ở cơ sở dữ liệu | 100 yêu cầu song song cùng (phiên người dùng, ứng dụng) ra đúng một phiên ứng dụng; lặp 100 lần (KT-11) | Chưa đo |
| Bảo mật dữ liệu | Mật khẩu, token, `SESSION_ID`, `authorization_id` không có trong log, token, event | Quét log và cơ sở dữ liệu sau test (KT-08, 25) | Chưa đo |
| Khả dụng của Redis | Redis lỗi thì mở cửa giới hạn số lần thử, chấp nhận mất bảo vệ trong lúc lỗi (`tech.md` T3) | KT-17 | Chỉ thiết kế |

**Điều kiện đo cần ghi cùng số:** máy, số lõi và bộ nhớ (tối thiểu vài lõi cho việc băm, vì ở trần thử hàng đợi CPU làm P99 tăng), hệ số băm,
phiên bản PostgreSQL và Redis, kích thước bảng phiên và `oauth2_authorization` lúc đo (đo cả bảng rỗng và cỡ hàng triệu dòng), địa chỉ mạng
của nguồn tải, giới hạn tần suất ở cổng và giới hạn số lần thử tắt hoặc nới trong phép đo tải và bật lại khi đo giới hạn. Khởi động nóng
trước khi đo, dùng email ngẫu nhiên, lặp 3 lần và ghi độ lệch giữa các lần.

**Liên hệ với feature khác:** trần băm mật khẩu của máy lấy từ feature 08 (bộ đo nền); độ trễ P99 đăng nhập ở trên cần số đó để biết phần chênh
do mã. Điểm sâu "đăng nhập dưới tải" mở rộng ở C5.

# Feature 01: đáp ứng NFR

> Nhãn mức chứng minh theo NFR mục 5: **Đã đo**, **Suy ra**, **Chỉ thiết kế**. Phép đo tải, độ trễ và 3 triệu tài khoản chưa
> chạy: các dòng đó còn là Chỉ thiết kế hoặc Suy ra. Trần của máy đã có từ bộ đo nền (feature 08, `2026-10-20`, xem mục cuối); phép đo ứng dụng
> vẫn là việc của feature này (`plan.md` bước 11).

| NFR áp dụng | Cách đáp ứng | Cách đo | Nhãn hiện tại |
|---|---|---|---|
| Đăng ký/giây đỉnh ~1 (NFR 3.3) | Mỗi yêu cầu băm một lần, đồng bộ; giới hạn tần suất; chỉ phụ thuộc cơ sở dữ liệu | Tải duy trì 1 yêu cầu/giây trong 5 phút; trần thử tăng dần tới điểm gãy (10 yêu cầu/giây là dư gấp 10 lần mục tiêu); đo độ trễ và tỉ lệ lỗi | Chỉ thiết kế |
| Độ trễ đăng ký (đề xuất, chưa có trong NFR gốc) | Độ trễ chủ yếu là một lần băm: mục tiêu P99 < 500 ms ở 1 yêu cầu/giây | Cùng phép đo trên; đối chiếu với trần băm của feature 08 (một lần băm hệ số 10 đo được P50 45–65 ms, P99 71–118 ms) | Chỉ thiết kế (Giả định) |
| Tài khoản tích lũy ~3 triệu (NFR 3.4) | Tra trùng bằng chỉ mục duy nhất trên email chuẩn hóa; không quét bảng | Sinh 3 triệu tài khoản (băm tính trước), đo độ trễ tra trùng và thêm | Chỉ thiết kế |
| Dung lượng chỉ mục email | ~60–80 byte mỗi mục × 3 triệu ≈ 200 MB | Đo kích thước chỉ mục sau khi sinh dữ liệu | Suy ra (kích thước mục là giả định) |
| Đúng khi đồng thời | Ràng buộc email duy nhất ở CSDL | 100 yêu cầu song song cùng email chỉ ra đúng một tài khoản; lặp 100 lần | **Đã đo** `2026-10-08`: 100 vòng × 100 yêu cầu, mỗi vòng đúng một 201 và 99 lần 409, không có 500 (`UserAccountConcurrencyTest`, Postgres 16 riêng cho test, cùng một máy chạy cả tải và ứng dụng) |
| Bảo mật dữ liệu | Mật khẩu chỉ ở dạng băm; không ghi mật khẩu, mã băm, token vào log (email được phép, xem `logging.md` mục 5) | Quét CSDL và log sau các test | Đo một phần: cột mật khẩu chỉ chứa chuỗi băm và phản hồi không chứa mật khẩu đã có test; quét log chưa làm |

**Điều kiện đo cần ghi cùng số:** máy, số lõi và bộ nhớ (tối thiểu 2 lõi cho việc băm, vì 1 yêu cầu/giây × 45–65 ms băm
chỉ dùng 5–7% một lõi nhưng ở trần thử hàng đợi CPU làm P99 tăng), hệ số băm, phiên bản PostgreSQL, kích thước bảng tài khoản
lúc đo (đo cả bảng rỗng và 3 triệu dòng), địa chỉ mạng của nguồn tải. Giới hạn tần suất phải được tắt hoặc nới trong phép
đo tải và bật lại khi đo giới hạn. Khởi động nóng trước khi đo, dùng email ngẫu nhiên không trùng, lặp 3 lần và ghi độ
lệch giữa các lần.

**Liên hệ với feature khác:** trần của máy lấy từ feature 08 (bộ đo nền, `2026-10-20`, `08-baseline-bench/tech.md` T5 đến T7); độ trễ P99 ở
trên cần các số đó để biết phần chênh do mã.

| Trần máy từ feature 08 (**Đã đo**) | Giá trị | Dùng cho feature này |
|---|---|---|
| Băm mật khẩu, hệ số 10 | khoảng 227 lần/giây (cả máy, 16 luồng); một lần băm P50 45–65 ms | Đăng ký ~1/giây chỉ dùng khoảng 0,5% trần; số giả định "~80 ms" cũ cao hơn thực đo |
| Ghi một dòng vào Postgres (cùng cột và chỉ mục như `user_accounts`) | khoảng 11.000 giao dịch/giây (Postgres 4 CPU, bảng nhỏ) | Phần ghi của đăng ký không phải điểm nghẽn; chưa đo ở 3 triệu dòng, việc đó thuộc phép đo ở trên |
| Điểm cuối rỗng | khoảng 65.000 yêu cầu/giây (khung 4 CPU trong container) | Đối chứng để tính phần chênh do mã khi đo `POST /register` |

Mọi số trên đo trong cùng điều kiện: container 4 CPU, gói điện Balanced có cắm sạc. Phép đo ứng dụng của feature này phải dùng điều kiện tương tự
thì phần chênh mới có nghĩa.

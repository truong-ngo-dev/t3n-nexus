# Feature 08: giải pháp kỹ thuật và ghi nhận từng bước

## T1. Cấu trúc `bench/` (bước 1)

`bench/` ở gốc kho mã, **không nằm trong build của `services/`** (parent riêng, `spring-boot-starter-parent` 4.0.6 như `services`). Build bằng wrapper của `services`:

```
cd bench
JAVA_HOME="C:\Program Files\Java\jdk-21"  ../services/mvnw.cmd -q -B package
```

| Phần | Vai trò |
|---|---|
| `bench/empty-endpoint` | Spring Boot tối thiểu (`spring-boot-starter-webmvc`), cổng 8099, `GET /ping` trả `ok`, log mức WARN |
| `bench/load-java` | Đoạn Java chạy trực tiếp (không Spring): băm bcrypt (bước 3) và tải JDBC (bước 5). Phụ thuộc `spring-security-crypto`, `commons-logging`, `postgresql` |
| `bench/k6` | Kịch bản k6, chạy bằng Docker |
| `bench/docker-compose.yml` | Postgres 16 riêng của bộ đo, cổng 5440, `cpus: 4`, `mem_limit: 2g`; `docker compose down -v` xóa sạch |

Chạy `load-java` ngoài Maven: `mvn dependency:copy-dependencies -DoutputDirectory=target/lib` rồi `java -cp "target/classes;target/lib/*" <lớp>`.

## T2. Kết quả kiểm công cụ (bước 1, `2026-10-20`)

| Điều cần kiểm | Kết luận |
|---|---|
| Docker chạy, chạy được k6 | Đúng. Docker Desktop 27.3.1, 16 CPU, 16 GB RAM; `grafana/k6` là k6 v2.3.0 |
| k6 trong Docker gọi được server trên máy chủ | Đúng, qua `host.docker.internal`. Thử với điểm cuối rỗng: khoảng 5.659 yêu cầu/giây ở 10 kết nối, P95 3,5 ms, không lỗi. Đây chỉ là khói thử, **không phải số đo** (k6 giới hạn 2 CPU, chưa làm nóng) |
| Docker ghim được CPU | Đúng cho **hạn mức** (`--cpus=2` đọc ra `cpu.max 200000 100000`) và **tập nhân** (`--cpuset-cpus=0-3` đọc ra `0-3`). Lưu ý: Docker Desktop chạy trong máy ảo WSL2, nhân "0-3" là nhân của máy ảo chứ không phải nhân vật lý; cô lập là theo hạn mức, không phải gán cứng nhân |
| Container Postgres ghi nhận giới hạn | Đúng: `NanoCpus` 4 tỷ, bộ nhớ 2 GB. `fsync` và `synchronous_commit` bật; `shared_buffers` 128 MB (mặc định) |
| Maven build được mô-đun bench | Đúng (JDK 21). `spring-jcl` không còn ở Spring 7: `BCryptPasswordEncoder` cần `commons-logging` khai báo thẳng, nếu thiếu thì `NoClassDefFoundError: LogFactory` |
| Chạy được bcrypt như `oauth2-service` | Đúng: `BCryptPasswordEncoder(10)` một lần băm đầu tiên (chưa làm nóng JIT) mất 74 ms. Không phải số đo |

## T3. Hệ quả cho các bước sau

- **Khung bị đo nên chạy trong container** (ảnh `eclipse-temurin:21-jre`, gắn jar vào, `--cpus` rõ ràng), để mọi thành phần (khung, k6, Postgres) cùng có hạn mức theo cgroup. Chạy khung trực tiếp trên Windows thì không giới hạn được nhân như vậy và còn tranh nhân với k6. Việc này đổi cơ sở so sánh với dịch vụ thật chạy trực tiếp trên máy; ghi ở điều kiện đo. Chốt ở bước 2, kiểm ở bước 4.
- **Dịch vụ thật không bật luồng ảo** (không có `spring.threads.virtual.enabled` trong `application.properties` nào). Điểm cuối rỗng đo cấu hình mặc định (luồng nền tảng của Tomcat) làm cơ sở; luồng ảo là một biến thể có thể đo thêm, theo quy tắc "một biến đổi một lần".
- Bộ sinh tải k6 chưa chắc đạt giới hạn của khung ở 2 CPU (khói thử 5.659 yêu cầu/giây chưa chạm trần); bước 4 phải tăng nhân của k6 hoặc ghi rằng trần đo là cận dưới.
- `bench/` không được thêm vào `services/pom.xml`; thêm vào thì `mvn package` của dịch vụ build luôn bộ đo.

## T4. Máy đo và quy ước đo (bước 2)

### T4.1. Cấu hình máy (ghi cạnh mọi số đo; đổi máy thì lập lại bảng)

| Hạng mục | Giá trị |
|---|---|
| CPU | AMD Ryzen AI 7 H 450, 8 nhân vật lý / 16 luồng, xung tối đa khai báo 2,0 GHz (có tăng tốc) |
| RAM | 31,2 GB |
| Loại máy | **Máy xách tay** (có pin), gói điện hiện tại **Balanced** |
| Hệ điều hành | Windows 11 Home 10.0.26200 |
| Docker | Docker Desktop 27.3.1 (WSL2), máy ảo thấy 16 CPU, 16 GB RAM |
| JDK | 21.0.4 (Oracle) trên máy chủ; ảnh `eclipse-temurin:21-jre` (21.0.12) cho khung trong container |
| Postgres | 16.15 (`postgres:16-alpine`) |
| k6 | v2.3.0 (`grafana/k6`) |

Máy xách tay nên tốc độ có thể đổi theo nguồn điện, nhiệt và gói điện. **Điều kiện phép đo:** cắm sạc, đặt gói điện **High performance** (hoặc Best performance) nếu có, đóng IDE, trình duyệt và các tiến trình nặng. Mỗi lượt đo ghi gói điện và việc đã cắm sạc. Gói điện không đặt được thì ghi rõ và báo số kèm biên độ lớn hơn.

### T4.2. Chia nhân

Docker chia theo **hạn mức** (`--cpus`), không gán cứng nhân vật lý (T2). Tổng hạn mức chạy cùng lúc không vượt 12 trên 16 luồng, chừa 4 luồng cho hệ điều hành và Docker.

| Phép đo | Thành phần | Hạn mức | Chạy ở đâu |
|---|---|---|---|
| Điểm cuối rỗng | Khung (`empty-endpoint`) | 4 CPU | Container `eclipse-temurin:21-jre`, `-Xms512m -Xmx512m` |
| | Bộ sinh tải k6 | 4 CPU | Container `grafana/k6` |
| Ghi một dòng | Postgres | 4 CPU, 2 GB | Container (`bench/docker-compose.yml`) |
| | Tải JDBC (`load-java`) | không giới hạn | Máy chủ; phần lớn thời gian chờ Postgres, dùng ít CPU |
| Băm mật khẩu | JVM (`load-java`) | cả máy, không chạy gì khác | Máy chủ; không Docker, không mạng |

Khung 4 CPU giữ cho trần có ý nghĩa khi đem so với dịch vụ thật (về sau đo dịch vụ thật với cùng 4 CPU, theo NFR 5.2). Nếu k6 4 CPU không bắn đủ tải để khung đạt trần (CPU của k6 chạm 100% trước khung), tăng k6 lên 6 và giảm khung theo cho tổng không đổi, hoặc ghi trần là cận dưới (`flow.md` mục 5).

### T4.3. Thời lượng, lặp và làm nóng

| Phép đo | Làm nóng (bỏ qua) | Đo mỗi bậc | Lặp |
|---|---|---|---|
| Băm | 5 s mỗi cấu hình (JIT) | 20 s | 3 lần, báo giá trị giữa và khoảng min–max |
| Điểm cuối rỗng | 30 s ở tải vừa trước chuỗi bậc | 30 s mỗi bậc | 3 lần cả chuỗi bậc, khởi động lại khung giữa các lần |
| Ghi một dòng | 10 s | 30 s mỗi số kết nối | 3 lần, tạo lại bảng và khởi động lại Postgres giữa các lần |

Chuỗi bậc điểm cuối rỗng: 10, 50, 100, 200, 400 kết nối; ghi một dòng: 1, 4, 16, 64 kết nối; băm: 1, 2, 4, 8, 16 luồng, hệ số 10, 11, 12. Thêm bậc ở khoảng đường cong gãy khi cần.

### T4.4. Ngưỡng xác định trần

Trần là **bậc cao nhất mà P99 còn dưới ngưỡng và không có lỗi**. Ngưỡng là số tạm, chỉnh một lần sau lượt thử đầu khi thấy đường cong thật rồi **khóa**, không đổi sau khi đã thấy kết quả của từng bậc.

| Phép đo | Ngưỡng P99 | Lý do |
|---|---|---|
| Điểm cuối rỗng | 50 ms | Công việc rỗng, độ trễ lẽ ra cỡ vài ms; P99 vượt 50 ms nghĩa là hàng đợi đã dài |
| Ghi một dòng (một giao dịch) | 100 ms | Ghi kèm `fsync`; cho phép một vài lần chờ ghi đĩa |
| Băm | Không dùng ngưỡng | Phép đo CPU thuần; trần là số luồng mà băm/giây thôi tăng (bão hòa), báo kèm thời gian một lần băm |

Với băm, độ trễ một lần là thông số thiết kế (hệ số 10 cho khoảng một phần mười giây). Nó quyết định hệ số, còn trần quyết định bao nhiêu đăng nhập đồng thời; hai thứ báo riêng.

### T4.5. Mẫu bảng kết quả

Mỗi phép đo một bảng, cột cố định, để so lượt này với lượt khác:

| Bậc (kết nối hoặc luồng) | Thông lượng (lần 1/2/3) | Giữa | P50 | P95 | P99 | Lỗi | CPU thành phần bị đo |
|---|---|---|---|---|---|---|---|

Dưới bảng ghi: ngày, gói điện và sạc, hạn mức từng thành phần, tham số lạ (hệ số bcrypt, `-Xmx`), trần kết luận và **nhãn mức chứng minh** (NFR 5.1). Số suy ra từ trần ghi nhãn **Suy ra** kèm mô hình.

### T4.6. Chốt quyết định bước 2

- Khung chạy trong container (T3 đề xuất, nay chốt). Số đo là của khung trong container 4 CPU.
- Hai ngưỡng P99 (50 ms, 100 ms) là số tạm, khóa sau lượt thử đầu.
- Chạy `load-java` ở máy chủ cho hai phép băm và ghi (băm cần cả máy; ghi chủ yếu chờ).

## T5. Kết quả đo băm mật khẩu (bước 3, `2026-10-20`)

**Điều kiện:** `BcryptPasswordEncoder` (`spring-security-crypto` 7.0.5, cùng thư viện với `oauth2-service`), `encode` một mật khẩu ASCII 11 ký tự, JDK 21.0.4 (Oracle) trên máy chủ, 16 luồng logic. Làm nóng 5 s, đo 20 s, 3 lần mỗi cấu hình. **Gói điện Balanced, có cắm sạc** (không đặt High performance; T4.1 đặt điều kiện này nhưng việc đổi gói điện của máy chưa được người cho phép, nên đo ở gói hiện có). Chưa kiểm tiến trình nền khác lúc đo. Dữ liệu thô: `bench/results/bcrypt-2026-10-20.txt` (không đưa vào git). Chạy lại: `BcryptBench` (`bench/load-java`).

**Hệ số 10 (hệ số đang dùng, ADR-0002)** — giá trị giữa của ba lần (khoảng min–max của băm/giây); P50, P99 là một lần băm (ms):

| Luồng | Băm/giây (giữa) | min–max | P50 | P99 |
|---|---|---|---|---|
| 1 | 19,9 | 18,4–21,9 | 45,1 | 79,0 |
| 2 | 30,6 | 30,6–33,1 | 65,1 | 73,2 |
| 4 | 62,7 | 60,3–71,6 | 66,0 | 71,4 |
| 8 | 133,8 | 128,3–137,8 | 59,5 | 72,8 |
| 16 | **227,1** | 222,8–230,3 | 65,1 | 117,9 |

**Hệ số 11:**

| Luồng | Băm/giây (giữa) | min–max | P50 | P99 |
|---|---|---|---|---|
| 1 | 8,0 | 8,0–9,1 | 123,9 | 130,6 |
| 2 | 16,6 | 15,6–20,0 | 127,1 | 136,6 |
| 4 | 37,7 | 35,8–39,1 | 101,9 | 144,2 |
| 8 | 68,9 | 68,7–70,0 | 113,4 | 148,6 |
| 16 | 113,3 | 107,9–114,8 | 128,7 | 223,9 |

**Hệ số 12:**

| Luồng | Băm/giây (giữa) | min–max | P50 | P99 |
|---|---|---|---|---|
| 1 | 5,0 | 4,4–5,6 | 183,9 | 268,5 |
| 2 | 9,2 | 9,0–9,9 | 194,6 | 277,9 |
| 4 | 15,1 | 15,0–15,2 | 265,6 | 279,1 |
| 8 | 30,4 | 30,2–31,3 | 261,9 | 282,2 |
| 16 | 58,6 | 57,3–59,7 | 247,8 | 395,8 |

**Nhãn:** Đã đo, trong điều kiện trên.

### Đọc kết quả

- **Trần băm của máy ở hệ số 10: khoảng 227 lần/giây** (16 luồng, bằng số luồng logic). Thông lượng còn tăng từ 8 lên 16 luồng (133,8 lên 227,1, gấp 1,7 lần), tức nhân logic (SMT) vẫn đóng góp; **chưa thử trên 16 luồng**, nên 227 là mức cao nhất đã đo chứ chưa kiểm là điểm bão hòa. Với việc CPU thuần, vượt số luồng logic thường không tăng thêm.
- **Gần tuyến tính theo nhân vật lý:** 1 luồng 19,9; 8 luồng 133,8 (gấp 6,7 lần trên 8 nhân vật lý). Hệ số 11 và 12 giảm đúng một nửa rồi một phần tư (113,3 và 58,6 so với 227,1).
- **Thời gian một lần băm ở hệ số 10: P50 45 đến 65 ms** (45 ms khi một luồng, khoảng 60 đến 65 ms khi nhiều luồng), P99 từ 71 đến 118 ms. **Giả định "khoảng 80 ms" của `01-register/nfr.md` cao hơn thực đo** (1,2 đến 1,8 lần) trên máy này. P50 tăng từ 45 lên 65 ms khi thêm luồng, giải thích bằng xung nhịp giảm khi nhiều nhân cùng tải (chưa kiểm, không có số đo xung nhịp).
- **Độ biến động:** các lần của 8 và 16 luồng nằm trong ±3%; cấu hình 1 và 4 luồng hệ số 10 dao động tới ±10 đến 15% (1 luồng: 18,4 đến 21,9; 4 luồng: 60,3 đến 71,6), có thể do xung nhịp tăng tốc không ổn định ở gói Balanced. Số của 1 và 4 luồng chỉ dùng để xem hình dạng đường cong, không dùng làm trần.

### Suy ra (nhãn **Suy ra**)

Mô hình: băm thuần CPU, thông lượng tỷ lệ với số luồng logic ở mức bão hòa; mỗi luồng logic cho khoảng 227,1 / 16 = 14,2 lần băm/giây ở hệ số 10.

| Mục tiêu | Số cần | Ghi chú |
|---|---|---|
| 700 đăng nhập/giây (NFR 3.3), hệ số 10 | khoảng 49 luồng logic, tức khoảng 25 nhân vật lý | Thay cho "khoảng 56 lõi" ở `02-login-session/nfr.md` (tính từ 80 ms giả định). Vẫn vượt xa một máy: đăng nhập flash sale không đạt trên máy này, đúng như `nfr.md` đã nói |
| Cùng mục tiêu, hệ số 12 | khoảng 4 lần số trên (khoảng 195 luồng logic) | Mỗi bước hệ số tăng gấp đôi chi phí |

Những con số này giả định đăng nhập chỉ tốn một lần băm; thực tế còn việc khác (giới hạn thử, phiên, cơ sở dữ liệu) nên số lõi thật lớn hơn.

## T6. Kết quả đo điểm cuối rỗng (bước 4, `2026-10-20`)

**Điều kiện:** `GET /ping` của `bench/empty-endpoint` (Spring Boot 4.0.6, Tomcat mặc định, luồng nền tảng, không bảo mật, log WARN), trong container `eclipse-temurin:21-jre` (21.0.12), `--cpus=4`, `-Xms512m -Xmx512m`. k6 v2.3.0 trong container `--cpus=8`, cùng mạng Docker với khung (gọi thẳng tên container, không qua cổng của máy chủ), không nghỉ giữa các yêu cầu, bỏ thân phản hồi. Làm nóng 30 s ở 50 kết nối, đo 30 s mỗi bậc, ba lần cả chuỗi bậc, **khởi động lại khung giữa các lần**. Gói điện Balanced, có cắm sạc; chưa kiểm tiến trình nền khác. Script: `bench/run-empty.sh`, `bench/k6/ping.js`, `bench/summarize.js`; dữ liệu thô `bench/results/empty-2026-10-20.txt` (không đưa vào git).

Giá trị giữa của ba lần; thông lượng kèm khoảng min–max; độ trễ là của yêu cầu (ms); không có lỗi ở bậc nào (0,00%).

| Kết nối | Yêu cầu/giây (giữa) | min–max | P50 | P95 | P99 |
|---|---|---|---|---|---|
| 10 | 23.479 | 23.149–23.672 | 0,32 | 0,62 | 1,11 |
| 50 | 59.837 | 59.073–64.467 | 0,59 | 1,95 | 3,48 |
| 100 | **65.406** | 63.058–66.426 | 1,12 | 3,74 | 6,38 |
| 200 | 58.772 | 55.346–61.813 | 2,19 | 8,20 | 14,79 |
| 400 | 58.672 | 58.513–59.432 | 3,50 | 12,20 | 20,86 |
| 800 | 56.440 | 56.176–58.395 | 7,61 | 20,06 | 33,35 |
| 1.600 | 51.275 | 49.606–52.762 | 18,45 | 50,37 | 70,37 |

**Nhãn:** Đã đo, trong điều kiện trên.

### Đọc kết quả

- **Trần đọc của khung trên 4 CPU: khoảng 65.000 yêu cầu/giây** ở 100 kết nối (P99 6,4 ms), tức khoảng 16.000 yêu cầu/giây mỗi CPU. Khung chạm hạn mức CPU từ 100 kết nối (đọc `docker stats` khoảng 340 đến 410% trên 400%).
- **Quá 100 kết nối, thông lượng giảm chứ không tăng**: 65,4 nghìn xuống 58,7 nghìn (200 và 400), 56,4 nghìn (800), 51,3 nghìn (1.600), còn P99 tăng từ 6 lên 70 ms. Thêm kết nối chỉ thêm hàng đợi và chuyển ngữ cảnh, không thêm việc xong.
- **Theo ngưỡng P99 dưới 50 ms (T4.4, đã khóa):** bậc cao nhất đạt là **800 kết nối** (P99 33,4 ms, 56.440 yêu cầu/giây); 1.600 kết nối vượt ngưỡng (P99 70 ms). Quy tắc T4.4 chỉ nói "bậc cao nhất còn dưới ngưỡng", nhưng thông lượng ở bậc đó thấp hơn đỉnh vì đã quá tải. **Làm rõ quy tắc (không đổi ngưỡng):** trần ghi vào NFR là **thông lượng cao nhất trong các bậc có P99 dưới ngưỡng** (65,4 nghìn ở 100 kết nối), kèm điểm gãy độ trễ (từ khoảng 800 kết nối).
- **Độ biến động:** thông lượng giữa các lần lệch tới 8 đến 10% ở 50 và 200 kết nối, còn dưới 5% ở 100 và 400. Đỉnh 100 kết nối ổn định (63,1 đến 66,4 nghìn).

### Hạn chế (đọc trước khi dùng số)

- **k6 gần bão hòa.** Với 8 CPU, k6 báo 720 đến 840% (trên 800%) từ 100 kết nối trở lên (từ 200 là trên 770%). Độ trễ ở các bậc cao có phần do hàng đợi trong k6, và thông lượng của khung có thể cao hơn số đo. **Trần 65.000 là cận dưới nhẹ**, không phải con số chính xác. Muốn tách hẳn phải chạy bộ sinh tải ở chỗ khác, ngoài phạm vi máy một nhân đang dùng.
- **Chia nhân lệch kế hoạch T4.2.** T4.2 dự kiến k6 4 CPU, và nếu k6 nghẽn thì tăng k6, giảm khung. Thực tế k6 4 CPU nghẽn rõ (391 trên 400% khi khung mới 320%), nên **giữ khung 4 CPU** (làm cơ sở so sánh với dịch vụ thật) và **tăng k6 lên 8 CPU**: tổng 12, vẫn trong ngân sách. Quyết định này đổi thứ tự ưu tiên so với T4.2 (giữ khung cố định thay cho giữ tổng nhân của hai bên).
- **Một khung trong container** (WSL2), không phải khung chạy trực tiếp trên Windows (T3). So với dịch vụ thật phải đo dịch vụ thật trong cùng điều kiện container 4 CPU.
- `docker stats` là một ảnh chụp lúc giữa lượt, không phải trung bình cả lượt; dùng để biết ai gần bão hòa, không dùng làm số đo.
- Chưa đo biến thể luồng ảo (`plan.md` bước 4 để như biến thể đo thêm); chưa đo khi bật log yêu cầu (việc của bước đo overhead ở feature 07).

## T7. Kết quả đo ghi một dòng vào Postgres (bước 5, `2026-10-20`)

**Điều kiện:** Postgres 16.15 (`postgres:16-alpine`) trong container `--cpus=4`, `mem_limit=2g`, cấu hình mặc định (`shared_buffers` 128 MB, `fsync` và `synchronous_commit` bật, `max_connections` 100), khởi động lại trước mỗi lần chạy. Bảng `bench_user` có cùng cột, ràng buộc và chỉ mục như `user_accounts` (khóa UUID v7 theo ADR-0001, email duy nhất, 5 ràng buộc kiểm), tạo lại trước mỗi mức kết nối. Tải: `bench/load-java` (`JdbcWriteBench`) trên máy chủ, mỗi luồng một kết nối JDBC, tự xác nhận, **mỗi INSERT là một giao dịch** có `fsync`. Làm nóng 10 s, đo 30 s, ba lần. Gói điện Balanced, có cắm sạc; chưa kiểm tiến trình nền khác. Script: `bench/run-write.sh`; dữ liệu thô `bench/results/write-2026-10-20.txt` (không đưa vào git).

Giá trị giữa của ba lần; độ trễ là của một giao dịch (ms); không có lỗi ở mức nào.

| Kết nối | Dòng/giây (giữa) | min–max | P50 | P95 | P99 | CPU Postgres (ảnh chụp) |
|---|---|---|---|---|---|---|
| 1 | 838 | 830–841 | 1,14 | 1,49 | 1,84 | khoảng 15% |
| 4 | 2.415 | 2.415–2.426 | 1,61 | 1,94 | 2,21 | khoảng 55 đến 62% |
| 16 | 7.227 | 7.039–7.272 | 2,15 | 2,83 | 3,48 | khoảng 183 đến 191% |
| 64 | **10.936** | 10.833–11.047 | 4,30 | 23,93 | 33,02 | khoảng 403 đến 414% |

**Nhãn:** Đã đo, trong điều kiện trên.

### Đọc kết quả

- **Trần ghi của Postgres trên 4 CPU: khoảng 11.000 giao dịch ghi một dòng mỗi giây** ở 64 kết nối (P99 33 ms, dưới ngưỡng 100 ms của T4.4). Đây cũng là mức cao nhất trong các mức đã đo, nên thỏa quy tắc "thông lượng cao nhất trong các bậc dưới ngưỡng".
- **Một giao dịch tuần tự tốn khoảng 1,1 ms** (một kết nối: 838 giao dịch/giây). Đó chủ yếu là thời gian chờ ghi nhật ký (`fsync`) trên đĩa ảo của Docker Desktop. Thêm kết nối thì các giao dịch được gộp vào cùng một lần ghi đĩa (nhóm xác nhận), nên thông lượng tăng nhanh: 1 lên 16 kết nối tăng 8,6 lần.
- **Từ 16 đến 64 kết nối nghẽn ở CPU của Postgres**, không còn ở đĩa: CPU Postgres chạm hạn mức 4 CPU (khoảng 400%), P95 tăng vọt từ 2,8 lên 24 ms trong khi thông lượng chỉ tăng 1,5 lần. Muốn ghi nhanh hơn trên máy này cần thêm CPU cho Postgres, không phải đĩa nhanh hơn.
- **Độ biến động thấp:** các lần chạy lệch dưới 3% ở mọi mức.

### Hạn chế

- **Chỉ tới 64 kết nối.** `max_connections` mặc định 100 và nhóm kết nối thật của dịch vụ nhỏ hơn nhiều; chưa đo trên 64. CPU Postgres đã chạm hạn mức nên khó tăng thêm.
- **Bảng nhỏ và nằm trọn trong bộ nhớ đệm.** Mỗi mức chỉ ghi chừng 330 nghìn dòng; chưa đo ở quy mô 3 triệu tài khoản (NFR 4.3), khi chỉ mục lớn hơn bộ nhớ đệm có thể chậm hơn. Ghi chú này giữ lại cho phép đo đăng ký của feature 01.
- **`fsync` trên đĩa ảo của Docker Desktop (WSL2)**, không phải đĩa vật lý của máy trực tiếp. Số 1,1 ms mỗi giao dịch phụ thuộc đĩa này.
- **Cấu hình Postgres mặc định**, chưa chỉnh (`shared_buffers`, `max_wal_size`, `wal_buffers`...). Trần có thể cao hơn khi chỉnh, và dịch vụ thật dùng cấu hình mặc định giống vậy nên số này vẫn dùng để so.
- **Tải JDBC chạy trên máy chủ, không giới hạn CPU**, đi qua cổng chuyển tiếp của Docker Desktop. Độ trễ có thêm phần chuyển tiếp (dưới 1 ms theo ước tính, chưa đo riêng). Postgres đã chạm 4 CPU nên trần là của Postgres chứ không phải của bộ sinh tải.
- **Một giao dịch một dòng vào một bảng.** Luồng đặt hàng hay đăng ký thật ghi nhiều dòng, nhiều bảng và có đọc, nên số giao dịch thật mỗi giây thấp hơn.

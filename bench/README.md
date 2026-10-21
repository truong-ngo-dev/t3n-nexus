# bench: bộ đo nền (feature 08)

Đo trần của máy cho ba việc: băm mật khẩu, điểm cuối rỗng, ghi một dòng vào Postgres. Giải thích, điều kiện đo, kết quả và hạn chế:
`docs/stage/c0-foundation/08-baseline-bench/tech.md` (T4 quy ước, T5 băm, T6 điểm cuối rỗng, T7 ghi). Mục NFR: `6-non-function-requirement.md` mục 4.1.

Không thuộc build của `services/`. Cần Docker, JDK 21, Node (cho `summarize.js`). Chạy trên máy cắm sạc, gói điện High performance nếu đặt được,
đóng IDE và trình duyệt. Chạy các lệnh dưới đây từ thư mục `bench/` bằng Git Bash.

```
export JAVA_HOME="C:/Program Files/Java/jdk-21"
../services/mvnw.cmd -q -B package
../services/mvnw.cmd -q -B -pl load-java dependency:copy-dependencies -DoutputDirectory=target/lib
```

| Phép đo | Lệnh | Thời gian | Kết quả |
|---|---|---|---|
| Băm bcrypt (hệ số 10, 11, 12; 1 đến 16 luồng) | `java -cp "load-java/target/classes;load-java/target/lib/*" vn.t3nexus.bench.load.BcryptBench > results/bcrypt-$(date +%F).txt` | khoảng 19 phút | `results/bcrypt-<ngày>.txt` |
| Điểm cuối rỗng (khung 4 CPU, k6 8 CPU) | `LEVELS="10 50 100 200 400 800 1600" ./run-empty.sh 3 4 8` | khoảng 14 phút | `results/empty-<ngày>.txt` |
| Ghi một dòng (Postgres 4 CPU) | `./run-write.sh 3` | khoảng 10 phút | `results/write-<ngày>.txt` |

`run-write.sh` tự dựng Postgres riêng (`docker-compose.yml`, cổng 5440) và không đụng cơ sở dữ liệu dev. Xong thì dọn:
`docker compose down -v` (Postgres của bộ đo) và `docker network rm bench-net` (mạng của phép đo điểm cuối rỗng, nếu còn).

Ảnh Docker dùng: `grafana/k6`, `eclipse-temurin:21-jre`, `curlimages/curl`, `postgres:16-alpine`. Thư mục `results/` không đưa vào git; số liệu đã chốt nằm ở tài liệu.

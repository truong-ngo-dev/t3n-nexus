#!/usr/bin/env bash
# Phép đo ghi một dòng (feature 08, bước 5). Dùng: ./run-write.sh [số lần chạy]. Postgres riêng: bench/docker-compose.yml.
# Mỗi lần chạy: khởi động lại Postgres, rồi lần lượt từng số kết nối (bảng tạo lại, làm nóng, đo). Kết quả: results/write-<ngày>.txt
set -u
REPEATS=${1:-3}; CONNS=${CONNS:-"1 4 16 64"}; WARM=${WARM:-10}; MEASURE=${MEASURE:-30}
J="${JAVA_HOME:-C:/Program Files/Java/jdk-21}/bin/java"
OUT=results/write-$(date +%F).txt; mkdir -p results
docker compose up -d >/dev/null 2>&1
echo "# postgres=$(docker compose exec -T postgres-bench postgres --version | tr -d '\r') warm=${WARM}s measure=${MEASURE}s $(date -Is)" > "$OUT"
echo "run conns rows_per_s p50_ms p95_ms p99_ms errors rows_total pg_cpu_pct" >> "$OUT"
ready() { for i in $(seq 1 60); do docker compose exec -T postgres-bench pg_isready -U bench -d bench_db >/dev/null 2>&1 && return 0; sleep 1; done; return 1; }
for run in $(seq 1 "$REPEATS"); do
  docker compose restart postgres-bench >/dev/null 2>&1; ready || { echo "postgres khong len" >&2; exit 1; }
  for c in $CONNS; do
    ( sleep $((WARM + MEASURE / 2)); docker stats --no-stream --format '{{.CPUPerc}}' bench-postgres-bench-1 > results/stats-pg.txt ) &
    line=$("$J" -cp "load-java/target/classes;load-java/target/lib/*" vn.t3nexus.bench.load.JdbcWriteBench conns="$c" warmup="$WARM" measure="$MEASURE")
    wait
    echo "$run $line $(tr -d '\r\n' < results/stats-pg.txt)" >> "$OUT"
  done
done
echo "xong $OUT"

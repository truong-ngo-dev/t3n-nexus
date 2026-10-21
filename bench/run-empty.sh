#!/usr/bin/env bash
# Phép đo điểm cuối rỗng (feature 08, bước 4). Dùng: ./run-empty.sh [số lần chạy chuỗi bậc] [CPU khung] [CPU k6]
# Khung và k6 chạy trong container cùng một mạng Docker (không qua cổng của máy chủ). Kết quả: results/empty-<ngày>.txt
set -u
REPEATS=${1:-3}; FW_CPUS=${2:-4}; K6_CPUS=${3:-4}
LEVELS=${LEVELS:-"10 50 100 200 400"}
WARM=${WARM:-30s}; DUR=${DUR:-30s}
export MSYS_NO_PATHCONV=1
HERE=$(pwd -W); OUT=results/empty-$(date +%F).txt; mkdir -p results
JAR=empty-endpoint/target/empty-endpoint-1.0.0.jar
docker network create bench-net >/dev/null 2>&1

start_fw() {
  docker rm -f bench-empty >/dev/null 2>&1
  docker run -d --name bench-empty --network bench-net --cpus="$FW_CPUS" \
    -v "$HERE/empty-endpoint/target:/app:ro" eclipse-temurin:21-jre \
    java -Xms512m -Xmx512m -jar /app/empty-endpoint-1.0.0.jar >/dev/null
  for i in $(seq 1 60); do
    docker run --rm --network bench-net curlimages/curl -fs -m2 http://bench-empty:8099/ping >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "khung khong len" >&2; return 1
}

k6run() { # vus dur -> in dòng tóm tắt
  docker run --rm --name bench-k6 --network bench-net --cpus="$K6_CPUS" -e VUS="$1" -e DUR="$2" \
    -v "$HERE/k6:/scripts:ro" -v "$HERE/results:/out" grafana/k6 run --quiet \
    --summary-export=/out/k6-last.json /scripts/ping.js >/dev/null 2>&1
}

echo "# khung=${FW_CPUS}cpu k6=${K6_CPUS}cpu warm=$WARM dur=$DUR $(date -Is)" > "$OUT"
echo "run vus req_per_s p50_ms p95_ms p99_ms fail_pct fw_cpu_pct k6_cpu_pct" >> "$OUT"
for run in $(seq 1 "$REPEATS"); do
  start_fw || exit 1
  k6run 50 "$WARM"                      # làm nóng, bỏ kết quả
  for v in $LEVELS; do
    ( sleep 15; docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' bench-empty bench-k6 > results/stats.txt ) &
    k6run "$v" "$DUR"
    wait
    node summarize.js "$run" "$v" >> "$OUT"
  done
  docker rm -f bench-empty >/dev/null 2>&1
done
echo "xong $OUT"

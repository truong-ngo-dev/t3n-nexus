#!/usr/bin/env bash
# Dừng chuỗi dịch vụ do up.sh dựng. Dùng: scenarios/down.sh [--only gateway]
# Chỉ dừng tiến trình đã ghi mã trong scenarios/.run/<tên>.pid (không dừng mọi java hay node trên máy).
set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RUN="$ROOT/scenarios/.run"
ONLY=all
if [ "${1:-}" = "--only" ]; then ONLY=${2:-}; fi
case "$ONLY" in all|gateway) ;; *) echo "--only phải là all hoặc gateway" >&2; exit 2 ;; esac

NAMES=(api-gateway frontend storefront-web-bff oauth2-service)
[ "$ONLY" = gateway ] && NAMES=(api-gateway)

for n in "${NAMES[@]}"; do
  f="$RUN/$n.pid"
  [ -f "$f" ] || continue
  pid=$(tr -d '\r\n' < "$f")
  if [ -n "$pid" ] && tasklist //FI "PID eq $pid" 2>/dev/null | grep -q "$pid"; then
    taskkill //PID "$pid" //T //F >/dev/null 2>&1 && echo "  Đã dừng $n (tiến trình $pid)"
  else
    echo "  $n không còn chạy (tiến trình $pid)"
  fi
  rm -f "$f"
done

# Chờ cổng được giải phóng
for p in 8000 8004 8090 4201; do
  [ "$ONLY" = gateway ] && [ "$p" != 8000 ] && continue
  for i in $(seq 1 20); do
    netstat -ano | tr -d '\r' | awk -v p=":$p\$" '$1=="TCP" && $2 ~ p && $4=="LISTENING" {f=1} END {exit !f}' || break
    sleep 1
  done
done
echo "Đã dừng."

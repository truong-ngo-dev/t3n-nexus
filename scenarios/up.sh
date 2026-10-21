#!/usr/bin/env bash
# Dựng chuỗi dịch vụ cho feature 09 (kiểm đầu-cuối cả chặng): Xác thực (8004), BFF (8090), cổng (8000), giao diện (4201).
# Dùng: scenarios/up.sh [--rules relaxed|default] [--only all|gateway] [--no-build]
#   --rules relaxed  (mặc định) cổng chạy với luật đăng ký nâng lên 100000/giờ, như bộ thật của feature 05 cần.
#   --rules default  cổng chạy với luật mặc định (10/giờ), dùng cho kiểm 429 (pha B).
#   --only gateway   chỉ khởi động lại cổng (các dịch vụ khác đã chạy; dùng sau `down.sh --only gateway`).
#   --no-build       không đóng gói lại jar (mặc định đóng gói lại ba dịch vụ để kiểm đúng mã hiện tại).
# Yêu cầu: Docker có sẵn infra-postgres-oauth2-1, infra-redis-1, infra-kafka-1; JDK 21; Node. Chạy bằng Git Bash.
# Mỗi nhóm khởi động có `>/dev/null 2>&1` ở ngoài để script không giữ ống dẫn của người gọi (`up.sh | tail` mới không treo).
# Tiến trình ghi mã vào scenarios/.run/<tên>.pid để down.sh dừng đúng chúng; đầu ra ở scenarios/.run/<tên>.out.
set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RUN="$ROOT/scenarios/.run"; mkdir -p "$RUN"
RULES=relaxed; ONLY=all; BUILD=1
while [ $# -gt 0 ]; do
  case "$1" in
    --rules) RULES=${2:-}; shift ;;
    --only) ONLY=${2:-}; shift ;;
    --no-build) BUILD=0 ;;
    *) echo "tham số lạ: $1" >&2; exit 2 ;;
  esac
  shift
done
case "$RULES" in relaxed|default) ;; *) echo "--rules phải là relaxed hoặc default" >&2; exit 2 ;; esac
case "$ONLY" in all|gateway) ;; *) echo "--only phải là all hoặc gateway" >&2; exit 2 ;; esac

export JAVA_HOME="C:/Program Files/Java/jdk-21"
JAVA="$JAVA_HOME/bin/java"
[ -x "$JAVA" ] || { echo "Không thấy JDK 21 tại $JAVA_HOME (mặc định của máy là 1.8)" >&2; exit 1; }

port_pid() { netstat -ano | tr -d '\r' | awk -v p=":$1\$" '$1=="TCP" && $2 ~ p && $4=="LISTENING" {print $5; exit}'; }

PORTS=(8000)
[ "$ONLY" = all ] && PORTS=(8000 8004 8090 4201 8002)
for p in "${PORTS[@]}"; do
  pid=$(port_pid "$p")
  [ -z "$pid" ] || { echo "Cổng $p đang bận (tiến trình $pid). Chạy scenarios/down.sh hoặc giải phóng cổng." >&2; exit 1; }
done

running=$(docker ps --format '{{.Names}}')
for c in infra-postgres-oauth2-1 infra-redis-1 infra-kafka-1; do
  echo "$running" | grep -qx "$c" || { echo "Container $c chưa chạy. Dựng hạ tầng trước (infra/docker-compose.yml)." >&2; exit 1; }
done

# Xóa mức giới hạn tần suất còn sót của lần trước (chỉ khóa tiền tố ratelimit:, không đụng khóa khác)
stale=$(docker exec infra-redis-1 redis-cli --scan --pattern 'ratelimit:*' | tr -d '\r')
if [ -n "$stale" ]; then
  echo "$stale" | xargs docker exec infra-redis-1 redis-cli DEL >/dev/null
  echo "Đã xóa $(echo "$stale" | wc -l) khóa giới hạn tần suất còn sót."
fi

if [ "$ONLY" = all ] && [ "$BUILD" = 1 ]; then
  echo "Đóng gói oauth2-service, storefront-web-bff, api-gateway ..."
  ( cd "$ROOT/services" && ./mvnw.cmd -q -B -DskipTests -pl oauth2-service,storefront-web-bff,api-gateway -am package ) \
    || { echo "Đóng gói lỗi" >&2; exit 1; }
fi

# Chạy ở thư mục services/ để file log JSON luôn nằm ở services/logs/ (tech.md T1.3 mục 2)
if [ "$ONLY" = all ]; then
  ( cd "$ROOT/services" && nohup "$JAVA" -Dspring.profiles.active=dev -jar oauth2-service/target/oauth2-service-1.0.0.jar > "$RUN/oauth2-service.out" 2>&1 & ) >/dev/null 2>&1
  ( cd "$ROOT/services" && nohup "$JAVA" -jar storefront-web-bff/target/storefront-web-bff-1.0.0.jar > "$RUN/storefront-web-bff.out" 2>&1 & ) >/dev/null 2>&1
  ( cd "$ROOT/frontends" && nohup npm start > "$RUN/frontend.out" 2>&1 & ) >/dev/null 2>&1
fi

GW_ENV=(SHOP_FRONTEND_URL=http://localhost:4201)
if [ "$RULES" = relaxed ]; then
  # Phải khai báo đủ bảy trường của luật: một biến _LIMIT đơn lẻ làm cổng không khởi động (frontends/README.md)
  GW_ENV+=(APP_RATELIMIT_RULES_0_NAME=register APP_RATELIMIT_RULES_0_HOST=auth APP_RATELIMIT_RULES_0_METHOD=POST
           APP_RATELIMIT_RULES_0_PATH=/register APP_RATELIMIT_RULES_0_LIMIT=100000 APP_RATELIMIT_RULES_0_WINDOWSECONDS=3600
           APP_RATELIMIT_RULES_0_ENABLED=true)
fi
( cd "$ROOT/services" && nohup env "${GW_ENV[@]}" "$JAVA" -jar api-gateway/target/api-gateway-1.0.0.jar > "$RUN/api-gateway.out" 2>&1 & ) >/dev/null 2>&1
echo "$RULES" > "$RUN/gateway-rules"

# wait_for <tên> <cổng> <url> <mã mong đợi> <giây tối đa>
wait_for() {
  local name=$1 port=$2 url=$3 want=$4 max=$5 code=000 i
  for i in $(seq 1 "$max"); do
    code=$(curl -s -o /dev/null -m 3 -w '%{http_code}' "$url" || true)
    if [ "$code" = "$want" ]; then
      port_pid "$port" > "$RUN/$name.pid"
      echo "  $name lên (cổng $port, tiến trình $(cat "$RUN/$name.pid"))"
      return 0
    fi
    sleep 1
  done
  echo "  $name KHÔNG lên sau ${max}s (lần cuối trả $code). Xem $RUN/$name.out" >&2
  return 1
}

fail=0
if [ "$ONLY" = all ]; then
  wait_for oauth2-service 8004 http://localhost:8004/.well-known/openid-configuration 200 120 || fail=1
  wait_for storefront-web-bff 8090 http://localhost:8090/bff/auth/session 401 120 || fail=1
  wait_for frontend 4201 http://localhost:4201/ 200 240 || fail=1
fi
wait_for api-gateway 8000 http://auth.localhost:8000/.well-known/openid-configuration 200 120 || fail=1

if [ "$fail" = 0 ] && [ "$ONLY" = all ]; then
  code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' http://shop.localhost:8000/bff/auth/session || true)
  [ "$code" = 401 ] || { echo "  Cổng không chuyển tới BFF: shop.localhost:8000/bff/auth/session trả $code (mong đợi 401)" >&2; fail=1; }
fi
if [ "$fail" = 0 ]; then echo "Chuỗi đã lên (luật đăng ký: $RULES)."; else echo "Chuỗi chưa lên đủ." >&2; exit 1; fi

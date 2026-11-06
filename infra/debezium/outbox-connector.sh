#!/usr/bin/env bash
# Đăng ký, gỡ, xem connector Debezium của hộp thư event cho một BC. Chạy lại được (đăng ký lần hai chỉ cập nhật).
#
#   outbox-connector.sh register <bc>             đặt cấu hình (PUT), đợi RUNNING
#   outbox-connector.sh remove   <bc> [--keep-slot]  xóa connector rồi xóa khe sao chép (bỏ qua nếu --keep-slot)
#   outbox-connector.sh render   <bc>             in cấu hình sẽ gửi (mật khẩu bị che), không gọi gì
#   outbox-connector.sh status   <bc>
#
# <bc>: chữ thường, số, dấu gạch dưới, bắt đầu bằng chữ (ví dụ: seller). Tên connector là <bc>-outbox-connector.
# Biến môi trường (không có mật khẩu nào viết cứng trong repo):
#   bắt buộc (register, render): DB_HOST  DB_NAME  DB_USER  DB_PASSWORD
#   tùy chọn: DB_PORT (5432)  CONNECT_URL (http://localhost:8083)
#             TOPIC_PREFIX (<bc>)  SLOT_NAME (<bc>_outbox_slot)   -- mỗi connector phải có giá trị riêng
#   remove: DB_NAME DB_USER và DB_CONTAINER (tên container Postgres, để xóa khe sao chép bằng docker exec)
# DB_HOST là địa chỉ mà container Kafka Connect thấy (tên dịch vụ trong docker compose), không phải localhost của máy.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE="$HERE/outbox-connector.template.json"

die()  { echo "LỖI: $*" >&2; exit 1; }
usage() { sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2; exit 64; }
require() { for v in "$@"; do [ -n "${!v:-}" ] || die "Thiếu biến môi trường: $v"; done; }

cmd="${1:-}"; bc="${2:-}"; flag="${3:-}"
[ -n "$cmd" ] || usage
case "$cmd" in register|remove|render|status) ;; *) echo "Lệnh lạ: $cmd" >&2; usage ;; esac
[[ "$bc" =~ ^[a-z][a-z0-9_]*$ ]] || die "Tên BC không hợp lệ (chữ thường, số, gạch dưới, bắt đầu bằng chữ): '$bc'"

NAME="${bc}-outbox-connector"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
DB_PORT="${DB_PORT:-5432}"
TOPIC_PREFIX="${TOPIC_PREFIX:-$bc}"
SLOT_NAME="${SLOT_NAME:-${bc}_outbox_slot}"
[[ "$SLOT_NAME" =~ ^[a-z0-9_]+$ ]] || die "SLOT_NAME chỉ gồm chữ thường, số, gạch dưới: '$SLOT_NAME'"

json_escape() { local s="$1"; s="${s//\\/\\\\}"; s="${s//\"/\\\"}"; printf '%s' "$s"; }

render() { # $1: giá trị mật khẩu đã escape
  DB_HOST="$(json_escape "$DB_HOST")" DB_PORT="$(json_escape "$DB_PORT")" DB_USER="$(json_escape "$DB_USER")" \
  DB_NAME="$(json_escape "$DB_NAME")" TOPIC_PREFIX="$(json_escape "$TOPIC_PREFIX")" SLOT_NAME="$SLOT_NAME" \
  DB_PASSWORD="$1" \
    envsubst '${DB_HOST} ${DB_PORT} ${DB_USER} ${DB_PASSWORD} ${DB_NAME} ${TOPIC_PREFIX} ${SLOT_NAME}' < "$TEMPLATE"
}

# Từ chối khi connector khác đã giữ cùng topic.prefix hoặc slot.name (trùng prefix làm connector sau kẹt ở bước quét).
check_unique() {
  local names n cfg
  names="$(curl -fsS "$CONNECT_URL/connectors" | grep -o '"[^"]*"' | tr -d '"' || true)"
  for n in $names; do
    [ "$n" = "$NAME" ] && continue
    cfg="$(curl -fsS "$CONNECT_URL/connectors/$n/config")"
    grep -qF "\"topic.prefix\":\"$TOPIC_PREFIX\"" <<<"$cfg" && die "topic.prefix '$TOPIC_PREFIX' đã được connector $n giữ; đặt TOPIC_PREFIX khác"
    grep -qF "\"slot.name\":\"$SLOT_NAME\"" <<<"$cfg" && die "slot.name '$SLOT_NAME' đã được connector $n giữ; đặt SLOT_NAME khác"
  done
  return 0
}

case "$cmd" in
  render)
    require DB_HOST DB_NAME DB_USER DB_PASSWORD
    render '****'
    ;;
  register)
    require DB_HOST DB_NAME DB_USER DB_PASSWORD
    check_unique
    render "$(json_escape "$DB_PASSWORD")" \
      | curl -fsS -X PUT -H 'Content-Type: application/json' --data-binary @- "$CONNECT_URL/connectors/$NAME/config" >/dev/null \
      || die "Kafka Connect từ chối cấu hình của $NAME"
    for _ in $(seq 1 30); do
      st="$(curl -fsS "$CONNECT_URL/connectors/$NAME/status" 2>/dev/null || true)"
      if grep -q '"connector":{"state":"RUNNING"' <<<"$st" && grep -q '"tasks":\[{[^]]*"state":"RUNNING"' <<<"$st"; then
        echo "$NAME: RUNNING (topic.prefix=$TOPIC_PREFIX, slot=$SLOT_NAME)"; exit 0
      fi
      grep -q '"state":"FAILED"' <<<"$st" && die "$NAME FAILED: $st"
      sleep 1
    done
    die "$NAME chưa RUNNING sau 30 giây: ${st:-không có trạng thái}"
    ;;
  status)
    curl -fsS "$CONNECT_URL/connectors/$NAME/status"; echo
    ;;
  remove)
    code="$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$CONNECT_URL/connectors/$NAME")"
    case "$code" in 204|200|404) echo "$NAME: đã gỡ connector (HTTP $code)";; *) die "Gỡ $NAME thất bại (HTTP $code)";; esac
    if [ "$flag" = "--keep-slot" ]; then
      echo "CẢNH BÁO: giữ khe sao chép '$SLOT_NAME' (--keep-slot); Postgres giữ nhật ký giao dịch cho tới khi xóa nó." >&2; exit 0
    fi
    require DB_NAME DB_USER
    if [ -z "${DB_CONTAINER:-}" ]; then
      echo "Đã gỡ connector nhưng KHÔNG xóa được khe sao chép vì thiếu DB_CONTAINER. Xóa tay trên $DB_NAME:" >&2
      echo "  select pg_drop_replication_slot('$SLOT_NAME');" >&2
      exit 2
    fi
    psql_c() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "$1"; }
    for _ in $(seq 1 15); do   # sau khi gỡ, khe còn "active" thêm một lúc
      psql_c "select pg_drop_replication_slot(slot_name) from pg_replication_slots where slot_name='$SLOT_NAME' and not active" >/dev/null
      left="$(psql_c "select count(*) from pg_replication_slots where slot_name='$SLOT_NAME'")"
      [ "$left" = "0" ] && { echo "$NAME: khe sao chép '$SLOT_NAME' đã xóa"; exit 0; }
      sleep 1
    done
    die "Khe sao chép '$SLOT_NAME' vẫn còn sau 15 giây (đang được dùng?)"
    ;;
esac

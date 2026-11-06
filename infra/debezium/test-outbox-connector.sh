#!/usr/bin/env bash
# Kiểm thử cho outbox-connector.sh (feature 01 C1a, bước 5).
#   Phần A (không cần hạ tầng): hình dạng cấu hình dựng ra, kiểm biến, kiểm tên BC.
#   Phần B (cần Kafka Connect + Postgres thật): đăng ký lặp lại, trùng topic.prefix/slot, gỡ kèm xóa khe sao chép.
#       Bật bằng LIVE=1 và các biến: CONNECT_URL, DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, DB_CONTAINER.
# Dùng:  ./test-outbox-connector.sh            (phần A)
#        LIVE=1 CONNECT_URL=... ./test-outbox-connector.sh   (A và B)
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/outbox-connector.sh"
TEMPLATE="$HERE/outbox-connector.template.json"
pass=0; fail=0
ok()   { pass=$((pass+1)); echo "  ok   $1"; }
bad()  { fail=$((fail+1)); echo "  FAIL $1"; }
check() { local name="$1"; shift; if "$@" >/dev/null 2>&1; then ok "$name"; else bad "$name"; fi; }
check_not() { local name="$1"; shift; if "$@" >/dev/null 2>&1; then bad "$name"; else ok "$name"; fi; }
contains() { grep -qF -- "$2" <<<"$1"; }

SECRET='p@ss"w\ord$1'
base_env() { env -i PATH="$PATH" DB_HOST=postgres-seller DB_NAME=seller_db DB_USER=t3nexus DB_PASSWORD="$SECRET" "$@"; }

echo "A. Cấu hình dựng ra"
[ -f "$SCRIPT" ] && ok "có outbox-connector.sh" || { bad "có outbox-connector.sh"; echo "pass=$pass fail=$fail"; exit 1; }
[ -f "$TEMPLATE" ] && ok "có outbox-connector.template.json" || bad "có outbox-connector.template.json"

out="$(base_env bash "$SCRIPT" render seller 2>&1)"; rc=$?
[ $rc -eq 0 ] && ok "render thành công" || bad "render thành công (rc=$rc): $out"
contains "$out" '"database.hostname": "postgres-seller"' && ok "host từ biến" || bad "host từ biến"
contains "$out" '"database.port": "5432"' && ok "cổng mặc định 5432" || bad "cổng mặc định 5432"
contains "$out" '"topic.prefix": "seller"' && ok "topic.prefix mặc định theo BC" || bad "topic.prefix mặc định theo BC"
contains "$out" '"slot.name": "seller_outbox_slot"' && ok "slot.name mặc định theo BC" || bad "slot.name mặc định theo BC"
contains "$out" '"key.converter": "org.apache.kafka.connect.json.JsonConverter"' && ok "khai lớp key.converter" || bad "khai lớp key.converter"
contains "$out" '"value.converter": "org.apache.kafka.connect.json.JsonConverter"' && ok "khai lớp value.converter" || bad "khai lớp value.converter"
contains "$out" '"key.converter.schemas.enable": "false"' && ok "key không bọc schema" || bad "key không bọc schema"
contains "$out" '"value.converter.schemas.enable": "false"' && ok "value không bọc schema" || bad "value không bọc schema"
contains "$out" '"snapshot.mode": "no_data"' && ok "snapshot.mode no_data" || bad "snapshot.mode no_data"
contains "$out" '"transforms.outbox.route.topic.replacement": "$1"' && ok "giữ nguyên \$1 của regex chủ đề" || bad "giữ nguyên \$1 của regex chủ đề"
contains "$out" '"table.include.list": "public.outbox_events"' && ok "bảng outbox_events" || bad "bảng outbox_events"
contains "$out" "$SECRET" && bad "render KHÔNG in mật khẩu" || ok "render KHÔNG in mật khẩu"
contains "$out" '"database.password": "****"' && ok "mật khẩu bị che" || bad "mật khẩu bị che"

out="$(base_env TOPIC_PREFIX=pfx SLOT_NAME=my_slot DB_PORT=5999 bash "$SCRIPT" render seller 2>&1)"
contains "$out" '"topic.prefix": "pfx"' && contains "$out" '"slot.name": "my_slot"' && contains "$out" '"database.port": "5999"' \
  && ok "ghi đè prefix, slot, cổng bằng biến" || bad "ghi đè prefix, slot, cổng bằng biến"

out="$(base_env DB_USER='a"b\c' bash "$SCRIPT" render seller 2>&1)"
contains "$out" '"database.user": "a\"b\\c"' && ok "ký tự đặc biệt được escape JSON" || bad "ký tự đặc biệt được escape JSON: $out"

echo "A. Kiểm đầu vào"
for v in DB_HOST DB_NAME DB_USER DB_PASSWORD; do
  out="$(env -i PATH="$PATH" DB_HOST=h DB_NAME=n DB_USER=u DB_PASSWORD=p bash -c "unset $v; bash '$SCRIPT' render seller" 2>&1)"; rc=$?
  { [ $rc -ne 0 ] && contains "$out" "$v"; } && ok "thiếu $v bị từ chối và nêu tên biến" || bad "thiếu $v bị từ chối và nêu tên biến"
done
for name in "Seller" "se ller" "1seller" "sel-ler" ""; do
  check_not "tên BC '$name' bị từ chối" base_env bash "$SCRIPT" render "$name"
done
check_not "lệnh lạ bị từ chối" base_env bash "$SCRIPT" explode seller

echo "A. Tệp mẫu không chứa bí mật"
check_not "mẫu không có mật khẩu viết cứng" grep -E '"database.password"\s*:\s*"[^$]' "$TEMPLATE"
check "mật khẩu trong mẫu là biến" grep -F '"database.password": "${DB_PASSWORD}"' "$TEMPLATE"

if [ "${LIVE:-0}" = "1" ]; then
  echo "B. Chạy thật trên Kafka Connect"
  : "${CONNECT_URL:?}" "${DB_PORT:=5432}" "${DB_CONTAINER:?}"
  export CONNECT_URL DB_PORT DB_CONTAINER
  live_env() { env -i PATH="$PATH" CONNECT_URL="$CONNECT_URL" DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_NAME="$DB_NAME" \
                DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" DB_CONTAINER="$DB_CONTAINER" "$@"; }
  list() { curl -s "$CONNECT_URL/connectors"; }
  slots() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "select slot_name from pg_replication_slots order by 1"; }

  live_env bash "$SCRIPT" remove livea >/dev/null 2>&1; live_env bash "$SCRIPT" remove liveb >/dev/null 2>&1

  check "đăng ký lần 1 thành công" live_env bash "$SCRIPT" register livea
  check "đăng ký lần 2 (chạy lại) thành công" live_env bash "$SCRIPT" register livea
  n="$(list | grep -o 'livea-outbox-connector' | wc -l | tr -d ' ')"
  [ "$n" = "1" ] && ok "sau hai lần chỉ có một connector" || bad "sau hai lần chỉ có một connector (có $n)"
  st="$(curl -s "$CONNECT_URL/connectors/livea-outbox-connector/status")"
  contains "$st" '"state":"RUNNING"' && ok "connector RUNNING" || bad "connector RUNNING: $st"
  contains "$(slots)" "livea_outbox_slot" && ok "có khe sao chép livea_outbox_slot" || bad "có khe sao chép livea_outbox_slot"

  if [ -n "${KAFKA_CONTAINER:-}" ]; then   # luồng thật: hàng vào bảng thì ra bus đúng chủ đề, khóa, khung trần
    RUN="$(date +%s)$RANDOM"; EV="ev-live-$RUN"; AGG="agg-live-$RUN"; TOPIC="livea.t$RUN.created"   # riêng mỗi lần chạy: chủ đề Kafka giữ thông điệp của lần trước
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "INSERT INTO outbox_events (event_id,aggregate_type,aggregate_id,event_type,routing_key,payload,occurred_on,created_at) VALUES ('$EV','Probe','$AGG','ProbeEvent','$TOPIC','{\"eventId\":\"$EV\",\"schemaVersion\":1,\"payload\":{\"n\":1}}',now(),now())" >/dev/null 2>&1
    sleep 4
    msg="$(MSYS_NO_PATHCONV=1 docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 --topic "$TOPIC" --from-beginning --timeout-ms 10000 --property print.key=true --property print.headers=true 2>/dev/null)"
    contains "$msg" "id:$EV" && ok "tiêu đề id là event_id" || bad "tiêu đề id là event_id: $msg"
    contains "$msg" "\"$AGG\"" && ok "khóa là aggregate_id" || bad "khóa là aggregate_id: $msg"
    contains "$msg" "{\"eventId\":\"$EV\",\"schemaVersion\":1,\"payload\":{\"n\":1}}" && ok "giá trị là JSON trần đã mở rộng" || bad "giá trị là JSON trần đã mở rộng: $msg"
    contains "$msg" '"schema"' && bad "không bọc schema" || ok "không bọc schema"
  fi

  out="$(live_env TOPIC_PREFIX=livea bash "$SCRIPT" register liveb 2>&1)"; rc=$?
  { [ $rc -ne 0 ] && contains "$out" "livea-outbox-connector"; } && ok "trùng topic.prefix bị từ chối, nêu connector đang giữ" || bad "trùng topic.prefix bị từ chối: rc=$rc $out"
  out="$(live_env SLOT_NAME=livea_outbox_slot bash "$SCRIPT" register liveb 2>&1)"; rc=$?
  { [ $rc -ne 0 ] && contains "$out" "livea-outbox-connector"; } && ok "trùng slot.name bị từ chối, nêu connector đang giữ" || bad "trùng slot.name bị từ chối: rc=$rc $out"
  contains "$(list)" "liveb-outbox-connector" && bad "connector bị từ chối không được tạo" || ok "connector bị từ chối không được tạo"

  check "gỡ thành công" live_env bash "$SCRIPT" remove livea
  contains "$(list)" "livea-outbox-connector" && bad "connector đã mất sau khi gỡ" || ok "connector đã mất sau khi gỡ"
  contains "$(slots)" "livea_outbox_slot" && bad "khe sao chép đã bị xóa cùng" || ok "khe sao chép đã bị xóa cùng"
  check "gỡ lần hai (đã gỡ) vẫn thành công" live_env bash "$SCRIPT" remove livea
  check_not "gỡ mà không có DB_CONTAINER và không --keep-slot bị từ chối (không để rò khe)" \
    env -i PATH="$PATH" CONNECT_URL="$CONNECT_URL" DB_HOST=x DB_NAME=x DB_USER=x DB_PASSWORD=x bash -c "bash '$SCRIPT' register livea >/dev/null 2>&1; bash '$SCRIPT' remove livea"
  live_env bash "$SCRIPT" remove livea --keep-slot >/dev/null 2>&1
  live_env bash "$SCRIPT" remove livea >/dev/null 2>&1
fi

echo; echo "pass=$pass fail=$fail"
[ $fail -eq 0 ]

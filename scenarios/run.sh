#!/usr/bin/env bash
# Kiểm đầu-cuối cả chặng C0 (feature 09): từ trạng thái sạch, dựng chuỗi, chạy S1 đến S5 và các kiểm chéo qua cổng, dọn, đối chiếu số dòng.
# Dùng: scenarios/run.sh [--no-build]
#   Pha A: cổng luật đăng ký nâng (bộ thật của feature 05 cộng S3, truy vết lỗi).
#   Pha B: cổng khởi động lại với luật mặc định (10/giờ): 429 của đăng ký.
# GHI VÀO HẠ TẦNG DEV (Postgres oauth2_db, Redis, Kafka): dữ liệu thử có tiền tố e2e-, được dọn; tài khoản ducthinh@dev.aplus.vn giữ nguyên.
# Thoát khác 0 nếu có test đỏ, chuỗi không lên, hoặc số dòng cuối khác số dòng đầu. Nhật ký ở scenarios/.run/.
set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RUN="$ROOT/scenarios/.run"; mkdir -p "$RUN"
UP_ARGS=()
[ "${1:-}" = "--no-build" ] && UP_ARGS+=(--no-build)

PG=infra-postgres-oauth2-1; REDIS=infra-redis-1
snapshot() {
  docker exec "$PG" psql -U t3nexus -d oauth2_db -At -c "
    select 'user_accounts', count(*) from user_accounts union all
    select 'login_sessions', count(*) from login_sessions union all
    select 'client_sessions', count(*) from client_sessions union all
    select 'oauth2_authorization', count(*) from oauth2_authorization union all
    select 'oauth2_authorization_consent', count(*) from oauth2_authorization_consent union all
    select 'spring_session', count(*) from spring_session union all
    select 'outbox_events', count(*) from outbox_events union all
    select 'tai_khoan_giu_nguyen(ducthinh)', count(*) from user_accounts where email = 'ducthinh@dev.aplus.vn'" | tr -d '\r'
  echo "redis_keys:"
  docker exec "$REDIS" redis-cli --scan | tr -d '\r' | grep -v '^ratelimit:' | sort
}

fail=0
note() { echo; echo "== $*"; }

# Luôn dừng chuỗi khi thoát (kể cả khi lỗi giữa chừng)
trap 'bash "$ROOT/scenarios/down.sh" >/dev/null 2>&1' EXIT

note "Số đầu"
bash "$ROOT/scenarios/down.sh" >/dev/null 2>&1   # bảo đảm bắt đầu từ trạng thái không dịch vụ nào chạy (chỉ tiến trình do up.sh ghi mã)
snapshot > "$RUN/before.txt"; cat "$RUN/before.txt"

note "Dựng chuỗi (pha A: cổng luật đăng ký nâng)"
bash "$ROOT/scenarios/up.sh" --rules relaxed "${UP_ARGS[@]}" || { echo "Chuỗi không lên" >&2; exit 1; }

note "Pha A: S1 đến S5, S3, truy vết lỗi, và các kiểm khác của bộ thật"
( cd "$ROOT/frontends" && npx playwright test -c playwright.real.config.ts > "$RUN/phase-a.log" 2>&1 ); rc=$?
grep -E "^ +[0-9]+ (passed|failed|skipped|flaky)|^ +x |failed" "$RUN/phase-a.log" | head -20
[ "$rc" = 0 ] || { echo "PHA A ĐỎ (mã thoát $rc), xem $RUN/phase-a.log"; fail=1; }

note "Pha B: cổng luật mặc định, 429 của đăng ký"
bash "$ROOT/scenarios/down.sh" --only gateway >/dev/null
bash "$ROOT/scenarios/up.sh" --only gateway --rules default --no-build || { echo "Cổng pha B không lên" >&2; exit 1; }
( cd "$ROOT/frontends" && E2E_GATEWAY_RULES=default npx playwright test -c playwright.real.config.ts ratelimit.real > "$RUN/phase-b.log" 2>&1 ); rc=$?
grep -E "^ +[0-9]+ (passed|failed|skipped|flaky)|^ +x |failed" "$RUN/phase-b.log" | head -10
[ "$rc" = 0 ] || { echo "PHA B ĐỎ (mã thoát $rc), xem $RUN/phase-b.log"; fail=1; }

note "Dừng chuỗi và đối chiếu số dòng"
bash "$ROOT/scenarios/down.sh" >/dev/null
snapshot > "$RUN/after.txt"
if diff -u "$RUN/before.txt" "$RUN/after.txt" > "$RUN/diff.txt"; then
  echo "Số dòng cuối bằng số dòng đầu (bảy bảng, khóa Redis; tài khoản giữ nguyên còn nguyên)."
else
  echo "SỐ DÒNG LỆCH so với lúc đầu:"; cat "$RUN/diff.txt"; fail=1
fi

echo
if [ "$fail" = 0 ]; then echo "KẾT QUẢ: ĐẠT (hai pha xanh, dữ liệu sạch)."; else echo "KẾT QUẢ: KHÔNG ĐẠT."; fi
exit "$fail"

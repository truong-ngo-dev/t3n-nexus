import { FullConfig } from '@playwright/test';
import { ADMIN_EMAIL, TEST_PREFIX, redisDelete, redisKeys, seedAdmin, sql } from './infra';

const TABLES = ['user_accounts', 'login_sessions', 'client_sessions', 'oauth2_authorization', 'oauth2_authorization_consent', 'spring_session', 'outbox_events'];
const counts = (): Record<string, number> => Object.fromEntries(TABLES.map((t) => [t, Number(sql(`SELECT count(*) FROM ${t}`)[0])]));

interface Kept { authorizations: string[]; sessions: string[] }

/** Xóa mọi dữ liệu thử của lần chạy, rồi kiểm số dòng từng bảng về đúng như trước khi chạy. */
function cleanUp(before: Record<string, number>, maxOutboxId: number, redisBefore: Set<string>, kept: Kept): void {
  // Hai bảng này còn chứa dòng không gắn tài khoản thử (phiên HTTP ẩn danh của trang đăng nhập; token client_credentials
  // nội bộ của chính dịch vụ), nên xóa theo "mọi dòng mới so với lúc bắt đầu" chứ không theo tiền tố.
  const notIn = (ids: string[]) => (ids.length ? `NOT IN (${ids.map((i) => `'${i}'`).join(',')})` : 'IS NOT NULL');
  const users = `(SELECT id FROM user_accounts WHERE email LIKE '${TEST_PREFIX}%')`;
  const logins = `(SELECT id FROM login_sessions WHERE user_account_id IN ${users})`;
  sql(`
    DELETE FROM client_sessions WHERE login_session_id IN ${logins};
    DELETE FROM oauth2_authorization_consent WHERE principal_name LIKE '${TEST_PREFIX}%' OR principal_name IN (SELECT id::text FROM user_accounts WHERE email LIKE '${TEST_PREFIX}%');
    DELETE FROM oauth2_authorization WHERE id ${notIn(kept.authorizations)};
    DELETE FROM login_sessions WHERE user_account_id IN ${users};
    DELETE FROM spring_session WHERE primary_id ${notIn(kept.sessions)};
    DELETE FROM user_accounts WHERE email LIKE '${TEST_PREFIX}%';
    DELETE FROM outbox_events WHERE id > ${maxOutboxId};
  `);
  redisDelete(redisKeys().filter((k) => !redisBefore.has(k)));

  const after = counts();
  const leftovers = TABLES.filter((t) => after[t] !== before[t]).map((t) => `${t}: trước ${before[t]}, sau ${after[t]}`);
  if (leftovers.length) throw new Error(`Còn dữ liệu thử sau khi dọn: ${leftovers.join('; ')}`);
}

export default async function globalSetup(config: FullConfig): Promise<() => void> {
  const base = config.projects[0].use.baseURL as string;
  for (const [name, url] of [['cổng + BFF', `${base}/bff/auth/session`], ['Xác thực', 'http://auth.localhost:8000/.well-known/openid-configuration']] as const) {
    const res = await fetch(url).catch((e: Error) => { throw new Error(`Không tới được ${name} (${url}): ${e.message}. Đã dựng hạ tầng chưa? Xem README.`); });
    if (name === 'cổng + BFF' && res.status !== 401) throw new Error(`${url} trả ${res.status}, mong đợi 401 (khách). Cổng có trỏ SHOP_FRONTEND_URL đúng không?`);
    if (name === 'Xác thực' && res.status !== 200) throw new Error(`${url} trả ${res.status}`);
  }
  if ((await fetch(base)).status !== 200) throw new Error(`${base} không trả giao diện. Đã chạy npm start và đặt SHOP_FRONTEND_URL chưa?`);

  // Trạng thái đầu chạy phải sạch dữ liệu thử của lần chạy trước (nếu lần trước bị ngắt giữa chừng)
  if (sql(`SELECT count(*) FROM user_accounts WHERE email LIKE '${TEST_PREFIX}%'`)[0] !== '0') {
    throw new Error(`Còn tài khoản thử "${TEST_PREFIX}*" từ lần chạy trước. Dọn tay rồi chạy lại: DELETE FROM user_accounts WHERE email LIKE '${TEST_PREFIX}%' (và phiên liên quan).`);
  }

  // Mức giới hạn tần suất của cổng còn sót (kể cả do chính các yêu cầu kiểm tra ở trên tạo ra) sẽ bị ghi vào "trước" rồi không bao giờ được dọn,
  // và cộng dồn giữa hai lần chạy liền nhau trong cùng một cửa sổ (mức chung 300 mỗi phút): xóa trước khi ghi số đầu. Chỉ khóa tiền tố ratelimit:.
  redisDelete(redisKeys().filter((k) => k.startsWith('ratelimit:')));

  const before = counts();
  const maxOutboxId = Number(sql('SELECT coalesce(max(id), 0) FROM outbox_events')[0]);
  const redisBefore = new Set(redisKeys());
  const kept: Kept = { authorizations: sql('SELECT id FROM oauth2_authorization'), sessions: sql('SELECT primary_id FROM spring_session') };

  seedAdmin();
  if (sql(`SELECT role FROM user_accounts WHERE email = '${ADMIN_EMAIL}'`)[0] !== 'ADMIN') throw new Error('Không tạo được tài khoản Quản trị thử');

  return () => cleanUp(before, maxOutboxId, redisBefore, kept);
}

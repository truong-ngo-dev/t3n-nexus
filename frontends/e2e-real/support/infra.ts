import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** Tiền tố của mọi dữ liệu thử: dọn theo tiền tố này, không đụng dữ liệu khác trong DB dev. */
export const TEST_PREFIX = 'e2e-';
/** Mật khẩu thử, dùng cho mọi tài khoản thử (đúng luật: 8-64 ký tự, A-Za-z0-9@$, có chữ và số). */
export const TEST_PASSWORD = 'Passw0rdE2e';
/** Bcrypt hệ số 10 của TEST_PASSWORD; chỉ mở được tài khoản thử, được xóa sau mỗi lần chạy. */
const TEST_PASSWORD_HASH = '$2a$10$lz48dENL415IT18jRFGxK.xmKk97V8KUlZzM47U9nrR./Zz.ANpTe';
export const ADMIN_EMAIL = `${TEST_PREFIX}admin@t3nexus.com.vn`;

const POSTGRES = process.env.E2E_POSTGRES_CONTAINER ?? 'infra-postgres-oauth2-1';
const REDIS = process.env.E2E_REDIS_CONTAINER ?? 'infra-redis-1';
const DB_USER = 't3nexus';
const DB_NAME = 'oauth2_db';

const docker = (args: string[], input?: string): string => execFileSync('docker', args, { encoding: 'utf8', input });

/** Chạy SQL trên DB Xác thực dev, trả về từng dòng (các cột cách nhau `|`). */
export function sql(statement: string): string[] {
  const out = docker(['exec', '-i', POSTGRES, 'psql', '-U', DB_USER, '-d', DB_NAME, '-v', 'ON_ERROR_STOP=1', '-tA'], statement);
  return out.split('\n').map((l) => l.trim()).filter(Boolean);
}

export const redisKeys = (): string[] =>
  docker(['exec', REDIS, 'redis-cli', '--scan']).split('\n').map((k) => k.trim()).filter(Boolean);

export const redisDelete = (keys: string[]): void => {
  for (let i = 0; i < keys.length; i += 100) docker(['exec', REDIS, 'redis-cli', 'DEL', ...keys.slice(i, i + 100)]);
};

/** Khóa phiên của BFF trong Redis (Spring Session, tiền tố mặc định). */
export const bffSessionKeys = (): string[] => redisKeys().filter((k) => k.startsWith('spring:session:'));

/** Tạo tài khoản Quản trị thử bằng script seed có sẵn (infra/seed/admin.sql), chạy lại vô hại. */
export function seedAdmin(): void {
  const script = readFileSync(join(__dirname, '..', '..', '..', 'infra', 'seed', 'admin.sql'), 'utf8');
  docker(
    ['exec', '-i', POSTGRES, 'psql', '-U', DB_USER, '-d', DB_NAME, '-v', `email=${ADMIN_EMAIL}`, '-v', `hash=${TEST_PASSWORD_HASH}`],
    script,
  );
}

export const accountCount = (email: string): number => Number(sql(`SELECT count(*) FROM user_accounts WHERE email = '${email.toLowerCase()}'`)[0]);

let counter = 0;
export const uniqueEmail = (): string => `${TEST_PREFIX}${Date.now().toString(36)}${(counter++).toString(36)}@example.com`;

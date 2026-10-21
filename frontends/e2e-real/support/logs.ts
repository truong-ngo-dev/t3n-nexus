import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

/** Thư mục log JSON của các dịch vụ: tiến trình chạy ở `services/` (scenarios/up.sh) nên log nằm ở `services/logs/`. */
const LOG_DIR = process.env.E2E_LOG_DIR ?? join(__dirname, '..', '..', '..', 'services', 'logs');

export type LogLine = Record<string, unknown> & { message?: string; 'trace.id'?: string };

/** Các dòng JSON của file log hiện hành (`<dịch vụ>.json`), đọc phần cuối tối đa `tailBytes` byte. */
export function readLog(service: string, tailBytes = 4_000_000): LogLine[] {
  const file = join(LOG_DIR, `${service}.json`);
  if (!existsSync(file)) throw new Error(`Không thấy file log ${file}. Dịch vụ có chạy ở thư mục services/ (scenarios/up.sh) không?`);
  const size = statSync(file).size;
  const text = readFileSync(file, 'utf8');
  const tail = size > tailBytes ? text.slice(text.length - tailBytes) : text;
  const lines: LogLine[] = [];
  for (const raw of tail.split('\n')) {
    if (!raw.startsWith('{')) continue; // bỏ dòng đầu bị cắt giữa chừng
    try { lines.push(JSON.parse(raw) as LogLine); } catch { /* dòng đang ghi dở */ }
  }
  return lines;
}

/** Nội dung thô của phần cuối file log, để kiểm một chuỗi không xuất hiện ở bất kỳ dòng nào. */
export function rawLogTail(service: string, tailBytes = 4_000_000): string {
  const text = readFileSync(join(LOG_DIR, `${service}.json`), 'utf8');
  return text.length > tailBytes ? text.slice(text.length - tailBytes) : text;
}

/** Log ghi bất đồng bộ: chờ có giới hạn tới khi có dòng thỏa điều kiện. */
export async function waitForLogLine(service: string, match: (l: LogLine) => boolean, timeoutMs = 10_000): Promise<LogLine | undefined> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const found = readLog(service).reverse().find(match);
    if (found || Date.now() > deadline) return found;
    await new Promise((r) => setTimeout(r, 250));
  }
}

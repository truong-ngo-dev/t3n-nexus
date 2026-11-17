/**
 * Giá trị là object thường (có khóa, giá trị bên trong vẫn `unknown`). Loại `null` và mảng, hai thứ
 * mà `typeof x === 'object'` vẫn cho qua. Bước đầu để thu hẹp thân phản hồi `unknown` trước khi đọc trường.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

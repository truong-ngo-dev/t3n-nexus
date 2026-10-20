/** Vai của người dùng, từ vựng của backend (Xác thực cấp đúng hai giá trị). Định nghĩa một chỗ duy nhất (R1). */
export const ROLES = ['USER', 'ADMIN'] as const;

export type Role = (typeof ROLES)[number];

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

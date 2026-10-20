import { isRole, Role } from './role';

/** Phiên của người đã đăng nhập, đúng phần `GET /bff/auth/session` trả về. */
export interface Session {
  userId: string;
  role: Role;
}

/**
 * Đọc nội dung phản hồi phiên. Trả `null` nếu sai khuôn, thiếu `userId` hoặc vai lạ: người gọi coi
 * đó là lỗi (không xác định), tuyệt đối không cho vào (R1). Trường thừa bị bỏ qua.
 */
export function parseSession(body: unknown): Session | null {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) return null;
  const { userId, role } = body as Record<string, unknown>;
  if (typeof userId !== 'string' || userId === '' || !isRole(role)) return null;
  return { userId, role };
}

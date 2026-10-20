import { Role } from '@t3n/model';

/** Nhãn hiển thị của vai (flow.md mục 2). Vai chỉ để hiển thị ở C0, chưa quyết định quyền vào trang. */
export const ROLE_LABEL: Record<Role, string> = {
  USER: 'Người dùng',
  ADMIN: 'Quản trị',
};

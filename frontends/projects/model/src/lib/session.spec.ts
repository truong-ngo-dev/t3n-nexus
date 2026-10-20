import { isRole, parseSession, ROLES } from '@t3n/model';

// KT-01 (phần kiểu): phản hồi phiên hợp lệ thành Session; vai lạ hoặc thiếu userId bị từ chối (R1)
describe('parseSession', () => {
  it('chấp nhận vai USER', () => {
    expect(parseSession({ userId: 'u1', role: 'USER' })).toEqual({ userId: 'u1', role: 'USER' });
  });

  it('chấp nhận vai ADMIN', () => {
    expect(parseSession({ userId: 'u2', role: 'ADMIN' })).toEqual({ userId: 'u2', role: 'ADMIN' });
  });

  it('bỏ qua trường thừa, chỉ giữ userId và role', () => {
    expect(parseSession({ userId: 'u1', role: 'USER', email: 'a@b.c' })).toEqual({ userId: 'u1', role: 'USER' });
  });

  it.each(['SUPERUSER', 'user', '', 42, null, undefined])('từ chối vai lạ %p', (role) => {
    expect(parseSession({ userId: 'u1', role })).toBeNull();
  });

  it.each([undefined, null, '', 7])('từ chối userId không hợp lệ %p', (userId) => {
    expect(parseSession({ userId, role: 'USER' })).toBeNull();
  });

  it.each([null, undefined, 'USER', 5, []])('từ chối nội dung không phải đối tượng %p', (body) => {
    expect(parseSession(body)).toBeNull();
  });
});

describe('Role', () => {
  it('chỉ có USER và ADMIN', () => {
    expect([...ROLES]).toEqual(['USER', 'ADMIN']);
  });

  it('isRole phân biệt hoa thường', () => {
    expect(isRole('ADMIN')).toBe(true);
    expect(isRole('admin')).toBe(false);
  });
});

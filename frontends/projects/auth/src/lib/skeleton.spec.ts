import { AUTH_LIBRARY } from '@t3n/auth';

describe('auth library skeleton', () => {
  it('is resolvable through the @t3n/auth path alias', () => {
    expect(AUTH_LIBRARY).toBe('auth');
  });
});

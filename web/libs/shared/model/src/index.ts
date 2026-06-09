export type Role = 'GUEST' | 'CUSTOMER' | 'SELLER' | 'SHIPPER' | 'ADMIN';

export interface User {
  id:        string;
  role:      Role;
  fullName:  string;
  avatarUrl: string | null;
}

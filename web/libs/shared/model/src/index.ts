export type Role = 'GUEST' | 'CUSTOMER' | 'SELLER' | 'SHIPPER' | 'ADMIN';

export interface User {
  id:    string;
  email: string;
  role:  Role;
}

export type Role = 'GUEST' | 'CUSTOMER' | 'SELLER' | 'SHIPPER' | 'ADMIN';

export interface User {
  id:          string;
  role:        Role;
  fullName:    string;
  avatarUrl:   string | null;
  hasPassword?: boolean;
}

export interface LoginHistoryItem {
  action:    string;
  ip:        string;
  browser:   string;
  os:        string;
  createdAt: string;
  endedAt:   string | null;
}

export interface DeviceItem {
  deviceId:    string;
  displayName: string;
  browser:     string;
  os:          string;
  lastSeenAt:  string;
  lastAction:  string;
  isCurrent:   boolean;
  isTrusted:   boolean;
  sessionId:   string | null;
}

export interface PagedData<T> {
  content:       T[];
  totalElements: number;
  page:          number;
  size:          number;
}

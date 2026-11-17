import {isRecord} from '@t3n/model';

export const SELLER_PROFILE_STATUSES = ['PENDING_REVIEW', 'APPROVED', 'REJECTED'] as const;
export type SellerProfileStatus = (typeof SELLER_PROFILE_STATUSES[number]);

export function isSellerProfileStatus(value: unknown): value is SellerProfileStatus {
  return typeof value === 'string' && (SELLER_PROFILE_STATUSES as readonly string[]).includes(value);
}

export type SellerType = 'INDIVIDUAL' | 'HOUSEHOLD' | 'BUSINESS';

/** Thông tin pháp lý: trường nào bắt buộc tùy loại hình (api.yaml `LegalInfo`). */
export interface LegalInfo {
  legalName?: string;
  representativeName?: string;
  identityNumber?: string;
  taxCode?: string;
  businessAddress?: string;
}

export interface Contact {
  personName?: string;
  phone?: string;
  address?: string | null;
}

export interface Shop {
  shopName?: string;
  description?: string | null;
  contact?: Contact;
}

/** Hồ sơ của tôi, đúng `data` của `GET /api/seller/profile` (api.yaml `SellerProfile`). `submittedAt` là chuỗi thời điểm ISO. */
export interface SellerProfile {
  id: string;
  status: SellerProfileStatus;
  sellerType: SellerType;
  legalInfo: LegalInfo;
  shop: Shop;
  rejectionReason?: string | null;
  submittedAt?: string;
}

/**
 * Đọc thân `GET /api/seller/profile` (lớp bọc `ApiResponse`, hồ sơ nằm ở `data`). Trả `null` nếu sai khuôn.
 * Chỉ `success` và `status` được kiểm lúc chạy, vì `status` quyết định luồng. Các trường còn lại chỉ để hiển thị
 * nên được chấp nhận theo hợp đồng: lời khẳng định kiểu duy nhất ở đây, cố ý và gom một chỗ.
 */
export function parseSellerProfile(body: unknown): SellerProfile | null {
  if (!isRecord(body) || body['success'] !== true) return null;
  const data = body['data'];
  if (!isRecord(data) || !isSellerProfileStatus(data['status'])) return null;
  return data as unknown as SellerProfile;
}

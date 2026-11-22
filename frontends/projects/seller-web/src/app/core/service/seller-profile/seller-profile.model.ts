import {isRecord} from '@t3n/model';

export const SELLER_PROFILE_STATUSES = ['PENDING_REVIEW', 'APPROVED', 'REJECTED'] as const;
export type SellerProfileStatus = (typeof SELLER_PROFILE_STATUSES)[number];

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
 * Đọc `data` của `GET /api/seller/profile` (hồ sơ). Dùng làm `parseData` của `unwrapData` ở `@t3n/http`, nên nhận **`data`**,
 * không phải cả thân: `unwrapData` đã lấy `data` ra từ thân. Trả `null` nếu sai khuôn
 * (`unwrapData` sẽ phát `InvalidApiResponse` lý do `data-mismatch`).
 *
 * Mức kiểm "chặt vừa": chỉ `status` được kiểm lúc chạy, vì nó quyết định luồng. Các trường còn lại chỉ để hiển thị
 * nên được chấp nhận theo hợp đồng: lời khẳng định kiểu duy nhất ở đây, cố ý và gom một chỗ.
 *
 * @param data giá trị của trường `data` trong khung, còn `unknown`
 */
export function parseSellerProfile(data: unknown): SellerProfile | null {
  if (!isRecord(data) || !isSellerProfileStatus(data['status'])) return null;
  return data as unknown as SellerProfile;
}

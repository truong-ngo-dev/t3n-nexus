import {SellerProfile} from '../../../core/service/seller-profile/seller-profile.model';

/**
 * Yêu cầu nộp và nộp lại hồ sơ (api.yaml `SubmitSellerProfileRequest`): đúng ba trường nhập của hồ sơ, nên suy ra từ `SellerProfile`
 * thay vì khai báo lại. Dùng chung cho nộp (`POST /profile`), sửa khi chờ duyệt (`PUT /profile`) và nộp lại (`POST /profile/resubmit`):
 * cả ba gửi toàn bộ trường.
 */
export type SubmitSellerProfileRequest = Pick<SellerProfile, 'sellerType' | 'legalInfo' | 'shop'>;

/**
 * Thân `PATCH /profile/shop-info` khi đã duyệt (api.yaml `UpdateShopInfoRequest`, bỏ `shopName` vì tên đã khóa sau khi duyệt).
 * Ba nghĩa của mỗi trường: vắng thì giữ nguyên, `null` thì xóa (chỉ trường tùy chọn), có giá trị thì đặt.
 */
export interface UpdateShopInfoRequest {
  description?: string | null;
  contact?: {
    personName?: string;
    phone?: string;
    address?: string | null;
  };
}

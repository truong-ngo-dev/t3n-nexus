import { SellerProfileState } from './service/seller-status.service';

/** Nhãn hiển thị cho từng trạng thái hồ sơ người bán. `Record` buộc phải đủ mọi `kind`: thiếu một giá trị là lỗi biên dịch. */
export const STATUS_LABEL: Record<SellerProfileState['kind'], string> = {
  loading: 'Đang kiểm tra',
  none: 'Chưa là người bán',
  pending: 'Chờ duyệt',
  approved: 'Đã duyệt',
  rejected: 'Bị từ chối',
  unknown: 'Không xác định được trạng thái',
};

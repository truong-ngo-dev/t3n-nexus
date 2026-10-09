# Feature 06: Cổng truy cập

> **Trạng thái:** chưa thiết kế · **Chặng:** C0 · **Loại:** Kỹ thuật
>
> **Nguồn:** Roadmap C0 (hạ tầng) · **Kiểm chứng bằng:** Mọi kịch bản đi qua cổng

Tài liệu của feature này (luồng, kế hoạch, phần hoãn, chọn giải pháp kỹ thuật, đáp ứng NFR) đặt trong thư mục này khi
bắt đầu thiết kế.

## Việc đã giao cho feature này

Từ feature 01 (đăng ký) và mô hình giới hạn tần suất (`../../../global/3.technical/rate-limiting-layers.md`):

- **Danh sách luật giới hạn tần suất theo đường dẫn** ở cổng, nạp từ thuộc tính lúc khởi động, mỗi luật gồm đường dẫn, phương
  thức, mức, khoảng thời gian, bật hay tắt; có luật mặc định dự phòng (300 yêu cầu/60 giây mỗi IP).
- **Luật đầu tiên:** `POST /auth/register`, 10 yêu cầu mỗi giờ mỗi địa chỉ mạng. Vượt thì 429 kèm `Retry-After` và `code`
  `TOO_MANY_REQUESTS`; hết thời gian chờ thì đăng ký lại được.
- **Địa chỉ mạng do cổng xác định**, không tin `X-Forwarded-For` do client tự gửi.
- Redis lỗi thì mở cửa và ghi cảnh báo; lệnh đếm có thời gian chờ ngắn.
- Kiểm chứng: 429 đúng thân lỗi, luật theo đường dẫn không chặn nhầm đường dẫn khác, hết thời gian chờ thì cho qua.

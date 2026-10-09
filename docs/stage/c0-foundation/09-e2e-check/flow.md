# Feature 09: Kiểm đầu-cuối cả chặng

> **Trạng thái:** chưa thiết kế · **Chặng:** C0 · **Loại:** Kỹ thuật
>
> **Nguồn:** Roadmap C0, `stage.md` mục 4 · **Kiểm chứng bằng:** Chạy hết S1 đến S5 qua cổng trên ứng dụng thật

Việc dựng, chạy và gọi API thật của từng chức năng thuộc **bước "Chạy thật" trong mỗi feature**, không thuộc feature này.
Feature này chạy một lần ở cuối chặng, sau khi 01 đến 08 xong: ghép các lời gọi thành kịch bản đầu-cuối S1 đến S5 (script kịch
bản ở `scenarios/`), chạy qua cổng, và kiểm kết quả. Mục đích là bắt lỗi ở chỗ nối giữa các feature mà từng feature riêng không thấy.

Tài liệu của feature này (luồng, kế hoạch, phần hoãn, chọn giải pháp kỹ thuật, đáp ứng NFR) đặt trong thư mục này khi
bắt đầu thiết kế.

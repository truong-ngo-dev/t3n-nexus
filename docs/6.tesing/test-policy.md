# Chính sách test: test bao nhiêu, lúc nào

> Bản tạm, chốt `2026-11-22`. Áp cho mọi feature, cả backend lẫn giao diện. Kế hoạch kiểm tích hợp, kiểm tải cũ ở
> `global/testing-plan.md` chưa được viết lại.

## 1. Nguyên tắc

- **Test theo thời điểm.** Đang làm một phần của tính năng thì chỉ test đủ để biết phần đó chạy. Đủ tính năng mới test đầy đủ.
- **Test thứ mắt không thấy.** Lỗi lộ ngay khi mở ứng dụng thì không cần spec riêng. Ví dụ: sai nút, sai nhãn, sai câu chữ, sai
  chỗ hiện lỗi.
- **Chạy tương xứng với thay đổi.**
  - Sửa tệp nào thì chạy spec của tệp đó.
  - Chuyển tệp, đổi tên, đổi import mà logic không đổi: chỉ cần biên dịch.
  - Chỉ chạy cả bộ của mô-đun khi đóng một phần hoặc trước khi commit.

## 2. Hai thời điểm

| Lúc                               | Viết test gì                                                                                        | Chạy thế nào                                         |
|-----------------------------------|-----------------------------------------------------------------------------------------------------|------------------------------------------------------|
| **Đang làm một phần** của feature | 1. **Thông luồng:** một ca đi hết đường chính của phần đó.<br>2. **Ca trọng yếu** (mục 3).<br>3. **Hàm thuần chứa quy tắc:** spec viết cùng lúc với hàm | Spec của tệp đang sửa; cả bộ của mô-đun một lần khi xong phần |
| **Đóng feature**                  | Phần còn lại của `test.md`: các ca lỗi, đủ các luồng qua trình duyệt giả, một lần chạy thật qua cổng | Cả bộ một lần; chạy thật một lần                     |

## 3. Ca trọng yếu

Ca trọng yếu là ca mà nếu sai thì **hỏng dữ liệu**, hoặc **lỗi không thấy được khi chạy thử bằng tay**.

| Loại                              | Ví dụ giao diện                                                  | Ví dụ backend                                              |
|-----------------------------------|------------------------------------------------------------------|------------------------------------------------------------|
| Không rõ đã ghi chưa              | Hết giờ, 504 thì không cho gửi lại mù                            | Ghi hộp thư cùng giao dịch, mất một thì mất cả hai         |
| Gửi trùng, nhận lặp               | Bấm đúp chỉ tạo một lời gọi                                      | Bên nhận event khử trùng                                   |
| Lỗi bị hiểu nhầm thành trạng thái | 5xx không được thành "chưa có hồ sơ"                             | Lỗi hạ tầng không thành mã nghiệp vụ                       |
| Đúng phiên bản, đúng bản đang xem | Quyết định gửi kèm phiên bản của bản đang hiện                   | Khóa lạc quan, khóa dòng                                   |
| Đồng thời, sai thứ tự             | Kết quả trang cũ về sau không đè trang mới                       | Đua nhau trên cùng một dòng; thứ tự event theo thực thể    |
| Lộ dữ liệu nhạy cảm               | Số căn cước không vào URL, console                               | Log, event, thông báo lỗi không mang số căn cước           |

## 4. Phá thử

Phá thử là cố ý làm sai mã để chắc rằng test bắt được lỗi.

- **Chỉ cho ca kỹ thuật:** đồng thời, sai thứ tự, nhận lặp, giao dịch. Không phá thử ca nghiệp vụ. Code đã phải đúng logic; ca
  lỗi và đường chính là việc của test thường.
- **Chỉ khi đóng feature,** chọn vài ca quan trọng nhất. Chạy đúng lớp test liên quan, không chạy cả mô-đun cho mỗi lỗi.
- Dừng giữa chừng thì kiểm mã nguồn đã khôi phục (`git diff`).

## 5. Viết `test.md` của feature

Mỗi ca có cột **"Khi"**, ghi một trong hai giá trị:
- `phần`: làm lúc làm phần đó;
- `đóng`: để tới lúc đóng feature.

Mục phá thử chỉ liệt kê ca kỹ thuật.

```
| #   | Ca                                                     | Bảo vệ    | Khi    |
|-----|--------------------------------------------------------|-----------|--------|
| K3  | Sửa khi chờ duyệt, lưu: PUT đủ thân, về trang xem      | srs 2     | phần   |
| K6  | Hết giờ: giữ khóa nút, không gửi lần hai               | Q6        | phần   |
| K11 | Đúng nút theo từng trạng thái                          | srs 2     | đóng   |
```

## 6. Chạy thật

- Qua cổng, trên ngăn xếp cách ly. Không chạm DB dev.
- Một lần khi đóng feature, hoặc khi cần chứng minh tích hợp mà test không làm được. Ví dụ: chữ ký token, Kafka thật, dừng rồi
  chạy lại connector.

# Feature 10: phần hoãn

| Việc                                                                   | Giá trị / vì sao xếp sau                                                                       | Làm ở                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| Chặn vai sai ngay ở BFF (`USER` vào Vận hành)                          | Hiện service từ chối (mặc định từ chối); chặn ở BFF cho giao diện biết sớm, không thêm an toàn | Cổng Vận hành khi có nhiều vai nội bộ (`00-features.md` dòng 12) |
| Đặt tên đoạn đường dẫn tuyến riêng (thay `/api/seller/seller/profile`) | Chỉ thẩm mỹ; làm khi giao diện thấy vướng                                                      | Khi viết giao diện Người bán                                     |
| Phá thử riêng từng điểm tham số hóa của thư viện BFF                   | Bộ test mỗi BFF đã phủ; chứng minh đỏ cho từng điểm chưa làm                                   | Khi sửa lớn thư viện BFF                                         |

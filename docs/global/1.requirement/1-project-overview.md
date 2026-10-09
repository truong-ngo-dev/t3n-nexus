# Giới thiệu dự án

## 1. Động cơ

Dự án xuất phát từ mong muốn được trải nghiệm thực tế một hệ thống có quy mô lớn hơn những gì công việc hiện tại có
điều kiện cho phép, với dữ liệu lớn và lượng truy cập đồng thời cao, để nâng cao khả năng bản thân.

Từ đó suy ra:

1. **Làm thật, đo thật.** Trải nghiệm phải có bằng chứng và giải thích được, không chỉ thiết kế trên giấy.
2. **Quy mô là tiền đề thiết kế.** Hệ thống được thiết kế như thể vận hành ở quy mô lớn, dù không có người dùng thật.
3. **Hai chiều quy mô.** Dữ liệu lớn và đồng thời cao là hai bài toán khác nhau, cần bằng chứng khác nhau.
4. **Nghiệp vụ là phương tiện.** Cần một nghiệp vụ đủ thật để sinh ra bài toán và đủ khép kín để chạy được. Nghiệp vụ chỉ
   cần đúng và đủ để ra quyết định thiết kế, không cần đầy đủ tính năng.
5. **Khả năng là khả năng kỹ thuật.** Hệ thống lớn đòi hỏi chuẩn cao hơn với cái đã biết (chặt chẽ, lần vết được khi có
   lỗi, chủ động đánh giá chi phí tài nguyên ngay khi thiết kế) và kỹ thuật mới với cái chưa biết. Ngoài ra được chủ
   động chọn công nghệ muốn học, kể cả vì giá trị thị trường.
6. **Ưu tiên khi xung đột.** Chiều sâu kỹ thuật được ưu tiên hơn độ rộng nghiệp vụ.

## 2. Đề tài

Một **marketplace thương mại điện tử giao dịch** kiểu Shopee: nhiều người bán và nhiều người mua gặp nhau trên một
nền tảng; nền tảng không sở hữu hàng mà đứng ra bảo đảm giao dịch (thanh toán, giao nhận, hậu mãi). Giá trị cốt lõi là
giúp người xa lạ giao dịch được với nhau.

- Shopee Việt Nam là khuôn mẫu về **quy tắc nghiệp vụ**, không phải bản sao danh sách tính năng. Khi nghi ngờ thì theo
  quy tắc nghiệp vụ của hệ thống thật.
- Một thị trường: Việt Nam.

## 3. Dấu hiệu đạt

Dự án không có hạn chót, nên đây là cơ chế dừng. Chỉ ghi loại bằng chứng; con số cụ thể xác định ở giai đoạn phân
tích yêu cầu phi chức năng.

**Mức đủ để dừng:**

| Dấu hiệu                                                                                                                     | Suy ra từ |
|------------------------------------------------------------------------------------------------------------------------------|-----------|
| Chạy được từ đầu đến cuối ít nhất một kịch bản mua hàng chính, qua giao diện ở mức ổn (nhất quán, dễ đọc, bố cục theo luồng) | Ý 4       |
| Có bằng chứng đo được cho chiều đồng thời cao ở ít nhất một điểm nóng                                                        | Ý 1, 3    |
| Có bằng chứng đo được cho chiều dữ liệu lớn ở ít nhất một điểm                                                               | Ý 1, 3    |
| Mỗi quyết định kỹ thuật lớn giải thích được lý do, kể cả khi bị hỏi xoáy                                                     | Ý 1, 5    |

**Mức mở rộng:** làm thêm điểm nóng hoặc năng lực khác khi còn hứng. Không bắt buộc.

## 4. Giả định và ngoài mục tiêu

| Dòng                                                                                     | Dạng           | Gốc    |
|------------------------------------------------------------------------------------------|----------------|--------|
| Quy mô lớn là tiền đề thiết kế, tải là giả lập                                           | Giả định       | Ý 2    |
| Không có người dùng thật, không có tiền thật                                             | Giả định       | Ý 2    |
| Không cần độ rộng tính năng ngang Shopee                                                 | Ngoài mục tiêu | Ý 4, 6 |
| Không đào sâu nghiệp vụ hơn mức đủ để ra quyết định thiết kế                             | Ngoài mục tiêu | Ý 4    |
| Bên thanh toán dùng môi trường thử nghiệm do bên cung cấp, ranh giới rõ để thay thế được | Giả định       | Ý 1, 2 |
| Giao diện chỉ cần ở mức ổn; không tinh chỉnh thẩm mỹ, hiệu ứng, trải nghiệm vượt mức đó  | Ngoài mục tiêu | Ý 5    |

## 5. Nguồn lực

| Loại          | Giá trị                                                                                                                    |
|---------------|----------------------------------------------------------------------------------------------------------------------------|
| Thời gian     | 2–4 giờ mỗi ngày, ổn định                                                                                                  |
| Nhân lực      | Một người, có AI agent hỗ trợ                                                                                              |
| Kiến thức nền | Backend vững. Frontend: biết viết nhưng chưa hiểu sâu, lâu không dùng. Triển khai lên môi trường thật: chưa có kinh nghiệm |

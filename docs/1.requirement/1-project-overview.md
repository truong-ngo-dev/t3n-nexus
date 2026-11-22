# Giới thiệu dự án

## 1. Mục đích

Dự án là một hệ thống thương mại điện tử dựng để làm **tài liệu tham chiếu trong CV**: chứng minh bằng sản phẩm chạy được, và
có số đo ở nơi đo được, những kỹ năng mà một hệ thống lớn đòi hỏi. Mỗi kỹ năng nêu ra phải có bằng chứng, và giải thích được lý do
chọn khi bị hỏi xoáy.

Dự án đồng thời là nơi học: những kỹ thuật đã biết nhưng chưa đi sâu là đối tượng chính của T1 và T4 (mục 2). Phần đang học vẫn
phải ghi đúng mức đã chứng minh được, không trình bày điều mới học như điều đã thành thạo.

Mọi quyết định về phạm vi, nghiệp vụ và công nghệ ở các tài liệu sau được suy ra từ mục đích này.

## 2. Bốn tiêu chí thể hiện

Bốn tiêu chí là gốc của mọi tài liệu sau. Tài liệu nào trích nguồn thì ghi mã (T1 đến T4).

| Mã     | Tiêu chí                                                                                                                                     | Bằng chứng thì coi là đã thể hiện                                                                                                                                                                                                                             | Đủ để dừng khi                                                                                                                                      |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **T1** | Áp dụng được nhiều công nghệ và mẫu thiết kế, mỗi cái vào đúng bài toán của nó                                                               | Mỗi kỹ thuật (ví dụ saga, outbox, CDC, đẩy thời gian thực) gắn với một bài toán thật trong hệ thống, chạy được, giải thích được                                                                                                                               | Mỗi kỹ thuật trong danh sách chốt ở phân tích nghiệp vụ có bài toán và bằng chứng                                                                   |
| **T2** | Xử lý được bài toán mà hệ thống lớn gặp, ở cả hai chiều quy mô: đồng thời cao và dữ liệu lớn                                                 | Các bài toán (ví dụ tranh chấp đồng thời, thanh toán với bên ngoài, xử lý lặp, truy vấn và báo cáo trên dữ liệu lớn) có kịch bản chạy lại được, kèm kiểm tra tính đúng                                                                                        | Mỗi bài toán trong danh sách có kịch bản và kiểm tra tính đúng đạt; có ít nhất một điểm đo cho chiều đồng thời và một điểm đo cho chiều dữ liệu lớn |
| **T3** | Thiết kế để mở rộng được                                                                                                                     | Ranh giới và cách tách thành phần (ví dụ DDD, CQRS) giải thích được bằng bài toán cụ thể; có phần chạy tách riêng được                                                                                                                                        | Mỗi quyết định thiết kế lớn nêu được vì sao, và có ít nhất một phần chứng minh được khi chạy riêng                                                  |
| **T4** | Có trải nghiệm làm frontend của hệ thống lớn: xử lý được những vấn đề mà hệ thống phân tán, bất đồng bộ và nhiều ứng dụng đẩy sang giao diện | Mỗi vấn đề frontend trong danh sách (ví dụ dữ liệu đến bất đồng bộ, lỗi từng phần, gửi trùng và thử lại an toàn, cập nhật thời gian thực, nhiều ứng dụng dùng chung nền đăng nhập và quyền) gắn với một bài toán thật của luồng, chạy được và giải thích được | Kịch bản mua hàng chính chạy được qua giao diện; mỗi vấn đề frontend trong danh sách chốt ở phân tích nghiệp vụ có bài toán và giải thích           |

Con số cụ thể (bao nhiêu kỹ thuật, bao nhiêu tải) không đặt ở đây: số kỹ thuật và danh sách vấn đề frontend chốt ở tài liệu
phân tích nghiệp vụ, số tải ở tài liệu phi chức năng.

## 3. Đề tài

**Marketplace thương mại điện tử giao dịch**: nhiều người bán và nhiều người mua gặp nhau trên một nền tảng; nền tảng không
sở hữu hàng mà đứng ra bảo đảm giao dịch (thanh toán, giao nhận, hậu mãi).

Một đề tài hợp khi thỏa cả ba điều:

| Điều kiện                                           | Thương mại điện tử đáp ứng ra sao                                                                                          | Phục vụ                           |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| Sinh được nhiều loại bài toán khác nhau             | Luồng nhiều bước giữa nhiều bên; tranh chấp tồn kho và voucher; thanh toán bên ngoài; thông báo; chat; tìm kiếm            | T1, T2                            |
| Khép kín đủ để chạy từ đầu đến cuối                 | Có đủ vòng đời: đăng hàng, đặt, thanh toán, giao, hậu mãi                                                                  | T2, T3, T4                        |
| Có quy tắc nghiệp vụ sẵn để tham chiếu, khỏi tự bịa | Các sàn lớn (Shopee, Tiki, Amazon...) là nguồn tham chiếu về **quy tắc nghiệp vụ**, không phải bản sao danh sách tính năng | Sàn "nghiệp vụ phải đúng" (mục 5) |

Không gắn với một sàn cụ thể. Khi các sàn làm khác nhau thì chọn cách phù hợp với bài toán kỹ thuật cần sinh ra và ghi rõ lấy
từ sàn nào. Phạm vi địa lý (một thị trường hay nhiều) và các quy tắc đặc thù (thuế, đơn vị vận chuyển, tiền tệ) quyết ở tài liệu
phân tích nghiệp vụ.

Nghiệp vụ là phương tiện để sinh bài toán kỹ thuật, không phải đích.

## 4. Ràng buộc

Những thứ cố định, quyết định giới hạn của bằng chứng.

| Ràng buộc                                                                                                 | Hệ quả                                                                                                                                         |
|-----------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Một người làm, có AI agent hỗ trợ; 2–4 giờ mỗi ngày                                                       | Phải cắt phạm vi; mỗi thứ thêm vào phải đáng với thời gian đó                                                                                  |
| Backend có nền tảng vững: đã làm tối ưu và xử lý dữ liệu ở quy mô vừa; chưa có cơ hội làm hệ thống cỡ lớn | Khoảng cách cần lấp là **quy mô**, không phải kiến thức nền; chiều sâu kỹ thuật dồn vào backend ở các điểm mà quy mô lớn làm cách đơn giản sụp |
| Frontend: từng làm sản phẩm kiểu dạng project cá nhân, chưa có trải nghiệm của hệ thống lớn               | Frontend là nơi học, nên làm theo hướng **có chỉ dẫn nhiều hơn** (giao đề bài, gợi ý theo bậc, rà lại) thay vì chỉ giao làm                    |
| Hạ tầng chỉ có máy local (cloud đánh giá sau)                                                             | Bằng chứng bị giới hạn bởi một máy; điều gì vượt giới hạn phải nói rõ là chưa đo                                                               |
| Không có người dùng thật, không có tiền thật                                                              | Tải là giả lập, phải có hình dạng thật                                                                                                         |
| Bên thanh toán dùng môi trường thử nghiệm của bên cung cấp                                                | Ranh giới với bên thanh toán phải rõ để thay thế được                                                                                          |

## 5. Quy tắc ưu tiên khi xung đột

| Quy tắc                                                                                                                                                                                          | Gốc    |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| **Chiều sâu kỹ thuật thắng độ rộng nghiệp vụ.** Hai sàn không được hạ: nghiệp vụ phải đúng (không cần đầy đủ), và sản phẩm phải chạy được từ đầu đến cuối                                        | T1, T2 |
| **Giao diện tối giản nhưng không được vỡ.** Theo Material, không cầu kỳ; bố cục phải hợp lý (các ô nhập cùng hàng đều nhau, khoảng cách nhất quán, không có ô sát nhau hoặc lệch cỡ không chủ ý) | T4     |
| **Bằng chứng thắng lời khẳng định.** Điều chưa đo không được trình bày như đã đo; ghi rõ đã chứng minh tới đâu                                                                                   | Mục 1  |
| **Kỹ thuật vào dự án vì có bài toán thật, không vì thấy hay.** Dùng một kỹ thuật thì phải nói được "nó giải bài toán nào ở đây"                                                                  | T1     |
| **Ít mà sâu thắng nhiều mà nông.** Một kỹ thuật chỉ tính cho T1 khi có bài toán thật và bằng chứng; chỉ thêm kỹ thuật mới khi các kỹ thuật đã có đã đạt mức đó                                   | T1, T2 |
| **Thiết kế như thể vận hành ở quy mô lớn**, dù tải thật chỉ chứng minh được ở mức máy local cho phép                                                                                             | T2, T3 |

## 6. Ngoài mục tiêu

| Dòng                                                           | Lý do                                                               |
|----------------------------------------------------------------|---------------------------------------------------------------------|
| Độ rộng tính năng ngang sàn thật                               | Không phục vụ T1–T4; tốn thời gian                                  |
| Đào sâu nghiệp vụ hơn mức đủ để sinh bài toán và đúng quy tắc  | Nghiệp vụ là phương tiện                                            |
| Vận hành thật: người dùng thật, tiền thật, mục tiêu uptime     | Ràng buộc mục 4; chỉ đo khả năng phục hồi, không cam kết            |
| Giao diện cầu kỳ: hiệu ứng, tinh chỉnh thẩm mỹ, thiết kế riêng | Mục tiêu T4 là kỹ năng frontend, không phải đồ họa; mức sàn ở mục 5 |

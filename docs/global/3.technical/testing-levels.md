# Các cấp kiểm thử: bản chất và lý do

> Ghi lại **vì sao** có nhiều loại test và mỗi loại chứng minh điều gì. Đây là ghi chú giải thích, không phải quy ước bắt buộc; khi nào và ai viết test ở `4.convention/work-process.md` mục 6.

## 1. Test để làm gì

Mã "trông đúng" và mã "đúng" khác nhau. Mỗi test là một giả thuyết có thể sai: "với đầu vào này, hệ thống phải ra kết quả này". Một test chỉ có giá trị khi nó **có khả năng đỏ**. Test không bao giờ đỏ thì xanh nghĩa là gì cũng được.

## 2. Vì sao phải có nhiều cấp

Lỗi sống ở những chỗ khác nhau. Mỗi cấp nhìn thấy một loại lỗi mà cấp khác không thấy. Đổi lại, cấp càng gần thật càng chậm, càng đắt và càng khó chỉ ra nguyên nhân.

| | Đơn vị | Tích hợp một dịch vụ | Chạy thật nhiều dịch vụ |
|---|---|---|---|
| Phần phụ thuộc | Giả hoặc không có | Cơ sở dữ liệu, Kafka thật chạy tạm; dịch vụ khác giả | Không giả gì |
| Tốc độ | Mili giây | Vài giây đến vài chục giây | Vài phút |
| Khi đỏ, tìm lỗi | Dễ: chỉ một lớp | Vừa: trong một dịch vụ | Khó: nhiều tiến trình |
| Lỗi nó thấy | Logic, quy tắc | Chỗ nối: SQL, giao dịch, ràng buộc, cấu hình | Chỗ nối giữa dịch vụ, sự cố, khởi động lại, độ trễ |

Vì cấp thấp nhanh và rẻ nên viết nhiều ở đó; cấp cao đắt nên chỉ dùng cho điều cấp thấp không chứng minh được.

## 3. Cấp 1: đơn vị

Gọi một hàm với dữ liệu cho sẵn rồi so kết quả. Đơn giản vì không có gì ngoài hàm đó. Chứng minh được **logic đúng**. Không chứng minh được hàm đó **nối đúng với thế giới thật**: cũng là lý do cần cấp 2.

## 4. Cấp 2: tích hợp

**Vì sao giả (mock) không thay được hạ tầng thật.** Giả lặp lại hiểu biết của người viết test. Cơ sở dữ liệu thật có hành vi người viết không nghĩ tới: ràng buộc duy nhất từ chối bản ghi trùng, rollback xóa mọi thứ trong giao dịch, kiểu cột cắt độ chính xác của thời gian. Một test giả cơ sở dữ liệu xanh vì giả làm đúng điều người viết tin, không vì cơ sở dữ liệu thật làm đúng.

**Ví dụ: "rollback không để lại hàng hộp thư".** Chỉ chứng minh được khi giao dịch thật tồn tại và rollback thật. Giả giao dịch thì test chỉ kiểm giả của mình.

**Testcontainers là gì và vì sao dùng.** Nó khởi động một cơ sở dữ liệu thật trong Docker chỉ cho lần chạy test rồi xóa. Được ba thứ: hành vi giống thật, cô lập (không đụng dữ liệu của môi trường dev), lặp lại được (mỗi lần bắt đầu từ trạng thái trống).

**`@SpringBootTest` chạy cái gì.** Nó dựng **một ngữ cảnh ứng dụng**: mọi bean mà lớp cấu hình được chọn kéo vào, nối với nhau như khi dịch vụ chạy thật. Nó **không phải** "một nhánh" của mã. "Nhánh" là do mình chọn lớp cấu hình lớn hay nhỏ:
- lớp ứng dụng thật: toàn bộ dịch vụ;
- một ứng dụng tối thiểu tự viết: chỉ phần cần kiểm (ví dụ chỉ JPA và hộp thư);
- chỉ vài bean tự khai báo: kiểm điều kiện tạo bean.

Lý do dựng cả ngữ cảnh: nhiều lỗi nằm ở **cách các phần được nối** (thiếu bean, sai cấu hình, entity lệch bảng), mà từng lớp riêng lẻ không có lỗi. Chỉ khi chúng được nối thật mới lộ ra.

**Vì sao dịch vụ khác vẫn giả.** Cấp này kiểm **một dịch vụ**. Dịch vụ bên kia là ranh giới; thay nó bằng một máy chủ giả có hợp đồng cố định để lỗi chỉ có thể do dịch vụ đang kiểm. Hai dịch vụ nói chuyện đúng với nhau thì chứng minh ở cấp 3.

**Kafka trong tiến trình (EmbeddedKafka).** Cùng ý như Testcontainers nhưng không cần Docker: một Kafka thật nhỏ chạy ngay trong tiến trình test, để kiểm bộ nhận event (thử lại, hàng chờ lỗi, thứ tự) với hành vi của Kafka thật.

**Đọc kết quả từ hạ tầng, không từ giá trị trả về.** Nếu chỉ tin vào giá trị trả về của chính mã đang kiểm thì test chứng minh mã nhất quán với chính nó. Đếm hàng bằng câu `SELECT` hay đọc chủ đề lỗi bằng một bên nhận riêng thì chứng minh trạng thái thật.

## 5. Hai loại test đặc biệt ở cấp 2

**Hợp đồng hai chiều.** Bên ghi và bên đọc của cùng một định dạng dễ lệch nhau (một bên đổi tên trường), mà test riêng của từng bên đều xanh. Test dùng cả hai bên cùng lúc (bên đọc đọc đúng cái bên ghi vừa ghi) bắt được lệch đó.

**Đồng thời.** Lỗi đua chỉ lộ khi nhiều luồng chạy cùng lúc, mà một luồng thì luôn đúng. Test cho nhiều luồng cùng thao tác trên cùng dữ liệu rồi kiểm kết quả: trọng tài đúng là ràng buộc ở cơ sở dữ liệu, không phải việc kiểm trước trong mã.

## 6. Cấp 3: chạy thật nhiều dịch vụ

Một số lỗi chỉ lộ khi các tiến trình thật chạy cùng nhau: khởi động lại giữa chừng, mất kết nối rồi nối lại, độ trễ thật, cấu hình giữa các thành phần. Ví dụ ở hộp thư event: thông điệp bị bọc thêm lớp schema do cấu hình mức máy chủ, hay hàng ghi trước lúc connector tồn tại bị sót. Không test ở cấp 2 nào thấy được, vì chúng chỉ tồn tại khi Kafka Connect thật chạy.

Không chạy mỗi lần vì chậm và nhiều phần chuyển động, nên chỉ chạy ở những lần kiểm có ý nghĩa (cuối feature, cuối chặng).

Hai điều làm phép chạy thật đáng tin:
- **Đối chứng:** dừng một thành phần rồi kiểm bên nhận không nhận gì, để biết phép đo nhạy.
- **Cắt ngang phải chứng minh đã cắt ngang:** khi thử khởi động lại giữa chừng, ghi số mục đã xử lý lúc cắt và thấy nó nằm giữa 0 và tổng. Nếu cắt khi mọi thứ đã xong thì ca xanh mà chưa thử gì.

## 7. Phá thử: kiểm chính các test

Phá thử **kiểm test**, không kiểm mã: cố ý sửa mã cho sai (bỏ một kiểm tra, đổi `<` thành `<=`), chạy test liên quan, và mong thấy **đỏ**. Thấy xanh nghĩa là test đó không bắt được lỗi.

Không thể viết thành một test case vì nó phải sửa mã sản phẩm rồi chạy lại cả bộ test; một test không tự sửa mã của chính nó được. Đây cũng là lý do test **xanh ngay lần đầu** đáng nghi: có thể mã đúng, có thể test yếu, và chỉ phá thử phân biệt được hai khả năng đó. Công cụ tự động (kiểm thử đột biến như PIT) làm việc này ở quy mô lớn.

## 8. Chọn cấp nào cho một hành vi

Chọn **cấp thấp nhất còn bắt được lỗi cần bắt**.

| Lỗi muốn bắt | Cấp |
|---|---|
| Sai quy tắc, sai công thức, sai điều kiện | 1 |
| Sai SQL, ràng buộc, giao dịch, ánh xạ entity | 2 (Postgres thật) |
| Bộ nhận event thử lại, hàng chờ lỗi, thứ tự | 2 (Kafka thật) |
| Bean không được tạo, cấu hình sai điều kiện | 2 (ngữ cảnh nhỏ) |
| Bên ghi và bên đọc lệch hợp đồng | 2 (test hai chiều) |
| Race condition, ghi đôi | 2 (nhiều luồng) |
| Khởi động lại, mất kết nối, cấu hình giữa thành phần, độ trễ | 3 |

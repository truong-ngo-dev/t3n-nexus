# Yêu cầu phi chức năng

> **Bản nháp để duyệt.** Số liệu sàn thật kèm nguồn như đã ghi, chưa kiểm lại nguồn trong lần viết này.

## 1. File này để làm gì

`2-business-analysis.md` đã nêu các bài toán hệ thống lớn mà dự án muốn thể hiện (mục 3.2 của file đó): nhiều người tranh một
mặt hàng, thanh toán bên ngoài, tìm kiếm trên hàng triệu sản phẩm... Nhưng "nhiều" là bao nhiêu, "nhanh" là nhanh tới đâu, và
làm sao chứng minh được trên một máy cá nhân thì chưa ai nói. File này nói điều đó.

Cách đọc: mỗi bài toán ở mục 3 là một đoạn đọc riêng được, trả lời năm câu theo cùng một thứ tự:

- **Chuyện gì xảy ra:** tình huống thật mà người dùng gặp.
- **Lớn cỡ nào:** con số thiết kế, và nó được tính ra từ đâu.
- **Đúng nghĩa là gì:** điều không bao giờ được sai, kể cả khi tải cao.
- **Nhanh nghĩa là gì:** ngưỡng độ trễ, nếu có.
- **Chứng minh thế nào:** đo được trực tiếp hay phải dùng cách khác, vì máy có giới hạn.

Trước mục 3 là bức tranh chung của sàn mà ta tưởng tượng (mục 2), để các con số trong từng bài toán có chung một gốc. Sau mục 3 là
giới hạn của máy và cách nói trung thực về những gì vượt giới hạn (mục 4), các yêu cầu chung cho mọi phần (mục 5), và thế nào thì
một số đo được tính là bằng chứng (mục 6).

Gốc ở overview: T2 (bài toán hệ thống lớn ở hai chiều quy mô), T3 (tách một phần ra chạy riêng được), quy tắc "thiết kế như thể
vận hành ở quy mô lớn" và "bằng chứng thắng lời khẳng định".

## 2. Sàn mà ta tưởng tượng

Dự án không có người dùng thật, nên phải tưởng tượng ra một sàn và đặt số cho nó. Sàn được chọn là **một sàn cỡ vừa ở Việt Nam,
cỡ Tiki**. Lấy cỡ Shopee thì số lớn tới mức không có cách nào chứng minh; lấy nhỏ hơn thì các bài toán không còn xuất hiện.

Hình dung một ngày của sàn này. Có khoảng **1 triệu lượt truy cập** (Tiki công bố 33,7 triệu lượt mỗi tháng năm 2019). Khoảng 5%
lượt truy cập dẫn tới một đơn, nên có khoảng **50.000 đơn mỗi ngày**; con số này cũng gần với ước tính từ doanh thu của Tiki. Mỗi
lượt truy cập kéo dài chừng 5 phút, nên lúc cao điểm có khoảng **20.000 người trên sàn cùng lúc**, mỗi người vài giây lại xem một
trang, tạo ra khoảng **8.000 yêu cầu đọc mỗi giây**.

Trên sàn có khoảng **5 triệu sản phẩm** (Tiki có hơn 10 triệu, lấy một nửa cho sàn cỡ vừa) và **3 triệu tài khoản**. Sau một năm, sàn
tích lũy khoảng **18 triệu đơn**.

Rồi có những ngày flash sale. Tiki nói lưu lượng lúc đó tăng khoảng 10 lần, và họ nhận "vài nghìn đơn trong một phút". Vậy trong
vài phút, sàn có khoảng **200.000 người cùng lúc** và khoảng **100 đơn mỗi giây**.

Các con số trên mang một trong ba nhãn, để biết nên tin tới đâu:

- **Tương đương:** gần một số có nguồn của sàn thật.
- **Suy ra:** tính từ số khác; các giả định dùng để tính được ghi ngay cạnh.
- **Giả định:** không có căn cứ trực tiếp, có lý do hợp lý, đổi được mà không làm đổi thiết kế.

| Đại lượng                                 | Số               | Nhãn        | Cách có                                                           |
|-------------------------------------------|------------------|-------------|-------------------------------------------------------------------|
| Lượt truy cập mỗi ngày                    | 1 triệu          | Tương đương | Tiki 33,7 triệu mỗi tháng                                         |
| Đơn mỗi ngày                              | 50.000           | Tương đương | 5% lượt truy cập; gần ước tính từ doanh thu Tiki                  |
| Người cùng lúc, thường / flash sale       | 20.000 / 200.000 | Suy ra      | Phiên 5 phút, cao điểm gấp 3 trung bình; flash sale gấp 10        |
| Yêu cầu đọc mỗi giây, thường / flash sale | 8.000 / 80.000   | Suy ra      | Mỗi người một yêu cầu mỗi 2,5 giây                                |
| Đơn mỗi giây lúc flash sale               | 100              | Tương đương | "Vài nghìn đơn mỗi phút"                                          |
| Sản phẩm                                  | 5 triệu          | Tương đương | Một nửa của Tiki                                                  |
| Tài khoản                                 | 3 triệu          | Suy ra      | 1 triệu người mua hoạt động mỗi tháng, nhân 3 vì có tài khoản ngủ |
| Đơn tích lũy một năm                      | 18 triệu         | Suy ra      | 50.000 × 365                                                      |

Nguồn: lượt truy cập Tiki 7/2019 (thống kê lưu lượng, qua tìm kiếm); flash sale và số sản phẩm Tiki (case study Google Cloud);
không tìm được số đơn thật mỗi ngày của sàn Việt Nam nào, nên số đơn là ước tính.

## 3. Từng bài toán

### 3.1 Nhiều người tranh một mặt hàng

**Chuyện gì xảy ra.** Đúng 12 giờ, một mặt hàng flash sale mở bán với 1.000 suất. Hàng nghìn người bấm "mua" trong cùng vài giây,
và tất cả cùng muốn trừ một con số tồn kho.

**Lớn cỡ nào.** Toàn sàn có 100 đơn thành công mỗi giây. Nhưng với mỗi người mua được có nhiều người thử mà trượt: nếu mỗi đơn
thành công kèm 10 lần thử, cộng hệ số đỉnh 2, thì một mặt hàng nhận khoảng **2.000 yêu cầu mỗi giây**, tất cả dồn vào đúng một chỗ
(suy ra; hai hệ số 10 và 2 là giả định). Với 1.000 suất (giả định, sàn không công bố), hàng hết sau khoảng 10 giây. Để biết hệ thống
gãy ở đâu, sẽ thử tăng tới **8.000 yêu cầu mỗi giây**; đây là mức để thử, không phải mục tiêu.

**Đúng nghĩa là gì.** Không bao giờ bán vượt: sau đợt bán, số đã giữ chỗ cộng số còn lại phải bằng đúng số ban đầu. Người mua trả
trực tuyến hay COD thì đơn đều đi theo "giữ chỗ trước, thanh toán sau", nên thao tác tranh nhau chỉ là trừ một suất; không được giữ
khóa trong lúc người dùng thanh toán. Với thanh toán trực tuyến, giữ chỗ quá hạn mà chưa trả phải được nhả về để người khác mua.

**Nhanh nghĩa là gì.** Người bấm mua biết mình được hay trượt trong **200 ms** ở P99 (giả định; ngưỡng phổ biến cho thao tác ghi
nhỏ mà người dùng đang chờ).

**Chứng minh thế nào.** Đo trực tiếp ở đúng 2.000 yêu cầu mỗi giây bằng kiểm chứng theo lát cắt (mục 4.3): chỉ chạy phần tồn kho và
khuyến mãi, phần còn lại là bản giả. Mức này dự kiến nằm trong khả năng của máy, nhưng chưa đo. Không dùng đo theo tỷ lệ được, vì
mọi yêu cầu tranh cùng một chỗ: thêm tài nguyên không làm chỗ đó nhanh hơn.

### 3.2 Suất có giới hạn theo người

**Chuyện gì xảy ra.** Voucher chỉ có 1.000 lượt dùng; mỗi người chỉ được mua tối đa 2 suất flash sale. Với COD còn tệ hơn: đặt xong
là chốt luôn, không có bước thanh toán để lọc, nên một người (hoặc một bot) có thể đặt ảo để chiếm hết suất, và suất bị giữ tới khi
giao thất bại, tức là sau khi đợt sale đã qua.

**Lớn cỡ nào.** Cùng tải với 3.1, vì xảy ra trong cùng đợt flash sale.

**Đúng nghĩa là gì.** Không cấp quá số suất; mỗi người không vượt giới hạn của mình, kể cả khi gửi nhiều yêu cầu cùng lúc từ nhiều
thiết bị.

**Chứng minh thế nào.** Trong cùng kịch bản lát cắt của 3.1, thêm một nhóm người giả lập cố tình đặt nhiều lần, rồi đếm suất theo
người sau khi chạy.

### 3.3 Đọc dồn lúc flash sale

**Chuyện gì xảy ra.** Trước giờ mở bán, 200.000 người mở trang sản phẩm và tải lại liên tục.

**Lớn cỡ nào.** Khoảng **80.000 yêu cầu đọc mỗi giây** (suy ra từ mục 2).

**Nhanh nghĩa là gì.** Trang chi tiết sản phẩm trong **100 ms** ở P99 (giả định; thao tác nhiều nhất, phải gần như tức thì).

**Chứng minh thế nào.** Con số này vượt sức một máy (mục 4.1), nên không đo trực tiếp được. Dùng đo theo tỷ lệ (mục 4.3) và chứng
minh mở rộng ngang; kết quả mang nhãn "suy ra". Phần lớn lượng đọc phải được bộ đệm hấp thụ, nên đây cũng là nơi thể hiện việc
chống dồn khi bộ đệm hết hạn.

### 3.4 Làn sóng đăng nhập

**Chuyện gì xảy ra.** Vài phút trước flash sale, rất nhiều người phải đăng nhập lại. Mỗi lần đăng nhập phải băm mật khẩu, một việc
cố ý tốn CPU để chống dò mật khẩu.

**Lớn cỡ nào.** Ngày thường chỉ khoảng **5 lần mỗi giây**. Trước flash sale: nếu 20% trong 200.000 người phải đăng nhập lại trong
một phút, là khoảng **700 lần mỗi giây** (giả định cả hai tỷ lệ).

**Chứng minh thế nào.** Một máy băm được khoảng 227 mật khẩu mỗi giây (mục 4.1), nên 700 vượt sức một máy. Dùng chứng minh mở rộng
ngang: đo với 1, 2, 4 bản dịch vụ đăng nhập xem thông lượng có tăng tương ứng không. Đây cũng là chỗ giới hạn tần suất phải bảo vệ
hệ thống khỏi bị đánh sập.

### 3.5 Thanh toán bên ngoài và gửi lặp

**Chuyện gì xảy ra.** Bên thanh toán báo kết quả về sau vài giây hoặc vài phút, có lúc báo hai lần, có lúc báo trễ sau khi đơn đã
bị hủy vì quá hạn. Phía người dùng, mạng rớt đúng lúc bấm đặt hàng nên họ bấm lại.

**Lớn cỡ nào.** Tối đa bằng số đơn: 100 mỗi giây lúc flash sale. Bài toán ở đây không phải độ lớn mà là **thứ tự và sự lặp lại**.

**Đúng nghĩa là gì.** Một yêu cầu lặp không bao giờ tạo hai kết quả: bấm đặt hai lần ra một đơn; nhận kết quả thanh toán hai lần
thì xử lý một lần. Kết quả đến trễ cho một đơn đã hủy phải được xử lý đúng (ví dụ hoàn tiền), không được làm đơn sống lại.

**Nhanh nghĩa là gì.** Phần phản hồi ngay cho người dùng khi bắt đầu thanh toán trong **2 giây** ở P99 (giả định; có bên ngoài
tham gia nên người dùng chấp nhận chờ hơn).

**Chứng minh thế nào.** Không cần tải lớn. Kịch bản cố ý gửi lặp, gửi trễ, gửi sai thứ tự, rồi đếm kết quả.

### 3.6 Nhất quán giữa các phần

**Chuyện gì xảy ra.** Một đơn đi qua nhiều phần: giữ tồn kho, thanh toán, người bán xác nhận, giao hàng. Các phần không chung một
giao dịch cơ sở dữ liệu, nên có lúc một phần đã xong mà phần sau hỏng.

**Đúng nghĩa là gì.** Mọi đơn cuối cùng phải dừng ở một trạng thái hợp lý, khớp với tồn kho và tiền: không có đơn treo mãi ở giữa
chừng, không có suất bị giữ mà không thuộc đơn nào, không có tiền đã thu mà đơn đã hủy chưa hoàn.

**Chứng minh thế nào.** Cố ý tắt một phần giữa chừng trong lúc chạy tải, bật lại, rồi kiểm tra không còn đơn treo hay suất mồ côi.

### 3.7 Nhiều kết nối thời gian thực

**Chuyện gì xảy ra.** Người dùng đang mở ứng dụng nhận thông báo và trạng thái đơn ngay khi có, không phải tải lại. Mỗi người đang
mở là một kết nối được giữ mở.

**Lớn cỡ nào.** Khoảng **20.000 kết nối** lúc thường và tới **200.000** lúc flash sale nếu ai cũng mở (suy ra từ mục 2).

**Nhanh nghĩa là gì.** Thông báo tới người dùng trong **1 giây** (giả định; đủ để cảm giác "ngay lúc đó").

**Chứng minh thế nào.** Giữ nhiều kết nối mở thì rẻ hơn nhiều so với xử lý nhiều yêu cầu, nên có thể thử trực tiếp một số lượng lớn
kết nối trên một máy. Báo cáo phải nói rõ đang đo số kết nối giữ được hay số thông báo gửi được mỗi giây.

### 3.8 Tìm kiếm trên hàng triệu sản phẩm

**Chuyện gì xảy ra.** Người mua gõ từ khóa, lọc theo giá, thương hiệu, còn hàng, rồi đổi bộ lọc liên tục.

**Lớn cỡ nào.** **5 triệu sản phẩm**; 8.000 yêu cầu đọc mỗi giây lúc thường, một phần trong đó là tìm kiếm.

**Nhanh nghĩa là gì.** Tìm kiếm có lọc trong **200 ms** ở P99 (giả định; chậm hơn thì cảm giác giật khi đổi bộ lọc).

**Chứng minh thế nào.** Đo trực tiếp với 3 đến 5 triệu sản phẩm, đủ đĩa trên một máy. Điều kiện bắt buộc: bộ nhớ cấp cho máy tìm
kiếm phải **nhỏ hơn** dữ liệu. Nếu toàn bộ dữ liệu nằm gọn trong bộ nhớ thì truy vấn vẫn nhanh dù thiết kế kém, và số đo không còn
nói gì về dữ liệu lớn.

### 3.9 Lịch sử đơn trên bảng lớn

**Chuyện gì xảy ra.** Người bán mở danh sách đơn của mình, lọc theo trạng thái, lật tới trang thứ 50.

**Lớn cỡ nào.** **18 triệu đơn** sau một năm; đo với khoảng 10 triệu.

**Nhanh nghĩa là gì.** Một trang danh sách trong **200 ms** ở P99, kể cả trang sâu (giả định). Đây là chỗ cách phân trang đơn giản
(bỏ qua N dòng đầu) bắt đầu chậm dần theo số trang.

**Chứng minh thế nào.** Đo trực tiếp, so sánh trang đầu với trang sâu, trước và sau khi đổi cách phân trang. Cùng điều kiện bộ nhớ
nhỏ hơn dữ liệu như 3.8.

### 3.10 Báo cáo trên dữ liệu lớn

**Chuyện gì xảy ra.** Người bán xem doanh thu và số đơn theo ngày trong ba tháng qua.

**Lớn cỡ nào.** Cũng trên 18 triệu đơn.

**Nhanh nghĩa là gì.** Hai thứ: báo cáo mở ra trong **2 giây** (giả định), và số liệu trên báo cáo không trễ quá **vài phút** so
với thực tế (giả định). Hai thứ này kéo nhau: tính trước thì nhanh nhưng trễ, tính lúc mở thì mới nhưng chậm.

**Chứng minh thế nào.** Đo trực tiếp. So sánh truy vấn thẳng trên bảng đơn với cách tổng hợp trước, ghi rõ đổi lấy bao nhiêu độ trễ
của số liệu.

### 3.11 Dữ liệu tăng theo thời gian

**Chuyện gì xảy ra.** Có những bảng chỉ thêm, không bao giờ xóa: đơn hàng, lịch sử đăng nhập. Sau một năm, lịch sử đăng nhập có
khoảng **60 triệu dòng** (giả định: khoảng 165.000 lần đăng nhập mỗi ngày, giữ 12 tháng).

**Đúng nghĩa là gì.** Truy vấn dữ liệu gần đây không chậm đi khi dữ liệu cũ tăng lên; dữ liệu quá hạn được dọn hoặc chuyển đi theo
quy tắc, không phải bằng tay.

**Chứng minh thế nào.** Đo cùng một truy vấn trên dữ liệu một tháng và mười hai tháng, trước và sau khi chia bảng theo thời gian.

### 3.12 Bài toán của nghiệp vụ mở rộng

Nghiệp vụ mở rộng mang theo con số riêng, chỉ tính khi làm tới nó. Ví dụ "Kho và vận hành giao hàng": khoảng 2.000 người giao, mỗi
người gửi vị trí 5 giây một lần trong ca 8 giờ, là khoảng **400 điểm vị trí mỗi giây**, hơn 11 triệu dòng mỗi ngày, và vị trí phải tới
người mua trong 500 ms. Chat, theo dõi hành vi, sổ cái chưa tính.

## 4. Máy có giới hạn: nói trung thực về phần vượt quá

### 4.1 Máy làm được tới đâu

Không đoán sức máy. Trước khi đo ứng dụng, đo những thao tác đơn giản nhất có thể: một yêu cầu không làm gì, ghi một dòng, băm một
mật khẩu. Kết quả là **trần của máy**: ứng dụng thật không thể nhanh hơn. Khi đo ứng dụng, phần chênh so với trần là phần do mã
của mình gây ra.

Đã đo được (laptop 8 nhân 16 luồng, 31 GB RAM; mỗi thành phần trong container 4 CPU; giá trị giữa của ba lần; cách chạy lại ở
`stage/c0-foundation/08-baseline-bench/tech.md`):

- Khoảng **65.000 yêu cầu đọc mỗi giây**. Đủ cho 8.000 lúc thường, không đủ cho 80.000 lúc flash sale (bài toán 3.3).
- Khoảng **11.000 lần ghi một dòng mỗi giây**. Dư nhiều so với 100 đơn mỗi giây: ghi đơn không phải chỗ nghẽn.
- Khoảng **227 lần băm mật khẩu mỗi giây**. Đủ cho ngày thường, không đủ cho làn sóng đăng nhập (bài toán 3.4).

Máy sinh tải chạy chung máy với hệ thống, nên hai bên tranh CPU. Mọi số đo đều chịu điều này và phải ghi rõ.

### 4.2 Ba mức chứng minh

Mỗi con số trong báo cáo nói rõ nó được chứng minh tới đâu:

- **Đã đo:** có số đo thật, kèm điều kiện đo.
- **Suy ra:** đo ở mức nhỏ hơn, cộng một lý lẽ cho thấy vì sao kết quả giữ được ở mức lớn hơn.
- **Chỉ thiết kế:** mã được viết cho mức đó nhưng chưa đo.

Không bao giờ viết một số "chỉ thiết kế" như số đã đo.

### 4.3 Ba cách đi xa hơn trần máy

**Đo theo tỷ lệ.** Thu nhỏ tài nguyên và tải cùng một tỷ lệ: cấp 1/10 CPU, chạy 1/10 tải. Nếu vẫn đạt ngưỡng, có cơ sở tin rằng 10
lần tài nguyên chịu được 10 lần tải. Chỉ đúng khi chỗ nghẽn tăng theo tài nguyên; không dùng cho mặt hàng nóng (3.1), vì mọi yêu cầu
tranh một chỗ.

**Chứng minh mở rộng ngang.** Chạy 1, 2, rồi 4 bản của cùng một dịch vụ, mỗi bản một nhân cố định, đo thông lượng tối đa. Tăng gần
tương ứng thì thiết kế mở rộng được; đứng yên thì có thứ dùng chung đang chặn, và tìm ra thứ đó là một kết quả có giá trị. Máy có đủ
nhân cho 4 bản hay không thì chưa kiểm.

**Kiểm chứng theo lát cắt.** Có phần của hệ thống tách ra chạy riêng được. Chỉ bật đúng phần đó, cả máy dành cho nó, phần khác thay
bằng bản giả, thì số đo có thể đạt đúng mức thiết kế của chính phần đó (ví dụ 3.1). Để trung thực:

- Nêu phạm vi cùng con số: "2.000 yêu cầu mỗi giây ở phần giữ chỗ flash sale, các phần khác là bản giả".
- Ghi lại phần nào là bản giả và bản giả hành xử ra sao.
- Đúng trước, nhanh sau: các điều kiện "đúng nghĩa là gì" của bài toán phải đạt trước, rồi mới tính tốc độ.

Tách được một phần ra chạy riêng cũng là bằng chứng cho T3: phần nào ranh giới chưa sạch thì không tách ra được.

## 5. Yêu cầu chung cho mọi phần

- **Lỗi không bị giấu.** Mọi nơi, kể cả giao diện, phân biệt lỗi do người dùng (nhập sai, hết hàng) và lỗi do hệ thống (thành phần
  hỏng), và xử lý khác nhau.
- **Lần được dấu vết của một đơn.** Một mã truy vết đi theo yêu cầu qua log và sự kiện; tìm toàn bộ dấu vết của một đơn bằng một
  truy vấn.
- **Mất phần phụ không làm sập luồng mua.** Bộ đệm hay máy tìm kiếm hỏng thì đặt hàng vẫn chạy và phục hồi được. Kiểm bằng cách cố ý
  tắt phần đó trong lúc đo.
- **Mọi nơi nhận sự kiện chịu được nhận lặp.**
- **Không đặt mục tiêu thời gian hoạt động.** Không có vận hành thật nên con số đó không có nghĩa; chỉ đo thời gian phục hồi sau lỗi.

## 6. Một số đo được tính là bằng chứng khi nào

Một số đo được đưa vào báo cáo khi trả lời được: chạy lại bằng lệnh nào, trên phiên bản mã nào; tải có hình dạng ra sao (bao nhiêu
luồng, phân bố thế nào, chạy bao lâu, làm nóng bao lâu); điều kiện máy (CPU, RAM từng thành phần, kích thước dữ liệu, máy sinh tải
có chạy chung không); kết quả (thông lượng, P50, P95, P99, tỷ lệ lỗi) **kèm kiểm tra "đúng nghĩa là gì"** của bài toán; so với cái
gì (trước và sau một quyết định thiết kế, hoặc điểm gãy); lặp ít nhất ba lần và báo khoảng, không báo lần tốt nhất; mức chứng minh
(mục 4.2); và quyết định nào tạo ra khác biệt.

Kịch bản theo lát cắt nêu thêm ở đầu: bài toán nó chứng minh; phần nào chạy thật, phần nào là bản giả; mô hình tải; tiêu chí đạt;
chạy ở đâu.

Nếu không đạt vì máy sinh tải tranh CPU, phải chứng minh bằng số đo CPU, không tự nới ngưỡng.

## 7. Cloud

Cloud là **giả thuyết cần đánh giá**, chưa phải cam kết. Chỉ đáng dùng khi một máy không đủ và các cách ở mục 4.3 không thay được,
ví dụ kiểm lại một kết quả "suy ra" bằng một hai điểm đo thật ở quy mô lớn hơn. Nếu dùng: chỉ bật phần cần thiết, chạy ngắn rồi tắt.

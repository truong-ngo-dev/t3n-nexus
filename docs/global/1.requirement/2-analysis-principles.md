# Nguyên tắc phân tích nghiệp vụ

## 1. Tài liệu này giải quyết vấn đề gì

Từ `1-project-overview.md`, ta phải ra được các quyết định có lý do: hệ thống gồm những nghiệp vụ nào, chia thành những
bounded context (BC) nào, mỗi BC làm nghiệp vụ nào và bỏ nghiệp vụ nào, làm sâu đến đâu, và thế nào thì gọi là khép
kín. Không có cách phân tích chung thì mỗi quyết định là cảm tính, scope trôi, và không biết khi nào thì đủ.

Tài liệu và code đã có từ trước không dùng làm đầu vào của phân tích; chỉ đối chiếu sau khi phân tích xong, để điều chỉnh
phần đã có theo kết quả, không để phần đã có quyết định kết quả.

Tài liệu đi theo hướng **DDD**: từ yêu cầu xác định BC, rồi trong mỗi BC chọn nghiệp vụ và mức triển khai. Mỗi mục là
một **vấn đề** cần giải, theo cùng một khuôn:

> Vấn đề → Cách phân tích → Tiêu chí chọn → Điều kiện dừng → Hai mức → Đầu ra → Ví dụ → Gốc

Hai ngoại lệ về nhãn: P1 dùng "Đưa gì vào danh sách" và "Chi tiết đến đâu" thay cho "Tiêu chí chọn" và "Điều kiện
dừng"; P6 gộp điều kiện dừng và đầu ra.

**Gốc** nói nguyên tắc suy ra từ phần nào của overview (Ý 1–6 của mục Động cơ, Đề tài, Dấu hiệu đạt, Giả định và ngoài
mục tiêu, Nguồn lực), hoặc là phương pháp mượn (DDD). Nguyên tắc không chỉ ra được gốc thì không có trong tài liệu này.

## 2. Dòng phân tích và hai giai đoạn

| #  | Vấn đề                                                     | Đầu ra                                    |
|----|------------------------------------------------------------|-------------------------------------------|
| P1 | Đề tài rộng vô hạn, làm sao biết đã nhìn đủ?               | Bản đồ nghiệp vụ                          |
| P2 | Làm sao chia hệ thống thành các phần độc lập mà không rối? | Danh sách BC và bản đồ quan hệ            |
| P3 | Trong một BC, làm nghiệp vụ nào, bỏ nghiệp vụ nào?         | Bảng năng lực kèm trạng thái              |
| P4 | Cắt trong từng BC có làm hở chỗ nối giữa các BC không?     | Danh sách chỗ lệch và bản đồ quan hệ cuối |
| P5 | Không thể đào sâu mọi thứ, đào sâu chỗ nào?                | Danh sách điểm sâu và mức triển khai      |
| P6 | Khi thêm hoặc đổi thì sao?                                 | Backlog và quy trình rà lại               |

Thứ tự: **vẽ ranh giới BC từ toàn bộ bản đồ nghiệp vụ (P2), rồi mới cắt trong từng BC (P3)**, sau đó đối soát liên BC
(P4). Ranh giới không phụ thuộc scope, nên scope đổi mà ranh giới vẫn bền; và điểm nối của phần cắt tạm chính là ranh
giới BC. Hai quy tắc xuyên suốt (chứng cứ, ưu tiên khi xung đột) ở mục 9.

Phân tích chạy theo **hai giai đoạn** với mức chi tiết khác nhau, cùng một nguyên tắc: chi tiết đến mức đủ để ra quyết
định của giai đoạn đó.

| Giai đoạn                                          | Làm gì                                                                                           | Quyết định cần ra                                             | Mức chi tiết |
|----------------------------------------------------|--------------------------------------------------------------------------------------------------|---------------------------------------------------------------|--------------|
| **Chiến lược** (một lần, toàn cục)                 | Chia BC và bản đồ quan hệ; mỗi BC nêu tên nghiệp vụ, trạng thái và lý do chọn. Sau đó xếp thứ tự triển khai | Chia BC, phạm vi và lý do chọn của từng BC, thứ tự triển khai | Thô: chỉ tên |
| **Theo bước** (mỗi bước triển khai, các BC liên quan) | Phân tích chi tiết để ra feature                                                                 | Feature, ràng buộc, đường không suôn sẻ                       | Mịn          |

Mỗi mục P1–P6 bên dưới có phần **Hai mức** nói rõ việc nào làm ở giai đoạn nào. Ranh giới BC được chốt trọn ở giai đoạn
chiến lược vì khó sửa; phần còn lại có thể mịn dần theo bước.

## 3. P1. Bản đồ nghiệp vụ

**Vấn đề.** "Kiểu Shopee" là cả một hệ sinh thái; không có mốc thì "đầy đủ" không có điểm dừng, mà cắt trước khi nhìn thì
không biết mình bỏ gì.

**Cách phân tích.** Quét đề tài theo năm việc của giá trị cốt lõi (giúp người xa lạ giao dịch được với nhau), liệt kê
mọi nghiệp vụ phục vụ ít nhất một việc, **chưa cắt gì**:

| Việc                | Ý nghĩa                                      |
|---------------------|----------------------------------------------|
| Khám phá            | Người mua tìm được hàng, người bán được thấy |
| Tin cậy             | Hai bên xa lạ tin nhau đủ để giao dịch       |
| Thực hiện giao dịch | Đặt, thanh toán, giao nhận                   |
| Sau giao dịch       | Hoàn trả, đánh giá, khiếu nại                |
| Vận hành nền tảng   | Điều hành, kiểm soát, đối soát               |

**Đưa gì vào danh sách.** Phục vụ một trong năm việc thì đưa vào. Không thuộc năm việc (ví dụ trò chơi, vay tiêu dùng)
là ngoài đề tài. Nghiệp vụ ở rìa (ví dụ quảng cáo của người bán, phục vụ khám phá gián tiếp) vẫn được nêu ra, và đi
tiếp qua P3. Chỉ cần gắn với một việc ở mức gián tiếp là đủ. Giá trị kỹ thuật không thay thế được liên hệ nghiệp vụ; nó
chỉ được xét ở P3 và P5, sau khi nghiệp vụ đã vào danh sách.

**Chi tiết đến đâu.** Chi tiết đến mức đủ để **ra hoặc giải thích một quyết định thiết kế**, không hơn. Đã đủ khi mọi
quyết định ở các bước sau trích được một lý do nghiệp vụ; quá mức khi chi tiết thêm vào mà không quyết định hay giải
thích được thiết kế nào.

**Hai mức.** Chiến lược: chỉ tên nghiệp vụ theo năm việc; không cần tài liệu riêng, có thể là một phần của tài liệu chia
BC. Theo bước: bổ sung chi tiết cho nghiệp vụ của BC đang làm.

**Đầu ra.** Bản đồ nghiệp vụ: danh sách nghiệp vụ nhóm theo năm việc, mỗi nghiệp vụ kèm nguồn tham chiếu (mục 9).

**Ví dụ.** Việc "Thực hiện giao dịch" liệt ra: đặt hàng, giữ chỗ tồn kho, thanh toán, đóng gói, giao nhận, đối soát
tiền thu hộ... Chưa nói cái nào làm.

**Gốc.** Đề tài; Ý 1 (làm thật, có bằng chứng); Ý 4 (nghiệp vụ đủ thật); Giả định và ngoài mục tiêu (không đào sâu quá
mức).

## 4. P2. Chia bounded context

**Vấn đề.** Cần chia hệ thống thành các phần độc lập, mà ranh giới phải **bền khi scope đổi**, vì ranh giới đắt để sửa
(dữ liệu, event, tài liệu).

**Cách phân tích.** Vẽ ranh giới từ **toàn bộ** bản đồ nghiệp vụ, không loại trừ phần sẽ bị cắt. *(Phương pháp mượn: DDD.)*

**Tiêu chí chọn.** Hai nghiệp vụ cùng BC khi:
- Dùng chung một thuật ngữ cùng nghĩa (cùng từ mà nghĩa khác nhau thì tách).
- Có ràng buộc cần nhất quán mạnh trong một giao dịch.
- Thay đổi vì cùng một lý do.

Tách BC vì **lý do kỹ thuật** (đặc tính tải khác hẳn, hoặc BC không bắt buộc về nghiệp vụ như chat, thông báo, lập lịch,
giả lập) chỉ được khi ghi rõ điểm sâu nó phục vụ (P5). Không tách chỉ vì thấy hay.

Quan hệ giữa BC chỉ có hai dạng: **event** hoặc **giao diện** rõ ràng, có hướng.

**Năng lực xuyên BC** có một **BC chủ**: nơi lệnh khởi tạo và trạng thái chính nằm. Các BC còn lại tham gia qua event.

**Điều kiện dừng.**
- Mỗi năng lực trong bản đồ có đúng một BC chủ.
- Mỗi ràng buộc cần nhất quán mạnh nằm trọn trong một BC.
- Mọi quan hệ giữa BC có hướng và có dạng (event hay giao diện).

BC chỉ có phần bị cắt thì chỉ ghi tên, trách nhiệm, điểm nối. Không phân tích sâu.

**Hai mức.** Chiến lược: làm trọn ở đây vì ranh giới khó sửa. Lúc này chỉ có tên nghiệp vụ, nên mỗi BC ghi thêm vài
**ràng buộc chính** (chỉ tên) để kiểm tiêu chí "ràng buộc cần nhất quán mạnh nằm trong một BC". Theo bước: chỉ rà lại;
nếu chi tiết cho thấy ranh giới sai thì sửa và ghi nhận lý do thay đổi.

**Đầu ra.** Danh sách BC (trách nhiệm, ranh giới, lý do tách, BC chủ của năng lực nào) và bản đồ quan hệ.

**Ví dụ.** "Số lượng còn bán được" và "vị trí hàng vật lý trong kho" là hai thuật ngữ và hai bộ ràng buộc khác nhau, nên
là hai BC dù cùng nói về hàng tồn. Kể cả khi kho vật lý bị hoãn, BC tồn kho vẫn phải chừa chỗ cho nó cắm vào.

**Gốc.** Phương pháp (DDD); Ý 5 (BC chọn vì lý do kỹ thuật phải gắn điểm sâu); Ý 3 (hai chiều quy mô).

## 5. P3. Chọn năng lực trong một BC

**Vấn đề.** Không làm hết được, nhưng làm dở một nghiệp vụ thì không chạy được. Cần biết làm cái nào, bỏ cái nào, và
thế nào thì một nghiệp vụ gọi là đủ để đưa vào.

**Cách phân tích.** Đơn vị là **năng lực**. Với mỗi năng lực của BC:
1. Kiểm phần đóng bằng sáu câu (bảng dưới). Phần đóng nằm ở BC khác thì ghi là qua event hoặc giao diện nào.
2. Xếp một trong ba trạng thái.

| # | Câu hỏi                       | Ví dụ với "duyệt sản phẩm"                                                           |
|---|-------------------------------|--------------------------------------------------------------------------------------|
| 1 | Ai khởi tạo?                  | Người bán gửi yêu cầu duyệt                                                             |
| 2 | Ai xử lý, theo quy trình nào? | Quản trị duyệt hoặc từ chối, kèm lý do                                                  |
| 3 | Ai phải được báo, báo gì?     | Người bán nhận kết quả                                                                  |
| 4 | Đường không suôn sẻ là gì?    | Bị từ chối thì sửa và gửi lại; quá hạn không ai duyệt; sửa sản phẩm đã duyệt thì sao |
| 5 | Ai xem lại được lịch sử?      | Quản trị và người bán xem ai duyệt, lúc nào, vì sao                                        |
| 6 | Dữ liệu chảy sang đâu?        | Tìm kiếm chỉ nhận sản phẩm đã duyệt                                                    |

| Trạng thái        | Ý nghĩa            | Phải ghi                                                                               |
|-------------------|--------------------|----------------------------------------------------------------------------------------|
| **Trong scope**   | Vào đủ phần đóng   | Sáu câu đã trả lời                                                                     |
| **Cắt tạm**       | Đã phân tích, hoãn | Cái gì bị cắt; điểm nối (event hay giao diện nào) khi quay lại; cần thêm gì để đưa lại |
| **Ngoài phạm vi** | Không làm          | Lý do và điều kiện để xem xét lại                                                      |

**Hai nghĩa của "khép kín", không được lẫn:**

| Tầng              | Câu hỏi                                          | Giải quyết ở |
|-------------------|--------------------------------------------------|--------------|
| BC khép kín       | BC sở hữu những gì, tương tác với BC khác ra sao | P2           |
| Năng lực khép kín | Nghiệp vụ cần những ai, bước nào để chạy trọn    | P3 (sáu câu) |

**Hai mức.**
- Chiến lược: mỗi BC liệt kê **tên** nghiệp vụ, xếp ba trạng thái, ghi lý do chọn. Khép kín kiểm ở mức tên: nghiệp vụ được
  giữ thì các nghiệp vụ nó cần (bên xử lý, thông báo kết quả, chỗ xem lịch sử) phải có mặt trong danh sách, kể cả ở BC
  khác. Đây là bản rút gọn của sáu câu. Mức cắt ở giai đoạn này là **tên năng lực**, và chỉ ghi các chỗ cắt có ảnh
  hưởng ranh giới, BC khác hoặc thứ tự triển khai (kèm lý do, chỗ cắm, điều kiện kéo vào). Cắt chỉ ảnh hưởng nội bộ một BC, hoặc cắt
  từng tính năng, thì để đến lúc làm chi tiết theo bước.
- Theo bước: trả lời sáu câu đầy đủ, ghi ràng buộc và đường không suôn sẻ, ra feature. Điều kiện dừng và đầu ra bên dưới
  áp dụng cho mức này.

**Tiêu chí chọn.** Giữ một năng lực khi có **một trong hai lý do**: (a) nó cần để nghiệp vụ **đủ thật để sinh bài toán và đủ khép kín để chạy được**; (b) nó phục vụ một điểm sâu (P5), kể cả điểm sâu **chủ động chọn** do người làm nêu. Chỉ xếp **cắt tạm** khi không có cả hai lý do, hoặc chi phí vượt khả năng. Lý do (b) cần người làm xác nhận, không suy ra được từ nghiệp vụ.
Không cần đầy đủ tính năng. Khi xung đột, áp dụng quy tắc ưu tiên (mục 9). "Tối giản" chỉ nói về độ rộng nghiệp vụ
(phần đóng có mặt nhưng đơn giản, ví dụ thông báo từ chối chỉ là một email); **không có nghĩa là làm ẩu về kỹ thuật**.

**Điều kiện dừng.**
- Mọi năng lực của BC đã được xếp trạng thái.
- Năng lực trong scope đã trả lời đủ sáu câu, đặc biệt câu 4 (đường không suôn sẻ), nơi thường lộ khoảng hở.
- Chi phí của năng lực được hiểu là chi phí của **cả phần đóng**; không làm dở.

**Đầu ra.** Bảng năng lực của BC: trạng thái, phần đóng, điểm nối nếu cắt tạm, và các ràng buộc (INV) cùng đường
không suôn sẻ để làm đầu vào cho phần bảo vệ khi triển khai.

**Ví dụ.** "Duyệt sản phẩm": nếu đưa vào, kéo theo trạng thái chờ duyệt, màn hình quản trị, thông báo từ chối, luồng gửi
lại, và Tìm kiếm chỉ nhận sản phẩm đã duyệt. Nếu cắt tạm thì ghi điểm nối: "sản phẩm tạo xong là hiển thị; khi bật duyệt,
thêm trạng thái chờ duyệt trước khi phát event công khai".

**Gốc.** Ý 4 (nghiệp vụ đủ khép kín để chạy); Ý 5 (giữ vì điểm sâu chủ động chọn); Dấu hiệu đạt (kịch bản mua hàng chạy trọn); Nguồn lực (một người, thời
gian hạn chế buộc phải cắt).

## 6. P4. Đối soát liên BC

**Vấn đề.** Cắt trong từng BC làm riêng rẽ có thể để lại chỗ hở ở nơi các BC gặp nhau: event không ai phát, năng lực
trong scope thiếu phần đóng ở BC khác.

**Cách phân tích.** Sau khi mọi BC đã xếp xong năng lực, kiểm ba điều trên bản đồ quan hệ:
1. Mọi event được tiêu thụ có bên phát trong scope, hoặc có bản thay thế đơn giản.
2. Mọi năng lực trong scope có phần đóng nằm ở BC khác đều ở trong scope, hoặc có thay thế đơn giản.
3. Mọi phần cắt tạm giữ nguyên điểm nối: không BC nào trong scope phụ thuộc vào thứ bị cắt trừ qua điểm nối.

**Hai mức.** Chiến lược: bản nhẹ, chỉ tên event cùng bên phát và bên nhận. Theo bước: bản đầy đủ cho các BC trong bước, rồi
cập nhật bản đồ quan hệ.

**Điều kiện dừng.** Cả ba kiểm tra đạt, hoặc chỗ lệch còn lại được ghi và có quyết định xử lý.

**Đầu ra.** Danh sách chỗ lệch cần xử lý và bản đồ quan hệ cuối.

**Ví dụ.** Thông báo có luồng "sản phẩm bị từ chối", nhưng duyệt sản phẩm bị cắt: bỏ luồng đó khỏi scope, hoặc giữ như
điểm nối chưa có bên phát (ghi rõ).

**Gốc.** Phương pháp (DDD); Ý 4 (chạy được từ đầu đến cuối).

## 7. P5. Độ sâu triển khai

**Vấn đề.** Không thể đào sâu mọi thứ. Cần biết chỗ nào đáng đầu tư kỹ thuật vượt mức bình thường.

**Cách phân tích.** "Điểm sâu" có hai nguồn, tiêu chí khác nhau:

| Nguồn                | Tiêu chí                                                                                                                                                                   | Ví dụ                                                |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| **Hệ thống đòi hỏi** | Cách làm đơn giản **sụp ở quy mô nào, vì sao**. Chỉ ra được thì là điểm sâu thật                                                                                           | Báo cáo: nối bảng gốc sụp, cần cơ chế tổng hợp riêng |
| **Chủ động chọn**    | Không cần cách khác sụp, nhưng phải thỏa ba điều: gắn nhãn "chủ động chọn" kèm lý do; chi phí thời gian chấp nhận được; không làm hệ thống kém vững hơn phương án đơn giản | Học một công nghệ vì giá trị thị trường              |

Mỗi điểm sâu thuộc một trong hai chiều quy mô: **dữ liệu lớn** hoặc **đồng thời cao**. Hai chiều cần bằng chứng khác
nhau, ghi rõ điểm sâu đó phục vụ chiều nào.

**Hai mức.** Chiến lược: gán gốc (đòi hỏi hay chủ động chọn) và chiều quy mô cho từng BC hoặc nghiệp vụ chính; đây là cột
"tại sao chọn" của tài liệu chia BC. Theo bước: chi tiết từng điểm sâu của BC trong bước.

**Điều kiện dừng.** Mỗi điểm sâu chỉ ra được gốc (đòi hỏi hay chủ động chọn) và chiều quy mô. Điểm sâu không chỉ ra được
gốc nào thì không phải điểm sâu, bỏ hoặc hạ xuống mức thử.

**Đầu ra.** Danh sách điểm sâu (gốc, chiều quy mô) và mức triển khai của từng năng lực (tối giản hay đầy đủ hơn).

**Ví dụ.** Giữ chỗ tồn kho khi nhiều người tranh một mặt hàng: cách đơn giản (đọc rồi trừ) sụp khi đồng thời cao, nên là
điểm sâu thuộc chiều đồng thời. Một thông báo đơn lẻ không là điểm sâu.

**Gốc.** Ý 2 (quy mô là tiền đề); Ý 3 (hai chiều quy mô); Ý 5 (hệ thống đòi hỏi và quyền chủ động chọn).

## 8. P6. Giữ kết luận sống

**Vấn đề.** Phạm vi sẽ nở ra theo cái học được, và overview có thể đổi. Không có cửa vào cố định thì scope trôi.

**Cách phân tích.** Năng lực mới (kể cả phần cắt tạm muốn đưa lại) đi qua cùng một cửa:
1. Trả lời đủ sáu câu của P3, nêu chi phí phần đóng.
2. Xếp trạng thái theo P3 và, nếu có điểm sâu, theo P5.
3. Ghi vào backlog. Chỉ nhận vào làm khi thứ tự triển khai có chỗ.

Lan truyền thay đổi: khi overview đổi, rà các nguyên tắc có gốc ở phần đó, rồi các kết quả phân tích phía dưới. Khi kết
quả phân tích mâu thuẫn một nguyên tắc, quay lại sửa nguyên tắc (và overview nếu cần), không chỉ sửa kết quả.

Tài liệu chia BC và thứ tự triển khai được cập nhật sau mỗi bước, vì chi tiết ở giai đoạn theo bước có thể làm đổi phạm vi hoặc
ước lượng.

**Điều kiện dừng và đầu ra.** Backlog có chi phí phần đóng ghi trước cho từng mục; danh sách tài liệu cần rà lại sau mỗi
lần overview đổi.

**Gốc.** Nguồn lực (thời gian hạn chế nên mỗi lần nở phải có giá được tính trước).

## 9. Quy tắc xuyên suốt

| Quy tắc                  | Vấn đề giải quyết                                                          | Nội dung                                                                                                                                                                                                                 | Gốc                                          |
|--------------------------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| **Chứng cứ nghiệp vụ**   | Quy tắc nghiệp vụ không có nguồn thì không giải thích được khi bị hỏi xoáy | Mỗi quy tắc quan trọng ghi nguồn và ngày truy cập; mức tin cậy (kiểm chứng từ trang chính thức, hay chỉ từ bản tóm tắt hoặc bài bên thứ ba); không có nguồn thì đánh dấu giả định của dự án, không trình bày như sự thật | Đề tài (Shopee là khuôn mẫu về quy tắc); Ý 1 |
| **Ưu tiên khi xung đột** | Độ rộng nghiệp vụ tranh nguồn lực với chiều sâu kỹ thuật                   | Chiều sâu kỹ thuật thắng, với hai ngưỡng sàn không được hạ: nghiệp vụ phải **đúng** (không cần đầy đủ); sản phẩm phải **chạy được** từ đầu đến cuối                                                                      | Ý 6; Ý 4                                     |

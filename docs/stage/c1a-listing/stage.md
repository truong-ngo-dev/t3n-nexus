# Chặng C1a: Niêm yết

Bước 1 của quy trình: phân tích nghiệp vụ cấp chặng. Nguồn: roadmap mục C1a, overview §2, context map, `5-ui-analysis.md`,
tổng kết chặng C0 ([`../c0-foundation/00-features.md`](../c0-foundation/00-features.md) §7).

Phạm vi của tài liệu này: chặng **cần gì** và **vì sao**, tới mức đủ để biết BC nào tham gia và thế nào là xong. Phân
tích chi tiết (mô hình hồ sơ, dữ liệu sản phẩm, INV, đường lỗi) giao cho `analysis.md` của BC. Mỗi phần kết thúc bằng một
dòng **Chốt**: kết luận và việc giao đi.

## 1. Yêu cầu của chặng

Roadmap yêu cầu C1a demo được việc: người bán đăng ký, được duyệt, đăng sản phẩm có ảnh; quản trị duyệt người bán và
hành động đó tra cứu được trong nhật ký. Đó là phát biểu về kết quả. Câu hỏi của phân tích là: vì sao sàn cần một bước
trước khi có hàng để bán, và để bước đó đúng thì nghiệp vụ phải làm những gì.

Chặng này mở đầu bằng hai chiều của một sàn: **ai được bán** (người bán) và **bán gì** (sản phẩm). C0 mới trả lời được
"người này là ai"; C1a trả lời "người này có được bán không" và "hàng của họ trông ra sao khi sẵn sàng bán".

## 2. Suy luận từ yêu cầu đến hành vi

**2.1. Vì sao người bán phải được duyệt.** Sàn đứng ra bảo đảm giao dịch (overview §2): người mua trả tiền cho nền tảng, tin
rằng người bán có thật và giao đúng hàng. Nếu ai có tài khoản cũng đăng bán ngay, nền tảng không truy ra được ai đứng sau một
gian hàng khi có tranh chấp hay vi phạm. Quy định về thương mại điện tử cũng yêu cầu sàn xác minh danh tính người bán trước
khi cho mở gian hàng, theo loại hình (cá nhân, hộ kinh doanh, doanh nghiệp). Vì vậy cần một **hồ sơ người bán** đi kèm bước
**duyệt** do nhân sự nền tảng quyết định.

Người bán không phải một vai mới của tài khoản. Tài khoản vẫn là tài khoản của C0; "đã là người bán" là việc tài khoản đó có
một hồ sơ được duyệt. Cách này giữ token chỉ mang `USER` hoặc `ADMIN`, và một người vừa mua vừa bán bằng một tài khoản.

> **Chốt:** cần BC Người bán giữ hồ sơ người bán và quy trình duyệt, gắn với mã tài khoản. → `analysis.md` của Người bán
> định nghĩa hồ sơ, loại hình, thông tin pháp lý khai báo, trạng thái và quy tắc duyệt.

**2.2. Hồ sơ thu gì, ở mức nào.** Để duyệt cần thông tin đủ để xác định và liên hệ một chủ thể: tên pháp lý, người đại diện, số
căn cước hoặc mã số thuế theo loại hình, địa chỉ kinh doanh, người liên hệ. Đính kèm giấy tờ (ảnh căn cước, giấy đăng ký)
cần chế độ lưu riêng tư cho tệp, nên tách thành mức sau. Ở C1a hồ sơ chỉ **khai báo**, không đính kèm; chừa chỗ cắm cho tệp.
Thông tin định danh là dữ liệu cá nhân nên không được xuất hiện trong log hay sự kiện.

> **Chốt:** hồ sơ mức khai báo, không tệp đính kèm. → `analysis.md` của Người bán ghi hồ sơ mức 1, điểm nối cho mức 2 và quy
> tắc che dữ liệu định danh.

**2.3. Hàng hóa phải có cấu trúc mới đăng được.** Người mua so sánh, lọc, chọn kích cỡ và màu; kho và đơn hàng cần biết đúng
một đơn vị bán được. Vì vậy sản phẩm không chỉ là tên và ảnh: nó thuộc một **danh mục**, có **thuộc tính** (trục tạo biến thể
như màu, kích cỡ; thông số mô tả), có thể có **thương hiệu**, và bán ra qua các **biến thể** mỗi cái có **giá niêm yết** riêng. Ảnh
là một phần điều kiện để người mua tin vào hàng. Sản phẩm có trạng thái nháp trước khi **đăng**, và có thể **gỡ**.

> **Chốt:** cần BC Danh mục hàng giữ danh mục, thuộc tính, thương hiệu, sản phẩm, biến thể, giá niêm yết, ảnh và trạng thái
> đăng. → `analysis.md` của Danh mục hàng định nghĩa các khái niệm này và điều kiện đăng.

**2.4. Ai làm việc gì, và vì sao Danh mục phải biết trạng thái người bán.** Chỉ người bán đã duyệt được đăng sản phẩm; chỉ chủ sản
phẩm sửa sản phẩm của mình; chỉ quản trị quản danh mục và thuộc tính (cấu trúc dùng chung của sàn). Điều đầu tiên đòi hỏi
Danh mục biết người bán đã duyệt chưa, nhưng hồ sơ thuộc Người bán. Người bán thông báo kết quả duyệt bằng **sự kiện**;
Danh mục giữ một bản sao trạng thái để kiểm khi đăng, không gọi sang Người bán mỗi lần (xem 3, luồng đăng sản phẩm). Hệ quả: có
một độ trễ ngắn giữa lúc duyệt và lúc đăng được; chấp nhận được vì duyệt là việc thủ công.

Phân quyền ở C1a dùng vai từ token cộng quyền sở hữu trong từng BC, mặc định từ chối. Cơ chế chính sách theo thuộc tính
(ABAC) và bản đồ quyền cho giao diện **hoãn có điểm nối** tới khi làm cổng Vận hành, nơi có nhiều vai nội bộ.

> **Chốt:** Danh mục dựng bản sao trạng thái người bán từ sự kiện; phân quyền C1a theo vai và chủ sở hữu. → `analysis.md` hai
> BC ghi các kiểm quyền; `service.md` ghi sự kiện người bán đã duyệt hoặc bị từ chối.

**2.5. Hành động quản trị phải để lại dấu vết.** Duyệt hay từ chối một người bán là quyết định có hậu quả pháp lý và tiền bạc.
Khi có tranh chấp, nền tảng cần trả lời được ai quyết định, lúc nào, với hồ sơ nào, và dấu vết đó không được sửa hay xóa. Dấu
vết này không thuộc BC nào trong nghiệp vụ (nhiều BC cùng cần), nên là một BC riêng: **Nhật ký kiểm toán**, chỉ ghi thêm,
không sửa. Ở C1a nó nhận hai nhóm hành động quản trị (duyệt, từ chối hồ sơ người bán; tạo, sửa, ngừng dùng danh mục, thuộc tính, thương hiệu)
và cho quản trị tra cứu; các loại khác (khóa và mở tài khoản, chặn sản phẩm) thêm ở C1b. Nhật ký chỉ giữ hành động có chủ thể
và có hậu quả; dấu vết tiền thuộc Sổ cái, lịch sử bảo mật của người dùng thuộc Xác thực. Nhật ký không chứa dữ liệu định danh của hồ sơ, chỉ mã tham chiếu.

> **Chốt:** cần BC Nhật ký kiểm toán tối giản: nhận, lưu chỉ-ghi-thêm, tra cứu. → `analysis.md` của Nhật ký kiểm toán định
> nghĩa bản ghi, tính bất biến và tra cứu. Người bán phát bản ghi bằng sự kiện cùng giao dịch với quyết định duyệt.

**2.6. Điều gì có thể sai.** Mỗi hành vi dưới đây cần một điều được bảo đảm. Phát biểu chính thức, kèm lý do, nằm ở
`analysis.md` dưới dạng INV.

| Tình huống                                                                                             | Điều cần bảo đảm                                                                        |
|--------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Một người nộp nhiều hồ sơ, hoặc hai hồ sơ cùng một chủ thể pháp lý (cùng số căn cước, cùng mã số thuế) | R1. Một tài khoản một hồ sơ; một chủ thể pháp lý chỉ có một hồ sơ còn hiệu lực          |
| Hai gian hàng trùng tên hoặc chỉ khác dấu, dễ gây nhầm                                                 | R2. Tên gian hàng duy nhất toàn sàn, so sánh không phân biệt dấu                        |
| Hồ sơ bị duyệt hai lần, hoặc từ chối rồi duyệt tiếp                                                    | R3. Mỗi hồ sơ đi theo trạng thái cho phép; duyệt và từ chối chỉ từ trạng thái chờ duyệt |
| Người bán sửa hồ sơ ngay lúc quản trị đang duyệt bản cũ                                                | R4. Quyết định duyệt áp lên đúng phiên bản hồ sơ mà quản trị đã xem                     |
| Từ chối không nói lý do, người bán không biết sửa gì                                                   | R5. Từ chối luôn kèm lý do                                                              |
| Người dùng thường tự duyệt hồ sơ, hoặc người bán sửa sản phẩm của người khác                           | R6. Duyệt chỉ cho `ADMIN`; sửa sản phẩm chỉ cho chủ sản phẩm; mặc định từ chối          |
| Người chưa được duyệt đăng bán                                                                         | R7. Chỉ người bán đã duyệt tạo và đăng sản phẩm                                         |
| Đăng sản phẩm thiếu ảnh, thiếu giá, thiếu thuộc tính bắt buộc, hoặc ở danh mục không còn dùng          | R8. Đăng chỉ khi đủ điều kiện đăng; lý do không đủ được báo rõ                          |
| Hai biến thể cùng tổ hợp thuộc tính; giá âm hoặc quá lớn                                               | R9. Mỗi tổ hợp biến thể duy nhất trong sản phẩm; giá là số nguyên đồng, dương, có trần  |
| Xóa danh mục hoặc thuộc tính đang có sản phẩm dùng                                                     | R10. Cái đã được dùng chỉ ngừng dùng, không xóa                                         |
| Số căn cước, mã số thuế lộ qua log, sự kiện hay danh sách                                              | R11. Dữ liệu định danh bị che ở log, vắng mặt ở sự kiện và danh sách                    |
| Quyết định duyệt xảy ra nhưng bản ghi kiểm toán mất (hoặc ngược lại)                                   | R12. Quyết định và bản ghi kiểm toán cùng thành công hoặc cùng không                    |
| Kẻ xấu hoặc sự cố sửa, xóa bản ghi kiểm toán                                                           | R13. Nhật ký kiểm toán chỉ ghi thêm                                                     |

> **Chốt:** mười ba điều cần bảo đảm. → `analysis.md` các BC nâng chúng thành INV kèm lý do và bổ sung đường lỗi chi tiết. INV là
> đầu vào của aggregate ở `service.md` (bước 3) và của test (bước 6).

## 3. Hành vi và BC

| Hành vi | BC | Vì sao ở BC đó |
|---|---|---|
| H1. Nộp hồ sơ người bán (kèm loại hình, thông tin pháp lý khai báo) | Người bán | Hồ sơ và vòng đời của nó thuộc Người bán |
| H2. Sửa và nộp lại hồ sơ (khi chờ duyệt hoặc sau khi bị từ chối); xem hồ sơ của mình | Người bán | Như trên |
| H3. Duyệt hoặc từ chối hồ sơ; xem danh sách và chi tiết hồ sơ chờ duyệt | Người bán | Quyết định về hồ sơ thuộc BC giữ hồ sơ |
| H4. Quản danh mục, thuộc tính, thương hiệu | Danh mục hàng | Cấu trúc dùng chung của sản phẩm |
| H5. Tạo và sửa sản phẩm, biến thể, giá niêm yết | Danh mục hàng | Sản phẩm và biến thể thuộc Danh mục |
| H6. Gắn ảnh vào sản phẩm | Danh mục hàng | Ảnh là một phần điều kiện đăng của sản phẩm |
| H7. Đăng, gỡ, đăng lại sản phẩm; xóa nháp; xem sản phẩm của mình | Danh mục hàng | Trạng thái đăng thuộc sản phẩm |
| H8. Biết người bán đã duyệt hay chưa (từ sự kiện) | Danh mục hàng | Cần cho điều kiện đăng; dựng bản sao từ sự kiện, không gọi đồng bộ |
| H9. Ghi hành động quản trị vào nhật ký (duyệt, từ chối hồ sơ; quản danh mục, thuộc tính, thương hiệu) | Nhật ký kiểm toán | Dấu vết dùng chung, bất biến |
| H10. Tra cứu nhật ký | Nhật ký kiểm toán | Như trên |

Mọi hành vi rơi vào một BC. Các BC có liên quan nhưng chưa làm ở C1a:

| BC | Vì sao chưa cần |
|---|---|
| Quản lý tệp | C1a chỉ có một loại tệp là ảnh sản phẩm, gắn thẳng vào sản phẩm. BC riêng chỉ đáng dựng khi có loại tệp thứ hai với chế độ riêng tư (giấy tờ hồ sơ mức 2). Hoãn có điểm nối: sản phẩm tham chiếu ảnh bằng mã tệp |
| Dữ liệu tham chiếu | Mọi địa chỉ ở C1a là văn bản tự do; cấu trúc hóa theo tỉnh, huyện, xã khi Giao nhận cần (C3a) |
| Khách hàng | Hồ sơ giao hàng chỉ cần khi đặt hàng (C2a) |
| Tìm kiếm | Khách chưa tìm, lọc sản phẩm; làm ở C1b |
| Thông báo | Gửi thư kết quả duyệt cần email (C2a); ở C1a người bán xem kết quả trong màn hồ sơ của mình |
| Tồn kho, Khuyến mãi, Định phí | Chưa có đường mua; giá ở C1a chỉ là giá niêm yết |

Giao diện không phải BC mà là nơi người dùng thực hiện hành vi. C1a cần hai ứng dụng mức tối giản: **Người bán** (nộp hồ sơ,
xem trạng thái, tạo sản phẩm, đăng) và **Vận hành** (duyệt hồ sơ, quản danh mục, tra cứu nhật ký). Khung giao diện và bộ
thành phần dùng chung đã có từ C0.

> **Chốt:** C1a có ba BC: Người bán, Danh mục hàng, Nhật ký kiểm toán; mười hành vi. → Bước 2 viết `analysis.md` cho ba BC,
> phần thuộc C1a ở mức mịn (mỗi hành vi thành một hoặc vài năng lực). Quản lý tệp, Dữ liệu tham chiếu, Khách hàng, Tìm kiếm,
> Thông báo giữ mức tên.

## 4. Kịch bản chấp nhận

Mỗi kịch bản là một cách chứng minh hành vi hoặc điều cần bảo đảm ở mục 2 đã đúng.

| # | Kịch bản | Chứng minh |
|---|---|---|
| S1 | Người dùng nộp hồ sơ người bán; quản trị duyệt; người bán tạo sản phẩm có biến thể, giá, ảnh rồi đăng; sản phẩm ở trạng thái đã đăng | H1, H3, H5, H6, H7, H8 |
| S2 | Quản trị từ chối hồ sơ kèm lý do (không có lý do thì bị từ chối thao tác); người bán thấy lý do, sửa và nộp lại; hồ sơ được duyệt | H2, H3, R3, R5 |
| S3 | Người chưa duyệt hoặc bị từ chối không đăng được sản phẩm; sau khi duyệt thì đăng được | H8, R7 |
| S4 | Nộp hồ sơ thứ hai bằng cùng tài khoản, hoặc bằng tài khoản khác nhưng trùng số căn cước hay mã số thuế, hoặc trùng tên gian hàng (kể cả khác dấu) bị từ chối với lý do rõ | R1, R2 |
| S5 | Đăng sản phẩm thiếu ảnh, thiếu giá, thiếu thuộc tính bắt buộc hoặc ở danh mục ngừng dùng bị từ chối và báo thiếu gì; hai biến thể cùng tổ hợp, giá không hợp lệ bị từ chối | R8, R9 |
| S6 | Người dùng thường không duyệt được hồ sơ; người bán không sửa được sản phẩm của người bán khác; ai chưa có hồ sơ không gọi được thao tác của người bán | R6 |
| S7 | Quản trị duyệt hồ sơ vừa bị người bán sửa sau khi quản trị mở xem: bị từ chối và yêu cầu xem lại | R4 |
| S8 | Sau khi duyệt hoặc từ chối, quản trị tra cứu nhật ký thấy: ai, lúc nào, hành động gì, hồ sơ nào; không thấy số căn cước hay mã số thuế; không có thao tác sửa hay xóa bản ghi | H9, H10, R11, R12, R13 |
| S9 | Quản trị tạo danh mục, thuộc tính, thương hiệu; thứ đã có sản phẩm dùng chỉ ngừng dùng được, không xóa; mỗi thao tác quản trị tra cứu được trong nhật ký | H4, H9, H10, R10 |

R11 và R12 còn kiểm bằng test ở bước thiết kế (log, sự kiện, giao dịch) vì không quan sát hết từ bên ngoài.

> **Chốt:** chín kịch bản (cùng mười ba điều cần bảo đảm) là điều kiện "xong" của phần nghiệp vụ. → `analysis.md` dùng chúng
> để kiểm tra mỗi năng lực có ít nhất một kịch bản chứng minh. Bước 4 dùng chúng để cắt feature theo lát dọc; bước 6 viết
> test từ chúng.

## 5. Luồng nghiệp vụ

C1a là chặng đầu có luồng xuyên BC, nối bằng sự kiện.

| Luồng | Bước |
|---|---|
| Nộp và duyệt hồ sơ | Người dùng gửi hồ sơ → Người bán kiểm quy tắc (R1, R2), lưu hồ sơ chờ duyệt → quản trị xem hồ sơ chờ duyệt → quyết định duyệt hoặc từ chối kèm lý do (R3, R4, R5, R6) → Người bán cập nhật trạng thái, cùng giao dịch ghi sự kiện kết quả và sự kiện kiểm toán (R12) → Danh mục cập nhật bản sao trạng thái người bán; Nhật ký lưu bản ghi (R13) |
| Đăng sản phẩm | Người bán đã duyệt tạo sản phẩm nháp, thêm biến thể, giá, thuộc tính, ảnh → yêu cầu đăng → Danh mục kiểm người bán đã duyệt (bản sao) và điều kiện đăng (R7, R8, R9) → sản phẩm chuyển sang đã đăng → ghi sự kiện sản phẩm cho bên sau (Tìm kiếm ở C1b) |
| Quản danh mục | Quản trị tạo, sửa, ngừng dùng danh mục, thuộc tính, thương hiệu (R10) |

> **Chốt:** hai luồng xuyên BC đều async-first: Người bán → Danh mục và Nhật ký bằng sự kiện, không thêm cặp gọi đồng bộ.
> → `analysis.md` lấy ba luồng làm khung viết từng năng lực; `service.md` ghi các sự kiện: người bán đã duyệt, bị từ chối, bản ghi
> kiểm toán, sản phẩm.

## 6. Điều kiện ngoài nghiệp vụ của chặng

Roadmap còn ghi cho C1a những điều kiện không thuộc nghiệp vụ của BC nào:
- **Kỹ thuật:** bus sự kiện kèm hộp thư sự kiện (ghi sự kiện cùng giao dịch, đẩy ra bus, xử lý nhận trùng ở bên nhận, thứ tự
  theo thực thể). Định dạng sự kiện ở giai đoạn này là JSON; nâng lên Avro kèm Schema Registry là hướng sau (xem
  `tech-stack.md`). Che dữ liệu định danh trong log là quy ước chung (`logging.md`). Lưu tệp ảnh.
- **Giao diện:** hai ứng dụng Người bán và Vận hành ở mức tối giản, mỗi ứng dụng có cổng giữ token phía máy chủ và client
  đăng ký riêng (nối nốt các dòng hoãn của C0).
- **Phân quyền:** vai từ token và quyền sở hữu, mặc định từ chối (2.4).

Chúng không đi qua analysis của BC.

> **Chốt:** các điều kiện này không thuộc ba BC. → Bước 4 đưa chúng vào `00-features.md` như feature kỹ thuật (nguồn là NFR,
> quy ước chung và ADR) và feature giao diện (nguồn là `5-ui-analysis.md`). Quyết định kỹ thuật phát sinh vào ADR ở bước 5.

## 7. Ngoài chặng

| Việc | Vì sao ngoài C1a | Làm ở |
|---|---|---|
| Tìm kiếm, lọc, xem chi tiết cho khách | Cần read model và hạ tầng tìm kiếm | C1b |
| Khóa, mở người bán; chặn, mở sản phẩm; nhật ký cho các hành động đó | Cần phân quyền sâu và danh sách người dùng | C1b |
| Giấy tờ đính kèm hồ sơ; đổi thông tin pháp lý sau khi duyệt | Cần chế độ tệp riêng tư và luồng xét lại | Sau C1a, khi Quản lý tệp dựng |
| Thông báo kết quả duyệt qua email | Chưa có Thông báo | C2a |
| Quy trình duyệt chạy dài, hạn duyệt và quá hạn | Cần Lập lịch | C2b |
| Chính sách phân quyền theo thuộc tính (ABAC) và bản đồ quyền cho giao diện | Chỉ có ý nghĩa với cổng nhiều vai nội bộ | Khi làm cổng Vận hành, thời điểm thích hợp |
| Địa chỉ có cấu trúc; địa chỉ nhận hàng hoàn; tài khoản ngân hàng nhận tiền | Chưa có Giao nhận, hoàn hàng, Sổ cái | C3a, C3b, chặng Sổ cái |

> **Chốt:** các việc trên không vào `deferred.md` của chặng; chúng nằm ở roadmap và phần "Chưa làm" của `analysis.md` BC liên quan.

## 8. Câu hỏi chuyển sang bước 2

- Người bán: hồ sơ, loại hình, khóa định danh và quy tắc duyệt đã có mô hình nháp; bước 2 chuyển thành `analysis.md`.
- Danh mục hàng: khái niệm danh mục, thuộc tính (trục biến thể và thông số), thương hiệu, sản phẩm, biến thể và điều kiện đăng
  đã có mô hình nháp; bước 2 chuyển thành `analysis.md`, sau đó mới đối chiếu mã hiện có.
- Nhật ký kiểm toán: chưa có mô hình nháp; cần phân tích bản ghi (ai, lúc nào, hành động, đối tượng, kết quả), tính bất biến
  và cách tra cứu.

# Tài khoản

> Phạm vi: mô hình và quy tắc nghiệp vụ của tài khoản đăng nhập (aggregate `UserAccount`): thông tin, vai, trạng thái, tài khoản nội bộ, đăng ký, bất biến. Cách lưu và kỹ thuật ở `service.md`, `data.md`.
> Phần phiên (đăng nhập, đăng xuất, hạn, thiết bị) ở `login-session.md`. Phần dùng chung của Xác thực (ranh giới, ngôn ngữ chung, hợp đồng danh tính, điểm nối) ở `0.overview.md`.
> Mã BC: `ATH`.

## 1. Định nghĩa

**Tài khoản** đại diện cho một người trong hệ thống: nhận ra bằng email, chứng minh bằng mật khẩu, thuộc đúng một vai, và đang hoạt động hay bị khóa. Xác thực là **nguồn sự thật duy nhất** cho câu hỏi "người này đăng nhập được không, với vai gì". Tài khoản là một aggregate (`UserAccount`) không có thực thể con.

Tài khoản và phiên là hai aggregate riêng, tham chiếu nhau bằng mã tài khoản: tài khoản sống lâu và hiếm khi đổi, phiên ngắn và đổi liên tục. Khóa tài khoản kết thúc mọi phiên của nó (xem `login-session.md`, lý do "tài khoản bị khóa").

## 2. Thông tin của tài khoản

Theo ý nghĩa nghiệp vụ; kiểu dữ liệu và cột nằm ở `service.md`, `data.md`.

| Thông tin | Ý nghĩa | Luật | Bất biến |
|---|---|---|---|
| Email | Định danh đăng nhập | Duy nhất, không phân biệt chữ hoa thường; đăng ký công khai không dùng được email miền nội bộ | INV-ATH-01, 07 |
| Mật khẩu | Bí mật của chủ tài khoản | Chỉ tồn tại dạng không đọc được. **Mật khẩu hợp lệ** khi đăng ký: từ 8 đến 64 ký tự, chỉ gồm chữ cái không dấu `A-Za-z`, chữ số `0-9` và hai ký tự đặc biệt `@` `$`; có ít nhất một chữ cái và một chữ số. Không cho dấu cách, emoji hay chữ có dấu. Chặn trên 64 nằm dưới giới hạn 72 byte của thuật toán băm (ADR-0002). Ở C0 không có điều cấm nào khác (mật khẩu phổ biến, đã bị lộ: xem phần hoãn của feature đăng ký) | INV-ATH-02 |
| Vai | Nhóm của tài khoản | Đúng một giá trị, không đổi sau khi tạo (mục 3) | INV-ATH-06, 09 |
| Trạng thái | Hoạt động hoặc bị khóa | Mục 4 | |
| Thời điểm tạo | Biết tài khoản có từ khi nào | | |

## 3. Vai

**Mỗi tài khoản có đúng một vai.** Nhiều vai trên một tài khoản kéo theo phiên mang tập vai, khái niệm "đang hành động với
tư cách nào", chuyển ngữ cảnh ở giao diện và kiểm quyền phức tạp hơn. Đó là cách nghĩ của các hệ thống nhiều tổ chức,
không phải của một sàn. Một vai trên mỗi tài khoản đơn giản hơn và đủ cho nghiệp vụ.

**Tập vai.** Tập vai mở rộng bằng dữ liệu, không phải bằng đổi cấu trúc tài khoản, để thêm vai không phải sửa Xác thực.

| Vai | Loại | Có từ | Ghi chú |
|---|---|---|---|
| Người dùng | Công khai | C0 | Tự đăng ký; có hồ sơ Khách hàng khi đặt hàng (C2a) |
| Quản trị | Nội bộ | C0 (có sẵn) | Context map ghi Quản trị là nhiều vai trò gộp (duyệt, kiểm duyệt, phân xử, vận hành) |
| Vai nội bộ khác (vận hành, Nhân viên kho...) | Nội bộ | Khi có | Thêm khi BC đầu tiên cần phân biệt (người đầu tiên là Người bán duyệt ở C1a, thời điểm phân quyền là C1b) |

**Người bán và Shipper không phải vai.** Chúng là hồ sơ năng lực: hồ sơ nằm ở BC của chúng, trỏ về tài khoản, có trạng thái
duyệt (chờ duyệt, đã duyệt...). Một tài khoản Người dùng có thể có thêm hồ sơ Người bán, hồ sơ Shipper, hoặc cả hai, và
vẫn mua hàng như mọi người. Lý do:
- Sự thật "người này là người bán" chỉ có **một nơi sở hữu** (BC Người bán). Nếu thêm vai thì cùng sự thật nằm ở hai
  nơi, phải đồng bộ, và lệch là sai.
- Một người vừa mua vừa bán là chuyện bình thường, không cần tài khoản thứ hai hay chuyển ngữ cảnh.
- Duyệt chỉ đổi trạng thái ở một nơi; tài khoản không đổi.

Tài khoản đang chờ duyệt đăng nhập và mua bình thường, nhưng chưa bán được vì trạng thái ở BC Người bán chưa đạt.

## 4. Trạng thái tài khoản

Tài khoản có trạng thái hoạt động hoặc bị khóa; thêm "chờ xác minh" khi kéo xác minh email vào. Ở C0 đăng ký cho ra tài
khoản hoạt động ngay. **Xác thực là nguồn sự thật duy nhất của trạng thái**, và nó hiệu lực ngay: khóa là thao tác bảo
mật, nếu trạng thái có bản sao làm căn cứ đăng nhập thì khóa sẽ có độ trễ và có đường đua giữa khóa và kích hoạt. Định
danh chỉ giữ bản sao để hiển thị. Tài khoản bị khóa thì mọi phiên của nó kết thúc (C1b).

## 5. Tài khoản nội bộ

- Tài khoản nội bộ **không có hồ sơ Khách hàng**, tức không mua hàng. Người nội bộ muốn mua thì tạo tài khoản Người dùng
  riêng, bằng email riêng.
- Email của tài khoản nội bộ thuộc một **miền nội bộ** (ví dụ `@t3nexus.com.vn`), và đăng ký công khai không được dùng
  email thuộc miền đó. Lý do: nếu ai cũng đăng ký được `admin@...` thì tài khoản đó trông như của nội bộ, dễ giả mạo.
  Miền nội bộ là cấu hình, không cố định trong mã. Nguồn của quyền là **vai**, không phải đuôi email; đuôi email chỉ là
  ràng buộc để hai thứ khớp nhau (INV-ATH-07).
- Tách tài khoản nội bộ khỏi tài khoản mua đúng với thực tế ở các hệ thống khác (mục 11) và tránh việc quyền vận hành lẫn
  vào việc mua cá nhân.

**Chừa chỗ cho nhân viên.** Việc nhân viên được ưu đãi là nghiệp vụ của BC sở hữu ưu đãi (Khuyến mãi, hoặc Nhân sự nếu có),
không phải của Xác thực. Liên kết giữa tài khoản nội bộ và tài khoản mua của cùng một người là một bản ghi ở BC đó, tham
chiếu hai tài khoản bằng mã định danh. Để thêm sau này mà **không phải sửa Xác thực**, Xác thực bảo đảm:
1. Mỗi tài khoản có mã định danh ổn định, và BC khác tham chiếu bằng mã đó, không bằng email (email có thể đổi).
2. Tạo tài khoản nội bộ là một năng lực không phụ thuộc người gọi: hôm nay do Quản trị, sau này có thể do Nhân sự.
3. Tài khoản có trạng thái và không bị xóa vật lý, để khi nhân viên nghỉ việc có thể khóa và BC khác biết mà thu hồi.
4. Tập vai nội bộ mở rộng bằng dữ liệu (mục 3).

## 6. Năng lực

Mỗi năng lực trả lời sáu câu của P3: (1) ai khởi tạo, (2) ai xử lý và theo quy trình nào, (3) ai được báo, (4) đường không suôn sẻ, (5) ai xem lại được lịch sử, (6) dữ liệu chảy sang đâu. Đăng nhập, giữ phiên, đăng xuất (CAP-ATH-02 đến 04) thuộc `login-session.md`.

### CAP-ATH-01 Đăng ký tự phục vụ (stage: H1)

1. Khách vãng lai. Đầu vào chỉ gồm email và mật khẩu; **vai không phải đầu vào**, dù người gọi gửi kèm gì.
2. Xác thực tự xử lý, không có bước duyệt: chuẩn hóa email, kiểm email chưa dùng và không thuộc miền nội bộ, kiểm mật
   khẩu hợp lệ; tạo tài khoản vai Người dùng ở trạng thái hoạt động.
3. Người đăng ký thấy kết quả ngay. C0 không gửi thư (Thông báo chưa có). Xác thực phát event "tài khoản đã đăng ký"
   (mã tài khoản, email, vai; không mang tên); ở C0 chưa có bên nhận.
4. Đường không suôn sẻ:
   - Email đã dùng (không phân biệt chữ hoa thường): từ chối với lý do rõ. Việc này lộ rằng email đã có tài khoản; chấp
     nhận vì người dùng cần biết để chuyển sang đăng nhập.
   - Gửi lặp (bấm hai lần, mạng chậm): chỉ ra một tài khoản (INV-ATH-04).
   - Mật khẩu không hợp lệ, email sai định dạng: từ chối và nêu lý do.
   - Email thuộc miền nội bộ: từ chối (INV-ATH-07).
   - Đăng ký bằng email của người khác: chưa có xác minh email nên không ngăn được; rủi ro chấp nhận ở C0 (Q2).
5. Chưa có lịch sử riêng ở C0 ngoài thời điểm tạo lưu trong tài khoản.
6. Danh tính chảy sang các BC khác khi người dùng gọi tới chúng. Hồ sơ Khách hàng và hồ sơ Định danh được tạo khi các BC
   đó vào (khi nào tạo là câu hỏi của chặng đó).

### CAP-ATH-05 Ghi nhận và cung cấp vai (stage: H6)

1. Hệ thống, lúc tạo tài khoản: vai Người dùng khi tự đăng ký; vai Quản trị khi có sẵn.
2. Xác thực giữ vai cùng tài khoản.
3. BC khác đọc vai từ hợp đồng danh tính.
4. Đường không suôn sẻ: không có tài khoản không vai hoặc vai ngoài tập (INV-ATH-06); vai không đổi sau khi tạo ở C0
   (INV-ATH-09), việc đổi vai là việc chưa làm.
5. Không có.
6. Vai chảy sang mọi BC cùng với danh tính. Xác thực **không** biết tài khoản có hồ sơ Người bán hay Shipper hay không.

### Dữ liệu khởi tạo: Quản trị có sẵn (stage: H2)

Tài khoản Quản trị là dữ liệu khởi tạo khi dựng hệ thống, không phải năng lực của người dùng: một bản ghi tài khoản vai
Quản trị, mật khẩu ở dạng băm, email thuộc miền nội bộ. Dữ liệu này phải thỏa cùng các bất biến như mọi tài khoản
(INV-ATH-01, 02, 06, 07). Mật khẩu ban đầu không nằm trong mã nguồn (Q1). Ở C0 đây là **đường duy nhất** tạo ra tài khoản nội bộ (INV-ATH-11); không có thao tác nào của người dùng tạo được nó.

## 7. Bất biến (INV)

| ID | Bất biến | Lý do | Từ stage |
|---|---|---|---|
| INV-ATH-01 | Một email, một tài khoản; so sánh không phân biệt chữ hoa chữ thường | Email là cách nhận ra người và nơi gửi thông báo về sau; trùng sẽ lẫn người | R1 |
| INV-ATH-02 | Mật khẩu không tồn tại ở dạng đọc được, ở bất kỳ đâu | Người dùng hay dùng lại mật khẩu; lộ ở đây là thiệt hại ở nơi khác | R2 |
| INV-ATH-04 | Gửi đăng ký lặp không tạo hai tài khoản | Bấm hai lần và mạng chậm là chuyện thường | R4 |
| INV-ATH-06 | Mỗi tài khoản có đúng một vai, thuộc tập đã quy định; đăng ký công khai luôn ra vai Người dùng, người gọi không chọn được vai | Nền của kiểm quyền; vai lạ thì BC khác không biết xử lý; tự chọn vai quản trị là mở cửa cho giả mạo | R6 |
| INV-ATH-07 | Tài khoản có vai nội bộ khi và chỉ khi email thuộc miền nội bộ; đăng ký công khai không dùng được email miền đó | Chống giả mạo tài khoản nội bộ (mục 5) | R7 |
| INV-ATH-09 | Vai của tài khoản không đổi sau khi tạo (ở C0) | Giữ một nguồn sự thật cho vai; đổi vai kéo theo nghiệp vụ chưa có | mới |
| INV-ATH-11 | Tài khoản vai nội bộ chỉ được tạo bởi dữ liệu khởi tạo hoặc bởi người dùng nội bộ cấp cao có quyền tạo tài khoản nội bộ; không có đường tự đăng ký hay tự tạo | INV-ATH-06 chỉ chặn đường đăng ký công khai; bất biến này chặn mọi đường tạo tài khoản nội bộ, để không ai lập tài khoản ẩn hay tự nâng quyền | R6 mở rộng |

Các bất biến về đăng nhập và phiên (INV-ATH-03, 05, 08, 10) ở `login-session.md` và `0.overview.md`.

## 8. Event

| Event | Khi nào | Nội dung chính | Bên nhận |
|---|---|---|---|
| Tài khoản đã đăng ký | Đăng ký thành công | Mã tài khoản, email, vai (không mang tên) | Chưa có bên nhận ở C0 |
| `UserAccountLocked`, `UserAccountUnlocked` | Quản trị khóa hoặc mở | Mã tài khoản, người thao tác, thời điểm | Phiên (cùng BC), Định danh, Nhật ký kiểm toán (C1b) |

## 9. Chưa làm

Mỗi mục ghi trạng thái theo P3, giá trị, lý do xếp sau và điểm nối khi quay lại. "Trong scope" là phần root đã xếp vào
BC nhưng thuộc chặng sau. "Cắt tạm" theo B16. "Chưa có ở root" là phần phân tích này thấy cần ghi lại.

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Khóa, mở tài khoản | Trong scope (B16) | C1b | Quản trị xử lý tài khoản vi phạm; khóa có hiệu lực ngay và kết thúc mọi phiên của tài khoản | Cần Nhật ký kiểm toán đi cùng | Quản trị thao tác trực tiếp tại Xác thực; event khóa/mở; trạng thái tài khoản |
| Tạo tài khoản nội bộ | Trong scope (B16) | C1b trở đi, khi vai nội bộ đầu tiên cần | Có người dùng nội bộ ngoài tài khoản có sẵn | Ở C0 chưa có vai nào ngoài Quản trị | Hợp đồng không phụ thuộc người gọi (mục 5). Người gọi phải là người dùng nội bộ cấp cao đã xác thực có quyền tạo tài khoản nội bộ (INV-ATH-11). Chi tiết vai nào tạo được vai nào để khi có phân quyền (C1b) |
| Đổi mật khẩu | Trong scope (B16) | Có thể vào C0 (Q1) | Quản trị cần đổi mật khẩu ban đầu | Phụ thuộc quyết định ở Q1 | Cần phiên đang hoạt động; kết thúc mọi phiên khác |
| Quên, đặt lại mật khẩu | **Cắt tạm** (B16) | Khi có Thông báo | Người dùng quên mật khẩu là chắc chắn; ở C0 người quên phải tạo tài khoản mới | Cần gửi thư | Thông báo |
| Xác minh email | **Cắt tạm** (B16) | Khi có Thông báo | Chống chiếm email (Q2) | Cần gửi thư | Thông báo; thêm trạng thái "chờ xác minh" |
| Nhiều vai trên một tài khoản | Hoãn (chưa có ở root) | Không dự kiến | Trải nghiệm giống một số sàn | Không thêm bài học phân tán; kéo theo phiên mang tập vai, chuyển ngữ cảnh | Với mô hình hồ sơ năng lực (mục 3) có thể không bao giờ cần |
| Liên kết nhân viên với tài khoản mua | Không thuộc Xác thực | Khi có ưu đãi nhân viên | Cho phép ưu đãi nhân viên | Chưa có nghiệp vụ | Xem mục 5 |
| Nhân viên của gian hàng (tài khoản phụ có quyền riêng) | Không thuộc Xác thực | Người bán, nếu cần | Chủ gian hàng giao việc | Chưa có nghiệp vụ | Quan hệ thành viên giữa tài khoản và gian hàng ở BC Người bán |
| Shipper tự đăng ký hay Quản trị tạo | Root để ngỏ (B27) | C3a | — | Chốt khi làm | Hồ sơ Shipper trỏ về tài khoản |

## 10. Câu hỏi cần trả lời

Danh sách này là việc còn mở của phân tích. Câu nào được trả lời thì chuyển kết luận và lý do vào thân bài, rồi xóa
khỏi đây.

| # | Câu hỏi | Vì sao đáng hỏi | Đề xuất |
|---|---|---|---|
| Q1 | Quản trị có sẵn: mật khẩu ban đầu từ đâu, có bắt đổi ở lần đăng nhập đầu không | Mật khẩu cố định trong mã là rủi ro; roadmap yêu cầu giữ bí mật ngoài mã từ C0 | Mật khẩu băm nằm trong script khởi tạo của môi trường, không trong mã. Đổi mật khẩu cho tài khoản đang đăng nhập có thể vào C0 vì Quản trị cần |
| Q2 | Chưa xác minh email thì chấp nhận rủi ro người đăng ký bằng email của người khác (chiếm email) | INV-ATH-01 giữ email duy nhất, nên chủ email thật sau đó không đăng ký được | Chấp nhận ở C0 vì xác minh email đang cắt tạm (mục 9) |

## 11. Tham khảo từ hệ thống khác

Dùng để hình dung, không phải chứng cứ chính thức. Ngày truy cập `2026-10-08`.

| Nội dung | Hệ thống | Mức tin cậy | Nguồn |
|---|---|---|---|
| Đăng ký bán dùng chung đăng nhập với tài khoản mua, rồi qua luồng đăng ký riêng | Shopee | Khá (bài của bên thứ ba) | [Ginee](https://ginee.com/my/insights/how-to-register-shopee-seller/) |
| Đăng ký bán và đăng ký tài xế Flex đều có thể dùng tài khoản Amazon có sẵn; tài khoản bán luôn liên kết một tài khoản mua | Amazon | Khá | [Amazon IT](https://sell.amazon.it/en/vendere-online/guida-alla-registrazione), [Flex FAQ](https://flex.amazon.com/faqs/) |
| Một bảng người dùng có cờ nhân viên, nhân viên vẫn đặt hàng được | Saleor | Cao (tài liệu chính thức) | [Saleor docs](https://docs.saleor.io/developer/users/user-model) |
| Bảng quản trị tách khỏi bảng khách | Magento | Trung bình (bên thứ ba) | [Adobe Commerce](https://experienceleague.adobe.com/en/docs/commerce-operations/security-and-compliance/reference/data-m1) |
| Ưu đãi nhân viên gắn lên tài khoản mua, xác minh bằng email công ty hoặc mã | Magento (tiện ích), Amazon | Thấp | [Ecosire](https://ecosire.com/es/apps/magento-2/employee-staff-discount-program) |

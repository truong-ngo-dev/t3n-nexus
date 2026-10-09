# Phân tích nghiệp vụ: Xác thực

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-10-09` · **Mã BC:** `ATH` (tiền tố cho ID)
>
> Tài liệu giữ nghiệp vụ **kèm lý do**, phản ánh trạng thái hiện tại, không ghi lịch sử thay đổi. Bản này mới phủ phần
> thuộc chặng C0; phần của chặng sau ở mức tên (mục 8). Chỗ cần chốt nằm ở mục 9.
>
> **Nguồn:** `4-context-map.md` B16, `5-ui-analysis.md` §5, `7-roadmap.md` C0 · **Chặng:** [`stage.md` của C0](../../stage/c0-foundation/stage.md)

## 1. Nhiệm vụ và ranh giới

Xác thực là **nguồn sự thật duy nhất** cho câu hỏi "người này đăng nhập được không, với vai gì". Nó:
- tạo và giữ **tài khoản đăng nhập**, kèm **vai** và **trạng thái** của tài khoản;
- **xác thực** người dùng và giữ **phiên** cho mọi portal, để đăng nhập một lần dùng chung;
- công bố **hợp đồng danh tính** của người đang đăng nhập để các BC khác tự đọc (mục 4.5).

Lý do là một BC riêng: nhiều portal (ứng dụng web và di động) cần cùng một máy chủ xác thực để đăng nhập chung, và chính
sách bảo mật đổi vì lý do khác hồ sơ hiển thị.

**Điểm sâu:** (1) phân quyền (chủ động chọn, C1b); (2) làn sóng đăng nhập trước flash sale (đòi hỏi, đồng thời cao): mỗi lần
đăng nhập phải băm mật khẩu tốn CPU, trong khi tải lúc flash sale lớn hơn nhiều so với thường ngày (số liệu ở NFR 3.3, đo
bằng bộ đo nền ở C0). Các phần còn lại của Xác thực không phải điểm sâu.

Xác thực **không** làm:

| Việc | Thuộc | Lý do |
|---|---|---|
| Tên hiển thị, ảnh đại diện, thiết bị, lịch sử đăng nhập | [Định danh](../identity-service/analysis.md) | Dữ liệu để xem, đổi vì lý do hiển thị và theo dõi |
| Hồ sơ giao hàng, sổ địa chỉ, người nhận | Khách hàng (C2a) | Đổi theo lý do khác (giao hàng) |
| Hồ sơ bán hàng, gian hàng, duyệt người bán | Người bán (C1a) | Có vòng đời và bước duyệt riêng |
| Hồ sơ giao hàng, duyệt shipper | Shipper (C3a) | Như trên |
| Quyết định "được làm thao tác này không" trên tài nguyên của BC khác | BC sở hữu tài nguyên | Chỉ BC đó biết chủ sở hữu và trạng thái (UI §5) |
| Quan hệ lao động, ưu đãi nhân viên | Nhân sự, Khuyến mãi (sau này) | Không phải chuyện xác thực (mục 4.4) |
| Ghi lịch sử hành động quản trị | Nhật ký kiểm toán (C1b) | BC riêng |
| Gửi thư cho người dùng | Thông báo | BC riêng |

## 2. Ngôn ngữ chung

| Thuật ngữ | Định nghĩa | Nghĩa ở BC khác |
|---|---|---|
| Tài khoản | Đại diện cho một người trong hệ thống; thông tin giữ ở mục 4.1 | Định danh giữ bản sao email, vai, trạng thái chỉ để hiển thị |
| Vai | Nhóm mà tài khoản thuộc về, có đúng một giá trị. Quyết định tài khoản vào portal nào và thao tác thô | Các BC khác đọc vai, không tự đặt |
| Người dùng | Vai của tài khoản tự đăng ký. Viết hoa chỉ vai này (glossary) | — |
| Vai nội bộ | Mọi vai khác "Người dùng": Quản trị ở C0, các vai cấp dưới thêm sau | — |
| Phiên | Khoảng thời gian một tài khoản được coi là đã xác thực; có hai tầng (mục 4.6) | Định danh hiển thị phiên đang hoạt động |
| Portal | Ứng dụng giao diện cho một nhóm bên tham gia (Cửa hàng, Người bán, Vận hành, Shipper); người dùng vào một portal thì đăng nhập qua Xác thực | Mỗi portal tự quyết hiển thị gì, không quyết ai được đăng nhập |
| Phiên gốc, phiên portal | Phiên gốc là một lần đăng nhập trên một thiết bị, ở nơi xác thực; phiên portal là phần của lần đăng nhập đó ở từng portal đã vào (mục 4.6) | Định danh gọi cả hai là "phiên" khi hiển thị |
| Hồ sơ năng lực | Dữ liệu và vòng đời của một năng lực thêm vào tài khoản Người dùng (bán, giao hàng), do BC khác giữ và trỏ về tài khoản | Người bán, Shipper |
| Hợp đồng danh tính | Nội dung Xác thực công bố cho BC khác đọc về người đang gọi (mục 4.5) | Mọi BC đọc |

Người mua, Người bán, Shipper là bên tham gia trong giao dịch, không phải vai (glossary).

## 3. Tác nhân

| Tác nhân | Việc |
|---|---|
| Khách vãng lai | Đăng ký |
| Người có tài khoản | Đăng nhập, đăng xuất |
| Hệ thống (lúc dựng) | Có sẵn tài khoản Quản trị (mục 5, Dữ liệu khởi tạo) |
| BC khác | Đọc hợp đồng danh tính của người đang gọi |

## 4. Mô hình hóa

Phần này là kết luận về cách một người được đại diện trong hệ thống. Mỗi quyết định kèm lý do.

### 4.1 Thông tin của tài khoản

Theo ý nghĩa nghiệp vụ; kiểu dữ liệu và cột nằm ở `service.md`, `data.md`.

| Thông tin | Ý nghĩa | Luật | Bất biến |
|---|---|---|---|
| Email | Định danh đăng nhập | Duy nhất, không phân biệt chữ hoa thường; đăng ký công khai không dùng được email miền nội bộ | INV-ATH-01, 07 |
| Mật khẩu | Bí mật của chủ tài khoản | Chỉ tồn tại dạng không đọc được. **Mật khẩu hợp lệ** khi đăng ký: từ 8 đến 64 ký tự, chỉ gồm chữ cái không dấu `A-Za-z`, chữ số `0-9` và hai ký tự đặc biệt `@` `$`; có ít nhất một chữ cái và một chữ số. Không cho dấu cách, emoji hay chữ có dấu. Chặn trên 64 nằm dưới giới hạn 72 byte của thuật toán băm (ADR-0002). Ở C0 không có điều cấm nào khác (mật khẩu phổ biến, đã bị lộ: xem phần hoãn của feature đăng ký) | INV-ATH-02 |
| Vai | Nhóm của tài khoản | Đúng một giá trị, không đổi sau khi tạo (mục 4.2) | INV-ATH-06, 09 |
| Trạng thái | Hoạt động hoặc bị khóa | Mục 4.3 | |
| Thời điểm tạo | Biết tài khoản có từ khi nào | | |

### 4.2 Vai

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

### 4.3 Trạng thái tài khoản

Tài khoản có trạng thái hoạt động hoặc bị khóa; thêm "chờ xác minh" khi kéo xác minh email vào. Ở C0 đăng ký cho ra tài
khoản hoạt động ngay. **Xác thực là nguồn sự thật duy nhất của trạng thái**, và nó hiệu lực ngay: khóa là thao tác bảo
mật, nếu trạng thái có bản sao làm căn cứ đăng nhập thì khóa sẽ có độ trễ và có đường đua giữa khóa và kích hoạt. Định
danh chỉ giữ bản sao để hiển thị. Tài khoản bị khóa thì mọi phiên của nó kết thúc (C1b).

### 4.4 Tài khoản nội bộ

- Tài khoản nội bộ **không có hồ sơ Khách hàng**, tức không mua hàng. Người nội bộ muốn mua thì tạo tài khoản Người dùng
  riêng, bằng email riêng.
- Email của tài khoản nội bộ thuộc một **miền nội bộ** (ví dụ `@t3nexus.com.vn`), và đăng ký công khai không được dùng
  email thuộc miền đó. Lý do: nếu ai cũng đăng ký được `admin@...` thì tài khoản đó trông như của nội bộ, dễ giả mạo.
  Miền nội bộ là cấu hình, không cố định trong mã. Nguồn của quyền là **vai**, không phải đuôi email; đuôi email chỉ là
  ràng buộc để hai thứ khớp nhau (INV-ATH-07).
- Tách tài khoản nội bộ khỏi tài khoản mua đúng với thực tế ở các hệ thống khác (mục 10) và tránh việc quyền vận hành lẫn
  vào việc mua cá nhân.

**Chừa chỗ cho nhân viên.** Việc nhân viên được ưu đãi là nghiệp vụ của BC sở hữu ưu đãi (Khuyến mãi, hoặc Nhân sự nếu có),
không phải của Xác thực. Liên kết giữa tài khoản nội bộ và tài khoản mua của cùng một người là một bản ghi ở BC đó, tham
chiếu hai tài khoản bằng mã định danh. Để thêm sau này mà **không phải sửa Xác thực**, Xác thực bảo đảm:
1. Mỗi tài khoản có mã định danh ổn định, và BC khác tham chiếu bằng mã đó, không bằng email (email có thể đổi).
2. Tạo tài khoản nội bộ là một năng lực không phụ thuộc người gọi: hôm nay do Quản trị, sau này có thể do Nhân sự.
3. Tài khoản có trạng thái và không bị xóa vật lý, để khi nhân viên nghỉ việc có thể khóa và BC khác biết mà thu hồi.
4. Tập vai nội bộ mở rộng bằng dữ liệu (mục 4.2).

### 4.5 Hợp đồng danh tính

Xác thực không trả lời "người này là ai" mỗi khi một BC hỏi. Nó công bố một **hợp đồng danh tính** mà BC khác tự đọc từ
mã đăng nhập của yêu cầu, kiểm chữ ký tại chỗ, không gọi lại Xác thực.

| Mục | Nội dung |
|---|---|
| Nội dung | Mã tài khoản, vai, mã phiên, hạn |
| Không có | Tên, ảnh, trạng thái bán hay giao hàng, mã riêng của Khách hàng hoặc Người bán |
| Khóa chủ sở hữu | BC khác tham chiếu chủ sở hữu bằng **mã tài khoản** (đơn thuộc về tài khoản X, sản phẩm thuộc về tài khoản X). Hồ sơ riêng có mã riêng chỉ để dùng nội bộ BC đó. Chỉ khi một gian hàng có nhiều tài khoản (đang hoãn) mới cần tra từ mã tài khoản ra mã miền |
| Độ tươi | Đúng tại lúc cấp. Vai không đổi (INV-ATH-09) nên không lỗi thời. Khóa tài khoản và thu hồi phiên có độ trễ tối đa bằng hạn của mã đăng nhập, trừ danh sách chặn (Q5) |
| Người dùng giao diện | Giao diện hỏi "tôi là ai" qua cổng; vai lấy từ Xác thực, tên hiển thị từ Định danh (C1b), cần ghép ở cổng |

### 4.6 Phiên

Phiên có **hai tầng**: một phiên ở nơi xác thực (một lần đăng nhập) và một phiên cho mỗi portal người dùng vào, nằm bên dưới
phiên gốc. Lý do: đăng nhập chung, người dùng không đăng nhập lại ở từng portal; hệ quả là đăng xuất và thu hồi phải cắt
được cả hai tầng.

- Mỗi phiên ghi: chủ phiên, portal, thời điểm cấp và hạn, địa chỉ mạng và thông tin thiết bị lúc cấp (để Định danh hiển thị).
- **Hạn tuyệt đối.** Hết hạn là suy ra từ hạn, không phải một trạng thái; các kiểu hết hạn xem Q2.
- Mỗi (phiên gốc, portal) có tối đa một phiên đang hoạt động (INV-ATH-10).
- **Đăng nhập chung.** Khi người dùng vào portal thứ hai mà phiên gốc còn hiệu lực thì chỉ cấp phiên portal bên dưới phiên
  gốc đó, không hỏi lại mật khẩu. Phiên gốc gắn với một lần đăng nhập trên một thiết bị; đăng nhập ở thiết bị khác tạo phiên
  gốc khác.
- **Vai quyết định portal được vào** (INV-ATH-11): phiên portal chỉ cấp cho tài khoản có vai được phép vào portal đó.
- Xác thực chỉ giữ phiên đang hoạt động. Phiên đã kết thúc và lịch sử thuộc Định danh (C1b).

**Portal và vai được vào.** Bảng này là dữ liệu của Xác thực: thêm một portal là thêm dòng, không đổi cấu trúc tài khoản hay phiên.

| Portal | Vai được vào | Ghi chú |
|---|---|---|
| Cửa hàng | Người dùng | Vai nội bộ không mua hàng (mục 4.4) nên không vào; thấy trang "không có quyền" kèm nút về portal Vận hành |
| Vận hành | Vai nội bộ (Quản trị ở C0) | Chỉ chứa nghiệp vụ vận hành sàn: duyệt, phân xử, điều phối shipper, nhận hàng ở kho. Người dùng thấy trang "không có quyền" kèm nút về Cửa hàng |
| Người bán | Người dùng | Xác thực chỉ cho vào portal. Bán được hay không do BC Người bán quyết (hồ sơ bán hàng). Khi vào, portal gọi BC Người bán để chọn màn hình: chưa có hồ sơ thì màn đăng ký làm người bán, chờ duyệt, đã duyệt, bị khóa. Quyền thật kiểm ở mọi API của Người bán; ẩn màn hình không thay cho kiểm quyền |
| Shipper | Người dùng | Như Người bán, với BC Shipper. Có lối vào riêng (ứng dụng di động), Cửa hàng không dẫn tới |

- **Không có quyền không phải đăng nhập lỗi.** Mật khẩu đúng thì phiên gốc vẫn được tạo; chỉ phiên portal không được cấp. Trang
  "không có quyền" có nút về portal phù hợp với vai, và vì phiên gốc còn nên vào đó không hỏi lại mật khẩu.
- Từ Cửa hàng có lối sang portal Người bán (menu tài khoản; dùng chung phiên gốc), cũng là đường để người mua trở thành người
  bán. Có hiện lối đó hay không là quyết định giao diện của từng portal, không phải của Xác thực.

## 5. Năng lực trong C0

Mỗi năng lực trả lời sáu câu của P3: (1) ai khởi tạo, (2) ai xử lý và theo quy trình nào, (3) ai được báo, (4) đường không
suôn sẻ, (5) ai xem lại được lịch sử, (6) dữ liệu chảy sang đâu.

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
   - Đăng ký bằng email của người khác: chưa có xác minh email nên không ngăn được; rủi ro chấp nhận ở C0 (Q4).
5. Chưa có lịch sử riêng ở C0 ngoài thời điểm tạo lưu trong tài khoản.
6. Danh tính chảy sang các BC khác khi người dùng gọi tới chúng. Hồ sơ Khách hàng và hồ sơ Định danh được tạo khi các BC
   đó vào (khi nào tạo là câu hỏi của chặng đó).

### CAP-ATH-02 Đăng nhập (stage: H3)

1. Người có tài khoản, khi vào một portal cần đăng nhập. Đầu vào là email, mật khẩu và **portal đích** (portal người dùng
   đang muốn vào; do portal chuyển người dùng tới nơi đăng nhập, người dùng không tự chọn).
2. Xác thực xử lý theo thứ tự: chuẩn hóa email, tìm tài khoản, kiểm mật khẩu, kiểm tài khoản đang hoạt động; đạt thì có phiên gốc.
   Hai bước đầu sai thì cùng một kết quả (INV-ATH-03). Rồi mới xin phiên portal cho portal đích: kiểm vai được vào (INV-ATH-11)
   và cấp phiên portal (CAP-ATH-03). Nếu người dùng đã có phiên gốc còn hiệu lực (đã đăng nhập ở portal khác) thì **không hỏi
   lại mật khẩu**: chỉ xin phiên portal (đăng nhập chung, mục 4.6).
3. Người dùng thấy kết quả và được đưa về portal đích trong trạng thái đã đăng nhập.
4. Đường không suôn sẻ:
   - Sai email hoặc sai mật khẩu: cùng một kết quả, kể cả thời gian trả lời (INV-ATH-03), để không dò được email nào có
     tài khoản. Hệ quả: email không tồn tại cũng tốn đúng công kiểm mật khẩu như email có thật, nên tải đăng nhập không phụ
     thuộc việc email có thật hay không (và đây là lý do đăng nhập cần giới hạn tần suất, Q7).
   - Mật khẩu đúng nhưng vai không được vào portal đích: phiên gốc vẫn được tạo, phiên portal không được cấp; người dùng thấy
     trang "không có quyền" kèm nút về portal phù hợp với vai, và vào đó không hỏi lại mật khẩu (mục 4.6). Nói rõ được vì người
     gọi đã chứng minh danh tính, không còn gì để dò.
   - Tài khoản bị khóa: từ chối sau khi mật khẩu đúng, nói rõ là bị khóa; làm ở C1b.
   - Đã đăng nhập rồi đăng nhập lại, hoặc đăng nhập trên thiết bị khác: được, mỗi lần đăng nhập là một phiên gốc riêng (Q3).
   - Thử mật khẩu sai nhiều lần: giới hạn theo địa chỉ mạng ở cổng; khóa tạm tài khoản chưa làm ở C0 (mục 8, Q7).
5. Chưa lưu lịch sử đăng nhập ở C0 (Định danh, C1b). Đăng nhập thất bại được phát thành event để Định danh ghi về sau,
   **chỉ khi tìm ra tài khoản**: email không tồn tại không có chủ để ghi, và nếu ghi thì kẻ dò email làm phình lịch sử.
6. Phiên chảy sang các BC khác dưới dạng hợp đồng danh tính (mục 4.5).

### CAP-ATH-03 Giữ phiên (stage: H4)

1. Hệ thống, sau đăng nhập thành công.
2. Một lần đăng nhập tạo một phiên gốc ở nơi xác thực; mỗi portal người dùng vào tạo một phiên portal bên dưới phiên gốc đó
   (mục 4.6), và vào portal tiếp theo khi phiên gốc còn hiệu lực thì không đăng nhập lại. Xác thực xác nhận phiên khi được
   hỏi: phiên còn hiệu lực hay không, của tài khoản nào, vai gì.
3. Hết hạn thì người dùng về trạng thái chưa đăng nhập và đăng nhập lại được.
4. Đường không suôn sẻ: phiên hết hạn khi người dùng đang làm dở thao tác thì bị từ chối và yêu cầu đăng nhập lại
   (INV-ATH-08); cấp phiên lặp cho cùng (phiên gốc, portal) không tạo thêm phiên (INV-ATH-10).
5. Phiên đang hoạt động xem được qua Định danh (C1b); Xác thực không giữ lịch sử phiên đã kết thúc.
6. Hợp đồng danh tính cho các BC khác; event phiên được cấp, phiên kết thúc cho Định danh.

### CAP-ATH-04 Đăng xuất (stage: H5)

1. Người dùng.
2. Xác thực kết thúc **phiên gốc của lần đăng nhập hiện tại** và mọi phiên portal bên dưới nó, rồi báo các portal đó hủy phiên
   của mình. Lần đăng nhập khác của cùng tài khoản (thiết bị khác) giữ nguyên (Q3).
3. Người dùng về trạng thái chưa đăng nhập ở mọi portal của thiết bị đó.
4. Đường không suôn sẻ: đăng xuất khi phiên đã hết hạn hoặc chưa đăng nhập vẫn thành công (INV-ATH-05).
5. Không có lịch sử ở Xác thực; event phiên kết thúc đi sang Định danh.
6. Event phiên kết thúc chảy sang Định danh.

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
(INV-ATH-01, 02, 06, 07). Mật khẩu ban đầu không nằm trong mã nguồn (Q1).

## 6. Bất biến (INV)

| ID | Bất biến | Lý do | Từ stage |
|---|---|---|---|
| INV-ATH-01 | Một email, một tài khoản; so sánh không phân biệt chữ hoa chữ thường | Email là cách nhận ra người và nơi gửi thông báo về sau; trùng sẽ lẫn người | R1 |
| INV-ATH-02 | Mật khẩu không tồn tại ở dạng đọc được, ở bất kỳ đâu | Người dùng hay dùng lại mật khẩu; lộ ở đây là thiệt hại ở nơi khác | R2 |
| INV-ATH-03 | Đăng nhập sai email và sai mật khẩu cho cùng một kết quả, kể cả thời gian trả lời | Không để dò email nào có tài khoản | R3 |
| INV-ATH-04 | Gửi đăng ký lặp không tạo hai tài khoản | Bấm hai lần và mạng chậm là chuyện thường | R4 |
| INV-ATH-05 | Đăng xuất luôn thành công | Người dùng đạt trạng thái chưa đăng nhập dù lý do gì | R5 |
| INV-ATH-06 | Mỗi tài khoản có đúng một vai, thuộc tập đã quy định; đăng ký công khai luôn ra vai Người dùng, người gọi không chọn được vai | Nền của kiểm quyền; vai lạ thì BC khác không biết xử lý; tự chọn vai quản trị là mở cửa cho giả mạo | R6 |
| INV-ATH-07 | Tài khoản có vai nội bộ khi và chỉ khi email thuộc miền nội bộ; đăng ký công khai không dùng được email miền đó | Chống giả mạo tài khoản nội bộ (mục 4.4) | R7 |
| INV-ATH-08 | Phiên hết hạn hoặc bị hủy thì không còn hiệu lực | Mất ý nghĩa của hết hạn và đăng xuất nếu vẫn dùng được | mới |
| INV-ATH-09 | Vai của tài khoản không đổi sau khi tạo (ở C0) | Giữ một nguồn sự thật cho vai; đổi vai kéo theo nghiệp vụ chưa có | mới |
| INV-ATH-10 | Mỗi (phiên gốc, portal) có tối đa một phiên đang hoạt động | Đăng nhập chung không được nhân bản phiên; thu hồi một phiên là đủ cắt quyền | mới |
| INV-ATH-11 | Phiên portal chỉ được cấp cho tài khoản có vai được phép vào portal đó | Vai quyết định tài khoản vào portal nào; nếu mỗi portal tự chặn thì một portal quên chặn là lộ cửa | R8 |

## 7. Điểm nối với BC khác

| BC | Hướng | Nội dung | Chặng |
|---|---|---|---|
| Mọi BC | Xác thực công bố, BC khác đọc (giao diện đọc, không có lời gọi) | Hợp đồng danh tính: mã tài khoản, vai, mã phiên, hạn (mục 4.5) | C0 công bố; BC đọc từ chặng có BC đầu tiên |
| Định danh | Xác thực phát event | Tài khoản đã đăng ký (mã, email, vai); phiên được cấp (kèm hạn, địa chỉ mạng, thiết bị) và kết thúc (kèm lý do); đăng nhập thất bại; khóa, mở. Event được ghi từ C0, Định danh nhận từ C1b | C0 ghi, C1b nhận |
| Định danh | Định danh gọi Xác thực | Thu hồi phiên; bỏ tin cậy thiết bị | C1b |
| Khách hàng | Khách hàng trỏ về Xác thực | Hồ sơ trỏ về mã tài khoản; chỉ có với tài khoản Người dùng | C2a |
| Người bán | Người bán trỏ về Xác thực | Hồ sơ trỏ về mã tài khoản; Xác thực không biết trạng thái bán | C1a |
| Shipper | Shipper trỏ về Xác thực | Như trên | C3a |
| Nhật ký kiểm toán | Xác thực phát event | Khóa, mở tài khoản | C1b |
| Thông báo | Xác thực yêu cầu | Gửi thư (xác minh email, đặt lại mật khẩu) | Khi kéo vào |
| Khuyến mãi / Nhân sự | Họ trỏ về Xác thực | Liên kết tài khoản nội bộ với tài khoản mua (mục 4.4) | Sau này |

## 8. Chưa làm

Mỗi mục ghi trạng thái theo P3, giá trị, lý do xếp sau và điểm nối khi quay lại. "Trong scope" là phần root đã xếp vào
BC nhưng thuộc chặng sau. "Cắt tạm" theo B16. "Chưa có ở root" là phần phân tích này thấy cần ghi lại.

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Phân quyền theo vai | Trong scope (B16, điểm sâu chủ động chọn) | C1b | Mỗi BC chặn đúng thao tác theo vai | Chưa BC nào có thao tác cần chặn | Một hàm kiểm quyền dùng chung, đọc vai từ hợp đồng danh tính |
| Xác thực nhiều lớp | Trong scope (B16) | C1b | Người dùng tự nâng mức bảo mật tài khoản của mình; **không bắt buộc**, kể cả Quản trị | Không cần để chạy được | Một bước thêm sau xác thực mật khẩu |
| Yêu cầu bật xác thực nhiều lớp để có một số năng lực | Chưa có ở root (ý tưởng, phân tích sau) | Chưa xếp | Gắn điều kiện bảo mật với năng lực nhạy cảm (ví dụ nhận tiền, duyệt) mà không ép mọi người | Cần biết năng lực nào đáng yêu cầu; chưa có BC nào cần | Hàm kiểm quyền hỏi "tài khoản đã bật xác thực nhiều lớp chưa" |
| Khóa, mở tài khoản | Trong scope (B16) | C1b | Quản trị xử lý tài khoản vi phạm; khóa có hiệu lực ngay và kết thúc mọi phiên của tài khoản | Cần Nhật ký kiểm toán đi cùng | Quản trị thao tác trực tiếp tại Xác thực; event khóa/mở; trạng thái tài khoản |
| Thu hồi phiên từ xa | Trong scope (B16) | C1b | Cắt phiên khi nghi ngờ lộ tài khoản | Cần Định danh để người dùng thấy phiên | Giao diện thu hồi phiên; event phiên kết thúc |
| Tạo tài khoản nội bộ | Trong scope (B16) | C1b trở đi, khi vai nội bộ đầu tiên cần | Có người dùng nội bộ ngoài tài khoản có sẵn | Ở C0 chưa có vai nào ngoài Quản trị | Hợp đồng không phụ thuộc người gọi (mục 4.4) |
| Đổi mật khẩu | Trong scope (B16) | Có thể vào C0 (Q1) | Quản trị cần đổi mật khẩu ban đầu | Phụ thuộc quyết định ở Q1 | Cần phiên đang hoạt động; kết thúc mọi phiên khác |
| Thiết bị tin cậy | **Cắt tạm** (B16) | Sau xác thực nhiều lớp | Đỡ nhập mã mỗi lần trên máy quen | Chỉ là tiện ích trên xác thực nhiều lớp | Mã thiết bị ghi sẵn trên phiên (Q6) |
| Quên, đặt lại mật khẩu | **Cắt tạm** (B16) | Khi có Thông báo | Người dùng quên mật khẩu là chắc chắn; ở C0 người quên phải tạo tài khoản mới | Cần gửi thư | Thông báo |
| Xác minh email | **Cắt tạm** (B16) | Khi có Thông báo | Chống chiếm email (Q4) | Cần gửi thư | Thông báo; thêm trạng thái "chờ xác minh" |
| Giới hạn đăng nhập sai (khóa tạm tài khoản) | **Cắt tạm** (B16) | Khi cần | Chống dò mật khẩu | Cần chỗ đếm và chính sách; giới hạn tần suất ở cổng vẫn áp dụng (Q8) | Bước trước kiểm mật khẩu |
| Đăng nhập mạng xã hội hoặc số điện thoại | **Cắt tạm** (B16) | Cải thiện | Tiện cho người dùng; quen thuộc với người dùng sàn ở Việt Nam | Cần hạ tầng bên ngoài | Thêm cách đăng nhập mà không đổi tài khoản |
| Nhiều vai trên một tài khoản | Hoãn (chưa có ở root) | Không dự kiến | Trải nghiệm giống một số sàn | Không thêm bài học phân tán; kéo theo phiên mang tập vai, chuyển ngữ cảnh | Với mô hình hồ sơ năng lực (mục 4.2) có thể không bao giờ cần |
| Liên kết nhân viên với tài khoản mua | Không thuộc Xác thực | Khi có ưu đãi nhân viên | Cho phép ưu đãi nhân viên | Chưa có nghiệp vụ | Xem mục 4.4 |
| Nhân viên của gian hàng (tài khoản phụ có quyền riêng) | Không thuộc Xác thực | Người bán, nếu cần | Chủ gian hàng giao việc | Chưa có nghiệp vụ | Quan hệ thành viên giữa tài khoản và gian hàng ở BC Người bán |
| Portal Nhân sự, ERP (chấm công, tính lương, kế toán, mua hàng) | Ngoài phạm vi dự án | Không xếp | Mở rộng sàn thành hệ thống doanh nghiệp | Nghiệp vụ lớn, ít bài học phân tán thêm; người dùng và độ nhạy dữ liệu khác portal Vận hành | Mỗi portal là một dòng trong bảng portal, vai; vai nội bộ thêm theo portal; Nhân sự có thể gọi tạo tài khoản nội bộ (mục 4.4) |
| Shipper tự đăng ký hay Quản trị tạo | Root để ngỏ (B27) | C3a | — | Chốt khi làm | Hồ sơ Shipper trỏ về tài khoản |

## 9. Câu hỏi cần trả lời

Danh sách này là việc còn mở của phân tích. Câu nào được trả lời thì chuyển kết luận và lý do vào thân bài, rồi xóa
khỏi đây.

| # | Câu hỏi | Vì sao đáng hỏi | Đề xuất |
|---|---|---|---|
| Q1 | Quản trị có sẵn: mật khẩu ban đầu từ đâu, có bắt đổi ở lần đăng nhập đầu không | Mật khẩu cố định trong mã là rủi ro; roadmap yêu cầu giữ bí mật ngoài mã từ C0 | Mật khẩu băm nằm trong script khởi tạo của môi trường, không trong mã. Đổi mật khẩu cho tài khoản đang đăng nhập có thể vào C0 vì Quản trị cần |
| Q2 | Phiên hết hạn trong những trường hợp nào (không hoạt động, tuyệt đối) | Nghiệp vụ cần biết có hai kiểu hết hạn hay một | Hai kiểu; con số là quyết định kỹ thuật |
| Q3 | Một tài khoản đăng nhập trên nhiều thiết bị cùng lúc có được không; đăng xuất hủy phiên nào | Ảnh hưởng nghĩa của "đăng xuất" | Được. Mỗi lần đăng nhập là một phiên gốc riêng; đăng xuất hủy phiên gốc của lần đăng nhập hiện tại cùng mọi phiên portal bên dưới nó, không đụng lần đăng nhập ở thiết bị khác |
| Q4 | Chưa xác minh email thì chấp nhận rủi ro người đăng ký bằng email của người khác (chiếm email) | INV-ATH-01 giữ email duy nhất, nên chủ email thật sau đó không đăng ký được | Chấp nhận ở C0 vì xác minh email đang cắt tạm (mục 8) |
| Q5 | Đăng xuất từ xa có hiệu lực ngay hay trong tối đa bao lâu, với từng loại ứng dụng | Token đã cấp không thu hồi ngay được; web giữ phiên ở gateway cắt ngay, ứng dụng giữ token ở máy có độ trễ | Web ngay; di động trong tối đa thời hạn token truy cập; khóa tài khoản đưa vào danh sách chặn |
| Q6 | "Tin cậy thiết bị" để làm gì: bỏ qua xác thực nhiều lớp, hay là điều kiện để thu hồi thiết bị khác, hay cả hai | Ảnh hưởng nơi giữ dữ liệu tin cậy và luồng thu hồi | Chờ chốt khi vào C1b |
| Q7 | Đăng nhập có giới hạn tần suất ở C0 không, theo khóa nào | Mỗi lần đăng nhập tốn một lần kiểm mật khẩu, kể cả với email không tồn tại (INV-ATH-03); kẻ dò có thể đẩy CPU. Giới hạn theo địa chỉ mạng ở cổng không thấy email; khóa tạm tài khoản đang cắt tạm | Giới hạn theo địa chỉ mạng ở cổng (nhóm 1) là đủ cho C0. Giới hạn theo email ở bước đăng nhập (nhóm 2, `docs/global/3.technical/rate-limiting-layers.md`) quyết khi làm feature đăng nhập, cùng với số đo trần băm của bộ đo nền. Khóa tạm tài khoản vẫn cắt tạm |

## 10. Tham khảo từ hệ thống khác

Dùng để hình dung, không phải chứng cứ chính thức. Ngày truy cập `2026-10-08`.

| Nội dung | Hệ thống | Mức tin cậy | Nguồn |
|---|---|---|---|
| Đăng ký bán dùng chung đăng nhập với tài khoản mua, rồi qua luồng đăng ký riêng | Shopee | Khá (bài của bên thứ ba) | [Ginee](https://ginee.com/my/insights/how-to-register-shopee-seller/) |
| Đăng ký bán và đăng ký tài xế Flex đều có thể dùng tài khoản Amazon có sẵn; tài khoản bán luôn liên kết một tài khoản mua | Amazon | Khá | [Amazon IT](https://sell.amazon.it/en/vendere-online/guida-alla-registrazione), [Flex FAQ](https://flex.amazon.com/faqs/) |
| Một bảng người dùng có cờ nhân viên, nhân viên vẫn đặt hàng được | Saleor | Cao (tài liệu chính thức) | [Saleor docs](https://docs.saleor.io/developer/users/user-model) |
| Bảng quản trị tách khỏi bảng khách | Magento | Trung bình (bên thứ ba) | [Adobe Commerce](https://experienceleague.adobe.com/en/docs/commerce-operations/security-and-compliance/reference/data-m1) |
| Ưu đãi nhân viên gắn lên tài khoản mua, xác minh bằng email công ty hoặc mã | Magento (tiện ích), Amazon | Thấp | [Ecosire](https://ecosire.com/es/apps/magento-2/employee-staff-discount-program) |
| Thu hồi phiên: token làm mới vô hiệu ngay, token truy cập còn hiệu lực tới khi hết hạn; có thể rút ngắn bằng kiểm liên tục | Microsoft Entra | Trung bình | [Microsoft Q&A](https://learn.microsoft.com/en-us/answers/questions/2151518/revoking-azure-aad-refresh-token) |
| Thông báo ứng dụng khách khi phiên kết thúc bằng logout token mang mã phiên | Chuẩn OIDC Back-Channel Logout | Khá | [Auth0](https://auth0.com/docs/authenticate/login/logout/back-channel-logout) |
| Thu hồi token tiêu chuẩn; hướng thu hồi token tự mô tả | RFC 7009 | Cao | [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009) |

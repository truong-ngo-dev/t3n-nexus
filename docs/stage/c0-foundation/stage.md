# Chặng C0: Nền

Bước 1 của quy trình: phân tích nghiệp vụ cấp chặng. Nguồn: roadmap mục C0, overview §2, context map (B10, B11, B16,
B27, B28), `5-ui-analysis.md` §4, §5.

Phạm vi của tài liệu này: chặng **cần gì** và **vì sao**, tới mức đủ để biết BC nào tham gia và thế nào là xong. Phân
tích chi tiết (mô hình vai, dữ liệu tài khoản, INV, đường lỗi) giao cho `analysis.md` của BC. Mỗi phần kết thúc bằng một
dòng **Chốt**: kết luận và việc giao đi.

## 1. Yêu cầu của chặng

Roadmap yêu cầu C0 demo được việc đăng ký và đăng nhập qua giao diện, trên một khung bố cục. Đó là phát biểu về kết quả.
Câu hỏi của phân tích là: vì sao nền tảng cần điều này trước mọi thứ khác, và để nó đúng thì nghiệp vụ phải làm những gì.

## 2. Suy luận từ yêu cầu đến hành vi

**2.1. Vì sao cần danh tính trước tiên.** Nền tảng có nhiều người mua và nhiều người bán gặp nhau; nền tảng đứng ra bảo
đảm giao dịch (overview §2). Mọi việc sau này đều gắn với một người cụ thể: đơn của ai, sản phẩm của ai, giỏ của ai. Có
những việc chỉ một nhóm người được làm. Nền tảng không trả lời được "ai đang làm" và "người đó được làm gì" thì không
chạy được chặng nào sau. Vì thế cần một thứ đại diện cho một người trong hệ thống: **tài khoản**, kèm thông tin cho biết
người đó thuộc nhóm nào (**vai**).

> **Chốt:** cần khái niệm tài khoản có vai. → `analysis.md` của Xác thực định nghĩa tài khoản và vai, gồm tập vai và
> cách một tài khoản có vai.

**2.2. Ai có tài khoản bằng đường nào.** Rủi ro mỗi nhóm khác nhau nên đường vào khác nhau. Người dùng bình thường gây rủi
ro thấp, nền tảng cần nhiều người, nên **tự đăng ký**. Người vận hành nền tảng giữ quyền lớn, nếu đăng ký công khai được
thì ai cũng thành người vận hành, nên **không tự đăng ký**. Người đăng ký cũng không được chọn vai: nhóm của họ do hệ
thống gán. Với C0, chỉ cần hai đường: người dùng tự đăng ký, và tài khoản Quản trị có sẵn từ dữ liệu khởi tạo khi dựng hệ
thống. Các nhóm còn lại (người bán, shipper, nhân viên) cần có việc để làm và có bước duyệt hoặc tạo, nên đến ở các chặng
sau.

> **Chốt:** C0 có hai đường tạo tài khoản: tự đăng ký (một năng lực), và Quản trị có sẵn (dữ liệu khởi tạo, không phải
> thao tác của người dùng). → `analysis.md` mô tả năng lực đăng ký, ghi dữ liệu khởi tạo, và các đường còn lại ở mức tên
> kèm chặng.

**2.3. Cách hệ thống tin một người là ai.** Người đăng ký đặt một bí mật chỉ họ biết (mật khẩu). Khi quay lại, họ chứng
minh mình là người đó bằng bí mật ấy: **đăng nhập**. Hệ thống không thể đòi bí mật cho từng thao tác, nên phải nhớ rằng
người này đã được xác thực trong một khoảng thời gian: **phiên**. Phiên không được kéo dài vô hạn, vì máy dùng chung hoặc
bị mất sẽ để lộ tài khoản; người dùng phải tự kết thúc được: **đăng xuất**, và phiên cũng phải tự hết hạn.

Nền tảng có nhiều portal, và người dùng không nên đăng nhập lại ở từng portal. Vì vậy phiên có hai tầng: một phiên ở nơi
xác thực (một lần đăng nhập) và một phiên cho mỗi portal người dùng vào. Hệ quả: đăng xuất và thu hồi phải cắt được cả hai
tầng.

> **Chốt:** cần ba năng lực xác thực: đăng nhập, giữ phiên (hai tầng), đăng xuất. → `analysis.md` mô tả điều kiện hợp lệ,
> hai tầng phiên và trường hợp hết hạn. Cơ chế giữ phiên là quyết định kỹ thuật, để bước 5 và ADR.

**2.4. Xác thực khác với hồ sơ.** Xác thực trả lời "người này đăng nhập được không, với vai gì". Thông tin để hiển thị
người dùng (tên, ảnh), thiết bị và lịch sử đăng nhập đổi theo lý do khác (hiển thị, theo dõi) nên thuộc Định danh. Thông
tin giao hàng và hồ sơ năng lực bán hàng, giao hàng có vòng đời riêng nên thuộc Khách hàng, Người bán, Shipper. Hệ quả
cho C0: đăng ký chỉ thu thứ cần để xác thực (email, mật khẩu); giao diện hiện email vì chưa có tên hiển thị.

> **Chốt:** Xác thực không giữ hồ sơ hiển thị hay hồ sơ nghiệp vụ. → `analysis.md` ghi rõ dữ liệu nào ở Xác thực, dữ liệu
> nào ở Định danh và các BC khác, và điểm nối giữa chúng.

**2.5. Ai chặn việc gì.** Xác thực chỉ khẳng định "người này, vai này". Lời khẳng định đó được công bố thành một hợp đồng
danh tính mà các BC khác tự đọc, không gọi lại Xác thực ở mỗi yêu cầu. Việc cho hay không cho làm một thao tác thuộc BC
sở hữu thao tác đó (UI §5: quyền thật do BC kiểm, ẩn nút không thay cho kiểm quyền). Ở C0 chưa có BC nào có thao tác cần
chặn và chưa có BC nào đọc hợp đồng ngoài giao diện, nên vai chỉ được ghi nhận, trừ một việc của chính Xác thực: vai quyết
định tài khoản vào được **portal** nào (người dùng vào portal Cửa hàng, vai nội bộ vào portal Vận hành). Chặn theo vai ở các
thao tác của BC khác đến cùng BC đầu tiên cần nó (roadmap xếp phân quyền ở C1b).

> **Chốt:** C0 ghi nhận vai và dùng nó để quyết vào portal nào, không chặn thao tác của BC khác. → `analysis.md` ghi hợp đồng danh tính (nội dung, độ tươi, khóa chủ sở hữu)
> như điểm nối cho các BC sau; phân quyền để mức tên, ghi "C1b".

**2.6. Điều gì có thể sai.** Mỗi hành vi dưới đây cần một điều được bảo đảm. Phát biểu chính thức, kèm lý do, nằm ở
`analysis.md` dưới dạng INV.

| Tình huống | Điều cần bảo đảm |
|---|---|
| Hai người dùng chung một email, hoặc một người đăng ký hai lần với email khác chữ hoa | R1. Một email, một tài khoản |
| Dữ liệu bị lộ | R2. Mật khẩu không bao giờ tồn tại ở dạng đọc được |
| Kẻ xấu thử email để biết ai có tài khoản | R3. Đăng nhập sai email hay sai mật khẩu cho cùng một kết quả |
| Người dùng bấm đăng ký hai lần, hoặc mạng chậm nên gửi lại | R4. Gửi lặp không tạo hai tài khoản |
| Đăng xuất khi phiên đã hết hạn | R5. Đăng xuất luôn thành công |
| Vai ngoài tập quy định, hoặc người đăng ký tự chọn vai quản trị | R6. Mỗi tài khoản có đúng một vai thuộc tập đã quy định; người đăng ký công khai không chọn được vai |
| Người ngoài đăng ký bằng email trông như của nội bộ để giả mạo | R7. Email thuộc miền nội bộ chỉ dành cho tài khoản nội bộ |
| Người dùng thường cố vào portal Vận hành, hoặc tài khoản nội bộ vào portal Cửa hàng; hoặc một portal quên kiểm vai | R8. Phiên portal chỉ được cấp cho tài khoản có vai được phép vào portal đó |

> **Chốt:** tám điều cần bảo đảm. → `analysis.md` nâng chúng thành INV kèm lý do và bổ sung đường lỗi chi tiết. INV là đầu
> vào của aggregate ở `service.md` (bước 3) và của test (bước 6).

## 3. Hành vi và BC

| Hành vi | BC | Vì sao ở BC đó |
|---|---|---|
| H1. Đăng ký tài khoản (tự đăng ký) | Xác thực | Tạo ra danh tính |
| H2. Có sẵn tài khoản Quản trị | Xác thực | Như trên; là dữ liệu khởi tạo khi dựng hệ thống, không phải thao tác của người dùng |
| H3. Đăng nhập | Xác thực | Xác thực danh tính |
| H4. Giữ phiên | Xác thực | Phiên (ở nơi xác thực và ở từng portal) là hệ quả của xác thực |
| H5. Đăng xuất | Xác thực | Kết thúc phiên |
| H6. Ghi nhận vai | Xác thực | Vai là một phần của danh tính |

Mọi hành vi rơi vào một BC. Các BC có liên quan tới người dùng nhưng chưa làm ở C0, vì chưa có việc cần chúng:

| BC | Vì sao chưa cần |
|---|---|
| Định danh | Tên hiển thị, thiết bị và lịch sử đăng nhập chỉ có người xem khi Quản trị cần quản lý người dùng (C1b); cần bus sự kiện (C1a) để nhận event phiên |
| Khách hàng | Hồ sơ giao hàng chỉ cần khi đặt hàng (C2a) |
| Người bán | Đăng ký làm người bán và duyệt là phần của niêm yết (C1a) |
| Shipper | Đăng ký làm shipper là phần của giao hàng (C3a) |
| Nhật ký kiểm toán | Chỉ có ý nghĩa khi Quản trị bắt đầu khóa hoặc mở tài khoản (C1b) |
| Thông báo | Chỉ cần khi hệ thống phải gửi thư cho người dùng (xác minh email, đặt lại mật khẩu); C0 không gửi thư |

Mọi thứ ngoài H1–H6 không thuộc C0. Phần Xác thực chưa làm (phân quyền, khóa/mở tài khoản, tạo tài khoản nội bộ...)
được ghi ở mục "Chưa làm" của [`analysis.md`](../../service/oauth2-service/analysis.md).

Giao diện không phải BC mà là nơi người dùng thực hiện H1, H3, H5. C0 cần giao diện cho ba hành vi này và một khung trang
cho người đã đăng nhập, để chứng minh người dùng đã vào được và để các portal sau dùng lại bố cục.

> **Chốt:** C0 có một BC là Xác thực, sáu hành vi. → Bước 2 chỉ viết `analysis.md` cho Xác thực, phần thuộc C0 ở mức
> mịn (mỗi hành vi H1–H6 thành một năng lực). Các BC ở bảng trên chưa có `analysis.md` ở chặng này.

## 4. Kịch bản chấp nhận

Mỗi kịch bản là một cách chứng minh hành vi hoặc điều cần bảo đảm ở mục 2 đã đúng.

| # | Kịch bản | Chứng minh |
|---|---|---|
| S1 | Khách vãng lai đăng ký, đăng nhập, thấy mình đã đăng nhập, rồi đăng xuất; sau đó không vào được trang cần đăng nhập | H1, H3, H4, H5 |
| S2 | Đăng ký bằng email đã có (kể cả khác chữ hoa) hoặc email thuộc miền nội bộ bị từ chối với lý do rõ; bấm đăng ký hai lần liên tiếp chỉ ra một tài khoản | R1, R4, R7 |
| S3 | Đăng nhập sai email và đăng nhập sai mật khẩu nhận cùng một thông báo | R3 |
| S4 | Đăng nhập bằng tài khoản Quản trị có sẵn, hệ thống nhận ra vai Quản trị; tài khoản vừa tự đăng ký có vai khác Quản trị, kể cả khi yêu cầu đăng ký gửi kèm vai quản trị | H2, H6, R6 |
| S5 | Đăng xuất khi phiên đã hết hạn vẫn đưa người dùng về trạng thái chưa đăng nhập | R5 |
| S6 | Tài khoản Người dùng vào portal Vận hành: đăng nhập thành công nhưng thấy "không có quyền" và không có phiên portal; nút quay lại Cửa hàng đưa vào được mà không hỏi lại mật khẩu. Tài khoản Quản trị vào Cửa hàng cũng vậy | R8 |

R2 không có kịch bản nghiệp vụ vì không quan sát được từ ngoài; nó kiểm bằng test ở bước thiết kế. Tương tự, việc phiên hết
hạn và việc đăng xuất cắt cả hai tầng phiên kiểm bằng test từ bất biến của `analysis.md`.

> **Chốt:** sáu kịch bản (cùng tám điều cần bảo đảm, trừ R2 kiểm bằng test) là điều kiện "xong" của phần nghiệp vụ. →
> `analysis.md` dùng chúng để kiểm tra mỗi năng lực có ít nhất một kịch bản chứng minh. Bước 4 dùng chúng để cắt feature
> theo lát dọc; bước 6 viết test từ chúng.

## 5. Luồng nghiệp vụ

C0 chỉ có một BC nên chưa có luồng xuyên BC. Ba luồng nằm trọn trong Xác thực:

| Luồng | Bước |
|---|---|
| Đăng ký | Khách vãng lai gửi email và mật khẩu → Xác thực kiểm email chưa dùng và không thuộc miền nội bộ (R1, R7) và mật khẩu hợp lệ → tạo tài khoản vai Người dùng, đang hoạt động → báo kết quả |
| Đăng nhập | Người dùng vào một portal, gửi email và mật khẩu → Xác thực kiểm thông tin (R3), tạo phiên ở nơi xác thực → kiểm vai được vào portal đích (R8), tạo phiên portal → người dùng vào portal. Đã có phiên ở nơi xác thực thì vào portal khác không hỏi lại mật khẩu |
| Đăng xuất | Người dùng yêu cầu → phiên ở nơi xác thực của lần đăng nhập này và các phiên portal bên dưới bị hủy (R5), lần đăng nhập ở thiết bị khác giữ nguyên → người dùng về trạng thái chưa đăng nhập |

> **Chốt:** cả ba luồng nằm trong một BC, chưa có BC nào nhận kết quả. → `analysis.md` lấy ba luồng làm khung viết từng
> năng lực. `service.md` ở C0 ghi sẵn các event "tài khoản đã đăng ký", "phiên được cấp", "phiên kết thúc" (chưa có bên
> nhận, Định danh vào ở C1b) và hợp đồng danh tính mà các BC sau sẽ đọc (2.5).

## 6. Điều kiện ngoài nghiệp vụ của chặng

Roadmap còn ghi cho C0 những điều kiện không thuộc nghiệp vụ của BC nào:
- **Kỹ thuật:** lỗi lần ra được nguyên nhân từ log; có số trần của máy (bộ đo nền); chạy cục bộ theo lát cắt; cổng truy cập.
- **Giao diện:** khung frontend gồm cấu trúc, bố cục, bảo vệ truy cập và bộ thành phần dùng chung cho các portal web. Bảo vệ
  truy cập ở giao diện chỉ là trải nghiệm; quyền thật do BC kiểm (2.5).

Chúng không đi qua analysis của BC.

> **Chốt:** các điều kiện này không thuộc Xác thực. → Bước 2 bỏ qua chúng. Bước 4 đưa chúng vào `00-features.md` như feature
> kỹ thuật (nguồn là NFR và quy ước chung) và feature giao diện (nguồn là `5-ui-analysis.md`). Mục tiêu hiểu của
> frontend (cơ chế phát hiện thay đổi, vòng đời) ghi vào phần điều học được của `00-features.md`. Các quyết định kỹ thuật phát
> sinh quanh đăng nhập vào ADR ở bước 5.

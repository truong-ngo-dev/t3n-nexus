# Phân tích giao diện

> Bản nháp, chờ duyệt.

## 1. File này để làm gì

File này trả lời bốn câu hỏi về phía giao diện:

- Ai dùng giao diện, dùng trong hoàn cảnh nào.
- Chia thành mấy ứng dụng, vì sao.
- Mỗi ứng dụng có những nhóm màn hình nào, mỗi nhóm lấy dữ liệu từ BC nào.
- Những vấn đề frontend của hệ thống lớn (T4 ở `1-project-overview.md`) xuất hiện ở màn hình nào, và thế nào thì coi là đã xử lý
  đúng.

Mức chi tiết: tên nhóm màn hình, chưa vẽ bố cục, chưa có luồng bấm từng bước. Tên BC và khái niệm tra ở `4-glossary.md`; quan hệ
giữa các BC ở `5-context-map.md`. Làm màn hình nào trước do roadmap quyết.

## 2. Ai dùng giao diện

| Người dùng                     | Họ làm gì                                                        | Hoàn cảnh                                                                                     | Thiết bị                         |
|--------------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|----------------------------------|
| Khách vãng lai và người mua    | Xem, tìm, bỏ giỏ, đặt, trả tiền, theo dõi đơn                    | Lúc nào cũng có thể, thường trên điện thoại; ít kiên nhẫn, bỏ đi nếu trang chậm hoặc khó hiểu | Điện thoại và máy tính, như nhau |
| Người bán                      | Nộp hồ sơ, đăng sản phẩm, giữ tồn, xử lý đơn                     | Làm việc có chủ đích, nhiều dữ liệu dạng bảng và biểu mẫu dài                                 | Chủ yếu máy tính                 |
| Quản trị nền tảng              | Duyệt người bán, khóa tài khoản, chặn sản phẩm, quản lý danh mục | Xử lý theo hàng chờ, nhiều bộ lọc                                                             | Máy tính                         |
| Người giao (nghiệp vụ mở rộng) | Nhận đơn được phân, xác nhận giao, thu tiền COD, gửi vị trí      | Ngoài đường, tay bận, mạng chập chờn                                                          | Điện thoại                       |

Bên thanh toán không có giao diện của nền tảng: người mua được chuyển sang trang của bên thanh toán rồi quay về. Người chạy thử tải
(chính người làm dự án) dùng công cụ giả lập và xem số đo; đó là công cụ, không phải ứng dụng của sản phẩm, nên không tính ở đây.

## 3. Chia ứng dụng

### 3.1 Khi nào tách thành ứng dụng riêng

Hai nhóm người dùng ở chung một ứng dụng khi chúng giống nhau ở cả ba điểm dưới đây; khác một điểm là đủ lý do tách:

1. **Quyền.** Mã giao diện tải về trình duyệt ai cũng đọc được. Chức năng quản trị không nên nằm trong gói mà người mua tải về, dù
   nút bị ẩn.
2. **Cách làm việc.** Người mua lướt và quyết nhanh; người bán và quản trị làm việc với bảng và biểu mẫu dài. Một bố cục khó phục vụ
   tốt cả hai.
3. **Thiết bị.** Người giao chỉ dùng điện thoại ngoài đường, khác hẳn người ngồi bàn.

Các sàn lớn cũng làm như vậy: trang mua hàng và trang dành cho người bán là hai nơi riêng, dù dùng chung tài khoản.

### 3.2 Quyết định

**Ba ứng dụng web cho nghiệp vụ lõi**, thêm một ứng dụng thứ tư khi làm tới nghiệp vụ mở rộng có người giao:

| Ứng dụng      | Cho ai                      | Ghi chú                                                                                                       |
|---------------|-----------------------------|---------------------------------------------------------------------------------------------------------------|
| **Cửa hàng**  | Khách vãng lai, người mua   | Dùng tốt trên cả điện thoại và máy tính                                                                       |
| **Người bán** | Người dùng muốn bán hàng    | Cùng tài khoản với Cửa hàng; một người có thể vừa mua vừa bán                                                 |
| **Quản trị**  | Quản trị nền tảng (`ADMIN`) | Không ai ngoài quản trị tải được mã của ứng dụng này                                                          |
| Người giao    | `Courier` (mở rộng)         | Chỉ làm khi làm tới "Kho và vận hành giao hàng". Chạy trên điện thoại; dạng web hay ứng dụng gốc quyết lúc đó |

Nghiệp vụ lõi không có người giao thật: giao nhận là giả lập (`2-business-analysis.md` mục 2.2), nên ba ứng dụng là đủ để luồng mua
hàng chạy từ đầu đến cuối.

Nhiều ứng dụng thì tốn công hơn một ứng dụng. Cái giá đó giảm bằng cách **dùng chung phần nền**: đăng nhập, phiên, gọi API, mô hình
dữ liệu dùng chung, và một bộ thành phần giao diện theo Material. Màn hình thì không dùng chung cho tới khi thật sự có hai nơi cần
cùng một màn hình. Làm phần nền dùng chung cho đúng cũng chính là một vấn đề của T4 (mục 5.5).

## 4. Nhóm màn hình theo ứng dụng

Cột "BC" ghi BC sở hữu dữ liệu mà màn hình đọc hoặc ghi. Dòng in nghiêng thuộc nghiệp vụ mở rộng.

### 4.1 Cửa hàng

| Nhóm màn hình                                                    | BC                                                 |
|------------------------------------------------------------------|----------------------------------------------------|
| Đăng ký, đăng nhập, đăng xuất                                    | `Authentication`                                   |
| Thông tin cá nhân, sổ địa chỉ                                    | `Identity`, `Customer`                             |
| Duyệt theo danh mục, tìm và lọc                                  | `Search`                                           |
| Chi tiết sản phẩm: biến thể, giá, ảnh, còn hàng hay không        | `Catalog`, `Media`, `Inventory`                    |
| Giỏ hàng, kể cả khi chưa đăng nhập                               | `Cart`                                             |
| Đặt hàng: chọn địa chỉ, xem phí giao, chọn cách trả              | `Ordering`, `Customer`, `Shipping`, `Payment`      |
| Chờ thanh toán và kết quả thanh toán                             | `Payment`, `Ordering`                              |
| Đơn của tôi: danh sách, chi tiết, trạng thái giao, hủy           | `Ordering`, `Shipping`                             |
| Thông báo                                                        | `Notification`                                     |
| *Nhập voucher, trang flash sale*                                 | *`Promotion`*                                      |
| *Yêu cầu trả hàng*                                               | *`Returns`*                                        |
| *Chat với người bán*                                             | *`Chat`*                                           |
| *Theo dõi vị trí người giao*                                     | *`Fulfillment`*                                    |

### 4.2 Người bán

| Nhóm màn hình                                                   | BC                     |
|-----------------------------------------------------------------|------------------------|
| Nộp hồ sơ, xem trạng thái duyệt, sửa và nộp lại khi bị từ chối  | `Seller`               |
| Gian hàng                                                       | `Seller`               |
| Sản phẩm: danh sách, tạo và sửa nhiều biến thể, ảnh, đăng và gỡ | `Catalog`, `Media`     |
| Tồn kho theo từng SKU                                           | `Inventory`            |
| Đơn: danh sách lọc theo trạng thái, xác nhận, bàn giao          | `Ordering`, `Shipping` |
| Thông báo                                                       | `Notification`         |
| *Báo cáo bán hàng*                                              | *`Reporting`*          |
| *Duyệt yêu cầu trả hàng*                                        | *`Returns`*            |
| *Số dư và chi trả*                                              | *`Ledger`*             |
| *Chat với người mua*                                            | *`Chat`*               |

### 4.3 Quản trị

| Nhóm màn hình                                                    | BC                                  |
|------------------------------------------------------------------|-------------------------------------|
| Hàng chờ duyệt người bán: xem hồ sơ, duyệt, từ chối kèm lý do    | `Seller`                            |
| Tài khoản: tìm, xem, khóa và mở khóa                             | `Authentication`, `Identity`        |
| Sản phẩm: tìm, chặn và bỏ chặn                                   | `Catalog`                           |
| Danh mục và thuộc tính                                           | `Catalog`                           |
| *Tạo đợt flash sale, phát hành voucher*                          | *`Promotion`*                       |
| *Báo cáo toàn sàn*                                               | *`Reporting`*                       |
| *Đối soát tiền*                                                  | *`Ledger`*                          |
| *Kho, người giao, phân công*                                     | *`Fulfillment`*                     |

Ứng dụng Quản trị không có BC riêng (`5-context-map.md` mục 2): mỗi thao tác gọi thẳng BC sở hữu dữ liệu.

### 4.4 Người giao (mở rộng)

| Nhóm màn hình                                  | BC                              |
|------------------------------------------------|---------------------------------|
| Đăng nhập, bật và tắt sẵn sàng nhận việc       | `Authentication`, `Fulfillment` |
| Đơn được phân                                  | `Fulfillment`, `Shipping`       |
| Xác nhận giao, thu tiền COD, báo giao thất bại | `Shipping`, `Fulfillment`       |
| Gửi vị trí                                     | `Fulfillment`                   |

## 5. Vấn đề frontend và nơi nó xuất hiện

Mục này đi qua từng vấn đề ở `2-business-analysis.md` mục 3.4. Mỗi vấn đề trả lời bốn câu:

- **Ở đâu:** màn hình cụ thể làm bài toán này hiện ra.
- **Làm đơn giản thì hỏng ra sao:** cách làm đầu tiên ai cũng nghĩ ra, và nó sai ở chỗ nào.
- **Đúng nghĩa là gì:** điều phải giữ được.
- **Kiểm thế nào:** cách tạo lại tình huống xấu để xem giao diện có giữ được điều đó không.

Nhiều vấn đề là nửa phía trước của một bài toán backend ở `3-non-functional-requirement.md`. Backend làm đúng mà giao diện làm sai
thì người dùng vẫn thấy sai, ví dụ máy chủ chống được đơn trùng nhưng giao diện báo lỗi cho lần bấm thứ hai, khiến người mua tưởng
đặt hỏng.

### 5.1 Dữ liệu đến bất đồng bộ

**Ở đâu.** Trang chờ thanh toán. Người mua trả tiền ở trang của bên thanh toán rồi được chuyển về. Kết quả thật không đến theo
đường chuyển về đó mà đến sau, qua webhook vào `Payment` (`5-context-map.md` mục 5.1).

**Làm đơn giản thì hỏng ra sao.** Thấy người mua được chuyển về là báo "thanh toán thành công". Nhưng tiền có thể chưa trừ,
hoặc bị từ chối, hoặc webhook đến trước cả lúc người mua quay về.

**Đúng nghĩa là gì.** Giao diện chỉ hiện điều hệ thống đã biết: "đang chờ xác nhận" cho tới khi đơn đổi trạng thái thật. Chờ có giới
hạn; quá lâu thì nói rõ là chưa có kết quả, và người mua rời trang rồi quay lại vẫn thấy đúng trạng thái.

**Kiểm thế nào.** Giả lập webhook đến trễ, đến trước lúc chuyển về, và không đến. Cả ba trường hợp giao diện đều không báo sai.

### 5.2 Gửi trùng và thử lại an toàn

**Ở đâu.** Nút đặt hàng, nút thanh toán; về sau là nút xác nhận giao của người giao.

**Làm đơn giản thì hỏng ra sao.** Khóa nút sau khi bấm. Cách này chặn được bấm đúp, nhưng không chặn được trường hợp mạng rớt sau khi
yêu cầu đã tới máy chủ: người mua không biết đơn đã tạo chưa, bấm lại, và ra hai đơn.

**Đúng nghĩa là gì.** Mỗi lần người dùng **có ý định** đặt một đơn mang một mã chống lặp, và mã đó được giữ qua mọi lần thử lại của
cùng ý định. Thử lại thì nhận lại đúng kết quả của lần đầu, không báo lỗi trùng. Khi không chắc kết quả, giao diện hỏi lại máy chủ
thay vì đoán.

**Kiểm thế nào.** Bấm đúp; cắt mạng ngay sau khi gửi rồi thử lại; tải lại trang giữa chừng. Mỗi trường hợp ra đúng một đơn, và người
mua thấy đơn đó.

### 5.3 Lỗi từng phần

**Ở đâu.** Trang chi tiết sản phẩm. Thông tin chính đến từ `Catalog`, nhưng còn hàng đến từ `Inventory`, ảnh từ `Media`, phí giao dự
kiến từ `Shipping`.

**Làm đơn giản thì hỏng ra sao.** Chờ mọi phần về rồi mới vẽ trang; một phần lỗi thì cả trang báo lỗi. Một BC phụ hỏng kéo sập cả
trang chính.

**Đúng nghĩa là gì.** Phần chính hiện được thì hiện. Phần lỗi báo lỗi ngay tại chỗ của nó và thử lại riêng được. Riêng thông tin
quyết định việc mua (còn hàng) thì không được đoán: không biết thì nói không biết, không mặc định là còn. Đây là phía giao diện của
yêu cầu "mất phần phụ không làm sập luồng mua" (`3-non-functional-requirement.md` mục 5).

**Kiểm thế nào.** Tắt lần lượt từng BC phụ trong lúc mở trang.

### 5.4 Cập nhật thời gian thực

**Ở đâu.** Thông báo và trạng thái đơn ở cả ba ứng dụng; về sau là chat và vị trí người giao.

**Làm đơn giản thì hỏng ra sao.** Mở một kết nối và tin rằng nó không bao giờ đứt. Thực tế mạng đứt, máy ngủ, tab bị trình duyệt cho
nghỉ. Khi nối lại, tin trong lúc đứt bị mất; nếu nối lại bằng cách tải lại hết thì tin bị trùng. Khi máy chủ khởi động lại, mọi
máy khách nối lại cùng một lúc và dồn tải lên máy chủ.

**Đúng nghĩa là gì.** Nối lại tự động, giãn cách tăng dần và có ngẫu nhiên để các máy khách không nối lại cùng lúc. Sau khi nối lại,
xin bù những tin đã lỡ kể từ tin cuối đã nhận. Tin trùng bị bỏ; tin hiện đúng thứ tự.

**Kiểm thế nào.** Ngắt mạng một lúc rồi nối lại: không mất tin, không trùng tin. Khởi động lại máy chủ khi có nhiều máy khách đang
nối: các lần nối lại trải ra theo thời gian. Phía máy chủ của bài toán này (giữ nhiều kết nối) ở `3-non-functional-requirement.md`
mục 3.7.

### 5.5 Nhiều ứng dụng dùng chung nền

**Ở đâu.** Ba ứng dụng dùng chung tài khoản, đăng nhập, phiên và cách gọi API (mục 3.2).

**Làm đơn giản thì hỏng ra sao.** Mỗi ứng dụng tự làm phần đăng nhập và gọi API của mình. Ba bản dần lệch nhau: một ứng dụng xử lý
hết phiên kiểu này, ứng dụng kia kiểu khác. Hoặc giữ token trong bộ nhớ của trình duyệt, nơi mã độc chèn vào trang đọc được.

**Đúng nghĩa là gì.** Phần nền làm một lần, ba ứng dụng dùng chung. Token không để mã chạy trong trang đọc được. Phiên hết hạn giữa
lúc đang điền biểu mẫu thì gia hạn được hoặc đăng nhập lại mà không mất dữ liệu đã nhập. Ứng dụng chỉ hiện chức năng hợp với vai,
nhưng việc kiểm quyền thật vẫn do BC làm: ẩn nút không thay cho kiểm quyền.

**Kiểm thế nào.** Để phiên hết hạn khi biểu mẫu đang nhập dở. Đăng xuất ở một ứng dụng rồi xem ứng dụng kia. Gọi thẳng API của quản
trị bằng tài khoản người mua.

### 5.6 Trạng thái nhiều nguồn

**Ở đâu.** Giỏ hàng. Khách chưa đăng nhập vẫn bỏ được hàng vào giỏ (`GuestCart`). Khi đăng nhập, giỏ vãng lai phải gộp vào giỏ của
tài khoản, mà giỏ đó có thể đã có hàng từ máy khác.

**Làm đơn giản thì hỏng ra sao.** Đăng nhập xong thì lấy giỏ của tài khoản đè lên, mất hàng vừa chọn. Hoặc cộng dồn mà không báo,
người mua thấy số lượng lạ. Mở hai tab thì mỗi tab giữ một bản giỏ riêng và ghi đè nhau.

**Đúng nghĩa là gì.** Quy tắc gộp rõ ràng và người mua thấy được kết quả gộp. Máy chủ là nguồn đúng duy nhất; giao diện chỉ giữ bản
sao và làm mới khi bản sao có thể đã cũ.

**Kiểm thế nào.** Chọn hàng khi chưa đăng nhập, rồi đăng nhập vào tài khoản đã có giỏ. Sửa giỏ ở hai tab cùng lúc.

### 5.7 Biểu mẫu lớn, lỗi máy chủ gắn vào ô

**Ở đâu.** Hồ sơ người bán: các ô cần nhập đổi theo loại người bán (`SellerType`). Đăng sản phẩm: nhiều biến thể, mỗi biến thể có
giá, tồn và ảnh riêng.

**Làm đơn giản thì hỏng ra sao.** Máy chủ từ chối thì hiện một dòng lỗi chung ở đầu trang. Người dùng không biết ô nào sai, phải dò
lại cả biểu mẫu dài. Hoặc gửi lỗi xong thì biểu mẫu bị xóa trắng.

**Đúng nghĩa là gì.** Lỗi máy chủ trả về theo từng ô và hiện ngay tại ô đó; con trỏ nhảy tới ô lỗi đầu tiên. Quy tắc kiểm ở giao
diện khớp với quy tắc ở máy chủ, để người dùng ít khi phải chờ máy chủ mới biết sai. Gửi lỗi thì dữ liệu đã nhập vẫn còn.

**Kiểm thế nào.** Gửi biểu mẫu sai ở một ô mà chỉ máy chủ mới bắt được (ví dụ trùng mã số thuế). Đổi loại người bán giữa chừng.

### 5.8 Danh sách lớn: lọc, phân trang, trạng thái trên URL

**Ở đâu.** Trang tìm kiếm của Cửa hàng; danh sách đơn của Người bán; hàng chờ duyệt của Quản trị.

**Làm đơn giản thì hỏng ra sao.** Bộ lọc chỉ nằm trong bộ nhớ của trang: tải lại hay bấm quay lại là mất, không gửi được đường dẫn
cho người khác. Người mua gõ nhanh, kết quả của từ khóa cũ về sau kết quả của từ khóa mới và đè lên. Lật trang bằng số trang thì
danh sách thay đổi giữa hai lần lật làm trùng hoặc sót dòng.

**Đúng nghĩa là gì.** Bộ lọc, sắp xếp và vị trí trang nằm trên URL, nên tải lại, quay lại và chia sẻ đều ra đúng màn hình đó. Chỉ
kết quả của yêu cầu mới nhất được hiện. Danh sách thay đổi liên tục (đơn của người bán) lật trang theo con trỏ, khớp với cách phía
máy chủ giải bài toán trang sâu (`3-non-functional-requirement.md` mục 3.9).

**Kiểm thế nào.** Lọc, sang trang 3, tải lại. Gõ nhanh khi mạng bị làm chậm. Thêm đơn mới trong lúc đang lật trang.

### 5.9 Hiệu năng tải trang

**Ở đâu.** Trang tìm kiếm và trang sản phẩm, những trang người mua mở đầu tiên, thường trên điện thoại.

**Làm đơn giản thì hỏng ra sao.** Gói mã của mọi màn hình vào một tệp tải ngay từ đầu; ảnh gốc to tải nguyên cho ô nhỏ. Trang đầu
chậm dần khi ứng dụng lớn lên mà không ai để ý.

**Đúng nghĩa là gì.** Mỗi màn hình chỉ tải mã của nó. Ảnh tải đúng cỡ cần hiện. Kích thước gói tải ban đầu có ngân sách, và vượt
ngân sách thì bản dựng báo. Mức "đủ nhanh" giữ ở mức sàn, không đặt mục tiêu điểm số tối ưu (overview mục 6).

**Kiểm thế nào.** Đo kích thước gói ban đầu và thời gian mở trang khi mạng bị làm chậm kiểu điện thoại; ghi lại mỗi lần thêm màn
hình lớn.

### 5.10 Vấn đề của nghiệp vụ mở rộng

Chỉ tính khi làm tới nghiệp vụ đó:

- **Flash sale** (`Promotion`): đồng hồ đếm ngược theo giờ máy chủ, không theo đồng hồ của máy người mua, vì máy có thể lệch giờ. Bị
  từ chối vì hết suất thì báo rõ là hết; bị từ chối vì quá tải thì thử lại có giãn cách, không để người mua bấm liên tục.
- **Báo cáo** (`Reporting`): bảng hàng chục nghìn dòng cuộn mượt mà bộ nhớ trình duyệt không tăng theo số dòng; xuất tệp lớn không
  nạp cả tập vào bộ nhớ. Báo cáo ghi rõ số liệu tính tới lúc nào (`3-non-functional-requirement.md` mục 3.10).
- **Chat** (`Chat`): như 5.4, cộng thêm tin đang gửi và tin gửi lỗi hiện khác tin đã gửi.
- **Vị trí người giao** (`Fulfillment`): điểm vị trí đến dày; gộp lại trước khi vẽ để bản đồ không giật.
- **Ứng dụng Người giao**: mạng yếu là chuyện thường, nên thao tác được giữ trong hàng đợi trên máy và gửi lại khi có mạng; xác nhận
  giao không bao giờ ghi hai lần (dùng cách ở 5.2).

## 6. Sàn chung cho mọi màn hình

Những điều dưới đây không phải bài toán để học mà là mức tối thiểu mọi màn hình phải có:

- **Theo Material, tối giản.** Bố cục hợp lý: ô cùng hàng đều nhau, khoảng cách nhất quán, không có ô sát nhau hoặc lệch cỡ không
  chủ ý (overview mục 5).
- **Đủ trạng thái.** Mỗi màn hình có trạng thái đang tải, rỗng, lỗi; không có màn hình trắng.
- **Phân biệt hai loại lỗi.** Lỗi do người dùng (nhập sai, hết hàng) thì nói họ cần làm gì. Lỗi do hệ thống thì nói là lỗi hệ thống,
  cho thử lại, và hiện mã truy vết để lần ra trong log (`3-non-functional-requirement.md` mục 5).
- **Tiếng Việt, tiền VND.** Một ngôn ngữ, một loại tiền (`2-business-analysis.md` mục 2.2).
- **Cửa hàng dùng được trên điện thoại.** Người bán và Quản trị ưu tiên máy tính, nhưng không vỡ bố cục trên màn hình nhỏ.

## 7. Ngoài phạm vi

| Dòng                                                    | Lý do                                                                         |
|---------------------------------------------------------|-------------------------------------------------------------------------------|
| Dựng trang sẵn ở máy chủ để máy tìm kiếm (Google) đọc   | Phục vụ tiếp thị, không phục vụ T1–T4                                         |
| Ứng dụng gốc trên điện thoại cho người mua và người bán | Cửa hàng đã dùng được trên điện thoại; thêm một bề mặt mà không thêm bài toán |
| Dùng được khi mất mạng hẳn, ở Cửa hàng                  | Người mua mất mạng thì không mua được; chỉ ứng dụng Người giao cần            |
| Nhiều ngôn ngữ, nhiều loại tiền                         | Một thị trường (`2-business-analysis.md` mục 2.2)                             |
| Hiệu ứng, thiết kế riêng, tối ưu điểm số tải trang      | Overview mục 6                                                                |

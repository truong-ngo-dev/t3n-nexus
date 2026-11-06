# Phân tích nghiệp vụ: Nhật ký kiểm toán

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-10-23` · **Mã BC:** `AUD` (tiền tố cho ID)
>
> Tài liệu giữ nghiệp vụ **kèm lý do**, phản ánh trạng thái hiện tại, không ghi lịch sử thay đổi. Bản này phủ phần thuộc chặng C1a (hai nhóm hành động quản trị); các nhóm hành động của chặng sau ở mức tên (mục 12).
>
> **Nguồn:** `4-context-map.md` B25, `7-roadmap.md` C1a và C1b · **Chặng:** [`stage.md` của C1a](../../stage/c1a-listing/stage.md)

## 1. Nhiệm vụ và ranh giới

Nhật ký kiểm toán là **sổ chỉ-ghi-thêm về những hành động có chủ thể và có hậu quả**, để trả lời sau này: *ai, lúc nào, làm gì, lên đối tượng nào, vì sao*. Nó:
- nhận bản ghi hành động từ các BC khác và lưu lại;
- cho quản trị tra cứu.

**Tiêu chí để một hành động vào nhật ký (cả ba cùng đúng):**
1. có **chủ thể xác định**: một tài khoản, hoặc hệ thống (job);
2. tác động lên **người khác hoặc dữ liệu chung**, không phải dữ liệu riêng của chính chủ;
3. **đã thành công** và có hậu quả cần truy khi tranh chấp.

**Điểm sâu:** không có. Khối lượng thấp (hành động của người có quyền hạn), không nằm trên đường nóng. Giá trị của BC nằm ở tính **bất biến** và **không lộ dữ liệu định danh**, không ở tốc độ.

Nhật ký kiểm toán **không** làm:

| Việc | Thuộc | Lý do |
|---|---|---|
| Bút toán, số dư, dấu vết thao tác tiền | Sổ cái, Thanh toán | Là nguồn sự thật của tiền, ghi cùng giao dịch, khối lượng lớn, sổ phải cân bằng. Nhật ký chỉ ghi việc *ai can thiệp bằng tay* kèm mã bút toán, không giữ số tiền |
| Điểm thưởng | Không dự kiến (context map: ngoài phạm vi) | Nếu làm sau thì là sổ cộng trừ riêng như Sổ cái |
| Lịch sử trạng thái đơn, tồn kho | Đơn hàng, Tồn kho | Dữ liệu nghiệp vụ của BC sở hữu |
| Lịch sử đăng nhập, thiết bị, đổi mật khẩu của người dùng | Xác thực, Định danh (C3c) | Dữ liệu của chính chủ tài khoản, mục đích bảo mật |
| Thử bị từ chối quyền, đăng nhập thất bại, chặn tần suất | Log ứng dụng | Sự kiện bảo mật, chưa có hậu quả; có thời hạn giữ |
| Chẩn đoán lỗi | Log ứng dụng | Mất đi không sai nghiệp vụ |
| Phát hiện gian lận, báo cáo, cảnh báo theo mẫu | BC khác đọc nhật ký sau này | Nhật ký chỉ giữ dữ liệu |
| Trạng thái hiện tại của thực thể (ví dụ ai đã duyệt hồ sơ này) | BC sở hữu thực thể | Nhật ký giữ *chuỗi hành động*, BC gốc giữ trạng thái hiện tại |

## 2. Tại sao cần một BC riêng

| Nhu cầu | Nếu không tách |
|---|---|
| Nhiều BC cùng có hành động cần kiểm toán (Người bán, Xác thực, Danh mục hàng, về sau Giao nhận, Sổ cái, cấu hình) | Mỗi BC tự giữ một sổ: không tra cứu chéo được ("mọi việc của quản trị A hôm nay"), mỗi sổ một định dạng |
| Bất biến phải đủ mạnh để dùng làm bằng chứng khi tranh chấp | Sổ nằm trong BC đang sửa chính dữ liệu đó thì ai có quyền sửa dữ liệu cũng có thể sửa dấu vết |
| Không BC nào là chủ tự nhiên của dấu vết dùng chung | Gộp vào Người bán thì Xác thực phải ghi vào dữ liệu của Người bán, sai ranh giới |

## 3. Tác nhân và chức năng lõi

| Tác nhân | Việc |
|---|---|
| BC khác | Gửi bản ghi hành động (theo hợp đồng ở mục 8) |
| Quản trị | Tra cứu danh sách bản ghi có lọc; xem chi tiết một bản ghi |

Nhật ký không có tác nhân con người ghi vào: bản ghi chỉ đến từ BC khác, không ai nhập tay.

## 4. Ngôn ngữ chung

| Thuật ngữ | Định nghĩa | Nghĩa ở BC khác |
|---|---|---|
| Bản ghi kiểm toán | Một dòng nhật ký: một hành động đã thành công | — |
| Người làm | Tài khoản (kèm vai tại thời điểm làm) hoặc hệ thống thực hiện hành động | — |
| Hành động | Mã gọi tên điều đã làm, dạng `ĐỐI_TƯỢNG_HÀNH_ĐỘNG` (ví dụ `SELLER_PROFILE_APPROVED`) | BC phát đặt tên và khai báo ý nghĩa |
| Đối tượng | Thứ bị tác động: loại và mã (ví dụ hồ sơ người bán, mã hồ sơ). **Chỉ mã**, không dữ liệu của đối tượng | — |
| Dữ kiện bổ sung | Vài cặp khóa-giá trị ngắn làm rõ hành động (lý do, phiên bản đã xem). Không bao giờ là dữ liệu định danh | — |
| Mã truy vết | Mã nối bản ghi với log ứng dụng của yêu cầu gốc | — |
| Hệ thống | Người làm là job hoặc quy trình tự động, không phải tài khoản | — |

## 5. Mô hình domain

### 5.0 Suy ra thực thể

Quyết định duyệt hay từ chối một người bán, ngừng dùng một danh mục, khóa một tài khoản là những quyết định có hậu quả pháp lý, tiền bạc hoặc danh tiếng. Khi có tranh chấp, sàn phải trả lời được ai quyết định, lúc nào, với đối tượng nào, và câu trả lời đó không được thay đổi sau khi đã ghi. Chỉ cần **một khái niệm**: bản ghi kiểm toán, bất biến, tạo một lần.

Nó là thực thể đơn, **không có hành vi sửa**, nên không có khái niệm trạng thái hay phiên bản.

**Vì sao không tự suy bản ghi từ các sự kiện nghiệp vụ của BC khác** (ví dụ nghe "hồ sơ được duyệt"):
- sự kiện nghiệp vụ được thiết kế cho bên nhận nghiệp vụ: không mang người ra quyết định hay lý do, và đổi theo nhu cầu của họ;
- nhật ký cần một hợp đồng riêng, ổn định nhiều năm, dùng chung cho mọi BC;
- mỗi lần BC khác thêm hành động, Nhật ký sẽ phải biết thêm sự kiện của BC đó (phụ thuộc ngược).

Vì vậy Nhật ký sở hữu **một hợp đồng bản ghi**; BC nào có hành động cần kiểm toán thì gửi theo hợp đồng này, cùng lúc với hành động đó thành công (mục 8).

**Chỉ ghi hành động đã thành công.** Hành động bị từ chối quyền hoặc kiểm đầu vào là sự kiện bảo mật, đã có dòng log riêng; đưa vào nhật ký sẽ lẫn hai thứ có mục đích khác nhau. Hệ quả: tính "quyết định và dấu vết cùng thành công hoặc cùng không" chỉ cần BC phát bảo đảm, ở phía nó.

**Bản đồ aggregate**

| ID | Aggregate | Chứa bên trong | Vai trò | Phụ thuộc | Độ chi tiết |
|---|---|---|---|---|---|
| `AGG-AUD-01` | Bản ghi kiểm toán | Người làm, hành động, đối tượng, dữ kiện bổ sung, thời điểm | Bảo vệ tính bất biến và không lộ dữ liệu của một dòng nhật ký | — | DONE (phần C1a) |

### 5.1 Bản ghi kiểm toán — `AGG-AUD-01`

**Vai trò & ranh giới.** Một dòng nhật ký. Không trả lời "trạng thái hiện tại của đối tượng là gì" (việc của BC gốc); chỉ trả lời "điều gì đã được làm lên nó".

**Thành phần**

| Thành phần | Ý nghĩa | Ghi chú |
|---|---|---|
| Mã sự kiện nguồn | Định danh hành động ở BC phát | Một mã sự kiện nguồn chỉ cho một bản ghi |
| Thời điểm xảy ra | Lúc hành động xảy ra (giờ của BC phát) | Dùng để sắp xếp và lọc |
| Thời điểm nhận | Lúc Nhật ký lưu | Cho thấy độ trễ |
| Người làm | Mã tài khoản và vai **tại thời điểm làm**, hoặc "hệ thống" | Chụp lại: đổi vai hay khóa tài khoản sau đó không đổi lịch sử |
| Hành động | Mã gọi tên (mục 4) | Nhật ký không kiểm ý nghĩa |
| Đối tượng | Loại và mã | Chỉ mã |
| Dữ kiện bổ sung | Các cặp khóa-giá trị phẳng | Quy tắc ở đoạn "Dữ kiện bổ sung và dữ liệu định danh" ngay dưới |
| BC nguồn | BC đã gửi | Tra nguồn |
| Mã truy vết | Nối với log của yêu cầu gốc | Điều tra |

**Người làm là "hệ thống"** khi hành động do job hoặc quy trình tự động thực hiện và có hậu quả (ví dụ chi trả định kỳ, tự hủy đơn quá hạn). Chưa có hành động nào như vậy ở C1a; mô hình chừa chỗ.

**Dữ kiện bổ sung và dữ liệu định danh.** Nhật ký không biết nghiệp vụ của BC phát nên không tự phân biệt được cái gì là nhạy cảm. Cách bảo đảm không lộ chia hai lớp:
- **BC phát** khai báo hành động của mình và các khóa dữ kiện bổ sung được phép (trong `analysis.md` của chính BC đó), và chỉ gửi các khóa đó;
- **Nhật ký** chặn thêm theo hình dạng: dữ kiện phải phẳng, ngắn, số khóa giới hạn, và không được mang khóa mang tên thuộc loại dữ liệu định danh đã định nghĩa (số căn cước, mã số thuế, địa chỉ, số điện thoại, email, mật khẩu). Khóa vi phạm bị bỏ và có cảnh báo (nêu tên khóa, không nêu giá trị).

Phương án đã cân nhắc và loại: để Nhật ký giữ danh mục khóa được phép cho mọi hành động. Loại vì mỗi hành động mới ở BC khác phải sửa Nhật ký, trái với mục tiêu "thêm hành động chỉ là việc của BC phát".

**Lý do từ chối của hồ sơ người bán vào dữ kiện bổ sung:** đó là nội dung của quyết định và là thứ cần khi tranh chấp; người bán cũng được xem. Số căn cước, mã số thuế, địa chỉ, tên pháp lý, số điện thoại thì không.

#### Kết luận

**Quy tắc bất biến**

| ID | Quy tắc | Loại | Kiểm tra khi | Từ stage |
|---|---|---|---|---|
| `INV-AUD-01` | Bản ghi chỉ được thêm: không sửa, không xóa, kể cả bởi quản trị | Liên tục | mọi thao tác trên nhật ký | R13 |
| `INV-AUD-02` | Một mã sự kiện nguồn chỉ cho một bản ghi: nhận lặp không ghi đôi | Liên tục | `CMD-AUD-01` | R12 |
| `INV-AUD-03` | Mỗi bản ghi có đủ thời điểm xảy ra, người làm, hành động, đối tượng, BC nguồn; thiếu thì không lưu | Tiên quyết | `CMD-AUD-01` | mới |
| `INV-AUD-04` | Dữ kiện bổ sung phẳng, ngắn, và không mang khóa thuộc loại dữ liệu định danh | Tiên quyết | `CMD-AUD-01` | R11 |
| `INV-AUD-05` | Người làm là bản chụp tại thời điểm hành động | Liên tục | `CMD-AUD-01` | mới |
| `INV-AUD-06` | Chỉ Quản trị tra cứu | Tiên quyết | `RM-AUD-01`, `RM-AUD-02` | mới |
| `INV-AUD-07` | Hành động lạ vẫn được nhận, miễn đúng định dạng mã hành động | Tiên quyết | `CMD-AUD-01` | mới |
| `INV-AUD-08` | Bản ghi không bị dọn hay hết hạn (ở C1a) | Liên tục | — | mới |
| `INV-AUD-09` | Mọi hành động đã thành công ở BC phát đến được nhật ký; chậm được, mất không | Liên tục | giữa BC phát và nhận | R12 |

`INV-AUD-09` có độ cũ chấp nhận được vài giây đến vài phút: nhật ký có thể đến sau hành động một nhịp, nhưng không được thiếu.

**Vòng đời.** Không có trạng thái: bản ghi được tạo một lần và đứng yên. Không có bảng chuyển đổi hay ma trận hành động × trạng thái.

## 6. Năng lực

Mỗi năng lực trả lời sáu câu của P3: (1) ai khởi tạo, (2) ai xử lý và theo quy trình nào, (3) ai được báo, (4) đường không suôn sẻ, (5) ai xem lại được lịch sử, (6) dữ liệu chảy sang đâu.

### CAP-AUD-01 Ghi bản ghi kiểm toán (stage: H9)

1. BC phát, ngay khi hành động của nó thành công (Người bán khi duyệt, từ chối hồ sơ; Danh mục hàng khi quản danh mục, thuộc tính, thương hiệu).
2. Nhật ký tự xử lý, không có bước duyệt: kiểm đủ trường bắt buộc (`INV-AUD-03`), định dạng mã hành động (`INV-AUD-07`), dữ kiện bổ sung (`INV-AUD-04`), chưa có bản ghi cùng mã sự kiện nguồn (`INV-AUD-02`); rồi lưu.
3. Không ai được báo.
4. Đường không suôn sẻ:
   - Nhật ký tạm không nhận được: bản ghi nằm chờ ở phía gửi, nhận bù khi lên lại; chậm, không mất (`INV-AUD-09`).
   - Nhận cùng bản ghi hai lần: bỏ qua êm, không ghi đôi.
   - Bản ghi sai định dạng hoặc thiếu trường bắt buộc: không lưu, tách riêng để xử lý, cảnh báo; không chặn các bản ghi sau.
   - Dữ kiện bổ sung mang khóa bị cấm: bỏ khóa đó, vẫn lưu bản ghi, cảnh báo.
   - Đồng hồ hai BC lệch: thời điểm xảy ra theo giờ BC phát; thời điểm nhận để thấy độ lệch; không sửa.
5. Bản thân việc ghi là lịch sử; không có lịch sử của lịch sử.
6. Không có dữ liệu chảy ra. Chỉ Quản trị đọc (CAP-AUD-02).

### CAP-AUD-02 Tra cứu nhật ký (stage: H10)

1. Quản trị.
2. Nhật ký trả danh sách bản ghi, mới nhất trước, có lọc: khoảng thời gian, người làm, hành động, đối tượng. Chọn một bản ghi để xem đầy đủ trường, kể cả dữ kiện bổ sung và mã truy vết.
3. Không ai được báo.
4. Đường không suôn sẻ: khoảng thời gian quá rộng bị giới hạn bằng phân trang; không phải Quản trị bị từ chối (`INV-AUD-06`); không có kết quả thì trả danh sách rỗng.
5. Việc quản trị xem nhật ký không được ghi lại ở C1a (mục 12).
6. Mã truy vết cho phép Quản trị chuyển sang log ứng dụng để tìm yêu cầu gốc.

## 7. Quy tắc xuyên aggregate

N/A — chỉ có một aggregate. Quy tắc xuyên BC ở mục 8 và 11.

## 8. Sự kiện và hợp đồng bản ghi

### 8.1 Phát ra cho BC khác

Không — Nhật ký không phát sự kiện nào ở C1a.

### 8.2 Nhận từ BC khác (hợp đồng bản ghi)

Nhật ký chỉ nhận **một loại** sự kiện: *hành động đã thực hiện*. BC phát tên riêng cho hành động của mình nhưng dùng chung hình dạng này.

| Trường | Ý nghĩa |
|---|---|
| Mã sự kiện, thời điểm xảy ra, mã truy vết, BC nguồn | Phần chung của mọi sự kiện |
| Người làm (mã tài khoản, vai) | Hoặc "hệ thống" |
| Hành động | Mã định dạng `ĐỐI_TƯỢNG_HÀNH_ĐỘNG` |
| Đối tượng (loại, mã) | Chỉ mã |
| Dữ kiện bổ sung | Khóa-giá trị phẳng |

Hợp đồng do Nhật ký sở hữu; thêm hành động mới ở BC khác không làm đổi hợp đồng.

**Hành động nhận ở C1a** (BC phát khai báo ở `analysis.md` của mình):

| Hành động | BC phát | Đối tượng | Dữ kiện bổ sung | Nguồn |
|---|---|---|---|---|
| `SELLER_PROFILE_APPROVED` | Người bán | hồ sơ người bán, mã hồ sơ | phiên bản đã xem, mã tài khoản người bán | `EVT-SEL-04` |
| `SELLER_PROFILE_REJECTED` | Người bán | như trên | như trên, lý do từ chối | `EVT-SEL-04` |
| `CATEGORY_CREATED`, `CATEGORY_UPDATED`, `CATEGORY_DEACTIVATED`, `CATEGORY_DELETED` | Danh mục hàng | danh mục, mã danh mục (đổi tên, sắp xếp, gán, bỏ gán thuộc tính, bật lại đều ghi là `CATEGORY_UPDATED`) | phiên bản | `EVT-CAT-04` |
| `ATTRIBUTE_CREATED`, `ATTRIBUTE_UPDATED`, `ATTRIBUTE_DEACTIVATED` | Danh mục hàng | mẫu thuộc tính, mã mẫu | phiên bản | `EVT-CAT-04` |
| `BRAND_CREATED`, `BRAND_UPDATED`, `BRAND_DEACTIVATED` | Danh mục hàng | thương hiệu, mã thương hiệu | phiên bản | `EVT-CAT-04` |

## 9. Policy

N/A — chưa có luật "khi X thì ra lệnh Y". Chính sách lưu trữ và dọn là việc chưa làm (mục 12).

## 10. Hiển thị & tra cứu

| ID | Ai | Xem gì | Điều kiện được xem | Dữ liệu nguồn | Độ tươi |
|---|---|---|---|---|---|
| `RM-AUD-01` | Quản trị | Danh sách bản ghi: lọc theo khoảng thời gian, người làm, hành động, đối tượng; mới nhất trước; phân trang | Quản trị | `AGG-AUD-01` | Vài giây sau hành động |
| `RM-AUD-02` | Quản trị | Chi tiết một bản ghi: đủ trường, kể cả dữ kiện bổ sung và mã truy vết | Quản trị | `AGG-AUD-01` | Như trên |

Thứ tự phụ ổn định khi hai bản ghi cùng thời điểm xảy ra, để phân trang không lặp hay sót.

## 11. Quan hệ với BC khác

| BC | Chiều | Kiểu quan hệ | BC này cung cấp / nhận gì |
|---|---|---|---|
| Người bán | Upstream | OHS-PL (Nhật ký sở hữu hợp đồng) | Nhận `SELLER_PROFILE_APPROVED`, `SELLER_PROFILE_REJECTED` |
| Danh mục hàng | Upstream | OHS-PL | Nhận hành động quản danh mục, thuộc tính, thương hiệu |
| Xác thực | Upstream (C1b) | OHS-PL | Nhận khóa, mở tài khoản |
| Sổ cái, Giao nhận, Dữ liệu tham chiếu và cấu hình, Lập lịch | Upstream (sau này) | OHS-PL | Nhận hành động can thiệp bằng tay, hành động của hệ thống |

Nhật ký không phụ thuộc BC nào: nó không gọi ra ngoài và không biết nghiệp vụ của bên phát.

## 12. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Hành động khóa, mở tài khoản; chặn, mở sản phẩm | Trong scope (B25) | C1b | Khép kín quản trị ở C1b | BC phát chưa có hành động | Thêm mã hành động ở BC phát |
| Bằng chứng toàn vẹn (chuỗi băm) | Cắt tạm | Khi có yêu cầu pháp lý hoặc phát hiện giả mạo | Chứng minh bản ghi không bị sửa ngoài ứng dụng | `INV-AUD-01` chặn ở lớp ứng dụng và cơ sở dữ liệu là đủ cho C1a | Thành phần của bản ghi |
| Ghi lại việc quản trị xem nhật ký | Cắt tạm | Khi cần truy người xem | Truy trách nhiệm xem dữ liệu nhạy cảm | Chưa có nhu cầu | Hành động mới của chính Nhật ký |
| Chính sách lưu trữ, dọn, lưu lạnh | Hoãn | Khi bảng đủ lớn hoặc có quy định | Dung lượng, tuân thủ | Không hết hạn ở C1a | `INV-AUD-08` |
| Xuất báo cáo, cảnh báo theo mẫu hành động | Cắt tạm | Khi có nhu cầu | Giám sát bất thường | Chưa có người dùng | BC khác đọc nhật ký |
| Tìm văn bản trong dữ kiện bổ sung | Cắt tạm | Khi cần | Điều tra | Lọc có cấu trúc đủ cho C1a | Tra cứu |
| Hành động của hệ thống (job) | Chừa chỗ | C2b trở đi | Truy hành động tự động có hậu quả | Chưa có job như vậy | Người làm = hệ thống |
| Dấu vết tiền, điểm thưởng | Không thuộc Nhật ký | Sổ cái | — | Là nguồn sự thật của tiền | Xem mục 1 |
| Hành động nhạy cảm của người dùng thường | Không thuộc Nhật ký | C3c (Xác thực) | — | Dữ liệu của chính chủ tài khoản | Xem mục 1 |

## 13. Câu hỏi cần trả lời

| # | Câu hỏi | Vì sao đáng hỏi | Đề xuất |
|---|---|---|---|
| Q-AUD-01 | Quy định pháp luật có đòi giữ nhật ký thao tác quản trị của sàn trong một thời hạn tối thiểu không | Ảnh hưởng `INV-AUD-08` và chính sách lưu trữ | **Đã chốt `2026-10-24`:** không hết hạn ở C1a (`INV-AUD-08`); kiểm chứng quy định khi làm chính sách lưu trữ (mục 12) |

## 14. Kỳ vọng phi chức năng từ nghiệp vụ

**Dữ kiện.** Yêu cầu gốc không có con số riêng cho Nhật ký. Hành động quản trị do người thực hiện nên khối lượng thấp (**giả định**: tối đa vài trăm bản ghi mỗi ngày ở quy mô mô phỏng, chưa có số trong `6-non-function-requirement.md`).

**Phân tích.** Không có áp lực tốc độ. Điều cần giữ: (1) bản ghi không mất và không ghi đôi (`INV-AUD-02`, `09`); (2) bản ghi không bị sửa (`INV-AUD-01`); (3) không lộ dữ liệu định danh (`INV-AUD-04`). Tra cứu theo khoảng thời gian trên dữ liệu tăng dần cần giữ nhanh khi bảng lớn, nhưng chưa phải vấn đề ở quy mô này.

| Chức năng | Kỳ vọng | Lý do nghiệp vụ | Nguồn |
|---|---|---|---|
| Ghi bản ghi | Đến nhật ký trong vài giây, không mất | Dấu vết dùng khi tranh chấp | Phân tích này (giả định) |
| Tra cứu | Trả trang đầu nhanh với khoảng thời gian thường dùng | Quản trị tra cứu để xử lý việc đang chờ | Giả định |
| Bất biến | Không có đường nào sửa hay xóa | Giá trị của nhật ký | `INV-AUD-01` |

## 15. Tài liệu liên quan

- [`stage.md` của C1a](../../stage/c1a-listing/stage.md) — yêu cầu và kịch bản của chặng (S8, S9; R11 đến R13)
- [`4-context-map.md`](../../global/1.requirement/4-context-map.md) — B25
- [`seller-service/analysis.md`](../seller-service/analysis.md) — `EVT-SEL-04`, hành động của hồ sơ người bán
- [`logging.md`](../../global/4.convention/logging.md) — ranh giới với log ứng dụng

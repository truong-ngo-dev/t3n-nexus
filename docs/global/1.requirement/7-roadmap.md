# Roadmap

## 1. Nguyên tắc xếp chặng

Suy ra từ overview, không tự đặt:

| # | Nguyên tắc | Gốc |
|---|---|---|
| 1 | **Chặng nhỏ, mỗi chặng demo được và trọn vẹn.** Dừng ở chặng nào cũng có một sản phẩm chạy được | Không hạn chót; Dấu hiệu đạt |
| 2 | **Mỗi chặng đi đường mỏng nhất; phần hoàn thiện thành chặng riêng.** Chặng nào có phần dời đi thì ghi rõ "hoàn thiện ở chặng nào" để phần đó không bị hiểu thành bị cắt | Ý 6; nguyên tắc 1 |
| 3 | **Chạy từ đầu đến cuối sớm (C3a), rồi mới đào sâu.** Sàn "nghiệp vụ đúng, chạy được" phải đạt trước | Ý 6 (hai ngưỡng sàn) |
| 4 | **Mỗi chặng đi kèm điểm kiểm tra với dấu hiệu đạt**, để biết khi nào "đủ để dừng" | Dấu hiệu đạt |
| 5 | **Chỉ bật hạ tầng chặng đó cần.** Một máy 32GB không chạy toàn bộ cùng lúc | Nguồn lực |
| 6 | **Mỗi task có mục tiêu hiểu, ai viết, và xác nhận hiểu sau khi xong.** Task không ghi được mục tiêu hiểu thì làm tối giản | Ý 1, 5 |
| 7 | **Phân tích mịn ngay đầu chặng, không làm trước.** Mức chiến lược đã có (bản đồ BC), mức mịn làm khi tới BC đó | Analysis-principles (hai giai đoạn) |
| 8 | **BC đã có mã: phân tích trước, đối chiếu mã sau.** Mã cũ không làm đầu vào của phân tích | Analysis-principles (khi BC đã có code) |
| 9 | **Điểm sâu xếp sau khi có chỗ để đo.** Đo trên hệ thống chạy được, không đo trong chân không | Ý 1 (làm thật, đo thật) |

## 2. Tổng quan chặng

Cỡ (giả định ~20 giờ mỗi tuần): **S** ≈ 1–2 tuần · **M** ≈ 2–4 · **L** ≈ 4–6 · **XL** ≈ 6+. Ước lượng thô; frontend tự viết
nên chậm hơn backend. Cỡ chỉ có nghĩa tương đối giữa các chặng; hiệu chỉnh sau 2–3 chặng đầu bằng tốc độ thật.

| Chặng | Tên | Demo được gì | BC chính | Cỡ | Điểm kiểm tra |
|---|---|---|---|---|---|
| **C0** | Nền | Đăng ký, đăng nhập qua giao diện, khung bố cục | Xác thực | S–M | |
| **C1a** | Niêm yết | Người bán đăng ký, được duyệt, đăng sản phẩm có ảnh | Danh mục hàng, Người bán, Quản lý tệp, Dữ liệu tham chiếu, Nhật ký kiểm toán | M | |
| **C1b** | Khám phá | Khách tìm, lọc, xem sản phẩm; quản trị khóa tài khoản, chặn sản phẩm | Tìm kiếm, Xác thực (phân quyền, khóa), Định danh, Danh mục hàng | M | |
| **C2a** | Đặt hàng | Khách đặt COD, giữ chỗ, người bán xác nhận hoặc từ chối, người mua tự hủy | Giỏ hàng, Khách hàng, Định phí, Tồn kho, Đơn hàng, Thông báo | M | |
| **C2b** | Hạn và hủy tự động | Quá hạn xác nhận tự hủy; giỏ vãng lai; bù trừ khi lỗi giữa chừng | Lập lịch, Đơn hàng, Giỏ hàng, Định phí, Người bán | M | |
| **C3a** | Giao nhận (đường chính) | Hàng đi từ người bán, qua kho, tới tay khách; shipper xác nhận giao thì đơn "đã thanh toán" | Kho vật lý, Giao nhận, Shipper, Giả lập | M | **K1: dấu hiệu 1 (chạy hết kịch bản chính)** |
| **C3b** | Giao nhận (nhánh lỗi) | Giao thất bại, hoàn về người bán, hạn bàn giao, phân công theo quy tắc | Giao nhận, Shipper, Kho vật lý | M | |
| **C4** | Tiền | Trả trước, hoàn tiền (Sổ cái làm sau lõi) | Thanh toán | M | |
| **C5** | Đồng thời cao | Chạy tải lên điểm nóng, không bán lố, có số đo | Tồn kho, Khuyến mãi, Giỏ hàng, Xác thực (đăng nhập dưới tải), Giả lập | L | **K2: dấu hiệu 2** |
| **C6a** | Dữ liệu lớn: đo tìm kiếm | Tìm kiếm ở vài triệu SKU, có số đo | Tìm kiếm, Giả lập | M | **K3: dấu hiệu 3** |
| **C6b** | Dữ liệu lớn: báo cáo | Báo cáo tổng hợp, xuất file lớn, đánh giá sản phẩm | Báo cáo, Đánh giá | M | |
| **C7** | Thời gian thực | Thông báo trong ứng dụng, vị trí shipper, chat | Thông báo, Shipper, Giao nhận, Chat, Giả lập | M–L | |
| **C8** | Đưa lên môi trường thật | Cụm thiết yếu chạy trên cloud, demo, tắt | Hạ tầng | S–M | |

**Điểm dừng hợp lệ ("mức đủ để dừng"):** sau **C6a**, khi cả ba dấu hiệu đầu đã có (K1 ở C3a, K2 ở C5, K3 ở C6a); dấu hiệu 4
(giải thích quyết định) chạy xuyên suốt qua xác nhận hiểu của từng task. C6b, C7 và C8 thuộc scope nhưng nằm sau điểm dừng.

## 3. Chi tiết từng chặng (mức tên)

Mỗi chặng ghi: việc chính · điểm sâu và mục tiêu hiểu gợi ý · hạ tầng cần bật (loại, chưa chọn công nghệ) · điều kiện xong ·
phần được hoàn thiện ở chặng nào (nếu có).

### C0. Nền (S–M)

- **Việc chính:** quy ước phát triển (sàn chung: bắt lỗi, log); cách chạy cục bộ theo lát cắt; Xác thực tối giản (đăng ký,
  đăng nhập, đăng xuất; mỗi tài khoản có một vai: Người dùng tự đăng ký, Quản trị có sẵn) và cổng truy cập; khung frontend
  (cấu trúc, bố cục, đăng nhập, bảo vệ truy cập; bộ thành phần dùng chung cho các portal web); bộ đo nền (đo trần của
  máy: điểm cuối rỗng, ghi một dòng vào cơ sở dữ liệu, băm một mật khẩu) để biết giới hạn máy trước khi đo ứng dụng
  (`6-non-function-requirement.md` §4.1). Phần đo bộ đệm và tìm kiếm của bộ đo nền làm khi hạ tầng đó được bật (tìm kiếm
  ở C1b, bộ đệm ở C5).
- **Mục tiêu hiểu gợi ý:** cơ chế bên dưới của frontend (phát hiện thay đổi, vòng đời, quản lý đăng ký sự kiện); chuẩn xử
  lý lỗi và log để lần ra lỗi xuyên nhiều dịch vụ.
- **Hạ tầng:** cơ sở dữ liệu quan hệ, cổng truy cập.
- **Xong khi:** đăng ký, đăng nhập, đăng xuất qua giao diện; một yêu cầu lỗi lần ra được nguyên nhân từ log; có số trần
  máy cho điểm cuối rỗng, ghi cơ sở dữ liệu và băm một mật khẩu, ghi cùng điều kiện đo.
- **Hoàn thiện ở:** xác thực nhiều lớp, phân quyền theo vai, khóa và mở tài khoản: C1b.

### C1a. Niêm yết (M)

- **Việc chính:** đăng ký và duyệt người bán (Người bán, tối giản); tạo sản phẩm, biến thể, giá, danh mục, thuộc tính;
  ảnh sản phẩm; dữ liệu tham chiếu tối giản; Nhật ký kiểm toán tối giản (ghi hành động quản trị: duyệt người bán); giao
  diện người bán và nội bộ ở mức tối giản (đăng sản phẩm, duyệt người bán, danh mục). Chưa có tìm kiếm.
- **Mục tiêu hiểu gợi ý:** cách lưu và phục vụ ảnh tách khỏi dữ liệu sản phẩm; vì sao hành động quản trị cần nhật ký ghi
  thêm, không sửa.
- **Hạ tầng:** cơ sở dữ liệu, bus sự kiện, lưu tệp. Bus sự kiện kèm hộp thư event: ghi event cùng
  giao dịch với thay đổi dữ liệu, đẩy event từ hộp thư ra bus, chính sách giữ và dọn hộp thư, xử lý nhận trùng ở bên nhận,
  thứ tự event theo từng thực thể (C0 chưa ghi hay gửi event nào ra ngoài).
- **BC đã có mã (kiểm sơ ở mức tên thư mục):** Danh mục hàng. Phân tích trước, đối chiếu sau.
- **Xong khi:** người bán đăng sản phẩm có ảnh qua giao diện; quản trị duyệt người bán và hành động đó tra cứu được trong
  nhật ký.
- **Hoàn thiện ở:** tìm kiếm, lọc, sắp xếp, xem chi tiết cho khách; khóa và mở tài khoản, chặn và mở sản phẩm: C1b. Quy
  trình duyệt người bán chạy dài: C2b.

### C1b. Khám phá (M)

- **Việc chính:** tìm kiếm văn bản, lọc, sắp xếp, xem chi tiết (portal Cửa hàng); xác thực nhiều lớp, phân quyền theo vai,
  khóa và mở tài khoản; Định danh (hồ sơ, thiết bị, lịch sử đăng nhập) và thu hồi phiên từ xa; chặn và mở sản phẩm (ghi
  vào nhật ký).
- **Điểm sâu:** đọc đồng thời cao ở Danh mục (đòi hỏi); Tìm kiếm là read model (chưa đo lớn, đo ở C6a); phân quyền (chủ
  động chọn).
- **Mục tiêu hiểu gợi ý:** vì sao tìm kiếm tách khỏi nguồn dữ liệu và chấp nhận nhất quán cuối cùng; cách giữ cập nhật không
  lùi phiên bản.
- **Hạ tầng:** thêm tìm kiếm toàn văn.
- **Xong khi:** khách tìm thấy và lọc được sản phẩm qua giao diện; quản trị khóa một tài khoản và chặn một sản phẩm, cả hai
  tra cứu được trong nhật ký; vai không đủ quyền bị từ chối; người dùng xem được lịch sử đăng nhập và thu hồi được một
  phiên từ xa.

### C2a. Đặt hàng (M)

- **Việc chính:** giỏ của người đã đăng nhập; địa chỉ; phí tính đơn giản; giữ chỗ tồn kho, chốt, trả; đặt hàng COD; người
  bán xác nhận hoặc từ chối; người mua tự hủy; thông báo đơn mới. Điều phối bản đơn giản: một bước lỗi giữa chừng thì trả
  chỗ đã giữ.
- **Điểm sâu:** giữ chỗ xử lý lặp an toàn (đòi hỏi). Gửi lặp ở luồng tạo đơn: phân biệt khóa nghiệp vụ của đơn (bắt buộc,
  CSDL chốt duy nhất) với mã yêu cầu của client (chỉ giúp gửi lại an toàn, không chống lạm dụng); quyết định có dùng mã yêu cầu
  và lưu ở đâu khi tới chặng này (Đăng ký ở C0 đã quyết định không dùng, xem `tech.md` T4 của feature đăng ký).
- **Mục tiêu hiểu gợi ý:** vì sao giữ chỗ phải toàn bộ hoặc không và an toàn khi lặp; vì sao có thêm bước người bán xác
  nhận khi tồn kho chỉ là lời khai.
- **Hạ tầng:** thêm bộ nhớ đệm hoặc kho giỏ. Gom log tập trung (ELK): từ chặng này có luồng xuyên nhiều BC nên cần tìm
  toàn bộ dấu vết bằng một truy vấn (NFR mục 7). C0 chỉ cần log có cấu trúc kèm mã tương quan (feature 07), chưa dựng ELK.
- **BC đã có mã (kiểm sơ):** Tồn kho, Đơn hàng, Khách hàng, Thông báo. Phân tích trước, đối chiếu sau.
- **Xong khi:** đặt COD, người bán xác nhận hoặc từ chối, người mua tự hủy; các nhánh này trả đúng giữ chỗ; gửi lặp một
  yêu cầu giữ chỗ không giữ hai lần.
- **Hoàn thiện ở:** hạn xác nhận và hủy tự động, giỏ vãng lai, quy tắc tính phí, điều phối kèm bù trừ đầy đủ: C2b.

### C2b. Hạn và hủy tự động (M)

- **Việc chính:** Lập lịch; hạn xác nhận và hủy tự động khi quá hạn; giỏ vãng lai và gộp khi đăng nhập; quy tắc tính phí;
  quy trình duyệt người bán chạy dài (nâng cấp từ bản tối giản ở C1a).
- **Điểm sâu:** điều phối nhiều bước kèm bù trừ khi lỗi (đòi hỏi); quy tắc tính phí (chủ động chọn); quy trình duyệt người
  bán chạy dài, tạm dừng và tiếp tục (chủ động chọn).
- **Mục tiêu hiểu gợi ý:** cách bù trừ khi một bước thất bại giữa chừng; cách một quy trình chờ lâu tạm dừng rồi tiếp tục
  mà không mất trạng thái.
- **Hạ tầng:** thêm lập lịch.
- **BC đã có mã (kiểm sơ):** Lập lịch. Phân tích trước, đối chiếu sau.
- **Xong khi:** đơn quá hạn xác nhận tự hủy và trả đúng giữ chỗ; mọi nhánh lỗi giữa chừng trả đúng giữ chỗ; giỏ vãng lai
  gộp đúng khi đăng nhập.

### C3a. Giao nhận, đường chính (M) — **K1**

- **Việc chính:** người bán mang hàng tới kho; kho nhận và đối chiếu; nhật ký di chuyển; Shipper (hồ sơ, khu vực, sẵn
  sàng; đội giả lập); phân công đơn giản (chọn shipper sẵn sàng cùng khu vực); shipper xác nhận giao thì đơn chuyển sang
  "đã thanh toán" (COD coi như đã thu đúng; event "đã giao" vẫn mang số tiền đã thu và mã shipper); giao diện: portal Nội
  bộ (nhân viên kho), portal Người bán (xử lý đơn, bàn giao), portal Shipper (di động, tối giản); xem `5-ui-analysis.md`.
- **Mục tiêu hiểu gợi ý:** vì sao event "đã giao" nên mang đủ số tiền và mã shipper dù chưa ai dùng.
- **Hạ tầng:** không thêm.
- **Xong khi:** chạy hết một kịch bản COD qua giao diện, từ đặt hàng đến shipper xác nhận giao. **Đạt K1.**
- **Hoàn thiện ở:** giao thất bại, hoàn về người bán, hạn bàn giao, phân công theo quy tắc, nhật ký bất biến: C3b. Công nợ
  tiền mặt, nộp tiền, đối soát COD: phần cắt tạm, kéo vào cùng Sổ cái (mục 4).

### C3b. Giao nhận, nhánh lỗi (M)

- **Việc chính:** giao thất bại, hoàn hàng về người bán; hạn bàn giao và hủy tự động; phân công theo quy tắc (tải tối đa,
  khu vực, sẵn sàng); nhật ký di chuyển bất biến; thống kê việc của shipper và hiệu suất cho quản trị; thao tác bền khi
  mạng yếu cho shipper.
- **Điểm sâu:** nhật ký bất biến (chủ động chọn); phân công theo quy tắc (chủ động chọn); thao tác bền khi mạng yếu
  (điểm sâu frontend F4).
- **Mục tiêu hiểu gợi ý:** vì sao lịch sử di chuyển hàng nên là chuỗi sự kiện chỉ ghi thêm; khi nào quy tắc nên tách khỏi
  mã; cách thử lại an toàn khi mạng yếu.
- **Hạ tầng:** thêm cơ chế lưu sự kiện bất biến nếu chọn.
- **Xong khi:** có một kịch bản giao thất bại chạy đúng (hàng về người bán, tồn kho nhập lại theo quyết định đã chọn);
  quá hạn bàn giao tự hủy; ngắt mạng giữa lúc shipper xác nhận giao, nối lại thì đơn chuyển trạng thái đúng một lần.

### C4. Tiền (M)

- **Việc chính:** thanh toán trả trước qua môi trường thử nghiệm của bên cung cấp; xử lý kết quả gọi ngược trùng hoặc sai
  thứ tự; hoàn tiền khi hủy. Sổ cái đã tách thành BC riêng trong phân tích nhưng **làm sau lõi** (mục 4); chặng này chưa
  có ký quỹ, số dư người bán, chi trả, đối soát COD.
- **Điểm sâu:** đúng khi lặp và sai thứ tự (đòi hỏi).
- **Mục tiêu hiểu gợi ý:** cách xử lý kết quả gọi ngược đến hai lần.
- **Hạ tầng:** đường vào từ bên ngoài cho kết quả gọi ngược (khi chạy cục bộ).
- **Xong khi:** trả trước, hủy, hoàn đúng.

### C5. Đồng thời cao (L) — **K2**

- **Việc chính:** voucher (Khuyến mãi); đo và đào sâu giữ chỗ tồn kho, voucher, ghi giỏ, và đăng nhập dưới tải; công cụ tạo
  tải và tiêm lỗi (Giả lập); báo cáo số đo kèm điều kiện đo. **Nhánh C5b (tùy chọn):** kéo phần đối tác tối thiểu (kho giữ tồn theo
  SKU) để làm flash sale.
- **Điểm sâu:** tranh chấp trên cùng một mặt hàng và một voucher (đòi hỏi; đồng thời cao); làn sóng đăng nhập trước flash
  sale (Xác thực; đòi hỏi; đồng thời cao).
- **Mục tiêu hiểu gợi ý:** vì sao đọc rồi trừ sụp khi đồng thời; đánh đổi giữa tính đúng và thông lượng; cách tính khoảng
  thời gian tối đa được chiếm một mặt hàng nóng; vì sao băm mật khẩu làm đăng nhập thành điểm nghẽn CPU và cách giữ đăng
  nhập đúng mà vẫn chịu được làn sóng.
- **Hạ tầng:** thêm bộ nhớ đệm thao tác nguyên tử, công cụ tạo tải.
- **Xong khi:** chạy tải, bất biến "khả dụng không âm" và "suất không vượt giới hạn" không bao giờ vỡ; có số đo lặp lại được
  (kể cả số đăng nhập/giây tối đa kèm điều kiện đo);
  trên giao diện, gửi lặp một đơn chỉ ra một kết quả và đếm ngược flash sale đúng dù máy khách lệch giờ (điểm sâu frontend
  F1). **Đạt K2.**

### C6a. Dữ liệu lớn: đo tìm kiếm (M) — **K3**

- **Việc chính:** sinh dữ liệu lớn; đo Tìm kiếm ở vài triệu SKU (độ trễ, dựng lại chỉ mục).
- **Điểm sâu:** dữ liệu lớn (đòi hỏi).
- **Mục tiêu hiểu gợi ý:** vì sao truy vấn tìm kiếm ở vài triệu SKU khác ở vài nghìn; cách dựng lại chỉ mục mà không dừng
  phục vụ.
- **Hạ tầng:** công cụ sinh dữ liệu; tăng cấu hình tìm kiếm cho quy mô đã chọn.
- **Xong khi:** có số đo tìm kiếm ở quy mô đã chọn (ghi rõ quy mô, điều kiện). **Đạt K3.**
- **Hoàn thiện ở:** báo cáo, xuất file, đánh giá: C6b.

### C6b. Dữ liệu lớn: báo cáo (M)

- **Việc chính:** Báo cáo bằng cơ chế tổng hợp riêng (không nối bảng gốc); xuất file lớn tiết kiệm bộ nhớ; Đánh giá tối
  giản (cấp điểm cho xếp hạng); bảng báo cáo lớn trên giao diện.
- **Điểm sâu:** dữ liệu lớn (đòi hỏi); tài nguyên khi xuất file (đòi hỏi); bảng lớn trên giao diện (điểm sâu frontend F2).
- **Mục tiêu hiểu gợi ý:** vì sao nối bảng gốc sụp ở quy mô; cấu trúc tổng hợp phục vụ nhiều báo cáo; xử lý theo luồng để
  không nạp cả tập dữ liệu vào bộ nhớ.
- **Hạ tầng:** thêm công cụ tổng hợp dữ liệu và kho phân tích.
- **Xong khi:** báo cáo đúng nguồn trong dung sai; bảng báo cáo chục nghìn dòng cuộn mượt và xuất file lớn không nạp cả
  tập vào bộ nhớ.

### C7. Thời gian thực (M–L)

- **Việc chính:** thông báo trong ứng dụng thời gian thực; nhập vị trí GPS của shipper, lưu mới nhất và lịch sử, theo dõi
  giao hàng thời gian thực (mô phỏng qua đúng giao diện nhập); chat người mua – người bán.
- **Điểm sâu:** nhiều kết nối đồng thời, giao đúng thứ tự (chủ động chọn; đồng thời cao); nhập vị trí tần suất cao và lịch
  sử vị trí (chủ động chọn; đồng thời cao, dữ liệu lớn).
- **Mục tiêu hiểu gợi ý:** khác biệt giữa đẩy từ máy chủ và nhắn tin hai chiều; cách giữ thứ tự và không mất tin khi mất kết nối.
- **Hạ tầng:** thêm kênh thời gian thực.
- **Xong khi:** người mua thấy vị trí shipper cập nhật; hai người chat với nhau; ngắt mạng rồi nối lại không mất và không
  trùng tin; luồng điểm GPS tần suất cao không làm giao diện giật; shipper mất sóng rồi nối lại không mất điểm vị trí
  (điểm sâu frontend F3, F4).

### C8. Đưa lên môi trường thật (S–M)

- **Việc chính:** chọn cụm thiết yếu; đóng gói và triển khai lên cloud; chạy demo; tắt.
- **Mục tiêu hiểu gợi ý:** khác biệt giữa chạy cục bộ và môi trường thật (mạng, bí mật, dung lượng, chi phí).
- **Xong khi:** kịch bản chính chạy trên cloud và hạ được sau demo.

## 4. Phần chưa xếp (cắt tạm) và điều kiện kéo vào

| Mảng | Điều kiện kéo vào |
|---|---|
| Duyệt sản phẩm trước khi hiển thị | Muốn học quy trình duyệt; sau C1b |
| Hoàn trả và khiếu nại, kèm lưu bằng chứng giao hàng và hoàn trả (Quản lý tệp) | Muốn thêm nghiệp vụ sau giao dịch; sau C4 (cần hoàn tiền) |
| Chiến dịch khuyến mãi theo thời gian, gói ưu đãi (bundle) | Muốn thêm nghiệp vụ khuyến mãi ngoài voucher; sau C5 (đã có voucher) |
| Người bán đối tác, kho giữ tồn theo SKU, flash sale | Cùng chặng C5b |
| Theo dõi hành vi, gợi ý cá nhân hóa, báo cáo lượt xem | Muốn đổi tìm kiếm mặc định; sau C6a |
| Hạng người bán, chỉ số hiệu suất | Sau khi có đủ đơn và đánh giá |
| Kiểm duyệt và an toàn | Sau C7 (liên quan chat) |
| Rút tiền của người bán | Sau khi có Sổ cái |
| Công nợ tiền mặt của shipper, nộp tiền tại kho, nhân viên kho xác nhận, đối soát COD | **Đáng làm** vì có giá trị kỹ thuật (ràng buộc nhất quán mạnh: công nợ không âm, không ghi thu hoặc nộp hai lần; thao tác bền khi mạng yếu); hoãn vì bản tối giản ở C3a đã chạy hết kịch bản chính và công nợ chỉ có nghĩa khi có Sổ cái đi cùng, không phải vì không đáng. Kéo vào cùng Sổ cái. Điểm nối đã giữ: event "đã giao" mang số tiền đã thu và mã shipper |
| Sổ cái (đã tách BC): ký quỹ, số dư người bán, chi trả theo kỳ, đối soát COD, công nợ shipper nối vào sổ | **Đáng làm** vì có giá trị kỹ thuật (đúng khi lặp và đồng thời, chi trả theo lô); hoãn vì thứ tự hợp lý của roadmap, không phải vì không đáng. Sau khi xong lõi; mốc cụ thể (K1 hay K3) chọn khi tới. Trong lúc chờ, tiền trả trước chỉ có trạng thái ở Thanh toán, đơn COD chỉ có trạng thái "đã thanh toán" khi giao |

## 5. Việc làm ở đầu mỗi chặng

1. **Phân tích mịn cho BC của chặng** (mức mịn của P1, P3 với sáu câu khép kín, P4 đầy đủ). BC có mã: phân tích trước, đối chiếu mã sau.
2. **NFR và điểm sâu cho điểm nóng của chặng**; chọn công nghệ, gắn nhãn "đòi hỏi" hoặc "chủ động chọn" kèm lý do.
3. **Tách task**, mỗi task ghi: mục tiêu hiểu · ai viết (chọn khi bắt đầu) · điều kiện xong · xác nhận hiểu (làm sau).
4. **Làm**, rồi xác nhận hiểu, rồi cập nhật bản đồ BC và roadmap.

## 6. Rủi ro và giả định

| Rủi ro | Cách giảm |
|---|---|
| Cỡ thô, frontend chậm hơn dự kiến | Hiệu chỉnh sau C1a, C1b và C2a bằng tốc độ thật; giữ giao diện ở mức ổn |
| Chia nhỏ thành nhiều chặng thì chi phí khởi động (phân tích mịn, chọn công nghệ) lặp lại nhiều lần | Phân tích mịn của hai nửa cùng một BC làm một lần ở nửa đầu, nửa sau chỉ bổ sung; chặng nào thấy khởi động quá nặng thì gộp lại |
| Đối chiếu mã cũ tốn hơn dự kiến | Cỡ chặng đã tính phân tích và đối chiếu; ghi nợ lệch thay vì sửa hết |
| Hạ tầng không chạy cùng lúc | Chỉ bật loại cần của chặng; ghi hạ tầng ở mỗi chặng |
| Điểm kiểm tra K2, K3 phụ thuộc đo trên một máy | Ghi rõ điều kiện đo; ngoại suy phải ghi là ngoại suy |
| Ranh giới BC sai lộ ra muộn | Cho phép sửa ranh giới có ghi nhận lý do; rà ở đầu mỗi chặng |
| Chỉ triển khai thật ở C8 nên vấn đề cấu hình, mạng, bí mật, dung lượng lộ muộn; kiến thức nền triển khai còn yếu nên cỡ S–M có thể thấp | Giữ cấu hình và bí mật ngoài mã ngay từ C0, không phụ thuộc đường dẫn hay địa chỉ cục bộ; hiệu chỉnh cỡ C8 sau khi có tốc độ thật |

## 7. Đã chốt

| Câu hỏi | Quyết định | Lý do |
|---|---|---|
| C6 (dữ liệu lớn) trước C7 (thời gian thực), hay đảo lại? | C6a trước, rồi C6b, rồi C7; C6b và C7 sau điểm dừng | K3 là dấu hiệu đạt; C6 chủ yếu backend; dữ liệu lớn ở quy mô thiết kế vừa máy |
| Có diễn tập triển khai lên cloud sớm trước C8 không? | **Không; chỉ triển khai ở cuối dự án (C8)** | Dự án một người, kiến thức triển khai còn yếu: gom vào cuối để không làm chậm lõi |
| C1 (Tìm kiếm) trước C2 (Giỏ, Đơn), hay làm đường mua hàng trước? | C1 trước | Có sản phẩm mới đặt được; Tìm kiếm cần thời gian cho C6 |
| Chặng C0 có cần riêng, hay gộp vào C1? | Giữ riêng | Frontend là chỗ yếu nhất; gộp thì C1 thành XL |
| Giao diện chia thế nào | Bốn portal: Cửa hàng, Người bán, Vận hành (nhân viên kho và quản trị), Shipper di động | `5-ui-analysis.md` |
| Có tách các chặng lớn thành đường mỏng và hoàn thiện không? | Có: C1, C2, C3, C6 mỗi chặng tách thành nửa a (đường mỏng nhất) và nửa b (hoàn thiện) | Mỗi chặng chạy được ở mức tối giản rồi hoàn thiện dần; chặng nhỏ hơn thì dễ hiệu chỉnh bằng tốc độ thật |
| COD ở bản tối giản xử lý thế nào? | Shipper xác nhận giao thì đơn "đã thanh toán"; chưa có công nợ, nộp tiền, đối soát (xếp vào cắt tạm, kéo vào cùng Sổ cái) | Bản tối giản đã chạy hết kịch bản chính; công nợ chỉ có nghĩa khi có Sổ cái. Event "đã giao" vẫn mang số tiền và mã shipper để dựng lại sau |
| Điều phối kèm bù trừ khi đặt hàng | C2a chỉ bản đơn giản (lỗi giữa chừng thì trả chỗ); bản đầy đủ ở C2b cùng Lập lịch | Bản đầy đủ cần Lập lịch (C2b); bản đơn giản đủ cho đường đặt hàng chính |
| Mốc "chạy hết kịch bản chính" (K1) đặt ở đâu | Ngay sau C3a, trước khi có kịch bản giao thất bại | K1 chỉ cần đường mua hàng chính chạy hết; giao thất bại thuộc phần hoàn thiện |

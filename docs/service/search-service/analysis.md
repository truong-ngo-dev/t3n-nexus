# Phân tích nghiệp vụ — search-service

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-09-30` · **Mã BC:** `SRC` (prefix cho ID)
>
> Tài liệu giữ nghiệp vụ **kèm lý do**, phản ánh trạng thái hiện tại, không ghi lịch sử thay đổi.
> Bản draft này chưa có người ra quyết định nghiệp vụ xác nhận: các lựa chọn là **đề xuất** của người phân tích, những
> chỗ cần chốt nằm ở §13.

> **Loại BC:** Read-model (tổng hợp dữ liệu của catalog và tồn kho để người mua tra cứu)

---

## 1. Nhiệm vụ

`search-service` giúp người mua **tìm thấy** sản phẩm mình muốn trong hàng triệu đơn vị bán được:
- Tìm theo từ khoá.
- Duyệt theo danh mục.
- Thu hẹp bằng bộ lọc (thương hiệu, giá, thuộc tính, còn hàng) và sắp xếp.

BC này **không sở hữu dữ liệu nào**. Sản phẩm, giá niêm yết, danh mục, thuộc tính thuộc catalog; tồn kho thuộc BC tồn
kho. Search chỉ tổ chức lại dữ liệu đó thành các câu trả lời cho câu hỏi "có những sản phẩm nào thoả điều kiện này,
xếp theo thứ tự nào".

## 2. Tại sao cần tách riêng

| Nhu cầu | Nếu không tách |
|---|---|
| Tìm chữ tiếng Việt, gõ có dấu hoặc không dấu, khớp theo tên, thương hiệu, thuộc tính, mô tả, trên **5.000.000 đơn vị bán được** (`requirement.md` §2.5) | Truy vấn "chứa chuỗi" trên toàn bộ danh mục: chậm, không có độ liên quan, không có dấu/không dấu |
| Bộ lọc kèm **số sản phẩm** cho từng giá trị (brand, khoảng giá, thuộc tính) trên kết quả đã lọc | Mỗi bộ lọc là một phép nhóm riêng trên dữ liệu ghép nhiều bảng; chi phí tăng theo số bộ lọc |
| Một kết quả cần dữ liệu từ **2 BC** (tên, giá, thuộc tính ở catalog; còn hàng ở tồn kho) | Không ghép được xuyên BC mà không gọi đồng bộ qua lại; mỗi lần tìm kéo theo hàng nghìn lời gọi |
| Luồng tìm kiếm chịu tải đọc lớn, lệch hẳn so với luồng ghi (`requirement.md` §2.2: đỉnh ~80.000 req/s đọc) | Tìm kiếm tranh tài nguyên với luồng ghi nóng của catalog và đơn hàng |

## 3. Không làm gì

- **Không** sở hữu hay sửa dữ liệu sản phẩm, danh mục, thương hiệu, thuộc tính → catalog.
- **Không** quản lý tồn kho, **không** quyết định còn mua được thật hay không → tồn kho, kiểm ở bước đặt hàng.
- **Không** quyết định giá cuối (khuyến mãi, mã giảm giá) → pricing/promotion. Search chỉ hiển thị **giá niêm yết**
  của catalog.
- **Không** phục vụ trang chi tiết sản phẩm → catalog (`RM-CAT-02`, `RM-CAT-03`).
- **Không** nhận thao tác ghi nào từ người dùng. Mọi thay đổi dữ liệu của BC này đến từ BC khác.
- **Không** thu thập hay phân tích hành vi người dùng (xem, bấm, mua) → BC riêng (xem `Q-SRC-10`).
- **Không** đảm bảo thông tin hiển thị trên kết quả là mới nhất đến từng giây. Giá và tình trạng còn hàng ở đây chỉ để
  tham khảo; trang chi tiết và bước đặt hàng mới là nơi chốt.

---

## 4. Actor và chức năng lõi

| Actor | Chức năng |
|---|---|
| **Buyer / Guest** | Tìm theo từ khoá; duyệt theo danh mục; lọc theo thương hiệu, khoảng giá, thuộc tính, "chỉ hiện hàng còn"; sắp xếp; xem các trang kết quả tiếp theo |
| **Seller** *(gián tiếp)* | Không thao tác gì ở BC này. Kỳ vọng sản phẩm mình đăng, sửa, ngừng bán được phản ánh đúng và đủ nhanh |
| **Admin** *(gián tiếp)* | Không thao tác gì ở BC này. Sản phẩm bị chặn phải biến khỏi kết quả; cấu hình "thuộc tính nào tìm được/lọc được/sắp xếp được" ở danh mục (catalog) quyết định bộ lọc mà người mua thấy |

**Ranh giới quyền:** mọi kết quả đều công khai, không cần đăng nhập. Người mua đã đăng nhập và Guest thấy cùng một kết
quả (chưa có cá nhân hoá, xem `Q-SRC-10`).

## 5. Ngôn ngữ chung

| Thuật ngữ chính | Cách gọi khác | Định nghĩa | Nghĩa ở BC khác |
|---|---|---|---|
| Sản phẩm | SPU | Như catalog `AGG-CAT-04`. Là đơn vị hiển thị của kết quả: **1 thẻ = 1 sản phẩm** | Catalog: điểm buyer tìm/duyệt tới |
| Đơn vị bán được | SKU, biến thể | Như catalog `AGG-CAT-05`. Là đơn vị mà bộ lọc thuộc tính và giá được xét | — |
| Được xem công khai | — | Sản phẩm đang bán và không bị chặn (`INV-CAT-044`). Điều kiện tối thiểu để xuất hiện trong kết quả | Catalog định nghĩa, search tham chiếu |
| Đang mở | Còn mua | Trạng thái của đơn vị bán được do seller bật/tắt (catalog `AGG-CAT-05`) | Catalog: "Còn mua" / "Hết" |
| Còn hàng | — | Đơn vị bán được có tồn kho khả dụng (do BC tồn kho xác định) | Tồn kho: số lượng khả dụng |
| Mua được | — | Đơn vị bán được vừa **đang mở** vừa **còn hàng**. Sản phẩm "mua được" khi có ≥1 đơn vị mua được (trong ngữ cảnh bộ lọc đang áp) | — |
| Hết hàng | — | Sản phẩm không có đơn vị nào mua được (dù vì tồn kho về 0 hay vì seller tắt hết). Với người mua hai trường hợp này giống nhau: chưa mua được ngay | — |
| Thẻ sản phẩm | Kết quả | Một dòng kết quả: ảnh đại diện, tên, giá "Từ X", nhãn "Hết hàng" nếu có | — |
| Đơn vị đại diện | — | Đơn vị bán được mà thẻ dùng để lấy giá. Chọn theo `INV-SRC-04` | — |
| Bộ lọc | Facet | Một chiều thu hẹp kết quả (thương hiệu, giá, một thuộc tính, danh mục con, còn hàng), mỗi giá trị kèm số sản phẩm | Catalog: "cách buyer tìm/lọc/sắp xếp" là cấu hình theo danh mục |
| Từ khoá | Truy vấn | Chuỗi người mua gõ | — |
| Độ tươi | — | Khoảng thời gian tối đa từ lúc dữ liệu đổi ở BC nguồn đến lúc kết quả phản ánh | — |

---

## 6. Mô hình domain

### 6.0 Suy ra thực thể

**N/A — BC read-model.** Search không có aggregate, không có vòng đời, không có quy tắc bất biến của dữ liệu ghi: mọi
thứ nó hiển thị đều do BC khác sở hữu và quyết định. Phần "domain" của search nằm ở **cách trả lời một truy vấn**
(lọc thế nào, xếp thế nào, tính giá thế nào), tức là ở §10.

Lý do không mô hình hoá "sản phẩm trong search" như một thực thể riêng: nếu có, hai nơi cùng nắm quyền sửa một sản phẩm
và lập tức phát sinh câu hỏi bên nào đúng. Giữ search ở chế độ chỉ đọc loại bỏ hẳn câu hỏi đó.

**Bản đồ aggregate:** không có.

## 7. Quy tắc xuyên aggregate

**N/A — không có aggregate.** Quy tắc riêng của việc tra cứu nằm ở §10.

---

## 8. Sự kiện nghiệp vụ

### 8.1 Phát ra cho BC khác (hợp đồng)

**Không** — search chỉ tra cứu, không có sự kiện nghiệp vụ nào cho BC khác cần biết.

### 8.2 Nhận từ BC khác

| Sự kiện | Từ BC | Dẫn đến |
|---|---|---|
| `EVT-CAT-041` ProductPublished | catalog | `RM-SRC-01`, `02`, `03`: sản phẩm bắt đầu xuất hiện |
| `EVT-CAT-042` ProductUnpublished | catalog | `RM-SRC-01`, `02`, `03`: sản phẩm biến khỏi kết quả |
| `EVT-CAT-043` ProductBlocked | catalog | `RM-SRC-01`, `02`, `03`: sản phẩm biến khỏi kết quả |
| `EVT-CAT-044` ProductUnblocked | catalog | `RM-SRC-01`, `02`, `03`: xuất hiện lại nếu sản phẩm đang bán |
| `EVT-CAT-045` ProductUpdated | catalog | `RM-SRC-01`, `02`, `03`: tên, mô tả, ảnh, thuộc tính đổi |
| `EVT-CAT-046` ProductDeleted | catalog | Không ảnh hưởng: chỉ xoá được bản nháp, chưa từng xuất hiện |
| `EVT-CAT-051` VariantCreated | catalog | `RM-SRC-01`, `02`, `03`: thêm một phiên bản có thể lọc và tính giá |
| `EVT-CAT-052` VariantPriceChanged | catalog | `RM-SRC-01`, `02`, `03`: giá trên thẻ, bộ lọc giá, sắp xếp giá |
| `EVT-CAT-053` VariantActivated / Deactivated | catalog | `RM-SRC-01`, `02`, `03`: đơn vị mua được ↔ không mua được |
| `EVT-CAT-054` VariantDeleted | catalog | Không ảnh hưởng: chỉ xoá theo bản nháp |
| `EVT-CAT-031` CategoryUpdated | catalog | `RM-SRC-02`, `03`: cây danh mục, luật tìm/lọc/sắp xếp của danh mục |
| *Đổi tên thương hiệu, đổi nhãn giá trị chuẩn của thuộc tính* | catalog | `RM-SRC-01`, `03`: tên và nhãn hiển thị. **Chưa có sự kiện nghiệp vụ** — `Q-SRC-06` |
| *Đơn vị bán được còn hàng / hết hàng* | tồn kho | `RM-SRC-01`, `02`, `03`: nhãn "Hết hàng", bộ lọc còn hàng. **Chưa có analysis nguồn để gán ID** — `Q-SRC-05` |

## 9. Policy

**N/A — không có policy.** Search không ra lệnh cho ai. Việc giữ dữ liệu hiển thị khớp với BC nguồn không phải policy
(quy ước 9) mà là đặc tính của từng `RM`, ghi ở cột "Dữ liệu nguồn" §10.

---

## 10. Hiển thị & tra cứu

Trọng tâm của BC. Các quy tắc dưới đây được nhóm theo chủ đề; mỗi nhóm có lý do và phương án đã cân nhắc, bảng tóm tắt
ở cuối mục.

### 10.1 Ai được xuất hiện

Sản phẩm chỉ xuất hiện trong kết quả khi **được xem công khai** (`INV-CAT-044`). Sản phẩm đang soạn, đã ngừng bán hoặc
bị chặn không xuất hiện, dù tìm bằng cách nào.

Đã cân nhắc vẫn hiện thẻ kèm nhãn "ngừng kinh doanh" cho sản phẩm đã ngừng bán (để người dùng đã lưu đường dẫn không
hoang mang). Loại, vì trang chi tiết chỉ cho xem sản phẩm được xem công khai (`RM-CAT-02`): thẻ sẽ dẫn tới một trang không xem được.
Với sản phẩm bị chặn vì vi phạm thì càng không nên hiện.

Đã cân nhắc ẩn sản phẩm khỏi kết quả ngay khi mọi đơn vị bán được đều bị seller tắt. Loại, vì catalog chủ ý để sản phẩm
vẫn "đang bán" khi hết đơn vị mở (tình huống vận hành tạm thời), và người mua đã từng tìm thấy không nên thấy nó biến
mất khó hiểu. Trường hợp này xử lý như hết hàng (10.2).

### 10.2 Hết hàng

**Hết hàng vẫn hiển thị**, kèm nhãn "Hết hàng", xếp xuống cuối kết quả mặc định, và người mua có thể bật bộ lọc "Chỉ hiện
hàng còn". Sản phẩm hết hàng liên tục **quá một ngưỡng thời gian** (đề xuất 30 ngày, `Q-SRC-01`) thì ngừng xuất hiện,
coi như seller đã bỏ sản phẩm đó.

Phương án đã cân nhắc:
- **Ẩn ngay khi hết hàng:** loại. Tình trạng còn/hết có thể đổi qua lại trong thời gian ngắn (đơn giữ hàng bị huỷ thì
  hàng trở lại), nên sản phẩm sẽ chập chờn biến mất rồi xuất hiện giữa hai lần người mua tải lại trang. Thêm nữa, báo
  hết hàng mà không cho người mua đường nào khác là ngõ cụt; để họ tự bật bộ lọc là cách trung thực nhất.
- **Làm mờ nhưng để lẫn trong kết quả:** loại, vì chiếm chỗ ở vị trí tốt của thứ chưa mua được.
- **Cho đặt trước, giao chậm hơn:** loại, vì tồn kho do seller tự khai và bước đặt hàng từ chối khi không đủ hàng. Chỉ
  đáng xem lại nếu sau này có chính sách đặt trước (ngoài phạm vi).
- **Không bao giờ ẩn:** loại, vì sản phẩm seller đã bỏ sẽ tồn tại mãi và làm nhiễu kết quả.

### 10.3 Giá trên thẻ

Thẻ hiển thị **"Từ X"**. X là giá niêm yết thấp nhất trong các **đơn vị mua được khớp bộ lọc đang áp**. Nếu không có
đơn vị mua được nào khớp thì lấy giá thấp nhất trong các đơn vị **đang mở** khớp bộ lọc, kèm nhãn "Hết hàng". Không bao
giờ hiển thị giá 0 (`INV-CAT-054`). Hệ quả: giá trên thẻ luôn nằm trong khoảng giá người mua đang lọc.

Phương án đã cân nhắc:
- **Giá thấp nhất của mọi đơn vị:** loại, vì thẻ có thể hiện giá của một phiên bản đã hết hàng. Người mua bấm vào rồi
  thấy phiên bản đó không mua được, đây là phàn nàn phổ biến.
- **Khoảng giá "X – Y":** loại, vì đặt giá cao nhất ngay trước mắt và thẻ dài hơn.
- **Giá cố định của sản phẩm, không phụ thuộc bộ lọc:** loại, vì người mua lọc "dưới 500.000" rồi thấy thẻ ghi 800.000
  (do sản phẩm còn phiên bản khác đắt hơn) thì tưởng bộ lọc sai.

Khi gõ từ khoá chỉ định một phiên bản ("iphone 15 256gb"), đơn vị đại diện ưu tiên phiên bản khớp từ khoá trước, rồi mới
tới tiêu chí còn hàng và giá thấp nhất. Nhờ đó thẻ hiện giá đúng phiên bản 256GB thay vì bản 128GB rẻ hơn.

Giá ở đây là **giá niêm yết**. Khi có khuyến mãi thì hiển thị giá nào: `Q-SRC-08`.

### 10.4 Lọc

**Lọc theo thuộc tính phân loại phải khớp trên cùng một đơn vị bán được.** Ví dụ lọc "Đỏ + size M + còn hàng" chỉ trả
sản phẩm có **một** đơn vị vừa Đỏ, vừa size M, vừa còn hàng. Nếu sản phẩm chỉ có "Đỏ – L còn hàng" và "Xanh – M hết
hàng", nó **không** được lọt vào kết quả. Đã cân nhắc khớp từng điều kiện rời rạc trên cả sản phẩm (sản phẩm "có Đỏ",
"có M", "có hàng" nên lọt): loại, vì người mua bấm vào rồi thấy không có phiên bản nào đúng như mình lọc. Đó là kết quả
sai chứ không chỉ kém liên quan. Thông số chung (không phân loại) giống nhau ở mọi đơn vị nên không gặp vấn đề này.

**Bộ lọc khả dụng phụ thuộc ngữ cảnh đang xem:**
- Ở **danh mục lá**: thương hiệu, khoảng giá, "chỉ hiện hàng còn", và các thuộc tính mà Admin đã bật cho phép lọc ở
  danh mục đó (catalog `AGG-CAT-03`; thuộc tính chỉ gán ở lá và mỗi lá độc lập).
- Ở **danh mục cấp trên**, và khi **chỉ có từ khoá** (chưa vào danh mục nào): thương hiệu, khoảng giá, "chỉ hiện hàng
  còn" và **danh sách danh mục con** để đi sâu tiếp; **không** có bộ lọc thuộc tính.

Lý do không gộp thuộc tính của các nhánh: thuộc tính chỉ có nghĩa trong ngữ cảnh loại hàng ("Dung lượng pin" của điện
thoại không liên quan "Laptop" dù cùng ngành "Điện tử"). Gộp lại ra một bộ lọc dài và lẫn lộn.

**Kiểu lọc theo kiểu thuộc tính:** chọn một hoặc nhiều → tick chọn; đúng/sai → có/không; số, ngày → theo khoảng (catalog
`AGG-CAT-03`). Thuộc tính chữ tự do **không** lọc được, vì mỗi seller viết một kiểu ("cotton", "vải cotton", "100%
cotton") nên bộ lọc vỡ thành vô số giá trị lẻ. Chữ tự do vẫn **tìm được** bằng từ khoá (10.5). Bản đầu có làm đủ số/ngày
hay không: `Q-SRC-02`.

**Mỗi bộ lọc được tính trên kết quả đã áp mọi bộ lọc khác, trừ chính nó.** Đã chọn thương hiệu Apple thì bộ lọc thương
hiệu vẫn hiện Samsung để người mua chọn thêm; các bộ lọc còn lại (màu, giá…) thu hẹp theo Apple. Đã cân nhắc thu hẹp cả
bộ lọc thương hiệu về chỉ còn Apple: loại, vì người mua không thấy được lựa chọn khác và không biết mình có thể chọn
thêm. Mỗi bộ lọc **chỉ hiện giá trị có trong kết quả**, kèm số sản phẩm; không liệt kê đủ mọi giá trị chuẩn (nhiều
giá trị sẽ toàn số 0).

**Số đếm là số sản phẩm**, không phải số đơn vị bán được. Đã cân nhắc đếm đơn vị: loại, vì một áo có 3 màu bị đếm thành 3
và tổng kết quả không khớp với số thẻ người mua thấy.

**Bộ lọc kèm nhãn hiển thị sẵn** (tên thuộc tính, nhãn giá trị), để người mua không phải tự tra ở nơi khác. Giá trị mà
người mua chọn được ghi bằng định danh ổn định chứ không bằng nhãn, để liên kết bộ lọc đã lưu hoặc chia sẻ không hỏng khi
Admin đổi nhãn.

### 10.5 Tìm theo từ khoá

Từ khoá khớp với: **tên sản phẩm**, **tên thương hiệu**, **giá trị các thuộc tính được đánh dấu "tìm được"** ở danh mục
(catalog `AGG-CAT-03`, gồm cả thuộc tính chữ tự do), và **mô tả**. Người mua Việt thường gõ **không dấu**, nên có dấu
và không dấu đều phải khớp. Mức ưu tiên khi chấm độ liên quan (đề xuất): tên > thương hiệu > thuộc tính > mô tả.

Phương án đã cân nhắc:
- **Chỉ khớp tên:** loại, vì bỏ sót các truy vấn theo đặc tính ("cotton", "Nhật Bản") mà seller không viết vào tên.
- **Khớp mọi thuộc tính:** loại, vì nhiễu; thuộc tính nào có ích cho tìm kiếm là quyết định của Admin theo từng loại hàng,
  đã có sẵn ở catalog.

Sản phẩm thuộc danh mục đã đóng **vẫn tìm được** bằng từ khoá (10.6). Chịu sai chính tả, đồng nghĩa và gợi ý từ khoá khi
gõ: `Q-SRC-03`.

### 10.6 Duyệt theo danh mục

Duyệt một danh mục gồm sản phẩm của **mọi danh mục con** bên dưới. Danh mục không "dùng được" (chính nó hoặc tổ tiên bị
đóng, catalog `AGG-CAT-03`) không còn đường duyệt theo cây, nhưng sản phẩm trong đó **vẫn xuất hiện khi tìm bằng từ
khoá**, đúng như catalog đã quy định ("chỉ mất đường duyệt theo cây, không mất khả năng tìm bằng từ khoá"). Đã cân nhắc
ẩn sản phẩm của nhánh đóng khỏi mọi kết quả: loại, vì trái với quyết định của catalog và làm mất doanh thu của sản phẩm
vẫn đang bán bình thường.

### 10.7 Thứ tự kết quả

Có các cách sắp xếp: **Phổ biến** (mặc định), **Mới nhất**, **Giá tăng dần**, **Giá giảm dần**, và theo thuộc tính số/ngày
nếu Admin bật ở danh mục (catalog `AGG-CAT-03`).

Khi chưa có dữ liệu hành vi người dùng, "Phổ biến" dựa vào các tín hiệu có sẵn (đề xuất, `Q-SRC-04`):
1. **Còn hàng** đứng trước hết hàng (10.2).
2. **Độ mới**, suy giảm dần theo thời gian kể từ lần đăng bán đầu tiên. Không dùng thời điểm "mới nhất tuyệt đối", nếu
   không hàng cũ tốt luôn chìm.
3. **Độ hoàn thiện của listing** (số ảnh, độ dài mô tả, số thuộc tính đã điền): khuyến khích seller làm đầy đủ.
4. Khi có từ khoá thì cộng thêm **độ khớp từ khoá**.

Phương án đã cân nhắc: xếp theo thời điểm tạo thuần (seller đăng lại liên tục để lên đầu); ngẫu nhiên (thứ tự đổi giữa
hai lần tải, phân trang lặp và sót); theo giá (không phản ánh mức phù hợp).

**Yêu cầu về khả năng mở rộng:** sau này sẽ có tín hiệu hành vi (lượt xem, đã bán, đánh giá) và có thể cá nhân hoá. Cách
người mua thao tác (tìm, lọc, sắp xếp, xem trang tiếp) **không được thay đổi** khi đó; chỉ thứ tự kết quả thay đổi.

### 10.8 Phân trang

Người mua xem các trang tiếp theo mà **không thấy lặp và không bị sót** sản phẩm trong cùng một lần tìm, kể cả khi dữ
liệu đổi giữa chừng ở mức bình thường. Mỗi lần tìm hiển thị **tổng số sản phẩm** khớp. Đã cân nhắc đánh số trang thuần
(trang 1, 2, 3…): loại, vì khi dữ liệu thêm/bớt giữa hai lần tải thì sản phẩm dịch chỗ, gây lặp hoặc sót; và không hợp
khi sau này thứ tự có phần cá nhân hoá. Giới hạn độ sâu tối đa: `Q-SRC-09`. Mức đảm bảo tuyệt đối hay tốt nhất có thể: `Q-SRC-14`.

### 10.9 Độ tươi và tính tham khảo

Kết quả có thể **trễ** so với BC nguồn. Mức trễ chấp nhận được khác nhau theo loại thay đổi (cột "Độ tươi" bên dưới), vì
hậu quả của việc trễ khác nhau:
- Sản phẩm bị **chặn/ngừng bán** vẫn hiện thêm ít giây: người mua bấm vào sẽ không xem được trang chi tiết
  (`INV-CAT-044` được kiểm ở đó), nên rủi ro là nhìn thấy thẻ chứ không phải mua được.
- **Còn hàng/hết hàng** đổi nhanh nhất và ảnh hưởng thẳng đến việc bấm vào, nên cần độ tươi ngắn nhất.
- **Nội dung, giá, danh mục** đổi thưa, chịu được trễ lâu hơn.

Giá và tình trạng còn hàng trên kết quả **chỉ để tham khảo**; bước đặt hàng mới là nơi chốt (§3).

### 10.10 Bảng tóm tắt

| ID | Ai | Xem gì | Điều kiện được xem | Dữ liệu nguồn (BC / sự kiện nghiệp vụ) | Độ tươi chấp nhận được *(đề xuất, `Q-SRC-07`)* |
|---|---|---|---|---|---|
| `RM-SRC-01` | Buyer/Guest | Kết quả tìm theo từ khoá, có thể kèm bộ lọc và sắp xếp | `INV-SRC-01`…`04`, `05`, `09`, `10`, `12`, `13` | catalog: `EVT-CAT-041`…`045`, `051`…`053`; tồn kho: còn/hết hàng (`Q-SRC-05`); đổi tên thương hiệu/nhãn (`Q-SRC-06`) | Chặn/ngừng bán: ≤ 30s. Còn hàng: ≤ 5s. Nội dung, giá: ≤ 30s |
| `RM-SRC-02` | Buyer/Guest | Kết quả duyệt theo một danh mục (gồm mọi danh mục con) | Như `RM-SRC-01`, cộng `INV-SRC-11` | Như `RM-SRC-01`, cộng catalog `EVT-CAT-031` | Như trên. Cây danh mục: ≤ 60s |
| `RM-SRC-03` | Buyer/Guest | Các bộ lọc khả dụng cho ngữ cảnh hiện tại (từ khoá / danh mục), mỗi giá trị kèm số sản phẩm; danh mục con kèm số sản phẩm | `INV-SRC-06`…`08` | Như `RM-SRC-02`, cộng đổi nhãn (`Q-SRC-06`) | Như `RM-SRC-02` |

**Quy tắc tra cứu**

| ID | Quy tắc | Loại | Áp dụng cho |
|---|---|---|---|
| `INV-SRC-01` | Chỉ sản phẩm được xem công khai (`INV-CAT-044`) mới xuất hiện trong mọi kết quả | Liên tục | `RM-SRC-01`, `02`, `03` |
| `INV-SRC-02` | Sản phẩm không có đơn vị mua được được xử lý như hết hàng, không ẩn ngay | Liên tục | `RM-SRC-01`, `02`, `03` |
| `INV-SRC-03` | Hết hàng: hiện kèm nhãn, xếp cuối mặc định, có bộ lọc "chỉ hiện hàng còn", ngừng xuất hiện sau ngưỡng hết liên tục (`Q-SRC-01`) | Liên tục | `RM-SRC-01`, `02`, `03` |
| `INV-SRC-04` | Giá thẻ "Từ X": giá niêm yết thấp nhất trong đơn vị mua được khớp bộ lọc; không có thì đơn vị đang mở khớp bộ lọc kèm nhãn hết hàng; không hiển thị giá 0. Khi có từ khoá chỉ định phiên bản, đơn vị đại diện ưu tiên phiên bản khớp | Liên tục | `RM-SRC-01`, `02` |
| `INV-SRC-05` | Lọc thuộc tính phân loại phải khớp trên **cùng một** đơn vị bán được | Liên tục | `RM-SRC-01`, `02`, `03` |
| `INV-SRC-06` | Bộ lọc thuộc tính chỉ có ở danh mục lá; ở cấp trên và khi chỉ có từ khoá thì chỉ thương hiệu, giá, còn hàng, danh mục con | Liên tục | `RM-SRC-03` |
| `INV-SRC-07` | Mỗi bộ lọc tính trên kết quả áp mọi bộ lọc khác trừ chính nó; chỉ hiện giá trị có trong kết quả kèm số đếm | Liên tục | `RM-SRC-03` |
| `INV-SRC-08` | Số đếm là số sản phẩm; bộ lọc kèm nhãn hiển thị sẵn; giá trị chọn ghi bằng định danh ổn định | Liên tục | `RM-SRC-03` |
| `INV-SRC-09` | Từ khoá khớp tên, thương hiệu, thuộc tính "tìm được", mô tả; có dấu và không dấu đều khớp | Liên tục | `RM-SRC-01` |
| `INV-SRC-10` | Sản phẩm thuộc danh mục đã đóng vẫn tìm được bằng từ khoá | Liên tục | `RM-SRC-01` |
| `INV-SRC-11` | Duyệt danh mục gồm sản phẩm của mọi danh mục con; danh mục không "dùng được" không có đường duyệt theo cây | Liên tục | `RM-SRC-02` |
| `INV-SRC-12` | Thứ tự mặc định "Phổ biến" theo tín hiệu ở 10.7; cách người mua thao tác không đổi khi thêm tín hiệu sau này | Liên tục | `RM-SRC-01`, `02` |
| `INV-SRC-13` | Trang tiếp theo không lặp, không sót trong một lần tìm; luôn có tổng số sản phẩm khớp | Liên tục | `RM-SRC-01`, `02` |

---

## 11. Quan hệ với BC khác

| BC | Chiều | Kiểu quan hệ | BC này cung cấp / nhận gì |
|---|---|---|---|
| catalog | Upstream (catalog → search) | Conformist phía search; catalog cung cấp dạng OHS-PL (analysis catalog §11) | Nhận `EVT-CAT-031`, `041`…`045`, `051`…`053` và theo sát mô hình sản phẩm, đơn vị bán được, danh mục của catalog. Không gửi gì ngược lại |
| tồn kho | Upstream (tồn kho → search) | Customer-Supplier | Nhận trạng thái còn/hết hàng theo từng đơn vị bán được. ID sự kiện chưa gán vì chưa có analysis của tồn kho (`Q-SRC-05`) |
| pricing / promotion | Upstream *(chưa tồn tại)* | — | Khi có, có thể cần nhận giá sau khuyến mãi (`Q-SRC-08`) |
| tracking (hành vi người dùng) | Upstream *(chưa tồn tại)* | — | Khi có, nhận các tín hiệu đã tổng hợp cho xếp hạng (`Q-SRC-10`) |
| cart, order | — | Không quan hệ | Tìm kiếm không nhận gì từ giỏ hàng hay đơn hàng |

**Yêu cầu đặt ra cho catalog** (chờ catalog xác nhận):
1. Thông báo khi **đổi tên thương hiệu** hoặc **đổi nhãn giá trị chuẩn** của thuộc tính. Catalog hiện chỉ có sự kiện khi
   đổi danh mục và sản phẩm (`Q-SRC-06`).
2. Cung cấp **thời điểm sản phẩm được đăng bán lần đầu** như một sự thật nghiệp vụ (`Q-SRC-04`).

## 12. Kỳ vọng phi chức năng từ nghiệp vụ

> Phân tích trước, kết luận sau (quy ước 13): §12.1 dữ kiện, §12.2 phân tích, §12.3 kết luận.

### 12.1 Dữ kiện từ yêu cầu gốc

| Dữ kiện | Giá trị | Nguồn |
|---|---|---|
| Người dùng đồng thời, ngày thường | 20.000 | `requirement.md` §2.1 |
| Nhịp truy cập mỗi người | ~1 request / 2,5s | §2.1 |
| Đọc từ bên ngoài, ngày thường | ~8.000 req/s | §2.1 |
| Người dùng đồng thời, flash sale | 200.000 (gấp 10) | §2.2 |
| Đọc lúc cao điểm | ~80.000 req/s, chủ yếu "trang sản phẩm + đồng hồ đếm ngược" | §2.2 |
| Thời lượng một đợt flash sale | 15–30 phút | §2.2 |
| Độ trễ P99: tìm kiếm / chi tiết sản phẩm / thêm vào giỏ / thanh toán | < 200ms / < 100ms / < 200ms / < 2s | §2.3 |
| Quy mô | 5.000.000 SKU | §2.5 |
| Guest duyệt catalog, xem sản phẩm, không cần đăng nhập | — | §3 |

### 12.2 Phân tích

**a. Độ trễ tìm kiếm dưới 200ms (cho sẵn).** Con số này do yêu cầu gốc cho, ta không tự chọn. Phân tích hỏi nó kéo theo
gì. Tìm kiếm được gấp đôi chi tiết sản phẩm (100ms) vì phải tính bộ lọc và xếp hạng trên tập kết quả lớn, trong khi chi
tiết chỉ đọc một sản phẩm; bằng mức thêm vào giỏ. Điểm cần chú ý: **mỗi lần đổi bộ lọc là một lần tìm mới**. Người mua
đổi 3–5 bộ lọc trong một phiên (giả định) thì chờ 3–5 lần, nên 200ms là trần cho **từng lần**, không nới thêm được. Cả
hành trình tìm → mở chi tiết → thêm vào giỏ chờ tối đa (ở P99 từng bước) 200 + 100 + 200 = 500ms trước bước thanh toán
2s.

**b. Tải tìm kiếm.** Yêu cầu gốc chỉ cho tổng tải đọc, không tách phần tìm kiếm. Thay vì chọn một tỉ lệ phần trăm rồi
lấy nó làm kết quả, ta dựng mô hình một phiên duyệt hàng và đếm loại request (mọi con số trong bảng là **giả định**):

| Request trong một phiên | Ngày thường | Flash sale |
|---|---|---|
| Tìm hoặc mở danh mục | 2 | 1 |
| Đổi bộ lọc, sắp xếp | 4 | 1 |
| Xem trang kết quả tiếp theo | 2 | 0 |
| **Loại tìm kiếm** | **8** | **2** |
| Mở trang sản phẩm (mỗi trang 2 truy vấn: chi tiết và đơn vị bán được) | 4 trang = 8 | 3 trang = 6 |
| Đồng hồ đếm ngược | 0 | 4 |
| Khác (cây danh mục, thương hiệu, giỏ hàng, tài khoản) | 4 | 4 |
| **Tổng** | **20** | **16** |
| **Tỉ lệ tìm kiếm** | 8 ÷ 20 = 40% | 2 ÷ 16 = 12,5% |

Ngày thường người mua đi tìm nên tìm kiếm chiếm nhiều; lúc flash sale họ vào thẳng trang chiến dịch và sản phẩm nên
chiếm ít (đúng với ghi chú "trang sản phẩm + đồng hồ đếm ngược" của yêu cầu gốc). Nhân với tổng tải:
- Ngày thường: 8.000 × 40% = **3.200 req/s**.
- Cao điểm: 80.000 × 12,5% = **10.000 req/s**.

Độ nhạy: nếu người mua tìm ít hơn một nửa thì ngày thường còn 4 ÷ 16 = 25%, tức 2.000 req/s; lúc flash sale (1 thay vì 2)
còn 1 ÷ 15 ≈ 6,7%, tức ~5.300 req/s. Kết quả nằm trong khoảng 2.000–3.200 (thường) và 5.300–10.000 (cao điểm). Ta lấy **cận trên** làm mức cần kiểm chứng, vì
thiếu công suất thì hỏng lúc quan trọng nhất còn dư thì chỉ tốn chi phí. Mỗi lần tìm còn nặng hơn một lần đọc thường (phải
tính bộ lọc), nên tải thật cần nhân thêm hệ số chưa đo được. Mô hình phiên là suy đoán, sẽ thay bằng số liệu truy cập
thật khi có (`Q-SRC-07`).

**c. Quy mô.** 5.000.000 là số đơn vị bán được. Trung bình 3–5 đơn vị mỗi sản phẩm cho khoảng 1–1,7 triệu sản phẩm (giả
định, `Q-CAT-06`). Truy vấn rộng nhất (không từ khoá, không danh mục) khớp toàn bộ ngần ấy sản phẩm. Hệ quả: tổng số kết
quả và số đếm của bộ lọc luôn phải tính trên tập cỡ triệu, nhưng người mua không thể xem hết, nên cần giới hạn độ sâu
(`Q-SRC-09`).

**d. Độ tươi.** Câu hỏi: người mua chịu nhìn thấy thông tin sai trong bao lâu? Câu trả lời khác nhau theo hậu quả của
việc sai, nên tách theo loại thay đổi. Công cụ chung là **mức phơi nhiễm**: số lần thông tin sai bị nhìn thấy = tải
tìm kiếm × độ trễ. Đợt flash sale ngắn nhất là 15 phút = 900s, dùng làm thước đo tỉ lệ.

- **Còn hàng / hết hàng.** Hậu quả: người mua bấm vào thứ đã hết, hoặc bỏ qua thứ vẫn còn, đúng lúc cạnh tranh gay gắt
  nhất (sản phẩm flash sale chuyển từ còn sang hết nhanh nhất; giả định hàng flash sale hết trong vài phút). Tiêu chí ta
  chọn: nhãn sai chiếm **không quá 1%** thời lượng đợt ngắn nhất, tức 1% × 900s = **9s**. Các mức khác đã cân nhắc:
  - 5% (45s): mỗi lần hết hàng, nhãn sai kéo dài gần một phút trong đợt 15 phút, người mua gọi đó là "hàng ảo";
  - 0,1% (0,9s): đòi truyền dữ liệu gần thời gian thực cho mọi thay đổi tồn kho giữa lúc tải ghi dồn dập nhất, tốn kém
    mà lợi ích so với 9s là nhỏ.

  Ở 10.000 req/s: 9s là 90.000 lần nhìn sai, 45s là 450.000. Làm tròn xuống **5s** (0,6% thời lượng, 50.000 lần) để chừa
  dư địa cho độ trễ của đường truyền dữ liệu; sẽ kiểm bằng đo.
- **Sản phẩm bị chặn hoặc ngừng bán.** Thẻ cũ dẫn tới trang chi tiết không xem được (`RM-CAT-02` chỉ cho xem sản phẩm
  được xem công khai, `INV-CAT-044`), nên hậu quả là một cú bấm hụt, không ai mua được sản phẩm đó. Sự kiện này hiếm
  và rủi ro thấp hơn tồn kho, chọn **30s**. Ngoại lệ: chặn khẩn vì vi phạm nghiêm trọng có thể cần mức chặt hơn, đã
  ghi ở `Q-SRC-07`.
- **Nội dung, giá.** Trang chi tiết và bước đặt hàng luôn dùng giá đúng, nên hậu quả chỉ là thẻ lệch giá tạm thời. Tiêu
  chí lỏng hơn tồn kho: lệch không quá **5%** thời lượng đợt ngắn nhất, tức 45s; chọn **30s** (3,3%). Giá đổi đồng loạt
  lúc mở flash sale, khi đó 30s đầu thẻ có thể còn giá cũ. Cách xử lý thuộc vận hành: đặt giá flash sale trước giờ mở
  bán ít nhất 1 phút.

Các tiêu chí 1% và 5% là quyết định nghiệp vụ, không suy ra được từ yêu cầu gốc; cần người quyết định xác nhận
(`Q-SRC-07`).

**e. Sẵn sàng.** Yêu cầu gốc không có mức sẵn sàng cho tìm kiếm. Phân tích hậu quả của việc tìm kiếm ngừng: Guest và
người mua vẫn vào được sản phẩm bằng đường dẫn trực tiếp, từ giỏ hàng hoặc từ thông báo (§3), và thanh toán có mức độ
trễ riêng (§2.3). Mất tìm kiếm làm mất một phần lượt truy cập chứ không dừng doanh thu, nên mức sẵn sàng của tìm kiếm
có thể thấp hơn luồng đặt hàng. Con số cụ thể chưa có (`Q-SRC-07`).

### 12.3 Kết luận

| Chức năng | Kỳ vọng | Lý do nghiệp vụ | Nguồn / phân tích |
|---|---|---|---|
| Tìm kiếm + lọc | P99 < 200ms cho **từng lần** tìm hoặc đổi bộ lọc | Người mua rời trang nếu chờ; mỗi lần đổi bộ lọc là một lần tìm | `requirement.md` §2.3; 12.2a |
| Tải tìm kiếm | ~3.200 req/s thường, ~10.000 req/s cao điểm *(giả định)* | Ước lượng từ mô hình phiên | 12.2b; `Q-SRC-07` |
| Quy mô dữ liệu | ~5.000.000 đơn vị bán được (~1–1,7 triệu sản phẩm) | Quy mô mục tiêu của sàn | `requirement.md` §2.5; 12.2c |
| Độ tươi: còn hàng | ≤ 5s *(đề xuất)* | Nhãn sai chiếm dưới 1% đợt flash sale ngắn nhất | 12.2d; `Q-SRC-07` |
| Độ tươi: chặn / ngừng bán | ≤ 30s *(đề xuất)* | Hậu quả chỉ là một cú bấm hụt | 12.2d; `Q-SRC-07` |
| Độ tươi: nội dung, giá | ≤ 30s *(đề xuất)* | Lệch không quá 5% đợt ngắn nhất; trang chi tiết luôn đúng | 12.2d; `Q-SRC-07` |
| Sẵn sàng | Thấp hơn luồng đặt hàng; chưa có con số | Mất tìm kiếm không dừng doanh thu | 12.2e |

## 13. Chưa chốt

| ID | Câu hỏi | Ảnh hưởng | Chặn gì | Dự kiến giải quyết khi |
|---|---|---|---|---|
| `Q-SRC-01` | Ngưỡng ngừng hiển thị sản phẩm hết hàng liên tục: đề xuất 30 ngày. Có khác nhau theo ngành hàng không | `INV-SRC-03` | Đối chiếu với seller về hàng đặt theo đợt | Trước khi chốt bản đầu |
| `Q-SRC-02` | Bản đầu có lọc theo khoảng cho thuộc tính **số** và **ngày** không, hay chỉ chọn và đúng/sai | 10.4, `INV-SRC-06` | Giao diện bộ lọc khoảng (thanh trượt, khoảng gợi ý) | Khi có danh mục thật cần lọc số/ngày |
| `Q-SRC-03` | Có chịu sai chính tả, đồng nghĩa, gợi ý từ khoá khi gõ trong bản đầu không (đề xuất: không) | 10.5, `INV-SRC-09` | — | Sau khi có dữ liệu truy vấn thật |
| `Q-SRC-04` | Trọng số của "Phổ biến" (còn hàng, độ mới, hoàn thiện listing); có giới hạn số sản phẩm của một seller ở trang đầu không; cần catalog cung cấp "thời điểm đăng bán lần đầu" (phía catalog: `Q-CAT-05`) | 10.7, `INV-SRC-12`, §11 | Yêu cầu bổ sung cho catalog | Trước khi chốt bản đầu |
| `Q-SRC-05` | Định nghĩa "còn hàng" của tồn kho (tính cả hàng đang giữ chỗ không) và sự kiện nghiệp vụ tương ứng. Tồn kho chưa có analysis để gán ID | `INV-SRC-02`, `03`, §8.2 | Nguồn dữ liệu còn hàng | Khi tồn kho có analysis |
| `Q-SRC-06` | Catalog có phát sự kiện khi đổi tên thương hiệu và đổi nhãn giá trị chuẩn không. Hiện không có (phía catalog: `Q-CAT-04`) | `RM-SRC-01`, `03`, §8.2 | Nhãn và tên hiển thị đúng sau khi Admin đổi | Cùng đợt xử lý master data với catalog |
| `Q-SRC-07` | Chốt tỉ lệ tải thuộc tìm kiếm trong tổng tải đọc (giả định ở §12.2b), con số độ tươi (kể cả mức riêng cho chặn khẩn cấp) và mức sẵn sàng; ghi vào `requirement.md` | §12, §10.10 | Kiểm chứng NFR | Trước khi chốt thiết kế kỹ thuật |
| `Q-SRC-08` | Khi có khuyến mãi: thẻ, bộ lọc giá, sắp xếp giá dùng giá niêm yết hay giá sau khuyến mãi | 10.3, `INV-SRC-04` | Sự ra đời của pricing/promotion | Khi có pricing/promotion |
| `Q-SRC-09` | Giới hạn độ sâu tối đa khi xem trang tiếp theo trong một lần tìm | 10.8, `INV-SRC-13` | — | Sau khi có dữ liệu sử dụng thật |
| `Q-SRC-10` | Hành vi người dùng (xem, bấm, mua) và cá nhân hoá: BC nào sở hữu, cần sự đồng ý của người dùng thế nào, dữ liệu lưu bao lâu | 10.7, §3, §11 | Xếp hạng theo hành vi | Khi làm tracking |
| `Q-SRC-11` | Có cần "xem mọi sản phẩm của một shop" / lọc theo seller trong bản đầu không | §4, 10.4 | Trang shop | Khi làm giao diện storefront |
| `Q-SRC-12` | Sắp xếp "Giá giảm dần": xếp theo giá hiển thị trên thẻ ("Từ X") hay theo giá cao nhất của sản phẩm. Hai cách cho thứ tự khác nhau khi sản phẩm có nhiều mức giá | 10.3, 10.7, `INV-SRC-04` | Cách sắp xếp giá | Trước khi chốt bản đầu |
| `Q-SRC-13` | Số sản phẩm ở tổng kết quả và ở bộ lọc có được phép xấp xỉ (sai lệch nhỏ) khi tập rất lớn, hay phải chính xác | 10.4, `INV-SRC-08` | Chi phí tính bộ lọc | Sau khi đo mức sai lệch thực tế |
| `Q-SRC-14` | `INV-SRC-13` (không lặp, không sót) bắt buộc tuyệt đối, hay chấp nhận "tốt nhất có thể trong giới hạn độ sâu khi dữ liệu ổn định". Bảo đảm tuyệt đối tốn kém khi vừa gom theo sản phẩm vừa xếp theo độ liên quan | 10.8, `INV-SRC-13` | Cách phân trang | Trước khi chốt bản đầu |

---

## Quy ước ID

| Prefix | Ý nghĩa |
|---|---|
| `AGG-XXX-nn` | Aggregate |
| `INV-XXX-nn` | Quy tắc bất biến (`9x` cho xuyên aggregate; quy tắc tra cứu ở §10) |
| `CMD-XXX-nn` | Hành động |
| `EVT-XXX-nn` | Sự kiện |
| `POL-XXX-nn` | Policy |
| `RM-XXX-nn` | Hiển thị / tra cứu |
| `Q-XXX-nn` | Câu hỏi chưa chốt |

## Nguồn tham chiếu

Bản draft dựa trên hai nguồn: yêu cầu sơ bộ ở `requirement.md` và các phần đã chốt của analysis catalog. **Chưa đối chiếu
với thị trường** (Shopee, Tiki, Lazada, Amazon…): các mục "phương án đã cân nhắc" ở §10 là suy luận của người phân tích,
cần kiểm chứng trước khi coi là đã chốt.

## Tài liệu liên quan

- [`global/1.requirement/requirement.md`](../../global/1.requirement/old/requirement.md) — yêu cầu gốc (§2.2, §2.3, §2.5)
- [`../catalog-service/analysis.md`](../catalog-service/analysis.md) — nguồn của mọi `EVT-CAT-*` và `INV-CAT-*` được tham chiếu

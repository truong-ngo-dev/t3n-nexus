# Danh mục hàng: service (`catalog-service`)

> **Trạng thái:** Feature 05 (cấu trúc danh mục) đã làm · **Cập nhật:** `2026-11-04` · **Mã BC:** `CAT`
>
> Chỉ ghi kết luận, ở mức aggregate. Mỗi mục có nhãn **Dự kiến** hoặc **Đã làm**; mục thuộc feature 05 (danh mục, mẫu thuộc tính, thương hiệu, kiểm toán và `EVT-CAT-03` phía phát) và feature 07, 08, 09 (sản phẩm: soạn, ảnh, đăng, gỡ, xóa nháp, bản sao trạng thái người bán, `EVT-CAT-01`, `EVT-CAT-02`) là **Đã làm**, còn lại là **Dự kiến**. Chi tiết (trường, bảng, cột, mã lỗi) nằm ở [`data.md`](data.md) và [`api.yaml`](api.yaml). Nghiệp vụ và lý do: [`analysis/0.overview.md`](analysis/0.overview.md) và file từng aggregate.
>
> **Đã chốt:** sự kiện trạng thái đầy đủ thay cho sự kiện nhỏ theo chuyển đổi (D6, `2026-11-06`, ADR-0014); mã định danh UUID v7 (D3) `2026-10-26`.

## 1. Vai trò

Giữ cấu trúc dùng chung để mô tả hàng (cây danh mục, mẫu thuộc tính, thương hiệu), sản phẩm của người bán, biến thể và giá niêm yết, ảnh; quyết định sản phẩm nào đang hiển thị; công bố thay đổi cho Tìm kiếm và Tồn kho. Không giữ trạng thái người bán (chỉ một bản sao), tồn kho, khuyến mãi, phí.

## 2. Aggregate

### AGG-CAT-01 Danh mục (`Category`) · Đã làm

**Trách nhiệm:** giữ một nút của cây ba cấp và việc gán mẫu thuộc tính cho danh mục lá.
**Bất biến giữ:** INV-CAT-01, 02 (kiểm tên trong cùng cha qua kho), 03, 04, 05.

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi (ADR-0001) | |
| `parentId`, `level` | Mã, số | Cấp một: không cha; cấp hai, ba: có cha | Tối đa ba cấp (INV-CAT-01); không đổi sau khi tạo | Cấp ba là lá |
| `name`, `nameKey` | Chữ | Có | `nameKey` chuẩn hóa (bỏ dấu, chữ thường); duy nhất trong cùng cha (INV-CAT-02) | |
| `sortOrder` | Số | Có | Vị trí giữa các anh em cùng cha | |
| `status` | Trạng thái | Có | `ACTIVE`, `INACTIVE`; ngừng dùng không cascade xuống con | |
| `assignments` | Danh sách giá trị | Chỉ ở lá | Mỗi dòng: mã mẫu, bắt buộc, thứ tự, ràng buộc riêng theo danh mục; mỗi mẫu một lần (INV-CAT-03) | Thay toàn bộ danh sách một lần |
| `version`, `createdAt`, `updatedAt` | | Có | `version` tăng mỗi thay đổi | |

| Hành vi | Tiền điều kiện | Hậu điều kiện | Event | Chặng |
|---|---|---|---|---|
| Tạo gốc, tạo con (`createRoot`, `createChild`) | Cha tồn tại, chưa quá cấp ba; cha đang đóng vẫn được | Có danh mục `ACTIVE` | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Đổi tên, sắp xếp (`rename`, `reorder`) | | Tên, vị trí đổi | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Ngừng, bật lại dùng (`deactivate`, `activate`) | | Trạng thái đổi | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Thay danh sách gán thuộc tính (`replaceAssignments`) | Chỉ lá; mẫu tồn tại và đang dùng | Danh sách mới | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Xóa (`delete`) | Không có con, chưa có sản phẩm tham chiếu (INV-CAT-05, 98) | Bị xóa | `EVT-CAT-03`, `EVT-CAT-04` | C1a |

**Cổng:** kho danh mục (lưu; tìm theo cha; kiểm tên đã dùng trong cha; có con; có sản phẩm tham chiếu; có tổ tiên ngừng dùng); cổng sinh mã. Cách lưu cây để truy vấn tổ tiên, con nhanh: `data.md`.

### AGG-CAT-02 Mẫu thuộc tính (`AttributeTemplate`) · Đã làm

**Trách nhiệm:** giữ một thuộc tính dùng chung toàn sàn, kiểu nhập và danh sách giá trị chuẩn.
**Bất biến giữ:** INV-CAT-10, 11, 12.

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi | |
| `name` | Chữ | Có | Mã kỹ thuật: chữ thường không dấu, chữ số, gạch dưới (không đầu, không cuối, không liền nhau), tối đa 100; duy nhất toàn sàn (INV-CAT-10); không đổi | |
| `displayName`, `hint` | Chữ | Có, tùy chọn | Gợi ý tối đa 200 ký tự | |
| `inputType` | Kiểu nhập | Có | `SINGLE_SELECT`, `MULTI_SELECT`, `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`; không đổi (INV-CAT-12) | |
| `unit` | Chữ | Chỉ `NUMBER` | Tối đa 20 ký tự; không đổi | |
| `status` | Trạng thái | Có | `ACTIVE`, `INACTIVE` | |
| `options` | Danh sách thực thể | Kiểu chọn | Mỗi tùy chọn: mã, giá trị, tên hiển thị, thứ tự, trạng thái; giá trị là mã kỹ thuật: chữ thường không dấu, chữ số, gạch dưới hoặc gạch ngang (không đầu, không cuối, không liền nhau), tối đa 100, duy nhất trong mẫu, không đổi (INV-CAT-11) | |

| Hành vi | Tiền điều kiện | Hậu điều kiện | Event | Chặng |
|---|---|---|---|---|
| Tạo | | Mẫu `ACTIVE` | `EVT-CAT-04` | C1a |
| Sửa tên hiển thị, gợi ý (`updateDetails`) | | | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Ngừng, bật mẫu (`deactivate`, `activate`) | | | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Thêm tùy chọn (`addOption`) | Kiểu chọn; mẫu đang dùng; giá trị chưa có | Tùy chọn xếp cuối | `EVT-CAT-03`, `EVT-CAT-04` | C1a |
| Sửa tên hiển thị, ngừng, bật, sắp xếp tùy chọn | Tùy chọn tồn tại; sắp xếp phải đủ mọi tùy chọn | | `EVT-CAT-03`, `EVT-CAT-04` | C1a |

**Dùng được** (`isUsable`): `ACTIVE` và, với kiểu chọn, còn ít nhất một tùy chọn `ACTIVE`; là hàm dùng chung duy nhất cho các nơi kiểm thuộc tính bắt buộc. Ngừng tùy chọn cuối cùng được phép (mẫu khi đó không dùng được). Tên mẫu đặt lúc tạo, không có lệnh đổi (chỉ đổi tên hiển thị, gợi ý). Không có hành vi xóa.
**Cổng:** kho mẫu (lưu; tìm theo mã; tìm nhiều mã một lần); cổng sinh mã.

### AGG-CAT-03 Thương hiệu (`Brand`) · Đã làm

**Trách nhiệm:** giữ danh sách thương hiệu.
**Bất biến giữ:** INV-CAT-20, 21.

| Thuộc tính | Ràng buộc |
|---|---|
| `id` | Duy nhất, không đổi |
| `name`, `nameKey` | `nameKey` chuẩn hóa; duy nhất toàn sàn (INV-CAT-20) |
| `status` | `ACTIVE`, `INACTIVE` |

Hành vi: tạo, đổi tên, ngừng, bật lại; phát `EVT-CAT-04`. Không có hành vi xóa. **Cổng:** kho thương hiệu; cổng sinh mã. Chặng C1a.

### AGG-CAT-04 Sản phẩm (`Product`) · Đã làm (thông tin, thuộc tính, trục, ảnh, tạo, sửa, đăng, gỡ, đăng lại, xóa nháp, event; feature 07, 08, 09) · Dự kiến (chặn, mở chặn: C1b)

**Trách nhiệm:** giữ mô tả sản phẩm của một người bán, giá trị thuộc tính, ảnh, và trạng thái đăng.
**Bất biến giữ:** INV-CAT-30, 31, 32, 33, 34, 35, 36. Quy tắc xuyên aggregate ở mục 3.

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi | |
| `sellerAccountId` | Mã tài khoản | Có | Không đổi (INV-CAT-30) | Chủ sản phẩm |
| `categoryId` | Mã | Có | Danh mục cấp ba dùng được lúc tạo; không đổi (INV-CAT-97) | |
| `brandId` | Mã | Không | Thương hiệu đang dùng khi chọn mới (INV-CAT-21) | |
| `title`, `description` | Chữ | Có | 5 đến 200 ký tự; chữ thuần, tối đa 5.000 ký tự (INV-CAT-31) | Lưu nguyên, thoát ký tự khi hiển thị |
| `attributeValues` | Danh sách giá trị | | Mỗi dòng: mã mẫu, các giá trị (chữ hoặc mã tùy chọn), có phải trục không; trục: thuộc tính chọn một, tập giá trị dùng chính là các giá trị của dòng, tối đa 2 trục; thuộc tính bắt buộc đã làm trục coi như đã điền (INV-CAT-32, 33, 91) | |
| `images` | Danh sách thực thể | | Tối đa 9; mỗi ảnh: mã, khóa đối tượng, thứ tự; ảnh đầu là chính (INV-CAT-34) | |
| `status` | Trạng thái | Có | `DRAFT`, `PUBLISHED`, `UNPUBLISHED` (INV-CAT-35) | C1b thêm cờ bị chặn riêng |
| `publishedAt` | Thời điểm | Không | Đặt ở lần đăng đầu tiên, không đổi | Biết "đã từng đăng" |
| `version` | Số | Có | Tăng mỗi lần sản phẩm hay biến thể của nó đổi (INV-CAT-36) | Mọi sự kiện mang phiên bản |
| `createdAt`, `updatedAt` | Thời điểm | Có | | |

| Hành vi | Tiền điều kiện | Hậu điều kiện | Event | Chặng |
|---|---|---|---|---|
| Tạo (`create`) | Người bán đã duyệt; danh mục dùng được; dữ liệu hợp lệ | `DRAFT` | | C1a |
| Sửa thông tin, thông số, trục (`update`) | Chủ sản phẩm; hợp lệ; khai trục chỉ khi chưa có biến thể; không rút giá trị trục đang được biến thể dùng | Phiên bản tăng | `EVT-CAT-01` nếu hiển thị | C1a |
| Gắn, bỏ, sắp xếp ảnh | Chủ sản phẩm; tối đa 9; còn ít nhất một ảnh nếu đang `PUBLISHED` | | `EVT-CAT-01` nếu hiển thị | C1a |
| Đăng (`publish`) | `DRAFT` hoặc `UNPUBLISHED`; đủ điều kiện đăng (INV-CAT-91) | `PUBLISHED` | `EVT-CAT-01`, `EVT-CAT-02` | C1a |
| Gỡ (`unpublish`) | `PUBLISHED` | `UNPUBLISHED` | `EVT-CAT-01` | C1a |
| Xóa nháp (`delete`) | `DRAFT` chưa từng đăng | Bị xóa cùng biến thể và ảnh | (không phát: nháp chưa từng hiển thị) | C1a |
| Chặn, mở chặn | Quản trị | Cờ bị chặn đổi | | C1b |

**Chính sách miền:** `ProductAttributeValidator` (INV-CAT-32, 33: giữ giá trị đã lưu, chỉ kiểm giá trị mới hoặc đổi; bắt buộc chỉ ép khi mẫu dùng được) và `PublishPolicy` (INV-CAT-91: trả **đủ** điều kiện còn thiếu một lượt, không dừng ở điều kiện đầu).
**Cổng:** kho sản phẩm (lưu; khóa dòng khi thêm, sửa biến thể hoặc sửa danh sách thuộc tính, vì thuộc tính có thể là trục; tìm theo chủ); kho danh mục, mẫu, thương hiệu (đọc); bản sao trạng thái người bán (đọc); cổng tải tệp lên dùng chung (`upload-starter`, ADR-0013): cấp chỗ tải lên cho đúng người và sản phẩm, kiểm tệp thật ở pha 1 ngoài khóa, nhận chỗ trong giao dịch ở pha 2 (ảnh được tải lên cho chính sản phẩm này); cổng sinh mã.

### AGG-CAT-05 Biến thể (`Variant`) · Đã làm (feature 07; event `EVT-CAT-02` feature 09)

**Trách nhiệm:** giữ một đơn vị bán được: tổ hợp, giá niêm yết, SKU, trạng thái.
**Bất biến giữ:** INV-CAT-40, 41, 42, 43, 44. Quy tắc xuyên aggregate ở mục 3.

| Thuộc tính | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | Mã định danh | Có | Duy nhất, không đổi | Các BC khác tham chiếu bằng mã này |
| `productId` | Mã | Có | Không đổi | |
| `combination`, `combinationKey` | Danh sách cặp (mã mẫu, mã tùy chọn); khóa chuẩn hóa không phụ thuộc thứ tự trục | Có | Không đổi (INV-CAT-41); duy nhất trong sản phẩm (INV-CAT-92); rỗng cho biến thể mặc định (INV-CAT-44) | |
| `skuCode` | Chữ | Không | 1 đến 50 ký tự (INV-CAT-43); duy nhất theo người bán (INV-CAT-96) | |
| `price` | Số nguyên | Có | Đồng; lớn hơn 0; không quá 1 tỷ (INV-CAT-40) | |
| `status` | Trạng thái | Có | `ACTIVE`, `INACTIVE` | |
| `createdAt`, `updatedAt` | Thời điểm | Có | | |

| Hành vi | Tiền điều kiện | Hậu điều kiện | Event | Chặng |
|---|---|---|---|---|
| Tạo (`create`) | Tổ hợp đúng mục 3 (INV-CAT-92, 93, 94); giá hợp lệ | Biến thể `ACTIVE` | `EVT-CAT-02`, `EVT-CAT-01` nếu hiển thị | C1a |
| Đổi giá (`changePrice`) | Giá hợp lệ | | `EVT-CAT-01` nếu hiển thị | C1a |
| Đặt SKU (`updateSkuCode`) | SKU hợp lệ, chưa dùng | | | C1a |
| Ngừng, bật lại (`deactivate`, `activate`) | Bật lại: giá hợp lệ | Trạng thái đổi | `EVT-CAT-02`, `EVT-CAT-01` nếu hiển thị | C1a |

**Chính sách miền:** `VariantCombinationPolicy` (đủ mọi trục, mỗi trục một giá trị thuộc tập sản phẩm đã chọn, giá trị và mẫu đang dùng lúc tạo).
**Cổng:** kho biến thể (lưu; tìm theo sản phẩm; tồn tại theo tổ hợp; tồn tại SKU trong người bán); kho mẫu (đọc); cổng sinh mã. Không có hành vi xóa riêng lẻ.

### Bản sao trạng thái người bán (không phải aggregate) · Đã làm

Dữ liệu hiển thị của BC (mục 3 của `analysis/0.overview.md`, `RM-CAT-06`): mã tài khoản, đã duyệt hay chưa, phiên bản đã nhận. Chỉ ghi khi phiên bản sự kiện mới hơn bản đang giữ; người bán chưa có dòng được coi là chưa duyệt.

## 3. Nghiệp vụ liên aggregate

Cột "Cơ chế": **dịch vụ miền** (thao tác trên nhiều aggregate), **ràng buộc lưu trữ** (chốt cuối khi đua nhau), **cùng giao dịch**.

| Nghiệp vụ | Tham gia | Cơ chế | Nhất quán | Bất biến | Chặng | Nhãn |
|---|---|---|---|---|---|---|
| Điều kiện đăng | Sản phẩm, Biến thể, Danh mục, Mẫu thuộc tính, bản sao người bán | `PublishPolicy` đọc qua kho, kiểm trong giao dịch đăng; độ cũ của trạng thái người bán chấp nhận vài giây | Cùng giao dịch | INV-CAT-91 | C1a | Đã làm |
| Tổ hợp biến thể duy nhất trong sản phẩm | Sản phẩm, Biến thể | Khóa dòng sản phẩm khi thêm biến thể; báo sớm qua kho; chốt cuối là ràng buộc duy nhất `(sản phẩm, khóa tổ hợp)` | Cùng giao dịch | INV-CAT-92 | C1a | Đã làm |
| Tối đa 50 biến thể | Sản phẩm, Biến thể | Đếm trong cùng giao dịch, dưới khóa dòng sản phẩm | Cùng giao dịch | INV-CAT-93 | C1a | Đã làm |
| Giá trị trục thuộc tập sản phẩm đã chọn | Sản phẩm, Biến thể | `VariantCombinationPolicy` đọc sản phẩm dưới khóa dòng | Cùng giao dịch | INV-CAT-94 | C1a | Đã làm |
| Mọi thay đổi biến thể tăng phiên bản sản phẩm | Sản phẩm, Biến thể | Cùng giao dịch, dưới khóa dòng sản phẩm; bản chụp và sự kiện mang phiên bản này | Cùng giao dịch | INV-CAT-36 | C1a | Đã làm |
| SKU duy nhất theo người bán | Biến thể, Sản phẩm (chủ) | Báo sớm qua kho; chốt cuối là ràng buộc duy nhất `(người bán, SKU)` khi có SKU; cần lưu mã người bán ở dòng biến thể | Cùng giao dịch | INV-CAT-96 | C1a | Đã làm |
| Hiển thị = Đã đăng, không bị chặn, người bán còn được duyệt | Sản phẩm, bản sao người bán | Hàm thuần dùng chung duy nhất `isPubliclyVisible`; mọi nơi đọc sản phẩm cho người mua gọi hàm này | Cuối cùng với trạng thái người bán | INV-CAT-95 | C1a (chặn: C1b) | Đã làm (chưa có điều kiện chặn) |
| Sản phẩm mới chỉ vào danh mục dùng được | Sản phẩm, Danh mục | Tạo và đăng lại kiểm danh mục cấp ba, đang dùng, mọi tổ tiên đang dùng | Cùng giao dịch | INV-CAT-97 | C1a | Đã làm (tạo); đăng lại: feature 09 |
| Danh mục có sản phẩm tham chiếu không xóa | Danh mục, Sản phẩm | Kiểm trước khi xóa; không có khóa ngoại giữa hai aggregate (giữ ranh giới aggregate rõ). Đua giữa xóa danh mục và tạo sản phẩm vào nó là rủi ro chấp nhận được: danh mục chỉ xóa được khi chưa từng dùng, nên thao tác này hiếm; tạo sản phẩm kiểm danh mục tồn tại trong giao dịch của nó | Cùng giao dịch | INV-CAT-98 | C1a | Đã làm |
| Tên danh mục duy nhất trong cùng cha | Danh mục (tập hợp) | Báo sớm qua kho; chốt cuối là ràng buộc duy nhất trên `(cha, khóa tên)` có xử lý cha rỗng ở cấp một | Cùng giao dịch | INV-CAT-02 | C1a | Đã làm |
| Hành động quản trị đi cùng sự kiện kiểm toán | Danh mục, Mẫu, Thương hiệu, hộp thư event | Ghi `EVT-CAT-04` vào hộp thư cùng giao dịch với thay đổi | Cùng giao dịch | (stage R12) | C1a | Đã làm |

## 4. Use case

| Use case | Năng lực | Chặng | Nhãn |
|---|---|---|---|
| Quản danh mục: tạo, đổi tên, sắp xếp, ngừng, bật, xóa; thay danh sách gán thuộc tính | CAP-CAT-01 | C1a | Đã làm |
| Quản mẫu thuộc tính: tạo, sửa, ngừng, bật; thêm, sửa, ngừng, bật, sắp xếp tùy chọn | CAP-CAT-02 | C1a | Đã làm |
| Quản thương hiệu: tạo, đổi tên, ngừng, bật | CAP-CAT-03 | C1a | Đã làm |
| Tạo sản phẩm; sửa sản phẩm (kèm thông số và trục) | CAP-CAT-04 | C1a | Đã làm |
| Thêm biến thể; đổi giá, SKU; ngừng, bật biến thể | CAP-CAT-05 | C1a | Đã làm |
| Gắn ảnh (xin chỗ tải lên, xác nhận), bỏ ảnh, sắp xếp ảnh | CAP-CAT-06 | C1a | Đã làm |
| Đăng; gỡ; đăng lại; xóa nháp | CAP-CAT-07 | C1a | Đã làm |
| Nhận kết quả duyệt người bán, cập nhật bản sao trạng thái | CAP-CAT-08 | C1a | Đã làm |
| Cây danh mục (`RM-CAT-01`); thuộc tính được gán cho một lá (`RM-CAT-02`); danh sách thương hiệu (`RM-CAT-03`) | | C1a | Đã làm |
| Sản phẩm của tôi: danh sách phân trang lọc theo trạng thái, và chi tiết kèm điều kiện còn thiếu để đăng (`RM-CAT-04`) | | C1a | Đã làm |
| Xem sản phẩm hiển thị cho người mua (`RM-CAT-05`) | | C1b | Dự kiến |
| Chặn, mở sản phẩm | | C1b | Dự kiến |

**Quyền (mặc định từ chối; vai kiểm trong use case, không bằng chú thích ở lớp trình bày, cùng cách với `seller-service`; mọi đường cần đăng nhập, chưa có đường công khai):** quản danh mục, mẫu, thương hiệu: vai `ADMIN` (`CatalogActor.requireAdmin()`, 403 `CATALOG_ROLE_NOT_ALLOWED`); tạo, sửa, đăng sản phẩm và biến thể, ảnh: vai `USER` kèm người bán đã duyệt (kiểm qua bản sao trạng thái); sửa chỉ khi là chủ sản phẩm, kiểm trong miền, và sản phẩm của người khác trả như không tồn tại; đọc cây, thuộc tính, thương hiệu: đã đăng nhập. Chính sách thuộc tính (ABAC) hoãn tới cổng Vận hành.

## 5. Event

### Phát

| Event | Khi nào | Nội dung chính | Bên nhận | Chặng | Nhãn |
|---|---|---|---|---|---|
| `EVT-CAT-01` Bản chụp sản phẩm | Sản phẩm hay biến thể của nó đổi khi sản phẩm đang hiển thị, hoặc cờ hiển thị đổi (kể cả khi chuyển sang không hiển thị) | Trạng thái đầy đủ: mã sản phẩm, mã người bán, tiêu đề, mô tả, danh mục, thương hiệu, thuộc tính (kèm kiểu nhập, trục), ảnh, biến thể (mã, tổ hợp, giá, hoạt động), cờ hiển thị, phiên bản | Tìm kiếm | C1a phát, C1b nhận | Đã làm (phát; bên nhận C1b) |
| `EVT-CAT-02` Đơn vị bán được thay đổi | Biến thể của sản phẩm đang hiển thị được tạo, ngừng, bật lại; sản phẩm được đăng, gỡ, đăng lại (phát cho mọi biến thể). Nháp chưa từng đăng không phát gì, kể cả khi bị xóa | Trạng thái tuyệt đối của **một** biến thể: mã biến thể, mã sản phẩm, mã người bán, hoạt động, hiển thị, phiên bản của sản phẩm (bên nhận so **theo từng đơn vị**, ghi đè khi lớn hơn hẳn). **Không mang giá** | Tồn kho | C1a phát, C2a nhận | Đã làm (phát; bên nhận C2a) |
| `EVT-CAT-03` Danh mục thay đổi | Cây hay gán thuộc tính đổi (kể cả xóa danh mục), mẫu hay tùy chọn đổi. Thương hiệu không phát | Mã danh mục hoặc mã mẫu, phiên bản sau thay đổi, loại thay đổi | Tìm kiếm | C1a phát, C1b nhận | Đã làm (phát; bên nhận C1b) |
| `EVT-CAT-04` Hành động quản trị đã thực hiện (`AuditActionRecorded`) | Quản trị tạo, sửa, ngừng, xóa danh mục; tạo, sửa, ngừng mẫu; tạo, sửa, ngừng thương hiệu | Hợp đồng bản ghi của Nhật ký kiểm toán; hành động `CATEGORY_CREATED/UPDATED/DEACTIVATED/DELETED`, `ATTRIBUTE_*`, `BRAND_*` (đổi tên, sắp xếp, gán thuộc tính, bật lại đều là `_UPDATED`), đối tượng, phiên bản | Nhật ký kiểm toán | C1a | Đã làm (phát) |

Ghi vào hộp thư event cùng giao dịch với thay đổi (mục 3), chuyển ra bus sau (ít nhất một lần). Khóa phân vùng: mã sản phẩm cho `EVT-CAT-01` và `EVT-CAT-02` (thứ tự đúng theo sản phẩm); mã danh mục, mã mẫu cho `EVT-CAT-03`. Bên nhận chỉ ghi đè nếu phiên bản mới hơn. Định dạng JSON (`tech-stack.md`).

### Nhận

| Event | Từ BC | Hiện thực | Chặng | Nhãn |
|---|---|---|---|---|
| `SellerProfileApproved`, `SellerProfileRejected` (`EVT-SEL-01`, `EVT-SEL-02`) | Người bán | Cập nhật bản sao trạng thái người bán; khử trùng theo mã event; bỏ sự kiện có phiên bản không mới hơn | C1a | Đã làm |

Sai thứ tự: guard bằng phiên bản (C1b thêm sự kiện bị khóa). Lỗi nhận: thử lại có backoff, hết số lần thì vào hàng chờ lỗi và cảnh báo.

## 6. Giao diện giữa BC

| Giao diện | Hướng | Nội dung | Chặng | Nhãn |
|---|---|---|---|---|
| Hợp đồng danh tính | Xác thực công bố, Danh mục đọc từ token | Mã tài khoản (`sub`) làm chủ sản phẩm, vai (`role`) để kiểm quyền (ADR-0003) | C1a | Dự kiến |
| Kết quả duyệt người bán | Người bán phát, Danh mục nhận | Mục 5; không gọi đồng bộ | C1a | Đã làm |
| Hành động quản trị | Danh mục phát, Nhật ký kiểm toán nhận | `EVT-CAT-04` | C1a | Đã làm (phát) |
| Bản chụp sản phẩm | Danh mục phát, Tìm kiếm nhận | `EVT-CAT-01`, `EVT-CAT-03` | C1b | Dự kiến |
| Đơn vị bán được | Danh mục phát, Tồn kho nhận | `EVT-CAT-02` | C2a | Dự kiến |
| Giá niêm yết | Giỏ hàng, Định phí đọc từ Danh mục | Theo mã biến thể; cách đọc (đồng bộ hay qua bản sao) chốt ở C2a theo các cặp gọi đồng bộ đã duyệt | C2a | Dự kiến |
| API cho ứng dụng Người bán và Vận hành | Ứng dụng gọi qua BFF và cổng truy cập | Mục 4 | C1a | Dự kiến |

## 7. Khái niệm kỹ thuật

| Khái niệm | Kết luận | ADR |
|---|---|---|
| Mã định danh thực thể | **UUID v7** sinh trong miền qua cổng sinh mã, lưu kiểu `uuid`, cho mọi thực thể (kể cả mẫu, tùy chọn, ảnh). **Đã chốt `2026-10-26` (D3).** Đổi kiểu cột đi cùng gộp migration thành bản gốc sạch (D8), **Đã làm** ở feature 05 | ADR-0001 |
| Sự kiện trạng thái đầy đủ | Hai loại: bản chụp cả sản phẩm (cho Tìm kiếm) và trạng thái tuyệt đối từng đơn vị bán được (cho Tồn kho); bên nhận ghi đè theo phiên bản. Không dùng sự kiện nhỏ theo chuyển đổi (đã đăng, đã gỡ, đổi giá, bị chặn...): bên nhận chỉ cần cờ tuyệt đối, và nhận lặp hay sai thứ tự đều an toàn. **Đã chốt `2026-11-06` (D6).** Quy tắc phát và so `version` ở ADR-0014. | ADR-0014 |
| Phiên bản sản phẩm | Cột phiên bản trên dòng sản phẩm; mọi thay đổi sản phẩm hoặc biến thể tăng nó dưới khóa dòng sản phẩm; bản chụp và sự kiện mang phiên bản này | Chờ |
| Khóa dòng sản phẩm | Thêm, sửa biến thể và sửa danh sách thuộc tính (có thể đổi trục) khóa dòng sản phẩm trong giao dịch để bảo đảm tổ hợp duy nhất, tối đa 50, giá trị trục không bị rút khi biến thể đang dùng và phiên bản tuần tự; sửa tiêu đề, mô tả, thương hiệu dùng khóa lạc quan | Chờ |
| Tính duy nhất khi đua nhau | Ràng buộc duy nhất ở lưu trữ làm chốt cuối: `(sản phẩm, khóa tổ hợp)`, `(người bán, SKU)`, `(cha, khóa tên danh mục)` (có xử lý cấp một), tên mẫu, khóa tên thương hiệu, `(mẫu, giá trị tùy chọn)` | Chờ |
| Chuẩn hóa tên | Hàm thuần của miền: bỏ khoảng trắng thừa, chữ thường, bỏ dấu tiếng Việt; dùng cho danh mục, thương hiệu (tên mẫu và giá trị tùy chọn không chuẩn hóa mà bắt buộc đúng dạng, từ chối nếu sai) | Chờ |
| Ảnh sản phẩm | Người bán xin chỗ tải lên (đường dẫn ký sẵn của kho đối tượng, hạn ngắn, giới hạn tần suất), tải thẳng lên kho đối tượng, rồi gọi xác nhận; Danh mục kiểm đối tượng tồn tại, loại và dung lượng cho phép, và khóa đối tượng chứa mã sản phẩm của chính nó. Quản lý tệp tách riêng khi có loại tệp thứ hai | Chờ |
| Cây danh mục | Cách lưu để hỏi tổ tiên, con nhanh: `data.md`. Bộ nhớ đệm cho đọc nhiều thuộc C1b và C5, chưa làm ở C1a | Chờ |
| Mô tả chữ thuần | Lưu nguyên văn, không cho định dạng; thoát ký tự khi hiển thị ở giao diện | Quy ước chung |
| Phân quyền | Chú thích vai ở lớp trình bày, kiểm chủ và người bán đã duyệt trong miền; ABAC hoãn tới cổng Vận hành | Quyết định `2026-10-22` |
| Định dạng event | JSON; nâng Avro kèm Schema Registry là hướng sau | `tech-stack.md` |

## 8. Hạ tầng cần ở C1a

Cơ sở dữ liệu quan hệ riêng của dịch vụ; bus sự kiện kèm hộp thư event và connector chuyển ra bus; kho đối tượng cho ảnh; bộ nhận sự kiện kèm hàng chờ lỗi (cho kết quả duyệt người bán); cổng truy cập và route; quan sát theo quy ước chung. Chưa cần Redis ở C1a (bộ nhớ đệm là C1b, C5). Dữ liệu khởi tạo (danh mục, mẫu thuộc tính, thương hiệu mẫu để demo, `Q-CAT-02`): **Đã làm** (feature 06) bằng script SQL chạy tay `infra/seed/catalog.sql`, ngoài Flyway, không phát event; cách chạy ở `infra/seed/README.md`.

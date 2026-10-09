# Feature 01: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế;
**Agent** viết phần thường, người rà), điều cần trao đổi trong bước đó (nếu có), và cách biết bước đã xong. Chọn cách
thực hiện khi bắt đầu từng bước.

**Nguyên tắc thứ tự:** mô hình miền được ưu tiên; hạ tầng (cơ sở dữ liệu, băm, API) là chi tiết cài đặt đi sau
và được định hình theo miền, không ngược lại. Thứ tự: tham số → miền (kèm cổng) → use case trên cổng giả → cài đặt hạ tầng
sau từng cổng → API → các tính chất bổ sung → đo → đóng. Quyết định kỹ thuật (ADR) đặt ngay trước bước cần dùng nó.

**Nguyên tắc phạm vi:** mỗi bước chỉ làm và kiểm **đúng phạm vi của bước đó**; phần chưa thuộc bước này chưa nằm trong luồng và
test của bước, và test không khẳng định điều gì về chúng. Mỗi bước nêu rõ phạm vi khi cần.

Các bước chỉ cần cơ sở dữ liệu. Cổng truy cập (feature 06) chỉ cần khi chạy qua `/auth` từ đầu đến cuối; giới hạn tần suất của đăng ký do cổng thực hiện, không nằm trong feature này.

## Điều kiện bắt đầu

Những điều thuộc **nghiệp vụ** đã được chốt ở `analysis.md` và `service.md` trước khi vào kế hoạch này, không bàn lại ở đây:
- **Luật mật khẩu** (độ dài, thành phần): `analysis.md`, thuật ngữ "Mật khẩu hợp lệ".
- **Mã vai** (`USER`, `ADMIN`) và tập vai: `service.md`.

Nếu một trong hai chưa chốt thì dừng, chốt xong mới làm bước 1.

## Bước 1. Chốt các tham số kỹ thuật và NFR · Người

**Trạng thái:** Xong · `2026-10-08`. Giá trị đã chốt ghi ở `tech.md` T6 và `nfr.md`.

Đã trao đổi, kết quả ghi vào `tech.md`, `nfr.md`.

Kết quả chốt:
- **Giới hạn tần suất:** 10 yêu cầu mỗi giờ cho mỗi địa chỉ mạng, vượt thì trả 429 kèm thời gian chờ; do cổng truy cập thực hiện (`tech.md` T6, feature 06).
- **Mục tiêu tải và độ trễ:** 1 yêu cầu/giây duy trì, trần thử tăng dần tới điểm gãy; P99 < 500 ms; tối thiểu 2 lõi cho việc
  băm (`nfr.md`).
- **Điều kiện đo:** máy, hệ số băm, phiên bản PostgreSQL, bảng rỗng và 3 triệu dòng, tắt giới hạn tần suất khi đo tải,
  khởi động nóng, lặp 3 lần (`nfr.md`).

Xong khi: các giá trị trên có số cụ thể trong tài liệu và không còn chữ "chưa chốt". ==> **Đã đạt.**

## Bước 2. Rà kịch bản test · Người

**Trạng thái:** Xong · `2026-10-08`. Kết quả: 13 kịch bản test (KT-01 đến KT-13) ở `flow.md` mục 6, mỗi kịch bản có mức và
trỏ về kịch bản chặng, bất biến, luật hoặc quyết định.

Đối chiếu kịch bản test ở dạng chữ với S2, S4 và các bất biến INV-ATH-01, 02, 04, 06, 07, 09; thiếu hay thừa thì sửa ở
`flow.md`. Xong khi: danh sách được chốt, mỗi kịch bản trỏ về một nguồn.

## Bước 3. Mô hình hóa miền · Người

**Trạng thái:** Xong · `2026-10-08`. Kết quả: miền ở `services/oauth2-service/…/domain/user_account` (`UserAccount`, `UserAccountId`, `Email`,
`RawPassword`, `PasswordHash`, `Role`, `UserAccountService`, `UserAccountRegistered`, các cổng) và cổng sinh mã ở `common-domain`;
`service.md` mục AGG-ATH-01 và mục 3 đã khớp. Dựng thẳng cả hành vi, không có pha khung rỗng riêng.

Thiết kế mô hình miền của feature rồi **dựng thành khung mã** (kiểu, chữ ký, chưa có hành vi). Mô hình miền được diễn đạt
bằng mã nên không có file `model.md` riêng. Đây là bước định hình miền; test ở bước sau viết trên giao diện công khai của
khung này.

Sản phẩm:
- **Khung mã của miền** biên dịch được: aggregate, value object, event, lỗi nghiệp vụ, các cổng; các hàm chưa cài (ném lỗi
  "chưa cài đặt"). Quyết định mức lớp (vì sao là value object, vì sao chính sách đưa vào qua cổng) ghi thành bình luận ở nơi
  quyết định.
- **`service.md`** cập nhật ở mức aggregate: thông tin giữ kèm ràng buộc và kiểu mức khái niệm, hành vi theo tên kèm một dòng
  tiền và hậu điều kiện, event và nội dung, cổng. Không chép cấu trúc lớp, chữ ký hàm hay lớp lỗi.
- **Tùy chọn:** mục "Mô hình miền" trong `flow.md` với sơ đồ lớp Mermaid, viết một lần ở bước này, nếu thấy giúp người rà.

Nội dung cần trao đổi:
- **Ranh giới aggregate:** Tài khoản đăng nhập giữ những gì; email và vai là value object hay trường thường.
- **Bất biến nằm ở đâu:** chuẩn hóa email và kiểm định dạng trong `Email`; luật mật khẩu trong `RawPassword`; vai cố định lúc
  tạo trong hàm tạo tài khoản; kiểm miền nội bộ cần chính sách do cấu hình đưa vào (INV-ATH-07) nên là chính sách khai
  báo ở miền, không đọc cấu hình trực tiếp.
- **Hàm tạo:** hàm tĩnh `UserAccount.register(id, email, passwordHash, now)`; mã, giá trị băm và thời điểm do use case tính
  trước rồi truyền vào (`tech.md` T7), aggregate không giữ cổng nào. Event `UserAccountRegistered` được thu thập thế nào.
  Đường tạo thứ hai (`createInternal`, feature 04) chưa làm, thêm khi đến.
- **Dịch vụ miền `UserAccountService`:** `assertRegistrable(email)` kiểm email chưa dùng (qua kho) và không thuộc miền nội
  bộ (qua chính sách); use case gọi trước khi tạo.
- **Truyền cổng qua tham số:** chỉ `verifyPassword(raw, hasher)`, vì cần `passwordHash` của aggregate.
- **Cổng của miền:** kho tài khoản (lưu, kiểm email đã dùng), bộ băm mật khẩu, cổng sinh mã (ADR-0001, đặt ở
  `common-domain`), đồng hồ.
- **Lỗi nghiệp vụ:** `EmailInvalid`, `EmailReserved`, `PasswordInvalid`, `EmailTaken`; ai ném, ai bắt.
- **Chỗ mất tính thuần túy:** ràng buộc duy nhất trên email thật sự do cơ sở dữ liệu giữ, nên miền chỉ khai báo qua cổng
  kho và chấp nhận lỗi trùng trả về.

Xong khi: khung mã được rà và biên dịch được, `service.md` khớp khung mã ở mức aggregate, miền không phụ thuộc thư viện,
cơ sở dữ liệu hay khung ứng dụng.

## Bước 4. Viết test cho miền (từ bất biến, trên khung) · Agent, Người rà

**Trạng thái:** Xong · `2026-10-08`. Kết quả: `EmailTest`, `RawPasswordTest`, `UserAccountTest` ở thư mục test của
`oauth2-service`. Viết sau khi cài hành vi nên không có pha chạy đỏ.

Từ bất biến và kịch bản test đã chốt ở bước 2, viết test **bằng mã** trên giao diện công khai của khung ở bước 3: chuẩn hóa
email, vai, mật khẩu hợp lệ, `UserAccountId`, hành vi đăng ký của aggregate (vai luôn Người dùng, trạng thái hoạt động, ghi nhận
event `UserAccountRegistered` mang đúng mã được truyền vào), `UserAccountService.assertRegistrable` trên kho giả, và
`verifyPassword` với bộ băm giả. Test miền không cần bộ sinh mã vì mã là đối số.

Test không viết từ phần thân hàm đã cài, vì khung chưa có hành vi.

Xong khi: test biên dịch được và chạy **đỏ** vì hành vi chưa cài.

## Bước 5. Cài hành vi của miền · Người

**Trạng thái:** Xong · `2026-10-08`. Làm cùng bước 3; test bước 4 xanh.

Cài thân hàm của value object, aggregate, chính sách miền nội bộ cho đến khi test ở bước 4 xanh.

Xong khi: test ở bước 4 **xanh**; miền vẫn không phụ thuộc thư viện, cơ sở dữ liệu hay khung ứng dụng.

## Bước 6. Use case đăng ký trên cổng giả · Agent, Người rà

**Trạng thái:** Xong · `2026-10-08`. Kết quả: `RegisterUser` và `RegisterUserTest` (kho, bộ băm, bộ sinh mã giả).

Use case ghép miền: chuẩn hóa, kiểm hợp lệ, gọi `UserAccountService.assertRegistrable`, sinh mã và băm qua cổng, tạo tài
khoản, thu event, bắt lỗi trùng thành lỗi nghiệp vụ. Chạy trên cài đặt giả của các cổng (kho trong bộ nhớ, bộ băm giả, bộ
sinh mã và đồng hồ cố định), chưa cần cơ sở dữ liệu.

Phạm vi bước này: giới hạn tần suất do cổng thực hiện nên không nằm trong use case. Đăng ký ở C0 không ghi event ra
đâu cả (`tech.md` T5).

Test: đăng ký hợp lệ, email khác chữ hoa, khoảng trắng đầu cuối, email miền nội bộ, mật khẩu không hợp lệ, lỗi giữa chừng thì
không có tài khoản và aggregate không thu event.

Xong khi: test use case xanh, chạy nhanh vì không chạm cơ sở dữ liệu.

## Bước 7. Cài bộ băm mật khẩu · Người rồi Agent

**Trạng thái:** Xong · `2026-10-08`. Kết quả: `../../../global/2.architecture/adr/0002-password-hashing-bcrypt.md` (bcrypt hệ số 10;
luật mật khẩu tối đa 64 ký tự ASCII nằm dưới giới hạn 72 byte); `PasswordHasherAdapter` và test của nó.

Trước hết viết **ADR-0002** (T1 trong `tech.md`): chốt thuật toán, hệ số khởi đầu, định dạng tự mô tả, việc băm lại khi
đăng nhập, và cách xử lý giới hạn 72 byte của bcrypt (luật mật khẩu chỉ nhận ASCII và tối đa 64 ký tự nên luôn dưới giới hạn).

Sau khi ADR Accepted, Agent cài bộ băm thật cho cổng của miền. Xong khi: test bộ băm xanh (băm rồi kiểm lại đúng; sai thì từ
chối; mật khẩu dài nhất hợp lệ không bị cắt) => **Đã đạt.**

## Bước 8. Lưu trữ: lược đồ và ánh xạ · Agent, Người rà

**Trạng thái:** Xong · `2026-10-08`. Kết quả: migration `V12__user_accounts_register_design.sql` (bỏ và tạo lại `user_accounts`),
`UserAccountRepositoryAdapter`, `UserAccountRepositoryAdapterTest` (KT-11, KT-12).

Cài lưu trữ **theo miền đã định hình**: migration cho `user_accounts` (khóa `uuid`, duy nhất trên email, ràng buộc vai và
trạng thái); ánh xạ aggregate sang bảng và cài đặt kho tài khoản, dịch vi phạm duy nhất thành lỗi trùng. Nếu ánh xạ khó thì
sửa ánh xạ hoặc bảng, không sửa miền.

Test tích hợp với cơ sở dữ liệu thật, chỉ những ca không thay được bằng test miền: lưu rồi đọc lại đúng, cột mật khẩu chỉ chứa
chuỗi băm (KT-11); vi phạm duy nhất của email thành lỗi trùng (KT-12). Mỗi test chạy trong một giao dịch rồi hoàn tác nên không
để lại dữ liệu. Ràng buộc kiểu `CHECK` của vai và trạng thái không cần test riêng (miền đã chặn trước).

Xong khi: test tích hợp xanh, và các test use case của bước 6 chạy lại trên kho thật vẫn xanh.

## Bước 9. API · Agent, Người rà

**Trạng thái:** Xong một phần · `2026-10-08`. Kết quả: `UserAccountController`, trường `code` ở khung phản hồi chung (`common-web`),
`UserAccountApiTest` (5 test gọi thật: 201, KT-02, 409, các lỗi 400). Lỗi 429 do cổng trả, kiểm ở feature 06.

Điểm cuối `POST /register`, kiểm đầu vào, miền nội bộ (T8), ánh xạ lỗi sang mã (T9), bỏ qua trường vai. Test hợp đồng đối
chiếu với `api.yaml`.

Xong khi: test 400 cho từng mã lỗi và test gửi kèm vai quản trị xanh => **Đã đạt** cho phần 201, 400, 409.

## Bước 10. Đồng thời · Người

**Trạng thái:** Xong · `2026-10-08`. Kết quả: `UserAccountConcurrencyTest`; chạy 100 vòng × 100 yêu cầu song song cùng email trên
Postgres test riêng: mỗi vòng đúng một 201, 99 lần 409, không có 500, DB một tài khoản (hơn 40 giây tổng cộng).

Test 100 yêu cầu song song cùng email chỉ ra đúng một tài khoản; lặp lại nhiều lần để bắt lỗi hiếm.

Xong khi: chạy lặp 100 lần vẫn đúng => **Đã đạt.** Nếu sau này fail thì sửa ở bước 6 hoặc 8.

## Bước 11. Đo NFR · Người

**Trạng thái:** Hoãn · `2026-10-09`: chờ bộ đo nền (feature 08), ghi ở `deferred.md`. Đã đo phần tính đúng khi đồng thời (bước 10).

Theo `nfr.md`: tải duy trì 1 yêu cầu/giây trong 5 phút rồi tăng dần tới điểm gãy, sinh 3 triệu tài khoản (băm tính trước) để
đo tra trùng và kích thước chỉ mục. Phụ thuộc feature 08 để biết trần băm của máy.

Xong khi: nhãn ở `nfr.md` đổi từ Chỉ thiết kế sang Đã đo kèm điều kiện đo.

## Bước 12. Chạy thật · Người

**Trạng thái:** Xong một phần · `2026-10-09`: đã chạy bằng curl 8 ca vào `oauth2-service` (cổng 8004) và kiểm DB, khớp mong đợi (JSON hỏng trả 500, giao cho feature 07). Chưa kiểm log (`traceId`, không lộ mật khẩu/email) và chưa chạy qua cổng (chờ feature 06).

Build và khởi động ứng dụng, gọi API thật bằng `run.http` (cạnh file này), xem log. Kiểm: mỗi lời gọi trả đúng mã và `code`;
dòng log `[RegisterUser] registered` có `traceId`; tài khoản vừa tạo có vai `USER`, mật khẩu trong cột là chuỗi băm; mật khẩu và email
thô không xuất hiện trong log. Khi feature 06 xong, chạy lại qua cổng `/auth/register`.

Xong khi: cả bảy lời gọi trong `run.http` ra đúng kết quả đã ghi chú, và log không có lỗi bất thường.

## Bước 13. Đóng feature · Agent, Người rà

**Trạng thái:** Xong tài liệu · `2026-10-09`; mở lại sau bước 12 (Chạy thật) để đối chiếu với kết quả chạy. `service.md`, `data.md`, `api.yaml` đã khớp mã; điều học được ở `../00-features.md`; tài liệu đã chuyển về `docs/service/oauth2-service/` và `docs/stage/c0-foundation/`.

Cập nhật `service.md`, `data.md`, `api.yaml` cho khớp mã và đổi nhãn Dự kiến thành Đã làm; ghi điều học được vào
`../00-features.md`.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã.

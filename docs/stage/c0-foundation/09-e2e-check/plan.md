# Feature 09: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** quyết định hoặc rà điểm cần insight; **Agent** viết phần thường,
người rà), và cách biết bước đã xong. Phạm vi, quy tắc và đường lỗi ở `flow.md`.

**Nguyên tắc thứ tự:** biết hiện trạng và dựng chuỗi chạy được bằng một lệnh trước, rồi thêm phần mới (S3, truy vết, 429) từng cái một và phá thử, sau cùng
chạy cả bộ từ trạng thái sạch và ghi bảng truy vết.

**Ngoài feature này:** xem `flow.md` mục 1.

## Điều kiện bắt đầu

Feature 01 đến 08 xong (đã). Docker chạy, JDK 21 tại `C:\Program Files\Java\jdk-21`, Node. Test chạm cơ sở dữ liệu dev: báo trước khi chạy.

## Bước 1. Khảo sát hiện trạng và ghi số đầu · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. Kết quả ở `tech.md` T1: bộ thật có 11 test và không có S3; luật đăng ký 10/giờ mâu thuẫn với bộ thật nên kiểm 429 phải chạy ở pha riêng với cổng luật mặc định; file log phụ thuộc thư mục chạy; jar của `oauth2-service` và BFF cũ hơn mã. Chưa chạy lại bộ thật (làm ở bước 2 và 6).

Kiểm bộ thật của feature 05 (`frontends/e2e-real`) còn chạy đúng không; liệt kê khóa Redis và bảng cần đếm; tìm tên khóa giới hạn tần suất của cổng và của đăng ký
(để dọn đúng chỗ); tìm nơi file log JSON của `oauth2-service`. Ghi số dòng các bảng của `oauth2_db` và khóa Redis lúc bắt đầu.

Xong khi: `tech.md` có danh sách bảng, khóa Redis, đường dẫn log và số đầu; mỗi điều có kết luận đúng hoặc sai kèm cách xử lý.

## Bước 2. Script dựng chuỗi · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. `scenarios/up.sh` và `down.sh` ở `tech.md` T2; thử hai vòng, cả khi gọi qua ống dẫn, và thử khởi động lại riêng cổng với luật mặc định. Dữ liệu dev sạch sau thử.

`scenarios/up.sh` và `scenarios/down.sh`: dựng hạ tầng (nếu chưa lên), `oauth2-service` (profile `dev`), `storefront-web-bff`, `api-gateway` (`SHOP_FRONTEND_URL=http://localhost:4201`),
giao diện (`npm start` ở `frontends/`, cổng 4201); chờ cả chuỗi lên bằng kiểm sức khỏe; dừng và báo nếu có dịch vụ không lên. `down.sh` dừng đúng các tiến trình đã dựng (theo cổng hoặc mã tiến trình đã ghi, không dừng mọi `java`).

Xong khi: `up.sh` rồi `down.sh` chạy được hai lần liên tiếp; sau `down.sh` không còn dịch vụ nào của chuỗi chạy.

## Bước 3. S3 qua cổng · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. KT-21 (nhìn thấy) và KT-22 (thời gian, xen kẽ, API) ở `s3.real.spec.ts`; phá thử bỏ băm giả làm KT-22 đỏ (lệch 98 ms), đổi mã lỗi làm cả hai đỏ; 13 test của bộ thật xanh. Chi tiết ở `tech.md` T3.

Thêm test vào `frontends/e2e-real`: (a) đăng ký tài khoản thật rồi đăng nhập sai mật khẩu; (b) đăng nhập bằng email không tồn tại. So cùng địa chỉ đích `/login?error`, cùng thông báo, không email trên địa chỉ;
đo thời gian hai lần và kiểm trung vị của 20 lần mỗi nhánh, xen kẽ, lệch dưới 30 ms (ngưỡng hiệu chỉnh ở `tech.md` T3). Phá thử: cho nhánh email không tồn tại chuyển về địa chỉ khác (hoặc bỏ băm giả) rồi xác nhận test đỏ, hoàn tác rồi xanh.

Xong khi: test xanh khi mã đúng, đỏ khi bị phá, mỗi lần phá một chỗ.

## Bước 4. Truy vết lỗi qua cổng · Agent, Người rà

**Trạng thái:** Xong · `2026-10-21`, kèm một lỗi tìm ra và đã sửa ngay (feature 07, mật khẩu vào log khi nội dung là chuỗi JSON). KT-23 xanh và đã phá thử (Xác thực chỉ nhận B3 thì đỏ); KT-24 ghi lỗi mật khẩu vào log khi nội dung là chuỗi JSON hợp lệ, đỏ trước sửa và xanh sau sửa. Chi tiết ở `tech.md` T4.

Gửi một yêu cầu lỗi đã biết qua cổng (nội dung đăng ký hỏng), lấy `traceId` trong phản hồi, tìm dòng log có `trace.id` đó ở file log JSON của `oauth2-service` (chờ có giới hạn vì log bất đồng bộ), kiểm có mã lỗi
và không có mật khẩu. Phá thử: lấy `traceId` sai hoặc xóa tên trường ánh xạ rồi xác nhận test đỏ.

Xong khi: test xanh, đỏ khi bị phá; phần "chạy tay `run.http` để nhìn log" còn nợ của feature 07 và 01 được đóng hoặc ghi lại là không cần.

## Bước 5. 429 của đăng ký · Agent, Người rà

**Trạng thái:** Xong · `2026-10-21`. KT-25 ở `ratelimit.real.spec.ts`, chạy ở pha B (cổng luật mặc định, `E2E_GATEWAY_RULES=default`); đã phá thử (cổng luật nâng thì đỏ). Chi tiết ở `tech.md` T5.

Gửi chuỗi `POST /register` vượt mức qua cổng (host `auth`); kiểm 429, `Retry-After`, thân lỗi chuẩn; xóa đúng khóa giới hạn trong Redis sau đó. Test này chạy ở **pha B**: cổng khởi động lại với luật mặc định 10/giờ (pha A dùng luật nâng của bộ thật, `tech.md` T1.3).
Phá thử: nới luật giới hạn rồi xác nhận test đỏ.

Xong khi: test xanh, đỏ khi bị phá; sau test khóa giới hạn đã được dọn và lần chạy sau không bị chặn.

## Bước 6. Chạy cả chuỗi từ trạng thái sạch · Agent, Người

**Trạng thái:** Xong · `2026-10-21`. `scenarios/run.sh` chạy hai lần liên tiếp đều đạt (pha A 15 xanh, pha B 1 xanh, số dòng cuối bằng số đầu, `ducthinh@dev.aplus.vn` còn nguyên). Chi tiết ở `tech.md` T6.

`scenarios/run.sh`: ghi số đầu, `up.sh`, chạy S1, S2, S4, S5 (bộ có sẵn), S3, truy vết, rồi đổi cổng sang luật mặc định (pha B) và chạy 429, dọn dữ liệu thử và mức giới hạn, so số dòng với lúc đầu, `down.sh`. Chạy hai lần liên tiếp, lần hai bắt đầu từ trạng thái sạch.
**Báo trước vì chạm cơ sở dữ liệu dev.**

Xong khi: hai lần liên tiếp đều xanh toàn bộ và số dòng cuối bằng số dòng đầu; `ducthinh@dev.aplus.vn` còn nguyên.

## Bước 7. Bảng truy vết và đóng chặng · Agent, Người rà

**Trạng thái:** Xong · `2026-10-21`. Bảng truy vết ở `tech.md` T7; `deferred.md` (hai phần hoãn); `00-features.md` (hàng 09 và Điều học được); dữ liệu dev sạch.

Lập bảng S1 đến S5, INV-ATH-01 đến 11 và R1 đến R7, mỗi dòng trỏ tới test và feature chứng minh nó; chỗ nào chưa có thì ghi rõ (không bịa). Cập nhật `00-features.md` (hàng 09, phần Điều học được, tổng kết chặng nếu người muốn),
`flow.md` (trạng thái), `deferred.md` (chỉ phần có chỗ trong phạm vi), `tech.md`; dọn dữ liệu thử còn sót.

Xong khi: không còn kịch bản nào chưa có test chứng minh, hoặc chỗ nào chưa có đã được ghi thành phần hoãn có lý do.

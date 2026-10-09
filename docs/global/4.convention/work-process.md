# Quy trình triển khai theo chặng

Quy trình đi từ roadmap tới code và tài liệu. Quy trình được chỉnh dần theo kinh nghiệm làm thật; thay đổi ghi thẳng vào
file này. Thư mục của chặng tên `stage`.

## 1. Nguyên tắc

1. **Mỗi thông tin có đúng một nơi sở hữu.** Nơi khác chỉ trỏ link, không chép lại.
2. **Tài liệu sống khác tài liệu lịch sử.** Tài liệu sống luôn phản ánh trạng thái hiện tại. Tài liệu lịch sử ghi một lần
   ở chặng làm ra nó; muốn biết hiện trạng thì đọc tài liệu sống.
3. **Hướng tham chiếu một chiều:** root không trỏ xuống; `analysis.md` trỏ lên root; tài liệu chặng trỏ root và
   `analysis.md`; `impl.md` trỏ lên chặng, `service.md` và ADR; `service.md` chỉ trỏ tới ADR.
4. **Người làm hiểu thứ đã làm.** Mục tiêu dự án là học. Chỗ cần insight do người viết; chỗ thường do agent viết, người rà.
5. **Kiểm chứng bằng thứ chạy được.** Test và script là nguồn sự thật; tài liệu chỉ ghi cần chứng minh điều gì.
6. **Chi tiết dần theo lượt.** Chặng đang làm thì chi tiết, chặng kế mức tên, các chặng xa chỉ nằm ở roadmap.
7. **Tài liệu chỉ viết khi có điều để nói.** Feature nhỏ có thể bỏ `plan.md`, hoặc `impl.md`.
8. **Chặng chỉ nói nghiệp vụ.** Thành phần kỹ thuật (log, truy vết, rate limiter, CI) và lựa chọn công nghệ đi qua ADR và
   quy ước kỹ thuật, không đi qua analysis của BC.

## 2. Cấu trúc tài liệu

```
docs/
  global/
    1.requirement/     root: bài toán (overview đến roadmap), không nêu công nghệ
    2.architecture/    giải pháp toàn hệ thống: tech-stack.md, adr/ (mỗi quyết định một file)
    3.technical/       ghi chú kỹ thuật đi sâu theo chủ đề
    4.convention/      quy ước làm việc (file này) và quy ước kỹ thuật chung
  service/<service>/   vòng đời của BC (tên thư mục là tên service), cộng dồn qua các chặng
    analysis.md  service.md  data.md  api.yaml  old/ (tài liệu cũ đã được thay)
  stage/
    c0-foundation/
      stage.md         bước 1: yêu cầu, hành vi, BC, kịch bản
      00-features.md   bước 4: năng lực sang feature, phủ hai chiều, kiểm chứng, điều học được
      NN-<tên>/       flow.md, tech.md, nfr.md, deferred.md, plan.md
scenarios/  testdata/  ở gốc repo: script kịch bản và dữ liệu mẫu
```

Tên thư mục và file dùng tiếng Anh; nội dung viết tiếng Việt. Chặng đặt tên theo mã roadmap kèm tên ngắn (`c0-foundation`,
`c1a-listing`).

### 2.1 Nơi sở hữu

| Thông tin | Nơi | Loại |
|---|---|---|
| Lý do nghiệp vụ, năng lực, INV, đường không suôn sẻ của BC | `analysis.md` | Sống |
| Aggregate, event, giao diện giữa BC, khái niệm kỹ thuật (một dòng) | `service.md` | Sống |
| Cấu trúc lưu trữ | `data.md` | Sống |
| API | `api.yaml` | Sống |
| Quy ước dùng chung | `4.convention` | Sống |
| Quyết định kỹ thuật, phương án đã loại, lý do | `adr/` | Lịch sử (bị thay thì thêm ADR mới) |
| Yêu cầu, kịch bản, luồng nghiệp vụ của chặng | `stage.md` | Lịch sử |
| Bảng năng lực sang feature, kế hoạch kiểm chứng, điều học được | `00-features.md` | Lịch sử |
| Luồng xử lý, lưu ý kỹ thuật của feature | `impl.md` | Lịch sử |
| Task, người làm | `plan.md` | Lịch sử |

`analysis.md` cộng dồn qua các chặng: chặng sau bổ sung phần của mình. Tài liệu chặng chỉ trỏ tới nó, không chép.

## 3. Luồng bảy bước

| # | Bước | Đầu ra |
|---|---|---|
| 1 | **Phân tích nghiệp vụ cấp chặng.** Đi từ yêu cầu của chặng: vì sao cần, hành vi nào cần để nó đúng, hành vi thuộc BC nào, điều gì có thể sai thì sinh ra quy tắc nào. Sau đó mới viết kịch bản chấp nhận, mỗi kịch bản chỉ ra nó chứng minh hành vi hoặc quy tắc nào. Cuối cùng là luồng nghiệp vụ xuyên BC, phần ngoài chặng kèm lý do, và câu hỏi chuyển sang bước 2 | `stage.md` |
| 2 | **Phân tích BC.** Cho mỗi BC suy ra ở bước 1, bổ sung `analysis.md`: năng lực của phần thuộc chặng ở mức mịn (sáu câu của P3), INV, đường lỗi, điểm nối. Phần khác của BC giữ mức tên | `analysis.md` |
| 3 | **Khung `service.md`.** Từ analysis: aggregate và INV mà nó giữ, tên event cùng bên phát và bên nhận, giao diện giữa BC. Mỗi mục có nhãn "Dự kiến" | `service.md` |
| 4 | **Chia feature.** Lập bảng năng lực sang feature, cắt lát dọc theo kịch bản, không cắt theo BC. Feature kỹ thuật vào từ quy ước và ADR (xem mục 4). Kiểm tra phủ hai chiều: mọi năng lực của chặng có feature; mọi feature có nguồn | `00-features.md` |
| 5 | **Thiết kế từng feature.** Viết `impl.md`: luồng, lưu ý, link tới ADR. Quyết định kỹ thuật ghi vào ADR (hoặc quy ước nếu dùng chung). Trước khi code, bổ sung chi tiết vào `service.md`, `data.md`, `api.yaml` cho phần của feature | `impl.md`, ADR, tài liệu BC |
| 6 | **Làm** theo lát nhỏ; phân công theo mục 5; test viết từ kịch bản và INV | Code, test, tài liệu BC cập nhật |
| 7 | **Đóng chặng** (mục 7) | `00-features.md` có điều học được |

Thứ tự thiết kế trong một BC: phân tích nghiệp vụ, rồi `service.md`, rồi `data.md`, `api.yaml` và code.

### 3.1 Khung và chi tiết

`service.md` được thiết kế hai mức:

- **Khung** ở bước 3: tên aggregate và trách nhiệm, INV, event, giao diện giữa BC. Đây là quyết định chung cả BC, và là
  hợp đồng để agent làm song song.
- **Chi tiết** ở bước 5: trường dữ liệu, trạng thái, hành vi cụ thể, nội dung event, bảng và cột. Chỉ biết đúng khi thiết
  kế luồng của feature.

Khung chỉ phủ phần của BC thuộc chặng hiện tại. Mỗi mục trong `service.md` có nhãn "Dự kiến" hoặc "Đã làm"; feature xong
thì đổi nhãn và đối chiếu với code.

### 3.2 `service.md` chỉ chứa kết luận

`service.md` ghi kết luận ngắn, không ghi lý do hay luồng. Khái niệm kỹ thuật là một dòng kèm link tới ADR.

> Bộ đệm: đọc qua bộ đệm cho chi tiết sản phẩm, hết hạn sau 5 phút, xóa khi có event cập nhật. Xem ADR-0012.

### 3.3 Quyết định đi lên

Mỗi quyết định kỹ thuật có một trong ba phạm vi:

- chỉ đúng cho feature: ghi trong ADR, `impl.md` trỏ tới;
- đúng cho cả BC: thêm một dòng kết luận vào `service.md`, lý do ở ADR;
- đúng cho mọi BC: đưa vào quy ước kỹ thuật chung.

## 4. Nguồn của feature

| Nguồn | Ví dụ | Căn cứ |
|---|---|---|
| Năng lực nghiệp vụ trong `analysis.md` | Đăng ký, đăng nhập | Bảng năng lực sang feature |
| Giao diện | Khung trang, màn hình | `5-ui-analysis.md` |
| Quy ước kỹ thuật và ADR | Truy vết lỗi, bộ đo nền, chạy cục bộ | NFR và quy ước dùng chung |

Feature kỹ thuật không đi qua analysis của BC vì không có BC sở hữu từ vựng của nó.

## 5. Phân công người và agent

Mỗi task trong `plan.md` có nhãn người viết hoặc agent viết.

- **Người viết:** điểm sâu, chỗ cần insight, chỗ quyết định thiết kế.
- **Agent viết, người rà:** phần thường, lặp lại, theo khuôn có sẵn.

Quy tắc chọn thực hiện khi bắt đầu từng task, không chốt trước cho cả chặng.

## 6. Kiểm chứng

### 6.1 Không có file test case bằng chữ

Test viết bằng code là đặc tả sống. Tài liệu chỉ ghi ba thứ: kịch bản chấp nhận của chặng (`stage.md`), danh sách INV và
đường lỗi phải có test (`impl.md`, chỉ ghi tên), và kế hoạch đo của điểm sâu.

### 6.2 Tài sản kiểm thử

| Loại | Ví dụ | Vị trí |
|---|---|---|
| Test tự động | Test cho INV, đường lỗi, đường chính | Trong từng service |
| Dữ liệu mẫu | Một bộ seed dùng chung cho test, thử tay, Giả lập | Trong service; bộ dùng chung ở `testdata/` |
| Script kịch bản | Một file chạy từ đầu đến cuối rồi kiểm kết quả | `scenarios/` |
| File thử tay | curl hoặc `.http` | Cạnh script kịch bản |

Mỗi feature có bước **Chạy thật**: build, khởi động ứng dụng, gọi API thật bằng file `.http` đặt trong thư mục feature, xem log; feature
chỉ đóng sau bước này. Script kịch bản ở `scenarios/` ghép các lời gọi thành kịch bản đầu-cuối của cả chặng (feature kiểm đầu-cuối).

Ưu tiên script kịch bản hơn curl rời rạc, vì script kịch bản chuyển được thành test đầu-cuối. Tài sản không được chạy thì
sẽ lệch, nên mỗi tài sản phải được chạy ở bước đóng chặng.

### 6.3 Viết test

Agent viết test từ kịch bản và INV, không từ code vừa viết; người duyệt kịch bản kỹ hơn duyệt code. Lý do: test viết
theo code dễ mang cùng hiểu lầm với code.

### 6.4 Công cụ kiểm thử

Công cụ được xây khi thiếu nó buộc phải kiểm tay lặp lại từ ba lần, hoặc không có nó thì không phát biểu được một con
số yêu cầu phi chức năng.

| Tầng | Ví dụ | Thời điểm |
|---|---|---|
| Hạ tầng kiểm thử thường | Chạy kịch bản, kiểm thử tích hợp có CSDL thật, kiểm thử hợp đồng, dựng dữ liệu mẫu | Mỏng, từ chặng đầu |
| Quan sát | Mã tương quan, log, số đo cơ bản | Trước mọi phép đo |
| Giả lập | Mô phỏng shipper, sinh tải, tiêm lỗi | Từng phần, khi chặng cần |

Mỗi công cụ đo có một phép thử với đáp án đã biết (như bộ đo nền) để con số do nó đo ra đáng tin.

## 7. Đóng chặng

Chặng đóng khi:

1. Mọi kịch bản chấp nhận chạy qua.
2. Mọi INV và đường lỗi đã nêu có test.
3. Điểm sâu (nếu có) có số đo kèm nhãn Đã đo, Suy ra hoặc Chỉ thiết kế.
4. `service.md`, `data.md`, `api.yaml` khớp code; nhãn "Dự kiến" đã đổi thành "Đã làm" cho phần xong.
5. `00-features.md` có đoạn ghi điều học được.

## 8. Tài liệu và code có từ trước

Tài liệu và code có từ trước không dùng làm đầu vào của phân tích, và tài liệu mới không nhắc tới chúng. Mọi tài liệu
viết như phát triển mới từ đầu, kể cả khi việc làm là sửa code có sẵn: sửa code cũ được coi là phát triển mới.

Khi phân tích tới BC nào thì viết `analysis.md` trước, rồi mới đối chiếu code cũ của BC đó. Kết quả đối chiếu ghi trong
một file tạm (`docs/draft/_so-sanh-<bc>.md`) để lập danh sách việc sửa; xong việc thì bỏ file tạm. Nếu đối chiếu cho
thấy phân tích thiếu một nghiệp vụ thì bổ sung vào `analysis.md` như nghiệp vụ mới, kèm lý do nghiệp vụ, không ghi
nguồn là code.

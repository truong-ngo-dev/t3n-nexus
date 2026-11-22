# Báo cáo hiện trạng: Mô hình hóa tri thức dự án phần mềm cho đa vai trò và nhiều AI Agent

**Thời điểm khảo sát:** Tháng 11/2026

> **Quy ước độ tin cậy trong báo cáo:**
> - Các số liệu (số sao GitHub, giấy phép, phiên bản, kết luận bài báo) là thông tin **[Xác minh từ nguồn]** tại thời điểm tháng 11/2026.
> - Các đánh giá về mức độ đáp ứng 5 yêu cầu cốt lõi trong Bảng B và phân tích khoảng trống ở mục D là **[Suy luận]** dựa trên tài liệu chính thức và kiến trúc đã xác minh của từng công cụ.

---

## Bài toán đặt ra

Mô hình hóa tri thức của một dự án phần mềm để nhiều vai trò (phân tích nghiệp vụ, kiến trúc sư, lập trình viên, kiểm thử) và nhiều AI agent cùng đọc và ghi. Tri thức gồm: yêu cầu, phân tích nghiệp vụ, thiết kế kỹ thuật, quyết định kiến trúc (ADR), ghi chú brainstorm, mô tả cách thành phần vận hành.

**5 điểm mấu chốt:**
1. Mỗi loại tài liệu có độ tin cậy khác nhau (quyết định đã chốt khác ý tưởng brainstorm).
2. Tri thức có vòng đời: còn hiệu lực, bị thay thế, hoặc lệch so với mã thực tế.
3. Có truy vết giữa các loại (`yêu cầu -> thiết kế -> mã -> kiểm thử`).
4. Mỗi tác vụ của agent cần một "gói ngữ cảnh" chọn lọc, không phải cả kho tài liệu.
5. Nhiều agent cùng làm việc trên một trạng thái chung có cấu trúc (không phải bản ghi hội thoại thô).

---

## A. Phân loại các công cụ, khung làm việc và dự án liên quan

Hệ sinh thái hiện tại phân mảnh thành **7 nhóm** (gồm 6 nhóm trong đề bài và 1 nhóm bổ sung quan trọng):

1. **Bộ nhớ dài hạn cho AI Agent (Agent Memory):**
   - **Mem0** ([GitHub](https://github.com/mem0ai/mem0)), **Graphiti / Zep** ([GitHub](https://github.com/getzep/graphiti)), **Letta** (trước đây là MemGPT, [GitHub](https://github.com/letta-ai/letta)), **Cognee** ([GitHub](https://github.com/topoteretes/cognee)), **Basic Memory** ([GitHub](https://github.com/basicmachines-co/basic-memory)).
   - Tập trung lưu trữ và truy xuất sự kiện/sự thật (facts) qua nhiều phiên làm việc. Trong đó **Graphiti** nổi bật với đồ thị tri thức song thời gian (*bi-temporal knowledge graph*) cho phép đánh dấu sự thật cũ bị vô hiệu hóa thay vì xóa bỏ.

2. **Quản lý ADR & Quyết định kiến trúc (ADR / Architecture-as-Code):**
   - **adr-tools** ([GitHub](https://github.com/npryce/adr-tools)), **Log4Brains** ([GitHub](https://github.com/thomvaill/log4brains)), **Structurizr DSL** ([GitHub](https://github.com/structurizr/dsl)).
   - Quản lý quyết định kiến trúc dạng Markdown/DSL trong Git, có trạng thái `Proposed / Accepted / Superseded`, nhưng hầu như chỉ phục vụ con người, chưa có cơ chế đóng gói ngữ cảnh cho agent.

3. **Truy vết yêu cầu dạng văn bản trong Git (Requirements-as-Code & Traceability):**
   - **StrictDoc** ([GitHub](https://github.com/strictdoc-project/strictdoc)), **Doorstop** ([GitHub](https://github.com/doorstop-dev/doorstop)), **OpenFastTrace** ([GitHub](https://github.com/itsallcode/openfasttrace)), **TReqS (`treqs-ng`)** ([GitLab](https://gitlab.com/treqs-on-git/treqs-ng)).
   - Xuất phát từ ngành phần mềm an toàn (ISO 26262, DO-178C). Mạnh về truy vết 2 chiều (`yêu cầu ↔ thiết kế ↔ mã ↔ kiểm thử`) và phát hiện liên kết nghi ngờ (*suspect links*) khi mã hoặc yêu cầu thay đổi, nhưng không được thiết kế cho AI agent hay tri thức phi cấu trúc (brainstorm).

4. **Phát triển theo đặc tả cho AI Agent (Spec-Driven Development — SDD):**
   - **GitHub Spec Kit** ([GitHub](https://github.com/github/spec-kit)), **OpenSpec** ([GitHub](https://github.com/Fission-AI/OpenSpec)), **BMAD-METHOD** ([GitHub](https://github.com/bmad-code-org/BMAD-METHOD)), **AWS Kiro** ([kiro.dev](https://kiro.dev)).
   - Bùng nổ trong năm 2025–2026 nhằm khắc phục "vibe coding": chuẩn hóa luồng `Specify → Plan → Tasks → Implement` bằng file Markdown (`requirements.md`, `design.md`, `tasks.md`). Riêng **OpenSpec** tập trung vào dự án *brownfield* bằng cơ chế thư mục thay đổi (*delta specs*).

5. **Knowledge Graph & Ngữ cảnh mã nguồn (Codebase Knowledge Graph & Semantic MCP):**
   - **CodeGraphContext (CGC)** ([GitHub](https://github.com/CodeGraphContext/CodeGraphContext)), **Serena** ([GitHub](https://github.com/oraios/serena)).
   - Biến mã nguồn thành đồ thị AST/SCIP hoặc cung cấp truy vấn ngữ nghĩa qua LSP + MCP, giúp agent truy vết call-graph và phạm vi ảnh hưởng (*blast radius*) mà không cần nạp cả repo.

6. **Điều phối đa agent có trạng thái chung (Multi-Agent Shared State Orchestration):**
   - **LangGraph** ([GitHub](https://github.com/langchain-ai/langgraph)), **CrewAI** ([GitHub](https://github.com/crewAIInc/crewAI)), **MetaGPT** ([GitHub](https://github.com/FoundationAgents/MetaGPT)), **AutoGen** ([GitHub](https://github.com/microsoft/autogen)).
   - Quản lý trạng thái chung có cấu trúc (graph state / typed schema) trong quá trình thực thi tác vụ đa agent, nhưng trạng thái này thường mang tính *runtime* (theo từng phiên/luồng chạy) thay vì kho tri thức dài hạn của cả dự án.

7. **Nhóm bổ sung chưa được nêu trong đề bài:**
   - **Tài liệu sống gắn mã & Phát hiện trôi lệch (Code-Coupled Living Docs):** **Swimm** ([swimm.io](https://swimm.io)) — gắn trực tiếp đoạn tài liệu vào AST/symbol của mã nguồn và chạy kiểm tra drift trong CI/CD.
   - **Đóng gói ngữ cảnh kho mã (Context Packing):** **Repomix** ([GitHub](https://github.com/yamadashy/repomix)) — gom và nén mã nguồn theo bộ lọc/tác vụ thành định dạng tối ưu token cho LLM.
   - **Trình theo dõi công việc & bộ nhớ đồ thị ngay trong Git (Git-Native Graph Issue/Memory Tracker cho Agent):** **Beads** ([GitHub](https://github.com/gastownhall/beads), trước là `steveyegge/beads`) — lưu đồ thị công việc, phụ thuộc và ngữ cảnh thực thi ngay trong Git để nhiều agent cùng đọc/ghi có cấu trúc thay vì đọc log hội thoại.

---

## B. Bảng so sánh chi tiết các công cụ

> *Ghi chú cột:* Trong các cột tính năng (5–8), **"Có"** = tính năng cốt lõi được xác minh trong tài liệu; **"Một phần"** = hỗ trợ gián tiếp hoặc thủ công *(Suy luận)*; **"Không"** = không hỗ trợ gốc.

| Tên | Link | Mô tả một câu | Loại tri thức ghi nhận | Có độ tin cậy/trọng số? | Có hiệu lực/bị thay thế/lệch mã? | Có truy vết? | Có đóng gói ngữ cảnh theo tác vụ? | Giấy phép | Lần cập nhật gần nhất | Mức độ trưởng thành (sao, phát hành, người dùng) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Mem0** | [github.com/mem0ai/mem0](https://github.com/mem0ai/mem0) | Lớp bộ nhớ dài hạn (vector + graph) tự trích xuất và hợp nhất tri thức qua nhiều phiên cho AI agent. | Sự thật hội thoại, sở thích, thực thể/quan hệ (entity/graph). | **Một phần** (điểm tương đồng truy xuất, không phân cấp độ tin cậy theo loại tài liệu — *Suy luận*). | **Một phần** (cập nhật/hợp nhất fact mới; không phát hiện lệch mã). | **Không** | **Có** (truy xuất chọn lọc theo truy vấn/agent). | Apache-2.0 | Cuối 2026 (Python SDK v2.x, cập nhật 09/2026). | Rất cao (~69k+ sao, có bài báo arXiv & nền tảng thương mại). |
| **Graphiti (Zep)** | [github.com/getzep/graphiti](https://github.com/getzep/graphiti) | Engine đồ thị tri thức song thời gian (bi-temporal) theo dõi sự thay đổi của sự thật theo thời gian cho AI agent. | Thực thể, quan hệ, sự kiện (episodes), tóm tắt chuỗi sự kiện (sagas). | **Không** (mặc định theo độ mới/liên quan, chưa có trọng số độ tin cậy theo loại tài liệu — *Suy luận*). | **Có (hiệu lực/thay thế)** nhờ mô hình bi-temporal tự vô hiệu hóa cạnh cũ; **Không** tự phát hiện lệch mã. | **Một phần** (truy vết nguồn gốc từ episode gốc, không truy vết yêu cầu→mã→test). | **Có** (truy vấn đồ thị + MCP server v1.0). | Apache-2.0 | 10–11/2026 (MCP v1.0, `summarize_saga`). | Cao (~31k+ sao, sản phẩm thương mại Zep Cloud, có bài báo arXiv). |
| **Letta (MemGPT)** | [github.com/letta-ai/letta](https://github.com/letta-ai/letta) | Nền tảng xây dựng agent có trạng thái với bộ nhớ phân tầng (core, archival, recall) do agent tự quản lý. | Bộ nhớ cốt lõi (memory blocks), nhật ký hội thoại, kho lưu trữ ngữ nghĩa. | **Một phần** (phân tầng core vs archival — *Suy luận*). | **Một phần** (agent tự sửa memory block; không kiểm tra lệch mã). | **Không** | **Có** (nén ngữ cảnh, nạp khối bộ nhớ vào cửa sổ 128k). | Apache-2.0 | 2026 (kiến trúc V2, Letta Code). | Cao (~25.5k+ sao, cộng đồng & sản phẩm Letta Cloud). |
| **Cognee** | [github.com/topoteretes/cognee](https://github.com/topoteretes/cognee) | Nền tảng bộ nhớ AI biến tài liệu phi cấu trúc thành đồ thị tri thức kết hợp vector store. | Tài liệu văn bản, thực thể, đồ thị ngữ nghĩa, dấu vết phiên chạy. | **Không** (không xác minh được cơ chế trọng số độ tin cậy theo loại tài liệu). | **Không** (chưa thấy cơ chế vòng đời superseded/code drift). | **Một phần** (liên kết đồ thị giữa tài liệu/thực thể). | **Có** (truy xuất đồ thị + MCP server). | Apache-2.0 | 11/2026 (`v1.6.8`). | Cao (~33k+ sao, phát hành đều đặn, tích hợp MCP/VS Code). |
| **Basic Memory** | [github.com/basicmachines-co/basic-memory](https://github.com/basicmachines-co/basic-memory) | Kho tri thức Markdown local-first (kiểu Obsidian) phơi bày qua MCP cho AI assistant đọc/ghi. | Ghi chú Markdown, quan hệ wiki-link, ghi chú dự án/brainstorm. | **Không** | **Không** | **Một phần** (liên kết thủ công dạng wiki-link). | **Có** (đọc chọn lọc qua MCP). | GNU AGPLv3 | Không xác minh được ngày commit gần nhất. | Trung bình (~3.6k–4.6k sao). |
| **Beads** | [github.com/gastownhall/beads](https://github.com/gastownhall/beads) | Trình quản lý tác vụ/bộ nhớ dạng đồ thị lưu trực tiếp trong Git (hỗ trợ Dolt) để AI agent chia sẻ trạng thái có cấu trúc. | Tác vụ (issues), phụ thuộc đồ thị, trạng thái công việc, ghi chú thực thi. | **Không** | **Một phần** (vòng đời issue open/closed, không phát hiện lệch mã tự động). | **Một phần** (truy vết phụ thuộc giữa các task/issue). | **Có** (agent truy vấn các task sẵn sàng làm và ngữ cảnh liên quan). | MIT | 11/2026 (`v1.4.0`). | Cao (~28k+ sao, tăng trưởng rất nhanh trong cộng đồng agent). |
| **adr-tools** | [github.com/npryce/adr-tools](https://github.com/npryce/adr-tools) | Bộ script CLI cổ điển tạo và liên kết các Architecture Decision Records (ADR) dạng Markdown trong Git. | Quyết định kiến trúc (ADR). | **Không** | **Có (hiệu lực/thay thế)** (`Accepted`, `Superseded by`); **Không** phát hiện lệch mã. | **Một phần** (chỉ liên kết ADR ↔ ADR). | **Không** | GPL-3.0 | **Gần như ngừng phát triển tính năng** (bản phát hành cuối v2.2.0 từ 07/2018, chỉ có vài commit bảo trì nhỏ). | Trưởng thành nhưng cũ (~5.8k sao, chuẩn tham chiếu lịch sử). |
| **Log4Brains** | [github.com/thomvaill/log4brains](https://github.com/thomvaill/log4brains) | Công cụ CLI và giao diện web tĩnh để ghi nhận và xuất bản ADR ngay cạnh mã nguồn. | Quyết định kiến trúc (ADR). | **Không** | **Có (hiệu lực/thay thế)** (trạng thái ADR); **Không** phát hiện lệch mã. | **Một phần** (liên kết giữa các ADR). | **Không** | Apache-2.0 | Cuối 2024–2025 (cập nhật tương thích Node.js; tốc độ chậm). | Trung bình (~1.6k sao). |
| **Structurizr DSL** | [github.com/structurizr/dsl](https://github.com/structurizr/dsl) | Ngôn ngữ mô tả kiến trúc phần mềm dạng mã (C4 model) có hỗ trợ nhúng ADR và tài liệu kỹ thuật. | Mô hình kiến trúc (C4), thiết kế thành phần, ADR. | **Không** | **Một phần** (hiển thị trạng thái ADR; không tự phát hiện lệch mã trừ khi tự viết script kiểm tra). | **Một phần** (liên kết mô hình kiến trúc ↔ ADR). | **Không** | Apache-2.0 | 2026 (đang duy trì tích cực trong hệ sinh thái Structurizr). | Cao trong cộng đồng kiến trúc (~548 sao cho repo DSL, dùng rộng rãi doanh nghiệp). |
| **Swimm** | [swimm.io](https://swimm.io) | Nền tảng thương mại tạo tài liệu gắn chặt vào mã nguồn (code-coupled) và tự động cảnh báo trôi lệch trong CI/CD. | Mô tả cách thành phần vận hành, tài liệu kỹ thuật, hướng dẫn mã nguồn. | **Không** | **Có (lệch mã)** — dùng AST/static analysis tự đồng bộ thay đổi nhỏ hoặc báo lỗi PR khi mã lệch tài liệu. | **Có (tài liệu ↔ mã)**; không chuyên truy vết yêu cầu ↔ kiểm thử. | **Một phần** (hiển thị tài liệu gắn với file đang mở; không phải gói ngữ cảnh đa vai cho agent — *Suy luận*). | Thương mại (độc quyền, bán theo hợp đồng doanh nghiệp). | 2026 (đang vận hành thương mại). | Cao ở mảng doanh nghiệp (không công khai số người dùng). |
| **StrictDoc** | [github.com/strictdoc-project/strictdoc](https://github.com/strictdoc-project/strictdoc) | Công cụ quản lý yêu cầu và tài liệu kỹ thuật dạng văn bản (`.sdoc`) trong Git, hỗ trợ tiêu chuẩn an toàn. | Yêu cầu, đặc tả kỹ thuật, liên kết mã nguồn và kiểm thử. | **Không** (có thuộc tính trạng thái/mức độ ưu tiên nhưng không phân loại tri thức mềm như brainstorm). | **Có** (kiểm tra liên kết hỏng/thay đổi qua hash/git diff). | **Có (rất mạnh)** — truy vết 2 chiều yêu cầu ↔ thiết kế ↔ mã ↔ kiểm thử. | **Không** | Apache-2.0 | 11/2026 (commit liên tục trong vài ngày gần đây). | Trung bình–Cao ở mảng nhúng/an toàn (~457 sao, hỗ trợ ISO 26262/DO-178C). |
| **Doorstop** | [github.com/doorstop-dev/doorstop](https://github.com/doorstop-dev/doorstop) | Công cụ quản lý yêu cầu lưu từng mục dưới dạng file YAML theo cấu trúc cây trong Git. | Yêu cầu, đặc tả, mục kiểm thử. | **Không** | **Có** (phát hiện *suspect links* qua fingerprint khi mục cha thay đổi). | **Có** (cây truy vết cha–con giữa các tập tài liệu). | **Không** | LGPL-3.0 | 11/2026 (bảo trì tích cực trong tuần gần đây). | Trung bình (~697 sao, dự án lâu năm). |
| **OpenFastTrace** | [github.com/itsallcode/openfasttrace](https://github.com/itsallcode/openfasttrace) | Bộ công cụ Java truy vết yêu cầu nhúng trực tiếp trong mã nguồn và tích hợp Maven/Gradle. | Yêu cầu, thiết kế, thẻ truy vết trong mã và test. | **Không** | **Có** (phát hiện yêu cầu chưa phủ hoặc mã/yêu cầu lỗi thời khi build). | **Có** (yêu cầu ↔ thiết kế ↔ mã ↔ kiểm thử). | **Không** | GPL-3.0 | 2026 (rất tích cực, tới `v4.11.0`). | Ngách (~210 sao, ổn định trong hệ sinh thái Java/CI). |
| **TReqS (`treqs-ng`)** | [gitlab.com/treqs-on-git/treqs-ng](https://gitlab.com/treqs-on-git/treqs-ng) | Hệ thống quản lý yêu cầu dạng văn bản nhẹ trong Git do nhóm nghiên cứu ĐH Gothenburg/Chalmers phát triển. | Yêu cầu hệ thống, lập luận tuân thủ, liên kết mã/test. | **Không** | **Có** (kiểm tra tính nhất quán liên kết trong Git/CI). | **Có** (yêu cầu ↔ mã ↔ test). | **Không** | Không xác minh được giấy phép cụ thể. | Không xác minh được ngày commit gần nhất. | Nghiên cứu / Ngách (dùng trong các dự án nghiên cứu & an toàn). |
| **GitHub Spec Kit** | [github.com/github/spec-kit](https://github.com/github/spec-kit) | Bộ công cụ CLI chính thức của GitHub chuẩn hóa quy trình Spec-Driven Development cho nhiều AI coding agent. | Hiến pháp dự án (constitution), đặc tả tính năng, kế hoạch kỹ thuật, danh sách tác vụ. | **Một phần** (tách biệt constitution bất biến với spec từng tính năng — *Suy luận*). | **Không** (chủ yếu Spec-First; chưa tự động phát hiện lệch mã sau khi triển khai — *Suy luận*). | **Một phần** (liên kết thủ công giữa spec → plan → tasks). | **Có** (slash command nạp template & spec theo từng bước). | MIT | 11/2026 (`v1.4.3`, commit hàng ngày). | Rất cao (~150k+ sao, được GitHub hậu thuẫn). |
| **OpenSpec** | [github.com/Fission-AI/OpenSpec](https://github.com/Fission-AI/OpenSpec) | Khung phát triển theo đặc tả gọn nhẹ, tối ưu token, dùng thư mục delta cho dự án brownfield. | Đề xuất thay đổi (proposal), delta yêu cầu, thiết kế, tác vụ. | **Một phần** (tách biệt đề xuất đang mở với spec đã chốt/archive — *Suy luận*). | **Một phần** (có cơ chế archive/delta cập nhật spec, chưa có bộ kiểm tra lệch mã tự động). | **Một phần** (gắn theo từng thư mục thay đổi). | **Có** (thiết kế tối ưu token, skill kiến trúc `.agents/skills`). | MIT | 11/2026 (`v2.1.1`). | Rất cao (~72k+ sao). |
| **BMAD-METHOD** | [github.com/bmad-code-org/BMAD-METHOD](https://github.com/bmad-code-org/BMAD-METHOD) | Khung làm việc Agile đa vai trò (PM, BA, Architect, Dev, QA) dưới dạng persona Markdown cho AI agent. | Brief, PRD, tài liệu kiến trúc, user story, kế hoạch QA. | **Một phần** (quy trình cổng kiểm duyệt giữa các pha — *Suy luận*). | **Không** (không có kiểm tra lệch mã tự động sau pha dev). | **Một phần** (chuyển giao artifact theo pha PRD → Architecture → Story). | **Có** (chia nhỏ ngữ cảnh theo từng vai trò/story). | MIT | 10/2026 (`v6.12.4`, nhánh phát triển đang tiếp tục). | Rất cao (~54k+ sao). |
| **AWS Kiro** | [kiro.dev](https://kiro.dev) | IDE tích hợp AI agent của Amazon xây dựng xoay quanh quy trình đặc tả (`requirements.md`, `design.md`, `tasks.md`). | Yêu cầu (cú pháp EARS), thiết kế kỹ thuật, tác vụ, steering docs. | **Không** (không có thang trọng số tin cậy đa mức cho tri thức mềm). | **Một phần** (hỗ trợ kiểm tra nhất quán logic bằng EARS/SMT ở mức spec; không xác minh được khả năng tự bắt drift dài hạn toàn repo). | **Có (một phần)** — liên kết requirements ↔ design ↔ tasks. | **Có** (tự điều phối ngữ cảnh theo từng task trong IDE). | Thương mại (gói Free + Pro $20–$200/tháng, Team $20/seat). | 2026 (đang phát triển thương mại tích cực). | Cao (sản phẩm chiến lược của AWS). |
| **CodeGraphContext** | [github.com/CodeGraphContext/CodeGraphContext](https://github.com/CodeGraphContext/CodeGraphContext) | Công cụ/MCP server lập chỉ mục mã nguồn thành đồ thị thuộc tính (dùng Tree-sitter/SCIP) để AI agent truy vấn. | Cấu trúc mã nguồn: hàm, lớp, call-chain, kế thừa, phụ thuộc. | **Không** (chỉ phản ánh mã thực tế). | **Có (chỉ cho mã)** — phản ánh đúng mã nguồn hiện tại; không quản lý tài liệu nghiệp vụ/ADR. | **Một phần** (chỉ truy vết mã ↔ mã). | **Có** (truy vấn đồ thị chọn lọc qua MCP). | MIT | 11/2026 (cập nhật rất thường xuyên). | Trung bình–Cao (~4.4k sao). |
| **Serena** | [github.com/oraios/serena](https://github.com/oraios/serena) | Bộ công cụ MCP cung cấp khả năng đọc/sửa mã theo ngữ nghĩa (dựa trên LSP) cho AI coding agent. | Biểu tượng mã (symbols), tham chiếu mã nguồn, bộ nhớ dự án cơ bản. | **Không** | **Có (chỉ cho mã)** — đọc trực tiếp trạng thái mã qua LSP. | **Một phần** (truy vết tham chiếu symbol trong mã). | **Có** (đọc đúng symbol cần thiết thay vì cả file). | Kép: MIT (`SolidLSP`, bản ≤v1.7.0) / GPL-3.0+ (Serena v2+). | 2026 (đang phát triển tích cực). | Cao (~31k+ sao). |
| **Repomix** | [github.com/yamadashy/repomix](https://github.com/yamadashy/repomix) | Công cụ đóng gói toàn bộ hoặc một phần kho mã nguồn thành file thân thiện với LLM. | Mã nguồn và tài liệu có sẵn trong repo. | **Không** | **Không** | **Không** | **Có** (lọc file/nén mã để đóng gói ngữ cảnh). | MIT | 11/2026 (cập nhật trong tuần gần đây). | Cao (~29.4k sao). |
| **LangGraph** | [github.com/langchain-ai/langgraph](https://github.com/langchain-ai/langgraph) | Khung điều phối mức thấp cho luồng agent có trạng thái (stateful graph), bền bỉ và kiểm soát được. | Trạng thái có cấu trúc (typed state) và bộ nhớ phiên/dài hạn (Store) do lập trình viên tự định nghĩa. | **Không** (trừ khi tự lập trình). | **Không** (trừ khi tự lập trình). | **Không** (chỉ có trace thực thi runtime qua LangSmith). | **Có** (middleware/node chọn lọc ngữ cảnh theo từng bước). | MIT | Cuối 2026 (`langgraph 1.2.x`, SDK `0.4.x`). | Rất cao (~45k+ sao, chuẩn công nghiệp cho agent workflow). |
| **CrewAI** | [github.com/crewAIInc/crewAI](https://github.com/crewAIInc/crewAI) | Khung điều phối nhóm AI agent phân vai phối hợp thực hiện quy trình. | Trạng thái luồng (Flows), đầu ra tác vụ, bộ nhớ ngắn/dài hạn. | **Không** | **Không** | **Không** | **Một phần** (chuyển giao output giữa các task). | MIT | 11/2026 (`v1.15.x`). | Rất cao (~60k+ sao). |
| **MetaGPT** | [github.com/FoundationAgents/MetaGPT](https://github.com/FoundationAgents/MetaGPT) | Khung đa agent mô phỏng công ty phần mềm (PM, Architect, Engineer) theo SOP cố định. | PRD, thiết kế hệ thống, mã nguồn sinh ra trong phiên chạy. | **Không** | **Không** | **Một phần** (chỉ trong một lần sinh dự án mới). | **Một phần** (truyền tài liệu theo vai SOP). | MIT | **Chậm lại / Ít phát hành mới** (bản phát hành chính thức cuối v0.8.1–v0.8.2 từ 03/2025; năm 2026 không có release mới dù vẫn có PR/issue cộng đồng). | Cao về độ phổ biến (~70k+ sao) nhưng đà phát triển chậm lại. |
| **AutoGen** | [github.com/microsoft/autogen](https://github.com/microsoft/autogen) | Khung lập trình đa agent dựa trên hội thoại của Microsoft. | Nhật ký hội thoại, trạng thái nhóm agent. | **Không** | **Không** | **Không** | **Một phần** | MIT (mã) / CC BY 4.0 (tài liệu). | **Chế độ bảo trì (maintenance mode)** — Microsoft định hướng người dùng mới sang *Microsoft Agent Framework*. | Cao lịch sử (~50k+ sao), đang chuyển giao thế hệ. |

---

## C. Nghiên cứu học thuật và bài kỹ thuật đáng tin cậy

1. **Memory in the Age of AI Agents: A Survey** — Hu et al. (tháng 12/2025, cập nhật 01/2026) — [arXiv:2512.13564](https://arxiv.org/abs/2512.13564)
   - **Kết luận chính:** Phá vỡ cách phân loại thô sơ "ngắn hạn/dài hạn", đề xuất khung phân loại 3 chiều cho bộ nhớ agent: *Hình thức* (token-level, parametric, latent), *Chức năng* (working, factual, experiential) và *Động học* (hình thành, tiến hóa, truy xuất).
2. **A Survey of Agent Memory in the Second Half: Towards Self-Evolving and Long-Horizon Agents** — Huang et al. (01–08/2026) — [arXiv:2602.06052](https://arxiv.org/abs/2602.06052)
   - **Kết luận chính:** Trong các tác vụ dài hạn như *agentic coding*, bùng nổ ngữ cảnh là nút thắt lớn nhất; agent cần cơ chế tích lũy, tinh lọc và tái sử dụng chọn lọc tri thức xuyên phiên thay vì lưu trữ phẳng.
3. **From Storage to Experience: A Survey on the Evolution of LLM Agent Memory Mechanisms** — Chấp nhận tại ACL Findings (05/2026) — [arXiv:2605.06716](https://arxiv.org/abs/2605.06716)
   - **Kết luận chính:** Ghi nhận sự dịch chuyển từ RAG tĩnh (lưu trữ thụ động) sang chưng cất kinh nghiệm chủ động (chuyển vết thực thi thành tri thức thủ tục tái sử dụng được).
4. **Evaluating AGENTS.md: Are Repository-Level Context Files Helpful for Coding Agents?** — Gloaguen et al., ETH Zurich & LogicStar.ai (02/2026) — [arXiv:2602.11988](https://arxiv.org/abs/2602.11988)
   - **Kết luận chính:** File ngữ cảnh cấp repo do LLM tự sinh (`/init`) làm **giảm 2–3% tỷ lệ giải quyết tác vụ** và **tăng >20% chi phí suy luận**. File do người viết chỉ cải thiện nhẹ (~+4%) nhưng vẫn tăng chi phí. Chỉ nên giữ các quy tắc tối giản không thể tự suy ra từ mã.
5. **Building Effective AI Coding Agents for the Terminal: Scaffolding, Harness, Context Engineering, and Lessons Learned** — Bui et al. (03/2026) — [arXiv:2603.05344](https://arxiv.org/abs/2603.05344)
   - **Kết luận chính:** Giới thiệu hệ thống OPENDEV và chứng minh hiệu quả của nén ngữ cảnh thích ứng (*adaptive context compaction*), khám phá công cụ lười (*lazy tool discovery*) và bộ nhớ tự động tích lũy tri thức đặc thù dự án.
6. **From Question Answering to Task Completion: A Survey on Agent System and Harness Design** — (06/2026) — [arXiv:2606.20683](https://arxiv.org/abs/2606.20683)
   - **Kết luận chính:** Thành công của coding agent phụ thuộc mạnh vào *harness* và biểu diễn trạng thái liên quan trực tiếp đến quyết định (*decision-relevant state*), hơn là chỉ nâng cấp mô hình nền.
7. **Zep: A Temporal Knowledge Graph Architecture for Agent Memory** — Rasmussen et al. (01/2025) — [arXiv:2501.13956](https://arxiv.org/abs/2501.13956)
   - **Kết luận chính:** Kiến trúc đồ thị song thời gian (Graphiti) giải quyết bài toán tri thức thay đổi theo thời gian, vượt MemGPT trên Deep Memory Retrieval và tăng tới 18.5% độ chính xác trên LongMemEval đồng thời giảm mạnh độ trễ.
8. **Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory** — Chhikara et al. (04/2025) — [arXiv:2504.19413](https://arxiv.org/abs/2504.19413)
   - **Kết luận chính:** Cơ chế trích xuất–hợp nhất động kết hợp đồ thị của Mem0 vượt RAG truyền thống và full-context trên benchmark LOCOMO, tiết kiệm >90% token.
9. **Effective Context Engineering for AI Agents** — Anthropic Engineering Blog (2025) — [anthropic.com](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents) *(xác minh nội dung qua trích dẫn chính thức của Anthropic/Claude)*
   - **Kết luận chính:** Cửa sổ ngữ cảnh là tài nguyên hữu hạn chịu hiện tượng suy giảm chú ý (*context rot*). Bốn đòn bẩy cốt lõi là: nén (*compaction*), xóa kết quả công cụ thừa (*tool-result clearing*), bộ nhớ ngoài có cấu trúc, và cô lập ngữ cảnh bằng subagent.
10. **Context Engineering for Agents** — LangChain Blog (2025) — [blog.langchain.com](https://blog.langchain.com/context-engineering-for-agents/)
    - **Kết luận chính:** Chuẩn hóa 4 chiến lược quản lý ngữ cảnh: **Write** (ghi trạng thái), **Select** (chọn đúng ngữ cảnh cho từng bước), **Compress** (nén lịch sử), **Isolate** (cô lập tác vụ phụ).
11. **Understanding Spec-Driven Development** — Birgitta Böckeler trên martinfowler.com (2025–2026) — [martinfowler.com](https://martinfowler.com)
    - **Kết luận chính:** Phân loại SDD thành 3 cấp độ (*Spec-First*, *Spec-Anchored*, *Spec-As-Source*), đồng thời nhấn mạnh vai trò của *Harness Engineering* và cảnh báo rủi ro quay lại mô hình Waterfall cứng nhắc.

---

## D. Khoảng trống (Phân tích trực tiếp từ Bảng B)

Đối chiếu 5 yêu cầu cốt lõi của bài toán với 25 công cụ trong Bảng B cho thấy **chưa có công cụ nào lấp đủ toàn bộ bài toán**, và xuất hiện **4 khoảng trống cấu trúc rõ rệt**:

1. **Khoảng trống về "Độ tin cậy / Trọng số tri thức" (Yêu cầu 1):**
   - Trong toàn bộ Bảng B, **0/25 công cụ** có mô hình hạng nhất (*first-class*) về cấp độ tin cậy tri thức (ví dụ: phân biệt *brainstorm nháp* < *đề xuất đang thảo luận* < *ADR/Spec đã phê duyệt* < *sự thật trong mã & test đang chạy*).
   - Các công cụ SDD (GitHub Spec Kit, OpenSpec, BMAD-METHOD) chỉ tách biệt thô qua thư mục/pha; các công cụ bộ nhớ (Mem0, Graphiti, Letta, Cognee) đối xử các fact tương đối bình đẳng dựa trên độ tương đồng hoặc thời gian. *(Suy luận từ cột 5 của Bảng B)*.

2. **Đứt gãy giữa "Truy vết & Phát hiện lệch mã" với "Đóng gói ngữ cảnh cho Agent" (Yêu cầu 2 + 3 + 4):**
   - Nhóm làm tốt **truy vết + phát hiện lệch mã** là StrictDoc, Doorstop, OpenFastTrace, Swimm — nhưng cả 4 công cụ này đều có cột *"Đóng gói ngữ cảnh theo tác vụ"* là **Không** hoặc **Một phần rất hạn chế**.
   - Ngược lại, nhóm làm tốt **đóng gói ngữ cảnh cho agent** (GitHub Spec Kit, OpenSpec, Mem0, Graphiti, Serena, CodeGraphContext) lại có cột *"Truy vết đầy đủ yêu cầu→thiết kế→mã→test"* và *"Phát hiện lệch mã tự động cho tài liệu nghiệp vụ/ADR"* là **Không** hoặc **Một phần**.
   - **Hệ quả:** Không có hệ thống nào vừa biết *ADR/Yêu cầu X đã bị lệch so với hàm Y sau commit Z* (như Swimm/StrictDoc), vừa dùng đồ thị đó để *tự động lắp ráp một gói ngữ cảnh tối giản, sạch drift* cho agent sửa lỗi hoặc viết test. *(Suy luận từ cột 6, 7, 8 của Bảng B)*.

3. **Khoảng trống về "Vòng đời liên kết chéo giữa tri thức nghiệp vụ và đồ thị mã" (Yêu cầu 2 + 3):**
   - **Graphiti** giải quyết tốt vòng đời thời gian (`valid_time` / `invalidated_at`) cho *sự thật ngữ nghĩa*, còn **CodeGraphContext / Serena** giải quyết tốt *đồ thị cấu trúc mã*. Chưa có công cụ nào hợp nhất hai đồ thị này thành một kho chung trong Git để khi mã nguồn thay đổi (AST diff), các cạnh tri thức liên quan (yêu cầu, thiết kế, ADR) tự động chuyển trạng thái `needs-review` hoặc `stale`. *(Suy luận từ Bảng B)*.

4. **Khoảng trống về "Trạng thái chung đa vai (người + nhiều agent) bền vững theo dự án" (Yêu cầu 5):**
   - Nhóm **LangGraph / CrewAI** có trạng thái có cấu trúc cho nhiều agent nhưng là *trạng thái thực thi ngắn hạn (runtime state)*, không phải kho tri thức để BA, Kiến trúc sư, Dev, QA cùng đọc/ghi như tài liệu dự án.
   - **Beads** tiến gần nhất tới trạng thái chung dạng đồ thị trong Git cho nhiều agent, nhưng phạm vi dừng ở *issue/task graph*, chưa mô hình hóa đầy đủ yêu cầu nghiệp vụ, ADR, brainstorm và liên kết kiểm thử. **BMAD-METHOD** có đủ vai trò nhưng quản lý bằng file Markdown rời rạc, thiếu cơ chế trạng thái chung có kiểm soát đồng thời và kiểm tra tính nhất quán. *(Suy luận từ cột 4 và 7 của Bảng B)*.

---

## E. Các cách tiếp cận thất bại hoặc bị chỉ trích & Bài học

1. **Nhồi nhét tài liệu vào `AGENTS.md` / `CLAUDE.md` và tự sinh bằng `/init`:**
   - **Bằng chứng:** Nghiên cứu của ETH Zurich ([arXiv:2602.11988](https://arxiv.org/abs/2602.11988)) chỉ ra file ngữ cảnh cấp repo tự sinh làm giảm 2–3% tỷ lệ thành công và tăng >20% chi phí token; agent quá "ngoan ngoãn" nên bị dẫn lạc bởi hướng dẫn dư thừa hoặc lỗi thời.
   - **Bài học:** Không viết lại những gì agent tự đọc được từ mã (`package.json`, cấu trúc thư mục). Chỉ ghi các ràng buộc bất thường, ngoại lệ, hoặc quyết định "tại sao" không thể suy ra từ mã.

2. **Bộ nhớ agent dạng vector/RAG không kiểm soát (Memory Pollution & Implicit Conflict):**
   - **Bằng chứng:** Các nghiên cứu như *Zep* ([arXiv:2501.13956](https://arxiv.org/abs/2501.13956)), phân tích *The Memory Curse* ([mycobrain.dev](https://mycobrain.dev)) và benchmark *STALE* trên arXiv chỉ ra rằng lưu mọi ghi chú/hội thoại vào vector store gây trùng lặp, nhiễu và **xung đột ngầm** (*implicit conflict*): khi kiến trúc thay đổi, fact cũ và mới cùng được truy xuất với điểm tương đồng ngang nhau, khiến agent hành động theo thực tế đã lỗi thời.
   - **Bài học:** Phải tách bộ nhớ nhật ký tạm (*ephemeral*) khỏi tri thức đã kiểm duyệt (*curated*); áp dụng cơ chế hết hạn/vô hiệu hóa theo sự kiện (*event-driven invalidation*) hoặc song thời gian (*bi-temporal*) như Graphiti thay vì chỉ `append` vào vector DB.

3. **Spec-Driven Development cứng nhắc ("Waterfall mới" & ảo tưởng "Spec là lưới an toàn"):**
   - **Bằng chứng:** Các phân tích của Martin Fowler, Birgitta Böckeler và Kent Beck ([martinfowler.com](https://martinfowler.com)) chỉ trích việc viết đặc tả khổng lồ trước khi viết mã cho mọi tác vụ nhỏ gây phình to thủ tục, tách rời thực tế thiết kế tiến hóa (*evolutionary design*), và tạo ảo giác rằng bản spec là lưới an toàn thay cho bộ kiểm thử tự động (*test suite*).
   - **Bài học:** Hướng tới mô hình **Spec-Anchored** (đặc tả sống, gọn, cập nhật cùng mã) kết hợp **vòng phản hồi thực thi được** (test tự động, CI check) thay vì coi văn bản tĩnh là chân lý tuyệt đối.

4. **Tài liệu tách rời mã nguồn không có kiểm tra trôi lệch (Documentation Drift):**
   - **Bằng chứng:** Thực tiễn ngành (được nêu trong tài liệu kỹ thuật của [Swimm](https://swimm.io) và nghiên cứu ETH Zurich) cho thấy tài liệu Markdown/Wiki không gắn với mã nguồn sẽ nhanh chóng lỗi thời; với AI agent, "bản đồ cũ" còn nguy hiểm hơn "không có bản đồ" vì agent tin vào tài liệu và sinh mã theo mẫu đã bỏ.
   - **Bài học:** Mọi tri thức mô tả cách hệ thống vận hành hoặc quyết định kiến trúc phải có **neo liên kết tới mã/test** và cơ chế phát hiện thay đổi trong CI (hoặc gắn hash/AST như StrictDoc/Swimm) để gắn cờ `stale` ngay khi mã đổi, trước khi nạp vào gói ngữ cảnh của agent.

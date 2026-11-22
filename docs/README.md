# T3Nexus — Documentation

Cấu trúc tài liệu, quy tắc phân loại, template cho mọi loại doc: [`5.convention`](5.convention/doc-structure.md).

AI agent bắt đầu session: đọc [`5.convention`](5.convention/agent-workflow.md) trước tiên — file đó định nghĩa chính xác cần đọc gì tiếp theo tuỳ theo scenario.

```
docs/
├── global/      # Kiến thức nền toàn hệ thống — requirement, architecture, technical, convention
├── service/     # Thiết kế từng service/lib
└── feature/     # Flow xuyên service của từng tính năng
```

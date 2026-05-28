# DDD Package Structure Convention

> **Dành cho AI agents**: Đây là quy ước bắt buộc cho tất cả services trong hệ thống.
> Mọi code được sinh ra phải tuân theo cấu trúc này. Không tự ý tạo package ngoài
> những package đã định nghĩa dưới đây.

---

## Kiến trúc áp dụng

Hệ thống áp dụng đồng thời 4 pattern, kết hợp với nhau theo tầng:

- **DDD (Domain-Driven Design)** — tổ chức code xoay quanh domain model
- **CQRS (Command Query Responsibility Segregation)** — tách biệt write (Command) và read (Query)
- **Hexagonal Architecture** — domain không phụ thuộc vào infrastructure
- **Vertical Slice Architecture** — trong `application/`, mỗi use case là 1 slice độc lập, tự chứa toàn bộ những gì cần thiết để thực thi

### Vertical Slice trong `application/`

Thay vì gom tất cả handlers vào 1 chỗ (horizontal), mỗi use case được tổ chức thành 1 **slice dọc** riêng. Các slice này thường được nhóm theo **Aggregate**, nhưng cũng có thể được nhóm theo **Feature** (nếu use case đó tổng hợp dữ liệu từ nhiều aggregate và không thuộc về riêng ai).

```
✅ Vertical Slice (ĐANG dùng):
application/
├── role/                       ← Aggregate Slice
│   ├── create/
│   │   ├── CreateRoleCommand
│   │   └── CreateRoleHandler
│   └── find_by_id/
│       ├── FindRoleByIdQuery
│       └── FindRoleByIdHandler
├── user/                       ← Aggregate Slice
│   └── register/
│       ├── RegisterUserCommand
│       └── RegisterUserHandler
└── iam_dashboard/              ← Feature Slice (Cross-aggregate)
    └── overview/
        ├── IamOverviewQuery
        └── IamOverviewHandler
```

**Lợi ích**: Mỗi slice độc lập — thêm/sửa/xoá 1 use case không ảnh hưởng slice khác.
**Quy tắc**: Slice không được import lẫn nhau. Nếu cần share logic → đưa vào `domain/` hoặc tạo shared service trong `application/shared/service/` (global) hoặc `application/{aggregate}/service/` (local).

---

## Entity vs Value Object

> **Lưu ý**: "Entity" ở đây là khái niệm DDD — object có identity trong domain model,
> **không bao gồm Aggregate Root** (Aggregate Root là tầng cao hơn, quản lý toàn bộ aggregate).
> Không nhầm với JPA `@Entity` — đó là infrastructure concern, đặt trong `persistence/`.

### Hierarchy trong domain model
```
Aggregate Root    ← entry point, bảo vệ invariants toàn aggregate
  └── Entity      ← có identity, owned by aggregate root
  └── Value Object← không có identity, immutable
```

### Entity
Có identity riêng bên trong aggregate, có thể thay đổi trạng thái theo thời gian.
**Không thể tồn tại độc lập** — phải thuộc về một Aggregate Root.

**Dấu hiệu nhận biết:**
- Cần phân biệt "cái này" với "cái kia" dù data giống nhau.
- Có vòng đời (lifecycle) bên trong aggregate — tạo, cập nhật, xóa.
- Được reference bởi ID từ nơi khác trong cùng aggregate.
```text
// Entity — có id, có lifecycle, owned by Aggregate Root
// Không access trực tiếp từ ngoài aggregate — phải qua Aggregate Root
Device { DeviceId, userId, fingerprint, status, lastSeenAt }
OAuthSession { SessionId, userId, deviceId, status }
```

### Value Object
Không có identity, bất biến (immutable), so sánh theo giá trị.

**Dấu hiệu nhận biết:**
- 2 object có cùng data thì là một — không cần phân biệt.
- Không thay đổi sau khi tạo — tạo mới thay vì update.
- Không có ý nghĩa khi tồn tại độc lập ngoài aggregate chứa nó.
```text
// Value Object — immutable, so sánh theo giá trị
Email { value }
DeviceFingerprint { clientHash, userAgent, compositeHash }
RoleId { value }   // reference đến aggregate khác — chỉ lưu ID, không lưu object
```

### Rule phân biệt nhanh
> "Cái nào?" → **Entity** — cần identity để phân biệt.  
> "Giá trị gì?" → **Value Object** — chỉ quan tâm đến nội dung.

**Lưu ý**: Immutable không đồng nghĩa với Value Object. Một object có thể immutable sau khi tạo nhưng vẫn là Entity nếu cần identity để phân biệt với object khác có cùng data.

## Cấu trúc package chuẩn

```
{base-package}.{service-name}/
├── application/            ← Use cases (Commands, Queries, Event Handlers)
│   ├── {aggregate_or_feature}/
│   │   ├── {use_case}/         ← Command / Query Handler
│   │   │   ├── {UseCase}Command / {UseCase}Query
│   │   │   ├── {UseCase}Handler
│   │   │   └── {UseCase}ApplicationService    ← Use-case Specific Application Service (mặc định)
│   │   ├── event/              ← Reactive orchestrators — trigger bởi domain event
│   │   │   └── {DomainEvent}Handler
│   │   └── service/            ← Local Application/Feature Services (shared logic within slice)
│   └── shared/                 ← Global shared application logic
│       └── service/            ← Global Application Services (shared across aggregates)
│
├── domain/                 ← Nghiệp vụ thuần túy, không phụ thuộc framework
│   ├── {aggregate}/
│   │   ├── {Aggregate}            (Aggregate Root)
│   │   ├── {Aggregate}Id          (Value Object — typed ID)
│   │   ├── {Aggregate}Repository  (interface — port)
│   │   ├── {Aggregate}ErrorCode   (enum)
│   │   ├── {Aggregate}Service     (Domain Service — logic không thuộc về 1 aggregate cụ thể)
│   │   ├── {Aggregate}Factory     (Factory class — chỉ khi cần, xem Factory convention)
│   │   ├── {SubEntity}            (Entity thuộc aggregate)
│   │   ├── {ValueObject}          (Value Object)
│   │   └── {DomainEvent}          (Domain Event)
│   └── {concept}/                 (Domain concept khác dùng bởi nhiều aggregate — xem Convention cho các domain concept khác)
│       ├── {Concept}              (Value Object)
│       └── {Concept}Service       (interface — port để resolve)
│
├── infrastructure/         ← Adapter — implement các port từ domain
│   ├── api/                ← Outbound inter-service clients — tổ chức theo protocol
│   │   ├── http/           ← HTTP/REST (Feign hoặc tương tự)
│   │   │   ├── internal/   ← MS khác trong cùng hệ thống
│   │   │   │   └── {service}/
│   │   │   │       ├── {Service}Client        (Feign interface — 1 per target service)
│   │   │   │       └── dto/
│   │   │   │           └── {Concept}Response
│   │   │   └── external/   ← Third-party provider
│   │   │       └── {provider}/
│   │   │           ├── {Provider}Client       (Feign interface hoặc SDK wrapper)
│   │   │           └── dto/
│   │   │               └── {Concept}Response
│   │   └── grpc/           ← gRPC (generated stubs từ .proto)
│   │       ├── internal/
│   │       │   └── {service}/
│   │       │       └── {Service}GrpcClient    (wrapper quanh generated stub)
│   │       └── external/
│   │           └── {provider}/
│   │               └── {Provider}GrpcClient
│   │
│   ├── adapter/            ← implements tất cả Ports — điểm kết nối duy nhất với domain & application
│   │   ├── repository/     ← implements domain Repository ports (write side)
│   │   │   └── {aggregate}/
│   │   │       └── {Aggregate}PersistenceAdapter
│   │   ├── query/          ← implements Query ports (read side, bypass domain)
│   │   │   └── {aggregate}/
│   │   │       └── {Aggregate}QueryService
│   │   └── service/        ← implements Domain Service ports (device, notification...)
│   │       └── {concern}/
│   │           └── {Provider}Adapter
│   │
│   ├── persistence/        ← ORM write side — JPA detail thuần túy, không implement Port
│   │   └── {aggregate}/
│   │       ├── {Aggregate}JpaEntity       (ORM mapping — không expose ra ngoài infrastructure)
│   │       ├── {Aggregate}JpaRepository   (Spring Data interface)
│   │       └── {Aggregate}Mapper          (domain ↔ JpaEntity)
│   │
│   ├── readstore/          ← ORM read side — optional, chỉ cần khi read DB khác loại write DB
│   │   ├── {concern}/      (ví dụ: elasticsearch/, mongodb/, redis/)
│   │   │   ├── {Aggregate}ReadDocument/Entity   (schema read store — không phải JpaEntity)
│   │   │   ├── {Aggregate}ReadRepository        (Spring Data cho read store)
│   │   │   └── {Aggregate}ReadMapper            (ReadDocument ↔ ReadModel DTO)
│   │   └── config/
│   │       └── ReadStoreConfig
│   │
│   ├── pipeline/           ← sync write → read — độc lập hoàn toàn, không implement Port
│   │   ├── {aggregate}/
│   │   │   ├── {Aggregate}PipelineConsumer    (nhận event từ Debezium / Kafka)
│   │   │   ├── {Aggregate}PipelineMapper      (payload → ReadDocument)
│   │   │   └── {Aggregate}ReadModelProjector  (upsert vào readstore/)
│   │   └── config/
│   │       └── PipelineConfig
│   │
│   ├── security/           ← Spring Security, OAuth2
│   │   ├── oauth2/
│   │   ├── handler/
│   │   ├── service/
│   │   ├── model/
│   │   ├── key/
│   │   └── SecurityConfiguration
│   │
│   ├── messaging/          ← Kafka, RabbitMQ — optional
│   ├── scheduling/         ← @Scheduled jobs — optional
│   └── cross-cutting/      ← không implement Port cụ thể nào
│       ├── config/
│       └── utils/
│
├── presentation/           ← Entry points — nhận request từ bên ngoài, không chứa business logic
│   ├── base/               ← Shared presentation infrastructure
│   │   └── ErrorResponse
│   ├── {aggregate}/        ← 1 package per aggregate
│   │   ├── model/          ← Request/Response DTOs của aggregate này
│   │   │   └── {Action}{Aggregate}Request
│   │   └── {Aggregate}Controller
│   └── internal/           ← Service-to-service endpoints (không expose ra ngoài)
│       ├── model/
│       │   └── {Action}Request
│       └── Internal{Aggregate}Controller
│
└── {ServiceName}Application    ← root package — @SpringBootApplication, ngang hàng với các layer
```

---

## Quy tắc từng layer

### `domain/` — Trái tim của service

- **Không import** bất cứ thứ gì từ `application`, `infrastructure`, `presentation`
- **Không import** Spring framework (`@Component`, `@Service`, v.v.)
- **Không import** JPA (`@Entity`, `@Column`, v.v.)
- Repository là **interface** — không biết persistence technology là gì
- Aggregate Root chịu trách nhiệm bảo vệ invariants, không để logic leak ra ngoài
- Domain Event đặt trong package của aggregate phát ra event

```
✅ domain/user/User.java           import domain/user/UserId
✅ domain/user/User.java           import domain/user/UserStatus
❌ domain/user/User.java           import infrastructure/persistence/...
❌ domain/user/User.java           import org.springframework...
```

→ Convention chi tiết về `ErrorCode`, `DomainException`, cách throw: [`error-handling.md`](./error-handling.md)

---

## Factory convention

Factory kiểm soát việc tạo aggregate — đảm bảo object luôn ở trạng thái hợp lệ ngay từ đầu.

### Quy tắc aggregate boundary — bắt buộc

**Không bao giờ truyền aggregate khác vào factory method.** Chỉ truyền Value Object hoặc typed Id:

```java
// ✅ Chỉ truyền RoleId — User aggregate không biết Role aggregate tồn tại
public static User register(String username, Email email,
                            UserPassword password, Set<RoleId> roleIds) {}

// ❌ Truyền Role aggregate — vi phạm aggregate boundary
public static User register(String username, Email email,
                            UserPassword password, Set<Role> roles) {}
```

### Khi nào dùng static factory method trong Aggregate Root

Trường hợp mặc định — dùng khi tạo object đơn giản, không cần dependency bên ngoài:

```java
// domain/user/User.java
public class User extends AggregateRoot {

    // Tạo mới — generate Id trong domain, không phụ thuộc DB
    public static User register(String username, Email email,
                                UserPassword password, Set<RoleId> roleIds) {
        User user = new User(UserId.generate(), username, email, password, roleIds);
        user.registerEvent(new UserRegisteredEvent(user.getId(), email, roleIds));
        return user;
    }

    // Reconstitute từ persistence — dùng Id đã có, không fire event
    static User reconstitute(UserId id, String username, Email email,
                             UserPassword password, Set<RoleId> roleIds) {
        return new User(id, username, email, password, roleIds);
    }

    private User() {}  // constructor private — bắt buộc đi qua factory
}
```

**Factory method trong Aggregate Root cũng dùng để tạo child entity** — khi child không có ý nghĩa ngoài aggregate:

```java
// domain/calendar/Calendar.java
public CalendarEntry scheduleEntry(CalendarEntryId entryId, String description,
                                   Owner owner, DateRange timeSpan) {
    // Calendar tự truyền calendarId của mình vào — caller không cần biết
    return new CalendarEntry(this.id, entryId, description, owner, timeSpan);
}
```

### Khi nào tách Factory class riêng

Tách ra `{Aggregate}Factory` trong cùng package `domain/{aggregate}/` khi:

**1. Nhiều cách tạo với logic khác nhau:**

```java
// domain/user/UserFactory.java
public class UserFactory {

    public static User createDefault(String username, Email email,
                                     UserPassword password, Set<RoleId> roleIds) {
        User user = new User(UserId.generate(), username, email, password, roleIds);
        user.registerEvent(new UserRegisteredEvent(RegistrationMethod.DEFAULT));
        return user;
    }

    public static User createFromSocial(Email email, String fullName,
                                        String providerId, Set<RoleId> roleIds) {
        User user = new User(UserId.generate(), null, email, fullName, null, roleIds);
        user.registerEvent(new UserRegisteredEvent(RegistrationMethod.SOCIAL));
        return user;
    }
}
```

**2. Tạo từ Domain Event của aggregate khác hoặc từ các domain concept khác:**

```java
// domain/task-history/TaskHistory.java
public class TaskHistory extends AggregateRoot {

    // Nhận Domain Event — không nhận Task aggregate
    public static TaskHistory from(TaskUpdatedEvent event) {
        return new TaskHistory(
            TaskHistoryId.generate(),
            event.taskId(),
            event.title(),
            event.status(),
            event.occurredAt()
        );
    }
}
```

**Factory class không được inject Repository hay external service** — nếu cần thì đó là Domain Service, không phải Factory.

---

## Convention cho các domain concept khác

Một số concept trong domain không phải aggregate của BC này nhưng vẫn có ý nghĩa nghiệp vụ rõ ràng — ví dụ `Assignee`, `Reporter`. Chúng được resolve từ BC khác (Identity BC) nhưng domain không quan tâm điều đó — chỉ biết "trong domain này tồn tại khái niệm Assignee".

### Quy tắc phân loại — quan trọng nhất

**Câu hỏi để quyết định**: *Concept này có được dùng bởi nhiều hơn 1 aggregate, hoặc có tiềm năng như vậy không?*

```
Không — chỉ 1 aggregate dùng, không có tiềm năng mở rộng
→ Value Object thông thường, đặt trong package của aggregate đó

Có — nhiều aggregate dùng, hoặc có tiềm năng
→ Domain concept riêng, có package riêng ngang hàng với aggregate
```

```java
// ✅ Chỉ 1 aggregate dùng → Value Object trong package aggregate
// domain/ticket/Assignee.java
public record Assignee(UserId userId, String fullName) {}

// ✅ Nhiều aggregate dùng → Domain concept riêng
// domain/assignee/Assignee.java      (Value Object)
// domain/assignee/AssigneeService.java  (interface — port)
```

### Cấu trúc package

Mỗi concept là 1 package riêng ngang hàng với aggregate, **không gộp chung vào 1 package wrapper**. Tên package = tên concept trong Ubiquitous Language của BC:

```
domain/
├── ticket/
│   ├── Ticket.java
│   └── ...
├── assignee/               ← domain concept riêng
│   ├── Assignee.java       (Value Object — không có lifecycle trong BC này)
│   └── AssigneeService.java  (interface — port để resolve)
└── reporter/               ← domain concept riêng
    ├── Reporter.java
    └── ReporterService.java
```

Không đặt tên package theo nguồn gốc (`external/`) hay scope sử dụng (`shared/`) — tên phải phản ánh **ý nghĩa nghiệp vụ** trong BC này.

### Service interface — resolve từ BC khác

Mỗi concept có Service interface riêng trong cùng package, implement trong `infrastructure/adapter/service/`:

```java
// domain/assignee/AssigneeService.java
public interface AssigneeService {
    Assignee assigneeOf(UserId userId);
}

// infrastructure/adapter/service/assignee/AssigneeServiceAdapter.java
// → implement bằng cách gọi Identity BC qua HTTP hoặc đọc từ local projection
```

Application Handler inject interface — không biết cơ chế resolve bên dưới:

```java
public Result handle(AssignTicketCommand command) {
    Ticket ticket = ticketRepository.findById(command.ticketId())
            .orElseThrow(TicketException::notFound);
    Assignee assignee = assigneeService.assigneeOf(command.assigneeId());
    ticket.assignTo(assignee);
    ticketRepository.save(ticket);
    eventDispatcher.dispatchAll(ticket.pullEvents());
    return new Result(ticket.getId().getValue());
}
```

---

### `application/` — Orchestration

Tất cả handler trong layer này đều là **orchestrator** — phối hợp domain objects, repository, port, và command khác. Không chứa business rule.

Ba loại orchestrator, phân biệt theo **ai trigger**:

| Loại                | Trigger                            | Folder                               |
|---------------------|------------------------------------|--------------------------------------|
| **Command Handler** | External intent (user/API) — write | `{aggregate_or_feature}/{use_case}/` |
| **Query Handler**   | External intent (user/API) — read  | `{aggregate_or_feature}/{use_case}/` |
| **Event Handler**   | Internal domain state change       | `{aggregate_or_feature}/event/`      |

- Handler chỉ được dùng domain objects và repository interfaces từ `domain/`
- Handler **không** gọi thẳng JpaRepository — chỉ gọi qua domain Repository interface

```
✅ RegisterUserHandler     inject UserRepository (domain interface)
❌ RegisterUserHandler     inject UserJpaRepository (infrastructure)
```

**Naming convention:**

| Loại                | Pattern                    | Ví dụ               |
|---------------------|----------------------------|---------------------|
| Write use case file | `{Action}{Aggregate}.java` | `CreateRole.java`   |
| Read use case file  | `{Action}{Aggregate}.java` | `FindRoleById.java` |
| Folder              | `snake_case`               | `find_by_id/`       |

---

### Aggregate Slice vs Feature Slice

| Loại                | Khi nào dùng                                                                                                             | Package ví dụ                         |
|---------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| **Aggregate Slice** | Use case tác động trực tiếp hoặc xoay quanh 1 Aggregate cụ thể.                                                          | `application/user/register/`          |
| **Feature Slice**   | Use case tổng hợp dữ liệu từ nhiều aggregate (Dashboard, Reports) hoặc logic cross-cutting không thuộc về aggregate nào. | `application/iam_dashboard/overview/` |

---

**Single file convention:**

Mỗi use case gom tất cả vào **1 file duy nhất** — Command/Query, Handler, Mapper, Result đều là inner class:

```java
// create/CreateRole.java
public class CreateRole {

    public record Command(String name, String description) {}  // input

    public record Result(UUID id) {}                           // output

    static class Mapper {                                      // mapping
        static Result toResult(Role role) {
            return new Result(role.getId().getValue());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Handler implements CommandHandler<Command> {
        private final RoleRepository roleRepository;
        private final EventDispatcher eventDispatcher;

        @Override
        public Result handle(Command command) {}
    }
}
```

Tách ra file riêng khi Handler có nhiều dependency hoặc Mapper có logic phức tạp hoặc code quá dài.

**Mapper convention:**
- Mỗi use case có Mapper riêng — không share Mapper giữa các use case
- Mapper không inject Spring bean — pure static methods
- Không để mapping logic trong Handler

---

## Domain logic placement

Câu hỏi để phân loại bất kỳ logic nào: *"Logic này bảo vệ invariant của aggregate nào? Hay nó phối hợp nhiều thứ?"*

### Aggregate method — logic bảo vệ invariant của chính aggregate

```text
// ✅ Aggregate method — Ticket tự bảo vệ rule của mình
public void assign(Assignee assignee) {
    if (this.status != TicketStatus.OPEN) {
        throw TicketException.cannotAssignClosedTicket();
    }
    this.assignee = assignee;
    registerEvent(new TicketAssignedEvent(this.id, assignee));
}

// ❌ Không đặt vào Application Handler — đây là business rule, không phải orchestration
if (ticket.getStatus() != TicketStatus.OPEN) { 
    throw ex; 
} else {
    ticket.setAssignee(assignee);
}
                
```

### Domain Service — logic liên quan nhiều aggregate hoặc cần external port

```text
// ✅ Domain Service — rule liên quan cả Ticket lẫn Assignee
public class TicketAssignmentPolicy {
    public void validate(Ticket ticket, Assignee assignee) {
        // "Assignee chỉ được nhận ticket nếu workload hiện tại < 10"
        int current = ticketRepository.countOpenByAssignee(assignee.userId());
        if (current >= 10) throw TicketException.assigneeOverloaded();
    }
}

// ✅ Domain Service — cần Repository để validate, không thể đặt trong aggregate
public class UserDomainService {
    public void validateEmailUnique(Email email) {
        if (userRepository.existsByEmail(email)) throw UserException.alreadyExists();
    }
}

// ❌ Không dùng Domain Service cho orchestration đơn giản
// → để Application Handler tự điều phối
```

### Application Handler — orchestration, không chứa business rule

Command Handler và Query Handler điều phối theo **explicit intent** từ bên ngoài.
Event Handler điều phối theo **domain state change** từ bên trong — bản chất là reactive orchestrator, logic bên trong giống hệt Command Handler: inject port, gọi domain, không chứa business rule.

```text
// ✅ Handler điều phối: load → validate → call aggregate → save → dispatch
public Result handle(AssignTicketCommand command) {
    Ticket ticket = ticketRepository.findById(command.ticketId())
            .orElseThrow(TicketException::notFound);

    Assignee assignee = collaboratorService.assigneeOf(command.assigneeId()); // resolve

    assignmentPolicy.validate(ticket, assignee); // Domain Service nếu có rule phức tạp

    ticket.assign(assignee);                     // business rule trong aggregate
    ticketRepository.save(ticket);
    eventDispatcher.dispatchAll(ticket.pullEvents());
    return new Result(ticket.getId().getValue());
}

// ❌ Business rule trong Handler
if (ticket.getStatus() != TicketStatus.OPEN) { throw ...; } // → vào aggregate method
        if (workload >= 10) { throw ...; }                           // → vào Domain Service
```

**Bảng quyết định nhanh:**

| Logic                                     | Đặt ở đâu                                |
|-------------------------------------------|------------------------------------------|
| Bảo vệ invariant của 1 aggregate          | Aggregate method                         |
| Liên quan nhiều aggregate                 | Domain Service                           |
| Cần Repository để validate                | Domain Service                           |
| Cần resolve entity từ BC khác             | `CollaboratorService` (domain interface) |
| Check tồn tại đơn thuần trước khi tạo     | Application Handler tự điều phối         |
| Load → call → save → dispatch             | Application Handler                      |
| Orchestration logic dùng bởi nhiều slices | Application Service                      |

---

## Application Service (Application Port) convention

Application Service (Application Port) là nơi chứa **orchestration logic** phức tạp mà không thuộc về business rule thuần túy (Domain Service), hoặc logic được chia sẻ giữa nhiều use cases (slices) trong cùng một aggregate để tránh duplication.

### Khi nào tách ra Application Service thay vì Domain Service?

Quy tắc phân biệt dựa trên **"What" vs "How"**:

- **Domain Service ("What")**: Chứa **quy tắc nghiệp vụ** (business rules/policies). Nó trả lời cho câu hỏi: *"Nghiệp vụ yêu cầu gì?"*.
    - Ví dụ: `validateEmailUnique`, `calculateTax`, `checkUserEligibility`.
- **Application Service ("How")**: Chứa **quy trình thực hiện** (orchestration/technical steps). Nó trả lời cho câu hỏi: *"Làm thế nào để thực hiện quy trình này?"*.
    - Thường phối hợp giữa domain objects và các outbound ports (gửi mail, upload file, call external API không mang tính nghiệp vụ).
    - Ví dụ: `ImageUploadService`, `ExcelExportService`, `NotificationService`.

**Ví dụ thực tế:**
- Việc kiểm tra xem user có đủ điều kiện đăng ký không là **Domain Service**.
- Việc phối hợp: tạo user -> upload avatar lên S3 -> gửi email chào mừng là **Application Service**.

### Phân loại theo Scope

| Loại                           | Vị trí                                           | Khi nào dùng                                             |
|--------------------------------|--------------------------------------------------|----------------------------------------------------------|
| **Use-case Specific**          | `application/{aggregate_or_feature}/{use_case}/` | Logic orchestration chỉ phục vụ cho đúng 1 use case này. |
| **Local Application Service**  | `application/{aggregate_or_feature}/service/`    | Share logic giữa các use case trong **cùng 1 slice**.    |
| **Global Application Service** | `application/shared/service/`                    | Share logic xuyên suốt **nhiều slice** khác nhau.        |

**Quy tắc tiến hoá (Refactoring Path):**
1.  **Mặc định**: Khi bắt đầu triển khai, cứ đặt Application Service trực tiếp trong package của use case (`Use-case Specific`).
2.  **Tách Local**: Nếu thấy 2 use case trong cùng 1 slice có logic giống nhau → đưa vào `application/{aggregate_or_feature}/service/`.
3.  **Tách Global**: Nếu thấy logic đó có thể dùng chung cho toàn bộ service (ví dụ: `EmailService`, `CloudStorageService`) → đưa vào `application/shared/service/`.

---

## Domain Service convention

Dùng khi logic nghiệp vụ **không thuộc về một aggregate cụ thể** — thường là logic liên quan đến nhiều aggregate hoặc cần collaborator bên ngoài (như repository).

**Naming**: `{Aggregate}Service` hoặc `{Action}Service` nếu cross-aggregate.

```
✅ Dùng Domain Service khi:
- Logic cần phối hợp nhiều aggregate (ví dụ: kiểm tra User + Role khi assign)
- Logic cần gọi Repository để validate (ví dụ: check email unique trước khi tạo User)
- Logic không tự nhiên fit vào method nào của Aggregate Root

❌ Không dùng Domain Service khi:
- Logic chỉ liên quan đến 1 aggregate → đặt vào Aggregate Root
- Logic là orchestration của use case → đặt vào Application Handler
```

Ví dụ:

```java
// domain/user/UserService.java
public class UserService {
    private final UserRepository userRepository;

    // Logic cần repository — không thể đặt trong User aggregate
    public void validateEmailUnique(Email email) {
        if (userRepository.existsByEmail(email)) {
            throw UserException.alreadyExists();
        }
    }
}
```

**Quan trọng**: Domain Service vẫn thuộc `domain/` — không được import Spring, JPA, hay infrastructure.
Domain Service được inject vào Application Handler, không inject vào Aggregate Root.

---

## Application Handler convention

Handler là điểm điều phối duy nhất của một use case — không chứa business rule, không biết HTTP hay messaging.

**Được làm:**
- Load aggregate từ Repository
- Gọi `CollaboratorService` để resolve external concept
- Gọi Domain Service nếu có rule phức tạp
- Gọi aggregate method để thực thi business rule
- Save aggregate
- Dispatch domain event sau khi persist

**Không được làm:**
- Chứa business rule — phải đẩy vào aggregate method hoặc Domain Service
- Gọi trực tiếp `JpaRepository` — chỉ qua domain `Repository` interface
- Truyền aggregate sang aggregate khác — chỉ truyền `Id` hoặc Value Object
- Dispatch event trước khi persist

```text
// ✅ Handler chuẩn
public Result handle(UpdateTaskCommand command) {
    // 1. Load
    Task task = taskRepository.findById(TaskId.of(command.taskId()))
            .orElseThrow(TaskException::notFound);

    // 2. Gọi aggregate method — business rule nằm trong Task
    task.update(command.title(), command.description(), command.status());

    // 3. Save trước
    taskRepository.save(task);

    // 4. Dispatch sau khi persist
    task.pullEvents().forEach(event -> {
        if (event instanceof TaskUpdatedEvent e) {
            TaskHistory history = TaskHistory.from(e); // factory nhận event, không nhận aggregate
            taskHistoryRepository.save(history);
        }
    });
    eventDispatcher.dispatchAll(task.pullEvents());

    return new Result(task.getId().getValue());
}

// ❌ Handler sai — business rule rò rỉ ra khỏi aggregate
public Result handle(UpdateTaskCommand command) {
    Task task = taskRepository.findById(...);
    if (task.getStatus() == TaskStatus.CLOSED) { throw ...; } // → vào Task.update()
    task.setTitle(command.title());                            // → setter không bảo vệ invariant
    taskRepository.save(task);
}
```

---

### `infrastructure/` — Adapter

**Nguyên tắc cốt lõi**: Domain định nghĩa `what` nó cần — infrastructure quyết định `how` để làm điều đó. Câu hỏi để phân loại bất kỳ class nào vào infrastructure: *"Class này implement Port nào? Port đó thuộc concern gì?"*
**Single responsibility trong infrastructure**: Mỗi class chỉ làm đúng việc của nó — không gọi sang class khác cùng tầng trong cùng concern. Ví dụ: `Mapper` chỉ map, không gọi `JpaRepository`; `PersistenceAdapter` orchestrate `Mapper` và `JpaRepository`, không chứa mapping logic.

#### `api/` — Outbound Inter-service Clients

Package chứa tất cả outbound client khi service cần gọi sang service khác. Tổ chức theo **protocol** ở tầng trên, rồi **internal/external** ở tầng dưới — không tổ chức theo domain concept.

> `messaging/` đứng ngoài `api/` vì nó là async event-driven (fire-and-forget), không phải request-response. `api/` chỉ chứa request-response clients (HTTP, gRPC).

**Phân biệt internal vs external:**

|                 | `internal/`                   | `external/`          |
|-----------------|-------------------------------|----------------------|
| Contract owner  | Chính hệ thống                | Vendor/third-party   |
| Auth            | Internal service token        | API key, OAuth riêng |
| DTO naming      | Mirror domain của service kia | Mirror schema vendor |
| Circuit breaker | Tuỳ chọn                      | Thường bắt buộc      |

**Quy tắc chung:**
- 1 client per target service — không tách thành nhiều client cho cùng 1 service
- DTO trong `api/` là **transport contract** — không được import vào `domain/` hay `application/`
- Mapping DTO → domain Value Object xảy ra tại `adapter/service/`, không tại client

**HTTP (`api/http/`):**

```text
// ✅ 1 Feign client gom tất cả endpoint của admin-service
@FeignClient(name = "admin-service", url = "${services.admin.uri}")
public interface AdminServiceClient {
    @GetMapping("/api/admin/v1/users/{id}")
    UserProfileResponse getUserById(@PathVariable String id);

    @GetMapping("/api/admin/v1/users/{id}/roles")
    UserRolesResponse getRolesByUserId(@PathVariable String id);
}

// ❌ Tách ra nhiều client cho cùng service — redundant config
@FeignClient(name = "admin-user-profile", url = "...") public interface UserProfileClient { ... }
@FeignClient(name = "admin-user-roles",   url = "...") public interface UserRolesClient   { ... }
```

**gRPC (`api/grpc/`):**

```java
// Wrapper quanh generated stub — ẩn protobuf detail khỏi adapter
public class PaymentGrpcClient {
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;

    public ChargeResponse charge(ChargeRequest request) {
        return stub.charge(request); // protobuf types — chỉ tồn tại trong api/grpc/
    }
}
```

**Adapter tiêu thụ client — pattern giống nhau bất kể protocol:**

```java
// adapter/service/user_profile/UserProfileAdapter.java
public class UserProfileAdapter implements UserProfilePort {
    private final AdminServiceClient httpClient;         // HTTP
    // hoặc private final PaymentGrpcClient grpcClient; // gRPC

    public UserProfile findById(String userId) {
        UserProfileResponse dto = httpClient.getUserById(userId); // transport DTO
        return new UserProfile(dto.getId(), dto.getFullName());   // domain VO
    }
}
```

```
✅ AdminServiceClient        infrastructure/api/http/internal/admin/
✅ UserProfileResponse       infrastructure/api/http/internal/admin/dto/
✅ PaymentGrpcClient         infrastructure/api/grpc/external/payment/
✅ UserProfileAdapter        infrastructure/adapter/service/user_profile/
❌ UserProfileResponse       domain/                → transport DTO không lên domain
❌ AdminServiceClient        adapter/service/       → client không đặt trong adapter/
```

---

#### `adapter/` — implements tất cả Ports

Package duy nhất được phép implement interfaces từ `domain/` và `application/`. Chia 3 sub-package theo loại port:

| Sub-package               | Implements port từ                         | Bản chất                                        |
|---------------------------|--------------------------------------------|-------------------------------------------------|
| `repository/{aggregate}/` | `domain/{aggregate}/{Aggregate}Repository` | Write side — đi qua domain model                |
| `query/{aggregate}/`      | `application/{aggregate}/{use_case}/`      | Read side — bypass domain, trả thẳng ReadModel  |
| `service/{concern}/`      | `domain/{concern}/{Capability}`            | Domain capability — device, parser, notifier... |

```
✅ UserPersistenceAdapter    implements domain/user/UserRepository
✅ UserQueryService          implements Query port từ application/
✅ YauaaDeviceAdapter        implements domain/device/DeviceNameDetector
❌ UserPersistenceAdapter    inject UserJpaRepository trực tiếp vào Handler
```

#### `persistence/` — ORM write side

JPA detail thuần túy. Không implement Port — chỉ cung cấp building blocks cho `adapter/repository/`.

- `{Aggregate}JpaEntity` — ORM entity, **không được expose** ra ngoài infrastructure
- `{Aggregate}JpaRepository` — Spring Data interface
- `{Aggregate}Mapper` — chuyển đổi domain object ↔ JpaEntity, **không dùng** MapStruct nếu logic mapping có nghiệp vụ

#### `readstore/` — ORM read side *(optional)*

Đối xứng với `persistence/` cho read store. **Chỉ cần khi read DB khác loại với write DB** (Elasticsearch, MongoDB, Redis...). Khi dùng native query thì bỏ qua package này — `adapter/query/` dùng thẳng `EntityManager` hoặc `JdbcTemplate`.

- `{concern}/` — ví dụ: `elasticsearch/`, `mongodb/`, `redis/`
- `{Aggregate}ReadDocument` — schema read store, không phải JpaEntity
- `{Aggregate}ReadRepository` — Spring Data cho read store
- `{Aggregate}ReadMapper` — ReadDocument ↔ ReadModel DTO

#### `pipeline/` — sync write → read *(optional)*

Sync pipeline từ write DB sang read store. Hoàn toàn độc lập — không implement Port nào, không gọi domain, không biết business logic tồn tại. Cơ chế có thể thay đổi (CDC, event-driven, dual-write) mà không ảnh hưởng các package khác.

- `{Aggregate}PipelineConsumer` — nhận event từ Debezium / Kafka
- `{Aggregate}PipelineMapper` — raw payload → ReadDocument
- `{Aggregate}ReadModelProjector` — upsert vào readstore/

#### `security/` — Spring Security, OAuth2

Không implement domain Port — là Spring Security concern riêng. Cấu trúc nội bộ: `oauth2/`, `handler/`, `service/`, `model/`, `key/`, và `SecurityConfiguration` ở top-level để wire toàn bộ.

#### `messaging/`, `scheduling/` *(optional)*

Thêm khi cần. `messaging/` cho Kafka/RabbitMQ producer-consumer. `scheduling/` cho `@Scheduled` jobs và batch tasks.

#### `cross-cutting/`

Không implement Port cụ thể nào. Chứa `@Bean` config không thuộc concern nào (`config/`) và utility class thuần túy (`utils/`).

```
✅ EventDispatcherConfig    infrastructure/cross-cutting/config/
✅ IpAddressExtractor       infrastructure/cross-cutting/utils/
❌ UserAgentParserConfig    infrastructure/cross-cutting/config/   → phải nằm trong adapter/service/device/
```

---

### `presentation/` — Entry Point

- REST Controller nhận request, delegate ngay cho application layer
- Controller **không chứa business logic**
- Controller **không** gọi trực tiếp domain objects hay infrastructure

```
✅ UserController    inject + call RegisterUserHandler
❌ UserController    inject UserRepository để query trực tiếp
```

---

## Event Handler convention

Event Handler là **reactive orchestrator**: được trigger bởi domain event thay vì external intent, nhưng bản chất orchestration giống Command Handler — inject port, gọi domain hoặc command khác, không chứa business rule.

Event handler phù hợp cho cross-aggregate reaction: `DeviceRevokedEvent` → revoke tất cả session của thiết bị → ghi activity. Đây là nơi duy nhất được phép điều phối sang aggregate khác mà không cần thông qua presentation layer.

`DomainEvent`, `EventHandler<E>`, `EventDispatcher` được định nghĩa trong `libs/common` —
các service chỉ import và implement, không tự định nghĩa lại.

**Phân tách trách nhiệm:**

| Thành phần                                       | Layer                                  | Lý do                                                        |
|--------------------------------------------------|----------------------------------------|--------------------------------------------------------------|
| `EventDispatcher`, `EventHandler`, `DomainEvent` | `libs/common`                          | Cơ chế dispatch — dùng chung toàn hệ thống                   |
| `EventDispatcherConfig`                          | `infrastructure/cross-cutting/config/` | Chỉ wire Spring bean — không chứa logic                      |
| `{Aggregate}EventHandler`                        | `application/{aggregate}/event/`       | Reactive orchestrator — cùng layer với Command/Query Handler |

Service không cần quan tâm internal của `EventDispatcher` — chỉ cần implement handler và gọi `dispatchAll`.

**Cấu trúc:**

```
infrastructure/
└── cross-cutting/
    └── config/
        └── EventDispatcherConfig.java    ← chỉ @Bean config

application/
└── user/
    ├── register/
    │   ├── RegisterUserCommand
    │   └── RegisterUserHandler
    └── event/
        ├── UserCreatedHandler.java
        ├── UserLockedHandler.java
        └── SocialConnectedHandler.java
```

**`EventDispatcherConfig` — chỉ wire, không có gì khác:**

```java
// infrastructure/cross-cutting/config/EventDispatcherConfig.java
@Configuration
public class EventDispatcherConfig {
    @Bean
    public EventDispatcher eventDispatcher(List<EventHandler<?>> handlers) {
        EventDispatcher dispatcher = new EventDispatcher();
        dispatcher.registerAll(handlers);
        return dispatcher;
    }
}
```

**Handler implement thẳng `EventHandler<E>`, không tạo thêm interface trung gian:**

```java
// application/user/event/UserCreatedHandler.java
@Component
public class UserCreatedHandler implements EventHandler<UserCreatedEvent> {

    @Override
    public void handle(UserCreatedEvent event) {}

    @Override
    public Class<UserCreatedEvent> getEventType() {
        return UserCreatedEvent.class;
    }

    // override nếu cần thay đổi thứ tự thực thi, giữ default nếu không
    @Override
    public int getOrder() {
        return 100;
    }
}

// ❌ Over-engineering — không tạo thêm interface trung gian
public interface UserCreatedHandler extends EventHandler<UserCreatedEvent> {}
public class UserCreatedHandlerImpl implements UserCreatedHandler {}
```

**Application Handler gọi `dispatchAll` sau khi persist:**

```java
// application/user/register/RegisterUserHandler.java
public class RegisterUserHandler {
    private final UserRepository userRepository;
    private final EventDispatcher eventDispatcher;

    public void handle(RegisterUserCommand command) {
        User user = User.register();
        userRepository.save(user);
        eventDispatcher.dispatchAll(user.pullEvents()); // dispatch sau khi persist
    }
}
```

**Cross-aggregate orchestration trong Event Handler:**

```java
// application/device/event/DeviceRevokedHandler.java
@Component
@RequiredArgsConstructor
public class DeviceRevokedHandler implements EventHandler<DeviceRevokedEvent> {

    // inject Command của aggregate khác — đây là cross-aggregate orchestration
    private final RevokeSession revokeSession;
    private final RecordLoginActivity recordActivity;

    @Override
    public void handle(DeviceRevokedEvent event) {
        revokeSession.handle(new RevokeSession.Command(event.deviceId()));
        recordActivity.handle(new RecordLoginActivity.Command(event.userId(), LoginResult.REVOKED));
    }

    @Override
    public Class<DeviceRevokedEvent> getEventType() { return DeviceRevokedEvent.class; }
}
```

**Quy tắc:**
- Dispatch **sau khi** persist — không dispatch trước khi save thành công
- Handler trong `application/` được phép inject repository, external service, Command của aggregate khác
- Handler **không** được gọi trực tiếp từ domain — chỉ qua `EventDispatcher`
- **Không** tự implement `EventDispatcher` trong service — dùng từ `libs/common`

---

## Domain Event convention

- Đặt trong package của aggregate phát ra event
- Naming: `{Aggregate}{Action}Event`
- Ví dụ: `UserCreatedEvent`, `UserLockedEvent`, `SocialConnectedEvent`

---

## Input / Output model convention

Mỗi Command/Query có input và output model **hoàn toàn độc lập** — không share model giữa các use case dù data trông giống nhau.

```
application/role/
├── create/
│   ├── CreateRoleCommand       ← input
│   └── CreateRoleResult        ← output
├── find_by_id/
│   ├── FindRoleByIdQuery       ← input
│   └── RoleDetail              ← output
└── find_all/
    ├── FindAllRolesQuery       ← input (filter, pagination params)
    └── RoleSummary             ← output (chỉ fields cần thiết cho list)
```

**Quy tắc:**
- Command output (`Result`) — chỉ trả về những gì caller cần, thường là ID hoặc trạng thái mới
- Query output — tối ưu cho đúng use case, không fetch thừa data
- Không dùng domain aggregate làm output của query
- Không dùng chung model giữa 2 use case dù fields giống nhau — chúng sẽ diverge theo thời gian

```java
// ✅ Mỗi use case model riêng
public record CreateRoleResult(UUID id) {}
public record RoleDetail(UUID id, String name, String description, Long createdAt) {}
public record RoleSummary(UUID id, String name) {}

// ❌ Dùng chung 1 model cho nhiều use case
public record RoleResponse(UUID id, String name, String description, Long createdAt) {}
// → FindAll dùng RoleResponse nhưng không cần description, createdAt
// → Create trả về RoleResponse nhưng caller chỉ cần id
```

## ID convention

Domain luôn dùng **UUID** làm identifier — tạo ngay khi khởi tạo aggregate, không phụ thuộc DB.

```java
// Domain — chỉ biết UUID
public class Role extends AbstractAggregateRoot<RoleId> {
    private final RoleId id; // UUID
}

// JpaEntity mặc định — UUID là PK
@Entity
public class RoleJpaEntity {
    @Id
    private UUID id;
}
```

**`seq` là opt-in** — chỉ thêm khi được chỉ định rõ trong mô tả domain (ví dụ: *"dùng seq để hỗ trợ sort/index"*). Mặc định không dùng.

Khi được chỉ định dùng `seq`:
```java
// JpaEntity có seq — vẫn không expose ra ngoài infrastructure
@Entity
public class RoleJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;               // internal, chỉ dùng để query/sort

    @Column(unique = true, nullable = false)
    private UUID id;                // map sang RoleId trong domain
}
```

**Quy tắc:**
- UUID được generate trong domain khi tạo aggregate, không để DB generate
- `seq` không được expose ra API response
- `seq` không được dùng làm reference giữa các service
- Khi find by ID từ API → query theo UUID, không query theo `seq`

# Khi truyền ID qua API sang service khác
- Serialize thành String (UUID string format)
- Service nhận tự wrap vào typed ID của mình
- Không nên share Id class giữa các service trừ khi chúng thuộc shared kernel

```java
// ✅ Dùng typed ID
public class UserId {
    private final UUID value;
}

// ❌ Không dùng raw UUID/Long trực tiếp trong domain
public class User {
    private UUID id;  // ❌
    private UserId id; // ✅
}
```

## Aggregate reference convention

Khi một aggregate cần reference đến aggregate khác, cách dùng phụ thuộc vào vị trí của 2 aggregate.

**Quy tắc quyết định:**

| Trường hợp                              | Dùng                           |
|-----------------------------------------|--------------------------------|
| Cùng BC, ref đến aggregate khác         | `{Aggregate}Id` Value Object   |
| Across BC, chưa có Collaborator concept | `UUID` hoặc `String` primitive |
| Across BC, đã có Collaborator concept   | Value Object Collaborator      |
@
### Cùng BC — dùng typed Id

```java
// ✅ User cùng BC với Role → dùng RoleId
public class User extends AggregateRoot {
    private Set<RoleId> roleIds;  // typed — compiler bắt lỗi nếu truyền nhầm
}
 
// ❌ Raw UUID — mất type safety, không rõ đây là Id của aggregate nào
public class User extends AggregateRoot {
    private Set<UUID> roleIds;
}
```

### Across BC — chưa có Collaborator concept

```java
// ✅ Ticket BC không import UserId của Identity BC
public class Ticket extends AggregateRoot {
    private UUID reporterId;  // raw UUID — không tạo coupling với Identity BC
}
```

### Across BC — đã có Collaborator concept

Khi concept đã được model thành Value Object trong `domain/{concept}/` thì dùng luôn — không cần raw UUID:

```java
// ✅ Assignee đã bọc UserId bên trong, không lộ raw UUID ra aggregate
public class Ticket extends AggregateRoot {
    private Assignee assignee;  // Value Object trong BC này
}
 
// ❌ Dùng raw UUID khi đã có Collaborator concept
public class Ticket extends AggregateRoot {
    private UUID assigneeId;
}
```
---

## ErrorCode convention

Mỗi aggregate có enum ErrorCode riêng implement `ErrorCode` interface từ `libs/common`:

```java
// ✅ Implement ErrorCode interface từ libs/common
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", "error.user.not_found", 404),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "User already exists", "error.user.already_exists", 409),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Account is locked", "error.user.account_locked", 423)
    // ...
}

// ❌ Không dùng raw enum không implement interface
public enum UserErrorCode {
    USER_NOT_FOUND,
    USER_ALREADY_EXISTS,
}
```

- Không dùng raw string để mô tả lỗi trong domain
- `httpStatus` đặt trong ErrorCode — không để presentation layer tự quyết định

→ Chi tiết: [`error-handling.md`](./error-handling.md)

---

## Base package

| Service     | Base package                                   |
|-------------|------------------------------------------------|
| admin       | `vn.truongngo.apartcom.one.service.admin`      |
| oauth2      | `vn.truongngo.apartcom.one.service.oauth2`     |
| web-gateway | `vn.truongngo.apartcom.one.service.webgateway` |
| *(TBD)*     | `vn.truongngo.apartcom.one.service.{name}`     |
# Javadoc Convention

## Core principle

Document the **WHY and WHAT IS NON-OBVIOUS** — not what the code already says.
Well-named identifiers are self-documenting. Javadoc that only restates the method name adds noise.

---

## HTML formatting cho doc nhiều đoạn

Khi class-level doc có ≥2 ý tách biệt (VD "làm gì" + "xác thực/bảo mật ra sao"), tách thành nhiều
`<p>` — mỗi `<p>` là 1 khối ý, render ra cách dòng rõ ràng trên IDE/generated HTML thay vì dồn thành
1 đoạn dài khó đọc. `<br>` chỉ dùng để xuống dòng **trong cùng 1 ý** (không tách khối), không thay
thế cho `<p>`.

Trong mỗi `<p>`, dùng tag inline để tăng khả năng scan nhanh:
- `<code>` — tên class/method/config key/giá trị literal (VD `<code>app.oauth2.internal-base-url</code>`)
- `<b>` — từ khoá cần nhấn, đặc biệt phủ định (VD `<b>KHÔNG</b>` qua api-gateway)
- `&mdash;` — dấu gạch ngang dài (—) trong prose, an toàn hơn ký tự Unicode thô khi build doc qua công cụ không handle UTF-8 nhất quán
- `{@link}` — vẫn theo quy tắc ở mục Tags reference bên dưới, dùng được cả bên trong `<p>`

```java
/**
 * <p>Back-channel session revoke sang <b>web-gateway</b> (<code>POST /webgw/internal/sessions/revoke</code>) &mdash;
 * server-to-server, <b>KHÔNG</b> qua api-gateway (cùng lý do các call nội bộ khác trong service này, xem
 * <code>app.oauth2.internal-base-url</code>).</p>
 *
 * <p>Xác thực bằng service-account JWT: mint token <b>MỚI</b> mỗi lần gọi qua OAuth2 <code>client_credentials</code> grant
 * (client <code>"oauth2-service-internal"</code>, scope <code>"webgw.internal"</code>, seed ở migration <code>V10</code>) &mdash;
 * xem {@link ClientCredentialsTokenClient} (<code>common-web</code>) cho phần mint token dùng chung. web-gateway
 * verify token qua JWKS đã có sẵn (<code>spring.security.oauth2.resourceserver.jwt.jwk-set-uri</code>,
 * web-gateway <code>SecurityConfiguration</code>).</p>
 */
```

Chỉ áp dụng khi doc thực sự nhiều đoạn/nhiều ý — 1-2 câu ngắn (trường hợp phổ biến nhất theo bảng ở
mục "Classes and Interfaces") thì không cần `<p>`, viết prose thường là đủ, thêm tag HTML vào sẽ chỉ
gây rườm rà không cần thiết.

---

## Classes and Interfaces

| Case                             | Required    | Note                                  |
|----------------------------------|-------------|---------------------------------------|
| Public API interface             | YES         | Describe contract, not implementation |
| Public API abstract class        | YES         | Describe role in the system           |
| Public API concrete class        | YES (brief) | 1–2 sentences on responsibility       |
| Internal / package-private class | NO          | —                                     |
| Utility class                    | NO          | —                                     |

```java
/**
 * Base for all policy nodes in the evaluation tree.
 * A null {@code target} means this node is always applicable.
 */
public sealed abstract class AbstractPolicy { /*...*/ }
```

---

## Interface methods

Document all public interface methods — this is the contract consuming code depends on.

```java
public interface ExpressionEvaluator {

    /**
     * Evaluates a literal expression against the given context.
     *
     * @param expression the atomic expression to evaluate
     * @param context    the current evaluation context
     * @return {@code true} if the expression holds, {@code false} otherwise
     * @throws ExpressionEvaluationException if the expression is malformed or evaluation fails
     */
    boolean evaluate(LiteralExpression expression, EvaluationContext context);
}
```

---

## Methods implementing an interface

| Case                                                                    | Required | Pattern                                     |
|-------------------------------------------------------------------------|----------|---------------------------------------------|
| Implementation matches interface contract exactly                       | NO       | Omit entirely — IDE inherits from interface |
| Implementation adds constraints not in the interface                    | YES      | Document the extra constraint only          |
| Implementation throws a checked exception the interface doesn't declare | YES      | `@throws` only                              |
| `@Override` of abstract method with identical contract                  | NO       | Omit                                        |

```java
// NO doc needed — contract is fully described in ExpressionEvaluator
@Override
public boolean evaluate(LiteralExpression expression, EvaluationContext context) {
    //...
}

// YES — adds constraint not in interface
@Override
public boolean evaluate(LiteralExpression expression, EvaluationContext context) {
    // SpEL adapter only supports standard SpEL syntax; Groovy-style closures are not supported
    //...
}
```

---

## Abstract methods

Document on the abstract method — subclasses inherit via `{@inheritDoc}` or omit entirely.

```java
/**
 * @throws IllegalArgumentException if required fields are missing
 */
public abstract T build();
```

---

## Constructors

| Case                                                          | Required |
|---------------------------------------------------------------|----------|
| Private constructor                                           | NO       |
| Protected constructor (e.g. builder-accepting)                | NO       |
| Public constructor with validation or non-obvious param rules | YES      |

---

## Builder classes and methods

| Case                                          | Required | Pattern                                    |
|-----------------------------------------------|----------|--------------------------------------------|
| Builder class itself                          | NO       | Implied by `builder()` factory             |
| Simple setter methods (`id()`, `code()`, ...) | NO       | —                                          |
| `build()`                                     | YES      | Document `@throws` for validation failures |
| `with(T existing)` copy-factory               | YES      | Explain copy-and-modify intent             |

```java
/**
 * Creates a pre-populated builder from an existing instance for copy-and-modify.
 */
public static PolicySetBuilder with(PolicySet policySet) { /*...*/ }

/**
 * @throws IllegalArgumentException if {@code code} or {@code algorithm} is null
 */
public PolicySet build() { /*...*/ }
```

---

## Fields

| Case                                      | Required                                   |
|-------------------------------------------|--------------------------------------------|
| Self-explanatory field                    | NO                                         |
| Nullable field with non-obvious semantics | YES (inline comment or `@param` on getter) |
| Field whose ordering affects evaluation   | YES                                        |

```java
/** null means always applicable — no target filtering is applied */
private final Expression target;

/** Ordered — evaluation order matters for {@link CombineAlgorithm#FIRST_APPLICABLE} */
private final List<AbstractPolicy> policies;
```

---

## Getters and Setters

Document only when the return value has a constraint that is not obvious from the field name.

```java
// NO — obvious
public String getId() { return id; }

// YES — null semantics are non-obvious
/**
 * @return the target expression, or {@code null} if this node is always applicable
 */
public Expression getTarget() { return target; }
```

---

## Enums

| Case                                  | Required                                        |
|---------------------------------------|-------------------------------------------------|
| Enum class                            | YES (brief — describe what the enum represents) |
| Enum values that are self-explanatory | NO                                              |
| Enum values with non-obvious behavior | YES                                             |

```java
/**
 * Algorithms for combining multiple evaluation results into a single {@link AuthzDecision}.
 */
public enum CombineAlgorithm {

    DENY_OVERRIDES,
    PERMIT_OVERRIDES,

    /** Returns the effect of the first applicable rule or policy, evaluated in declaration order. */
    FIRST_APPLICABLE,

    DENY_UNLESS_PERMIT,
    PERMIT_UNLESS_DENY
}
```

---

## `{@inheritDoc}` usage

Use `{@inheritDoc}` only when the implementation wants to **append** to the interface doc.
When the implementation matches exactly — omit the annotation entirely.

```java
/**
 * {@inheritDoc}
 * This implementation caches parsed expressions for repeated evaluation.
 */
@Override
public boolean evaluate(LiteralExpression expression, EvaluationContext context) { /*...*/ }
```

---

## Tags reference

| Tag       | When to use                                                                 |
|-----------|-----------------------------------------------------------------------------|
| `@param`  | Only for non-obvious parameters                                             |
| `@return` | Only when return value has constraints or nullable semantics                |
| `@throws` | Always for checked exceptions; for unchecked only if callers must handle it |
| `@see`    | When pointing to related concept is genuinely useful                        |
| `{@code}` | For inline code references                                                  |
| `{@link}` | For references to other types/methods                                       |

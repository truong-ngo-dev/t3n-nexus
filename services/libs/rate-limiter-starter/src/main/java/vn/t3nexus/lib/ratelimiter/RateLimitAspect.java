package vn.t3nexus.lib.ratelimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Xử lý {@link RateLimit}. Đặt order = {@code HIGHEST_PRECEDENCE} — chạy TRƯỚC advisor của
 * {@code @Transactional} (default order = LOWEST_PRECEDENCE), tức là bọc ngoài cùng: request
 * vượt limit bị chặn trước khi transaction/connection nào được mở, không lãng phí tài nguyên DB
 * cho request đã biết chắc sẽ bị từ chối.
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect implements Ordered {

    private final RateLimiter rateLimiter;

    private final ExpressionParser        parser               = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer  = new DefaultParameterNameDiscoverer();

    // argNames bắt buộc phải khai tường minh: AbstractAspectJAdvice bind tham số thứ 2 (rateLimit)
    // bằng cách match tên biến trong pointcut "@annotation(rateLimit)" với TÊN THAM SỐ method này
    // đọc qua reflection lúc runtime — nếu class lib này build không giữ parameter name (không có
    // -parameters/-g:vars), discovery fail và ném IllegalStateException "Required to bind 2
    // arguments, but only bound 1" ngay khi advice chạy lần đầu (production build strip debug info
    // theo mặc định — dev build của service gọi tới thì không sao, nhưng lib này build riêng).
    @Around(value = "@annotation(rateLimit)", argNames = "joinPoint,rateLimit")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = evaluateKey(joinPoint, rateLimit);

        boolean allowed = rateLimiter.tryAcquire(key, rateLimit.limit(), Duration.ofSeconds(rateLimit.windowSeconds()));
        if (!allowed) {
            // Log key ở đây (không phải RateLimitExceptionHandler) vì đây là nơi duy nhất có key
            // đã evaluate — RateLimitExceededException chỉ mang message tĩnh từ annotation, không
            // mang key, nên tách chỗ khác log sẽ mất thông tin ai/gì bị chặn.
            log.warn("[RateLimit] exceeded: key={}, limit={}/{}s", key, rateLimit.limit(), rateLimit.windowSeconds());
            throw new RateLimitExceededException(rateLimit.message());
        }

        return joinPoint.proceed();
    }

    private String evaluateKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method          method    = signature.getMethod();
        String[]        paramNames = paramNameDiscoverer.getParameterNames(method);
        Object[]        args       = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = parser.parseExpression(rateLimit.key());
        return String.valueOf(expression.getValue(context));
    }

    // KHÔNG dùng Ordered.HIGHEST_PRECEDENCE (Integer.MIN_VALUE) — trùng order với
    // ExposeInvocationInterceptor nội bộ của Spring (advisor expose MethodInvocation hiện tại
    // qua ThreadLocal cho các pointcut dạng "@annotation(x)" bind JoinPointMatch). Khi trùng
    // tuyệt đối Integer.MIN_VALUE, AspectJAwareAdvisorAutoProxyCreator có thể sort
    // ExposeInvocationInterceptor ra SAU aspect này thay vì trước → JoinPointMatch không được
    // set trước khi advice chạy → IllegalStateException "Required to bind 2 arguments, but only
    // bound 1 (JoinPointMatch was NOT bound in invocation)" ngay cả trên class đơn giản nhất.
    // +1 vẫn đủ để chạy trước @Transactional (LOWEST_PRECEDENCE) nhưng tránh trùng mốc tối thiểu.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

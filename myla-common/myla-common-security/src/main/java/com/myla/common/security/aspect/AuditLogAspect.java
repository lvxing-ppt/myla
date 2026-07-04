package com.myla.common.security.aspect;

import com.myla.common.security.annotation.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志 AOP 切面。
 * <p>
 * 拦截所有标注了 {@link AuditLog @AuditLog} 注解的方法，在方法执行前后
 * 记录审计信息（操作名称、资源类型、方法信息、参数、耗时、执行状态）。
 * 成功执行记录 INFO 级别日志，异常执行记录 ERROR 级别日志。
 * </p>
 * <p>
 * 通过环绕通知实现：
 * <ol>
 *   <li>方法执行前：记录调用时间和参数</li>
 *   <li>方法正常返回：记录状态为 SUCCESS 及耗时</li>
 *   <li>方法抛出异常：记录状态为 FAILED、异常信息及耗时，然后重新抛出异常</li>
 * </ol>
 * </p>
 *
 * @author MyLA Team
 * @see AuditLog
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    /**
     * 环绕通知：拦截标注了 {@link AuditLog @AuditLog} 注解的方法，
     * 记录审计信息后执行原方法。
     *
     * @param joinPoint 切入点，提供被拦截方法的元数据
     * @param auditLog 注解实例，提供操作名称和资源类型配置
     * @return 原方法的返回值
     * @throws Throwable 原方法抛出的异常（在记录日志后重新抛出）
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        // 构建审计日志条目
        Map<String, Object> auditEntry = new LinkedHashMap<>();
        auditEntry.put("action", auditLog.action());
        auditEntry.put("resourceType", auditLog.resourceType());
        auditEntry.put("className", className);
        auditEntry.put("methodName", methodName);
        auditEntry.put("args", truncateArgs(args));
        auditEntry.put("timestamp", LocalDateTime.now().toString());

        long start = System.currentTimeMillis();
        try {
            // 执行原方法
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            auditEntry.put("status", "SUCCESS");
            auditEntry.put("elapsedMs", elapsed);

            log.info("AUDIT: action={}, resource={}, method={}, elapsed={}ms",
                auditLog.action(), auditLog.resourceType(), methodName, elapsed);

            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            auditEntry.put("status", "FAILED");
            auditEntry.put("error", e.getMessage());
            auditEntry.put("elapsedMs", elapsed);

            log.error("AUDIT FAILED: action={}, resource={}, method={}, error={}",
                auditLog.action(), auditLog.resourceType(), methodName, e.getMessage());

            throw e;
        }
    }

    /**
     * 截断方法参数用于日志输出，避免参数对象过大导致日志膨胀。
     * <ul>
     *   <li>无参 — 返回 null</li>
     *   <li>单参 — 将单个参数转为字符串</li>
     *   <li>多参 — 返回参数数量摘要</li>
     * </ul>
     *
     * @param args 方法参数数组
     * @return 截断后的参数描述
     */
    private Object truncateArgs(Object[] args) {
        if (args == null || args.length == 0) return null;
        if (args.length == 1) return String.valueOf(args[0]);
        return "[" + args.length + " args]";
    }
}

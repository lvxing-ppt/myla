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

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> auditEntry = new LinkedHashMap<>();
        auditEntry.put("action", auditLog.action());
        auditEntry.put("resourceType", auditLog.resourceType());
        auditEntry.put("className", className);
        auditEntry.put("methodName", methodName);
        auditEntry.put("args", truncateArgs(args));
        auditEntry.put("timestamp", LocalDateTime.now().toString());

        long start = System.currentTimeMillis();
        try {
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

    private Object truncateArgs(Object[] args) {
        if (args == null || args.length == 0) return null;
        if (args.length == 1) return String.valueOf(args[0]);
        return "[" + args.length + " args]";
    }
}

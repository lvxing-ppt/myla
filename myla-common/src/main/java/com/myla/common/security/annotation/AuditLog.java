package com.myla.common.security.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解。
 * <p>
 * 标记在需要记录审计日志的方法上（通常为 Controller 或 Service 的写操作方法）。
 * 由 {@link com.myla.common.security.aspect.AuditLogAspect} 通过 AOP 拦截，
 * 自动记录操作人、操作类型、资源类型、方法参数和耗时等信息。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @AuditLog(action = "审核", resourceType = "样本")
 * public void approveSample(Long sampleId) { ... }
 * }</pre>
 *
 * @author MyLA Team
 * @see com.myla.common.security.aspect.AuditLogAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作名称，如 "审核"、"修改"、"删除"、"发布" 等。
     * @return 操作名称字符串
     */
    String action() default "";

    /**
     * 操作资源类型，如 "样本"、"结果"、"用户"、"仪器" 等。
     * @return 资源类型字符串
     */
    String resourceType() default "";
}

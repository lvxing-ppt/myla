package com.mlms.oes.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MLMS 系统用户实体类。
 * 对应数据库表 sys_user，存储系统用户的账户信息、
 * 个人信息及登录记录，支持 MyBatis-Plus 的自动填充和逻辑删除。
 *
 * @author MLMS Team
 */
@Data
@TableName("sys_user")
public class SysUser {
    /** 用户主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名，唯一标识 */
    private String username;

    /** 密码哈希值，存储 BCrypt 加密后的密码 */
    private String passwordHash;

    /** 用户真实姓名 */
    private String realName;

    /** 手机号码 */
    private String mobile;

    /** 电子邮箱地址 */
    private String email;

    /** 用户状态：ACTIVE-正常，DISABLED-禁用 */
    private String status;

    /** 所属医院ID，关联医院表 */
    private Long hospitalId;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 记录更新时间，由 MyBatis-Plus 插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
}

package com.myla.admin.service;

import com.myla.admin.entity.SysUser;

/**
 * MYLA 系统用户服务接口。
 * 定义用户管理的核心业务操作，
 * 包括按用户名查询用户和创建新用户。
 * 实现类负责具体的数据库操作和业务逻辑。
 */
public interface UserService {

    /**
     * 根据用户名查询用户信息。
     * 若用户不存在则抛出 BusinessException 异常。
     *
     * @param username 登录用户名
     * @return 匹配的 SysUser 实体对象
     */
    SysUser findByUsername(String username);

    /**
     * 创建新用户。
     * 设置用户默认状态为 ACTIVE，
     * 持久化用户信息并记录操作日志。
     *
     * @param user 待创建的用户实体（不含 ID 和状态）
     * @return 创建成功后的用户实体（含生成的 ID 和默认状态）
     */
    SysUser createUser(SysUser user);
}

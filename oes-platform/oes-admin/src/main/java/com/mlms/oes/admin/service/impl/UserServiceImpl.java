package com.mlms.oes.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mlms.oes.admin.entity.SysUser;
import com.mlms.oes.admin.mapper.SysUserMapper;
import com.mlms.oes.admin.service.UserService;
import com.mlms.oes.common.core.constant.ResultCode;
import com.mlms.oes.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MLMS 系统用户服务实现类。
 * 继承 MyBatis-Plus 的 ServiceImpl 以获得内置的 CRUD 方法，
 * 实现 UserService 接口中定义的用户查询和创建逻辑。
 * 业务逻辑包括：按用户名查询时校验存在性、创建时设置默认状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    /**
     * 根据用户名查询用户。
     * 使用 MyBatis-Plus 的 Lambda 查询进行精确匹配，
     * 若用户不存在则抛出 BusinessException（NOT_FOUND 错误码）。
     *
     * @param username 登录用户名
     * @return 匹配的 SysUser 实体
     * @throws BusinessException 当用户不存在时抛出
     */
    @Override
    public SysUser findByUsername(String username) {
        SysUser user = lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return user;
    }

    /**
     * 创建新用户。
     * 业务逻辑：先设置用户默认状态为 ACTIVE，
     * 再调用 MyBatis-Plus 的 save 方法持久化到数据库，
     * 最后记录创建日志。
     *
     * @param user 待创建的用户实体
     * @return 创建成功后的用户实体（包含生成的 ID 和状态）
     */
    @Override
    public SysUser createUser(SysUser user) {
        user.setStatus("ACTIVE");
        save(user);
        log.info("User created: username={}", user.getUsername());
        return user;
    }
}

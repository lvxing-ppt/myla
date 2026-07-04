package com.myla.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myla.admin.entity.SysUser;
import com.myla.admin.mapper.SysUserMapper;
import com.myla.admin.service.UserService;
import com.myla.common.core.constant.ResultCode;
import com.myla.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    @Override
    public SysUser findByUsername(String username) {
        SysUser user = lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return user;
    }

    @Override
    public SysUser createUser(SysUser user) {
        user.setStatus("ACTIVE");
        save(user);
        log.info("User created: username={}", user.getUsername());
        return user;
    }
}

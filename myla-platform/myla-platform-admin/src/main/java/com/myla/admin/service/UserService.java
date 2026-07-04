package com.myla.admin.service;

import com.myla.admin.entity.SysUser;

public interface UserService {
    SysUser findByUsername(String username);
    SysUser createUser(SysUser user);
}

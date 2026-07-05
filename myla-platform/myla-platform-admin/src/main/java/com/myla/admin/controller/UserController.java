package com.myla.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myla.admin.entity.SysUser;
import com.myla.admin.mapper.SysUserMapper;
import com.myla.common.core.constant.ResultCode;
import com.myla.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 用户管理 CRUD。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;

    /** 用户列表 */
    @GetMapping
    public R<Page<SysUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SysUser> p = new Page<>(page, size);
        return R.ok(userMapper.selectPage(p,
            new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getCreatedAt)));
    }

    /** 单个用户 */
    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        return user != null ? R.ok(user) : R.fail(ResultCode.NOT_FOUND);
    }

    /** 新增用户 */
    @PostMapping
    public R<SysUser> create(@RequestBody SysUser user) {
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return R.ok(user);
    }

    /** 更新用户 */
    @PutMapping("/{id}")
    public R<SysUser> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return R.ok(user);
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return R.ok();
    }
}

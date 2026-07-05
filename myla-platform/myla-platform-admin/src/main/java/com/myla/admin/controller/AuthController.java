package com.myla.admin.controller;

import com.myla.admin.entity.SysUser;
import com.myla.admin.security.JwtTokenProvider;
import com.myla.admin.service.UserService;
import com.myla.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MYLA 系统认证控制器。
 * 提供用户登录认证相关的 REST API 接口，
 * 包括用户名密码验证和 JWT 令牌签发功能。
 * 所有接口均以 /api/v1/auth 为前缀。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbc;

    /**
     * 用户登录接口。
     * HTTP 方法：POST
     * 接口路径：/api/v1/auth/login
     * 接收用户名和密码，验证用户身份后签发 JWT 令牌。
     * 生产环境中应使用 BCrypt 对密码进行哈希比对。
     *
     * @param body 请求体，包含 "username" 和 "password" 两个字段
     * @return 登录成功返回包含 token 和 username 的 Map，
     *         登录失败返回 UNAUTHORIZED 错误码
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        SysUser user = userService.findByUsername(username);
        if (user == null || !password.equals(user.getPasswordHash())) {
            return R.fail(com.myla.common.core.constant.ResultCode.UNAUTHORIZED);
        }

        // 查询用户角色
        List<String> roles = jdbc.queryForList(
            "SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            String.class, user.getId());
        if (roles.isEmpty()) roles = List.of("ROLE_TECHNICIAN");

        String token = jwtTokenProvider.createToken(username, roles);
        return R.ok(Map.of("token", token, "username", username, "roles", roles));
    }
}

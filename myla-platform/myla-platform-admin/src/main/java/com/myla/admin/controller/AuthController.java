package com.myla.admin.controller;

import com.myla.admin.entity.SysUser;
import com.myla.admin.security.JwtTokenProvider;
import com.myla.admin.service.UserService;
import com.myla.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public R<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        SysUser user = userService.findByUsername(username);
        // In production, verify password hash using BCrypt
        if (user == null) {
            return R.fail(com.myla.common.core.constant.ResultCode.UNAUTHORIZED);
        }

        String token = jwtTokenProvider.createToken(username, List.of("USER"));
        return R.ok(Map.of("token", token, "username", username));
    }
}

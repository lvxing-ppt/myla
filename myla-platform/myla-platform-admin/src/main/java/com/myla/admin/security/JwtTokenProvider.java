package com.myla.admin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * MYLA 系统 JWT 令牌提供器。
 * 负责 JWT 令牌的创建、验证和解析工作。
 * 使用 HMAC-SHA 算法对令牌进行签名，
 * 密钥和过期时间通过配置文件注入。
 * 令牌中包含用户名（subject）和角色列表（claims）。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** HMAC 签名密钥，由配置文件中的 myla.security.jwt-secret 生成 */
    private final SecretKey key;

    /** 令牌过期时间（毫秒），由 myla.security.jwt-expiration 配置（单位：秒）换算 */
    private final long expirationMs;

    /**
     * 构造 JWT 令牌提供器。
     * 从配置文件读取 JWT 密钥和过期时间，初始化签名密钥。
     *
     * @param secret     JWT 签名密钥明文，来自配置 myla.security.jwt-secret
     * @param expiration JWT 过期时间（秒），来自配置 myla.security.jwt-expiration
     */
    public JwtTokenProvider(@Value("${myla.security.jwt-secret}") String secret,
                            @Value("${myla.security.jwt-expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expiration * 1000;
    }

    /**
     * 创建 JWT 令牌。
     * 令牌中包含用户名作为主题（subject）和角色列表作为自定义声明（claims），
     * 过期时间由配置决定。
     *
     * @param username 用户名，作为令牌的 subject
     * @param roles    角色列表，作为令牌的 claims
     * @return 生成的 JWT 令牌字符串
     */
    public String createToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }

    /**
     * 验证 JWT 令牌是否有效。
     * 通过解析令牌并校验签名来验证其合法性，
     * 签名无效、令牌过期或格式错误均返回 false。
     *
     * @param token JWT 令牌字符串
     * @return true-令牌有效，false-令牌无效
     */
    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 JWT 令牌中提取用户名。
     * 仅在令牌已验证有效后调用，
     * 从令牌的 subject 字段中获取用户名。
     *
     * @param token JWT 令牌字符串
     * @return 令牌中的用户名
     */
    public String getUsername(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    /** 从 Token 中提取角色列表 */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return (List<String>) Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token)
            .getPayload()
            .get("roles", List.class);
    }
}

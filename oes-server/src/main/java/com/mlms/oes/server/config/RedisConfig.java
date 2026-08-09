package com.mlms.oes.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类。
 * <p>
 * 配置 {@link RedisTemplate}，定义 Redis 的键值序列化方式：
 * <ul>
 *   <li>键（Key）和哈希键（Hash Key）使用 {@link StringRedisSerializer}，以可读的字符串形式存储</li>
 *   <li>值（Value）和哈希值（Hash Value）使用 {@link GenericJackson2JsonRedisSerializer}，
 *       以 JSON 格式序列化，同时携带类型信息（@class），支持反序列化时恢复 Java 类型</li>
 * </ul>
 * </p>
 *
 * <p><b>使用场景：</b>缓存检验结果、Token 管理、分布式锁、仪器状态缓存等。</p>
 *
 * @author MLMS Team
 */
@Configuration
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisConfig {

    /**
     * 配置 RedisTemplate 实例。
     * <p>
     * 键序列化：字符串（可读性好，便于通过 redis-cli 查看）
     * 值序列化：JSON（支持复杂 Java 对象的序列化和反序列化）
     * </p>
     *
     * @param factory Redis 连接工厂
     * @return 配置完成的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 键和哈希键使用字符串序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 值和哈希值使用 JSON 序列化器（携带类型信息）
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}

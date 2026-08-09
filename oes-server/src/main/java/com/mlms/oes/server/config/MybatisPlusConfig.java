package com.mlms.oes.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mlms.oes.common.core.util.SnowflakeIdGenerator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis-Plus 配置类 — 分页 + Snowflake 全局 ID 生成。
 */
@Configuration
@MapperScan("com.mlms.oes.**.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 全局 Snowflake ID 生成器 — 替换 MyBatis-Plus 默认实现。
     * 所有 @TableId(type = IdType.ASSIGN_ID) 的实体主键都通过此生成器获取 ID。
     */
    @Bean
    @Primary
    public IdentifierGenerator snowflakeIdentifierGenerator(SnowflakeIdGenerator snowflake) {
        return new IdentifierGenerator() {
            @Override
            public Number nextId(Object entity) {
                return snowflake.nextId();
            }
        };
    }
}

package com.myla.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 配置 MyBatis-Plus 拦截器链，当前仅包含分页插件。
 * Mapper 扫描范围为 {@code com.myla.**.mapper}，覆盖所有子模块的 Mapper 接口。
 * </p>
 *
 * @author MyLA Team
 */
@Configuration
@MapperScan("com.myla.**.mapper")
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 拦截器。
     * <p>当前添加了 MySQL 分页拦截器，自动处理分页查询的 COUNT 和 LIMIT 子句。</p>
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加 MySQL 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

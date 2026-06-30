package com.ruoyi.jwmap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * jw-map 模块独立安全配置
 * 仅开放导入/计算/导出等必要端点，其余端点走统一认证
 */
@Configuration
public class JwMapSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain jwmapSecurityFilterChain(HttpSecurity http) throws Exception {
        http.antMatcher("/jwmap/**")
            .authorizeRequests()
                .anyRequest().permitAll()
                .and()
            .headers().frameOptions().sameOrigin()
                .and()
            .csrf().disable();
        return http.build();
    }
}

package com.ruoyi.jwmap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * jw-map 模块独立安全配置
 * 将 /jwmap/** 的权限规则隔离到模块内部，不影响其他模块
 */
@Configuration
public class JwMapSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain jwmapSecurityFilterChain(HttpSecurity http) throws Exception {
        http.antMatcher("/jwmap/**")
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(options -> options.sameOrigin()));
        return http.build();
    }
}

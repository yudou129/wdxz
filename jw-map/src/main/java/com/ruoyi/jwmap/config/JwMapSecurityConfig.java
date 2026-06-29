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
        String[] PUBLIC_POST = {
            "/jwmap/import/**",
            "/jwmap/compute/**",
            "/jwmap/data/branch/indicators/**",
            "/jwmap/data/branch/score/detail/**",
            "/jwmap/data/branch/ranking/**",
            "/jwmap/data/branch/topScores/**",
            "/jwmap/data/branch/pillar/**",
            "/jwmap/data/branch/summary/**",
            "/jwmap/data/grid/score/**",
            "/jwmap/data/grid/indicators/**",
            "/jwmap/data/quadrant/**",
            "/jwmap/data/dimension/stats/**",
            "/jwmap/data/ranking/threeFocus/**",
            "/jwmap/data/peerBank/**",
            "/jwmap/data/poi/**",
        };
        http.antMatcher("/jwmap/**")
            .authorizeRequests()
                .antMatchers(org.springframework.http.HttpMethod.POST, "/jwmap/import/**").authenticated()
                .antMatchers(org.springframework.http.HttpMethod.POST, "/jwmap/compute/**").authenticated()
                .antMatchers(org.springframework.http.HttpMethod.GET, PUBLIC_POST).authenticated()
                .antMatchers("/jwmap/data/access/**").authenticated()
                .anyRequest().denyAll()
                .and()
            .headers().frameOptions().sameOrigin()
                .and()
            .csrf().disable();
        return http.build();
    }
}

package com.ruoyi.jwmap.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智能体 AI 模块配置
 * 使用 JDK 原生 HttpURLConnection（不依赖 OkHttp），避免版本冲突
 */
@Configuration
@EnableConfigurationProperties(AgentAiProperties.class)
public class AgentAiConfig {
    // 使用 JDK 原生 HttpURLConnection，无需额外 Bean
}

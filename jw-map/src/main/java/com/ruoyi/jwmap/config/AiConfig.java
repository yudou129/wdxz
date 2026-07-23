package com.ruoyi.jwmap.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * AI 模块配置
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * OkHttpClient Bean（支持流式 SSE 读取）
     * 配置合理的超时和连接池
     */
    @Bean
    public OkHttpClient okHttpClient(AiProperties aiProperties) {
        int timeout = aiProperties.getTimeoutSeconds();
        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)
                .build();
    }
}

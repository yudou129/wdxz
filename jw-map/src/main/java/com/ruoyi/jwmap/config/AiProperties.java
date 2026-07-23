package com.ruoyi.jwmap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI LLM 配置属性
 * 从 application.yml 中读取 ai: 前缀配置
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** DeepSeek API Key */
    private String apiKey = "";

    /** API Base URL（兼容 OpenAI 格式） */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型名称 */
    private String model = "deepseek-v4-flash";

    /** 温度参数（0.0-1.0，分析类任务建议 0.2-0.4） */
    private Double temperature = 0.3;

    /** 最大输出 Token 数 */
    private Integer maxTokens = 4096;

    /** HTTP 超时时间（秒） */
    private Integer timeoutSeconds = 120;

    /** 分析缓存 TTL（分钟，仅用于存量的 Redis 缓存） */
    private Integer cacheTtlMinutes = 60;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Integer getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(Integer cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
}

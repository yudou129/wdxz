package com.ruoyi.jwmap.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智能体 AI 配置属性
 * 从 application.yml 中读取 agent.ai: 前缀配置
 * 用于替代原有的 DeepSeek API，对接新的智能体后端
 */
@ConfigurationProperties(prefix = "agent.ai")
public class AgentAiProperties {

    /** 智能体服务域名前缀 */
    private String domainPrefix = "mlp.xxx";

    /** 认证 Token（自定义 token 头，非 Bearer） */
    private String token = "";

    /** 智能体名称 */
    private String agentName = "贵州分行营业网点布局优化智能体";

    /** 默认用户 ID */
    private String defaultUserId = "";

    /** 项目 ID */
    private String projectId = "mock_project_id";

    /** 引擎类型 */
    private String engine = "workflow";

    /** HTTP 超时时间（秒） */
    private Integer timeoutSeconds = 120;

    public String getDomainPrefix() { return domainPrefix; }
    public void setDomainPrefix(String domainPrefix) { this.domainPrefix = domainPrefix; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getDefaultUserId() { return defaultUserId; }
    public void setDefaultUserId(String defaultUserId) { this.defaultUserId = defaultUserId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}

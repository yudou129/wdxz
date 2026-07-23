package com.ruoyi.jwmap.agent.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体对话请求体
 * 匹配 Python 参考实现中的 payload 格式：
 * {agentName, pluginList, question, sessionId, userId, projectId, parameters, upload, engine}
 */
public class AgentChatRequest {

    /** 智能体名称 */
    private String agentName;

    /** 插件列表 */
    private List<String> pluginList = new ArrayList<>();

    /** 用户提问内容 */
    private String question;

    /** 会话 ID（自动生成 UUID） */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 项目 ID */
    private String projectId;

    /** 参数（默认 {"img": {"curValue": ""}}） */
    private Map<String, Object> parameters;

    /** 上传文件列表 */
    private List<String> upload = new ArrayList<>();

    /** 引擎类型 */
    private String engine;

    public AgentChatRequest() {
        // 初始化默认参数
        Map<String, Object> imgParam = new HashMap<>();
        imgParam.put("curValue", "");
        this.parameters = new HashMap<>();
        this.parameters.put("img", imgParam);
    }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public List<String> getPluginList() { return pluginList; }
    public void setPluginList(List<String> pluginList) { this.pluginList = pluginList; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public List<String> getUpload() { return upload; }
    public void setUpload(List<String> upload) { this.upload = upload; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
}

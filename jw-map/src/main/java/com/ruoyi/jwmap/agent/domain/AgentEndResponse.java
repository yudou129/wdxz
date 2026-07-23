package com.ruoyi.jwmap.agent.domain;

/**
 * SSE 结束事件响应
 * 对应 event: end 的 data: 行内容 {"session_id": "..."}
 */
public class AgentEndResponse {

    /** 会话 ID */
    private String sessionId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}

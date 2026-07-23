package com.ruoyi.jwmap.agent.domain;

/**
 * SSE 错误事件响应
 * 对应 event: error 的 data: 行内容 {"message": "..."}
 */
public class AgentErrorResponse {

    /** 错误信息 */
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

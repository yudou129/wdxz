package com.ruoyi.jwmap.agent.domain;

/**
 * SSE 流式数据块
 * 对应智能体 API 推送的 data: 行内容
 * 兼容 chunk 和 content 两种字段名
 */
public class AgentStreamChunk {

    /** 文本块 */
    private String chunk;

    /** 文本块（备用字段名） */
    private String content;

    public String getChunk() { return chunk; }
    public void setChunk(String chunk) { this.chunk = chunk; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    /**
     * 获取文本内容，chunk 优先，content 作为后备
     */
    public String getChunkText() {
        if (chunk != null && !chunk.isEmpty()) {
            return chunk;
        }
        return content != null ? content : "";
    }
}

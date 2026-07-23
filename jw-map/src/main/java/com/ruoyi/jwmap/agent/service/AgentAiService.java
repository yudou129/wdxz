package com.ruoyi.jwmap.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能体 AI 服务接口
 * 对接新的智能体后端（替代原有的 DeepSeek API）
 */
public interface AgentAiService {

    /**
     * 流式对话：发送问题并通过 SseEmitter 推送 SSE 事件
     *
     * @param question 用户提问
     * @param emitter  SSE 发射器
     * @param userId   用户 ID（为空则使用默认值）
     */
    void chatStream(String question, SseEmitter emitter, String userId);

    /**
     * 同步对话：发送问题并等待完整响应
     *
     * @param question 用户提问
     * @param userId   用户 ID（为空则使用默认值）
     * @return 完整响应文本，出错返回 null
     */
    String chatSync(String question, String userId);
}

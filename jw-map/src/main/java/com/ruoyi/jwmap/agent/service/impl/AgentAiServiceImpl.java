package com.ruoyi.jwmap.agent.service.impl;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.jwmap.agent.config.AgentAiProperties;
import com.ruoyi.jwmap.agent.domain.AgentChatRequest;
import com.ruoyi.jwmap.agent.domain.AgentEndResponse;
import com.ruoyi.jwmap.agent.domain.AgentErrorResponse;
import com.ruoyi.jwmap.agent.domain.AgentStreamChunk;
import com.ruoyi.jwmap.agent.service.AgentAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 智能体 AI 服务实现（使用 JDK 原生 HttpURLConnection，不依赖 OkHttp）
 *
 * 对接新的智能体后端 API（替代原有的 DeepSeek API）：
 * - Endpoint: POST http://{domainPrefix}/siagent/agentConversationStreamService.htm
 * - Auth: 自定义 token 请求头
 * - Request: {agentName, question, sessionId, userId, projectId, parameters, upload, engine}
 * - Response SSE 协议：
 *   event: message → data: {"chunk": "..."}
 *   event: end     → data: {"session_id": "..."}
 *   event: error   → data: {"message": "..."}
 */
@Service
public class AgentAiServiceImpl implements AgentAiService {

    private static final Logger log = LoggerFactory.getLogger(AgentAiServiceImpl.class);

    /** SSE 事件类型：普通消息 */
    private static final String EVENT_MESSAGE = "message";
    /** SSE 事件类型：结束 */
    private static final String EVENT_END = "end";
    /** SSE 事件类型：错误 */
    private static final String EVENT_ERROR = "error";

    @Autowired
    private AgentAiProperties properties;

    // ==================== 接口实现 ====================

    @Override
    @Async("aiExecutor")
    public void chatStream(String question, SseEmitter emitter, String userId) {
        try {
            doStreamChat(question, emitter, userId);
        } catch (Exception e) {
            log.error("智能体流式对话异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"智能体服务连接中断：" + e.getMessage() + "\"}\n\n"));
            } catch (IOException ex) {
                // ignore
            }
            emitter.completeWithError(e);
        }
    }

    @Override
    public String chatSync(String question, String userId) {
        String resolvedUserId = resolveUserId(userId);
        String sessionId = UUID.randomUUID().toString();

        AgentChatRequest request = buildRequest(question, resolvedUserId, sessionId);
        String json = JSON.toJSONString(request);

        HttpURLConnection conn = null;
        try {
            conn = openConnection();
            writeRequestBody(conn, json);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readErrorStream(conn);
                log.error("智能体 API 调用失败: HTTP {} {}", responseCode, errorBody);
                return null;
            }

            StringBuilder fullContent = new StringBuilder();
            String eventType = EVENT_MESSAGE;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;

                    if (line.startsWith("event:")) {
                        eventType = line.substring(6).trim();
                        continue;
                    }

                    if (line.startsWith("data:")) {
                        String dataStr = line.substring(5).trim();

                        if (EVENT_END.equals(eventType)) {
                            break;
                        } else if (EVENT_ERROR.equals(eventType)) {
                            log.error("智能体返回错误: {}", dataStr);
                            return null;
                        } else {
                            try {
                                AgentStreamChunk chunk = JSON.parseObject(dataStr, AgentStreamChunk.class);
                                fullContent.append(chunk.getChunkText());
                            } catch (Exception e) {
                                // 解析异常忽略
                            }
                        }
                    }
                }
            }

            return fullContent.toString();

        } catch (IOException e) {
            log.error("智能体同步对话异常", e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ==================== 核心流式方法 ====================

    /**
     * 执行流式对话，通过 SseEmitter 推送 SSE 事件到前端
     */
    private void doStreamChat(String question, SseEmitter emitter, String userId) {
        String resolvedUserId = resolveUserId(userId);
        String sessionId = UUID.randomUUID().toString();

        AgentChatRequest request = buildRequest(question, resolvedUserId, sessionId);
        String url = buildUrl();
        String json = JSON.toJSONString(request);

        log.debug("智能体请求 URL: {}, sessionId: {}", url, sessionId);
        log.debug("智能体请求体: {}", json);

        HttpURLConnection conn = null;
        try {
            conn = openConnection();
            writeRequestBody(conn, json);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readErrorStream(conn);
                log.error("智能体 API 调用失败: HTTP {} {}", responseCode, errorBody);
                String errorMsg = "智能体服务暂时不可用，请稍后重试。（错误码：" + responseCode + "）";
                emitter.send(SseEmitter.event().data("{\"chunk\":" + JSON.toJSONString(errorMsg) + "}\n\n"));
                emitter.complete();
                return;
            }

            StringBuilder fullContent = new StringBuilder();
            String eventType = EVENT_MESSAGE;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 跳过空行（SSE 协议中的空行是分隔符）
                    if (line.isEmpty()) continue;

                    // 处理 event: 行
                    if (line.startsWith("event:")) {
                        eventType = line.substring(6).trim();
                        continue;
                    }

                    // 处理 data: 行
                    if (line.startsWith("data:")) {
                        String dataStr = line.substring(5).trim();

                        if (EVENT_END.equals(eventType)) {
                            // 结束事件
                            try {
                                AgentEndResponse endResp = JSON.parseObject(dataStr, AgentEndResponse.class);
                                log.debug("智能体会话结束, session_id: {}", endResp.getSessionId());
                            } catch (Exception e) {
                                // 解析失败不影响结束流程
                            }
                            emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
                            emitter.complete();
                            return;

                        } else if (EVENT_ERROR.equals(eventType)) {
                            // 错误事件
                            String errorMsg = "智能体返回错误";
                            try {
                                AgentErrorResponse errResp = JSON.parseObject(dataStr, AgentErrorResponse.class);
                                errorMsg = errResp.getMessage();
                            } catch (Exception e) {
                                // 使用默认错误信息
                            }
                            log.error("智能体错误: {}", errorMsg);
                            emitter.send(SseEmitter.event().data(
                                    "{\"chunk\":" + JSON.toJSONString(errorMsg) + "}\n\n"));
                            emitter.complete();
                            return;

                        } else {
                            // 普通消息事件
                            try {
                                AgentStreamChunk chunk = JSON.parseObject(dataStr, AgentStreamChunk.class);
                                String chunkText = chunk.getChunkText();
                                if (!chunkText.isEmpty()) {
                                    fullContent.append(chunkText);
                                    String sendData = "{\"chunk\":" + JSON.toJSONString(chunkText) + "}\n\n";
                                    emitter.send(SseEmitter.event().data(sendData));
                                }
                            } catch (Exception e) {
                                log.debug("解析智能体数据行失败: {}", dataStr);
                            }
                        }
                    }
                }
            }

            // 正常流结束
            emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
            emitter.complete();

        } catch (IOException e) {
            log.error("智能体流式读取异常", e);
            try {
                emitter.send(SseEmitter.event().data(
                        "{\"chunk\":" + JSON.toJSONString("智能体服务连接中断：" + e.getMessage()) + "}\n\n"));
            } catch (IOException ex) {
                // ignore
            }
            emitter.completeWithError(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ==================== HTTP 连接工具方法 ====================

    /**
     * 打开 HTTP 连接并设置通用属性
     */
    private HttpURLConnection openConnection() throws IOException {
        String urlStr = "http://" + properties.getDomainPrefix() + "/siagent/agentConversationStreamService.htm";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        int timeout = properties.getTimeoutSeconds() * 1000;
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("token", properties.getToken());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);

        return conn;
    }

    /**
     * 写入请求体
     */
    private void writeRequestBody(HttpURLConnection conn, String jsonBody) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
            os.flush();
        }
    }

    /**
     * 读取错误流内容
     */
    private String readErrorStream(HttpURLConnection conn) {
        if (conn == null) return "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 私有工具方法 ====================

    private String buildUrl() {
        return "http://" + properties.getDomainPrefix() + "/siagent/agentConversationStreamService.htm";
    }

    private AgentChatRequest buildRequest(String question, String userId, String sessionId) {
        AgentChatRequest request = new AgentChatRequest();
        request.setAgentName(properties.getAgentName());
        request.setQuestion(question);
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.setProjectId(properties.getProjectId());
        request.setEngine(properties.getEngine());
        return request;
    }

    private String resolveUserId(String userId) {
        if (StringUtils.isNotEmpty(userId)) {
            return userId;
        }
        String defaultUserId = properties.getDefaultUserId();
        if (StringUtils.isEmpty(defaultUserId)) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return defaultUserId;
    }
}

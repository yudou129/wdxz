package com.ruoyi.jwmap.service.impl;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.jwmap.config.AiProperties;
import com.ruoyi.jwmap.mapper.AiAnalysisMapper;
import com.ruoyi.jwmap.domain.AiAnalysisRecord;
import com.ruoyi.jwmap.domain.AiChatRequest;
import com.ruoyi.jwmap.domain.AiChatResponse;
import com.ruoyi.jwmap.constant.AiConstants;
import com.ruoyi.jwmap.service.IAiService;
import com.ruoyi.jwmap.config.JwMapConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 功能核心实现
 */
@Service
public class AiServiceImpl implements IAiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private AiPromptBuilder aiPromptBuilder;

    @Autowired
    private AiDataAggregator aiDataAggregator;

    @Autowired
    private AiCacheService aiCacheService;

    @Autowired
    private AiAnalysisMapper aiAnalysisMapper;

    @Autowired
    private JwMapConfig jwMapConfig;

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    /**
     * 临时缓存：流式完成后暂存内容，用户满意后才持久化
     * key=entityKey, value=TempContent (content + timestamp)
     * 缓存 30 分钟后自动过期
     */
    private static final Map<String, TempContent> analysisTempCache = new ConcurrentHashMap<>();

    private static class TempContent {
        final String content;
        final String city;
        final long timestamp;
        TempContent(String content, String city) {
            this.content = content;
            this.city = city;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30 * 60 * 1000;
        }
    }

    // ==================== 核心 LLM 调用 ====================

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        List<AiChatRequest.Message> messages = Arrays.asList(
                new AiChatRequest.Message("system", systemPrompt),
                new AiChatRequest.Message("user", userPrompt)
        );
        AiChatRequest request = new AiChatRequest(
                aiProperties.getModel(), messages,
                aiProperties.getTemperature(), aiProperties.getMaxTokens(), false
        );
        return doChat(request);
    }

    @Override
    public void chatStream(String systemPrompt, String userPrompt, SseEmitter emitter) {
        List<AiChatRequest.Message> messages = Arrays.asList(
                new AiChatRequest.Message("system", systemPrompt),
                new AiChatRequest.Message("user", userPrompt)
        );
        AiChatRequest request = new AiChatRequest(
                aiProperties.getModel(), messages,
                aiProperties.getTemperature(), aiProperties.getMaxTokens(), true
        );
        doChatStream(request, emitter);
    }

    /**
     * 非流式调用 LLM
     */
    private String doChat(AiChatRequest request) {
        String json = com.alibaba.fastjson.JSON.toJSONString(request);
        RequestBody body = RequestBody.create(JSON_MEDIA, json);
        Request httpRequest = new Request.Builder()
                .url(aiProperties.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("AI API 调用失败: HTTP {} {}", response.code(), errorBody);
                return "AI 服务暂时不可用，请稍后重试。（错误码：" + response.code() + "）";
            }
            String respBody = response.body() != null ? response.body().string() : "";
            AiChatResponse aiResp = com.alibaba.fastjson.JSON.parseObject(respBody, AiChatResponse.class);
            return aiResp != null ? aiResp.getFirstChoiceContent() : "";
        } catch (IOException e) {
            log.error("AI API 调用异常", e);
            return "AI 服务连接失败：" + e.getMessage();
        }
    }

    /**
     * 流式调用 LLM，通过 SSE 推送
     */
    private void doChatStream(AiChatRequest request, SseEmitter emitter) {
        String json = com.alibaba.fastjson.JSON.toJSONString(request);
        RequestBody body = RequestBody.create(JSON_MEDIA, json);
        Request httpRequest = new Request.Builder()
                .url(aiProperties.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("AI 流式调用失败: HTTP {} {}", response.code(), errorBody);
                emitter.send(SseEmitter.event().data("{\"chunk\":\"AI服务暂时不可用，请稍后重试。（错误码：" + response.code() + "）\"}\n\n"));
                emitter.complete();
                return;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                emitter.complete();
                return;
            }

            StringBuilder fullContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (AiConstants.SSE_DONE.equals(data)) {
                            break;
                        }
                        try {
                            AiChatResponse chunkResp = com.alibaba.fastjson.JSON.parseObject(data, AiChatResponse.class);
                            String chunk = chunkResp != null ? chunkResp.getFirstChoiceContent() : "";
                            if (!chunk.isEmpty()) {
                                fullContent.append(chunk);
                                String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                                emitter.send(SseEmitter.event().data(sendData));
                            }
                        } catch (Exception e) {
                            // 解析异常忽略，继续下一行
                        }
                    }
                }
            }

            emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
            emitter.complete();

            // 存储全量结果（对于需要持久化的功能）通过外部决定
        } catch (IOException e) {
            log.error("AI 流式读取异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"AI服务连接中断：" + e.getMessage() + "\"}\n\n"));
            } catch (IOException ex) {
                // ignore
            }
            emitter.completeWithError(e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 执行流式 AI 分析并在完成后保存结果
     */
    private void doStreamAndSave(String systemPrompt, String userPrompt, SseEmitter emitter,
                                  String analysisType, String entityKey, String city) {
        List<AiChatRequest.Message> messages = Arrays.asList(
                new AiChatRequest.Message("system", systemPrompt),
                new AiChatRequest.Message("user", userPrompt)
        );
        AiChatRequest request = new AiChatRequest(
                aiProperties.getModel(), messages,
                aiProperties.getTemperature(), aiProperties.getMaxTokens(), true
        );

        StringBuilder fullContent = new StringBuilder();
        String json = com.alibaba.fastjson.JSON.toJSONString(request);
        RequestBody body = RequestBody.create(JSON_MEDIA, json);
        Request httpRequest = new Request.Builder()
                .url(aiProperties.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                emitter.send(SseEmitter.event().data("{\"chunk\":\"AI服务暂时不可用（错误码:" + response.code() + "）\"}\n\n"));
                emitter.complete();
                return;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) { emitter.complete(); return; }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (AiConstants.SSE_DONE.equals(data)) break;
                        try {
                            AiChatResponse chunkResp = com.alibaba.fastjson.JSON.parseObject(data, AiChatResponse.class);
                            String chunk = chunkResp != null ? chunkResp.getFirstChoiceContent() : "";
                            if (!chunk.isEmpty()) {
                                fullContent.append(chunk);
                                String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                                emitter.send(SseEmitter.event().data(sendData));
                            }
                        } catch (Exception e) { /* ignore parse errors */ }
                    }
                }
            }

            // ★ 流式完成后不再自动保存，等待用户满意后再前端调保存接口

            emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
            emitter.complete();

        } catch (IOException e) {
            log.error("AI 流式异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"AI服务连接中断\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    // ==================== 功能1：选址建议 ====================

    @Override
    @Async("aiExecutor")
    public void siteSuggestionStream(String gridCode, SseEmitter emitter) {
        try {
            String dataContext;
            try {
                dataContext = aiDataAggregator.buildGridContextData(gridCode);
            } catch (Exception e) {
                log.error("网格数据聚合失败", e);
                emitter.send(SseEmitter.event().data("{\"chunk\":\"数据查询失败：" + e.getMessage() + "\"}\n\n"));
                emitter.complete();
                return;
            }
            String userPrompt = aiPromptBuilder.buildSiteSuggestionUserMessage(dataContext);
            chatStream(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter);
        } catch (Exception e) {
            log.error("选址建议异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成选址建议时出现错误\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    // ==================== 功能2：网点分析 ====================

    @Override
    @Async("aiExecutor")
    public void branchAnalysisStream(Long branchId, Integer year, SseEmitter emitter, boolean forceRefresh) {
        try {
            String entityKey = "branch_" + branchId + "_" + year;

            // 如果不强制刷新，尝试查询存量
            if (!forceRefresh) {
                AiAnalysisRecord cached = aiCacheService.getValidRecord(AiConstants.TYPE_BRANCH, entityKey);
                if (cached != null && StringUtils.isNotEmpty(cached.getContent())) {
                    // 存量直接推送
                    String content = cached.getContent();
                    // 模拟流式：分块发送
                    int chunkSize = 100;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                        String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                        emitter.send(SseEmitter.event().data(sendData));
                    }
                    emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
                    emitter.complete();
                    return;
                }
            }

            // 没有存量，调用 LLM 流式生成并保存
            String dataContext;
            try {
                dataContext = aiDataAggregator.buildBranchContextData(branchId, year);
            } catch (Exception e) {
                log.error("网点数据聚合失败", e);
                emitter.send(SseEmitter.event().data("{\"chunk\":\"数据查询失败：" + e.getMessage() + "\"}\n\n"));
                emitter.complete();
                return;
            }
            String userPrompt = aiPromptBuilder.buildBranchAnalysisUserMessage(dataContext);
            doStreamAndSave(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter,
                    AiConstants.TYPE_BRANCH, entityKey, "");
        } catch (Exception e) {
            log.error("网点分析异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成网点分析时出现错误\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    @Override
    public AiAnalysisRecord getBranchAnalysisCached(Long branchId, Integer year) {
        String entityKey = "branch_" + branchId + "_" + year;
        return aiCacheService.getValidRecord(AiConstants.TYPE_BRANCH, entityKey);
    }

    // ==================== 功能3：多网点对比 ====================

    @Override
    @Async("aiExecutor")
    public void branchComparisonStream(List<Long> branchIds, String city, Integer year, SseEmitter emitter) {
        try {
            String dataContext = aiDataAggregator.buildComparisonContextData(branchIds, city, year);
            String userPrompt = aiPromptBuilder.buildBranchComparisonUserMessage(branchIds.size(), dataContext);
            chatStream(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter);
        } catch (Exception e) {
            log.error("多网点对比异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成对比分析时出现错误\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    // ==================== 功能4：网格分析 ====================

    @Override
    @Async("aiExecutor")
    public void gridAnalysisStream(String gridCode, SseEmitter emitter, boolean forceRefresh) {
        try {
            String entityKey = "grid_" + gridCode;

            if (!forceRefresh) {
                AiAnalysisRecord cached = aiCacheService.getValidRecord(AiConstants.TYPE_GRID, entityKey);
                if (cached != null && StringUtils.isNotEmpty(cached.getContent())) {
                    String content = cached.getContent();
                    int chunkSize = 100;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                        String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                        emitter.send(SseEmitter.event().data(sendData));
                    }
                    emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
                    emitter.complete();
                    return;
                }
            }

            String dataContext;
            try {
                dataContext = aiDataAggregator.buildGridContextData(gridCode);
            } catch (Exception e) {
                log.error("网格数据聚合失败", e);
                emitter.send(SseEmitter.event().data("{\"chunk\":\"数据查询失败：" + e.getMessage() + "\"}\n\n"));
                emitter.complete();
                return;
            }
            String userPrompt = aiPromptBuilder.buildGridAnalysisUserMessage(dataContext);
            doStreamAndSave(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter,
                    AiConstants.TYPE_GRID, entityKey, "");
        } catch (Exception e) {
            log.error("网格分析异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成网格分析时出现错误\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    @Override
    public AiAnalysisRecord getGridAnalysisCached(String gridCode) {
        return aiCacheService.getValidRecord(AiConstants.TYPE_GRID, "grid_" + gridCode);
    }

    // ==================== 功能6：选址报告 ====================

    @Override
    public String generateSiteReport(String gridCode) {
        // 1. 获取报告内容（复用选址建议的 Prompt）
        String dataContext = aiDataAggregator.buildGridContextData(gridCode);
        String userPrompt = aiPromptBuilder.buildSiteReportUserMessage(dataContext);
        String reportContent = chat(aiPromptBuilder.buildSystemMessage(), userPrompt);

        // 2. 生成 Word 文档（由 WordReportGenerator 后续实现）
        // 暂时用纯文本返回，持久化在文件系统
        String reportId = UUID.randomUUID().toString().replace("-", "");
        String reportDir = jwMapConfig.getProfile() + "/reports/";
        File dir = new File(reportDir);
        if (!dir.exists()) dir.mkdirs();

        File reportFile = new File(reportDir + reportId + ".html");
        try (FileWriter fw = new FileWriter(reportFile)) {
            fw.write("<html><meta charset=\"utf-8\"><body>");
            fw.write("<h1>银行网点选址评估报告</h1>");
            fw.write("<p>网格编码：" + gridCode + "</p>");
            fw.write("<hr/>");
            fw.write(reportContent.replace("\n", "<br/>"));
            fw.write("</body></html>");
        } catch (IOException e) {
            log.error("报告写入失败", e);
            return null;
        }

        return reportId;
    }

    @Override
    public void downloadReport(String reportId, HttpServletResponse response) {
        String reportPath = jwMapConfig.getProfile() + "/reports/" + reportId + ".html";
        File file = new File(reportPath);
        if (!file.exists()) {
            response.setStatus(404);
            return;
        }

        try {
            response.setContentType("text/html; charset=utf-8");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("选址报告_" + reportId + ".html", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
        } catch (IOException e) {
            log.error("报告下载失败", e);
            response.setStatus(500);
        }
    }

    // ==================== 功能7：四象限分析 ====================

    @Override
    @Async("aiExecutor")
    public void quadrantAnalysisStream(String city, Integer year, SseEmitter emitter, boolean forceRefresh) {
        try {
            String entityKey = "quadrant_" + city + "_" + year;

            if (!forceRefresh) {
                AiAnalysisRecord cached = aiCacheService.getValidRecord(AiConstants.TYPE_QUADRANT, entityKey);
                if (cached != null && StringUtils.isNotEmpty(cached.getContent())) {
                    String content = cached.getContent();
                    int chunkSize = 100;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                        String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                        emitter.send(SseEmitter.event().data(sendData));
                    }
                    emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
                    emitter.complete();
                    return;
                }
            }

            String dataContext = aiDataAggregator.buildQuadrantContextData(city, year);
            String userPrompt = aiPromptBuilder.buildQuadrantAnalysisUserMessage(dataContext);
            doStreamAndSave(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter,
                    AiConstants.TYPE_QUADRANT, entityKey, city);
        } catch (Exception e) {
            log.error("四象限分析异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成四象限分析时出现错误\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    @Override
    public AiAnalysisRecord getQuadrantAnalysisCached(String city, Integer year) {
        return aiCacheService.getValidRecord(AiConstants.TYPE_QUADRANT, "quadrant_" + city + "_" + year);
    }

    // ==================== 功能7增强：单网点四象限分析 ====================

    @Override
    @Async("aiExecutor")
    public void perBranchQuadrantAnalysisStream(Long branchId, Integer year, SseEmitter emitter, boolean forceRefresh) {
        try {
            String entityKey = "quadrant_branch_" + branchId + "_" + year;

            if (!forceRefresh) {
                AiAnalysisRecord cached = aiCacheService.getValidRecord(AiConstants.TYPE_QUADRANT, entityKey);
                if (cached != null && StringUtils.isNotEmpty(cached.getContent())) {
                    String cachedContent = cached.getContent();
                    int chunkSize = 100;
                    for (int i = 0; i < cachedContent.length(); i += chunkSize) {
                        String chunk = cachedContent.substring(i, Math.min(i + chunkSize, cachedContent.length()));
                        String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                        emitter.send(SseEmitter.event().data(sendData));
                    }
                    emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
                    emitter.complete();
                    return;
                }
            }

            String dataContext = aiDataAggregator.buildPerBranchQuadrantContextData(branchId, year);
            String userPrompt = aiPromptBuilder.buildPerBranchQuadrantAnalysisUserMessage(dataContext);
            doStreamAndSave(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter,
                    AiConstants.TYPE_QUADRANT, entityKey, "");
        } catch (Exception e) {
            log.error("单网点四象限分析异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成四象限分析时出现错误\"}\n\n"));
            } catch (Exception ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    @Override
    public AiAnalysisRecord getPerBranchQuadrantCached(Long branchId, Integer year) {
        return aiCacheService.getValidRecord(AiConstants.TYPE_QUADRANT, "quadrant_branch_" + branchId + "_" + year);
    }

    // ==================== 功能8：迁址建议 ====================

    @Override
    @Async("aiExecutor")
    public void relocationSuggestionStream(Long branchId, Integer year, String city, SseEmitter emitter, boolean forceRefresh) {
        try {
            String entityKey = "relocation_" + branchId + "_" + year;

            if (!forceRefresh) {
                AiAnalysisRecord cached = aiCacheService.getValidRecord(AiConstants.TYPE_RELOCATION, entityKey);
                if (cached != null && StringUtils.isNotEmpty(cached.getContent())) {
                    String content = postProcessRelocationContent(cached.getContent());
                    int chunkSize = 100;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                        String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                        emitter.send(SseEmitter.event().data(sendData));
                    }
                    emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
                    emitter.complete();
                    return;
                }
            }

            String dataContext;
            try {
                dataContext = aiDataAggregator.buildRelocationContextData(branchId, year, city);
            } catch (Exception e) {
                log.error("迁址建议数据聚合失败", e);
                emitter.send(SseEmitter.event().data("{\"chunk\":\"数据查询失败：" + e.getMessage() + "\"}\n\n"));
                emitter.complete();
                return;
            }
            String userPrompt = aiPromptBuilder.buildRelocationSuggestionUserMessage(dataContext);
            // 不调 doStreamAndSave，使用专用方法做流式+后处理
            doRelocationStreamAndSave(aiPromptBuilder.buildSystemMessage(), userPrompt, emitter, entityKey, city);
        } catch (Exception e) {
            log.error("迁址建议异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"生成迁址建议时出现错误\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    @Override
    public AiAnalysisRecord getRelocationSuggestionCached(Long branchId, Integer year) {
        return aiCacheService.getValidRecord(AiConstants.TYPE_RELOCATION, "relocation_" + branchId + "_" + year);
    }

    // ==================== 满意度 & 过期 ====================

    @Override
    public void updateSatisfied(String analysisType, String entityKey, Integer satisfied) {
        aiCacheService.updateSatisfied(analysisType, entityKey, satisfied);
    }

    @Override
    public void invalidateByCity(String city) {
        aiCacheService.invalidateByCity(city);
    }

    @Override
    public void saveAnalysisContent(String analysisType, String entityKey, String city, String content) {
        if (content == null || content.isEmpty()) return;
        aiCacheService.saveRecord(analysisType, entityKey, city, content);
    }

    /**
     * 迁址建议专用流式方法：流式完成后对全文做后处理再推送并保存
     */
    private void doRelocationStreamAndSave(String systemPrompt, String userPrompt, SseEmitter emitter,
                                            String entityKey, String city) {
        List<AiChatRequest.Message> messages = Arrays.asList(
                new AiChatRequest.Message("system", systemPrompt),
                new AiChatRequest.Message("user", userPrompt)
        );
        AiChatRequest request = new AiChatRequest(
                aiProperties.getModel(), messages,
                aiProperties.getTemperature(), aiProperties.getMaxTokens(), true
        );

        StringBuilder fullContent = new StringBuilder();
        String json = com.alibaba.fastjson.JSON.toJSONString(request);
        RequestBody body = RequestBody.create(JSON_MEDIA, json);
        Request httpRequest = new Request.Builder()
                .url(aiProperties.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                emitter.send(SseEmitter.event().data("{\"chunk\":\"AI服务暂时不可用（错误码:" + response.code() + "）\"}\n\n"));
                emitter.complete();
                return;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) { emitter.complete(); return; }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (AiConstants.SSE_DONE.equals(data)) break;
                        try {
                            AiChatResponse chunkResp = com.alibaba.fastjson.JSON.parseObject(data, AiChatResponse.class);
                            String chunk = chunkResp != null ? chunkResp.getFirstChoiceContent() : "";
                            if (!chunk.isEmpty()) {
                                fullContent.append(chunk);
                                String sendData = "{\"chunk\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}\n\n";
                                emitter.send(SseEmitter.event().data(sendData));
                            }
                        } catch (Exception e) { /* ignore parse errors */ }
                    }
                }
            }

            // ★ 流式完成后不再自动保存，等待用户满意后再前端调保存接口
            // 但还是要发送 replaceAll 事件（给候选网格加链接标记）
            String processed = fullContent.length() > 0
                    ? postProcessRelocationContent(fullContent.toString())
                    : "";

            // 发送"替换全文"指令——前端收到此指令会用新内容替换当前全部内容
            String replaceData = "{\"replaceAll\":" + com.alibaba.fastjson.JSON.toJSONString(processed) + "}\n\n";
            emitter.send(SseEmitter.event().data(replaceData));
            emitter.send(SseEmitter.event().data("{\"chunk\":\"\"}\n\n"));
            emitter.complete();

        } catch (IOException e) {
            log.error("AI 流式异常", e);
            try {
                emitter.send(SseEmitter.event().data("{\"chunk\":\"AI服务连接中断\"}\n\n"));
            } catch (IOException ex) { /* ignore */ }
            emitter.completeWithError(e);
        }
    }

    /**
     * 后处理迁址建议内容：给候选网格编码添加可点击链接标记
     * 匹配格式如 "候选1：G10001" 或 "候选网格1：G10001" 等
     */
    private String postProcessRelocationContent(String content) {
        if (content == null || content.isEmpty()) return content;
        // 匹配 AI 实际输出格式中的候选网格行，如：
        //   1. **候选网格1：GZGY南明区0022**
        //   - **候选网格3：GZGY乌当区0014**
        // 提取网格编码（如 GZGY南明区0022），替换为可点击链接
        // 格式：编码以 GZ/GD 等字母开头，后跟中文区名 + 4位数字
        return content.replaceAll(
            "(?m)^(\\s*(?:[-•*]|\\d+\\.)\\s*\\*{0,2}\\s*候选[^：:]*?[:：]\\s*\\*{0,2})([A-Z]{2,4}[\\u4e00-\\u9fa5]+\\d{4})(\\s*\\*{0,2})",
            "$1[$2](grid:$2)$3"
        );
    }
}

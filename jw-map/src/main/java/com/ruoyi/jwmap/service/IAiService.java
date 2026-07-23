package com.ruoyi.jwmap.service;

import com.ruoyi.jwmap.domain.AiAnalysisRecord;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * AI 功能服务接口
 */
public interface IAiService {

    /** 非流式调用 LLM API */
    String chat(String systemPrompt, String userPrompt);

    /** 流式调用 LLM API，通过 SseEmitter 推送 */
    void chatStream(String systemPrompt, String userPrompt, SseEmitter emitter);

    /** 功能1：选址建议 — 流式 */
    void siteSuggestionStream(String gridCode, SseEmitter emitter);

    /** 功能2：网点分析 — 流式 */
    void branchAnalysisStream(Long branchId, Integer year, SseEmitter emitter, boolean forceRefresh);

    /** 功能2：查询网点分析存量 */
    AiAnalysisRecord getBranchAnalysisCached(Long branchId, Integer year);

    /** 功能3：多网点对比 — 流式 */
    void branchComparisonStream(List<Long> branchIds, String city, Integer year, SseEmitter emitter);

    /** 功能4：网格分析 — 流式 */
    void gridAnalysisStream(String gridCode, SseEmitter emitter, boolean forceRefresh);

    /** 功能4：查询网格分析存量 */
    AiAnalysisRecord getGridAnalysisCached(String gridCode);

    /** 功能6：生成选址报告 */
    String generateSiteReport(String gridCode);

    /** 功能6：下载报告 */
    void downloadReport(String reportId, HttpServletResponse response);

    /** 功能7：四象限分析 — 全市流式 */
    void quadrantAnalysisStream(String city, Integer year, SseEmitter emitter, boolean forceRefresh);

    /** 功能7：四象限分析 — 单网点流式 */
    void perBranchQuadrantAnalysisStream(Long branchId, Integer year, SseEmitter emitter, boolean forceRefresh);

    /** 功能7：查询四象限分析存量 */
    AiAnalysisRecord getQuadrantAnalysisCached(String city, Integer year);

    /** 功能7：查询单网点四象限分析存量 */
    AiAnalysisRecord getPerBranchQuadrantCached(Long branchId, Integer year);

    /** 更新满意度评价 */
    void updateSatisfied(String analysisType, String entityKey, Integer satisfied);

    /** 标记该城市所有分析记录过期 */
    void invalidateByCity(String city);

    /**
     * 保存 AI 分析内容（用户满意后手动触发）
     * @param analysisType 分析类型
     * @param entityKey 实体标识
     * @param city 城市
     * @param content 分析内容
     */
    void saveAnalysisContent(String analysisType, String entityKey, String city, String content);

    /** 功能8：迁址建议 — 流式 */
    void relocationSuggestionStream(Long branchId, Integer year, String city, SseEmitter emitter, boolean forceRefresh);

    /** 功能8：查询迁址建议存量 */
    AiAnalysisRecord getRelocationSuggestionCached(Long branchId, Integer year);
}

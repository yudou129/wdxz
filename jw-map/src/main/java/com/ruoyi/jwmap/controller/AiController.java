package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.AiAnalysisRecord;
import com.ruoyi.jwmap.constant.AiConstants;
import com.ruoyi.jwmap.service.IAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * AI 功能控制器
 * 提供 7 个 AI 分析功能的 SSE 流式端点和 AjaxResult 端点
 *
 * SSE 数据格式：
 *   data: {"chunk":"分析文本片段..."}\n\n
 *   结束符号：data: [DONE]\n\n
 */
@RestController
@RequestMapping("/jwmap/ai")
public class AiController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private IAiService aiService;

    // ==================== 流式端点（SSE） ====================

    /**
     * 功能1：选址建议 — 流式
     */
    @GetMapping("/site-suggestion/stream/{gridCode}")
    public SseEmitter siteSuggestionStream(@PathVariable String gridCode) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3分钟超时
        aiService.siteSuggestionStream(gridCode, emitter);
        return emitter;
    }

    /**
     * 功能2：网点分析 — 流式
     * ?forceRefresh=true 表示强制重新生成
     */
    @GetMapping("/branch-analysis/stream/{branchId}/{year}")
    public SseEmitter branchAnalysisStream(
            @PathVariable Long branchId,
            @PathVariable Integer year,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        SseEmitter emitter = new SseEmitter(180_000L);
        aiService.branchAnalysisStream(branchId, year, emitter, forceRefresh);
        return emitter;
    }

    /**
     * 功能3：多网点对比 — 流式
     */
    @GetMapping("/branch-comparison/stream")
    public SseEmitter branchComparisonStream(
            @RequestParam String branchIds,
            @RequestParam String city,
            @RequestParam Integer year) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String[] parts = branchIds.split(",");
        List<Long> ids = new java.util.ArrayList<>();
        for (String p : parts) {
            try {
                ids.add(Long.parseLong(p.trim()));
            } catch (NumberFormatException e) {
                // 忽略非法参数，前端会检测到对比网点不足2个而提示用户
                log.warn("非法 branchId 参数: {}", p);
            }
        }
        aiService.branchComparisonStream(ids, city, year, emitter);
        return emitter;
    }

    /**
     * 功能4：网格分析 — 流式
     */
    @GetMapping("/grid-analysis/stream/{gridCode}")
    public SseEmitter gridAnalysisStream(
            @PathVariable String gridCode,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        SseEmitter emitter = new SseEmitter(180_000L);
        aiService.gridAnalysisStream(gridCode, emitter, forceRefresh);
        return emitter;
    }

    /**
     * 功能7：四象限分析 — 全市流式
     */
    @GetMapping("/quadrant-analysis/stream/{city}/{year}")
    public SseEmitter quadrantAnalysisStream(
            @PathVariable String city,
            @PathVariable Integer year,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        SseEmitter emitter = new SseEmitter(180_000L);
        aiService.quadrantAnalysisStream(city, year, emitter, forceRefresh);
        return emitter;
    }

    /**
     * 功能7：四象限分析 — 单网点流式
     */
    @GetMapping("/quadrant-analysis/stream/per-branch")
    public SseEmitter perBranchQuadrantAnalysisStream(
            @RequestParam Long branchId,
            @RequestParam Integer year,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        SseEmitter emitter = new SseEmitter(180_000L);
        aiService.perBranchQuadrantAnalysisStream(branchId, year, emitter, forceRefresh);
        return emitter;
    }

    // ==================== 常规端点（AjaxResult） ====================

    /**
     * 功能6：生成选址报告
     */
    @PostMapping("/site-report/{gridCode}")
    public AjaxResult siteReport(@PathVariable String gridCode) {
        String reportId = aiService.generateSiteReport(gridCode);
        if (reportId == null) {
            return error("报告生成失败");
        }
        return success(reportId);
    }

    /**
     * 功能6：下载报告
     */
    @GetMapping("/report/download/{reportId}")
    public void downloadReport(@PathVariable String reportId, HttpServletResponse response) {
        aiService.downloadReport(reportId, response);
    }

    /**
     * 用户满意度评价
     */
    @PostMapping("/feedback")
    public AjaxResult feedback(@RequestBody Map<String, Object> body) {
        String analysisType = (String) body.get("analysisType");
        String entityKey = (String) body.get("entityKey");
        Integer satisfied = (Integer) body.get("satisfied");
        if (analysisType == null || entityKey == null || satisfied == null) {
            return error("参数不完整");
        }
        aiService.updateSatisfied(analysisType, entityKey, satisfied);
        return success("评价成功");
    }

    /**
     * 保存 AI 分析内容（用户满意后手动触发）
     */
    @PostMapping("/save")
    public AjaxResult saveAnalysis(@RequestBody Map<String, Object> body) {
        String analysisType = (String) body.get("analysisType");
        String entityKey = (String) body.get("entityKey");
        String city = body.containsKey("city") ? (String) body.get("city") : "";
        String content = (String) body.get("content");
        if (analysisType == null || entityKey == null || content == null || content.isEmpty()) {
            return error("参数不完整");
        }
        aiService.saveAnalysisContent(analysisType, entityKey, city, content);
        return success("保存成功");
    }

    // ==================== 功能8：迁址建议 ====================

    /**
     * 功能8：迁址建议 — 流式
     */
    @GetMapping("/relocation-suggestion/stream/{branchId}/{year}")
    public SseEmitter relocationSuggestionStream(
            @PathVariable Long branchId,
            @PathVariable Integer year,
            @RequestParam String city,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        SseEmitter emitter = new SseEmitter(180_000L);
        aiService.relocationSuggestionStream(branchId, year, city, emitter, forceRefresh);
        return emitter;
    }

    /**
     * 功能8：查询迁址建议存量记录
     */
    @GetMapping("/relocation-suggestion/cached")
    public AjaxResult getRelocationSuggestionCached(
            @RequestParam Long branchId,
            @RequestParam Integer year) {
        AiAnalysisRecord record = aiService.getRelocationSuggestionCached(branchId, year);
        if (record != null) {
            return success(record.getContent());
        }
        return AjaxResult.success("暂无缓存记录");
    }

    // ==================== 存量查询端点 ====================

    /**
     * 查询网点分析存量记录
     */
    @GetMapping("/branch-analysis/{branchId}/{year}")
    public AjaxResult getBranchAnalysisCached(
            @PathVariable Long branchId,
            @PathVariable Integer year) {
        AiAnalysisRecord record = aiService.getBranchAnalysisCached(branchId, year);
        if (record != null) {
            return success(record.getContent());
        }
        return AjaxResult.success("暂无缓存记录");
    }

    /**
     * 查询网格分析存量记录
     */
    @GetMapping("/grid-analysis/{gridCode}")
    public AjaxResult getGridAnalysisCached(@PathVariable String gridCode) {
        AiAnalysisRecord record = aiService.getGridAnalysisCached(gridCode);
        if (record != null) {
            return success(record.getContent());
        }
        return AjaxResult.success("暂无缓存记录");
    }

    /**
     * 查询四象限分析存量记录
     */
    @GetMapping("/quadrant-analysis/{city}/{year}")
    public AjaxResult getQuadrantAnalysisCached(
            @PathVariable String city,
            @PathVariable Integer year) {
        AiAnalysisRecord record = aiService.getQuadrantAnalysisCached(city, year);
        if (record != null) {
            return success(record.getContent());
        }
        return AjaxResult.success("暂无缓存记录");
    }

    /**
     * 查询单网点四象限分析存量记录
     */
    @GetMapping("/quadrant-analysis/per-branch/{branchId}/{year}")
    public AjaxResult getPerBranchQuadrantCached(
            @PathVariable Long branchId,
            @PathVariable Integer year) {
        AiAnalysisRecord record = aiService.getPerBranchQuadrantCached(branchId, year);
        if (record != null) {
            return success(record.getContent());
        }
        return AjaxResult.success("暂无缓存记录");
    }
}

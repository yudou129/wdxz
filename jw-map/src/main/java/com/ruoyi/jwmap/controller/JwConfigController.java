package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.JwScoreCategoryConfig;
import com.ruoyi.jwmap.mapper.JwScoreCategoryConfigMapper;
import com.ruoyi.jwmap.mapper.JwIndicatorConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * jw-map 配置管理 Controller
 * 提供指标/权重/分类配置的查询接口
 */
@RestController
@RequestMapping("/jwmap/config")
public class JwConfigController extends BaseController {

    @Autowired
    private JwScoreCategoryConfigMapper categoryConfigMapper;

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    /**
     * 查询所有分类及关联指标
     */
    @GetMapping("/categories")
    public AjaxResult getCategories() {
        List<JwScoreCategoryConfig> configs = categoryConfigMapper.selectAllActive();
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (JwScoreCategoryConfig c : configs) {
            result.computeIfAbsent(c.getCategoryCode(), k -> new ArrayList<>())
                .add(new HashMap<String, Object>() {{
                    put("indicatorCode", c.getIndicatorCode());
                    put("sortOrder", c.getSortOrder());
                }});
        }
        // 附加分类名称
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : result.entrySet()) {
            String catName = configs.stream()
                .filter(c -> c.getCategoryCode().equals(entry.getKey()))
                .findFirst().map(JwScoreCategoryConfig::getCategoryName).orElse(entry.getKey());
            Map<String, Object> catData = new LinkedHashMap<>();
            catData.put("name", catName);
            catData.put("indicators", entry.getValue());
            output.put(entry.getKey(), catData);
        }
        return success(output);
    }

    /**
     * 查询活跃加权指标列表
     */
    @GetMapping("/indicators")
    public AjaxResult getIndicators(@RequestParam(required = false) String keyword) {
        return success(indicatorConfigMapper.selectActiveWeighted().stream()
            .filter(i -> keyword == null || keyword.isEmpty()
                || i.getIndicatorCode().contains(keyword)
                || (i.getIndicatorName() != null && i.getIndicatorName().contains(keyword)))
            .map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("indicatorId", i.getIndicatorId());
                m.put("indicatorCode", i.getIndicatorCode());
                m.put("indicatorName", i.getIndicatorName());
                m.put("categoryLevel1", i.getCategoryLevel1());
                m.put("categoryLevel2", i.getCategoryLevel2());
                return m;
            })
            .collect(Collectors.toList()));
    }
}

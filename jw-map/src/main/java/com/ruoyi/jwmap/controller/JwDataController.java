package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据查看控制器
 */
@RestController
@RequestMapping("/jwmap/data")
public class JwDataController extends BaseController {

    @Autowired private JwPoiInfoMapper poiInfoMapper;
    @Autowired private JwGridMetaMapper gridMetaMapper;
    @Autowired private JwGridScoreMapper gridScoreMapper;
    @Autowired private JwBranchInfoMapper branchInfoMapper;
    @Autowired private JwBranchScoreMapper branchScoreMapper;
    @Autowired private JwIndicatorConfigMapper indicatorConfigMapper;
    @Autowired private JwGridSummaryMapper gridSummaryMapper;
    @Autowired private JwBranchSummaryMapper branchSummaryMapper;

    // ===== POI =====
    @GetMapping("/poi/list")
    public AjaxResult poiList(@RequestParam(required = false) String city) {
        JwPoiInfo query = new JwPoiInfo();
        if (city != null && !city.isEmpty()) query.setCity(city);
        List<JwPoiInfo> list = poiInfoMapper.selectPoiInfoList(query);
        return success(list);
    }

    // ===== 指标配置 =====
    @GetMapping("/indicator/list")
    public AjaxResult indicatorList(@RequestParam(required = false) String sourceTable) {
        List<JwIndicatorConfig> list;
        if (sourceTable != null && !sourceTable.isEmpty()) {
            list = indicatorConfigMapper.selectBySourceTable(sourceTable);
        } else {
            list = indicatorConfigMapper.selectActiveWeighted();
        }
        return success(list);
    }

    // ===== 网格 =====
    @GetMapping("/grid/list")
    public AjaxResult gridList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) {
            return success(new ArrayList<>());
        }
        List<JwGridMeta> list = gridMetaMapper.selectByCity(city);
        // 附加得分信息
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwGridMeta meta : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gridCode", meta.getGridCode());
            item.put("longitude", meta.getLongitude());
            item.put("latitude", meta.getLatitude());
            item.put("city", meta.getCity());
            item.put("poiCount", meta.getPoiCount());
            JwGridScore score = gridScoreMapper.selectByGridCode(meta.getGridCode());
            if (score != null) {
                item.put("siteScore", score.getSiteScore());
            }
            result.add(item);
        }
        return success(result);
    }

    @GetMapping("/grid/cities")
    public AjaxResult gridCities() {
        return success(gridMetaMapper.selectDistinctCities());
    }

    // ===== 网点 =====
    @GetMapping("/branch/list")
    public AjaxResult branchList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) {
            return success(new ArrayList<>());
        }
        List<JwBranchInfo> list = branchInfoMapper.selectByCity(city);
        return success(list);
    }

    @GetMapping("/branch/score/{city}/{year}")
    public AjaxResult branchScore(@PathVariable String city, @PathVariable Integer year) {
        List<JwBranchScore> scores = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
        return success(scores);
    }

    @GetMapping("/branch/summary/{city}/{year}")
    public AjaxResult branchSummary(@PathVariable String city, @PathVariable Integer year) {
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, year);
        return success(summaries);
    }

    // ===== 权重 =====
    @Autowired private JwExternalResWeightMapper externalWeightMapper;
    @Autowired private JwBranchEffWeightMapper branchEffWeightMapper;

    @GetMapping("/weight/external")
    public AjaxResult externalWeightList() {
        return success(externalWeightMapper.selectAll());
    }

    @GetMapping("/weight/branchEfficiency")
    public AjaxResult branchEfficiencyWeightList() {
        return success(branchEffWeightMapper.selectAll());
    }

    // ===== 人口热力 =====
    @Autowired private JwPopulationHeatMapper populationHeatMapper;

    @GetMapping("/population/grids/{city}")
    public AjaxResult getDistinctGrids(@PathVariable String city) {
        return success(populationHeatMapper.selectDistinctGridCodesByCity(city));
    }
}

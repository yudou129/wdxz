package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    @Autowired private JwGridDataRawMapper gridDataRawMapper;
    @Autowired private JwBranchIndicatorMapper branchIndicatorMapper;
    @Autowired private JwPeerBankInfoMapper peerBankInfoMapper;

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
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        // Batch load scores
        Map<String, Double> scoreMap = new HashMap<>();
        for (JwGridScore s : gridScoreMapper.selectByCity(city)) {
            if (s.getSiteScore() != null) scoreMap.put(s.getGridCode(), s.getSiteScore());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwGridMeta meta : metas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gridCode", meta.getGridCode());
            item.put("longitude", meta.getLongitude());
            item.put("latitude", meta.getLatitude());
            item.put("city", meta.getCity());
            item.put("siteScore", scoreMap.get(meta.getGridCode()));
            result.add(item);
        }
        return success(result);
    }

    @GetMapping("/grid/cities")
    public AjaxResult gridCities() {
        return success(gridMetaMapper.selectDistinctCities());
    }

    // ===== 地图可视化 =====

    @GetMapping("/grid/score/byCity/{city}")
    public AjaxResult gridScoreByCity(@PathVariable String city) {
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);

        // Batch load scores: gridCode → siteScore
        Map<String, Double> scoreMap = new HashMap<>();
        for (JwGridScore s : gridScoreMapper.selectByCity(city)) {
            if (s.getSiteScore() != null) scoreMap.put(s.getGridCode(), s.getSiteScore());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (JwGridMeta meta : metas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gridCode", meta.getGridCode());
            item.put("longitude", meta.getLongitude());
            item.put("latitude", meta.getLatitude());
            item.put("westLongitude", meta.getWestLongitude());
            item.put("eastLongitude", meta.getEastLongitude());
            item.put("northLatitude", meta.getNorthLatitude());
            item.put("southLatitude", meta.getSouthLatitude());
            item.put("district", meta.getDistrict());
            item.put("siteScore", scoreMap.get(meta.getGridCode()));
            result.add(item);
        }
        result.sort((a, b) -> {
            Double sa = (Double) a.get("siteScore");
            Double sb = (Double) b.get("siteScore");
            if (sa == null) return 1; if (sb == null) return -1; return sb.compareTo(sa);
        });
        return success(result);
    }

    @GetMapping("/grid/indicators/{gridCode}")
    public AjaxResult gridIndicators(@PathVariable String gridCode) {
        List<JwGridDataRaw> rawList = gridDataRawMapper.selectByGridCode(gridCode);
        if (rawList.isEmpty()) {
            return success(Collections.emptyList());
        }

        // Batch load indicator configs for category info
        List<String> codes = rawList.stream()
            .map(JwGridDataRaw::getIndicatorCode)
            .collect(Collectors.toList());
        List<JwIndicatorConfig> configs = indicatorConfigMapper.selectByCodes(codes);
        Map<String, JwIndicatorConfig> configMap = configs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (JwGridDataRaw raw : rawList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("indicatorCode", raw.getIndicatorCode());
            item.put("indicatorValue", raw.getIndicatorValue());
            JwIndicatorConfig cfg = configMap.get(raw.getIndicatorCode());
            item.put("categoryLevel1", cfg != null ? cfg.getCategoryLevel1() : null);
            item.put("categoryLevel2", cfg != null ? cfg.getCategoryLevel2() : null);
            result.add(item);
        }
        return success(result);
    }

    @GetMapping("/branch/score/detail/{branchId}/{year}")
    public AjaxResult branchScoreDetail(@PathVariable Long branchId, @PathVariable Integer year) {
        List<JwBranchScore> list = branchScoreMapper.selectByBranchAndYear(branchId, year);
        return success(list);
    }

    @GetMapping("/grid/ranking/{city}")
    public TableDataInfo gridRanking(@PathVariable String city) {
        startPage();
        List<JwGridScore> list = gridScoreMapper.selectByCity(city);
        return getDataTable(list);
    }

    @GetMapping("/branch/ranking/{city}/{year}")
    public TableDataInfo branchRanking(@PathVariable String city, @PathVariable Integer year) {
        startPage();
        List<JwBranchScore> list = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
        return getDataTable(list);
    }

    @GetMapping("/grid/branches/{gridCode}")
    public AjaxResult gridBranches(@PathVariable String gridCode) {
        List<JwBranchInfo> list = branchInfoMapper.selectByGridCode(gridCode);
        return success(list);
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

    // ===== 同业银行 =====
    @GetMapping("/peerBank/list")
    public AjaxResult peerBankList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) {
            return success(new ArrayList<>());
        }
        List<JwPeerBankInfo> list = peerBankInfoMapper.selectByCity(city);
        return success(list);
    }

    @GetMapping("/branch/indicators/{branchId}/{year}")
    public AjaxResult branchIndicators(@PathVariable Long branchId, @PathVariable Integer year) {
        List<JwBranchIndicator> list = branchIndicatorMapper.selectByBranchAndYear(branchId, year, "calc");
        return success(list);
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

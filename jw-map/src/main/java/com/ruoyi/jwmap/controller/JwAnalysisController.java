package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.JwBranchScore;
import com.ruoyi.jwmap.domain.JwGridScore;
import com.ruoyi.jwmap.mapper.JwBranchScoreMapper;
import com.ruoyi.jwmap.mapper.JwGridScoreMapper;
import com.ruoyi.jwmap.service.IJwDataQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析统计控制器（四象限/维度统计/三聚焦排名）
 */
@RestController
@RequestMapping("/jwmap/data")
public class JwAnalysisController extends BaseController {

    @Autowired
    private IJwDataQueryService dataQueryService;

    @Autowired
    private JwBranchScoreMapper branchScoreMapper;

    @Autowired
    private JwGridScoreMapper gridScoreMapper;

    // ===== 四象限分析 =====

    /**
     * 查询四象限结果 — 优先从 jw_branch_score 查已存储的结果，无数据时实时计算
     */
    @GetMapping("/quadrant/{city}/{year}")
    public AjaxResult getQuadrantData(@PathVariable String city, @PathVariable Integer year) {
        // 1. 尝试从库中读取已计算的象限结果
        List<JwBranchScore> stored = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
        boolean hasQuadrant = stored.stream().anyMatch(s -> s.getQuadrant() != null);

        if (hasQuadrant) {
            return success(buildQuadrantResultFromStored(stored, city));
        }

        // 2. 回退到实时计算
        return success(computeQuadrantRealtime(city, year));
    }

    /**
     * 手动执行四象限计算 — 接受分界方式参数，结果写入 jw_branch_score
     */
    @PostMapping("/quadrant/compute")
    public AjaxResult computeQuadrant(@RequestBody Map<String, Object> params) {
        String city = (String) params.get("city");
        if (city == null || city.isEmpty()) {
            return error("城市不能为空");
        }
        Integer dataYear;
        try {
            dataYear = params.get("dataYear") instanceof Integer
                ? (Integer) params.get("dataYear")
                : Integer.parseInt(String.valueOf(params.get("dataYear")));
        } catch (Exception e) {
            return error("年份参数无效");
        }
        String boundaryMethod = (String) params.getOrDefault("boundaryMethod", "median");

        // 查 overall 评分
        List<JwBranchScore> overallScores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, "overall");
        if (overallScores.size() < 2) {
            return error("数据不足，至少需要2个网点才能计算四象限");
        }

        // 获取 siteScore（该城市所有网格的排名）
        List<String> gridCodes = overallScores.stream()
            .map(JwBranchScore::getGridCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (gridCodes.isEmpty()) {
            return error("网点未关联网格，无法计算");
        }
        // 查该城市所有网格的 siteScore，用于计算每个网格在全城中的排名
        List<JwGridScore> allGridScores = gridScoreMapper.selectByCity(city);
        Map<String, Double> siteScoreMap = allGridScores.stream()
            .collect(Collectors.toMap(JwGridScore::getGridCode, JwGridScore::getSiteScore, (a, b) -> a));
        // SQL 已按 site_score DESC 排序，索引 i 即为排名
        Map<String, Integer> gridRankMap = new HashMap<>();
        for (int i = 0; i < allGridScores.size(); i++) {
            gridRankMap.put(allGridScores.get(i).getGridCode(), i + 1);
        }

        // 按 branchScore 排序 -> branchRank
        List<JwBranchScore> sorted = new ArrayList<>(overallScores);
        sorted.sort((a, b) -> Double.compare(
            b.getCategoryScore() != null ? b.getCategoryScore() : 0,
            a.getCategoryScore() != null ? a.getCategoryScore() : 0));

        Map<Long, Integer> branchRankMap = new HashMap<>();
        Map<Long, Integer> siteRankMap = new HashMap<>();
        List<JwBranchScore> validBranches = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            JwBranchScore s = sorted.get(i);
            Double ss = siteScoreMap.get(s.getGridCode());
            if (ss == null) continue;
            branchRankMap.put(s.getBranchId(), i + 1);
            validBranches.add(s);
        }
        if (validBranches.size() < 2) {
            return error("有网格评分的网点不足2个");
        }

        // siteRank = 该网点所在网格在全城所有网格中的排名
        for (JwBranchScore s : validBranches) {
            Integer rank = gridRankMap.get(s.getGridCode());
            siteRankMap.put(s.getBranchId(), rank != null ? rank : validBranches.size());
        }

        int total = validBranches.size();
        int totalGrids = allGridScores.size();
        int medianBranchRank, medianSiteRank;

        if ("custom".equals(boundaryMethod)) {
            try {
                medianBranchRank = params.get("branchRankCutoff") instanceof Integer
                    ? (Integer) params.get("branchRankCutoff")
                    : Integer.parseInt(String.valueOf(params.get("branchRankCutoff")));
                medianSiteRank = params.get("siteRankCutoff") instanceof Integer
                    ? (Integer) params.get("siteRankCutoff")
                    : Integer.parseInt(String.valueOf(params.get("siteRankCutoff")));
            } catch (Exception e) {
                return error("自定义分界参数无效");
            }
            if (medianBranchRank < 1 || medianSiteRank < 1) {
                return error("分界排名必须大于0");
            }
        } else {
            medianBranchRank = total / 2;
            medianSiteRank = totalGrids / 2;
        }

        // 分配象限 + 更新 DB
        List<JwBranchScore> toUpdate = new ArrayList<>();
        for (JwBranchScore s : validBranches) {
            int br = branchRankMap.get(s.getBranchId());
            int sr = siteRankMap.get(s.getBranchId());
            String quadrant;
            if (br <= medianBranchRank && sr <= medianSiteRank) quadrant = "Q1";
            else if (br <= medianBranchRank && sr > medianSiteRank) quadrant = "Q2";
            else if (br > medianBranchRank && sr > medianSiteRank) quadrant = "Q3";
            else quadrant = "Q4";

            s.setQuadrant(quadrant);
            s.setMedianSiteRank(medianSiteRank);
            s.setMedianBranchRank(medianBranchRank);
            toUpdate.add(s);
        }
        branchScoreMapper.batchUpdateQuadrant(toUpdate);

        return success(buildQuadrantResultFromList(toUpdate, siteScoreMap, branchRankMap, siteRankMap,
            medianSiteRank, medianBranchRank, boundaryMethod, totalGrids));
    }

    // ===== 分维度统计 =====

    @GetMapping("/dimension/stats/{city}/{year}")
    public AjaxResult getDimensionStats(@PathVariable String city, @PathVariable Integer year,
                                         @RequestParam(defaultValue = "district") String dimension) {
        return success(dataQueryService.queryDimensionStats(city, year, dimension));
    }

    // ===== 三聚焦分类排名 =====

    @GetMapping("/ranking/threeFocus/{city}/{year}")
    public AjaxResult getThreeFocusRanking(@PathVariable String city, @PathVariable Integer year) {
        return success(dataQueryService.queryThreeFocusRanking(city, year));
    }

    // ===== 构建响应 =====

    /** 从已存储的 jw_branch_score 构建四象限响应 */
    private Map<String, Object> buildQuadrantResultFromStored(List<JwBranchScore> stored, String city) {
        // 获取分界值（取第一条非空的）
        int siteCutoff = stored.stream()
            .filter(s -> s.getMedianSiteRank() != null)
            .findFirst().map(JwBranchScore::getMedianSiteRank).orElse(stored.size() / 2);
        int branchCutoff = stored.stream()
            .filter(s -> s.getMedianBranchRank() != null)
            .findFirst().map(JwBranchScore::getMedianBranchRank).orElse(stored.size() / 2);

        // 统计有象限标注的网点数，若无则返回空
        long labeledCount = stored.stream().filter(s -> s.getQuadrant() != null).count();
        if (labeledCount == 0) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("medianSiteScore", 0);
            empty.put("medianBranchScore", 0);
            empty.put("medianSiteRank", siteCutoff);
            empty.put("medianBranchRank", branchCutoff);
            empty.put("totalBranches", 0);
            empty.put("quadrants", new LinkedHashMap<>());
            empty.put("allData", new ArrayList<>());
            return empty;
        }

        // 获取该城市所有网格评分（SQL 已按 site_score DESC 排序）
        List<JwGridScore> allGrids = gridScoreMapper.selectByCity(city);
        Map<String, Integer> gridRankMap = new HashMap<>();
        for (int i = 0; i < allGrids.size(); i++) {
            gridRankMap.put(allGrids.get(i).getGridCode(), i + 1);
        }
        Map<String, Double> siteScoreMap = allGrids.stream()
            .collect(Collectors.toMap(JwGridScore::getGridCode, JwGridScore::getSiteScore, (a, b) -> a));

        Map<String, List<Map<String, Object>>> quadrants = new LinkedHashMap<>();
        quadrants.put("Q1", new ArrayList<>());
        quadrants.put("Q2", new ArrayList<>());
        quadrants.put("Q3", new ArrayList<>());
        quadrants.put("Q4", new ArrayList<>());

        List<Map<String, Object>> allData = new ArrayList<>();
        for (JwBranchScore s : stored) {
            if (s.getQuadrant() == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("branchId", s.getBranchId());
            item.put("branchName", s.getSecondaryBranch());
            item.put("primaryBranch", s.getPrimaryBranch());
            item.put("gridCode", s.getGridCode());
            item.put("siteScore", siteScoreMap.getOrDefault(s.getGridCode(), 0.0));
            item.put("branchScore", s.getCategoryScore());
            item.put("siteRank", gridRankMap.getOrDefault(s.getGridCode(), 0));
            item.put("branchRank", s.getRankNum());
            item.put("quadrant", s.getQuadrant());
            item.put("quadrantLabel", getQuadrantLabel(s.getQuadrant()));
            quadrants.get(s.getQuadrant()).add(item);
            allData.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("medianSiteScore", 0);
        result.put("medianBranchScore", 0);
        result.put("medianSiteRank", siteCutoff);
        result.put("medianBranchRank", branchCutoff);
        result.put("totalBranches", allData.size());
        result.put("totalGrids", allGrids.size());
        result.put("quadrants", quadrants);
        result.put("allData", allData);
        return result;
    }

    /** 从计算中的列表构建四象限响应 */
    private Map<String, Object> buildQuadrantResultFromList(
            List<JwBranchScore> validBranches, Map<String, Double> siteScoreMap,
            Map<Long, Integer> branchRankMap, Map<Long, Integer> siteRankMap,
            int medianSiteRank, int medianBranchRank, String boundaryMethod,
            int totalGrids) {

        Map<String, List<Map<String, Object>>> quadrants = new LinkedHashMap<>();
        quadrants.put("Q1", new ArrayList<>());
        quadrants.put("Q2", new ArrayList<>());
        quadrants.put("Q3", new ArrayList<>());
        quadrants.put("Q4", new ArrayList<>());

        List<Map<String, Object>> allData = new ArrayList<>();
        for (JwBranchScore s : validBranches) {
            double siteVal = siteScoreMap.getOrDefault(s.getGridCode(), 0.0);
            int siteRank = siteRankMap.getOrDefault(s.getBranchId(), 0);
            int branchRank = branchRankMap.getOrDefault(s.getBranchId(), 0);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("branchId", s.getBranchId());
            item.put("branchName", s.getSecondaryBranch());
            item.put("primaryBranch", s.getPrimaryBranch());
            item.put("gridCode", s.getGridCode());
            item.put("longitude", null);
            item.put("latitude", null);
            item.put("siteScore", siteVal);
            item.put("branchScore", s.getCategoryScore());
            item.put("siteRank", siteRank);
            item.put("branchRank", branchRank);
            item.put("quadrant", s.getQuadrant());
            item.put("quadrantLabel", getQuadrantLabel(s.getQuadrant()));

            quadrants.get(s.getQuadrant()).add(item);
            allData.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("medianSiteScore", 0);
        result.put("medianBranchScore", 0);
        result.put("medianSiteRank", medianSiteRank);
        result.put("medianBranchRank", medianBranchRank);
        result.put("totalBranches", allData.size());
        result.put("totalGrids", totalGrids);
        result.put("quadrants", quadrants);
        result.put("allData", allData);
        result.put("boundaryMethod", boundaryMethod);
        return result;
    }

    /** 实时计算四象限（保留原有逻辑） */
    private Map<String, Object> computeQuadrantRealtime(String city, Integer year) {
        List<Map<String, Object>> rows = dataQueryService.queryQuadrantData(city, year);

        List<Double> branchScores = new ArrayList<>();
        List<Map<String, Object>> validRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object ss = row.get("siteScore");
            Object bs = row.get("branchScore");
            if (ss != null && bs != null) {
                branchScores.add(((Number) bs).doubleValue());
                validRows.add(row);
            }
        }

        if (validRows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("medianSiteScore", 0);
            empty.put("medianBranchScore", 0);
            empty.put("medianSiteRank", 0);
            empty.put("medianBranchRank", 0);
            empty.put("quadrants", new LinkedHashMap<>());
            empty.put("allData", new ArrayList<>());
            return empty;
        }

        // 全城网格评分排名（SQL 已按 site_score DESC 排序，索引 i 即为排名）
        List<JwGridScore> allGridScores = gridScoreMapper.selectByCity(city);
        Map<String, Integer> gridRankMap = new HashMap<>();
        for (int i = 0; i < allGridScores.size(); i++) {
            gridRankMap.put(allGridScores.get(i).getGridCode(), i + 1);
        }

        // branchRank 只在有数据的网点间排名
        Map<Double, Integer> branchRankMap = buildRankMap(branchScores);

        List<Double> sortedBranchScores = new ArrayList<>(branchScores);
        Collections.sort(sortedBranchScores);
        double medianBranchScoreVal = sortedBranchScores.get(sortedBranchScores.size() / 2);

        int totalGrids = allGridScores.size();
        int medianBranchRank = validRows.size() / 2;
        int medianSiteRank = totalGrids / 2;

        Map<String, List<Map<String, Object>>> quadrants = new LinkedHashMap<>();
        quadrants.put("Q1", new ArrayList<>());
        quadrants.put("Q2", new ArrayList<>());
        quadrants.put("Q3", new ArrayList<>());
        quadrants.put("Q4", new ArrayList<>());

        List<Map<String, Object>> allData = new ArrayList<>();
        for (Map<String, Object> row : validRows) {
            double siteVal = ((Number) row.get("siteScore")).doubleValue();
            double branchVal = ((Number) row.get("branchScore")).doubleValue();
            String gridCode = (String) row.get("gridCode");
            int siteRank = gridRankMap.getOrDefault(gridCode, totalGrids);
            int branchRank = branchRankMap.get(branchVal);

            String quadrant;
            if (branchRank <= medianBranchRank && siteRank <= medianSiteRank) quadrant = "Q1";
            else if (branchRank <= medianBranchRank && siteRank > medianSiteRank) quadrant = "Q2";
            else if (branchRank > medianBranchRank && siteRank > medianSiteRank) quadrant = "Q3";
            else quadrant = "Q4";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("branchId", row.get("branchId"));
            item.put("branchName", row.get("branchName"));
            item.put("primaryBranch", row.get("primaryBranch"));
            item.put("gridCode", row.get("gridCode"));
            item.put("longitude", row.get("longitude"));
            item.put("latitude", row.get("latitude"));
            item.put("siteScore", siteVal);
            item.put("branchScore", branchVal);
            item.put("siteRank", siteRank);
            item.put("branchRank", branchRank);
            item.put("quadrant", quadrant);
            item.put("quadrantLabel", getQuadrantLabel(quadrant));

            quadrants.get(quadrant).add(item);
            allData.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("medianSiteScore", 0);
        result.put("medianBranchScore", medianBranchScoreVal);
        result.put("medianSiteRank", medianSiteRank);
        result.put("medianBranchRank", medianBranchRank);
        result.put("totalBranches", allData.size());
        result.put("totalGrids", totalGrids);
        result.put("quadrants", quadrants);
        result.put("allData", allData);
        return result;
    }

    // ===== 辅助方法 =====

    private Map<Double, Integer> buildRankMap(List<Double> scores) {
        List<Double> sorted = new ArrayList<>(scores);
        sorted.sort(Collections.reverseOrder());
        Map<Double, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            map.putIfAbsent(sorted.get(i), i + 1);
        }
        return map;
    }

    private String getQuadrantLabel(String q) {
        switch (q) {
            case "Q1": return "高能效 + 高聚集";
            case "Q2": return "高能效 + 低聚集";
            case "Q3": return "低能效 + 低聚集";
            case "Q4": return "低能效 + 高聚集";
            default: return q;
        }
    }
}

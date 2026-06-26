package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwBranchScore;
import com.ruoyi.jwmap.domain.JwGridDataRaw;
import com.ruoyi.jwmap.mapper.*;
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
    private JwBranchInfoMapper branchInfoMapper;

    @Autowired
    private JwBranchScoreMapper branchScoreMapper;

    @Autowired
    private JwGridDataRawMapper gridDataRawMapper;

    // ===== 四象限分析 =====

    @GetMapping("/quadrant/{city}/{year}")
    public AjaxResult getQuadrantData(@PathVariable String city, @PathVariable Integer year) {
        List<Map<String, Object>> rows = branchInfoMapper.selectQuadrantData(city, year);

        List<Double> siteScores = new ArrayList<>();
        List<Double> branchScores = new ArrayList<>();
        List<Map<String, Object>> validRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object ss = row.get("siteScore");
            Object bs = row.get("branchScore");
            if (ss != null && bs != null) {
                siteScores.add(((Number) ss).doubleValue());
                branchScores.add(((Number) bs).doubleValue());
                validRows.add(row);
            }
        }

        if (siteScores.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("medianSiteScore", 0);
            empty.put("medianBranchScore", 0);
            empty.put("medianSiteRank", 0);
            empty.put("medianBranchRank", 0);
            empty.put("quadrants", new LinkedHashMap<>());
            empty.put("allData", new ArrayList<>());
            return success(empty);
        }

        Map<Double, Integer> siteRankMap = buildRankMap(siteScores);
        Map<Double, Integer> branchRankMap = buildRankMap(branchScores);

        // 计算得分中位数（不是排名中位数）
        List<Double> sortedSiteScores = new ArrayList<>(siteScores);
        List<Double> sortedBranchScores = new ArrayList<>(branchScores);
        Collections.sort(sortedSiteScores);
        Collections.sort(sortedBranchScores);
        double medianSiteScoreVal = sortedSiteScores.get(sortedSiteScores.size() / 2);
        double medianBranchScoreVal = sortedBranchScores.get(sortedBranchScores.size() / 2);

        // 计算排名中位数（继续用于象限划分）
        List<Integer> siteRanks = new ArrayList<>();
        List<Integer> branchRanks = new ArrayList<>();
        for (Map<String, Object> row : validRows) {
            siteRanks.add(siteRankMap.get(((Number) row.get("siteScore")).doubleValue()));
            branchRanks.add(branchRankMap.get(((Number) row.get("branchScore")).doubleValue()));
        }
        Collections.sort(siteRanks);
        Collections.sort(branchRanks);
        int medianSiteRank = siteRanks.get(siteRanks.size() / 2);
        int medianBranchRank = branchRanks.get(branchRanks.size() / 2);

        Map<String, List<Map<String, Object>>> quadrants = new LinkedHashMap<>();
        quadrants.put("Q1", new ArrayList<>());
        quadrants.put("Q2", new ArrayList<>());
        quadrants.put("Q3", new ArrayList<>());
        quadrants.put("Q4", new ArrayList<>());

        List<Map<String, Object>> allData = new ArrayList<>();
        for (Map<String, Object> row : validRows) {
            double siteVal = ((Number) row.get("siteScore")).doubleValue();
            double branchVal = ((Number) row.get("branchScore")).doubleValue();
            int siteRank = siteRankMap.get(siteVal);
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
        result.put("medianSiteScore", medianSiteScoreVal);
        result.put("medianBranchScore", medianBranchScoreVal);
        result.put("medianSiteRank", medianSiteRank);
        result.put("medianBranchRank", medianBranchRank);
        result.put("totalBranches", allData.size());
        result.put("quadrants", quadrants);
        result.put("allData", allData);
        return success(result);
    }

    // ===== 分维度统计 =====

    @GetMapping("/dimension/stats/{city}/{year}")
    public AjaxResult getDimensionStats(@PathVariable String city, @PathVariable Integer year,
                                         @RequestParam(defaultValue = "district") String dimension) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchScore> scores = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
        Map<Long, Double> scoreMap = new HashMap<>();
        for (JwBranchScore s : scores) {
            if (s.getCategoryScore() != null) scoreMap.put(s.getBranchId(), s.getCategoryScore());
        }

        Map<String, List<Double>> groups = new LinkedHashMap<>();
        for (JwBranchInfo b : branches) {
            String key;
            switch (dimension) {
                case "branchType":
                    key = b.getBranchType() != null ? b.getBranchType() : "未知";
                    break;
                case "propertyRight":
                    key = b.getPropertyRight() != null ? b.getPropertyRight() : "未知";
                    break;
                default:
                    key = b.getDistrictName() != null ? b.getDistrictName() : "未知";
                    break;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>());
            Double score = scoreMap.get(b.getBranchId());
            if (score != null) groups.get(key).add(score);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : groups.entrySet()) {
            List<Double> vals = e.getValue();
            double sum = vals.stream().mapToDouble(Double::doubleValue).sum();
            double avg = vals.isEmpty() ? 0 : sum / vals.size();
            double max = vals.isEmpty() ? 0 : Collections.max(vals);
            double min = vals.isEmpty() ? 0 : Collections.min(vals);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dimension", e.getKey());
            item.put("count", vals.size());
            item.put("avgScore", avg);
            item.put("maxScore", max);
            item.put("minScore", min);
            result.add(item);
        }
        return success(result);
    }

    // ===== 三聚焦分类排名 =====

    @GetMapping("/ranking/threeFocus/{city}/{year}")
    public AjaxResult getThreeFocusRanking(@PathVariable String city, @PathVariable Integer year) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        Map<String, String> gridToBranch = new HashMap<>();
        List<String> gridCodes = new ArrayList<>();
        for (JwBranchInfo b : branches) {
            if (b.getGridCode() != null) {
                gridToBranch.put(b.getGridCode(), b.getSecondaryBranch());
                gridCodes.add(b.getGridCode());
            }
        }
        List<JwGridDataRaw> allGridData = gridCodes.isEmpty()
            ? new ArrayList<>() : gridDataRawMapper.selectByGridCodes(gridCodes);

        Map<String, Double> popScores = new LinkedHashMap<>();
        Map<String, Double> entScores = new LinkedHashMap<>();
        Map<String, Double> bizScores = new LinkedHashMap<>();
        for (JwGridDataRaw d : allGridData) {
            String code = d.getIndicatorCode();
            if (code == null) continue;
            double val = d.getIndicatorValue() != null ? d.getIndicatorValue().doubleValue() : 0;
            if (code.startsWith("pop_") || code.startsWith("age_") || code.startsWith("income_")
                || code.startsWith("consume_") || code.startsWith("education_")) {
                popScores.merge(d.getGridCode(), val, Double::sum);
            } else if (code.contains("公司") || code.contains("写字楼") || code.contains("企业")) {
                entScores.merge(d.getGridCode(), val, Double::sum);
            } else if (code.contains("商圈") || code.contains("购物") || code.contains("商业街")) {
                bizScores.merge(d.getGridCode(), val, Double::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("population", buildFocusRanking(popScores, gridToBranch));
        result.put("enterprise", buildFocusRanking(entScores, gridToBranch));
        result.put("business", buildFocusRanking(bizScores, gridToBranch));
        return success(result);
    }

    // ===== 辅助方法 =====

    /** 得分→排名（1=最优），并列同分取最小排名 */
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

    private List<Map<String, Object>> buildFocusRanking(Map<String, Double> scores, Map<String, String> gridToBranch) {
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(20)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("gridCode", e.getKey());
                m.put("branchName", gridToBranch.getOrDefault(e.getKey(), ""));
                m.put("score", e.getValue());
                return m;
            })
            .collect(Collectors.toList());
    }
}

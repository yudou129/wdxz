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
        List<JwBranchIndicator> list = branchIndicatorMapper.selectByBranchAndYear(branchId, year, "数据计算表");
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

    // ===== 四象限分析 =====

    @GetMapping("/quadrant/{city}/{year}")
    public AjaxResult getQuadrantData(@PathVariable String city, @PathVariable Integer year) {
        List<Map<String, Object>> rows = branchInfoMapper.selectQuadrantData(city, year);

        // 收集有效的 siteScore 和 branchScore
        List<Double> siteScores = new ArrayList<>();
        List<Double> branchScores = new ArrayList<>();
        List<Map<String, Object>> validRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object ss = row.get("siteScore");
            Object bs = row.get("branchScore");
            if (ss != null && bs != null) {
                double siteVal = ((Number) ss).doubleValue();
                double branchVal = ((Number) bs).doubleValue();
                siteScores.add(siteVal);
                branchScores.add(branchVal);
                validRows.add(row);
            }
        }

        if (siteScores.isEmpty()) {
            return success(new HashMap<String, Object>() {{
                put("medianSiteScore", 0); put("medianBranchScore", 0);
                put("quadrants", new HashMap<>()); put("allData", new ArrayList<>());
            }});
        }

        // 计算中位数
        Collections.sort(siteScores);
        Collections.sort(branchScores);
        double medianSite = siteScores.get(siteScores.size() / 2);
        double medianBranch = branchScores.get(branchScores.size() / 2);

        // 分类到四象限
        Map<String, List<Map<String, Object>>> quadrants = new LinkedHashMap<>();
        quadrants.put("Q1", new ArrayList<>()); // 高能效 + 高聚集
        quadrants.put("Q2", new ArrayList<>()); // 高能效 + 低聚集
        quadrants.put("Q3", new ArrayList<>()); // 低能效 + 低聚集
        quadrants.put("Q4", new ArrayList<>()); // 低能效 + 高聚集

        List<Map<String, Object>> allData = new ArrayList<>();
        for (Map<String, Object> row : validRows) {
            double siteVal = ((Number) row.get("siteScore")).doubleValue();
            double branchVal = ((Number) row.get("branchScore")).doubleValue();

            String quadrant;
            if (branchVal >= medianBranch && siteVal >= medianSite) {
                quadrant = "Q1";
            } else if (branchVal >= medianBranch && siteVal < medianSite) {
                quadrant = "Q2";
            } else if (branchVal < medianBranch && siteVal < medianSite) {
                quadrant = "Q3";
            } else {
                quadrant = "Q4";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("branchId", row.get("branchId"));
            item.put("branchName", row.get("branchName"));
            item.put("primaryBranch", row.get("primaryBranch"));
            item.put("gridCode", row.get("gridCode"));
            item.put("longitude", row.get("longitude"));
            item.put("latitude", row.get("latitude"));
            item.put("siteScore", siteVal);
            item.put("branchScore", branchVal);
            item.put("quadrant", quadrant);
            item.put("quadrantLabel", getQuadrantLabel(quadrant));

            quadrants.get(quadrant).add(item);
            allData.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("medianSiteScore", medianSite);
        result.put("medianBranchScore", medianBranch);
        result.put("totalBranches", allData.size());
        result.put("quadrants", quadrants);
        result.put("allData", allData);
        return success(result);
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

    // ===== 排名增强 =====

    /** 网点内部效能排名：支行排名 + 一级分行排名 */
    @GetMapping("/branch/ranking/internal/{branchId}/{year}")
    public AjaxResult getBranchInternalRanking(@PathVariable Long branchId, @PathVariable Integer year) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) return error("网点不存在");

        // 全市排名
        List<JwBranchScore> cityScores = branchScoreMapper.selectByCityAndYearAndCategory(
            branch.getCity(), year, "overall");
        int cityRank = -1, cityTotal = cityScores.size();
        for (int i = 0; i < cityScores.size(); i++) {
            if (cityScores.get(i).getBranchId().equals(branchId)) { cityRank = i + 1; break; }
        }

        // 一级支行内排名
        List<JwBranchInfo> peerBranches = branchInfoMapper.selectByCity(branch.getCity()).stream()
            .filter(b -> branch.getPrimaryBranch() != null && branch.getPrimaryBranch().equals(b.getPrimaryBranch()))
            .collect(Collectors.toList());
        int branchRank = -1, branchTotal = peerBranches.size();
        List<JwBranchScore> peerScores = new ArrayList<>();
        for (JwBranchInfo b : peerBranches) {
            List<JwBranchScore> scores = branchScoreMapper.selectByBranchAndYear(b.getBranchId(), year);
            scores.stream().filter(s -> "overall".equals(s.getScoreCategory())).findFirst()
                .ifPresent(peerScores::add);
        }
        peerScores.sort((a, b) -> Double.compare(
            b.getCategoryScore() != null ? b.getCategoryScore() : 0,
            a.getCategoryScore() != null ? a.getCategoryScore() : 0));
        for (int i = 0; i < peerScores.size(); i++) {
            if (peerScores.get(i).getBranchId().equals(branchId)) { branchRank = i + 1; break; }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cityRank", cityRank);
        result.put("cityTotal", cityTotal);
        result.put("branchRank", branchRank);
        result.put("branchTotal", branchTotal);
        return success(result);
    }

    /** 网格区县排名 */
    @GetMapping("/grid/ranking/district/{gridCode}")
    public AjaxResult getGridDistrictRank(@PathVariable String gridCode) {
        JwGridMeta grid = gridMetaMapper.selectByGridCode(gridCode);
        if (grid == null) return error("网格不存在");

        // 全市排名
        List<JwGridScore> cityScores = gridScoreMapper.selectByCity(grid.getCity());
        int cityRank = -1, cityTotal = cityScores.size();
        for (int i = 0; i < cityScores.size(); i++) {
            if (cityScores.get(i).getGridCode().equals(gridCode)) { cityRank = i + 1; break; }
        }

        // 区县排名
        List<JwGridMeta> districtGrids = gridMetaMapper.selectByCity(grid.getCity()).stream()
            .filter(g -> grid.getDistrict() != null && grid.getDistrict().equals(g.getDistrict()))
            .collect(Collectors.toList());
        List<String> districtCodes = districtGrids.stream()
            .map(JwGridMeta::getGridCode).collect(Collectors.toList());
        int districtRank = -1, districtTotal = districtCodes.size();
        for (int i = 0; i < cityScores.size(); i++) {
            if (districtCodes.contains(cityScores.get(i).getGridCode())) {
                if (cityScores.get(i).getGridCode().equals(gridCode)) {
                    districtRank = (int) cityScores.subList(0, i + 1).stream()
                        .filter(s -> districtCodes.contains(s.getGridCode())).count();
                    break;
                }
            }
        }

        // 与最高分差距
        double topScore = cityScores.isEmpty() ? 0 :
            (cityScores.get(0).getSiteScore() != null ? cityScores.get(0).getSiteScore() : 0);
        JwGridScore myScore = gridScoreMapper.selectByGridCode(gridCode);
        double gap = (myScore != null && myScore.getSiteScore() != null)
            ? topScore - myScore.getSiteScore() : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cityRank", cityRank);
        result.put("cityTotal", cityTotal);
        result.put("districtRank", districtRank);
        result.put("districtTotal", districtTotal);
        result.put("topScore", topScore);
        result.put("scoreGap", gap);
        return success(result);
    }

    /** 三聚集指标得分（人口/企业/商圈） */
    @GetMapping("/grid/pillar/{gridCode}")
    public AjaxResult getGridPillarScores(@PathVariable String gridCode) {
        List<JwGridDataRaw> rawData = gridDataRawMapper.selectByGridCode(gridCode);
        double popScore = 0, enterpriseScore = 0, bizScore = 0;
        int popCount = 0, enterpriseCount = 0, bizCount = 0;

        for (JwGridDataRaw d : rawData) {
            String code = d.getIndicatorCode();
            if (code == null) continue;
            double val = d.getIndicatorValue() != null ? d.getIndicatorValue().doubleValue() : 0;
            // 人口聚集：pop_*, age_*, income_*, 等
            if (code.startsWith("pop_") || code.startsWith("age_") || code.startsWith("income_")
                || code.startsWith("consume_") || code.startsWith("education_")) {
                popScore += val; popCount++;
            }
            // 企业聚集：poi_type_公司, poi_type_写字楼
            else if (code.contains("公司") || code.contains("写字楼") || code.contains("企业")) {
                enterpriseScore += val; enterpriseCount++;
            }
            // 商圈聚集：poi_type_商圈, poi_type_购物, poi_type_商业
            else if (code.contains("商圈") || code.contains("购物") || code.contains("商业街")) {
                bizScore += val; bizCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> popMap = new HashMap<>();
        popMap.put("score", popScore); popMap.put("count", popCount);
        result.put("population", popMap);
        Map<String, Object> entMap = new HashMap<>();
        entMap.put("score", enterpriseScore); entMap.put("count", enterpriseCount);
        result.put("enterprise", entMap);
        Map<String, Object> bizMap = new HashMap<>();
        bizMap.put("score", bizScore); bizMap.put("count", bizCount);
        result.put("business", bizMap);
        return success(result);
    }

    /** 全市最高网格选址得分 */
    @GetMapping("/grid/topScore/{city}")
    public AjaxResult getGridTopScore(@PathVariable String city) {
        List<JwGridScore> scores = gridScoreMapper.selectByCity(city);
        double topScore = scores.isEmpty() ? 0 :
            (scores.get(0).getSiteScore() != null ? scores.get(0).getSiteScore() : 0);
        return success(new HashMap<String, Object>() {{ put("topScore", topScore); }});
    }

    /** 网格三聚集指标与全市最高值的差距 */
    @GetMapping("/grid/pillar/gap/{gridCode}")
    public AjaxResult getPillarGap(@PathVariable String gridCode) {
        JwGridMeta grid = gridMetaMapper.selectByGridCode(gridCode);
        if (grid == null) return error("网格不存在");

        // 获取当前网格的三聚集得分
        Map<String, Object> myPillar = (Map<String, Object>)
            ((Map<String, Object>) getGridPillarScores(gridCode).get("data"));
        if (myPillar == null) return error("无数据");

        // 计算全市所有网格的三聚集得分，找到最大值
        List<JwGridMeta> allGrids = gridMetaMapper.selectByCity(grid.getCity());
        List<String> allCodes = allGrids.stream().map(JwGridMeta::getGridCode).collect(Collectors.toList());
        List<JwGridDataRaw> allData = allCodes.isEmpty() ? new ArrayList<>()
            : gridDataRawMapper.selectByGridCodes(allCodes);

        double maxPop = 0, maxEnt = 0, maxBiz = 0;
        Map<String, Double> gridPopScores = new HashMap<>();
        Map<String, Double> gridEntScores = new HashMap<>();
        Map<String, Double> gridBizScores = new HashMap<>();
        for (JwGridDataRaw d : allData) {
            String code = d.getIndicatorCode();
            if (code == null) continue;
            double val = d.getIndicatorValue() != null ? d.getIndicatorValue().doubleValue() : 0;
            String gc = d.getGridCode();
            if (code.startsWith("pop_") || code.startsWith("age_") || code.startsWith("income_")
                || code.startsWith("consume_") || code.startsWith("education_")) {
                double s = gridPopScores.merge(gc, val, Double::sum);
                if (s > maxPop) maxPop = s;
            } else if (code.contains("公司") || code.contains("写字楼") || code.contains("企业")) {
                double s = gridEntScores.merge(gc, val, Double::sum);
                if (s > maxEnt) maxEnt = s;
            } else if (code.contains("商圈") || code.contains("购物") || code.contains("商业街")) {
                double s = gridBizScores.merge(gc, val, Double::sum);
                if (s > maxBiz) maxBiz = s;
            }
        }

        double myPop = toDouble(((Map<String, Object>) myPillar.get("population")).get("score"));
        double myEnt = toDouble(((Map<String, Object>) myPillar.get("enterprise")).get("score"));
        double myBiz = toDouble(((Map<String, Object>) myPillar.get("business")).get("score"));

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> pop = new LinkedHashMap<>(); pop.put("my", myPop); pop.put("max", maxPop); pop.put("gap", maxPop - myPop);
        Map<String, Object> ent = new LinkedHashMap<>(); ent.put("my", myEnt); ent.put("max", maxEnt); ent.put("gap", maxEnt - myEnt);
        Map<String, Object> biz = new LinkedHashMap<>(); biz.put("my", myBiz); biz.put("max", maxBiz); biz.put("gap", maxBiz - myBiz);
        result.put("population", pop); result.put("enterprise", ent); result.put("business", biz);
        return success(result);
    }

    private double toDouble(Object obj) {
        return obj instanceof Number ? ((Number) obj).doubleValue() : 0;
    }

    // ===== 分维度统计 (Phase F) =====

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
                case "branchType": key = b.getBranchType() != null ? b.getBranchType() : "未知"; break;
                case "propertyRight": key = b.getPropertyRight() != null ? b.getPropertyRight() : "未知"; break;
                default: key = b.getDistrictName() != null ? b.getDistrictName() : "未知"; break;
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

    // ===== 三聚焦分类排名 (Phase G) =====

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

        // 三聚焦得分
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

    // ===== 同业距离 (Phase H) =====

    private static final double EARTH_RADIUS_KM = 6371.0;

    @GetMapping("/peerBank/distance/{branchId}")
    public AjaxResult getPeerBankDistance(@PathVariable Long branchId,
                                           @RequestParam(defaultValue = "1") double radius) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) return error("网点不存在");

        List<JwPeerBankInfo> peers = peerBankInfoMapper.selectByCity(branch.getCity());
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwPeerBankInfo p : peers) {
            if (p.getLongitude() == null || p.getLatitude() == null) continue;
            double dist = haversine(branch.getLatitude(), branch.getLongitude(),
                                     p.getLatitude(), p.getLongitude());
            if (dist <= radius) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orgName", p.getOrgName());
                m.put("bankName", p.getBankName());
                m.put("distance", Math.round(dist * 1000.0) / 1000.0);
                m.put("longitude", p.getLongitude());
                m.put("latitude", p.getLatitude());
                result.add(m);
            }
        }
        result.sort(Comparator.comparingDouble(m -> ((Number) m.get("distance")).doubleValue()));
        return success(result);
    }

    // ===== 周围网点距离 (Phase I) =====

    @GetMapping("/branch/nearby/{branchId}")
    public AjaxResult getNearbyBranches(@PathVariable Long branchId,
                                         @RequestParam(defaultValue = "1") double radius) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) return error("网点不存在");

        List<JwBranchInfo> all = branchInfoMapper.selectByCity(branch.getCity());
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwBranchInfo b : all) {
            if (b.getBranchId().equals(branchId)) continue;
            if (b.getLongitude() == null || b.getLatitude() == null) continue;
            double dist = haversine(branch.getLatitude(), branch.getLongitude(),
                                     b.getLatitude(), b.getLongitude());
            if (dist <= radius) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("branchId", b.getBranchId());
                m.put("branchName", b.getSecondaryBranch());
                m.put("distance", Math.round(dist * 1000.0) / 1000.0);
                m.put("longitude", b.getLongitude());
                m.put("latitude", b.getLatitude());
                result.add(m);
            }
        }
        result.sort(Comparator.comparingDouble(m -> ((Number) m.get("distance")).doubleValue()));
        return success(result);
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ===== 人口热力 =====
    @Autowired private JwPopulationHeatMapper populationHeatMapper;

    @GetMapping("/population/grids/{city}")
    public AjaxResult getDistinctGrids(@PathVariable String city) {
        return success(populationHeatMapper.selectDistinctGridCodesByCity(city));
    }
}

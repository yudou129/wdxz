package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IJwDataAccessService;
import com.ruoyi.system.mapper.SysDeptMapper;
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
    @Autowired private IJwDataAccessService accessService;
    @Autowired private SysDeptMapper sysDeptMapper;

    // ===== POI =====
    @GetMapping("/poi/list")
    public AjaxResult poiList(@RequestParam(required = false) String city) {
        JwPoiInfo query = new JwPoiInfo();
        if (city != null && !city.isEmpty()) query.setCity(city);
        List<JwPoiInfo> list = poiInfoMapper.selectPoiInfoList(query);
        return success(list);
    }

    // ===== POI 范围统计 =====

    @PostMapping("/poi/withinRange")
    public AjaxResult poiWithinRange(@RequestBody Map<String, Object> params) {
        String city = (String) params.get("city");
        if (city == null || city.isEmpty()) return error("城市参数不能为空");

        String shapeType = (String) params.get("shapeType"); // "circle" or "square"
        double centerLng = toDouble(params.get("centerLng"));
        double centerLat = toDouble(params.get("centerLat"));
        double radius = toDouble(params.get("radius")); // 米

        if (radius <= 0) return error("半径必须大于0");

        // 1) 计算矩形边界（以 center 为中心，radius/halfSide 为半边长）
        double halfSide = radius;
        double latDelta = halfSide / 111320.0;
        double lngDelta = halfSide / (111320.0 * Math.cos(Math.toRadians(centerLat)));

        double westLng = centerLng - lngDelta;
        double eastLng = centerLng + lngDelta;
        double southLat = centerLat - latDelta;
        double northLat = centerLat + latDelta;

        // 2) SQL 边界框查询（减少传输量）
        List<JwPoiInfo> withinBounds = poiInfoMapper.selectWithinBounds(city, westLng, eastLng, southLat, northLat);

        // 3) 圆形模式：用 Haversine 精确过滤
        List<Map<String, Object>> result;
        if ("circle".equals(shapeType)) {
            result = withinBounds.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> haversine(centerLat, centerLng, p.getLatitude(), p.getLongitude()) * 1000 <= radius)
                .map(this::poiToMap)
                .collect(Collectors.toList());
        } else {
            result = withinBounds.stream()
                .map(this::poiToMap)
                .collect(Collectors.toList());
        }

        return success(result);
    }

    /** JwPoiInfo → 精简 Map */
    private Map<String, Object> poiToMap(JwPoiInfo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("poiId", p.getPoiId());
        m.put("poiName", p.getPoiName());
        m.put("poiType", p.getPoiType());
        m.put("address", p.getAddress());
        m.put("longitude", p.getLongitude());
        m.put("latitude", p.getLatitude());
        return m;
    }

    // ===== 指标配置 =====
    @GetMapping("/indicator/list")
    public AjaxResult indicatorList(@RequestParam(required = false) String indicatorType) {
        List<JwIndicatorConfig> list;
        if (indicatorType != null && !indicatorType.isEmpty()) {
            list = indicatorConfigMapper.selectByType(indicatorType);
        } else {
            list = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
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
            item.put("district", meta.getDistrict());
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
        // 1. 加载 grid 和 grid_raw 类型的全部叶子节点（排除 grid_auto）
        List<JwIndicatorConfig> leafConfigs = new ArrayList<>();
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("grid"));
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("grid_raw"));

        // 2. 加载全部配置，确保父链可完整追溯
        List<JwIndicatorConfig> allConfigs = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        Map<String, JwIndicatorConfig> configMap = allConfigs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        // 3. 查询该网格的原始数据 → code→value 映射
        List<JwGridDataRaw> rawList = gridDataRawMapper.selectByGridCode(gridCode);
        Map<String, Double> dataMap = new HashMap<>();
        for (JwGridDataRaw raw : rawList) {
            if (raw.getIndicatorValue() != null) {
                dataMap.put(raw.getIndicatorCode(), raw.getIndicatorValue());
            }
        }

        // 4. 遍历全部叶子节点，有数据用实际值，无数据填 0.0
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwIndicatorConfig cfg : leafConfigs) {
            String code = cfg.getIndicatorCode();
            Double value = dataMap.getOrDefault(code, 0.0);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("indicatorCode", code);
            item.put("indicatorValue", value);
            item.put("indicatorName", cfg.getIndicatorName());
            item.put("indicatorType", cfg.getIndicatorType());
            item.put("parentCode", cfg.getParentCode());

            // 一级根节点信息（用于前端三级树展示）
            String level1Code = findLevel1Code(code, configMap);
            item.put("level1Code", level1Code);
            if (level1Code != null) {
                JwIndicatorConfig root = configMap.get(level1Code);
                item.put("level1Name", root != null ? root.getIndicatorName() : level1Code);
            } else {
                item.put("level1Name", null);
            }

            result.add(item);
        }
        return success(result);
    }

    @GetMapping("/branch/score/detail/{branchId}/{year}")
    public AjaxResult branchScoreDetail(@PathVariable Long branchId, @PathVariable Integer year) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        // 权限检查（含异常保护：会话过期时默认无权限）
        boolean hasAccess = false;
        try {
            hasAccess = accessService.hasBranchAccess(getUserId(), branch);
        } catch (Exception ignored) {}
        if (!hasAccess) {
            return error("暂无权限查看该网点详细数据");
        }
        List<JwBranchScore> list = branchScoreMapper.selectByBranchAndYear(branchId, year);
        return success(list);
    }

    /** 网点各一级分类 TOPSIS 得分（匹配网格 getGridPillarScores 模式） */
    @GetMapping("/branch/pillar/{branchId}/{year}")
    public AjaxResult getBranchPillarScores(@PathVariable Long branchId, @PathVariable Integer year) {
        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectByTypes(Arrays.asList("branch", "branch_auto", "branch_raw")).stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());

        List<JwBranchScore> scores = branchScoreMapper.selectByBranchAndYear(branchId, year);
        Map<String, Double> scoreMap = new HashMap<>();
        for (JwBranchScore s : scores) {
            if (s.getScoreCategory() != null && s.getCategoryScore() != null) {
                scoreMap.put(s.getScoreCategory(), s.getCategoryScore());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (JwIndicatorConfig root : roots) {
            if ("auto_import_branch".equals(root.getIndicatorCode())) continue; // 不展示自动导入得分
            Double score = scoreMap.get(root.getIndicatorCode());
            Map<String, Object> m = new HashMap<>();
            m.put("score", score != null ? score : 0.0);
            m.put("name", root.getIndicatorName());
            m.put("code", root.getIndicatorCode());
            result.put(root.getIndicatorCode(), m);
        }
        return success(result);
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
        List<JwBranchInfo> allList = branchInfoMapper.selectByGridCode(gridCode);
        if (allList == null || allList.isEmpty()) {
            return success(new ArrayList<>());
        }
        try {
            Long userId = getUserId();
            List<Long> authorizedDeptIds = accessService.selectAuthorizedDeptIds(userId);
            Set<Long> authSet = new HashSet<>(authorizedDeptIds);
            // 批量解析部门名称→ID，避免逐条查询
            Set<String> allDeptNames = new HashSet<>();
            for (JwBranchInfo b : allList) {
                if (b.getPrimaryBranch() != null) allDeptNames.add(b.getPrimaryBranch());
                if (b.getSecondaryBranch() != null) allDeptNames.add(b.getSecondaryBranch());
            }
            SysDept query = new SysDept();
            Map<String, Long> deptNameIdMap = new HashMap<>();
            for (String name : allDeptNames) {
                query.setDeptName(name);
                List<SysDept> list = sysDeptMapper.selectDeptList(query);
                if (!list.isEmpty()) deptNameIdMap.put(name, list.get(0).getDeptId());
            }
            // 过滤：网点的 primary 或 secondary 部门在用户权限范围内
            List<JwBranchInfo> filtered = allList.stream().filter(b -> {
                if (b.getPrimaryBranch() != null) {
                    Long deptId = deptNameIdMap.get(b.getPrimaryBranch());
                    if (deptId != null && authSet.contains(deptId)) return true;
                }
                if (b.getSecondaryBranch() != null) {
                    Long deptId = deptNameIdMap.get(b.getSecondaryBranch());
                    if (deptId != null && authSet.contains(deptId)) return true;
                }
                return false;
            }).collect(Collectors.toList());
            return success(filtered);
        } catch (Exception e) {
            logger.warn("gridBranches: 无法获取用户权限, 返回空列表");
            return success(new ArrayList<>());
        }
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
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        // 权限检查（含异常保护：会话过期时默认无权限）
        boolean hasAccess = false;
        try {
            hasAccess = accessService.hasBranchAccess(getUserId(), branch);
        } catch (Exception ignored) {}
        if (!hasAccess) {
            return error("暂无权限查看该网点详细数据");
        }

        // 1. 加载 branch 和 branch_raw 类型的全部叶子节点（排除 _auto）
        List<JwIndicatorConfig> leafConfigs = new ArrayList<>();
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("branch"));
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("branch_raw"));

        // 2. 加载全部配置，确保父链可完整追溯
        List<JwIndicatorConfig> allConfigs = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        Map<String, JwIndicatorConfig> configMap = allConfigs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        // 3. 查询该网点的数据计算表 → code→value 映射
        List<JwBranchIndicator> rawList = branchIndicatorMapper.selectByBranchAndYear(branchId, year, "数据计算表");
        Map<String, Double> dataMap = new HashMap<>();
        for (JwBranchIndicator raw : rawList) {
            if (raw.getIndicatorValue() != null) {
                dataMap.put(raw.getIndicatorCode(), raw.getIndicatorValue());
            }
        }

        // 4. 遍历全部叶子节点，有数据用实际值，无数据填 0.0
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwIndicatorConfig cfg : leafConfigs) {
            String code = cfg.getIndicatorCode();
            Double value = dataMap.getOrDefault(code, 0.0);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("indicatorCode", code);
            item.put("indicatorValue", value);
            item.put("indicatorName", cfg.getIndicatorName());
            item.put("indicatorType", cfg.getIndicatorType());
            item.put("parentCode", cfg.getParentCode());

            // 一级根节点信息（用于前端三级树展示）
            String level1Code = findLevel1Code(code, configMap);
            item.put("level1Code", level1Code);
            if (level1Code != null) {
                JwIndicatorConfig root = configMap.get(level1Code);
                item.put("level1Name", root != null ? root.getIndicatorName() : level1Code);
            } else {
                item.put("level1Name", null);
            }

            result.add(item);
        }
        return success(result);
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
                siteScores.add(((Number) ss).doubleValue());
                branchScores.add(((Number) bs).doubleValue());
                validRows.add(row);
            }
        }

        if (siteScores.isEmpty()) {
            return success(new HashMap<String, Object>() {{
                put("medianSiteScore", 0); put("medianBranchScore", 0);
                put("medianSiteRank", 0); put("medianBranchRank", 0);
                put("quadrants", new HashMap<>()); put("allData", new ArrayList<>());
            }});
        }

        // 计算排名（1=最优，并列同分取最小排名）
        Map<Double, Integer> siteRankMap = buildRankMap(siteScores);
        Map<Double, Integer> branchRankMap = buildRankMap(branchScores);

        // 收集排名用于中位数分割
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

        // 分类到四象限（排名越小越好，≤ 中位数排名的属于"高"象限）
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
            if (branchRank <= medianBranchRank && siteRank <= medianSiteRank) {
                quadrant = "Q1";
            } else if (branchRank <= medianBranchRank && siteRank > medianSiteRank) {
                quadrant = "Q2";
            } else if (branchRank > medianBranchRank && siteRank > medianSiteRank) {
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
            item.put("siteRank", siteRank);
            item.put("branchRank", branchRank);
            item.put("quadrant", quadrant);
            item.put("quadrantLabel", getQuadrantLabel(quadrant));

            quadrants.get(quadrant).add(item);
            allData.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("medianSiteScore", medianSiteRank);   // 兼容前端旧字段名，存排名中位数
        result.put("medianBranchScore", medianBranchRank);
        result.put("medianSiteRank", medianSiteRank);
        result.put("medianBranchRank", medianBranchRank);
        result.put("totalBranches", allData.size());
        result.put("quadrants", quadrants);
        result.put("allData", allData);
        return success(result);
    }

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

    /** 三聚焦根节点 TOPSIS 得分（只取 type=grid 的三聚焦根，不含 auto_import） */
    @GetMapping("/grid/pillar/{gridCode}")
    public AjaxResult getGridPillarScores(@PathVariable String gridCode) {
        // 加载 type=grid 的三聚焦根节点
        List<JwIndicatorConfig> threeFocusRoots = indicatorConfigMapper.selectByType("grid").stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());

        // 查询该网格的各分类 TOPSIS 得分
        List<JwGridScore> categoryScores = gridScoreMapper.selectScoresByGridCode(gridCode);
        Map<String, Double> topsisMap = new HashMap<>();
        for (JwGridScore s : categoryScores) {
            if (s.getScoreCategory() != null && s.getSiteScore() != null) {
                topsisMap.put(s.getScoreCategory(), s.getSiteScore());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (JwIndicatorConfig root : threeFocusRoots) {
            Double score = topsisMap.get(root.getIndicatorCode());
            Map<String, Object> m = new HashMap<>();
            m.put("score", score != null ? score : 0.0);
            m.put("name", root.getIndicatorName());
            m.put("code", root.getIndicatorCode());
            result.put(root.getIndicatorCode(), m);
        }
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

    /** 网格三聚焦 TOPSIS 得分与全市最高值的差距 */
    @GetMapping("/grid/pillar/gap/{gridCode}")
    public AjaxResult getPillarGap(@PathVariable String gridCode) {
        JwGridMeta grid = gridMetaMapper.selectByGridCode(gridCode);
        if (grid == null) return error("网格不存在");

        // 三聚焦根节点（type=grid）
        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectByType("grid").stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());
        Set<String> rootCodes = roots.stream().map(JwIndicatorConfig::getIndicatorCode).collect(Collectors.toSet());

        // 当前网格的各分类 TOPSIS 得分
        Map<String, Double> myTopsis = new HashMap<>();
        for (JwGridScore s : gridScoreMapper.selectScoresByGridCode(gridCode)) {
            if (s.getSiteScore() != null) myTopsis.put(s.getScoreCategory(), s.getSiteScore());
        }

        // 计算全市各分类 TOPSIS 最大值
        Map<String, Double> rootMaxes = new HashMap<>();
        for (String rc : rootCodes) rootMaxes.put(rc, 0.0);
        for (JwGridMeta gm : gridMetaMapper.selectByCity(grid.getCity())) {
            for (JwGridScore s : gridScoreMapper.selectScoresByGridCode(gm.getGridCode())) {
                if (rootCodes.contains(s.getScoreCategory()) && s.getSiteScore() != null) {
                    double val = s.getSiteScore();
                    if (val > rootMaxes.get(s.getScoreCategory())) rootMaxes.put(s.getScoreCategory(), val);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (JwIndicatorConfig root : roots) {
            double my = myTopsis.getOrDefault(root.getIndicatorCode(), 0.0);
            double max = rootMaxes.getOrDefault(root.getIndicatorCode(), 0.0);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("my", my);
            entry.put("max", max);
            entry.put("gap", max - my);
            entry.put("name", root.getIndicatorName());
            result.put(root.getIndicatorCode(), entry);
        }
        return success(result);
    }

    /**
     * 沿 parentCode 链追溯指标所属的一级根节点 code
     */
    private String findLevel1Code(String indicatorCode, Map<String, JwIndicatorConfig> configMap) {
        JwIndicatorConfig config = configMap.get(indicatorCode);
        if (config == null) return null;
        String code = indicatorCode;
        String parentCode = config.getParentCode();
        while (parentCode != null && !parentCode.isEmpty()) {
            code = parentCode;
            JwIndicatorConfig parent = configMap.get(parentCode);
            parentCode = parent != null ? parent.getParentCode() : null;
        }
        return code;
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

    // ===== 网点空白服务点 =====

    /** 返回排名前 limit 且未落入任何工行网点的网格（高潜力空白区域） */
    @GetMapping("/grid/topWithoutBranch/{city}")
    public AjaxResult gridTopWithoutBranch(@PathVariable String city) {
        // 1. 取前100个无网点网格code
        List<String> codes = gridScoreMapper.selectTopCodesWithoutBranch(city, 100);
        if (codes.isEmpty()) return success(new ArrayList<>());

        // 2. 从 gridMeta 中匹配坐标
        Set<String> codeSet = new HashSet<>(codes);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwGridMeta meta : gridMetaMapper.selectByCity(city)) {
            if (!codeSet.contains(meta.getGridCode())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gridCode", meta.getGridCode());
            item.put("longitude", meta.getLongitude());
            item.put("latitude", meta.getLatitude());
            item.put("westLongitude", meta.getWestLongitude());
            item.put("eastLongitude", meta.getEastLongitude());
            item.put("northLatitude", meta.getNorthLatitude());
            item.put("southLatitude", meta.getSouthLatitude());
            item.put("district", meta.getDistrict());
            result.add(item);
        }

        // 3. 批量加载 siteScore
        Map<String, Double> scoreMap = new HashMap<>();
        for (JwGridScore s : gridScoreMapper.selectByCity(city)) {
            if (s.getSiteScore() != null) scoreMap.put(s.getGridCode(), s.getSiteScore());
        }
        for (Map<String, Object> item : result) {
            item.put("siteScore", scoreMap.get(item.get("gridCode")));
        }

        // 4. 按得分降序排列
        result.sort((a, b) -> {
            Double sa = (Double) a.get("siteScore");
            Double sb = (Double) b.get("siteScore");
            if (sa == null) return 1; if (sb == null) return -1; return sb.compareTo(sa);
        });
        return success(result);
    }
}

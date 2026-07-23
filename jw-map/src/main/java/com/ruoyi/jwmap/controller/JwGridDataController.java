package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IJwDataAccessService;
import com.ruoyi.jwmap.util.JwGeoUtils;
import com.ruoyi.jwmap.util.JwIndicatorUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 网格数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data")
public class JwGridDataController extends BaseController {

    @Autowired
    private JwGridMetaMapper gridMetaMapper;

    @Autowired
    private JwGridScoreMapper gridScoreMapper;

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired
    private JwGridDataRawMapper gridDataRawMapper;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    @Autowired
    private IJwDataAccessService accessService;

    @Autowired
    private SysDeptMapper sysDeptMapper;

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

    // ===== 网格基础 =====

    @GetMapping("/grid/list")
    public AjaxResult gridList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) return success(new ArrayList<>());
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        Map<String, Double> scoreMap = buildSiteScoreMap(city);
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

    @GetMapping("/grid/score/byCity/{city}")
    public AjaxResult gridScoreByCity(@PathVariable String city,
                                       @RequestParam(required = false) String district) {
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        // 按区县过滤（"all" 表示不过滤）
        if (district != null && !district.isEmpty() && !"all".equals(district)) {
            metas = metas.stream()
                .filter(m -> district.equals(m.getDistrict()))
                .collect(Collectors.toList());
        }
        Map<String, Double> scoreMap = buildSiteScoreMap(city);

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
            if (sa == null) return 1;
            if (sb == null) return -1;
            return sb.compareTo(sa);
        });
        return success(result);
    }

    @GetMapping("/grid/indicators/{gridCode}")
    public AjaxResult gridIndicators(@PathVariable String gridCode) {
        List<JwIndicatorConfig> leafConfigs = new ArrayList<>();
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("grid"));
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("grid_raw"));

        List<JwIndicatorConfig> allConfigs = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        Map<String, JwIndicatorConfig> configMap = allConfigs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        List<JwGridDataRaw> rawList = gridDataRawMapper.selectByGridCode(gridCode);
        Map<String, Double> dataMap = new HashMap<>();
        for (JwGridDataRaw raw : rawList) {
            if (raw.getIndicatorValue() != null) dataMap.put(raw.getIndicatorCode(), raw.getIndicatorValue());
        }

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

            String level1Code = JwIndicatorUtils.findLevel1Code(code, configMap);
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

    // ===== 网格排名 =====

    @GetMapping("/grid/ranking/{city}")
    public TableDataInfo gridRanking(@PathVariable String city,
                                      @RequestParam(required = false) String district) {
        startPage();
        if (district != null && !district.isEmpty()) {
            return getDataTable(gridScoreMapper.selectRankingWithMetaByDistrict(city, district));
        }
        return getDataTable(gridScoreMapper.selectRankingWithMeta(city));
    }

    @GetMapping("/grid/ranking/district/{gridCode}")
    public AjaxResult getGridDistrictRank(@PathVariable String gridCode) {
        JwGridMeta grid = gridMetaMapper.selectByGridCode(gridCode);
        if (grid == null) return error("网格不存在");

        List<JwGridScore> cityScores = gridScoreMapper.selectByCity(grid.getCity());
        int cityRank = -1, cityTotal = cityScores.size();
        for (int i = 0; i < cityScores.size(); i++) {
            if (cityScores.get(i).getGridCode().equals(gridCode)) { cityRank = i + 1; break; }
        }

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

        double topScore = cityScores.isEmpty() ? 0 :
            (cityScores.get(0).getSiteScore() != null ? cityScores.get(0).getSiteScore() : 0);
        JwGridScore myScore = gridScoreMapper.selectByGridCode(gridCode);
        double gap = (myScore != null && myScore.getSiteScore() != null)
            ? topScore - myScore.getSiteScore() : 0;

        // — 行政区最高分 —
        double districtTopScore = 0;
        for (JwGridScore s : cityScores) {
            if (districtCodes.contains(s.getGridCode()) && s.getSiteScore() != null) {
                districtTopScore = Math.max(districtTopScore, s.getSiteScore());
            }
        }
        double districtGap = (myScore != null && myScore.getSiteScore() != null)
            ? districtTopScore - myScore.getSiteScore() : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cityRank", cityRank);
        result.put("cityTotal", cityTotal);
        result.put("districtRank", districtRank);
        result.put("districtTotal", districtTotal);
        result.put("topScore", topScore);
        result.put("scoreGap", gap);
        result.put("districtTopScore", districtTopScore);
        result.put("districtScoreGap", districtGap);
        return success(result);
    }

    // ===== 网格中的网点 =====

    @GetMapping("/grid/branches/{gridCode}")
    public AjaxResult gridBranches(@PathVariable String gridCode) {
        List<JwBranchInfo> allList = branchInfoMapper.selectByGridCode(gridCode);
        if (allList == null || allList.isEmpty()) return success(new ArrayList<>());
        try {
            Long userId = getUserId();
            List<Long> authorizedDeptIds = accessService.selectAuthorizedDeptIds(userId);
            Set<Long> authSet = new HashSet<>(authorizedDeptIds);
            Set<String> allDeptNames = new HashSet<>();
            for (JwBranchInfo b : allList) {
                if (b.getPrimaryBranch() != null) allDeptNames.add(b.getPrimaryBranch());
                if (b.getSecondaryBranch() != null) allDeptNames.add(b.getSecondaryBranch());
            }
            // 批量加载部门映射（一次查询替代逐条查询）
            SysDept queryAll = new SysDept();
            List<SysDept> allDepts = sysDeptMapper.selectDeptList(queryAll);
            Map<String, Long> deptNameIdMap = new HashMap<>();
            for (SysDept d : allDepts) {
                if (d.getDeptName() != null && allDeptNames.contains(d.getDeptName())) {
                    deptNameIdMap.putIfAbsent(d.getDeptName(), d.getDeptId());
                }
            }
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

    // ===== 网格柱状（三聚焦）评分 =====

    @GetMapping("/grid/pillar/{gridCode}")
    public AjaxResult getGridPillarScores(@PathVariable String gridCode) {
        List<JwIndicatorConfig> threeFocusRoots = indicatorConfigMapper.selectByType("grid").stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());

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

    @GetMapping("/grid/pillar/gap/{gridCode}")
    public AjaxResult getPillarGap(@PathVariable String gridCode) {
        JwGridMeta grid = gridMetaMapper.selectByGridCode(gridCode);
        if (grid == null) return error("网格不存在");

        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectByType("grid").stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());
        Set<String> rootCodes = roots.stream().map(JwIndicatorConfig::getIndicatorCode).collect(Collectors.toSet());

        Map<String, Double> myTopsis = new HashMap<>();
        for (JwGridScore s : gridScoreMapper.selectScoresByGridCode(gridCode)) {
            if (s.getSiteScore() != null) myTopsis.put(s.getScoreCategory(), s.getSiteScore());
        }

        // — 全市最高分 —
        Map<String, Double> cityMaxes = new HashMap<>();
        for (String rc : rootCodes) cityMaxes.put(rc, 0.0);
        List<JwGridMeta> cityGrids = gridMetaMapper.selectByCity(grid.getCity());
        List<String> allGridCodes = cityGrids.stream().map(JwGridMeta::getGridCode).collect(Collectors.toList());
        List<JwGridScore> allScores = allGridCodes.isEmpty() ? new ArrayList<>() : gridScoreMapper.selectScoresByGridCodes(allGridCodes);
        for (JwGridScore s : allScores) {
            if (rootCodes.contains(s.getScoreCategory()) && s.getSiteScore() != null) {
                double val = s.getSiteScore();
                if (val > cityMaxes.get(s.getScoreCategory())) cityMaxes.put(s.getScoreCategory(), val);
            }
        }

        // — 行政区最高分 —
        Map<String, Double> districtMaxes = new HashMap<>();
        for (String rc : rootCodes) districtMaxes.put(rc, 0.0);
        List<JwGridMeta> districtGrids = cityGrids.stream()
            .filter(g -> grid.getDistrict() != null && grid.getDistrict().equals(g.getDistrict()))
            .collect(Collectors.toList());
        Set<String> districtGridCodes = districtGrids.stream().map(JwGridMeta::getGridCode).collect(Collectors.toSet());
        for (JwGridScore s : allScores) {
            if (districtGridCodes.contains(s.getGridCode())
                && rootCodes.contains(s.getScoreCategory()) && s.getSiteScore() != null) {
                double val = s.getSiteScore();
                if (val > districtMaxes.get(s.getScoreCategory())) districtMaxes.put(s.getScoreCategory(), val);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (JwIndicatorConfig root : roots) {
            double my = myTopsis.getOrDefault(root.getIndicatorCode(), 0.0);
            double cityMax = cityMaxes.getOrDefault(root.getIndicatorCode(), 0.0);
            double distMax = districtMaxes.getOrDefault(root.getIndicatorCode(), 0.0);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("my", my);
            entry.put("max", cityMax);              // 保留向后兼容
            entry.put("gap", cityMax - my);          // 保留向后兼容
            entry.put("maxCity", cityMax);
            entry.put("gapCity", cityMax - my);
            entry.put("maxDistrict", distMax);
            entry.put("gapDistrict", distMax - my);
            entry.put("name", root.getIndicatorName());
            result.put(root.getIndicatorCode(), entry);
        }
        return success(result);
    }

    @GetMapping("/grid/topScore/{city}")
    public AjaxResult getGridTopScore(@PathVariable String city) {
        List<JwGridScore> scores = gridScoreMapper.selectByCity(city);
        double topScore = scores.isEmpty() ? 0 :
            (scores.get(0).getSiteScore() != null ? scores.get(0).getSiteScore() : 0);
        return success(new HashMap<String, Object>() {{ put("topScore", topScore); }});
    }

    // ===== 空白高潜力网格 =====

    @GetMapping("/grid/topWithoutBranch/{city}")
    public AjaxResult gridTopWithoutBranch(@PathVariable String city,
                                           @RequestParam(required = false) String district) {
        List<String> codes = gridScoreMapper.selectTopCodesWithoutBranch(city,
            district != null && !district.isEmpty() && !"all".equals(district) ? district : null, 1000000);
        if (codes.isEmpty()) return success(new ArrayList<>());

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

        Map<String, Double> scoreMap = buildSiteScoreMap(city);
        for (Map<String, Object> item : result) {
            item.put("siteScore", scoreMap.get(item.get("gridCode")));
        }

        result.sort((a, b) -> {
            Double sa = (Double) a.get("siteScore");
            Double sb = (Double) b.get("siteScore");
            if (sa == null) return 1;
            if (sb == null) return -1;
            return sb.compareTo(sa);
        });
        return success(result);
    }

    // ===== 空白点：最近网点 =====

    @GetMapping("/grid/nearestBranch/{gridCode}")
    public AjaxResult getNearestBranch(@PathVariable String gridCode) {
        JwGridMeta grid = gridMetaMapper.selectByGridCode(gridCode);
        if (grid == null) return error("网格不存在");
        if (grid.getLatitude() == null || grid.getLongitude() == null)
            return success(new HashMap<String, Object>() {{ put("name", "无坐标"); put("distance", -1); }});

        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(grid.getCity());
        if (branches == null || branches.isEmpty())
            return success(new HashMap<String, Object>() {{ put("name", "无关联网点"); put("distance", -1); }});

        double gridLat = grid.getLatitude();
        double gridLng = grid.getLongitude();
        JwBranchInfo nearest = null;
        double minDist = Double.MAX_VALUE;

        for (JwBranchInfo b : branches) {
            if (b.getLatitude() == null || b.getLongitude() == null) continue;
            double dist = JwGeoUtils.haversine(gridLat, gridLng, b.getLatitude(), b.getLongitude());
            if (dist < minDist) { minDist = dist; nearest = b; }
        }

        if (nearest == null)
            return success(new HashMap<String, Object>() {{ put("name", "未找到"); put("distance", -1); }});

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branchName", nearest.getSecondaryBranch() != null ? nearest.getSecondaryBranch() : nearest.getPrimaryBranch());
        result.put("primaryBranch", nearest.getPrimaryBranch());
        result.put("distance", Math.round(minDist * 10.0) / 10.0); // 保留1位小数
        return success(result);
    }

    // ===== 辅助方法 =====

    /** 批量构建网格评分映射 gridCode → siteScore */
    private Map<String, Double> buildSiteScoreMap(String city) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (JwGridScore s : gridScoreMapper.selectByCity(city)) {
            if (s.getSiteScore() != null) scoreMap.put(s.getGridCode(), s.getSiteScore());
        }
        return scoreMap;
    }

}

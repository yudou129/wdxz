package com.ruoyi.jwmap.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IJwDataAccessService;
import com.ruoyi.jwmap.util.JwGeoUtils;
import com.ruoyi.jwmap.util.JwIndicatorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 网点数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data")
public class JwBranchDataController extends BaseController {

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    @Autowired
    private JwBranchScoreMapper branchScoreMapper;

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired
    private JwBranchIndicatorMapper branchIndicatorMapper;

    @Autowired
    private JwBranchSummaryMapper branchSummaryMapper;

    @Autowired
    private IJwDataAccessService accessService;

    // ===== 网点基础 =====

    @GetMapping("/branch/list")
    public AjaxResult branchList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) return success(new ArrayList<>());
        return success(branchInfoMapper.selectByCity(city));
    }

    @GetMapping("/branch/score/{city}/{year}")
    public AjaxResult branchScore(@PathVariable String city, @PathVariable Integer year) {
        return success(branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall"));
    }

    @GetMapping("/branch/summary/{city}/{year}")
    public AjaxResult branchSummary(@PathVariable String city, @PathVariable Integer year) {
        return success(branchSummaryMapper.selectByCityAndYear(city, year));
    }

    @GetMapping("/branch/score/detail/{branchId}/{year}")
    public AjaxResult branchScoreDetail(@PathVariable Long branchId, @PathVariable Integer year) {
        return success(branchScoreMapper.selectByBranchAndYear(branchId, year));
    }

    // ===== 网点柱状评分 =====

    @GetMapping("/branch/pillar/{branchId}/{year}")
    public AjaxResult getBranchPillarScores(@PathVariable Long branchId, @PathVariable Integer year) {
        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectByTypes(
                Arrays.asList("branch", "branch_auto", "branch_raw")).stream()
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
            if ("auto_import_branch".equals(root.getIndicatorCode())) continue;
            Double score = scoreMap.get(root.getIndicatorCode());
            Map<String, Object> m = new HashMap<>();
            m.put("score", score != null ? score : 0.0);
            m.put("name", root.getIndicatorName());
            m.put("code", root.getIndicatorCode());
            result.put(root.getIndicatorCode(), m);
        }
        return success(result);
    }

    // ===== 网点详细指标（含权限检查） =====

    @Log(title = "网点指标", businessType = BusinessType.OTHER)
    @GetMapping("/branch/indicators/{branchId}/{year}")
    public AjaxResult branchIndicators(@PathVariable Long branchId, @PathVariable Integer year) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        boolean hasAccess = false;
        try {
            hasAccess = accessService.hasBranchAccess(getUserId(), branch);
        } catch (Exception e) {
            logger.error("检查网点 {} 的权限时出错", branchId, e);
            return error("权限检查失败，请稍后重试");
        }
        if (!hasAccess) return error("暂无权限查看该网点详细数据");

        List<JwIndicatorConfig> leafConfigs = new ArrayList<>();
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("branch"));
        leafConfigs.addAll(indicatorConfigMapper.selectLeavesByType("branch_raw"));

        List<JwIndicatorConfig> allConfigs = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        Map<String, JwIndicatorConfig> configMap = allConfigs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        List<JwBranchIndicator> rawList = branchIndicatorMapper.selectByBranchAndYear(branchId, year, "数据计算表");
        Map<String, Double> dataMap = new HashMap<>();
        for (JwBranchIndicator raw : rawList) {
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

    // ===== 网点排名 =====

    @GetMapping("/branch/ranking/{city}/{year}")
    public TableDataInfo branchRanking(@PathVariable String city, @PathVariable Integer year) {
        startPage();
        return getDataTable(branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall"));
    }

    @GetMapping("/branch/ranking/internal/{branchId}/{year}")
    public AjaxResult getBranchInternalRanking(@PathVariable Long branchId, @PathVariable Integer year) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) return error("网点不存在");

        List<JwBranchScore> cityScores = branchScoreMapper.selectByCityAndYearAndCategory(
            branch.getCity(), year, "overall");
        int cityRank = -1, cityTotal = cityScores.size();
        for (int i = 0; i < cityScores.size(); i++) {
            if (cityScores.get(i).getBranchId().equals(branchId)) { cityRank = i + 1; break; }
        }

        // 计算与全市最高分的差距
        double myCityScore = 0;
        double topCityScore = 0;
        for (JwBranchScore s : cityScores) {
            if (s.getCategoryScore() != null) {
                if (s.getBranchId().equals(branchId)) myCityScore = s.getCategoryScore();
                if (s.getCategoryScore() > topCityScore) topCityScore = s.getCategoryScore();
            }
        }
        double scoreGap = Math.max(0, topCityScore - myCityScore);

        List<JwBranchInfo> peerBranches = branchInfoMapper.selectByCity(branch.getCity()).stream()
            .filter(b -> branch.getPrimaryBranch() != null && branch.getPrimaryBranch().equals(b.getPrimaryBranch()))
            .collect(Collectors.toList());
        int branchRank = -1, branchTotal = peerBranches.size();
        List<Long> peerIds = peerBranches.stream().map(JwBranchInfo::getBranchId).collect(Collectors.toList());
        List<JwBranchScore> peerScores = new ArrayList<>();
        if (!peerIds.isEmpty()) {
            List<JwBranchScore> allPeerScores = branchScoreMapper.selectByBranchIdsAndYear(peerIds, year);
            for (Long pid : peerIds) {
                allPeerScores.stream()
                    .filter(s -> pid.equals(s.getBranchId()) && "overall".equals(s.getScoreCategory()))
                    .findFirst().ifPresent(peerScores::add);
            }
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
        result.put("scoreGap", scoreGap);
        return success(result);
    }

    // ===== 各分项全市最高分（作为差距比较基准） =====

    @GetMapping("/branch/topScores/{city}/{year}")
    public AjaxResult getBranchCategoryTopScores(@PathVariable String city, @PathVariable Integer year) {
        List<JwBranchScore> allScores = branchScoreMapper.selectByCityAndYear(city, year);
        Map<String, Double> topScores = new HashMap<>();
        for (JwBranchScore s : allScores) {
            if (s.getScoreCategory() != null && s.getCategoryScore() != null) {
                topScores.merge(s.getScoreCategory(), s.getCategoryScore(), Math::max);
            }
        }
        return success(topScores);
    }

    // ===== 附近网点 =====

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
            double dist = JwGeoUtils.haversine(branch.getLatitude(), branch.getLongitude(),
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

}

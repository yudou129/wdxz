package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IBranchComputeService;
import com.ruoyi.jwmap.util.TopsisCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 网点数据计算服务实现
 *
 * 管线步骤：
 * 1. assignGridToBranch — 空间关联网点到网格
 * 2. computeBranchIndicators — 遍历 is_derived=1 的网点指标，模式分发器公式计算
 * 3. computeBranchSummary — 权重来自 indicator_config 的 getEffectiveWeight()
 * 4. computeBranchNormalized — 归一化
 * 5. computeBranchScore — 按根节点分类 + overall 分别 TOPSIS
 * 6. computeRankings — 排名
 */
@Service
public class BranchComputeServiceImpl implements IBranchComputeService {

    @Autowired private JwBranchInfoMapper branchInfoMapper;
    @Autowired private JwBranchIndicatorMapper branchIndicatorMapper;
    @Autowired private JwBranchSummaryMapper branchSummaryMapper;
    @Autowired private JwBranchScoreMapper branchScoreMapper;
    @Autowired private JwIndicatorConfigMapper indicatorConfigMapper;
    @Autowired private JwGridMetaMapper gridMetaMapper;

    @Override
    @Transactional
    public int computeBranchData(String city, Integer dataYear) {
        assignGridToBranch(city);
        int count = computeBranchIndicators(city, dataYear);
        if (count == 0) return 0;
        computeBranchSummary(city, dataYear);
        computeBranchNormalized(city, dataYear);
        computeBranchScore(city, dataYear);
        return count;
    }

    @Override
    @Transactional
    public int computeBranchIndicators(String city, Integer dataYear) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        if (branches.isEmpty()) return 0;

        // 加载 is_derived=1 的 branch 叶子指标（公式驱动）
        List<JwIndicatorConfig> formulaIndicators = indicatorConfigMapper.selectLeavesByType("branch");
        // 加载 branch_raw 叶子节点（原始数据，需复制到数据计算表参与评分）
        List<JwIndicatorConfig> rawLeaves = indicatorConfigMapper.selectLeavesByType("branch_raw");

        // 清理该市该年旧的计算数据
        for (JwBranchInfo branch : branches) {
            branchIndicatorMapper.deleteByBranchAndYear(branch.getBranchId(), dataYear, "数据计算表");
            branchIndicatorMapper.deleteByBranchAndYear(branch.getBranchId(), dataYear, "数据计算表归一化");
        }

        // 批量预加载全部网点的基础数据（本年 + 上年），避免 calcGrowthRate 逐网点查库
        List<JwBranchIndicator> allCurrBase = branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear, "基础数据");
        List<JwBranchIndicator> allPrevBase = dataYear <= 2023 ? new ArrayList<>()
            : branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear - 1, "基础数据");
        Map<Long, Map<String, Double>> currBaseMap = new HashMap<>();
        Map<Long, Map<String, Double>> prevBaseMap = new HashMap<>();
        for (JwBranchIndicator ind : allCurrBase) {
            currBaseMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>()).put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }
        for (JwBranchIndicator ind : allPrevBase) {
            prevBaseMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>()).put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }

        int count = 0;
        for (JwBranchInfo branch : branches) {
            Map<String, Double> baseData = currBaseMap.get(branch.getBranchId());
            if (baseData == null || baseData.isEmpty()) continue;
            Map<String, Double> prevBaseData = prevBaseMap.get(branch.getBranchId());

            Map<String, Double> computed = new LinkedHashMap<>();

            // 1. 模式分发器：遍历所有公式指标并计算
            for (JwIndicatorConfig formula : formulaIndicators) {
                Double result = evaluateFormula(formula, baseData, prevBaseData, branch, dataYear);
                if (result != null) {
                    computed.put(formula.getIndicatorCode(), result);
                }
            }

            // 2. 复制 branch_raw 叶子值到数据计算表，使其参与 TOPSIS 评分
            for (JwIndicatorConfig raw : rawLeaves) {
                Double val = baseData.get(raw.getIndicatorCode());
                if (val != null) {
                    computed.put(raw.getIndicatorCode(), val);
                }
            }

            if (computed.isEmpty()) continue;

            for (Map.Entry<String, Double> entry : computed.entrySet()) {
                JwBranchIndicator indicator = new JwBranchIndicator();
                indicator.setBranchId(branch.getBranchId());
                indicator.setDataYear(dataYear);
                indicator.setSheetType("数据计算表");
                indicator.setIndicatorCode(entry.getKey());
                indicator.setIndicatorValue(entry.getValue());
                branchIndicatorMapper.upsertBranchIndicator(indicator);
            }
            count++;
        }
        return count;
    }

    /**
     * 模式分发器：根据 computation_pattern 执行对应的公式计算
     */
    private Double evaluateFormula(JwIndicatorConfig formula, Map<String, Double> base,
                                   Map<String, Double> prevBase,
                                   JwBranchInfo branch, Integer dataYear) {
        if (!"1".equals(formula.getIsDerived())) return null;

        String pattern = formula.getComputationPattern();
        if (pattern == null) return null;

        try {
            switch (pattern) {
                case "per_capita": {
                    String[] inputs = formula.getInputCodesArray();
                    if (inputs.length == 0) return null;
                    double val = base.getOrDefault(inputs[0], 0.0);
                    return safeDiv(val, totalStaff(branch));
                }
                case "per_area": {
                    String[] inputs = formula.getInputCodesArray();
                    if (inputs.length == 0) return null;
                    double val = base.getOrDefault(inputs[0], 0.0);
                    return safeDiv(val, totalArea(branch));
                }
                case "sum_per_capita": {
                    String[] inputs = formula.getInputCodesArray();
                    double sum = 0;
                    for (String code : inputs) sum += base.getOrDefault(code, 0.0);
                    return safeDiv(sum, totalStaff(branch));
                }
                case "sum_per_area": {
                    String[] inputs = formula.getInputCodesArray();
                    double sum = 0;
                    for (String code : inputs) sum += base.getOrDefault(code, 0.0);
                    return safeDiv(sum, totalArea(branch));
                }
                case "per_customer": {
                    String input = formula.getInputCodes();
                    if (input == null || input.isEmpty()) return null;
                    String[] parts = input.split("\\|");
                    String numPart = parts.length > 0 ? parts[0] : "";
                    String denPart = parts.length > 1 ? parts[1] : "";
                    double numerator = evaluateSignedSum(numPart, base);
                    double denominator = evaluateSignedSum(denPart, base);
                    return safeDiv(numerator, denominator);
                }
                case "growth_rate": {
                    String inputCodes = formula.getInputCodes();
                    if (inputCodes == null || !inputCodes.contains("|")) return null;
                    String[] parts = inputCodes.split("\\|");
                    if (parts.length < 2) return null;
                    return calcGrowthRate(base, prevBase, parts[0], parts[1]);
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private int totalStaff(JwBranchInfo branch) {
        return branch.getTotalStaff() != null && branch.getTotalStaff() > 0 ? branch.getTotalStaff() : 1;
    }

    private double totalArea(JwBranchInfo branch) {
        return branch.getTotalArea() != null && branch.getTotalArea() > 0 ? branch.getTotalArea() : 1.0;
    }

    /**
     * 计算带符号的多指标累加值
     * 格式: "+code1,-code2,+code3" → code1 - code2 + code3
     */
    private double evaluateSignedSum(String expr, Map<String, Double> base) {
        if (expr == null || expr.trim().isEmpty()) return 0;
        double sum = 0;
        for (String token : expr.split(",")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            if (token.startsWith("-")) {
                sum -= base.getOrDefault(token.substring(1), 0.0);
            } else {
                String code = token.startsWith("+") ? token.substring(1) : token;
                sum += base.getOrDefault(code, 0.0);
            }
        }
        return sum;
    }

    /**
     * 从基础数据提取指标值Map
     */
    private Map<String, Double> getBaseData(Long branchId, Integer dataYear) {
        List<JwBranchIndicator> list = branchIndicatorMapper.selectByBranchAndYear(branchId, dataYear, "基础数据");
        Map<String, Double> map = new LinkedHashMap<>();
        for (JwBranchIndicator item : list) {
            map.put(item.getIndicatorCode(), item.getIndicatorValue());
        }
        return map;
    }

    /**
     * 日均增幅 = 本年日均增量 / 上年日均余额
     * 使用预加载的数据映射，避免每调用一次就查两次库
     */
    private Double calcGrowthRate(Map<String, Double> currBaseData,
                                   Map<String, Double> prevBaseData,
                                   String balanceCode, String growthCode) {
        if (prevBaseData == null) return null;
        double prevBalance = prevBaseData.getOrDefault(balanceCode, 0.0);
        if (prevBalance == 0) return null;
        double currGrowth = currBaseData.getOrDefault(growthCode, 0.0);
        return safeDiv(currGrowth, prevBalance);
    }

    private double safeDiv(double numerator, double denominator) {
        if (denominator == 0) return 0;
        return numerator / denominator;
    }

    @Override
    @Transactional
    public int assignGridToBranch(String city) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwGridMeta> grids = gridMetaMapper.selectByCity(city);
        int count = 0;
        for (JwBranchInfo branch : branches) {
            if (branch.getLongitude() == null || branch.getLatitude() == null) continue;
            for (JwGridMeta grid : grids) {
                if (grid.getWestLongitude() == null || grid.getEastLongitude() == null) continue;
                if (branch.getLongitude() >= grid.getWestLongitude()
                    && branch.getLongitude() <= grid.getEastLongitude()
                    && branch.getLatitude() >= grid.getSouthLatitude()
                    && branch.getLatitude() <= grid.getNorthLatitude()) {
                    branchInfoMapper.updateGridCode(branch.getBranchId(), grid.getGridCode());
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private void computeBranchSummary(String city, Integer dataYear) {
        branchSummaryMapper.deleteByCityAndYear(city, dataYear);

        // 加载 branch + branch_raw 类型所有叶子指标，使原始数据也参与评分
        List<JwIndicatorConfig> allBranch = new ArrayList<>();
        allBranch.addAll(indicatorConfigMapper.selectByType("branch"));
        allBranch.addAll(indicatorConfigMapper.selectByType("branch_raw"));
        Map<String, JwIndicatorConfig> configMap = allBranch.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        // 只对叶子节点计算权重
        List<JwIndicatorConfig> leaves = allBranch.stream()
            .filter(c -> c.isLeaf(configMap))
            .collect(Collectors.toList());

        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        if (branches.isEmpty() || leaves.isEmpty()) return;

        // 批量加载全部"数据计算表"数据，避免 N+1
        List<JwBranchIndicator> allComputed = branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear, "数据计算表");
        Map<Long, Map<String, Double>> valueMap = new HashMap<>();
        for (JwBranchIndicator ind : allComputed) {
            valueMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>())
                .put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }

        for (JwIndicatorConfig leaf : leaves) {
            String code = leaf.getIndicatorCode();
            List<Double> values = new ArrayList<>();
            for (JwBranchInfo branch : branches) {
                Map<String, Double> branchValues = valueMap.get(branch.getBranchId());
                if (branchValues != null && branchValues.containsKey(code)) {
                    values.add(branchValues.get(code));
                }
            }
            if (values.isEmpty()) continue;

            double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double minVal = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double weight = leaf.getEffectiveWeight(configMap);

            JwBranchSummary summary = new JwBranchSummary();
            summary.setCity(city);
            summary.setDataYear(dataYear);
            summary.setIndicatorCode(code);
            summary.setActualWeight(weight);
            summary.setMaxValue(maxVal);
            summary.setMinValue(minVal);
            branchSummaryMapper.insertBranchSummary(summary);
        }
    }

    private void computeBranchNormalized(String city, Integer dataYear) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);
        if (branches.isEmpty() || summaries.isEmpty()) return;

        for (JwBranchInfo branch : branches) {
            branchIndicatorMapper.deleteByBranchAndYear(branch.getBranchId(), dataYear, "数据计算表归一化");
        }

        // 批量加载全部"数据计算表"数据，避免 N+1
        List<JwBranchIndicator> allComputed = branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear, "数据计算表");
        Map<Long, Map<String, Double>> valueMap = new HashMap<>();
        for (JwBranchIndicator ind : allComputed) {
            valueMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>())
                .put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }

        for (JwBranchSummary summary : summaries) {
            String code = summary.getIndicatorCode();
            List<Double> colValues = new ArrayList<>();
            Map<Long, Double> branchValueMap = new LinkedHashMap<>();
            for (JwBranchInfo branch : branches) {
                Map<String, Double> branchValues = valueMap.get(branch.getBranchId());
                double val = (branchValues != null) ? branchValues.getOrDefault(code, 0.0) : 0;
                colValues.add(val);
                branchValueMap.put(branch.getBranchId(), val);
            }
            double sumSq = TopsisCalculator.calcSumSq(colValues);
            double weight = summary.getActualWeight();
            double maxNorm = 0, minNorm = Double.MAX_VALUE;
            for (Map.Entry<Long, Double> entry : branchValueMap.entrySet()) {
                double norm = TopsisCalculator.normalizeBranch(entry.getValue(), weight, sumSq);
                maxNorm = Math.max(maxNorm, norm);
                minNorm = Math.min(minNorm, norm);

                JwBranchIndicator normInd = new JwBranchIndicator();
                normInd.setBranchId(entry.getKey());
                normInd.setDataYear(dataYear);
                normInd.setSheetType("数据计算表归一化");
                normInd.setIndicatorCode(code);
                normInd.setIndicatorValue(norm);
                branchIndicatorMapper.upsertBranchIndicator(normInd);
            }
            summary.setMaxNorm(maxNorm);
            summary.setMinNorm(minNorm);
            branchSummaryMapper.updateBranchSummary(summary);
        }
    }

    @Override
    @Transactional
    public int computeBranchScore(String city, Integer dataYear) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);
        if (branches.isEmpty() || summaries.isEmpty()) return 0;

        Map<String, JwBranchSummary> summaryMap = summaries.stream()
            .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a));

        branchScoreMapper.deleteByCityAndYear(city, dataYear);

        // 构建 code → config 映射，按根节点分组（匹配 computeGridScore 模式）
        List<JwIndicatorConfig> allBranch = indicatorConfigMapper.selectByTypes(Arrays.asList("branch", "branch_auto", "branch_raw"));
        Map<String, JwIndicatorConfig> configMap = allBranch.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));
        List<JwIndicatorConfig> roots = allBranch.stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());

        // 预计算所有根节点的叶子列表 + overall 叶子列表（只算一次，不放在网点循环内）
        Map<String, List<String>> rootLeafCodes = new LinkedHashMap<>();
        for (JwIndicatorConfig root : roots) {
            rootLeafCodes.put(root.getIndicatorCode(), getLeafCodesUnder(root.getIndicatorCode(), configMap));
        }
        List<JwIndicatorConfig> allLeaves = allBranch.stream()
            .filter(c -> c.isLeaf(configMap))
            .collect(Collectors.toList());
        List<String> allLeafCodes = allLeaves.stream()
            .map(JwIndicatorConfig::getIndicatorCode).collect(Collectors.toList());
        rootLeafCodes.put("overall", allLeafCodes);

        // 批量加载全部归一化数据，避免每个网点每个指标逐条查询
        List<JwBranchIndicator> allNormalized = branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear, "数据计算表归一化");
        Map<Long, Map<String, Double>> normValueMap = new HashMap<>();
        for (JwBranchIndicator ind : allNormalized) {
            normValueMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>())
                .put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }

        int count = 0;
        for (JwBranchInfo branch : branches) {
            Map<String, Double> branchNorm = normValueMap.getOrDefault(branch.getBranchId(), Collections.emptyMap());
            for (Map.Entry<String, List<String>> entry : rootLeafCodes.entrySet()) {
                String category = entry.getKey();
                List<String> leafCodes = entry.getValue();
                computeCategoryScore(branch, dataYear, city, category, leafCodes, summaryMap, branchNorm);
            }
            count++;
        }

        computeRankings(city, dataYear);
        return count;
    }

    /** 获取某个根节点下所有叶子指标的编码列表 */
    private List<String> getLeafCodesUnder(String rootCode, Map<String, JwIndicatorConfig> configMap) {
        Set<String> leafCodes = new LinkedHashSet<>();
        for (JwIndicatorConfig config : configMap.values()) {
            if (config.isLeaf(configMap) && isUnderRoot(config, rootCode, configMap)) {
                leafCodes.add(config.getIndicatorCode());
            }
        }
        return new ArrayList<>(leafCodes);
    }

    private boolean isUnderRoot(JwIndicatorConfig node, String rootCode, Map<String, JwIndicatorConfig> configMap) {
        String parent = node.getParentCode();
        while (parent != null && !parent.isEmpty()) {
            if (parent.equals(rootCode)) return true;
            JwIndicatorConfig p = configMap.get(parent);
            parent = p != null ? p.getParentCode() : null;
        }
        return false;
    }

    private void computeCategoryScore(JwBranchInfo branch, Integer dataYear, String city,
                                       String category, List<String> indicatorCodes,
                                       Map<String, JwBranchSummary> summaryMap,
                                       Map<String, Double> branchNorm) {
        List<Double> normValues = new ArrayList<>();
        List<Double> maxNorms = new ArrayList<>();
        List<Double> minNorms = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (String code : indicatorCodes) {
            JwBranchSummary summary = summaryMap.get(code);
            if (summary == null || summary.getMaxNorm() == null || summary.getMinNorm() == null) {
                normValues.add(0.0); maxNorms.add(0.0); minNorms.add(0.0); weights.add(0.0);
                continue;
            }
            normValues.add(branchNorm.getOrDefault(code, 0.0));
            maxNorms.add(summary.getMaxNorm());
            minNorms.add(summary.getMinNorm());
            weights.add(summary.getActualWeight());
        }

        double[] result = TopsisCalculator.calcTopsis(normValues, maxNorms, minNorms, weights);

        JwBranchScore score = new JwBranchScore();
        score.setBranchId(branch.getBranchId());
        score.setDataYear(dataYear);
        score.setCity(city);
        score.setScoreCategory(category);
        score.setPositiveDistance(result[0]);
        score.setNegativeDistance(result[1]);
        score.setCategoryScore(result[2]);
        branchScoreMapper.upsertBranchScore(score);
    }

    private void computeRankings(String city, Integer dataYear) {
        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectRoots("branch");
        for (JwIndicatorConfig root : roots) {
            List<JwBranchScore> scores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, root.getIndicatorCode());
            scores.sort((a, b) -> Double.compare(
                b.getCategoryScore() != null ? b.getCategoryScore() : 0,
                a.getCategoryScore() != null ? a.getCategoryScore() : 0));
            for (int i = 0; i < scores.size(); i++) {
                scores.get(i).setRankNum(i + 1);
                branchScoreMapper.updateRank(scores.get(i));
            }
        }
        List<JwBranchScore> overallScores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, "overall");
        overallScores.sort((a, b) -> Double.compare(
            b.getCategoryScore() != null ? b.getCategoryScore() : 0,
            a.getCategoryScore() != null ? a.getCategoryScore() : 0));
        for (int i = 0; i < overallScores.size(); i++) {
            overallScores.get(i).setRankNum(i + 1);
            branchScoreMapper.updateRank(overallScores.get(i));
        }
    }

    @Override
    public Map<String, Object> getBranchDataStatus(String city) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("city", city);
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        status.put("branchCount", branches.size());
        // hasWeight → 检查 branch 类型是否有叶子指标
        List<JwIndicatorConfig> leaves = indicatorConfigMapper.selectLeavesByType("branch");
        status.put("hasWeight", !leaves.isEmpty());
        boolean hasBase = false;
        if (!branches.isEmpty()) {
            List<JwBranchIndicator> base = branchIndicatorMapper.selectByBranchAndYear(branches.get(0).getBranchId(), 2024, "基础数据");
            hasBase = !base.isEmpty();
        }
        status.put("hasBaseData", hasBase);
        return status;
    }
}

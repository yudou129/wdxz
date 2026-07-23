package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.service.IAiService;
import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IBranchComputeService;
import com.ruoyi.jwmap.util.JwIndicatorUtils;
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
    @Autowired private JwGridScoreMapper gridScoreMapper;
    @Autowired private IAiService aiService;

    @Override
    @Transactional
    public int computeBranchData(String city, Integer dataYear) {
        assignGridToBranch(city);
        int count = computeBranchIndicators(city, dataYear);
        if (count == 0) return 0;

        // 入口处一次性加载后续共享的数据：网点列表、指标配置、"数据计算表"数据
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwIndicatorConfig> allFormulaLeaves = indicatorConfigMapper.selectLeavesByType("branch");
        List<JwIndicatorConfig> allRawLeaves = indicatorConfigMapper.selectLeavesByType("branch_raw");

        // 构建完整的 branch 类型配置映射（公式 + raw + auto 全部叶子）
        List<JwIndicatorConfig> allBranch = new ArrayList<>();
        allBranch.addAll(indicatorConfigMapper.selectByType("branch"));
        allBranch.addAll(indicatorConfigMapper.selectByType("branch_raw"));
        Map<String, JwIndicatorConfig> configMap = allBranch.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        // 加载"数据计算表"数据（步骤 2 刚写入），步骤 3/4 共享
        List<JwBranchIndicator> allComputed = branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear, "数据计算表");
        Map<Long, Map<String, Double>> valueMap = new HashMap<>();
        for (JwBranchIndicator ind : allComputed) {
            valueMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>())
                .put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }

        computeBranchSummary(city, dataYear, branches, allBranch, configMap, allComputed, valueMap);
        computeBranchNormalized(city, dataYear, branches, valueMap);
        computeBranchScore(city, dataYear, branches, configMap);
        // 四象限计算
        computeQuadrant(city, dataYear);
        // 计算完成后标记该城市 AI 分析记录过期
        aiService.invalidateByCity(city);
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

        // 批量清理该市该年旧的计算数据（消除 N+1 DELETE）
        branchIndicatorMapper.deleteByCityYearAndSheetType(city, dataYear, "数据计算表");
        branchIndicatorMapper.deleteByCityYearAndSheetType(city, dataYear, "数据计算表归一化");

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
        List<JwBranchIndicator> batch = new ArrayList<>();
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
                Double val = entry.getValue();
                // 过滤 NaN（来源于除以 0 的 safeDiv），避免无效值污染 TOPSIS 计算
                if (val == null || val.isNaN()) continue;
                JwBranchIndicator indicator = new JwBranchIndicator();
                indicator.setBranchId(branch.getBranchId());
                indicator.setDataYear(dataYear);
                indicator.setSheetType("数据计算表");
                indicator.setIndicatorCode(entry.getKey());
                indicator.setIndicatorValue(val);
                batch.add(indicator);
            }
            count++;
        }
        if (!batch.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < batch.size(); i += batchSize) {
                int end = Math.min(i + batchSize, batch.size());
                branchIndicatorMapper.batchInsert(batch.subList(i, end));
            }
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

    /**
     * @return 员工数，<=0 或 null 时返回 NaN（触发 safeDiv 产生 NaN，避免静默降级）
     */
    private double totalStaff(JwBranchInfo branch) {
        return branch.getTotalStaff() != null && branch.getTotalStaff() > 0 ? branch.getTotalStaff() : Double.NaN;
    }

    /**
     * @return 面积，<=0 或 null 时返回 NaN
     */
    private double totalArea(JwBranchInfo branch) {
        return branch.getTotalArea() != null && branch.getTotalArea() > 0 ? branch.getTotalArea() : Double.NaN;
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
     * 日均增幅 = 本年日均增量 / 上年日均余额
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

    /**
     * 安全除法 — 分母为 0 时返回 NaN，避免静默产生误导性的 0 值
     */
    private double safeDiv(double numerator, double denominator) {
        if (denominator == 0) return Double.NaN;
        return numerator / denominator;
    }

    @Override
    @Transactional
    public int assignGridToBranch(String city) {
        // 由数据库做空间关联：通过坐标包围盒匹配，一次查出所有 branchId → gridCode
        List<java.util.Map<String, Object>> mappings = branchInfoMapper.selectBranchGridMapping(city);
        if (mappings == null || mappings.isEmpty()) return 0;

        // 构建 branchId → gridCode 映射
        java.util.Map<Long, String> gridCodeMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : mappings) {
            Long branchId = row.get("branchId") instanceof Number
                ? ((Number) row.get("branchId")).longValue() : null;
            String gridCode = (String) row.get("gridCode");
            if (branchId != null && gridCode != null) {
                gridCodeMap.put(branchId, gridCode);
            }
        }
        if (gridCodeMap.isEmpty()) return 0;

        // 查出该城市所有网点，设置 gridCode 后批量更新
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchInfo> needUpdate = new java.util.ArrayList<>();
        int count = 0;
        for (JwBranchInfo branch : branches) {
            String gc = gridCodeMap.get(branch.getBranchId());
            if (gc != null) {
                branch.setGridCode(gc);
                needUpdate.add(branch);
                count++;
            }
        }
        if (!needUpdate.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < needUpdate.size(); i += batchSize) {
                int end = Math.min(i + batchSize, needUpdate.size());
                branchInfoMapper.batchUpdateGridCode(needUpdate.subList(i, end));
            }
        }
        return count;
    }

    private void computeBranchSummary(String city, Integer dataYear,
                                       List<JwBranchInfo> branches,
                                       List<JwIndicatorConfig> allBranch,
                                       Map<String, JwIndicatorConfig> configMap,
                                       List<JwBranchIndicator> allComputed,
                                       Map<Long, Map<String, Double>> valueMap) {
        branchSummaryMapper.deleteByCityAndYear(city, dataYear);

        List<JwIndicatorConfig> leaves = allBranch.stream()
            .filter(c -> c.isLeaf(configMap))
            .collect(Collectors.toList());

        if (branches.isEmpty() || leaves.isEmpty()) return;

        List<JwBranchSummary> batch = new ArrayList<>();
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
            batch.add(summary);
        }
        if (!batch.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < batch.size(); i += batchSize) {
                int end = Math.min(i + batchSize, batch.size());
                branchSummaryMapper.batchInsert(batch.subList(i, end));
            }
        }
    }

    private void computeBranchNormalized(String city, Integer dataYear,
                                          List<JwBranchInfo> branches,
                                          Map<Long, Map<String, Double>> valueMap) {
        // 从 DB 查 summary（刚由 computeBranchSummary 写入）
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);
        if (branches.isEmpty() || summaries.isEmpty()) return;

        // 批量删除旧归一化数据
        branchIndicatorMapper.deleteByCityYearAndSheetType(city, dataYear, "数据计算表归一化");

        List<JwBranchIndicator> allBatch = new ArrayList<>();
        List<JwBranchSummary> summaryUpdates = new ArrayList<>();
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
            double maxNorm = 0, minNorm = Double.MAX_VALUE;
            for (Map.Entry<Long, Double> entry : branchValueMap.entrySet()) {
                double norm = TopsisCalculator.normalize(entry.getValue(), sumSq);
                maxNorm = Math.max(maxNorm, norm);
                minNorm = Math.min(minNorm, norm);

                JwBranchIndicator normInd = new JwBranchIndicator();
                normInd.setBranchId(entry.getKey());
                normInd.setDataYear(dataYear);
                normInd.setSheetType("数据计算表归一化");
                normInd.setIndicatorCode(code);
                normInd.setIndicatorValue(norm);
                allBatch.add(normInd);
            }
            summary.setMaxNorm(maxNorm);
            summary.setMinNorm(minNorm);
            summaryUpdates.add(summary);
        }
        if (!allBatch.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < allBatch.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allBatch.size());
                branchIndicatorMapper.batchInsert(allBatch.subList(i, end));
            }
        }
        for (JwBranchSummary summary : summaryUpdates) {
            branchSummaryMapper.updateBranchSummary(summary);
        }
    }

    @Override
    @Transactional
    public int computeBranchScore(String city, Integer dataYear) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwIndicatorConfig> allBranch = indicatorConfigMapper.selectByTypes(Arrays.asList("branch", "branch_auto", "branch_raw"));
        Map<String, JwIndicatorConfig> configMap = allBranch.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));
        return computeBranchScore(city, dataYear, branches, configMap);
    }

    private int computeBranchScore(String city, Integer dataYear,
                                    List<JwBranchInfo> branches,
                                    Map<String, JwIndicatorConfig> configMap) {
        // 从 DB 查 summary（刚由 computeBranchNormalized 写入的 maxNorm/minNorm）
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);
        if (branches.isEmpty() || summaries.isEmpty()) return 0;

        Map<String, JwBranchSummary> summaryMap = summaries.stream()
            .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a));

        branchScoreMapper.deleteByCityAndYear(city, dataYear);

        List<JwIndicatorConfig> allBranchList = new ArrayList<>();
        // 从 configMap 还原 allBranch 结构（用于计算 roots / allLeaves）
        List<JwIndicatorConfig> roots = configMap.values().stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());

        // 预计算所有根节点的叶子列表 + overall 叶子列表
        Map<String, List<String>> rootLeafCodes = new LinkedHashMap<>();
        for (JwIndicatorConfig root : roots) {
            rootLeafCodes.put(root.getIndicatorCode(), JwIndicatorUtils.getLeafCodesUnder(root.getIndicatorCode(), configMap));
        }
        List<JwIndicatorConfig> allLeaves = configMap.values().stream()
            .filter(c -> c.isLeaf(configMap))
            .collect(Collectors.toList());
        List<String> allLeafCodes = allLeaves.stream()
            .map(JwIndicatorConfig::getIndicatorCode).collect(Collectors.toList());
        rootLeafCodes.put("overall", allLeafCodes);

        // 批量加载全部归一化数据
        List<JwBranchIndicator> allNormalized = branchIndicatorMapper.selectByCityYearAndSheetType(city, dataYear, "数据计算表归一化");
        Map<Long, Map<String, Double>> normValueMap = new HashMap<>();
        for (JwBranchIndicator ind : allNormalized) {
            normValueMap.computeIfAbsent(ind.getBranchId(), k -> new HashMap<>())
                .put(ind.getIndicatorCode(), ind.getIndicatorValue());
        }

        List<JwBranchScore> batchScores = new ArrayList<>();
        for (JwBranchInfo branch : branches) {
            Map<String, Double> branchNorm = normValueMap.getOrDefault(branch.getBranchId(), Collections.emptyMap());
            for (Map.Entry<String, List<String>> entry : rootLeafCodes.entrySet()) {
                String category = entry.getKey();
                List<String> leafCodes = entry.getValue();
                JwBranchScore score = computeCategoryScore(branch, dataYear, city, category, leafCodes, summaryMap, branchNorm);
                if (score != null) {
                    batchScores.add(score);
                }
            }
        }
        if (!batchScores.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < batchScores.size(); i += batchSize) {
                int end = Math.min(i + batchSize, batchScores.size());
                branchScoreMapper.batchInsert(batchScores.subList(i, end));
            }
        }

        computeRankings(city, dataYear);
        return branches.size();
    }

    private JwBranchScore computeCategoryScore(JwBranchInfo branch, Integer dataYear, String city,
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
        return score;
    }

    private void computeRankings(String city, Integer dataYear) {
        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectRoots("branch_raw");
        for (JwIndicatorConfig root : roots) {
            List<JwBranchScore> scores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, root.getIndicatorCode());
            if (scores.isEmpty()) continue;
            scores.sort((a, b) -> Double.compare(
                b.getCategoryScore() != null ? b.getCategoryScore() : 0,
                a.getCategoryScore() != null ? a.getCategoryScore() : 0));
            for (int i = 0; i < scores.size(); i++) {
                scores.get(i).setRankNum(i + 1);
            }
            int batchSize = 500;
            for (int i = 0; i < scores.size(); i += batchSize) {
                int end = Math.min(i + batchSize, scores.size());
                branchScoreMapper.batchUpdateRank(scores.subList(i, end));
            }
        }
        List<JwBranchScore> overallScores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, "overall");
        if (overallScores.isEmpty()) return;
        overallScores.sort((a, b) -> Double.compare(
            b.getCategoryScore() != null ? b.getCategoryScore() : 0,
            a.getCategoryScore() != null ? a.getCategoryScore() : 0));
        for (int i = 0; i < overallScores.size(); i++) {
            overallScores.get(i).setRankNum(i + 1);
        }
        {
            int batchSize = 500;
            for (int i = 0; i < overallScores.size(); i += batchSize) {
                int end = Math.min(i + batchSize, overallScores.size());
                branchScoreMapper.batchUpdateRank(overallScores.subList(i, end));
            }
        }
    }

    /**
     * 四象限计算：基于网点 overall 排名和网格 siteScore 排名，
     * 按排名中位数分割为 Q1/Q2/Q3/Q4，写入 jw_branch_score。
     */
    void computeQuadrant(String city, Integer dataYear) {
        // 1. 查 overall 评分
        List<JwBranchScore> overallScores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, "overall");
        if (overallScores.size() < 2) return;

        // 2. 获取该城市所有网格的 siteScore 排名
        List<JwGridScore> allGrids = gridScoreMapper.selectByCity(city);
        Map<String, Double> siteScoreMap = allGrids.stream()
            .collect(Collectors.toMap(JwGridScore::getGridCode, JwGridScore::getSiteScore, (a, b) -> a));
        List<JwGridScore> sortedGrids = new ArrayList<>(allGrids);
        sortedGrids.sort((a, b) -> Double.compare(
            b.getSiteScore() != null ? b.getSiteScore() : 0,
            a.getSiteScore() != null ? a.getSiteScore() : 0));
        Map<String, Integer> gridRankMap = new HashMap<>();
        for (int i = 0; i < sortedGrids.size(); i++) {
            gridRankMap.put(sortedGrids.get(i).getGridCode(), i + 1);
        }

        // 3. 按 branchScore 排序
        List<JwBranchScore> sorted = new ArrayList<>(overallScores);
        sorted.sort((a, b) -> Double.compare(
            b.getCategoryScore() != null ? b.getCategoryScore() : 0,
            a.getCategoryScore() != null ? a.getCategoryScore() : 0));

        // 4. 计算 branchRank 和 siteRank
        Map<Long, Integer> branchRankMap = new HashMap<>();
        Map<Long, Integer> siteRankMap = new HashMap<>();
        List<JwBranchScore> validBranches = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            JwBranchScore s = sorted.get(i);
            Double ss = siteScoreMap.get(s.getGridCode());
            if (ss == null) continue;
            branchRankMap.put(s.getBranchId(), i + 1);
            siteRankMap.put(s.getBranchId(), gridRankMap.getOrDefault(s.getGridCode(), allGrids.size()));
            validBranches.add(s);
        }
        if (validBranches.size() < 2) return;

        // 5. 取排名中位数为分界
        int total = validBranches.size();
        int medianBranchRank = total / 2;
        int medianSiteRank = total / 2;

        // 6. 分配象限并更新
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
        {
            int batchSize = 500;
            for (int i = 0; i < toUpdate.size(); i += batchSize) {
                int end = Math.min(i + batchSize, toUpdate.size());
                branchScoreMapper.batchUpdateQuadrant(toUpdate.subList(i, end));
            }
        }
    }

    @Override
    public Map<String, Object> getBranchDataStatus(String city) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("city", city);
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        status.put("branchCount", branches.size());
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

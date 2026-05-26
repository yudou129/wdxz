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
 * 1. 计算数据计算表（人均/单位面积/户均等22个衍生指标）
 * 2. 计算汇总行（实际权重/MAX/MIN）
 * 3. 归一化处理（网点公式: value*weight/SQRT(SUMSQ)）
 * 4. 五类TOPSIS得分（营收/指标/客户/运营/总分）
 */
@Service
public class BranchComputeServiceImpl implements IBranchComputeService {

    @Autowired private JwBranchInfoMapper branchInfoMapper;
    @Autowired private JwBranchIndicatorMapper branchIndicatorMapper;
    @Autowired private JwBranchSummaryMapper branchSummaryMapper;
    @Autowired private JwBranchScoreMapper branchScoreMapper;
    @Autowired private JwIndicatorConfigMapper indicatorConfigMapper;
    @Autowired private JwBranchEffWeightMapper branchEffWeightMapper;
    @Autowired private JwGridMetaMapper gridMetaMapper;

    // ===== TOPSIS 五类指标分组（硬编码）=====

    private static final List<String> REVENUE_INDICATORS = Arrays.asList(
        "branch_rev_per_capita", "branch_rev_per_area"
    );

    private static final List<String> INDICATOR_INDICATORS = Arrays.asList(
        "branch_asset_avg_balance", "branch_asset_avg_growth",
        "branch_saving_avg_balance", "branch_saving_avg_growth",
        "branch_corp_dep_avg_balance", "branch_corp_dep_avg_growth",
        "branch_inst_dep_avg_balance", "branch_inst_dep_avg_growth",
        "branch_incloan_per_capita", "branch_perloan_per_capita"
    );

    private static final List<String> CUSTOMER_INDICATORS = Arrays.asList(
        "branch_pcust_t1_per_capita", "branch_pcust_t2_per_capita", "branch_pcust_t3_per_capita",
        "branch_ccust_h_per_capita", "branch_ccust_l_per_capita",
        "branch_icust_h_per_capita", "branch_icust_l_per_capita"
    );

    private static final List<String> OPERATION_INDICATORS = Arrays.asList(
        "branch_counter_per_area", "branch_terminal_per_area", "branch_atm_per_area"
    );

    private static final Map<String, List<String>> CATEGORY_MAP = new LinkedHashMap<>();
    static {
        CATEGORY_MAP.put("revenue", REVENUE_INDICATORS);
        CATEGORY_MAP.put("indicator", INDICATOR_INDICATORS);
        CATEGORY_MAP.put("customer", CUSTOMER_INDICATORS);
        CATEGORY_MAP.put("operation", OPERATION_INDICATORS);
    }

    private List<String> getAllBranchIndicators() {
        List<String> all = new ArrayList<>();
        all.addAll(REVENUE_INDICATORS);
        all.addAll(INDICATOR_INDICATORS);
        all.addAll(CUSTOMER_INDICATORS);
        all.addAll(OPERATION_INDICATORS);
        return all;
    }

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

        // 清理该市该年旧的计算数据
        for (JwBranchInfo branch : branches) {
            branchIndicatorMapper.deleteByBranchAndYear(branch.getBranchId(), dataYear, "数据计算表");
            branchIndicatorMapper.deleteByBranchAndYear(branch.getBranchId(), dataYear, "数据计算表归一化");
        }

        int count = 0;
        for (JwBranchInfo branch : branches) {
            Map<String, Double> baseData = getBaseData(branch.getBranchId(), dataYear);
            if (baseData.isEmpty()) continue;

            // 计算22个衍生指标
            Map<String, Double> computed = computeDerivedIndicators(branch, baseData, dataYear);
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
     * 计算22个衍生指标
     */
    private Map<String, Double> computeDerivedIndicators(JwBranchInfo branch, Map<String, Double> base, Integer dataYear) {
        Map<String, Double> result = new LinkedHashMap<>();
        int totalStaff = branch.getTotalStaff() != null ? branch.getTotalStaff() : 1;
        double totalArea = branch.getTotalArea() != null && branch.getTotalArea() > 0 ? branch.getTotalArea() : 1;

        // 基础指标值
        double interestIncome = base.getOrDefault("interest_income", 0.0);
        double feeIncome = base.getOrDefault("fee_income", 0.0);
        double totalRevenue = interestIncome + feeIncome;

        // 客户数
        double pcustT1 = base.getOrDefault("pcust_t1", 0.0);
        double pcustT2 = base.getOrDefault("pcust_t2", 0.0);
        double pcustT3 = base.getOrDefault("pcust_t3", 0.0);
        double pcustTotal = pcustT1 + pcustT2 + pcustT3;
        double ccustH = base.getOrDefault("ccust_h", 0.0);
        double ccustL = base.getOrDefault("ccust_l", 0.0);
        double ccustTotal = ccustH + ccustL;
        double icustH = base.getOrDefault("icust_h", 0.0);
        double icustL = base.getOrDefault("icust_l", 0.0);
        double icustTotal = icustH + icustL;

        // 1-2. 营收类
        result.put("branch_rev_per_capita", safeDiv(totalRevenue, totalStaff));
        result.put("branch_rev_per_area", safeDiv(totalRevenue, totalArea));

        // 3-4. 全量个人金融资产
        double assetBalance = base.getOrDefault("total_asset_balance", 0.0);
        result.put("branch_asset_avg_balance", safeDiv(assetBalance, pcustTotal));
        result.put("branch_asset_avg_growth", calcGrowthRate(branch.getBranchId(), dataYear, "total_asset_balance", "total_asset_growth"));

        // 5-6. 储蓄存款
        double savingBalance = base.getOrDefault("saving_balance", 0.0);
        result.put("branch_saving_avg_balance", safeDiv(savingBalance, pcustTotal));
        result.put("branch_saving_avg_growth", calcGrowthRate(branch.getBranchId(), dataYear, "saving_balance", "saving_growth"));

        // 7-8. 公司客户存款
        double corpDepBalance = base.getOrDefault("corp_dep_balance", 0.0);
        result.put("branch_corp_dep_avg_balance", safeDiv(corpDepBalance, ccustTotal));
        result.put("branch_corp_dep_avg_growth", calcGrowthRate(branch.getBranchId(), dataYear, "corp_dep_balance", "corp_dep_growth"));

        // 9-10. 机构客户存款
        double instDepBalance = base.getOrDefault("inst_dep_balance", 0.0);
        result.put("branch_inst_dep_avg_balance", safeDiv(instDepBalance, icustTotal));
        result.put("branch_inst_dep_avg_growth", calcGrowthRate(branch.getBranchId(), dataYear, "inst_dep_balance", "inst_dep_growth"));

        // 11-12. 贷款类
        double inclusiveLoan = base.getOrDefault("inclusive_loan_amount", 0.0);
        double personalLoan = base.getOrDefault("personal_loan_amount", 0.0);
        result.put("branch_incloan_per_capita", safeDiv(inclusiveLoan, totalStaff));
        result.put("branch_perloan_per_capita", safeDiv(personalLoan, totalStaff));

        // 13-19. 客户发展（客户数/总人数）
        result.put("branch_pcust_t1_per_capita", safeDiv(pcustT1, totalStaff));
        result.put("branch_pcust_t2_per_capita", safeDiv(pcustT2, totalStaff));
        result.put("branch_pcust_t3_per_capita", safeDiv(pcustT3, totalStaff));
        result.put("branch_ccust_h_per_capita", safeDiv(ccustH, totalStaff));
        result.put("branch_ccust_l_per_capita", safeDiv(ccustL, totalStaff));
        result.put("branch_icust_h_per_capita", safeDiv(icustH, totalStaff));
        result.put("branch_icust_l_per_capita", safeDiv(icustL, totalStaff));

        // 20-22. 业务运营（业务量/总面积）
        double counterTxn = base.getOrDefault("counter_txn", 0.0);
        double terminalTxn = base.getOrDefault("terminal_txn", 0.0);
        double atmTxn = base.getOrDefault("atm_txn", 0.0);
        result.put("branch_counter_per_area", safeDiv(counterTxn, totalArea));
        result.put("branch_terminal_per_area", safeDiv(terminalTxn, totalArea));
        result.put("branch_atm_per_area", safeDiv(atmTxn, totalArea));

        return result;
    }

    /**
     * 日均增幅 = 本年日均增量 / 上年日均余额
     * 2023年无2022数据，返回null表示"--"
     */
    private Double calcGrowthRate(Long branchId, Integer dataYear, String balanceCode, String growthCode) {
        if (dataYear == 2023) return null;  // 无上一年数据
        // 查询上年日均余额
        List<JwBranchIndicator> prevYear = branchIndicatorMapper.selectByBranchAndYear(branchId, dataYear - 1, "基础数据");
        double prevBalance = 0;
        for (JwBranchIndicator item : prevYear) {
            if (balanceCode.equals(item.getIndicatorCode())) {
                prevBalance = item.getIndicatorValue() != null ? item.getIndicatorValue() : 0;
                break;
            }
        }
        if (prevBalance == 0) return null;
        // 查询本年日均增量
        List<JwBranchIndicator> currYear = branchIndicatorMapper.selectByBranchAndYear(branchId, dataYear, "基础数据");
        double currGrowth = 0;
        for (JwBranchIndicator item : currYear) {
            if (growthCode.equals(item.getIndicatorCode())) {
                currGrowth = item.getIndicatorValue() != null ? item.getIndicatorValue() : 0;
                break;
            }
        }
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
        List<JwWeightConfig> weights = branchEffWeightMapper.selectAll();
        Map<String, Double> weightMap = weights.stream()
            .filter(w -> w.getIndicatorCode() != null && !w.getIndicatorCode().isEmpty())
            .collect(Collectors.toMap(JwWeightConfig::getIndicatorCode, JwWeightConfig::getTotalWeight, (a, b) -> a));

        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<String> allIndicators = getAllBranchIndicators();

        for (String code : allIndicators) {
            List<Double> values = new ArrayList<>();
            for (JwBranchInfo branch : branches) {
                JwBranchIndicator ind = branchIndicatorMapper.selectByBranchYearSheetAndIndicator(
                    branch.getBranchId(), dataYear, "数据计算表", code);
                if (ind != null && ind.getIndicatorValue() != null) {
                    values.add(ind.getIndicatorValue());
                }
            }
            if (values.isEmpty()) continue;

            double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double minVal = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double weight = weightMap.getOrDefault(code, 0.0);

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

        // 清理旧归一化数据
        for (JwBranchInfo branch : branches) {
            branchIndicatorMapper.deleteByBranchAndYear(branch.getBranchId(), dataYear, "数据计算表归一化");
        }

        for (JwBranchSummary summary : summaries) {
            String code = summary.getIndicatorCode();
            List<Double> colValues = new ArrayList<>();
            Map<Long, Double> branchValueMap = new LinkedHashMap<>();
            for (JwBranchInfo branch : branches) {
                JwBranchIndicator ind = branchIndicatorMapper.selectByBranchYearSheetAndIndicator(
                    branch.getBranchId(), dataYear, "数据计算表", code);
                double val = (ind != null && ind.getIndicatorValue() != null) ? ind.getIndicatorValue() : 0;
                colValues.add(val);
                branchValueMap.put(branch.getBranchId(), val);
            }
            // 网点归一化: value * weight / SQRT(SUMSQ)
            double sumSq = TopsisCalculator.calcSumSq(colValues);
            double weight = summary.getActualWeight();
            double maxNorm = Double.MIN_VALUE, minNorm = Double.MAX_VALUE;
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

        // 清理旧得分
        branchScoreMapper.deleteByCityAndYear(city, dataYear);

        int count = 0;
        for (JwBranchInfo branch : branches) {
            // 对每个类别和总分做TOPSIS
            for (Map.Entry<String, List<String>> catEntry : CATEGORY_MAP.entrySet()) {
                String category = catEntry.getKey();
                List<String> indicatorCodes = catEntry.getValue();
                computeCategoryScore(branch, dataYear, city, category, indicatorCodes, summaryMap);
            }
            // 总分
            List<String> allCodes = getAllBranchIndicators();
            computeCategoryScore(branch, dataYear, city, "overall", allCodes, summaryMap);
            count++;
        }

        // 计算排名
        computeRankings(city, dataYear);

        return count;
    }

    private void computeCategoryScore(JwBranchInfo branch, Integer dataYear, String city,
                                       String category, List<String> indicatorCodes,
                                       Map<String, JwBranchSummary> summaryMap) {
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
            JwBranchIndicator norm = branchIndicatorMapper.selectByBranchYearSheetAndIndicator(
                branch.getBranchId(), dataYear, "数据计算表归一化", code);
            normValues.add(norm != null && norm.getIndicatorValue() != null ? norm.getIndicatorValue() : 0.0);
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
        for (String category : CATEGORY_MAP.keySet()) {
            List<JwBranchScore> scores = branchScoreMapper.selectByCityAndYearAndCategory(city, dataYear, category);
            scores.sort((a, b) -> Double.compare(
                b.getCategoryScore() != null ? b.getCategoryScore() : 0,
                a.getCategoryScore() != null ? a.getCategoryScore() : 0));
            for (int i = 0; i < scores.size(); i++) {
                scores.get(i).setRankNum(i + 1);
                branchScoreMapper.updateRank(scores.get(i));
            }
        }
        // overall排名
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
        status.put("hasWeight", branchEffWeightMapper.selectAll().size() > 0);
        boolean hasBase = false;
        if (!branches.isEmpty()) {
            List<JwBranchIndicator> base = branchIndicatorMapper.selectByBranchAndYear(branches.get(0).getBranchId(), 2024, "基础数据");
            hasBase = !base.isEmpty();
        }
        status.put("hasBaseData", hasBase);
        return status;
    }
}

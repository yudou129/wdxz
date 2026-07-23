package com.ruoyi.jwmap.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * TOPSIS 计算引擎
 * 统一处理网格选址得分和网点效能得分的TOPSIS计算
 */
public class TopsisCalculator {

    private static final Logger log = LoggerFactory.getLogger(TopsisCalculator.class);

    /**
     * 归一化：value / SQRT(SUMSQ(column))
     * 统一处理网格和网点，两者公式相同
     */
    public static double normalize(double value, double sumSq) {
        if (sumSq <= 0) return 0;
        return value / Math.sqrt(sumSq);
    }

    /**
     * @deprecated 使用 {@link #normalize(double, double)}
     */
    @Deprecated
    public static double normalizeGrid(double value, double sumSq) {
        return normalize(value, sumSq);
    }

    /**
     * @deprecated 使用 {@link #normalize(double, double)}
     */
    @Deprecated
    public static double normalizeBranch(double value, double sumSq) {
        return normalize(value, sumSq);
    }

    /**
     * 计算一列的 SUMSQ（所有值的平方和）
     * @throws NullPointerException 如果 values 为 null
     */
    public static double calcSumSq(List<Double> values) {
        requireNonNull(values, "values must not be null");
        if (values.isEmpty()) return 0;
        return values.stream().mapToDouble(v -> v != null ? v * v : 0).sum();
    }

    /**
     * 计算归一化值列表
     * @param rawValues 原始值列表（null → 返回空列表）
     * @return 归一化值列表（保持顺序）
     */
    public static List<Double> normalizeColumn(List<Double> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) return rawValues == null ? Collections.emptyList() : rawValues;
        double sumSq = calcSumSq(rawValues);
        return rawValues.stream()
            .map(v -> normalize(v != null ? v : 0, sumSq))
            .collect(Collectors.toList());
    }

    /**
     * @deprecated 使用 {@link #normalizeColumn(List)}
     */
    @Deprecated
    public static List<Double> normalizeGridColumn(List<Double> rawValues) {
        return normalizeColumn(rawValues);
    }

    /**
     * @deprecated 使用 {@link #normalizeColumn(List)}
     */
    @Deprecated
    public static List<Double> normalizeBranchColumn(List<Double> rawValues) {
        return normalizeColumn(rawValues);
    }

    /**
     * TOPSIS 正理想解距离 D+
     * D+ = SQRT( SUM( ((norm_ij - MAX_norm_j) / (MAX_norm_j - MIN_norm_j))^2 * weight_j ) )
     * @throws NullPointerException 如果任一参数为 null
     */
    public static double calcPositiveDistance(
            List<Double> normValues, List<Double> maxNorms,
            List<Double> minNorms, List<Double> weights) {
        requireNonNull(normValues, "normValues must not be null");
        requireNonNull(maxNorms, "maxNorms must not be null");
        requireNonNull(minNorms, "minNorms must not be null");
        requireNonNull(weights, "weights must not be null");

        if (normValues.isEmpty()) return 0;

        double sum = 0;
        int size = Math.min(normValues.size(), Math.min(maxNorms.size(), minNorms.size()));
        for (int j = 0; j < size; j++) {
            double norm = normValues.get(j) != null ? normValues.get(j) : 0;
            double max = maxNorms.get(j) != null ? maxNorms.get(j) : 0;
            double min = minNorms.get(j) != null ? minNorms.get(j) : 0;
            double w = j < weights.size() && weights.get(j) != null ? weights.get(j) : 0;
            double denom = max - min;
            if (denom == 0) {
                if (log.isWarnEnabled()) {
                    log.warn("TOPSIS D+ 除零: index={}, max={}, min={}, 指标区分度为0", j, max, min);
                }
                continue;
            }
            double term = (norm - max) / denom;
            sum += term * term * w;
        }
        return Math.sqrt(sum);
    }

    /**
     * TOPSIS 负理想解距离 D-
     * D- = SQRT( SUM( ((norm_ij - MIN_norm_j) / (MAX_norm_j - MIN_norm_j))^2 * weight_j ) )
     * @throws NullPointerException 如果任一参数为 null
     */
    public static double calcNegativeDistance(
            List<Double> normValues, List<Double> maxNorms,
            List<Double> minNorms, List<Double> weights) {
        requireNonNull(normValues, "normValues must not be null");
        requireNonNull(maxNorms, "maxNorms must not be null");
        requireNonNull(minNorms, "minNorms must not be null");
        requireNonNull(weights, "weights must not be null");

        if (normValues.isEmpty()) return 0;

        double sum = 0;
        int size = Math.min(normValues.size(), Math.min(maxNorms.size(), minNorms.size()));
        for (int j = 0; j < size; j++) {
            double norm = normValues.get(j) != null ? normValues.get(j) : 0;
            double max = maxNorms.get(j) != null ? maxNorms.get(j) : 0;
            double min = minNorms.get(j) != null ? minNorms.get(j) : 0;
            double w = j < weights.size() && weights.get(j) != null ? weights.get(j) : 0;
            double denom = max - min;
            if (denom == 0) {
                if (log.isWarnEnabled()) {
                    log.warn("TOPSIS D- 除零: index={}, max={}, min={}, 指标区分度为0", j, max, min);
                }
                continue;
            }
            double term = (norm - min) / denom;
            sum += term * term * w;
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算得分 = D- / (D+ + D-)
     * @param dPlus 正理想解距离（负数 → 取绝对值）
     * @param dMinus 负理想解距离（负数 → 取绝对值）
     */
    public static double calcScore(double dPlus, double dMinus) {
        double absDPlus = Math.abs(dPlus);
        double absDMinus = Math.abs(dMinus);
        double denom = absDPlus + absDMinus;
        if (denom == 0) return 0;
        if (Double.isNaN(absDPlus) || Double.isNaN(absDMinus)) return 0;
        if (Double.isInfinite(absDPlus) || Double.isInfinite(absDMinus)) return 0;
        return absDMinus / denom;
    }

    /**
     * 完整TOPSIS计算（单行）
     * @return [D+, D-, score]
     * @throws NullPointerException 如果任一参数为 null
     */
    public static double[] calcTopsis(
            List<Double> normValues,
            List<Double> maxNorms,
            List<Double> minNorms,
            List<Double> weights) {
        double dPlus = calcPositiveDistance(normValues, maxNorms, minNorms, weights);
        double dMinus = calcNegativeDistance(normValues, maxNorms, minNorms, weights);
        double score = calcScore(dPlus, dMinus);
        return new double[]{dPlus, dMinus, score};
    }
}

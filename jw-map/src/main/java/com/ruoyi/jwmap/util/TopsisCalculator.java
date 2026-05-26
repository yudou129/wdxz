package com.ruoyi.jwmap.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TOPSIS 计算引擎
 * 统一处理网格选址得分和网点效能得分的TOPSIS计算
 */
public class TopsisCalculator {

    /**
     * 归一化：网格公式 = value / SQRT(SUMSQ(column))
     */
    public static double normalizeGrid(double value, double sumSq) {
        if (sumSq <= 0) return 0;
        return value / Math.sqrt(sumSq);
    }

    /**
     * 归一化：网点公式 = value * weight / SQRT(SUMSQ(column))
     */
    public static double normalizeBranch(double value, double weight, double sumSq) {
        if (sumSq <= 0) return 0;
        return value * weight / Math.sqrt(sumSq);
    }

    /**
     * 计算一列的 SUMSQ（所有值的平方和）
     */
    public static double calcSumSq(List<Double> values) {
        return values.stream().mapToDouble(v -> v * v).sum();
    }

    /**
     * 计算归一化值列表（网格模式）
     * @param rawValues 原始值列表
     * @return 归一化值列表（保持顺序）
     */
    public static List<Double> normalizeGridColumn(List<Double> rawValues) {
        double sumSq = calcSumSq(rawValues);
        return rawValues.stream()
            .map(v -> normalizeGrid(v, sumSq))
            .collect(Collectors.toList());
    }

    /**
     * 计算归一化值列表（网点模式，带权重）
     * @param rawValues 原始值列表
     * @param weights   对应权重列表
     */
    public static List<Double> normalizeBranchColumn(List<Double> rawValues, List<Double> weights) {
        double sumSq = calcSumSq(rawValues);
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < rawValues.size(); i++) {
            double w = i < weights.size() ? weights.get(i) : 1.0;
            result.add(rawValues.get(i) * w / Math.sqrt(sumSq));
        }
        return result;
    }

    /**
     * TOPSIS 正理想解距离 D+
     * D+ = SQRT( SUM( ((norm_ij - MAX_norm_j) / (MAX_norm_j - MIN_norm_j))^2 * weight_j ) )
     */
    public static double calcPositiveDistance(
            List<Double> normValues, List<Double> maxNorms,
            List<Double> minNorms, List<Double> weights) {
        double sum = 0;
        for (int j = 0; j < normValues.size(); j++) {
            double norm = normValues.get(j);
            double max = maxNorms.get(j);
            double min = minNorms.get(j);
            double w = j < weights.size() ? weights.get(j) : 0;
            double denom = max - min;
            if (denom == 0) continue;
            double term = (norm - max) / denom;
            sum += term * term * w;
        }
        return Math.sqrt(sum);
    }

    /**
     * TOPSIS 负理想解距离 D-
     * D- = SQRT( SUM( ((norm_ij - MIN_norm_j) / (MAX_norm_j - MIN_norm_j))^2 * weight_j ) )
     */
    public static double calcNegativeDistance(
            List<Double> normValues, List<Double> maxNorms,
            List<Double> minNorms, List<Double> weights) {
        double sum = 0;
        for (int j = 0; j < normValues.size(); j++) {
            double norm = normValues.get(j);
            double max = maxNorms.get(j);
            double min = minNorms.get(j);
            double w = j < weights.size() ? weights.get(j) : 0;
            double denom = max - min;
            if (denom == 0) continue;
            double term = (norm - min) / denom;
            sum += term * term * w;
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算得分 = D- / (D+ + D-)
     */
    public static double calcScore(double dPlus, double dMinus) {
        double denom = dPlus + dMinus;
        if (denom == 0) return 0;
        return dMinus / denom;
    }

    /**
     * 完整TOPSIS计算（单行）
     * @return [D+, D-, score]
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

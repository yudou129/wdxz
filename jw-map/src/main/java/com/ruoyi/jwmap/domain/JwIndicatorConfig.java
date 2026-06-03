package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Map;

/**
 * 指标配置对象 jw_indicator_config
 *
 * 多级树结构：通过 parent_code 形成任意深度的层级，叶子节点的
 * 有效权重 = 沿 parent_code 链向上递归累乘各级 calculation_weight
 */
public class JwIndicatorConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long indicatorId;
    private String indicatorCode;
    private String indicatorName;
    /** 'grid'(网格三聚焦) / 'branch_raw'(网点原始数据) / 'branch'(网点衍生指标) */
    private String indicatorType;
    /** 上级指标编码，根节点为 NULL */
    private String parentCode;
    /** '0'=普通指标 / '1'=衍生计算指标 */
    private String isDerived;
    /** 计算模式: per_capita/per_area/sum_per_capita/sum_per_area/per_customer/growth_rate */
    private String computationPattern;
    /** 参与计算的指标编码（格式依 computation_pattern 而定） */
    private String inputCodes;
    /** 本级权重 */
    private Double calculationWeight;
    private Integer sortOrder;

    public Long getIndicatorId() { return indicatorId; }
    public void setIndicatorId(Long indicatorId) { this.indicatorId = indicatorId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getIndicatorType() { return indicatorType; }
    public void setIndicatorType(String indicatorType) { this.indicatorType = indicatorType; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getIsDerived() { return isDerived; }
    public void setIsDerived(String isDerived) { this.isDerived = isDerived; }
    public String getComputationPattern() { return computationPattern; }
    public void setComputationPattern(String computationPattern) { this.computationPattern = computationPattern; }
    public String getInputCodes() { return inputCodes; }
    public void setInputCodes(String inputCodes) { this.inputCodes = inputCodes; }
    public Double getCalculationWeight() { return calculationWeight; }
    public void setCalculationWeight(Double calculationWeight) { this.calculationWeight = calculationWeight; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 解析 input_codes 为字符串数组（按逗号分割）
     */
    public String[] getInputCodesArray() {
        if (inputCodes == null || inputCodes.isEmpty()) return new String[0];
        return inputCodes.split(",");
    }

    /**
     * 沿 parent_code 链递归累乘，计算叶子节点的有效权重
     * @param allConfigs key=indicator_code 的全量指标映射
     * @return 有效权重（L1 × L2 × ... × 本级）
     */
    public double getEffectiveWeight(Map<String, JwIndicatorConfig> allConfigs) {
        double weight = this.calculationWeight != null ? this.calculationWeight : 1.0;
        String parent = this.parentCode;
        while (parent != null && !parent.isEmpty()) {
            JwIndicatorConfig p = allConfigs.get(parent);
            if (p == null) break;
            weight *= (p.calculationWeight != null ? p.calculationWeight : 1.0);
            parent = p.parentCode;
        }
        return weight;
    }

    /**
     * 获取从根到当前节点的权重路径字符串
     */
    public String getWeightPath(Map<String, JwIndicatorConfig> allConfigs) {
        StringBuilder sb = new StringBuilder();
        buildPath(sb, this, allConfigs);
        return sb.toString();
    }

    private void buildPath(StringBuilder sb, JwIndicatorConfig node, Map<String, JwIndicatorConfig> allConfigs) {
        if (node.parentCode != null && !node.parentCode.isEmpty()) {
            JwIndicatorConfig p = allConfigs.get(node.parentCode);
            if (p != null) buildPath(sb, p, allConfigs);
        }
        if (sb.length() > 0) sb.append(" → ");
        sb.append(node.indicatorName).append("(").append(node.calculationWeight).append(")");
    }

    /**
     * 判断当前节点是否为叶子节点（在 allConfigs 中没有子节点）
     */
    public boolean isLeaf(Map<String, JwIndicatorConfig> allConfigs) {
        for (JwIndicatorConfig other : allConfigs.values()) {
            if (this.indicatorCode.equals(other.parentCode)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("indicatorId", getIndicatorId())
            .append("indicatorCode", getIndicatorCode())
            .append("indicatorName", getIndicatorName())
            .append("indicatorType", getIndicatorType())
            .append("parentCode", getParentCode())
            .append("isDerived", getIsDerived())
            .append("calculationWeight", getCalculationWeight())
            .toString();
    }
}

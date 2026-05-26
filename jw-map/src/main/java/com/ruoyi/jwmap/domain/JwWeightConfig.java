package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 权重基础对象（外部资源权重和网点效能权重共用）
 */
public class JwWeightConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long weightId;
    private String level1Name;
    private Double level1Ratio;
    private String level2Name;
    private Double level2Ratio;
    private String level3Name;
    private Double level3Ratio;
    private Double totalWeight;
    private String indicatorCode;

    public Long getWeightId() { return weightId; }
    public void setWeightId(Long weightId) { this.weightId = weightId; }
    public String getLevel1Name() { return level1Name; }
    public void setLevel1Name(String level1Name) { this.level1Name = level1Name; }
    public Double getLevel1Ratio() { return level1Ratio; }
    public void setLevel1Ratio(Double level1Ratio) { this.level1Ratio = level1Ratio; }
    public String getLevel2Name() { return level2Name; }
    public void setLevel2Name(String level2Name) { this.level2Name = level2Name; }
    public Double getLevel2Ratio() { return level2Ratio; }
    public void setLevel2Ratio(Double level2Ratio) { this.level2Ratio = level2Ratio; }
    public String getLevel3Name() { return level3Name; }
    public void setLevel3Name(String level3Name) { this.level3Name = level3Name; }
    public Double getLevel3Ratio() { return level3Ratio; }
    public void setLevel3Ratio(Double level3Ratio) { this.level3Ratio = level3Ratio; }
    public Double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(Double totalWeight) { this.totalWeight = totalWeight; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("weightId", getWeightId())
            .append("level3Name", getLevel3Name())
            .append("totalWeight", getTotalWeight())
            .toString();
    }
}

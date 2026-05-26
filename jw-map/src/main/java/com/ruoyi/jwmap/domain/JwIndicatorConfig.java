package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 指标配置对象 jw_indicator_config
 */
public class JwIndicatorConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long indicatorId;
    private String indicatorCode;
    private String indicatorName;
    private String categoryLevel1;
    private String categoryLevel2;
    private String dataType;
    private Integer sortOrder;
    private String sourceTables;
    private String isWeighted;
    private String isActive;

    public Long getIndicatorId() { return indicatorId; }
    public void setIndicatorId(Long indicatorId) { this.indicatorId = indicatorId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getCategoryLevel1() { return categoryLevel1; }
    public void setCategoryLevel1(String categoryLevel1) { this.categoryLevel1 = categoryLevel1; }
    public String getCategoryLevel2() { return categoryLevel2; }
    public void setCategoryLevel2(String categoryLevel2) { this.categoryLevel2 = categoryLevel2; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getSourceTables() { return sourceTables; }
    public void setSourceTables(String sourceTables) { this.sourceTables = sourceTables; }
    public String getIsWeighted() { return isWeighted; }
    public void setIsWeighted(String isWeighted) { this.isWeighted = isWeighted; }
    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("indicatorId", getIndicatorId())
            .append("indicatorCode", getIndicatorCode())
            .append("indicatorName", getIndicatorName())
            .toString();
    }
}

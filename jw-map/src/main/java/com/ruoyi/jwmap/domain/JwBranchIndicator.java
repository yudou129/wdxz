package com.ruoyi.jwmap.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网点业务指标对象 jw_branch_indicator
 */
public class JwBranchIndicator {
    private Long indicatorId;
    private Long branchId;
    private Integer dataYear;
    private String sheetType;
    private String indicatorCode;
    private Double indicatorValue;
    private java.util.Date createTime;

    public Long getIndicatorId() { return indicatorId; }
    public void setIndicatorId(Long indicatorId) { this.indicatorId = indicatorId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Integer getDataYear() { return dataYear; }
    public void setDataYear(Integer dataYear) { this.dataYear = dataYear; }
    public String getSheetType() { return sheetType; }
    public void setSheetType(String sheetType) { this.sheetType = sheetType; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Double getIndicatorValue() { return indicatorValue; }
    public void setIndicatorValue(Double indicatorValue) { this.indicatorValue = indicatorValue; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("indicatorId", getIndicatorId())
            .append("branchId", getBranchId())
            .append("dataYear", getDataYear())
            .append("indicatorCode", getIndicatorCode())
            .toString();
    }
}

package com.ruoyi.jwmap.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网格原始指标数据对象 jw_grid_data_raw
 */
public class JwGridDataRaw {
    private Long dataId;
    private String gridCode;
    private String indicatorCode;
    private Double indicatorValue;
    private java.util.Date createTime;

    public Long getDataId() { return dataId; }
    public void setDataId(Long dataId) { this.dataId = dataId; }
    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Double getIndicatorValue() { return indicatorValue; }
    public void setIndicatorValue(Double indicatorValue) { this.indicatorValue = indicatorValue; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("dataId", getDataId())
            .append("gridCode", getGridCode())
            .append("indicatorCode", getIndicatorCode())
            .toString();
    }
}

package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 人口热力对象 jw_population_heat
 */
public class JwPopulationHeat extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long heatId;
    private String gridCode;
    private String indicatorCode;
    private Double indicatorValue;

    public Long getHeatId() { return heatId; }
    public void setHeatId(Long heatId) { this.heatId = heatId; }
    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Double getIndicatorValue() { return indicatorValue; }
    public void setIndicatorValue(Double indicatorValue) { this.indicatorValue = indicatorValue; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("heatId", getHeatId())
            .append("gridCode", getGridCode())
            .append("indicatorCode", getIndicatorCode())
            .append("indicatorValue", getIndicatorValue())
            .toString();
    }
}

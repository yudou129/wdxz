package com.ruoyi.jwmap.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网格指标汇总对象 jw_grid_summary
 */
public class JwGridSummary {
    private Long summaryId;
    private String city;
    private String indicatorCode;
    private Double actualWeight;
    private Double maxRaw;
    private Double minRaw;
    private Double maxNorm;
    private Double minNorm;
    private java.util.Date createTime;

    public Long getSummaryId() { return summaryId; }
    public void setSummaryId(Long summaryId) { this.summaryId = summaryId; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Double getActualWeight() { return actualWeight; }
    public void setActualWeight(Double actualWeight) { this.actualWeight = actualWeight; }
    public Double getMaxRaw() { return maxRaw; }
    public void setMaxRaw(Double maxRaw) { this.maxRaw = maxRaw; }
    public Double getMinRaw() { return minRaw; }
    public void setMinRaw(Double minRaw) { this.minRaw = minRaw; }
    public Double getMaxNorm() { return maxNorm; }
    public void setMaxNorm(Double maxNorm) { this.maxNorm = maxNorm; }
    public Double getMinNorm() { return minNorm; }
    public void setMinNorm(Double minNorm) { this.minNorm = minNorm; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("summaryId", getSummaryId())
            .append("city", getCity())
            .append("indicatorCode", getIndicatorCode())
            .toString();
    }
}

package com.ruoyi.jwmap.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网格得分对象 jw_grid_score
 */
public class JwGridScore {
    private String gridCode;
    private String city;
    private String scoreCategory;
    private Double positiveDistance;
    private Double negativeDistance;
    private Double siteScore;
    private java.util.Date createTime;

    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getScoreCategory() { return scoreCategory; }
    public void setScoreCategory(String scoreCategory) { this.scoreCategory = scoreCategory; }
    public Double getPositiveDistance() { return positiveDistance; }
    public void setPositiveDistance(Double positiveDistance) { this.positiveDistance = positiveDistance; }
    public Double getNegativeDistance() { return negativeDistance; }
    public void setNegativeDistance(Double negativeDistance) { this.negativeDistance = negativeDistance; }
    public Double getSiteScore() { return siteScore; }
    public void setSiteScore(Double siteScore) { this.siteScore = siteScore; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("gridCode", getGridCode())
            .append("siteScore", getSiteScore())
            .toString();
    }
}

package com.ruoyi.jwmap.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网点得分对象 jw_branch_score
 */
public class JwBranchScore {
    private Long scoreId;
    private Long branchId;
    private Integer dataYear;
    private String city;
    private String scoreCategory;
    private Double positiveDistance;
    private Double negativeDistance;
    private Double categoryScore;
    private Integer rankNum;
    private java.util.Date createTime;
    /** 以下为 JOIN jw_branch_info 获取的扩展字段 */
    private String secondaryBranch;
    private String primaryBranch;
    private String gridCode;
    /** 四象限 */
    private String quadrant;
    private Integer medianSiteRank;
    private Integer medianBranchRank;

    public Long getScoreId() { return scoreId; }
    public void setScoreId(Long scoreId) { this.scoreId = scoreId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Integer getDataYear() { return dataYear; }
    public void setDataYear(Integer dataYear) { this.dataYear = dataYear; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getScoreCategory() { return scoreCategory; }
    public void setScoreCategory(String scoreCategory) { this.scoreCategory = scoreCategory; }
    public Double getPositiveDistance() { return positiveDistance; }
    public void setPositiveDistance(Double positiveDistance) { this.positiveDistance = positiveDistance; }
    public Double getNegativeDistance() { return negativeDistance; }
    public void setNegativeDistance(Double negativeDistance) { this.negativeDistance = negativeDistance; }
    public Double getCategoryScore() { return categoryScore; }
    public void setCategoryScore(Double categoryScore) { this.categoryScore = categoryScore; }
    public Integer getRankNum() { return rankNum; }
    public void setRankNum(Integer rankNum) { this.rankNum = rankNum; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }
    public String getSecondaryBranch() { return secondaryBranch; }
    public void setSecondaryBranch(String secondaryBranch) { this.secondaryBranch = secondaryBranch; }
    public String getPrimaryBranch() { return primaryBranch; }
    public void setPrimaryBranch(String primaryBranch) { this.primaryBranch = primaryBranch; }
    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public String getQuadrant() { return quadrant; }
    public void setQuadrant(String quadrant) { this.quadrant = quadrant; }
    public Integer getMedianSiteRank() { return medianSiteRank; }
    public void setMedianSiteRank(Integer medianSiteRank) { this.medianSiteRank = medianSiteRank; }
    public Integer getMedianBranchRank() { return medianBranchRank; }
    public void setMedianBranchRank(Integer medianBranchRank) { this.medianBranchRank = medianBranchRank; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("scoreId", getScoreId())
            .append("branchId", getBranchId())
            .append("dataYear", getDataYear())
            .append("scoreCategory", getScoreCategory())
            .append("categoryScore", getCategoryScore())
            .toString();
    }
}

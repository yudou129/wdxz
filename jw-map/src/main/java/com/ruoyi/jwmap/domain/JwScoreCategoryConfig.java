package com.ruoyi.jwmap.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 评分分类指标映射配置 jw_score_category_config
 */
public class JwScoreCategoryConfig extends BaseEntity {

    private Long configId;

    @Excel(name = "类别编码")
    private String categoryCode;

    @Excel(name = "类别名称")
    private String categoryName;

    @Excel(name = "指标编码")
    private String indicatorCode;

    @Excel(name = "排序")
    private Integer sortOrder;

    @Excel(name = "是否启用")
    private String isActive;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }
}

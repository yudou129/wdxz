package com.ruoyi.jwmap.domain;

import java.util.Date;

/**
 * jw_ai_analysis 表实体
 * AI分析结果持久化记录
 */
public class AiAnalysisRecord {

    private Long id;
    /** 分析类型：branch/grid/quadrant */
    private String analysisType;
    /** 实体标识 */
    private String entityKey;
    /** 所属城市 */
    private String city;
    /** AI分析内容(Markdown) */
    private String content;
    /** 用户满意度：1满意 0不满意 NULL未评价 */
    private Integer satisfied;
    /** 过期标记：0未过期 1已过期 */
    private Integer expired;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public String getEntityKey() { return entityKey; }
    public void setEntityKey(String entityKey) { this.entityKey = entityKey; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getSatisfied() { return satisfied; }
    public void setSatisfied(Integer satisfied) { this.satisfied = satisfied; }

    public Integer getExpired() { return expired; }
    public void setExpired(Integer expired) { this.expired = expired; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}

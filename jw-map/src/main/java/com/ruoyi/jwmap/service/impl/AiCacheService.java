package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.mapper.AiAnalysisMapper;
import com.ruoyi.jwmap.domain.AiAnalysisRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 分析结果持久化缓存服务
 * 管理 jw_ai_analysis 表的 CRUD 和过期逻辑
 */
@Service
public class AiCacheService {

    @Autowired
    private AiAnalysisMapper aiAnalysisMapper;

    /**
     * 查询指定类型+实体键的分析记录
     * @param analysisType 分析类型（branch/grid/quadrant）
     * @param entityKey 实体标识
     * @return 未过期的记录，如过期或无记录返回 null
     */
    public AiAnalysisRecord getValidRecord(String analysisType, String entityKey) {
        AiAnalysisRecord record = aiAnalysisMapper.selectByTypeAndKey(analysisType, entityKey);
        if (record == null) return null;
        if (record.getExpired() != null && record.getExpired() == 1) return null;
        return record;
    }

    /**
     * 保存 AI 分析结果（INSERT/UPDATE）
     * GaussDB 不支持 ON CONFLICT/MERGE INTO，改为先查后增改
     */
    public void saveRecord(String analysisType, String entityKey, String city, String content) {
        AiAnalysisRecord existing = aiAnalysisMapper.selectByTypeAndKey(analysisType, entityKey);
        if (existing != null) {
            existing.setContent(content);
            existing.setCity(city);
            existing.setSatisfied(null);
            existing.setExpired(0);
            aiAnalysisMapper.updateByTypeAndKey(existing);
        } else {
            AiAnalysisRecord record = new AiAnalysisRecord();
            record.setAnalysisType(analysisType);
            record.setEntityKey(entityKey);
            record.setCity(city);
            record.setContent(content);
            record.setExpired(0);
            aiAnalysisMapper.insertRecord(record);
        }
    }

    /**
     * 标记该城市所有分析记录为过期
     * 在 Grid/Branch 计算完成后调用
     */
    public void invalidateByCity(String city) {
        if (city != null && !city.isEmpty()) {
            aiAnalysisMapper.expireByCity(city);
        }
    }

    /**
     * 更新用户满意度
     */
    public void updateSatisfied(String analysisType, String entityKey, Integer satisfied) {
        aiAnalysisMapper.updateSatisfied(analysisType, entityKey, satisfied);
    }
}

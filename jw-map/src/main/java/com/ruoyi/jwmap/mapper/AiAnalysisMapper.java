package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.AiAnalysisRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI分析结果持久化 Mapper
 */
public interface AiAnalysisMapper {

    /**
     * 根据类型+实体键查询记录
     */
    AiAnalysisRecord selectByTypeAndKey(
            @Param("analysisType") String analysisType,
            @Param("entityKey") String entityKey
    );

    /**
     * 根据城市查询所有记录（用于过期批量更新）
     */
    List<AiAnalysisRecord> selectByCity(@Param("city") String city);

    /**
     * 插入新记录（ON DUPLICATE KEY UPDATE）
     * GaussDB 请使用 insertRecord + updateByTypeAndKey 组合
     */
    int upsert(AiAnalysisRecord record);

    /**
     * GaussDB 专用：插入新记录（不含 upsert 语法）
     */
    int insertRecord(AiAnalysisRecord record);

    /**
     * GaussDB 专用：按 UK 更新（不含 upsert 语法）
     */
    int updateByTypeAndKey(AiAnalysisRecord record);

    /**
     * 标记某城市的分析记录为过期
     */
    int expireByCity(@Param("city") String city);

    /**
     * 更新满意度评价
     */
    int updateSatisfied(
            @Param("analysisType") String analysisType,
            @Param("entityKey") String entityKey,
            @Param("satisfied") Integer satisfied
    );
}

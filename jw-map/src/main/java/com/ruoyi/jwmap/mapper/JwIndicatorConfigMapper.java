package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwIndicatorConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 指标配置Mapper接口
 */
@Mapper
public interface JwIndicatorConfigMapper {

    List<JwIndicatorConfig> selectJwIndicatorConfigList(JwIndicatorConfig config);

    JwIndicatorConfig selectJwIndicatorConfigById(Long indicatorId);

    List<JwIndicatorConfig> selectActiveWeighted();

    List<JwIndicatorConfig> selectBySourceTable(@Param("sourceTable") String sourceTable);

    JwIndicatorConfig selectByCode(@Param("indicatorCode") String code);

    JwIndicatorConfig selectByIndicatorName(@Param("indicatorName") String name);

    int insertJwIndicatorConfig(JwIndicatorConfig config);

    int insertIndicatorConfig(JwIndicatorConfig config);

    int updateJwIndicatorConfig(JwIndicatorConfig config);

    int deleteJwIndicatorConfigById(Long id);

    int deleteJwIndicatorConfigByIds(Long[] ids);

    int batchInsert(List<JwIndicatorConfig> list);

    int upsertJwIndicatorConfig(JwIndicatorConfig config);
}

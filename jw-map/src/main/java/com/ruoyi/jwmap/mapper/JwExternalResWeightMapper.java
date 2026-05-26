package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwWeightConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 外部资源权重Mapper接口（表：jw_external_resource_weight）
 */
@Mapper
public interface JwExternalResWeightMapper {

    List<JwWeightConfig> selectAll();

    JwWeightConfig selectByIndicatorCode(@Param("indicatorCode") String code);

    int insertJwWeightConfig(JwWeightConfig config);

    int insertWeightConfig(JwWeightConfig config);

    int updateJwWeightConfig(JwWeightConfig config);

    int deleteJwWeightConfigById(Long id);

    int deleteAll();

    int batchInsert(List<JwWeightConfig> list);
}

package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwGridDataNormalized;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网格归一化指标数据Mapper接口
 */
@Mapper
public interface JwGridDataNormalizedMapper {

    List<JwGridDataNormalized> selectJwGridDataNormalizedList(JwGridDataNormalized data);

    JwGridDataNormalized selectJwGridDataNormalizedById(Long id);

    List<JwGridDataNormalized> selectByGridCode(@Param("gridCode") String gridCode);

    JwGridDataNormalized selectByGridAndIndicator(@Param("gridCode") String gridCode,
                                                   @Param("indicatorCode") String indicatorCode);

    List<JwGridDataNormalized> selectByCity(@Param("city") String city);

    List<JwGridDataNormalized> selectAllByCity(@Param("city") String city);

    int insertJwGridDataNormalized(JwGridDataNormalized data);

    int updateJwGridDataNormalized(JwGridDataNormalized data);

    int deleteJwGridDataNormalizedById(Long id);

    int deleteJwGridDataNormalizedByIds(Long[] ids);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwGridDataNormalized> list);

    int upsertJwGridDataNormalized(JwGridDataNormalized data);

    int upsertGridData(JwGridDataNormalized data);

    int deleteByIndicatorCode(@Param("indicatorCode") String indicatorCode);
    int updateIndicatorCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}

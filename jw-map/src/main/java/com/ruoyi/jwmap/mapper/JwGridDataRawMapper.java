package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwGridDataRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网格原始指标数据Mapper接口
 */
@Mapper
public interface JwGridDataRawMapper {

    List<JwGridDataRaw> selectJwGridDataRawList(JwGridDataRaw data);

    JwGridDataRaw selectJwGridDataRawById(Long id);

    List<JwGridDataRaw> selectByGridCode(@Param("gridCode") String gridCode);

    JwGridDataRaw selectByGridAndIndicator(@Param("gridCode") String gridCode,
                                            @Param("indicatorCode") String indicatorCode);

    List<JwGridDataRaw> selectByCity(@Param("city") String city);

    List<JwGridDataRaw> selectAllByCity(@Param("city") String city);

    int insertJwGridDataRaw(JwGridDataRaw data);

    int updateJwGridDataRaw(JwGridDataRaw data);

    int deleteJwGridDataRawById(Long id);

    int deleteJwGridDataRawByIds(Long[] ids);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwGridDataRaw> list);

    int upsertJwGridDataRaw(JwGridDataRaw data);

    int upsertGridData(JwGridDataRaw data);
}

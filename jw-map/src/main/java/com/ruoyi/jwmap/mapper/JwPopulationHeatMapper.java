package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwPopulationHeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 人口热力Mapper接口
 */
@Mapper
public interface JwPopulationHeatMapper {

    List<JwPopulationHeat> selectJwPopulationHeatList(JwPopulationHeat heat);

    JwPopulationHeat selectJwPopulationHeatById(Long heatId);

    List<JwPopulationHeat> selectByGridCode(@Param("gridCode") String gridCode);

    JwPopulationHeat selectByGridAndIndicator(@Param("gridCode") String gridCode,
                                              @Param("indicatorCode") String indicatorCode);

    List<String> selectDistinctGridCodes();

    List<String> selectDistinctGridCodesByCity(@Param("city") String city);

    List<String> selectDistinctCities();

    int insertJwPopulationHeat(JwPopulationHeat heat);

    int updateJwPopulationHeat(JwPopulationHeat heat);

    int deleteJwPopulationHeatById(Long heatId);

    int deleteJwPopulationHeatByIds(Long[] heatIds);

    int deleteByGridCode(@Param("gridCode") String gridCode);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwPopulationHeat> list);

    int upsertJwPopulationHeat(JwPopulationHeat heat);

    int upsertPopulationHeat(JwPopulationHeat heat);

    int deleteByIndicatorCode(@Param("indicatorCode") String indicatorCode);
    int updateIndicatorCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}

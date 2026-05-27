package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwPoiInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * POI信息Mapper接口
 */
@Mapper
public interface JwPoiInfoMapper {

    List<JwPoiInfo> selectPoiInfoList(JwPoiInfo poi);

    JwPoiInfo selectJwPoiInfoById(Long poiId);

    List<JwPoiInfo> selectByCity(@Param("city") String city);

    List<String> selectDistinctCities();

    int insertJwPoiInfo(JwPoiInfo poi);

    int upsertPoiInfo(JwPoiInfo poi);

    int updateJwPoiInfo(JwPoiInfo poi);

    int deleteJwPoiInfoById(Long poiId);

    int deleteJwPoiInfoByIds(Long[] poiIds);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwPoiInfo> list);
}

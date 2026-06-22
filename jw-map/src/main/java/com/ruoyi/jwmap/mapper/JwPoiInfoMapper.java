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

    /**
     * 查询指定矩形边界内的 POI（供范围统计使用）
     */
    List<JwPoiInfo> selectWithinBounds(@Param("city") String city,
                                        @Param("westLng") Double westLng,
                                        @Param("eastLng") Double eastLng,
                                        @Param("southLat") Double southLat,
                                        @Param("northLat") Double northLat);

    /**
     * 查询某个城市下所有非空的 poiType 列表（去重）
     */
    List<String> selectDistinctPoiTypes(@Param("city") String city);
}

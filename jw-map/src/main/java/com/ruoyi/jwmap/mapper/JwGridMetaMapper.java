package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwGridMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网格元信息Mapper接口
 */
@Mapper
public interface JwGridMetaMapper {

    List<JwGridMeta> selectJwGridMetaList(JwGridMeta meta);

    JwGridMeta selectJwGridMetaById(String gridCode);

    JwGridMeta selectByGridCode(@Param("gridCode") String gridCode);

    List<JwGridMeta> selectByCity(@Param("city") String city);

    List<String> selectDistinctCities();

    int insertJwGridMeta(JwGridMeta meta);

    int updateJwGridMeta(JwGridMeta meta);

    int deleteJwGridMetaById(String gridCode);

    int deleteJwGridMetaByIds(String[] gridCodes);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwGridMeta> list);

    int upsertJwGridMeta(JwGridMeta meta);

    int upsertGridMeta(JwGridMeta meta);

    JwGridMeta selectByPoint(@Param("longitude") Double longitude, @Param("latitude") Double latitude);
}

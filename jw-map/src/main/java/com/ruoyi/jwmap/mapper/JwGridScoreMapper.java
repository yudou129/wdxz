package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwGridScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网格得分Mapper接口
 */
@Mapper
public interface JwGridScoreMapper {

    List<JwGridScore> selectJwGridScoreList(JwGridScore s);

    JwGridScore selectJwGridScoreByGridCode(String gridCode);

    JwGridScore selectByGridCode(@Param("gridCode") String gridCode);

    List<JwGridScore> selectScoresByGridCode(@Param("gridCode") String gridCode);

    List<JwGridScore> selectScoresByGridCodes(@Param("gridCodes") List<String> gridCodes);

    List<JwGridScore> selectScoresByGridCodesAndCategory(@Param("gridCodes") List<String> gridCodes,
                                                          @Param("category") String category);

    List<JwGridScore> selectByCity(@Param("city") String city);

    List<JwGridScore> selectByCityAndDistrict(@Param("city") String city,
                                               @Param("district") String district);

    /** 网格排名用—带 district/lng/lat */
    List<java.util.Map<String, Object>> selectRankingWithMeta(@Param("city") String city);

    List<java.util.Map<String, Object>> selectRankingWithMetaByDistrict(@Param("city") String city,
                                                                         @Param("district") String district);

    int countByCity(@Param("city") String city);

    int insertJwGridScore(JwGridScore s);

    int updateJwGridScore(JwGridScore s);

    int deleteJwGridScoreByGridCode(String gridCode);

    int deleteJwGridScoreByGridCodes(String[] gridCodes);

    List<String> selectTopCodesWithoutBranch(@Param("city") String city,
                                              @Param("district") String district,
                                              @Param("limit") int limit);

    List<String> selectBetterBlankCodes(@Param("city") String city,
                                         @Param("minScore") double minScore);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwGridScore> list);

    int upsertGridScore(JwGridScore s);
}

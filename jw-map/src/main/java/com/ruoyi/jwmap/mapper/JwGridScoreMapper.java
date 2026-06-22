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

    List<JwGridScore> selectByCity(@Param("city") String city);

    int countByCity(@Param("city") String city);

    int insertJwGridScore(JwGridScore s);

    int updateJwGridScore(JwGridScore s);

    int deleteJwGridScoreByGridCode(String gridCode);

    int deleteJwGridScoreByGridCodes(String[] gridCodes);

    List<String> selectTopCodesWithoutBranch(@Param("city") String city, @Param("limit") int limit);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwGridScore> list);

    int upsertGridScore(JwGridScore s);
}

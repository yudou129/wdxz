package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwBranchScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网点得分Mapper接口
 */
@Mapper
public interface JwBranchScoreMapper {

    List<JwBranchScore> selectJwBranchScoreList(JwBranchScore s);

    JwBranchScore selectJwBranchScoreById(Long id);

    List<JwBranchScore> selectByBranchAndYear(@Param("branchId") Long branchId,
                                               @Param("dataYear") Integer dataYear);

    List<JwBranchScore> selectByCityAndYear(@Param("city") String city,
                                             @Param("dataYear") Integer dataYear);

    List<JwBranchScore> selectByCityAndYearAndCategory(@Param("city") String city,
                                                        @Param("dataYear") Integer dataYear,
                                                        @Param("category") String category);

    int insertJwBranchScore(JwBranchScore s);

    int updateJwBranchScore(JwBranchScore s);

    int updateRank(JwBranchScore s);

    int deleteJwBranchScoreById(Long id);

    int deleteJwBranchScoreByIds(Long[] ids);

    int deleteByCityAndYear(@Param("city") String city, @Param("dataYear") Integer dataYear);

    int batchInsert(List<JwBranchScore> list);

    int upsertJwBranchScore(JwBranchScore s);

    int upsertBranchScore(JwBranchScore s);
}

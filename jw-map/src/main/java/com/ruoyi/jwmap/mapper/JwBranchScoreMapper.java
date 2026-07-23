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

    List<JwBranchScore> selectByBranchIdsAndYear(@Param("branchIds") List<Long> branchIds,
                                                  @Param("dataYear") Integer dataYear);

    List<JwBranchScore> selectByCityAndYear(@Param("city") String city,
                                             @Param("dataYear") Integer dataYear);

    List<JwBranchScore> selectByCityAndYearRange(@Param("city") String city,
                                                  @Param("startYear") Integer startYear,
                                                  @Param("endYear") Integer endYear);

    List<JwBranchScore> selectByCityAndYearAndCategory(@Param("city") String city,
                                                        @Param("dataYear") Integer dataYear,
                                                        @Param("category") String category);

    List<JwBranchScore> selectByCityAndYearAndCategoryAndBranch(@Param("city") String city,
                                                                 @Param("dataYear") Integer dataYear,
                                                                 @Param("category") String category,
                                                                 @Param("primaryBranch") String primaryBranch);

    int insertJwBranchScore(JwBranchScore s);

    int updateJwBranchScore(JwBranchScore s);

    int updateRank(JwBranchScore s);

    int deleteJwBranchScoreById(Long id);

    int deleteJwBranchScoreByIds(Long[] ids);

    int deleteByCityAndYear(@Param("city") String city, @Param("dataYear") Integer dataYear);

    int batchInsert(List<JwBranchScore> list);

    /**
     * 批量更新排名（GaussDB兼容：使用VALUES语法批量UPDATE）
     */
    int batchUpdateRank(@Param("list") List<JwBranchScore> list);

    /**
     * 批量更新四象限字段（quadrant, median_site_rank, median_branch_rank）
     */
    int batchUpdateQuadrant(@Param("list") List<JwBranchScore> list);
}

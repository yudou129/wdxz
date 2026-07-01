package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwBranchIndicator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网点业务指标Mapper接口
 */
@Mapper
public interface JwBranchIndicatorMapper {

    List<JwBranchIndicator> selectJwBranchIndicatorList(JwBranchIndicator ind);

    JwBranchIndicator selectJwBranchIndicatorById(Long id);

    List<JwBranchIndicator> selectByBranchAndYear(@Param("branchId") Long branchId,
                                                   @Param("dataYear") Integer dataYear,
                                                   @Param("sheetType") String sheetType);

    JwBranchIndicator selectByBranchYearSheetAndIndicator(@Param("branchId") Long branchId,
                                                           @Param("dataYear") Integer dataYear,
                                                           @Param("sheetType") String sheetType,
                                                           @Param("indicatorCode") String indicatorCode);

    List<JwBranchIndicator> selectByCityAndYear(@Param("city") String city,
                                                 @Param("dataYear") Integer dataYear,
                                                 @Param("sheetType") String sheetType);

    List<JwBranchIndicator> selectByCityAndYearRange(@Param("city") String city,
                                                      @Param("startYear") Integer startYear,
                                                      @Param("endYear") Integer endYear,
                                                      @Param("sheetType") String sheetType);

    List<JwBranchIndicator> selectByCityAndSheetType(@Param("city") String city,
                                                      @Param("sheetType") String sheetType);

    List<JwBranchIndicator> selectByCityYearAndSheetType(@Param("city") String city,
                                                          @Param("dataYear") Integer dataYear,
                                                          @Param("sheetType") String sheetType);

    int insertJwBranchIndicator(JwBranchIndicator ind);

    int updateJwBranchIndicator(JwBranchIndicator ind);

    int deleteJwBranchIndicatorById(Long id);

    int deleteJwBranchIndicatorByIds(Long[] ids);

    int deleteByBranchAndYear(@Param("branchId") Long branchId,
                              @Param("dataYear") Integer dataYear,
                              @Param("sheetType") String sheetType);

    int deleteByBranchAndSheetType(@Param("branchId") Long branchId,
                                   @Param("sheetType") String sheetType);

    int batchInsert(List<JwBranchIndicator> list);

    int upsertJwBranchIndicator(JwBranchIndicator ind);

    int upsertBranchIndicator(JwBranchIndicator ind);

    int deleteByIndicatorCode(@Param("indicatorCode") String indicatorCode);
    int updateIndicatorCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}

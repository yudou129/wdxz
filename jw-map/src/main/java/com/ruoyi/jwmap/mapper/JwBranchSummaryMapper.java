package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwBranchSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网点指标汇总Mapper接口
 */
@Mapper
public interface JwBranchSummaryMapper {

    List<JwBranchSummary> selectJwBranchSummaryList(JwBranchSummary s);

    JwBranchSummary selectJwBranchSummaryById(Long id);

    List<JwBranchSummary> selectByCityAndYear(@Param("city") String city,
                                               @Param("dataYear") Integer dataYear);

    int insertJwBranchSummary(JwBranchSummary s);

    int insertBranchSummary(JwBranchSummary s);

    int updateJwBranchSummary(JwBranchSummary s);

    int updateBranchSummary(JwBranchSummary s);

    int deleteJwBranchSummaryById(Long id);

    int deleteJwBranchSummaryByIds(Long[] ids);

    int deleteByCityAndYear(@Param("city") String city, @Param("dataYear") Integer dataYear);

    int batchInsert(List<JwBranchSummary> list);

    int upsertJwBranchSummary(JwBranchSummary s);
}

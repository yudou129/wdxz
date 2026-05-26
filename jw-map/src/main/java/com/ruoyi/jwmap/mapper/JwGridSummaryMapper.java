package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwGridSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网格指标汇总Mapper接口
 */
@Mapper
public interface JwGridSummaryMapper {

    List<JwGridSummary> selectJwGridSummaryList(JwGridSummary s);

    JwGridSummary selectJwGridSummaryById(Long id);

    List<JwGridSummary> selectByCity(@Param("city") String city);

    int insertJwGridSummary(JwGridSummary s);

    int insertGridSummary(JwGridSummary s);

    int updateJwGridSummary(JwGridSummary s);

    int updateGridSummary(JwGridSummary s);

    int deleteJwGridSummaryById(Long id);

    int deleteJwGridSummaryByIds(Long[] ids);

    int deleteByCity(@Param("city") String city);

    int batchInsert(List<JwGridSummary> list);

    int upsertJwGridSummary(JwGridSummary s);
}

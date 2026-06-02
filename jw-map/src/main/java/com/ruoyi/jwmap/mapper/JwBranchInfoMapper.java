package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwBranchInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网点基本信息Mapper接口
 */
@Mapper
public interface JwBranchInfoMapper {

    List<JwBranchInfo> selectJwBranchInfoList(JwBranchInfo branch);

    JwBranchInfo selectJwBranchInfoById(Long branchId);

    JwBranchInfo selectByBranchCode(@Param("branchCode") String branchCode);

    List<JwBranchInfo> selectByCity(@Param("city") String city);

    List<String> selectDistinctCities();

    int insertBranchInfo(JwBranchInfo branch);

    int updateBranchInfo(JwBranchInfo branch);

    int updateGridCode(@Param("branchId") Long branchId, @Param("gridCode") String gridCode);

    int deleteJwBranchInfoById(Long branchId);

    int deleteJwBranchInfoByIds(Long[] branchIds);

    int batchInsert(List<JwBranchInfo> list);

    int upsertJwBranchInfo(JwBranchInfo branch);

    List<JwBranchInfo> selectByGridCode(@Param("gridCode") String gridCode);

    /** 四象限分析：JOIN grid_score + branch_score 获取选址得分和网点得分 */
    List<java.util.Map<String, Object>> selectQuadrantData(@Param("city") String city,
                                                            @Param("year") Integer year);
}

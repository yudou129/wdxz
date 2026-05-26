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

    int insertJwBranchInfo(JwBranchInfo branch);

    int insertBranchInfo(JwBranchInfo branch);

    int updateJwBranchInfo(JwBranchInfo branch);

    int updateBranchInfo(JwBranchInfo branch);

    int updateGridCode(@Param("branchId") Long branchId, @Param("gridCode") String gridCode);

    int deleteJwBranchInfoById(Long branchId);

    int deleteJwBranchInfoByIds(Long[] branchIds);

    int batchInsert(List<JwBranchInfo> list);

    int upsertJwBranchInfo(JwBranchInfo branch);
}

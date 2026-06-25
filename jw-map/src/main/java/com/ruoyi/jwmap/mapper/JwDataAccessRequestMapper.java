package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwDataAccessRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 数据查看申请 Mapper 接口
 */
@Mapper
public interface JwDataAccessRequestMapper {

    /** 我的申请列表 */
    List<JwDataAccessRequest> selectMyList(@Param("applicantId") Long applicantId);

    /** 待审批列表（按指定审核人ID） */
    List<JwDataAccessRequest> selectPendingListByReviewerId(@Param("reviewerId") Long reviewerId);

    /** 已审批列表 */
    List<JwDataAccessRequest> selectReviewedList(@Param("reviewerId") Long reviewerId);

    /** 按ID查询 */
    JwDataAccessRequest selectJwDataAccessRequestById(@Param("requestId") Long requestId);

    /** 检查有效审批数 */
    int countValid(@Param("applicantId") Long applicantId,
                   @Param("targetDeptId") Long targetDeptId,
                   @Param("now") Date now);

    /** 待审批数（按审核人ID） */
    int countPendingByReviewerId(@Param("reviewerId") Long reviewerId);

    /** 插入 */
    int insertJwDataAccessRequest(JwDataAccessRequest req);

    /** 更新状态 */
    int updateStatus(JwDataAccessRequest req);

    /** 软删除 */
    int deleteJwDataAccessRequestById(@Param("requestId") Long requestId);

    /** 批量过期 */
    int batchExpire(@Param("today") Date today);

    /** 获取审核人列表（指定角色 + 可选上级部门） */
    List<Map<String, Object>> selectReviewers(@Param("roleKey") String roleKey,
                                              @Param("parentDeptId") Long parentDeptId);
}

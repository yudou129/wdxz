package com.ruoyi.jwmap.service;

import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwDataAccessRequest;

import java.util.List;
import java.util.Map;

/**
 * 数据查看权限审批服务接口
 */
public interface IJwDataAccessService {

    // ===== 申请提交/管理 =====

    /** 提交申请 */
    JwDataAccessRequest submitRequest(Long applicantId, Long targetDeptId, String reason, Integer validDays, Long reviewerId);

    /** 撤销申请 */
    int cancelRequest(Long requestId, Long userId);

    /** 我的申请列表 */
    List<JwDataAccessRequest> selectMyList(Long applicantId);

    /** 按ID查询 */
    JwDataAccessRequest selectById(Long requestId);

    // ===== 审批 =====

    /** 待审批列表 */
    List<JwDataAccessRequest> selectPendingList(Long reviewerUserId);

    /** 已审批列表 */
    List<JwDataAccessRequest> selectReviewedList(Long reviewerUserId);

    /** 待审批总数 */
    int countPending(Long reviewerUserId);

    /** 通过 */
    int approveRequest(Long requestId, Long reviewerId, String comment);

    /** 拒绝 */
    int rejectRequest(Long requestId, Long reviewerId, String comment);

    // ===== 权限校验 =====

    /** 判断用户是否有权查看某网点详细数据 */
    boolean hasBranchAccess(Long userId, JwBranchInfo branch);

    /** 判断用户是否有权查看某支行下的网点数据 */
    boolean hasBranchAccessByDeptId(Long userId, Long branchDeptId);

    /** 获取用户有权限查看的支行 dept_id 列表（含自然权限 + 审批授权） */
    List<Long> selectAuthorizedDeptIds(Long userId);

    // ===== 审核员工具 =====

    /** 判断用户是否为数据审核员 */
    boolean isReviewer(Long userId);

    /** 获取审核员管辖的部门ID列表（通过 role_dept 关联的市行及以下） */
    List<Long> getReviewerDeptIds(Long userId);

    /** 批量过期已通过的申请（由定时任务调用） */
    int batchExpire();

    /** 根据网点ID查找对应支行部门（用于申请页自动回显） */
    Map<String, Object> resolveBranchDept(Long branchId);

    /** 获取审核人列表（根据目标部门的上级机构筛选） */
    List<Map<String, Object>> listReviewers(Long targetDeptId);
}

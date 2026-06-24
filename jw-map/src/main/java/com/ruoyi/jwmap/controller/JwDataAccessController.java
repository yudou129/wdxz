package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwDataAccessRequest;
import com.ruoyi.jwmap.mapper.JwBranchInfoMapper;
import com.ruoyi.jwmap.service.IJwDataAccessService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ServletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 数据查看权限审批控制器
 */
@RestController
@RequestMapping("/jwmap/access")
public class JwDataAccessController extends BaseController {

    @Autowired
    private IJwDataAccessService accessService;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    // ===== 申请提交 =====

    /**
     * 提交数据查看申请
     */
    @PostMapping("/request/submit")
    public AjaxResult submit(@RequestBody JwDataAccessRequest req) {
        Long userId = safeGetUserId();
        if (userId == null) {
            return error("登录状态已过期，请重新登录");
        }
        try {
            JwDataAccessRequest result = accessService.submitRequest(
                userId, req.getTargetDeptId(), req.getReason(), req.getValidDays(), req.getReviewerId());
            return success(result);
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 安全获取当前用户ID，认证失败时返回null
     */
    private Long safeGetUserId() {
        try {
            Long uid = getUserId();
            if (uid == null) {
                String uri = "";
                try { uri = ServletUtils.getRequest().getRequestURI(); } catch (Exception ignored) {}
                Object auth = SecurityUtils.getAuthentication();
                logger.warn("safeGetUserId: getUserId() returned NULL, authClass={}, uri={}",
                    auth != null ? auth.getClass().getSimpleName() : "null", uri);
            }
            return uid;
        } catch (Exception e) {
            String uri = "";
            try { uri = ServletUtils.getRequest().getRequestURI(); } catch (Exception ignored) {}
            logger.warn("safeGetUserId: getUserId() THREW {}: {}, uri={}",
                e.getClass().getSimpleName(), e.getMessage(), uri);
            return null;
        }
    }

    // ===== 我的申请 =====

    /**
     * 我的申请列表
     */
    @GetMapping("/request/myList")
    public TableDataInfo myList() {
        Long userId = safeGetUserId();
        if (userId == null) {
            return getDataTable(new java.util.ArrayList<>());
        }
        startPage();
        return getDataTable(accessService.selectMyList(userId));
    }

    /**
     * 撤销申请
     */
    @PostMapping("/request/cancel/{requestId}")
    public AjaxResult cancel(@PathVariable Long requestId) {
        Long userId = safeGetUserId();
        if (userId == null) {
            return error("登录状态已过期，请重新登录");
        }
        try {
            return toAjax(accessService.cancelRequest(requestId, userId));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 申请详情
     */
    @GetMapping("/request/{requestId}")
    public AjaxResult detail(@PathVariable Long requestId) {
        return success(accessService.selectById(requestId));
    }

    // ===== 审批 =====

    /**
     * 待审批列表
     */
    @GetMapping("/request/pendingList")
    public TableDataInfo pendingList() {
        Long userId = safeGetUserId();
        if (userId == null) {
            return getDataTable(new java.util.ArrayList<>());
        }
        startPage();
        return getDataTable(accessService.selectPendingList(userId));
    }

    /**
     * 已审批列表
     */
    @GetMapping("/request/reviewedList")
    public TableDataInfo reviewedList() {
        Long userId = safeGetUserId();
        if (userId == null) {
            return getDataTable(new java.util.ArrayList<>());
        }
        startPage();
        return getDataTable(accessService.selectReviewedList(userId));
    }

    /**
     * 待审批数量
     */
    @GetMapping("/request/pendingCount")
    public AjaxResult pendingCount() {
        Long userId = safeGetUserId();
        if (userId == null) {
            return success(0);
        }
        return success(accessService.countPending(userId));
    }

    /**
     * 审批通过
     */
    @PostMapping("/request/approve")
    public AjaxResult approve(@RequestBody JwDataAccessRequest req) {
        Long userId = safeGetUserId();
        if (userId == null) {
            return error("登录状态已过期，请重新登录");
        }
        try {
            return toAjax(accessService.approveRequest(req.getRequestId(), userId, req.getReviewComment()));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 审批拒绝
     */
    @PostMapping("/request/reject")
    public AjaxResult reject(@RequestBody JwDataAccessRequest req) {
        Long userId = safeGetUserId();
        if (userId == null) {
            return error("登录状态已过期，请重新登录");
        }
        try {
            return toAjax(accessService.rejectRequest(req.getRequestId(), userId, req.getReviewComment()));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    // ===== 权限校验 =====

    /**
     * 检查当前用户是否有权查看某网点详细数据
     */
    @GetMapping("/checkBranch/{branchId}")
    public AjaxResult checkBranchAccess(@PathVariable Long branchId) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) {
            return success(new java.util.HashMap<String, Object>() {{
                put("hasAccess", false);
                put("deptName", "");
            }});
        }
        boolean hasAccess = false;
        try {
            hasAccess = accessService.hasBranchAccess(getUserId(), branch);
        } catch (Exception e) {
            // getUserId() 可能因会话过期失败，此时默认无权限
        }
        final boolean access = hasAccess;
        return success(new java.util.HashMap<String, Object>() {{
            put("hasAccess", access);
            put("deptName", branch.getPrimaryBranch());
        }});
    }

    /**
     * 获取当前用户有权限查看的机构列表（含自然权限+审批授权）
     * 返回 dept_id 列表
     */
    @GetMapping("/myAuthorizedDepts")
    public AjaxResult myAuthorizedDepts() {
        Long userId = safeGetUserId();
        if (userId == null) {
            return success(new java.util.ArrayList<>());
        }
        return success(accessService.selectAuthorizedDeptIds(userId));
    }

    /**
     * 判断当前用户是否为数据审核员
     */
    @GetMapping("/isReviewer")
    public AjaxResult isReviewer() {
        Long userId = safeGetUserId();
        if (userId == null) {
            return success(false);
        }
        return success(accessService.isReviewer(userId));
    }

    // ===== 审核人列表 =====

    /**
     * 获取审核人列表（根据目标部门的上级机构筛选）
     */
    @GetMapping("/reviewers")
    public AjaxResult reviewers(@RequestParam(required = false) Long targetDeptId) {
        return success(accessService.listReviewers(targetDeptId));
    }

    // ===== 网点→部门解析（申请页自动回显） =====

    /**
     * 根据网点ID查找对应支行部门（用于申请页自动回显）
     */
    @GetMapping("/resolveBranchDept/{branchId}")
    public AjaxResult resolveBranchDept(@PathVariable Long branchId) {
        return success(accessService.resolveBranchDept(branchId));
    }
}

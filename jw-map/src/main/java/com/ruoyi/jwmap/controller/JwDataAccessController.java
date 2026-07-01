package com.ruoyi.jwmap.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwDataAccessRequest;
import com.ruoyi.jwmap.mapper.JwBranchInfoMapper;
import com.ruoyi.jwmap.mapper.JwDataAccessRequestMapper;
import com.ruoyi.jwmap.service.IJwDataAccessService;
import com.ruoyi.jwmap.service.impl.ExcelExportService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private JwDataAccessRequestMapper accessRequestMapper;

    @Autowired
    private ExcelExportService excelExportService;

    // ===== 申请提交 =====

    /**
     * 提交数据查看申请
     */
    @Log(title = "数据访问", businessType = BusinessType.INSERT)
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
    @Log(title = "数据访问", businessType = BusinessType.UPDATE)
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
     * 申请详情（仅申请人或审核人可查看）
     */
    @Log(title = "数据访问", businessType = BusinessType.OTHER)
    @GetMapping("/request/{requestId}")
    public AjaxResult detail(@PathVariable Long requestId) {
        Long userId = safeGetUserId();
        if (userId == null) {
            return error("登录状态已过期，请重新登录");
        }
        JwDataAccessRequest result = accessService.selectById(requestId, userId);
        if (result == null) {
            return error("申请不存在或无权限查看");
        }
        return success(result);
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
    @Log(title = "数据访问", businessType = BusinessType.UPDATE)
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
    @Log(title = "数据访问", businessType = BusinessType.UPDATE)
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
    @Log(title = "数据访问", businessType = BusinessType.OTHER)
    @GetMapping("/checkBranch/{branchId}")
    public AjaxResult checkBranchAccess(@PathVariable Long branchId) {
        Long userId = safeGetUserId();
        if (userId == null) {
            return error("登录状态已过期，请重新登录");
        }
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) {
            return success(new java.util.HashMap<String, Object>() {{
                put("hasAccess", false);
                put("deptName", "");
            }});
        }
        boolean hasAccess = accessService.hasBranchAccess(userId, branch);
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

    // ===== 部门树（申请页选择目标支行） =====

    /**
     * 获取全量部门树（不受 DataScope 限制，用于申请页选择目标支行）
     */
    // ===== 审批通过记录 — 网点数据导出 =====

    @GetMapping("/export/{requestId}")
    public void exportApproved(@PathVariable Long requestId, HttpServletResponse response) {
        try {
            JwDataAccessRequest req = accessRequestMapper.selectJwDataAccessRequestById(requestId);
            if (req == null) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"msg\":\"申请记录不存在\"}");
                return;
            }
            if (!"1".equals(req.getStatus())) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"msg\":\"仅审批通过的记录可导出\"}");
                return;
            }
            // 查部门名称
            SysDept dept = sysDeptMapper.selectDeptById(req.getTargetDeptId());
            String deptName = (dept != null) ? dept.getDeptName() : null;
            if (deptName == null) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"msg\":\"未找到对应部门\"}");
                return;
            }
            // 查该部门下的网点（同时匹配一级和二级支行名称）
            List<JwBranchInfo> branches = branchInfoMapper.selectByDeptName(deptName);
            if (branches.isEmpty()) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"msg\":\"该部门下无网点数据\"}");
                return;
            }
            // 取第一个网点的城市，年份取前一年(数据已含三年范围)
            String city = branches.get(0).getCity();
            int year = java.time.LocalDate.now().getYear() - 1;

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("网点数据_" + deptName + "_" + year + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            excelExportService.exportBranchCombined(city, year, response.getOutputStream());
        } catch (Exception e) {
            logger.error("审批导出失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"msg\":\"导出失败\"}");
            } catch (Exception ignored) {}
        }
    }

    @GetMapping("/deptTree")
    public AjaxResult deptTree() {
        SysDept query = new SysDept();
        List<SysDept> depts = sysDeptMapper.selectDeptList(query);
        List<SysDept> tree = buildDeptTree(depts);
        List<TreeSelect> result = tree.stream().map(TreeSelect::new).collect(Collectors.toList());
        return success(result);
    }

    private List<SysDept> buildDeptTree(List<SysDept> depts) {
        List<SysDept> returnList = new ArrayList<>();
        List<Long> tempList = depts.stream().map(SysDept::getDeptId).collect(Collectors.toList());
        for (SysDept dept : depts) {
            if (!tempList.contains(dept.getParentId())) {
                recursionFn(depts, dept);
                returnList.add(dept);
            }
        }
        if (returnList.isEmpty()) {
            returnList = depts;
        }
        return returnList;
    }

    private void recursionFn(List<SysDept> list, SysDept t) {
        List<SysDept> childList = getChildList(list, t);
        t.setChildren(childList);
        for (SysDept tChild : childList) {
            if (hasChild(list, tChild)) {
                recursionFn(list, tChild);
            }
        }
    }

    private List<SysDept> getChildList(List<SysDept> list, SysDept t) {
        List<SysDept> tlist = new ArrayList<>();
        for (SysDept dept : list) {
            if (dept.getParentId() != null && dept.getParentId().equals(t.getDeptId())) {
                tlist.add(dept);
            }
        }
        return tlist;
    }

    private boolean hasChild(List<SysDept> list, SysDept t) {
        return getChildList(list, t).size() > 0;
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

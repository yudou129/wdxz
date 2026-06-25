package com.ruoyi.jwmap.service.impl;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwDataAccessRequest;
import com.ruoyi.jwmap.mapper.JwDataAccessRequestMapper;
import com.ruoyi.jwmap.service.IJwDataAccessService;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.jwmap.constant.AccessStatus;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据查看权限审批服务实现
 */
@Service
public class JwDataAccessServiceImpl implements IJwDataAccessService {

    @Autowired
    private JwDataAccessRequestMapper accessRequestMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private com.ruoyi.jwmap.mapper.JwBranchInfoMapper branchInfoMapper;

    /** 数据审核员角色 key */
    private static final String REVIEWER_ROLE_KEY = "data_reviewer";

    @Override
    @Transactional
    public JwDataAccessRequest submitRequest(Long applicantId, Long targetDeptId, String reason, Integer validDays, Long reviewerId) {
        // 校验目标部门：不能是根节点，必须有上级部门
        SysDept targetDept = sysDeptMapper.selectDeptById(targetDeptId);
        if (targetDept == null) {
            throw new RuntimeException("目标机构不存在");
        }
        if (targetDept.getParentId() == null || targetDept.getParentId() == 0) {
            throw new RuntimeException("不能对省行级别发起申请，请选择市行或支行");
        }

        // 检查是否已有有效审批
        int exists = accessRequestMapper.countValid(applicantId, targetDeptId, new Date());
        if (exists > 0) {
            throw new RuntimeException("您已有该支行在有效期内的审批，无需重复申请");
        }

        // 检查是否有待审批的重复申请
        List<JwDataAccessRequest> myList = accessRequestMapper.selectMyList(applicantId);
        boolean hasPending = myList.stream()
            .anyMatch(r -> r.getTargetDeptId().equals(targetDeptId)
                && AccessStatus.PENDING.equals(r.getStatus()));
        if (hasPending) {
            throw new RuntimeException("该支行已有待审批的申请，请勿重复提交");
        }

        // 校验审核人是否具有 data_reviewer 角色
        if (reviewerId == null) {
            throw new RuntimeException("请选择审核人");
        }
        SysUser reviewer = sysUserMapper.selectUserById(reviewerId);
        if (reviewer == null || reviewer.getRoles() == null
                || reviewer.getRoles().stream().noneMatch(r -> REVIEWER_ROLE_KEY.equals(r.getRoleKey()))) {
            throw new RuntimeException("选择的审核人不具备审核权限");
        }

        // 创建申请
        JwDataAccessRequest req = new JwDataAccessRequest();
        req.setApplicantId(applicantId);
        req.setTargetDeptId(targetDeptId);
        req.setReviewerId(reviewerId);
        req.setReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "");
        req.setValidDays(validDays != null ? validDays : 30);
        req.setStatus(AccessStatus.PENDING);
        req.setCreateBy(getUsernameById(applicantId));
        accessRequestMapper.insertJwDataAccessRequest(req);

        // 查询完整信息返回
        return accessRequestMapper.selectJwDataAccessRequestById(req.getRequestId());
    }

    @Override
    @Transactional
    public int cancelRequest(Long requestId, Long userId) {
        JwDataAccessRequest req = accessRequestMapper.selectJwDataAccessRequestById(requestId);
        if (req == null || !java.util.Objects.equals(req.getApplicantId(), userId)) {
            throw new RuntimeException("只能撤销自己的申请");
        }
        if (!AccessStatus.PENDING.equals(req.getStatus())) {
            throw new RuntimeException("仅待审批状态的申请可以撤销");
        }
        JwDataAccessRequest update = new JwDataAccessRequest();
        update.setRequestId(requestId);
        update.setStatus(AccessStatus.CANCELLED);
        update.setUpdateBy(getUsernameById(userId));
        return accessRequestMapper.updateStatus(update);
    }

    @Override
    public List<JwDataAccessRequest> selectMyList(Long applicantId) {
        return accessRequestMapper.selectMyList(applicantId);
    }

    @Override
    public JwDataAccessRequest selectById(Long requestId) {
        return accessRequestMapper.selectJwDataAccessRequestById(requestId);
    }

    @Override
    public List<JwDataAccessRequest> selectPendingList(Long reviewerUserId) {
        return accessRequestMapper.selectPendingListByReviewerId(reviewerUserId);
    }

    @Override
    public List<JwDataAccessRequest> selectReviewedList(Long reviewerUserId) {
        return accessRequestMapper.selectReviewedList(reviewerUserId);
    }

    @Override
    public int countPending(Long reviewerUserId) {
        return accessRequestMapper.countPendingByReviewerId(reviewerUserId);
    }

    @Override
    @Transactional
    public int approveRequest(Long requestId, Long reviewerId, String comment) {
        JwDataAccessRequest req = accessRequestMapper.selectJwDataAccessRequestById(requestId);
        if (req == null || !AccessStatus.PENDING.equals(req.getStatus())) {
            throw new RuntimeException("该申请不是待审批状态");
        }
        // 校验当前用户是否为该申请指定的审核人
        if (req.getReviewerId() != null && !req.getReviewerId().equals(reviewerId)) {
            throw new RuntimeException("您不是该申请的指定审核人，无法审批");
        }
        // 校验审核人角色是否仍然有效
        SysUser reviewer = sysUserMapper.selectUserById(reviewerId);
        if (reviewer == null || reviewer.getRoles() == null
                || reviewer.getRoles().stream().noneMatch(r -> REVIEWER_ROLE_KEY.equals(r.getRoleKey()))) {
            throw new RuntimeException("当前用户不再具备审核权限");
        }
        // 计算有效期
        Calendar cal = Calendar.getInstance();
        Date from = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, req.getValidDays() != null ? req.getValidDays() : 30);
        Date to = cal.getTime();

        JwDataAccessRequest update = new JwDataAccessRequest();
        update.setRequestId(requestId);
        update.setStatus(AccessStatus.APPROVED);
        update.setReviewerId(reviewerId);
        update.setReviewComment(comment);
        update.setReviewTime(new Date());
        update.setValidDateFrom(from);
        update.setValidDateTo(to);
        update.setUpdateBy(getUsernameById(reviewerId));
        return accessRequestMapper.updateStatus(update);
    }

    @Override
    @Transactional
    public int rejectRequest(Long requestId, Long reviewerId, String comment) {
        JwDataAccessRequest req = accessRequestMapper.selectJwDataAccessRequestById(requestId);
        if (req == null || !AccessStatus.PENDING.equals(req.getStatus())) {
            throw new RuntimeException("该申请不是待审批状态");
        }
        // 校验当前用户是否为该申请指定的审核人
        if (req.getReviewerId() != null && !req.getReviewerId().equals(reviewerId)) {
            throw new RuntimeException("您不是该申请的指定审核人，无法审批");
        }
        // 校验审核人角色是否仍然有效
        SysUser reviewer = sysUserMapper.selectUserById(reviewerId);
        if (reviewer == null || reviewer.getRoles() == null
                || reviewer.getRoles().stream().noneMatch(r -> REVIEWER_ROLE_KEY.equals(r.getRoleKey()))) {
            throw new RuntimeException("当前用户不再具备审核权限");
        }
        JwDataAccessRequest update = new JwDataAccessRequest();
        update.setRequestId(requestId);
        update.setStatus(AccessStatus.REJECTED);
        update.setReviewerId(reviewerId);
        update.setReviewComment(comment);
        update.setReviewTime(new Date());
        update.setUpdateBy(getUsernameById(reviewerId));
        return accessRequestMapper.updateStatus(update);
    }

    @Override
    public boolean hasBranchAccess(Long userId, JwBranchInfo branch) {
        if (userId == null || branch == null) return false;

        // 1. 获取用户所在部门及所有子孙部门名称
        SysUser user = sysUserMapper.selectUserById(userId);
        if (user == null || user.getDeptId() == null) return false;

        Set<String> accessibleDeptNames = getDescendantDeptNames(user.getDeptId());

        // 2. 名称匹配 → 网点在本机构范围内
        if (branch.getPrimaryBranch() != null
                && accessibleDeptNames.contains(branch.getPrimaryBranch())) {
            return true;
        }
        if (branch.getSecondaryBranch() != null
                && accessibleDeptNames.contains(branch.getSecondaryBranch())) {
            return true;
        }

        // 3. 跨机构 → 查审批表（支持层级覆盖）
        //    先查 primary_branch 对应部门，再查 secondary_branch 对应部门（可能是更精确的二级支行部门）
        SysDept branchDept = findDeptByName(branch.getPrimaryBranch());
        SysDept secondaryDept = null;
        if (branch.getSecondaryBranch() != null
                && !branch.getSecondaryBranch().equals(branch.getPrimaryBranch())) {
            secondaryDept = findDeptByName(branch.getSecondaryBranch());
        }

        List<JwDataAccessRequest> approvedList = accessRequestMapper.selectMyList(userId);
        Date now = new Date();
        // 预加载所有审批目标部门的子孙缓存，避免重复查询
        Map<Long, Set<Long>> deptDescendantsCache = new HashMap<>();
        for (JwDataAccessRequest r : approvedList) {
            if (r.getTargetDeptId() != null && !deptDescendantsCache.containsKey(r.getTargetDeptId())) {
                Set<Long> descendants = new HashSet<>();
                List<SysDept> children = sysDeptMapper.selectChildrenDeptById(r.getTargetDeptId());
                if (children != null) children.forEach(d -> descendants.add(d.getDeptId()));
                deptDescendantsCache.put(r.getTargetDeptId(), descendants);
            }
        }
        for (JwDataAccessRequest r : approvedList) {
            if (!AccessStatus.APPROVED.equals(r.getStatus())) continue;
            if (r.getValidDateFrom() == null || r.getValidDateTo() == null) continue;
            if (r.getValidDateFrom().after(now) || r.getValidDateTo().before(now)) continue;
            Set<Long> cache = deptDescendantsCache.get(r.getTargetDeptId());
            // 检查 primary 部门层级覆盖
            if (branchDept != null && isDeptCoveredByApproved(r.getTargetDeptId(), branchDept.getDeptId(), cache)) {
                return true;
            }
            // 检查 secondary 部门（可能直接匹配审批的网点级部门）
            if (secondaryDept != null && (branchDept == null || !secondaryDept.getDeptId().equals(branchDept.getDeptId()))) {
                if (isDeptCoveredByApproved(r.getTargetDeptId(), secondaryDept.getDeptId(), cache)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasBranchAccessByDeptId(Long userId, Long branchDeptId) {
        if (userId == null || branchDeptId == null) return false;

        SysDept targetDept = sysDeptMapper.selectDeptById(branchDeptId);
        if (targetDept == null) return false;

        // 1. 名称匹配
        SysUser user = sysUserMapper.selectUserById(userId);
        if (user != null && user.getDeptId() != null) {
            Set<String> accessibleDeptNames = getDescendantDeptNames(user.getDeptId());
            if (accessibleDeptNames.contains(targetDept.getDeptName())) {
                return true;
            }
        }

        // 2. 查审批表（支持层级覆盖）
        List<JwDataAccessRequest> approvedList = accessRequestMapper.selectMyList(userId);
        Date now = new Date();
        for (JwDataAccessRequest r : approvedList) {
            if (!AccessStatus.APPROVED.equals(r.getStatus())) continue;
            if (r.getValidDateFrom() == null || r.getValidDateTo() == null) continue;
            if (r.getValidDateFrom().after(now) || r.getValidDateTo().before(now)) continue;
            if (isDeptCoveredByApproved(r.getTargetDeptId(), branchDeptId, null)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Long> selectAuthorizedDeptIds(Long userId) {
        Set<Long> result = new HashSet<>();
        SysUser user = sysUserMapper.selectUserById(userId);
        if (user != null && user.getDeptId() != null) {
            // 自然权限：用户部门及其所有子孙
            List<SysDept> descendants = sysDeptMapper.selectChildrenDeptById(user.getDeptId());
            descendants.forEach(d -> result.add(d.getDeptId()));
            result.add(user.getDeptId());
        }

        // 审批授权：查所有已通过的申请，并展开目标部门的所有子孙
        List<JwDataAccessRequest> myList = accessRequestMapper.selectMyList(userId);
        Date now = new Date();
        myList.stream()
            .filter(r -> AccessStatus.APPROVED.equals(r.getStatus()))
            .filter(r -> r.getValidDateFrom() != null && r.getValidDateTo() != null
                    && !r.getValidDateFrom().after(now) && !r.getValidDateTo().before(now))
            .forEach(r -> {
                Long targetId = r.getTargetDeptId();
                if (targetId != null) {
                    result.add(targetId);
                    // 展开子孙部门
                    List<SysDept> children = sysDeptMapper.selectChildrenDeptById(targetId);
                    if (children != null) {
                        children.forEach(c -> result.add(c.getDeptId()));
                    }
                }
            });

        return new ArrayList<>(result);
    }

    @Override
    public boolean isReviewer(Long userId) {
        SysUser user = sysUserMapper.selectUserById(userId);
        if (user == null || user.getRoles() == null) return false;
        return user.getRoles().stream()
            .anyMatch(r -> REVIEWER_ROLE_KEY.equals(r.getRoleKey()));
    }

    @Override
    public List<Long> getReviewerDeptIds(Long userId) {
        // 通过用户 data_reviewer 角色关联的 sys_role_dept 获取管辖部门
        SysUser user = sysUserMapper.selectUserById(userId);
        if (user == null || user.getRoles() == null) return Collections.emptyList();

        for (SysRole role : user.getRoles()) {
            if (REVIEWER_ROLE_KEY.equals(role.getRoleKey())) {
                List<Long> deptIds = sysDeptMapper.selectDeptListByRoleId(role.getRoleId(), false);
                if (deptIds == null) return Collections.emptyList();

                // 展开：管辖部门 + 其所有子孙部门（覆盖该市行下的所有支行）
                Set<Long> expanded = new HashSet<>(deptIds);
                for (Long deptId : deptIds) {
                    List<SysDept> children = sysDeptMapper.selectChildrenDeptById(deptId);
                    children.forEach(d -> expanded.add(d.getDeptId()));
                }
                return new ArrayList<>(expanded);
            }
        }
        return Collections.emptyList();
    }

    // ===== 私有工具方法 =====

    /** 获取用户所在部门的所有子孙部门名称集合 */
    private Set<String> getDescendantDeptNames(Long deptId) {
        Set<String> names = new HashSet<>();
        SysDept self = sysDeptMapper.selectDeptById(deptId);
        if (self != null) names.add(self.getDeptName());

        List<SysDept> descendants = sysDeptMapper.selectChildrenDeptById(deptId);
        if (descendants != null) {
            descendants.forEach(d -> names.add(d.getDeptName()));
        }
        return names;
    }

    /** 根据部门名称查找部门 */
    private SysDept findDeptByName(String deptName) {
        if (deptName == null || deptName.isEmpty()) return null;
        SysDept query = new SysDept();
        query.setDeptName(deptName);
        List<SysDept> list = sysDeptMapper.selectDeptList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 获取用户名 */
    private String getUsernameById(Long userId) {
        SysUser user = sysUserMapper.selectUserById(userId);
        return user != null ? user.getUserName() : String.valueOf(userId);
    }

    /**
     * 检查 approvedDeptId 是否为 branchDeptId 或其祖先
     * 审批了市行 → 覆盖该市行下所有支行/网点
     * 审批了支行 → 覆盖该支行下所有网点
     */
    private boolean isDeptCoveredByApproved(Long approvedDeptId, Long branchDeptId, Set<Long> deptDescendantsCache) {
        if (approvedDeptId == null || branchDeptId == null) return false;
        if (approvedDeptId.equals(branchDeptId)) return true;
        // 有缓存 → 直接在缓存中查找（已包含完整后代数据）
        if (deptDescendantsCache != null) {
            return deptDescendantsCache.contains(branchDeptId);
        }
        // 无缓存 → 回退查数据库
        List<SysDept> descendants = sysDeptMapper.selectChildrenDeptById(approvedDeptId);
        if (descendants != null) {
            for (SysDept d : descendants) {
                if (branchDeptId.equals(d.getDeptId())) return true;
            }
        }
        return false;
    }

    @Override
    public int batchExpire() {
        return accessRequestMapper.batchExpire(new Date());
    }

    @Override
    public Map<String, Object> resolveBranchDept(Long branchId) {
        Map<String, Object> result = new HashMap<>();
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) {
            result.put("deptId", null);
            result.put("deptName", "");
            return result;
        }
        // 优先匹配二级支行（网点级），否则退到一级支行
        String targetName = branch.getSecondaryBranch();
        SysDept dept = findDeptByName(targetName);
        if (dept == null && branch.getPrimaryBranch() != null
            && !branch.getPrimaryBranch().equals(targetName)) {
            dept = findDeptByName(branch.getPrimaryBranch());
        }
        result.put("deptId", dept != null ? dept.getDeptId() : null);
        result.put("deptName", dept != null ? dept.getDeptName() : targetName);
        return result;
    }

    @Override
    public List<Map<String, Object>> listReviewers(Long targetDeptId) {
        // 找到目标部门的上级机构（市行的上级是省行，支行的上级是市行）
        Long parentDeptId = null;
        if (targetDeptId != null) {
            SysDept targetDept = sysDeptMapper.selectDeptById(targetDeptId);
            if (targetDept != null && targetDept.getParentId() != null && targetDept.getParentId() != 0) {
                parentDeptId = targetDept.getParentId();
            }
        }
        return accessRequestMapper.selectReviewers("data_reviewer", parentDeptId);
    }
}

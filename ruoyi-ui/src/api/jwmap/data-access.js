import request from '@/utils/request'

// 提交申请
export function submitAccessRequest(data) {
  return request({ url: '/jwmap/access/request/submit', method: 'post', data })
}

// 我的申请列表
export function getMyRequestList(params) {
  return request({ url: '/jwmap/access/request/myList', method: 'get', params })
}

// 申请详情
export function getRequestDetail(requestId) {
  return request({ url: `/jwmap/access/request/${requestId}`, method: 'get' })
}

// 撤销申请
export function cancelRequest(requestId) {
  return request({ url: `/jwmap/access/request/cancel/${requestId}`, method: 'post' })
}

// 待审批列表
export function getPendingRequestList(params) {
  return request({ url: '/jwmap/access/request/pendingList', method: 'get', params })
}

// 已审批列表
export function getReviewedRequestList(params) {
  return request({ url: '/jwmap/access/request/reviewedList', method: 'get', params })
}

// 待审批数量
export function getPendingCount() {
  return request({ url: '/jwmap/access/request/pendingCount', method: 'get' })
}

// 审批通过
export function approveRequest(data) {
  return request({ url: '/jwmap/access/request/approve', method: 'post', data })
}

// 审批拒绝
export function rejectRequest(data) {
  return request({ url: '/jwmap/access/request/reject', method: 'post', data })
}

// 权限校验
export function checkBranchAccess(branchId) {
  return request({ url: `/jwmap/access/checkBranch/${branchId}`, method: 'get' })
}

// 有权限的机构列表
export function getMyAuthorizedDepts() {
  return request({ url: '/jwmap/access/myAuthorizedDepts', method: 'get' })
}

// 是否为审核员
export function checkIsReviewer() {
  return request({ url: '/jwmap/access/isReviewer', method: 'get' })
}

// 根据网点ID解析对应支行部门（用于申请页自动回显）
export function resolveBranchDept(branchId) {
  return request({ url: `/jwmap/access/resolveBranchDept/${branchId}`, method: 'get' })
}

// 获取审核人列表（可选targetDeptId筛选上级机构审核人）
export function getReviewers(targetDeptId) {
  return request({ url: '/jwmap/access/reviewers', method: 'get', params: { targetDeptId } })
}

// 获取全量部门树（不受DataScope限制，用于申请页选择目标支行）
export function getAccessDeptTree() {
  return request({ url: '/jwmap/access/deptTree', method: 'get' })
}

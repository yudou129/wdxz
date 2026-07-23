import request from '@/utils/request'

// ==================== SSE 流式请求封装 ====================

/**
 * 发起 SSE 流式请求，逐段回调
 * 参考业界最佳实践：async/await + ReadableStream + buffer 断帧保护
 *
 * @param {string} url - SSE URL（相对路径，如 /jwmap/ai/...）
 * @param {function} onChunk - 每收到一段文本的回调 (chunk: string) => void
 * @param {function} onDone - 流结束的回调
 * @param {function} onError - 错误回调 (message: string) => void
 */
export function fetchSSE(url, onChunk, onDone, onError, onReplace) {
  const baseURL = process.env.VUE_APP_BASE_API || '/dev-api'
  const fullUrl = baseURL + url

  fetch(fullUrl, {
    headers: { 'Authorization': 'Bearer ' + getToken() }
  }).then(async response => {
    if (!response.ok) {
      onError('SSE 连接失败：HTTP ' + response.status)
      return
    }
    if (!response.body) {
      onError('SSE 连接失败：响应无内容体')
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = '' // 跨 chunk 缓冲区

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        // 用 { stream: true } 防止多字节 UTF-8 字符被截断
        buffer += decoder.decode(value, { stream: true })

        // 按 \n\n 分割 SSE 事件（SSE 协议：每个事件以 \n\n 结尾）
        const events = buffer.split('\n\n')
        // 最后一段是不完整的事件，留在 buffer 等下一批数据
        buffer = events.pop() || ''

        for (const event of events) {
          // 遍历事件内的每一行
          const lines = event.split('\n')
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.substring(5).trim()
              if (!data || data === '[DONE]') continue
              try {
                const parsed = JSON.parse(data)
                if (parsed.chunk) {
                  onChunk(parsed.chunk)
                } else if (parsed.replaceAll && onReplace) {
                  onReplace(parsed.replaceAll)
                }
              } catch (e) {
                // 解析失败，跳过该帧（不中断整个流）
              }
            }
          }
        }
      }
    } catch (err) {
      reader.cancel().catch(() => {})
      onError('SSE 读取错误：' + err.message)
      return
    }

    onDone()
  }).catch(err => {
    onError('SSE 请求失败：' + err.message)
  })
}

// 从 localStorage 或 cookies 获取 token
function getToken() {
  try {
    const userInfo = JSON.parse(localStorage.getItem('user-info') || '{}')
    return userInfo.token || ''
  } catch (e) {
    return ''
  }
}

// ==================== 选址建议 ====================

/**
 * 选址建议 — SSE 流式
 */
export function getSiteSuggestionStream(gridCode) {
  return '/jwmap/ai/site-suggestion/stream/' + gridCode
}

// ==================== 网点分析 ====================

/**
 * 网点分析 — SSE 流式
 * @param {number} branchId
 * @param {number} year
 * @param {boolean} forceRefresh - 是否强制重新生成
 */
export function getBranchAnalysisStream(branchId, year, forceRefresh = false) {
  let url = '/jwmap/ai/branch-analysis/stream/' + branchId + '/' + year
  if (forceRefresh) url += '?forceRefresh=true'
  return url
}

/**
 * 查询网点分析存量
 */
export function getBranchAnalysisCached(branchId, year) {
  return request({
    url: '/jwmap/ai/branch-analysis/' + branchId + '/' + year,
    method: 'get'
  })
}

// ==================== 多网点对比 ====================

/**
 * 多网点对比 — SSE 流式
 * @param {number[]} branchIds - 网点ID数组
 * @param {string} city
 * @param {number} year
 */
export function getBranchComparisonStream(branchIds, city, year) {
  return '/jwmap/ai/branch-comparison/stream?branchIds=' + branchIds.join(',') + '&city=' + city + '&year=' + year
}

// ==================== 网格分析 ====================

/**
 * 网格分析 — SSE 流式
 */
export function getGridAnalysisStream(gridCode, forceRefresh = false) {
  let url = '/jwmap/ai/grid-analysis/stream/' + gridCode
  if (forceRefresh) url += '?forceRefresh=true'
  return url
}

/**
 * 查询网格分析存量
 */
export function getGridAnalysisCached(gridCode) {
  return request({
    url: '/jwmap/ai/grid-analysis/' + gridCode,
    method: 'get'
  })
}

// ==================== 选址报告 ====================

/**
 * 生成选址报告
 */
export function generateSiteReport(gridCode) {
  return request({
    url: '/jwmap/ai/site-report/' + gridCode,
    method: 'post'
  })
}

/**
 * 下载选址报告
 */
export function downloadReport(reportId) {
  return request({
    url: '/jwmap/ai/report/download/' + reportId,
    method: 'get',
    responseType: 'blob'
  })
}

// ==================== 四象限分析 ====================

/**
 * 四象限分析 — SSE 流式（全市）
 */
export function getQuadrantAnalysisStream(city, year, forceRefresh = false) {
  let url = '/jwmap/ai/quadrant-analysis/stream/' + city + '/' + year
  if (forceRefresh) url += '?forceRefresh=true'
  return url
}

/**
 * 查询四象限分析存量
 */
export function getQuadrantAnalysisCached(city, year) {
  return request({
    url: '/jwmap/ai/quadrant-analysis/' + city + '/' + year,
    method: 'get'
  })
}

/**
 * 单网点四象限分析 — SSE 流式
 */
export function getPerBranchQuadrantStream(branchId, year, forceRefresh = false) {
  let url = '/jwmap/ai/quadrant-analysis/stream/per-branch?branchId=' + branchId + '&year=' + year
  if (forceRefresh) url += '&forceRefresh=true'
  return url
}

/**
 * 查询单网点四象限分析存量
 */
export function getPerBranchQuadrantCached(branchId, year) {
  return request({
    url: '/jwmap/ai/quadrant-analysis/per-branch/' + branchId + '/' + year,
    method: 'get'
  })
}

// ==================== 迁址建议 ====================

/**
 * 迁址建议 — SSE 流式
 * @param {number} branchId
 * @param {number} year
 * @param {string} city
 * @param {boolean} forceRefresh
 */
export function getRelocationSuggestionStream(branchId, year, city, forceRefresh = false) {
  let url = '/jwmap/ai/relocation-suggestion/stream/' + branchId + '/' + year + '?city=' + encodeURIComponent(city)
  if (forceRefresh) url += '&forceRefresh=true'
  return url
}

/**
 * 查询迁址建议存量
 */
export function getRelocationSuggestionCached(branchId, year) {
  return request({
    url: '/jwmap/ai/relocation-suggestion/cached',
    method: 'get',
    params: { branchId, year }
  })
}

// ==================== 满意度评价 ====================

/**
 * 提交满意度评价
 */
export function submitFeedback(data) {
  return request({
    url: '/jwmap/ai/feedback',
    method: 'post',
    data
  })
}

/**
 * 保存 AI 分析内容（用户满意后手动触发）
 * @param {string} analysisType
 * @param {string} entityKey
 * @param {string} city
 * @param {string} content
 */
export function saveAnalysisContent(data) {
  return request({
    url: '/jwmap/ai/save',
    method: 'post',
    data
  })
}

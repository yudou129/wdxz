/**
 * 异步请求状态管理 mixin（仅 jwmap 模块使用）
 * 提供 withAsyncState 方法自动管理 loading/error 状态
 *
 * 用法:
 *   mixins: [fetchStateMixin],
 *   methods: {
 *     async fetchList() {
 *       const res = await this.withAsyncState('list', getMyRequestList, { pageNum: 1 })
 *       if (res) this.list = res.rows || []
 *     }
 *   }
 *
 * 自动生成:
 *   this.loading.list  — 加载中 true/false
 *   this.error.list    — 失败时错误消息，成功时 null
 */
const fetchStateMixin = {
  data() {
    return {
      loading: {},
      error: {}
    }
  },
  methods: {
    /**
     * 执行异步请求并自动管理 loading/error 状态
     * @param {String} key 状态键名
     * @param {Function} apiFn API 函数
     * @param {Object} params 请求参数
     * @param {String} context 操作名称（默认同 key）
     * @returns {Promise<*>} API 响应或 null
     */
    async withAsyncState(key, apiFn, params, context) {
      this.$set(this.loading, key, true)
      this.$set(this.error, key, null)
      try {
        return await apiFn(params)
      } catch (e) {
        const msg = (e && (e.msg || e.message)) || '未知错误'
        this.$set(this.error, key, msg)
        this.$message.error(`${context || key}失败：${msg}`)
        console.error(`[jwmap] ${context || key}失败:`, e)
        return null
      } finally {
        this.$set(this.loading, key, false)
      }
    }
  }
}

export default fetchStateMixin

/**
 * 统一错误处理 mixin（仅 jwmap 模块使用）
 * 提供 handleError 方法替代分散的 catch 块
 */
const errorHandlerMixin = {
  methods: {
    /**
     * 统一处理错误：弹 toast + 打印日志
     * @param {Error|Object} e 错误对象
     * @param {String} context 操作名称（如"加载列表"）
     * @param {Boolean} silent 是否静默（不弹 toast）
     * @returns {String} 错误消息
     */
    handleError(e, context = '操作', silent = false) {
      const msg = (e && (e.msg || e.message)) || '未知错误'
      if (!silent) {
        this.$message.error(`${context}失败：${msg}`)
      }
      console.error(`[jwmap] ${context}失败:`, e)
      return msg
    }
  }
}

export default errorHandlerMixin

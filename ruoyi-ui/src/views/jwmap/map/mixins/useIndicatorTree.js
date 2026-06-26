import { getIndicatorList } from '@/api/jwmap/data'

/**
 * 指标树查询 mixin — 加载指标名称映射表、追溯父链分类
 */
export default {
  methods: {
    async loadIndicatorNames() {
      try {
        const res = await getIndicatorList(null)
        const nameMap = {}
        const parentMap = {}
        const list = res.data || []
        for (const item of list) {
          if (item.indicatorCode) {
            nameMap[item.indicatorCode] = item.indicatorName || item.indicatorCode
            if (item.parentCode) parentMap[item.indicatorCode] = item.parentCode
          }
        }
        this.indicatorNameMap = nameMap
        this.indicatorParentMap = parentMap
      } catch (e) { /* ignore */ }
    },

    getIndicatorCategory(code) {
      if (!code || !this.indicatorParentMap) return '其他'
      let current = code
      let parentCode = this.indicatorParentMap[current]
      while (parentCode) {
        current = parentCode
        parentCode = this.indicatorParentMap[parentCode]
      }
      return this.indicatorNameMap[current] || current
    },

    getAncestorChain(code) {
      if (!code || !this.indicatorParentMap) return []
      const chain = []
      let current = code
      let parentCode = this.indicatorParentMap[current]
      while (parentCode) {
        chain.unshift(this.indicatorNameMap[parentCode] || parentCode)
        current = parentCode
        parentCode = this.indicatorParentMap[parentCode]
      }
      return chain
    },

    getParentCategoryName(code) {
      if (!code || !this.indicatorParentMap) return null
      const parentCode = this.indicatorParentMap[code]
      return parentCode ? (this.indicatorNameMap[parentCode] || parentCode) : null
    }
  }
}

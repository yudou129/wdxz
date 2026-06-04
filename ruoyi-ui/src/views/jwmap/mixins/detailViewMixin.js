/**
 * 数据详情视图混入（GridDetailView / BranchDetailView 共享逻辑）
 *
 * 组件需提供 computed `indicators`（指标数据数组，如 gridIndicators / branchIndicators）
 */
export default {
  data() {
    return {
      pillarScores: null,
      indicatorMap: {},
      categoryMap: {},
      districtFilter: '',
      currentPage: 1,
      pageSize: 20,
      activeCategories: [],
      activeL2: {}
    }
  },
  computed: {
    /** 三级指标树：根 → 每个根下分 direct（直属于根）和 categories（有中间父节点）两组 */
    indicatorTree() {
      const rootMap = {}
      const list = this.indicators || []
      for (const ind of list) {
        const l1Code = ind.level1Code || '__other__'
        const l1Name = ind.level1Name || '其他'
        const parentCode = ind.parentCode
        if (!rootMap[l1Code]) {
          rootMap[l1Code] = { code: l1Code, name: l1Name, direct: [], categories: {} }
        }
        if (parentCode && parentCode !== l1Code) {
          const catName = this.categoryMap[parentCode] || parentCode
          if (!rootMap[l1Code].categories[catName]) {
            rootMap[l1Code].categories[catName] = []
          }
          rootMap[l1Code].categories[catName].push(ind)
        } else {
          rootMap[l1Code].direct.push(ind)
        }
      }
      return Object.values(rootMap).map(root => ({
        code: root.code,
        name: root.name,
        direct: root.direct,
        categories: Object.entries(root.categories).map(([name, indicators]) => ({ name, indicators }))
      }))
    },
    /** 各一级分类 TOPSIS 得分列表 */
    pillarList() {
      if (!this.pillarScores) return []
      return Object.keys(this.pillarScores).map(key => ({
        key,
        label: this.pillarScores[key].name || key,
        score: (this.pillarScores[key].score || 0).toFixed(4)
      }))
    }
  },
  methods: {
    formatValue(val) {
      if (val === null || val === undefined) return '-'
      const num = Number(val)
      if (isNaN(num)) return val
      if (Number.isInteger(num)) return num.toLocaleString()
      return num.toFixed(4)
    }
  }
}

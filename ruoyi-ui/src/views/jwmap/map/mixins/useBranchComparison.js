import { getBranchScoreDetail, getBranchInternalRanking, getBranchIndicators } from '@/api/jwmap/data'

/**
 * 网点对比 mixin — 对比模式开关、添加/移除网点、数据加载刷新（含具体指标）
 */
export default {
  methods: {
    onToggleCompare() {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      this.compareMode = !this.compareMode
      if (this.compareMode) {
        this.comparePanel.visible = true
        this.closeSidebar()
      } else {
        this.comparePanel.visible = false
        this.comparePanel.branches = []
      }
    },

    onCompareClose() {
      this.compareMode = false
      this.comparePanel.visible = false
      this.comparePanel.branches = []
    },

    onCompareRemoveBranch(branchId) {
      this.comparePanel.branches = this.comparePanel.branches.filter(b => b.branchId !== branchId)
    },

    onCompareClearAll() {
      this.comparePanel.branches = []
    },

    onAddCompareBranch(branch) {
      if (!this.compareMode) return
      if (this.comparePanel.branches.length >= 4) {
        this.$message.warning('最多选择4个网点进行对比')
        return
      }
      if (this.comparePanel.branches.some(b => b.branchId === branch.branchId)) {
        this.$message.info('该网点已在对比列表中')
        return
      }
      // 新增：区分一级支行和二级支行以判断权限
      const access = this.checkBranchAccess(branch)
      this.comparePanel.branches.push({
        branchId: branch.branchId,
        branchData: branch,
        scores: [],
        indicators: [],
        rankMeta: {},
        access
      })
      this.loadBranchCompareData(branch.branchId)
    },

    /** 检查网点的数据访问权限 */
    checkBranchAccess(branch) {
      return this.branchAccess !== false
    },

    async loadBranchCompareData(branchId) {
      this.comparePanel.loading = true
      try {
        const [scoresRes, rankRes, indRes] = await Promise.all([
          getBranchScoreDetail(branchId, this.selectedYear),
          getBranchInternalRanking(branchId, this.selectedYear),
          this.fetchBranchIndicatorsSafe(branchId)
        ])
        const idx = this.comparePanel.branches.findIndex(b => b.branchId === branchId)
        if (idx === -1) return
        this.$set(this.comparePanel.branches, idx, {
          ...this.comparePanel.branches[idx],
          scores: (scoresRes.data || [])
            .filter(s => !s.scoreCategory || !s.scoreCategory.toLowerCase().includes('_auto'))
            .map(s => ({ ...s, categoryName: this.indicatorNameMap[s.scoreCategory] || '' })),
          indicators: indRes || [],
          rankMeta: rankRes.data || { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
        })
      } catch (e) {
        console.error('[jwmap] 加载对比数据失败:', e)
        this.$message.error('加载对比数据失败')
      }
      this.comparePanel.loading = false
    },

    async fetchBranchIndicatorsSafe(branchId) {
      try {
        const res = await getBranchIndicators(branchId, this.selectedYear)
        const list = res.data || res || []
        if (!Array.isArray(list)) return []
        return list.map(i => ({
          code: i.indicatorCode,
          name: this.indicatorNameMap[i.indicatorCode] || i.indicatorCode,
          value: i.indicatorValue,
          categoryLevel1: i.level1Name,
          categoryLevel2: i.level2Name || i.level1Name,
          level1Code: i.level1Code,
        }))
      } catch (e) { return [] }
    },

    async refreshAllCompareData() {
      const ids = this.comparePanel.branches.map(b => b.branchId)
      if (!ids.length) return
      this.comparePanel.loading = true
      try {
        const results = await Promise.all(ids.map(async (id) => {
          const [scoresRes, rankRes, indRes] = await Promise.all([
            getBranchScoreDetail(id, this.selectedYear),
            getBranchInternalRanking(id, this.selectedYear),
            this.fetchBranchIndicatorsSafe(id)
          ])
          return {
            branchId: id,
            scores: (scoresRes.data || [])
              .filter(s => !s.scoreCategory || !s.scoreCategory.toLowerCase().includes('_auto'))
              .map(s => ({ ...s, categoryName: this.indicatorNameMap[s.scoreCategory] || '' })),
            indicators: indRes || [],
            rankMeta: rankRes.data || { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
          }
        }))
        for (const r of results) {
          const idx = this.comparePanel.branches.findIndex(b => b.branchId === r.branchId)
          if (idx !== -1) {
            this.$set(this.comparePanel.branches, idx, {
              ...this.comparePanel.branches[idx],
              scores: r.scores,
              indicators: r.indicators,
              rankMeta: r.rankMeta
            })
          }
        }
      } catch (e) {
        console.error('[jwmap] 刷新对比数据失败:', e)
      }
      this.comparePanel.loading = false
    }
  }
}

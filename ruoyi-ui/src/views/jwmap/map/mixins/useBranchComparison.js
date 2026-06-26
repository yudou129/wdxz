import { getBranchScoreDetail, getBranchInternalRanking } from '@/api/jwmap/data'

/**
 * 网点对比 mixin — 对比模式开关、添加/移除网点、数据加载刷新
 */
export default {
  methods: {
    onToggleCompare() {
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
      this.comparePanel.branches.push({
        branchId: branch.branchId,
        branchData: branch,
        scores: [],
        rankMeta: {}
      })
      this.loadBranchCompareData(branch.branchId)
    },

    async loadBranchCompareData(branchId) {
      this.comparePanel.loading = true
      try {
        const [scoresRes, rankRes] = await Promise.all([
          getBranchScoreDetail(branchId, this.selectedYear),
          getBranchInternalRanking(branchId, this.selectedYear)
        ])
        const idx = this.comparePanel.branches.findIndex(b => b.branchId === branchId)
        if (idx === -1) return
        this.$set(this.comparePanel.branches, idx, {
          ...this.comparePanel.branches[idx],
          scores: (scoresRes.data || [])
            .filter(s => !s.scoreCategory || !s.scoreCategory.toLowerCase().includes('_auto'))
            .map(s => ({ ...s, categoryName: this.indicatorNameMap[s.scoreCategory] || '' })),
          rankMeta: rankRes.data || { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
        })
      } catch (e) {
        console.error('[jwmap] 加载对比数据失败:', e)
        this.$message.error('加载对比数据失败')
      }
      this.comparePanel.loading = false
    },

    async refreshAllCompareData() {
      const ids = this.comparePanel.branches.map(b => b.branchId)
      if (!ids.length) return
      this.comparePanel.loading = true
      try {
        const results = await Promise.all(ids.map(async (id) => {
          const [scoresRes, rankRes] = await Promise.all([
            getBranchScoreDetail(id, this.selectedYear),
            getBranchInternalRanking(id, this.selectedYear)
          ])
          return {
            branchId: id,
            scores: (scoresRes.data || [])
              .filter(s => !s.scoreCategory || !s.scoreCategory.toLowerCase().includes('_auto'))
              .map(s => ({ ...s, categoryName: this.indicatorNameMap[s.scoreCategory] || '' })),
            rankMeta: rankRes.data || { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
          }
        }))
        for (const r of results) {
          const idx = this.comparePanel.branches.findIndex(b => b.branchId === r.branchId)
          if (idx !== -1) {
            this.$set(this.comparePanel.branches, idx, {
              ...this.comparePanel.branches[idx],
              scores: r.scores,
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

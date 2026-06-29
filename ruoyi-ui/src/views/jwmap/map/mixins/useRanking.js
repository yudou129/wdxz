import { getGridRanking, getBranchRanking, getThreeFocusRanking, getGridScoreByCity } from '@/api/jwmap/data'

/**
 * 排名 mixin — 网格/网点排名加载、翻页、类型切换、三聚焦排名
 */
export default {
  methods: {
    onToggleRanking() {
      if (this.ranking.visible) {
        this.ranking.visible = false
      } else if (this.currentCity) {
        this.loadRanking(this.ranking.type || 'grid')
      } else {
        this.$message.warning('请先选择城市')
      }
    },

    loadRanking(type, show) {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      this.ranking.type = type || 'grid'
      this.ranking.page = 1; this.ranking.hasMore = false
      if (show !== false) this.ranking.visible = true
      this.fetchRanking()
    },

    async fetchRanking() {
      if (!this.currentCity) return
      this.ranking.loading = true
      try {
        const pageSize = 20
        const isBranch = this.ranking.type === 'branch'
        const res = isBranch
          ? await getBranchRanking(this.currentCity, this.selectedYear, this.ranking.page, pageSize)
          : await getGridRanking(this.currentCity, this.ranking.page, pageSize)
        const rows = res.rows || res.data || []
        const mapped = rows.map(r => ({
          id: isBranch ? (r.branchId || r.gridCode) : (r.gridCode || r.branchId),
          name: r.gridCode || r.secondaryBranch || ('网点#' + r.branchId),
          score: r.siteScore || r.categoryScore || 0,
          type: this.ranking.type
        }))
        this.ranking.items = this.ranking.page === 1 ? mapped : [...this.ranking.items, ...mapped]
        this.ranking.hasMore = rows.length >= pageSize
        this.ranking.title = isBranch ? '网点效能排名' : '网格选址排名'
      } catch (e) { if (this.ranking.page === 1) this.ranking.items = []; this.ranking.hasMore = false }
      this.ranking.loading = false
    },

    loadMoreRanking() {
      this.ranking.page++
      this.fetchRanking()
    },

    onRankingTypeChange(type) {
      this.loadRanking(type)
    },

    onFocusChange(category) {
      this.ranking.type = 'focus'
      this.ranking.page = 1
      this.ranking.hasMore = false
      this.ranking.visible = true
      this.fetchFocusRanking(category)
    },

    async fetchFocusRanking(category) {
      if (!this.currentCity) return
      this.ranking.loading = true
      try {
        const res = await getThreeFocusRanking(this.currentCity, this.selectedYear)
        const data = res.data || {}
        const list = data[category] || []
        this.ranking.items = list.map((r, i) => ({
          id: r.gridCode || ('f' + i),
          name: r.branchName || r.gridCode || '',
          score: r.score || 0,
          type: 'grid'
        }))
      } catch (e) { this.ranking.items = [] }
      this.ranking.loading = false
    },

    onRankingItemClick(item) {
      if (!item.id) return
      if (item.type === 'branch') {
        this.branchLayer.eachLayer(layer => {
          if (layer.branchData && layer.branchData.branchId === item.id) {
            this.map.flyTo([layer.branchData.latitude, layer.branchData.longitude], 14)
            this.onBranchClick(layer.branchData)
            this.highlightBranch(layer.branchData)
          }
        })
      } else {
        this.navigateToGrid(item.id)
      }
    },

    async navigateToGrid(gridCode) {
      if (!this.currentCity) return
      try {
        // Use cached grid data if available to avoid redundant API call
        let list = this.gridDataCache
        if (!list || !Array.isArray(list)) {
          const res = await getGridScoreByCity(this.currentCity)
          list = res.data || []
        }
        if (!Array.isArray(list)) return
        const found = list.find(d => d.gridCode === gridCode)
        if (found && found.latitude != null && found.longitude != null) {
          this.gridDataCache = list
          this.map.flyTo([found.latitude, found.longitude], 14)
          await this.onGridClick(found.gridCode, found)
          this.highlightGrid(found)
        }
      } catch (e) { console.error('[jwmap] navigateToGrid error:', e) }
    }
  }
}

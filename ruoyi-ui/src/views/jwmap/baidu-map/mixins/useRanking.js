/**
 * 百度地图 GL 版 排名 mixin — 网格/网点排名加载、翻页、类型切换、三聚焦排名
 *
 * 与 Leaflet 版区别：flyTo 改用 BMapGL.Point，遍历 branchMarkers 代替 branchLayer.eachLayer
 */
import { getGridRanking, getBranchRanking, getThreeFocusRanking, getGridScoreByCity } from '@/api/jwmap/data'

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
      this.ranking.page = 1
      this.ranking.hasMore = false
      if (show !== false) this.ranking.visible = true
      this.fetchRanking()
    },

    async fetchRanking() {
      if (!this.currentCity) return
      if (!this._rankingSeq) this._rankingSeq = 0
      const seq = ++this._rankingSeq
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
        if (seq !== this._rankingSeq) return
        this.ranking.items = this.ranking.page === 1 ? mapped : [...this.ranking.items, ...mapped]
        this.ranking.hasMore = rows.length >= pageSize
        this.ranking.title = isBranch ? '网点效能排名' : '网格选址排名'
      } catch (e) {
        if (this.ranking.page === 1) this.ranking.items = []
        this.ranking.hasMore = false
      }
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
      if (!this._focusSeq) this._focusSeq = 0
      const seq = ++this._focusSeq
      this.ranking.loading = true
      try {
        const res = await getThreeFocusRanking(this.currentCity, this.selectedYear)
        const data = res.data || {}
        const list = data[category] || []
        if (seq !== this._focusSeq) return
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
        for (const marker of this.branchMarkers) {
          if (marker._branchData && marker._branchData.branchId === item.id) {
            const b = marker._branchData
            const BMapGL = window.BMapGL
            if (BMapGL) {
              this.map.flyTo(new BMapGL.Point(b.longitude, b.latitude), 14, { duration: 0.5 })
            }
            this.onBranchClick(b)
            this.highlightBranch(b)
            return
          }
        }
      } else {
        this.navigateToGrid(item.id)
      }
    },

    async navigateToGrid(gridCode) {
      if (!this.currentCity) return
      if (!this._navSeq) this._navSeq = 0
      const seq = ++this._navSeq
      try {
        let list = this.gridDataCache
        if (!list || !Array.isArray(list)) {
          const res = await getGridScoreByCity(this.currentCity)
          list = res.data || []
        }
        if (!Array.isArray(list)) return
        const found = list.find(d => d.gridCode === gridCode)
        if (seq !== this._navSeq) return
        if (found && found.latitude != null && found.longitude != null) {
          this.gridDataCache = list
          const BMapGL = window.BMapGL
          if (BMapGL) {
            this.map.flyTo(new BMapGL.Point(found.longitude, found.latitude), 14, { duration: 0.5 })
          }
          await this.onGridClick(found.gridCode, found)
          this.highlightGrid(found)
        }
      } catch (e) { console.error('[bmap] navigateToGrid error:', e) }
    }
  }
}

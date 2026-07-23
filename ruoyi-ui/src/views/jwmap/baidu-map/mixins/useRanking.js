/**
 * 排名 mixin — 网格排名 / 网点排名（分离）+ 三聚焦排名
 *
 * ranking → 网格排名
 * branchRanking → 网点排名（带年份）
 */
import { getGridRanking, getBranchRanking, getThreeFocusRanking, getGridScoreByCity } from '@/api/jwmap/data'

export default {
  methods: {
    // ==================== 网格排名 ====================

    onToggleGridRanking() {
      if (this.ranking.visible) {
        this.ranking.visible = false
      } else if (this.currentCity) {
        this.loadGridRanking()
      } else {
        this.$message.warning('请先选择城市')
      }
    },

    loadGridRanking(show = true) {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      this.ranking.page = 1
      this.ranking.hasMore = false
      if (show !== false) this.ranking.visible = true
      this.ranking.title = '网格选址排名'
      this.ranking.type = 'grid'
      this._fetchGridRanking()
    },

    async _fetchGridRanking() {
      if (!this.currentCity) return
      if (!this._gridRankSeq) this._gridRankSeq = 0
      const seq = ++this._gridRankSeq
      this.ranking.loading = true
      try {
        const pageSize = 20
        const district = this.currentDistrict || null
        const res = await getGridRanking(this.currentCity, this.ranking.page, pageSize, district)
        const rows = res.rows || res.data || []
        const mapped = rows.map(r => ({
          id: r.gridCode || r.branchId,
          name: r.gridCode,
          subtitle: r.district || '',
          score: r.siteScore || r.categoryScore || 0,
          type: 'grid',
          _lng: r.longitude,
          _lat: r.latitude
        }))
        if (seq !== this._gridRankSeq) return
        this.ranking.items = this.ranking.page === 1 ? mapped : [...this.ranking.items, ...mapped]
        this.ranking.hasMore = rows.length >= pageSize
        // 异步加载地址（前50条有坐标的）
        this._loadGridAddresses(this.ranking.items)
      } catch (e) {
        if (this.ranking.page === 1) this.ranking.items = []
        this.ranking.hasMore = false
      }
      this.ranking.loading = false
    },

    /** 对网格排名条目异步加载地址（最多前50条，避免百度API限频） */
    async _loadGridAddresses(items) {
      if (!window.BMapGL || !items || !items.length) return
      const toLoad = items.filter(i => i._lng && i._lat && (!i.subtitle || i.subtitle.indexOf('区') >= 0))
      const batch = toLoad.slice(0, 50)
      for (const item of batch) {
        try {
          const geocoder = new window.BMapGL.Geocoder()
          await new Promise(resolve => {
            geocoder.getLocation(new window.BMapGL.Point(item._lng, item._lat), (result) => {
              if (result && result.address) {
                item.subtitle = result.address
              }
              resolve()
            })
          })
        } catch (e) { /* 单个地址失败跳过 */ }
      }
    },

    loadMoreGridRanking() {
      this.ranking.page++
      this._fetchGridRanking()
    },

    // ==================== 网点排名（带年份） ====================

    onToggleBranchRanking() {
      if (this.branchRanking.visible) {
        this.branchRanking.visible = false
      } else if (this.currentCity) {
        this.loadBranchRanking()
      } else {
        this.$message.warning('请先选择城市')
      }
    },

    loadBranchRanking() {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      this.branchRanking.page = 1
      this.branchRanking.hasMore = false
      this.branchRanking.visible = true
      this.branchRanking.title = '网点效能排名'
      this.branchRanking.type = 'branch'
      this._fetchBranchRanking()
    },

    async _fetchBranchRanking() {
      if (!this.currentCity) return
      if (!this._branchRankSeq) this._branchRankSeq = 0
      const seq = ++this._branchRankSeq
      this.branchRanking.loading = true
      try {
        const pageSize = 20
        const primaryBranch = this.currentFilter || null
        const res = await getBranchRanking(this.currentCity, this.branchRanking.year || this.selectedYear, this.branchRanking.page, pageSize, primaryBranch)
        const rows = res.rows || res.data || []
        const mapped = rows.map(r => ({
          id: r.branchId || r.gridCode,
          name: r.secondaryBranch || r.gridCode || ('网点#' + r.branchId),
          score: r.categoryScore || r.siteScore || 0,
          type: 'branch'
        }))
        if (seq !== this._branchRankSeq) return
        this.branchRanking.items = this.branchRanking.page === 1 ? mapped : [...this.branchRanking.items, ...mapped]
        this.branchRanking.hasMore = rows.length >= pageSize
      } catch (e) {
        if (this.branchRanking.page === 1) this.branchRanking.items = []
        this.branchRanking.hasMore = false
      }
      this.branchRanking.loading = false
    },

    loadMoreBranchRanking() {
      this.branchRanking.page++
      this._fetchBranchRanking()
    },

    onBranchRankingYearChange(year) {
      this.branchRanking.year = year
      this.branchRanking.page = 1
      this.branchRanking.hasMore = false
      this._fetchBranchRanking()
    },

    // ==================== 三聚焦排名 ====================

    onFocusChange(category) {
      this.branchRanking.type = 'focus'
      this.branchRanking.page = 1
      this.branchRanking.hasMore = false
      this.branchRanking.visible = true
      this._fetchFocusRanking(category)
    },

    async _fetchFocusRanking(category) {
      if (!this.currentCity) return
      if (!this._focusSeq) this._focusSeq = 0
      const seq = ++this._focusSeq
      this.branchRanking.loading = true
      try {
        const res = await getThreeFocusRanking(this.currentCity, this.branchRanking.year || this.selectedYear)
        const data = res.data || {}
        const list = data[category] || []
        if (seq !== this._focusSeq) return
        this.branchRanking.items = list.map((r, i) => ({
          id: r.gridCode || ('f' + i),
          name: r.branchName || r.gridCode || '',
          score: r.score || 0,
          type: 'grid'
        }))
      } catch (e) { this.branchRanking.items = [] }
      this.branchRanking.loading = false
    },

    // ==================== 排名条目点击 ====================

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

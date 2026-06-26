import L from 'leaflet'
import { TiandituBd09Crs } from '../utils/tiandituCrs.js'
import { BoundaryManager } from '../utils/boundaryManager.js'
import { MeasureTool } from '../utils/measureTool.js'
import { HeatmapLayer } from '../utils/heatmapLayer'
import { getGridScoreByCity, getBranchList, getGridIndicators, getGridBranches,
         getGridDistrictRanking, getGridPillarScores, getBranchScoreDetail,
         getBranchInternalRanking, getQuadrantData, getDimensionStats,
         getPillarGap, getBranchIndicators } from '@/api/jwmap/data'
import { checkBranchAccess, getPendingCount } from '@/api/jwmap/data-access'
import '@/views/jwmap/map/assets/branch-icon.css'

/**
 * 地图生命周期 mixin — 地图初始化、工具栏、网点/网格点击编排、挂载/销毁
 */
export default {
  mounted() {
    this.$nextTick(() => this.initMap())
    this.loadPendingCount()
    this.$root.$on('pending-count-changed', this.loadPendingCount)
  },

  beforeDestroy() {
    if (this.measureTool) { this.measureTool._deactivate(); this.measureTool._clear() }
    if (this.rangeModeActive) this.unbindRangeEvents()
    if (this.blankSpotLayer) { this.map.removeLayer(this.blankSpotLayer); this.blankSpotLayer = null }
    this._hidePeerBankLegend()
    this.$root.$off('pending-count-changed', this.loadPendingCount)
    if (this.map) { this.map.remove(); this.map = null }
  },

  methods: {
    initMap() {
      this.map = L.map(this.$refs.mapEl, {
        crs: TiandituBd09Crs, center: [26.5807, 106.7238], zoom: 10,
        minZoom: 9, maxZoom: 17, zoomControl: true, attributionControl: false,
        zoomAnimation: true, fadeAnimation: true
      })
      L.tileLayer('/tiles_tianditu/vec/{z}/{x}/{y}.png', { minZoom: 9, maxZoom: 17, tileSize: 256 }).addTo(this.map)
      L.tileLayer('/tiles_tianditu/cva/{z}/{x}/{y}.png', { minZoom: 9, maxZoom: 17, tileSize: 256 }).addTo(this.map)

      this.heatmapLayer = new HeatmapLayer(this.map)
      this.branchLayer = L.layerGroup().addTo(this.map)
      this.peerBankLayer = L.layerGroup()
      this.map.on('click', (e) => {
        if (!this.heatmapVisible) return
        const target = e.originalEvent && e.originalEvent.target
        if (target && (target.classList.contains('branch-icon') || target.closest('.branch-icon'))) return
        const gridData = this.heatmapLayer.getGridAtLatLng(e.latlng)
        if (gridData) {
          this.onGridClick(gridData.gridCode, gridData)
        } else if (this.sidebar.visible) {
          this.closeSidebar()
        }
      })

      this.boundaryMgr = new BoundaryManager(this.map)
      this.boundaryMgr.init({ createControl: false })
        .then(() => {
          this.cityBoundaries = this.boundaryMgr.cities || []
          const map = {}
          for (const c of this.cityBoundaries) {
            if (c.properties && c.properties.adcode != null) map[c.properties.adcode] = c.properties.name
          }
          this.cityNameMap = map
        })
        .catch(err => console.error('[jwmap] 边界加载失败:', err))

      this.measureTool = new MeasureTool(this.map)
      this.measureTool.init()
      this.loadIndicatorNames()
      this.heatLegend = L.control({ position: 'bottomleft' })
      this.heatLegend.onAdd = function () {
        const div = L.DomUtil.create('div', '')
        div.style.cssText = 'background:rgba(255,255,255,0.9);border-radius:6px;padding:8px 10px;font-size:11px;box-shadow:0 1px 6px rgba(0,0,0,0.15);'
        div.innerHTML = '<div style="display:flex;align-items:center;gap:6px;margin-bottom:2px">'
          + '<span style="color:#333;font-weight:600;font-size:10px">选址得分</span></div>'
          + '<div style="display:flex;align-items:stretch;gap:4px">'
          + '<span style="font-size:10px;color:#888;line-height:1.2">低</span>'
          + '<div style="width:120px;height:14px;border-radius:3px;background:linear-gradient(90deg,#00ff00,#ffff00,#ff0000)"></div>'
          + '<span style="font-size:10px;color:#888;line-height:1.2">高</span>'
          + '</div>'
        return div
      }
    },

    // ==== 工具栏 ====
    async onSelectCity(adcode) {
      if (!adcode) {
        if (this.boundaryMgr) this.boundaryMgr.showAllCities()
        return
      }
      const cityName = this.cityNameMap[adcode]
      if (!cityName) return
      this.currentCity = cityName; this.currentAdcode = +adcode; this.currentFilter = null
      this.closeSidebar()
      if (this.heatmapVisible) { this.heatmapLayer.hide(); this.heatmapVisible = false; if (this.heatLegend) this.map.removeControl(this.heatLegend) }
      this.peerBankVisible = false
      if (this.boundaryMgr) this.boundaryMgr.showCity(this.currentAdcode)
      await this.loadGridDataCache()
      await this.loadBranches()
      await this.loadPeerBankMarkers()
      this.loadRanking('grid', false)
    },

    onSelectDistrict(adcode) {
      if (!this.boundaryMgr) return
      if (!adcode) {
        if (this.currentAdcode) this.boundaryMgr.showCity(this.currentAdcode)
        return
      }
      this.boundaryMgr.showDistrict(+adcode)
    },

    async onToggleHeatmap() {
      if (!this.currentCity) return
      if (this.heatmapVisible) {
        this.heatmapLayer.hide(); this.heatmapVisible = false
        if (this.heatLegend) this.map.removeControl(this.heatLegend)
        return
      }
      await this.heatmapLayer.loadData(this.currentCity)
      this.heatmapLayer.show()
      this.heatmapVisible = true
      if (this.heatLegend) this.heatLegend.addTo(this.map)
    },

    onFilterBranch(primaryBranch) {
      this.currentFilter = primaryBranch
      this.branchLayer.eachLayer(layer => {
        const match = !primaryBranch || (layer.branchData && layer.branchData.primaryBranch === primaryBranch)
        if (match) { this.map.addLayer(layer) } else { this.map.removeLayer(layer) }
      })
    },

    async onYearChange(year) {
      const _seq = (this._yearChangeSeq || 0) + 1
      this._yearChangeSeq = _seq
      this.selectedYear = year
      if (this.sidebar.visible && this.sidebar.branchData.branchId) {
        await this.loadBranchScores(this.sidebar.branchData.branchId)
      }
      if (this._yearChangeSeq !== _seq) return
      if (this.ranking.visible) this.loadRanking(this.ranking.type)
      if (this._yearChangeSeq !== _seq) return
      if (this.comparePanel.branches.length > 0) {
        await this.refreshAllCompareData()
      }
    },

    // ==== 网点 ====
    async loadBranches() {
      const res = await getBranchList(this.currentCity)
      this.branchLayer.clearLayers()
      const branches = res.data || []
      this.branchList = branches
      const icon = L.divIcon({ className: 'branch-icon', html: '工', iconSize: [24, 24], iconAnchor: [12, 12] })
      for (const b of branches) {
        if (b.longitude == null || b.latitude == null) continue
        const m = L.marker([b.latitude, b.longitude], { icon })
        m.branchData = b
        m.on('click', () => this.onBranchClick(b))
        this.branchLayer.addLayer(m)
      }
    },

    onSearchBranch(branch) {
      if (branch.latitude != null && branch.longitude != null) {
        this.map.flyTo([branch.latitude, branch.longitude], 15)
        setTimeout(() => this.onBranchClick(branch), 400)
      }
    },

    async onBranchClick(branch) {
      if (this.compareMode) {
        this.onAddCompareBranch(branch)
        return
      }
      const _seq = (this._branchClickSeq || 0) + 1
      this._branchClickSeq = _seq

      this.sidebar.mode = 'branch-only'; this.sidebar.width = 380
      this.sidebar.branchData = branch
      this.sidebar.visible = true

      this.branchAccess = false
      if (branch.branchId) {
        try {
          const res = await checkBranchAccess(branch.branchId)
          const data = res.data || {}
          this.branchAccess = data.hasAccess !== false
        } catch (e) {
          this.branchAccess = false
        }
      }
      if (this._branchClickSeq !== _seq) return

      await Promise.all([
        this.loadBranchScores(branch.branchId),
        this.loadBranchRankMeta(branch.branchId),
        this.loadBranchQuadrant(branch),
        this.loadPeerAndNearby(branch.branchId),
        this.loadPillarGap(branch.gridCode)
      ])
    },

    async loadPillarGap(gridCode) {
      const safeGap = { population: { gap: 0, name: '---' }, enterprise: { gap: 0, name: '---' }, business: { gap: 0, name: '---' } }
      if (!gridCode) { this.sidebar.pillarGap = safeGap; return }
      try {
        const res = await getPillarGap(gridCode)
        const d = res.data || {}
        const gapValues = Object.values(d)
        this.sidebar.pillarGap = {
          population: gapValues[0] ? { gap: gapValues[0].gap != null ? gapValues[0].gap : 0, name: gapValues[0].name || '---' } : { gap: 0, name: '---' },
          enterprise: gapValues[1] ? { gap: gapValues[1].gap != null ? gapValues[1].gap : 0, name: gapValues[1].name || '---' } : { gap: 0, name: '---' },
          business:   gapValues[2] ? { gap: gapValues[2].gap != null ? gapValues[2].gap : 0, name: gapValues[2].name || '---' } : { gap: 0, name: '---' }
        }
      } catch (e) { this.sidebar.pillarGap = safeGap }
    },

    async loadBranchScores(branchId) {
      try {
        const res = await getBranchScoreDetail(branchId, this.selectedYear)
        this.sidebar.branchScores = (res.data || [])
          .filter(s => !s.scoreCategory || !s.scoreCategory.toLowerCase().includes('_auto'))
          .map(s => ({ ...s, categoryName: this.indicatorNameMap[s.scoreCategory] || '' }))
      } catch (e) { this.sidebar.branchScores = [] }
    },

    async loadBranchRankMeta(branchId) {
      try {
        const res = await getBranchInternalRanking(branchId, this.selectedYear)
        this.sidebar.branchRankMeta = res.data || { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
      } catch (e) { this.sidebar.branchRankMeta = { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 } }
    },

    async loadBranchQuadrant(branch) {
      try {
        if (!this.quadrant.data || !this.quadrant.data.allData) {
          const res = await getQuadrantData(branch.city || this.currentCity, this.selectedYear)
          this.quadrant.data = res.data || null
        }
        if (this.quadrant.data && this.quadrant.data.allData) {
          const found = this.quadrant.data.allData.find(d => d.branchId === branch.branchId)
          this.sidebar.branchQuadrant = found ? found.quadrant : ''
        }
      } catch (e) { this.sidebar.branchQuadrant = '' }
    },

    // ==== 网格点击 ====
    async onGridClick(gridCode, data) {
      this.sidebar.visible = true
      this.sidebar.gridData = data
      const hd = this.heatmapLayer && this.heatmapLayer.getData()
      if (hd && hd.length) {
        const idx = hd.findIndex(d => d.gridCode === gridCode)
        this.sidebar.gridRank = idx >= 0 ? idx + 1 : null
      }
      const _seq = (this._gridClickSeq || 0) + 1
      this._gridClickSeq = _seq
      try {
        const [indRes, rankRes, pillarRes] = await Promise.all([
          getGridIndicators(gridCode).catch(() => ({ data: [] })),
          getGridDistrictRanking(gridCode).catch(() => ({ data: null })),
          getGridPillarScores(gridCode).catch(() => ({ data: null }))
        ])
        if (this._gridClickSeq !== _seq) return
        this.sidebar.gridIndicators = (indRes.data || []).map(d => ({
          code: d.indicatorCode,
          name: this.indicatorNameMap[d.indicatorCode] || d.indicatorCode,
          value: d.indicatorValue,
          categoryLevel1: d.level1Name,
          categoryLevel2: this.getParentCategoryName(d.indicatorCode) || d.level1Name,
          level1Code: d.level1Code,
          level1Name: d.level1Name
        }))
        if (this._gridClickSeq !== _seq) return
        this.sidebar.gridRankMeta = rankRes.data || { cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0, scoreGap: 0 }
        const rawPillar = pillarRes.data || {}
        const pillarValues = Object.values(rawPillar)
        const pillarKeys = Object.keys(rawPillar)
        const pillarCounts = {}
        pillarKeys.forEach(k => { pillarCounts[k] = 0 })
        for (const item of (indRes.data || [])) {
          if (item.level1Code && pillarCounts.hasOwnProperty(item.level1Code)) {
            pillarCounts[item.level1Code]++
          }
        }
        this.sidebar.pillar = {
          population: pillarValues[0] ? { ...pillarValues[0], count: pillarCounts[pillarKeys[0]] || 0 } : { score: 0, count: 0, name: '---' },
          enterprise: pillarValues[1] ? { ...pillarValues[1], count: pillarCounts[pillarKeys[1]] || 0 } : { score: 0, count: 0, name: '---' },
          business:   pillarValues[2] ? { ...pillarValues[2], count: pillarCounts[pillarKeys[2]] || 0 } : { score: 0, count: 0, name: '---' }
        }
        this.loadPillarGap(gridCode)

        const brRes = await getGridBranches(gridCode).catch(() => ({ data: [] }))
        if (this._gridClickSeq !== _seq) return
        const branches = brRes.data || []
        if (branches.length > 0) {
          this.sidebar.mode = 'split'; this.sidebar.width = 600
          this.sidebar.branchData = branches[0]
          await Promise.all([
            this.loadBranchScores(branches[0].branchId),
            this.loadBranchRankMeta(branches[0].branchId),
            this.loadBranchQuadrant(branches[0])
          ])
        } else {
          this.sidebar.mode = 'grid-only'; this.sidebar.width = 380
        }
      } catch (e) {
        console.error('[jwmap] 网格点击加载失败:', e)
      }
    },

    zoomToBranch() {
      const b = this.sidebar.branchData
      if (b && b.latitude != null && b.longitude != null) {
        this.map.flyTo([b.latitude, b.longitude], 15)
      }
    },

    async loadGridDataCache() {
      try {
        const res = await getGridScoreByCity(this.currentCity)
        this.gridDataCache = res.data || []
      } catch (e) { this.gridDataCache = [] }
    },

    closeSidebar() { this.sidebar.visible = false; this.detailPanel.visible = false; this.detailPanel.noAccess = false },

    navigateToApplyAccess(branchId) {
      this.$router.push({ path: '/jwmap/access-request', query: { branchId } })
    },

    goToAccessRequest() { this.$router.push('/jwmap/access-request') },
    goToApproval() { this.$router.push('/jwmap/access-approval') },

    loadPendingCount() {
      getPendingCount().then(res => {
        this.pendingCount = res.data || 0
      }).catch(() => {})
    },

    // ==== 维度统计 ====
    async loadDimStats(dimension) {
      if (!this.currentCity) return
      const dim = dimension || this.dimStats.dimension
      this.dimStats.dimension = dim
      try {
        const res = await getDimensionStats(this.currentCity, this.selectedYear, dim)
        this.dimStats.data = res.data || []
        this.dimStats.visible = true
      } catch (e) { this.$message.error('加载统计数据失败') }
    },

    // ==== 四象限 ====
    async loadQuadrant() {
      if (!this.currentCity) return
      try {
        const res = await getQuadrantData(this.currentCity, this.selectedYear)
        this.quadrant.data = res.data || null
        this.quadrant.visible = true
      } catch (e) { this.$message.error('加载象限数据失败') }
    },

    onQuadrantItemClick(item) {
      this.branchLayer.eachLayer(layer => {
        if (layer.branchData && layer.branchData.branchId === item.branchId) {
          this.map.flyTo([layer.branchData.latitude, layer.branchData.longitude], 14)
          this.onBranchClick(layer.branchData)
        }
      })
    },

    onQuadrantClose() {
      this.quadrant.visible = false
      // Re-apply active branch filter or show all when quadrant filter is cleared
      if (this.currentFilter) {
        this.onFilterBranch(this.currentFilter)
      } else {
        this.branchLayer.eachLayer(layer => { this.map.addLayer(layer) })
      }
    },

    onFilterQuadrant(quadrantCode) {
      if (!this.quadrant.data || !this.quadrant.data.quadrants) return
      const branches = this.quadrant.data.quadrants[quadrantCode] || []
      const branchIds = new Set(branches.map(b => b.branchId))
      this.branchLayer.eachLayer(layer => {
        if (layer.branchData) {
          const match = branchIds.has(layer.branchData.branchId)
          if (match) { this.map.addLayer(layer) } else { this.map.removeLayer(layer) }
        }
      })
      this.$message.success(`已筛选 ${quadrantCode}: ${branchIds.size} 个网点`)
    },

    async showDetailDialog(type) {
      const sidebarLeft = 12
      const sidebarWidth = this.sidebar.width
      this.detailPanel.left = sidebarLeft + sidebarWidth + 8
      this.detailPanel.data = []

      if (type === 'grid') {
        const indicators = this.sidebar.gridIndicators
        const maxVal = indicators.reduce((m, i) => {
          const v = parseFloat(i.value); return !isNaN(v) && v > m ? v : m
        }, 0)
        this.detailPanel.data = indicators.map(i => ({
          code: i.code, name: i.name, value: i.value,
          categoryLevel1: i.categoryLevel1, categoryLevel2: i.categoryLevel2,
          pct: maxVal > 0 ? Math.round(parseFloat(i.value) / maxVal * 100) : 0
        }))
        this.detailPanel.mode = 'grid'
        this.detailPanel.visible = true
      }

      if (type === 'branch') {
        this.detailPanel.data = []
        this.detailPanel.noAccess = false
        const branchId = this.sidebar.branchData.branchId
        this.detailPanel.branchId = branchId
        if (branchId && this.branchAccess) {
          try {
            const res = await getBranchIndicators(branchId, this.selectedYear)
            const list = res.data || res || []
            this.detailPanel.data = (Array.isArray(list) ? list : []).map(i => ({
              name: this.indicatorNameMap[i.indicatorCode] || i.indicatorCode,
              value: i.indicatorValue,
              ancestors: this.getAncestorChain(i.indicatorCode)
            }))
          } catch (e) {
            this.$message.error('加载网点指标数据失败')
          }
        } else if (branchId) {
          this.detailPanel.noAccess = true
        }
        this.detailPanel.mode = 'branch'
        this.detailPanel.visible = true
      }
    }
  }
}

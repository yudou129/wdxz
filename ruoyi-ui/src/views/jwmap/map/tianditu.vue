<template>
  <div class="jw-map-container">
    <div id="jwmap-tianditu" ref="mapEl"></div>

    <TopToolbar
      :cities="cityBoundaries"
      :heatmapActive="heatmapVisible"
      @select-city="onSelectCity"
      @select-district="onSelectDistrict"
      @toggle-heatmap="onToggleHeatmap"
      @toggle-quadrant="loadQuadrant"
      @toggle-dim-stats="loadDimStats"
      @filter-branch="onFilterBranch" />

    <SidebarPanel
      :visible="sidebar.visible"
      :mode="sidebar.mode"
      :width="sidebar.width"
      :gridData="sidebar.gridData"
      :gridRank="sidebar.gridRank"
      :gridRankMeta="sidebar.gridRankMeta"
      :gridIndicators="sidebar.gridIndicators"
      :branchData="sidebar.branchData"
      :branchScores="sidebar.branchScores"
      :branchRankMeta="sidebar.branchRankMeta"
      :branchQuadrant="sidebar.branchQuadrant"
      :pillar="sidebar.pillar"
      :peerBanks="peerBanks"
      :nearbyBranches="nearbyBranches"
      :pillarGap="sidebar.pillarGap"
      :years="availableYears"
      :year="selectedYear"
      @close="closeSidebar"
      @view-detail="showDetailDialog"
      @zoom-branch="zoomToBranch"
      @year-change="onYearChange" />

    <RankingList
      :visible="ranking.visible"
      :title="ranking.title"
      :type="ranking.type"
      :items="ranking.items"
      :page="ranking.page"
      :hasMore="ranking.hasMore"
      :loading="ranking.loading"
      :showTypeSwitch="true"
      :showFocusTabs="false"
      @item-click="onRankingItemClick"
      @load-more="loadMoreRanking"
      @type-change="onRankingTypeChange"
      @focus-change="onFocusChange"
      @close="ranking.visible = false" />

    <QuadrantChart
      :visible="quadrant.visible"
      :data="quadrant.data"
      @close="quadrant.visible = false"
      @item-click="onQuadrantItemClick"
      @filter-quadrant="onFilterQuadrant" />

    <DimensionStats
      :visible="dimStats.visible"
      :data="dimStats.data"
      @close="dimStats.visible = false"
      @dim-change="loadDimStats" />

    <DetailPanel
      :visible="detailPanel.visible"
      :data="detailPanel.data"
      :mode="detailPanel.mode"
      :leftPos="detailPanel.left"
      @close="detailPanel.visible = false" />
  </div>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { TiandituBd09Crs } from './utils/tiandituCrs.js'
import { BoundaryManager } from './utils/boundaryManager.js'
import { MeasureTool } from './utils/measureTool.js'
import TopToolbar from './components/TopToolbar'
import SidebarPanel from './components/SidebarPanel'
import RankingList from './components/RankingList'
import DetailPanel from './components/DetailPanel'
import QuadrantChart from './components/QuadrantChart'
import DimensionStats from './components/DimensionStats'
import PeerBankSection from './components/PeerBankSection'
import { HeatmapLayer } from './utils/heatmapLayer'
import { getGridIndicators, getBranchScoreDetail, getGridBranches,
         getGridRanking, getBranchRanking, getBranchList, getIndicatorList,
         getBranchIndicators, getQuadrantData,
         getBranchInternalRanking, getGridDistrictRanking,
         getGridPillarScores, getDimensionStats,
         getPeerBankDistance, getNearbyBranches, getThreeFocusRanking, getPillarGap,
         getGridScoreByCity } from '@/api/jwmap/data'
import '@/views/jwmap/map/assets/branch-icon.css'

export default {
  name: 'JwMapTianditu',
  components: { TopToolbar, SidebarPanel, RankingList, QuadrantChart, DetailPanel, DimensionStats, PeerBankSection },
  data() {
    return {
      map: null, boundaryMgr: null, measureTool: null,
      heatmapLayer: null, heatmapVisible: false,
      cityBoundaries: [], branchLayer: null,
      indicatorNameMap: {}, indicatorParentMap: {}, cityNameMap: {},
      selectedYear: 2024,
      sidebar: {
        visible: false, mode: 'grid-only', width: 380,
        gridData: {}, gridRank: null, gridIndicators: [],
        gridRankMeta: { cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0, scoreGap: 0 },
        branchData: {}, branchScores: [],
        branchRankMeta: { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 },
        branchQuadrant: '',
        pillar: { population: { score: 0, count: 0 }, enterprise: { score: 0, count: 0 }, business: { score: 0, count: 0 } },
        pillarGap: { population: { gap: 0 }, enterprise: { gap: 0 }, business: { gap: 0 } }
      },
      ranking: { visible: false, title: '', items: [], page: 1, hasMore: false, loading: false, type: 'grid' },
      quadrant: { visible: false, data: null },
      dimStats: { visible: false, data: [], dimension: 'district' },
      peerBanks: [],
      nearbyBranches: [],
      gridDataCache: null,
      currentCity: null, currentFilter: null, currentAdcode: null,
      detailPanel: { visible: false, data: [], mode: 'branch', left: 400 }
    }
  },
  computed: {
    availableYears() {
      const cur = new Date().getFullYear()
      return [cur - 2, cur - 1, cur]
    }
  },
  mounted() { this.$nextTick(() => this.initMap()) },
  beforeDestroy() {
    if (this.measureTool) { this.measureTool._deactivate(); this.measureTool._clear() }
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
      // 热力图网格点击 — 通过坐标范围判断（替代 L.rectangle SVG 点击，后者在透明填充下不可靠）
      this.map.on('click', (e) => {
        if (!this.heatmapVisible) return
        // 排除网点标记点击：网点标记有独立 click 事件，避免重复触发
        const target = e.originalEvent && e.originalEvent.target
        if (target && (target.classList.contains('branch-icon') || target.closest('.branch-icon'))) return
        const gridData = this.heatmapLayer.getGridAtLatLng(e.latlng)
        if (gridData) this.onGridClick(gridData.gridCode, gridData)
      })

      this.boundaryMgr = new BoundaryManager(this.map)
      this.boundaryMgr.init({ createControl: false })
        .then(() => {
          this.cityBoundaries = this.boundaryMgr.cities || []
          // adcode(520100) → 中文名(贵阳市) 映射
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
    },

    // ==== 工具栏 ====
    async onSelectCity(adcode) {
      if (!adcode) {
        if (this.boundaryMgr) this.boundaryMgr.showAllCities()
        return
      }
      // adcode(520100) → 中文名(贵阳市)，后端 city 列存中文名
      const cityName = this.cityNameMap[adcode]
      if (!cityName) return
      this.currentCity = cityName; this.currentAdcode = +adcode; this.currentFilter = null
      this.closeSidebar()
      if (this.heatmapVisible) { this.heatmapLayer.hide(); this.heatmapVisible = false }
      if (this.boundaryMgr) this.boundaryMgr.showCity(this.currentAdcode)
      await this.loadGridDataCache()
      await this.loadBranches()
      this.loadRanking('grid')
    },
    onSelectDistrict(adcode) {
      if (!this.boundaryMgr) return
      if (!adcode) {
        // "全部区县" → 回到地市视图
        if (this.currentAdcode) this.boundaryMgr.showCity(this.currentAdcode)
        return
      }
      this.boundaryMgr.showDistrict(+adcode)
    },
    async onToggleHeatmap() {
      if (!this.currentCity) return
      if (this.heatmapVisible) { this.heatmapLayer.hide(); this.heatmapVisible = false; return }
      // currentCity 是中文名(贵阳市)，但 heatmap API 通过 cityNameMap 映射
      await this.heatmapLayer.loadData(this.currentCity)
      this.heatmapLayer.show()
      this.heatmapVisible = true
    },
    onFilterBranch(primaryBranch) {
      this.currentFilter = primaryBranch
      this.branchLayer.eachLayer(layer => {
        const match = !primaryBranch || (layer.branchData && layer.branchData.primaryBranch === primaryBranch)
        if (match) { this.map.addLayer(layer) } else { this.map.removeLayer(layer) }
      })
    },
    async onYearChange(year) {
      this.selectedYear = year
      if (this.sidebar.visible && this.sidebar.branchData.branchId) {
        await this.loadBranchScores(this.sidebar.branchData.branchId)
      }
      if (this.ranking.visible) this.loadRanking(this.ranking.type)
    },

    // ==== 网点 ====
    async loadBranches() {
      const res = await getBranchList(this.currentCity)
      this.branchLayer.clearLayers()
      const branches = res.data || []
      const icon = L.divIcon({ className: 'branch-icon', html: '工', iconSize: [24, 24], iconAnchor: [12, 12] })
      for (const b of branches) {
        if (b.longitude == null || b.latitude == null) continue
        const m = L.marker([b.latitude, b.longitude], { icon })
        m.branchData = b
        m.on('click', () => this.onBranchClick(b))
        this.branchLayer.addLayer(m)
      }
    },
    async onBranchClick(branch) {
      this.sidebar.mode = 'branch-only'; this.sidebar.width = 380
      this.sidebar.branchData = branch
      await Promise.all([
        this.loadBranchScores(branch.branchId),
        this.loadBranchRankMeta(branch.branchId),
        this.loadBranchQuadrant(branch),
        this.loadPeerAndNearby(branch.branchId),
        this.loadPillarGap(branch.gridCode)
      ])
      this.sidebar.visible = true
    },
    async loadPillarGap(gridCode) {
      if (!gridCode) { this.sidebar.pillarGap = {}; return }
      try {
        const res = await getPillarGap(gridCode)
        this.sidebar.pillarGap = res.data || {}
      } catch (e) { this.sidebar.pillarGap = {} }
    },
    async loadBranchScores(branchId) {
      try {
        const res = await getBranchScoreDetail(branchId, this.selectedYear)
        this.sidebar.branchScores = res.data || []
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
          // 尝试加载象限数据
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
      try {
        this.sidebar.gridData = data
        const hd = this.heatmapLayer && this.heatmapLayer.getData()
        if (hd && hd.length) {
          const idx = hd.findIndex(d => d.gridCode === gridCode)
          this.sidebar.gridRank = idx >= 0 ? idx + 1 : null
        }
        // 并行加载指标、排名、三聚集
        const [indRes, rankRes, pillarRes] = await Promise.all([
          getGridIndicators(gridCode).catch(() => ({ data: [] })),
          getGridDistrictRanking(gridCode).catch(() => ({ data: null })),
          getGridPillarScores(gridCode).catch(() => ({ data: null }))
        ])
        this.sidebar.gridIndicators = (indRes.data || []).map(d => ({
          code: d.indicatorCode,
          name: this.indicatorNameMap[d.indicatorCode] || d.indicatorCode,
          value: d.indicatorValue,
          categoryLevel1: d.level1Name,
          categoryLevel2: d.level1Name,
          level1Code: d.level1Code,
          level1Name: d.level1Name
        }))
        this.sidebar.gridRankMeta = rankRes.data || { cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0, scoreGap: 0 }
        this.sidebar.pillar = pillarRes.data || {}
        this.loadPillarGap(gridCode)

        const brRes = await getGridBranches(gridCode).catch(() => ({ data: [] }))
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
      this.sidebar.visible = true
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

    // ==== 同业 + 周围网点 ====
    async loadPeerAndNearby(branchId) {
      try {
        const [peerRes, nearbyRes] = await Promise.all([
          getPeerBankDistance(branchId, 1),
          getNearbyBranches(branchId, 1)
        ])
        this.peerBanks = peerRes.data || []
        this.nearbyBranches = nearbyRes.data || []
      } catch (e) {
        this.peerBanks = []
        this.nearbyBranches = []
      }
    },

    // ==== 排名 ====
    loadRanking(type) {
      if (!this.currentCity) return
      this.ranking.type = type || 'grid'
      this.ranking.page = 1; this.ranking.hasMore = false; this.ranking.visible = true
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
          id: r.gridCode || r.branchId,
          name: r.gridCode || r.secondaryBranch || ('网点#' + r.branchId),
          score: r.siteScore || r.categoryScore || 0,
          type: this.ranking.type
        }))
        this.ranking.items = this.ranking.page === 1 ? mapped : [...this.ranking.items, ...mapped]
        this.ranking.hasMore = rows.length >= pageSize
        this.ranking.title = isBranch ? '网点效能排名' : '网格选址排名'
      } catch (e) { if (this.ranking.page === 1) this.ranking.items = [] }
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
      // 加载三聚焦排名数据
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
        this.ranking.loading = false
      } catch (e) {
        this.ranking.items = []
        this.ranking.loading = false
      }
    },
    onRankingItemClick(item) {
      if (!item.id) return
      if (item.type === 'branch') {
        this.branchLayer.eachLayer(layer => {
          if (layer.branchData && layer.branchData.branchId === item.id) {
            this.map.flyTo([layer.branchData.latitude, layer.branchData.longitude], 14)
            this.onBranchClick(layer.branchData)
          }
        })
      } else {
        this.navigateToGrid(item.id)
      }
    },
    async navigateToGrid(gridCode) {
      if (!this.currentCity) return
      try {
        const res = await getGridScoreByCity(this.currentCity)
        const list = res.data || []
        if (!Array.isArray(list)) return
        const found = list.find(d => d.gridCode === gridCode)
        if (found && found.latitude != null && found.longitude != null) {
          this.gridDataCache = list
          this.map.flyTo([found.latitude, found.longitude], 14)
          this.onGridClick(found.gridCode, found)
        }
      } catch (e) { /* ignore */ }
    },
    closeSidebar() { this.sidebar.visible = false; this.detailPanel.visible = false },

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
      // 点击散点 -> 定位到网点
      this.branchLayer.eachLayer(layer => {
        if (layer.branchData && layer.branchData.branchId === item.branchId) {
          this.map.flyTo([layer.branchData.latitude, layer.branchData.longitude], 14)
          this.onBranchClick(layer.branchData)
        }
      })
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
      const sidebarLeft = 12 // SidebarPanel left position
      const sidebarWidth = this.sidebar.width
      this.detailPanel.left = sidebarLeft + sidebarWidth + 8
      this.detailPanel.data = []

      if (type === 'grid' || type === 'all') {
        // 网格指标：计算百分比
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

      if (type === 'branch' || type === 'all') {
        this.detailPanel.data = []
        const branchId = this.sidebar.branchData.branchId
        if (branchId) {
          try {
            const res = await getBranchIndicators(branchId, this.selectedYear)
            const list = res.data || res || []
            this.detailPanel.data = (Array.isArray(list) ? list : []).map(i => ({
              name: this.indicatorNameMap[i.indicatorCode] || i.indicatorCode,
              value: i.indicatorValue,
              categoryLevel1: this.getIndicatorCategory(i.indicatorCode)
            }))
          } catch (e) {
            this.$message.error('加载网点指标数据失败')
          }
        }
        this.detailPanel.mode = 'branch'
        this.detailPanel.visible = true
      }
    },

    getIndicatorCategory(code) {
      if (!code || !this.indicatorParentMap) return '其他'
      // 沿 parentCode 链上溯到一级根节点
      let current = code
      let parentCode = this.indicatorParentMap[current]
      while (parentCode) {
        current = parentCode
        parentCode = this.indicatorParentMap[parentCode]
      }
      return this.indicatorNameMap[current] || current
    },

    // ==== 指标名称映射 ====
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
    }
  }
}
</script>

<style scoped>
.jw-map-container { position: absolute; top: 0; left: 0; right: 0; bottom: 0; overflow: hidden; }
#jwmap-tianditu { width: 100%; height: 100%; }
</style>

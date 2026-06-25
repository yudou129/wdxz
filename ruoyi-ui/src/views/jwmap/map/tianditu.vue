<template>
  <div class="jw-map-container">
    <div id="jwmap-tianditu" ref="mapEl"></div>

    <TopToolbar
      :cities="cityBoundaries"
      :heatmapActive="heatmapVisible"
      :peerBankActive="peerBankVisible"
      :rangeActive="rangeModeActive"
      :rankingActive="ranking.visible"
      :compareActive="compareMode"
      :branchList="branchList"
      :blankSpotActive="blankSpotActive"
      @toggle-blank-spot="onToggleBlankSpot"
      @select-city="onSelectCity"
      @select-district="onSelectDistrict"
      @toggle-heatmap="onToggleHeatmap"
      @toggle-peerbank="onTogglePeerBank"
      @toggle-range="onToggleRangeStats"
      @toggle-quadrant="loadQuadrant"
      @toggle-dim-stats="loadDimStats"
      @filter-branch="onFilterBranch"
      @search-branch="onSearchBranch"
      @toggle-ranking="onToggleRanking"
      @toggle-compare="onToggleCompare"
      @add-compare-branch="onAddCompareBranch"
      :pendingCount="pendingCount"
      @goto-access="goToAccessRequest"
      @goto-approval="goToApproval" />

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

    <RankingList
      :visible="blankSpotRanking.visible"
      :title="'空白点排名'"
      type="grid"
      :items="blankSpotRanking.items"
      :page="blankSpotRanking.page"
      :hasMore="blankSpotRanking.hasMore"
      :loading="blankSpotRanking.loading"
      @item-click="onBlankSpotItemClick"
      @load-more="loadMoreBlankSpot"
      @close="onBlankSpotClose" />

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
      :noAccess="detailPanel.noAccess"
      :branchId="detailPanel.branchId"
      @close="detailPanel.visible = false; detailPanel.noAccess = false"
      @apply-access="navigateToApplyAccess" />

    <RangeStatsPanel
      ref="rangeStats"
      :visible="rangeStats.visible"
      :currentCity="currentCity"
      @close="onCloseRangeStats"
      @locate="onRangeItemLocate"
      @param-change="onRangeParamChange" />

    <ComparisonPanel
      :visible="comparePanel.visible"
      :branches="comparePanel.branches"
      :loading="comparePanel.loading"
      @close="onCompareClose"
      @remove-branch="onCompareRemoveBranch"
      @clear-all="onCompareClearAll" />
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
import RangeStatsPanel from './components/RangeStatsPanel'
import ComparisonPanel from './components/ComparisonPanel'
import { HeatmapLayer } from './utils/heatmapLayer'
import { getGridIndicators, getBranchScoreDetail, getGridBranches,
         getGridRanking, getBranchRanking, getBranchList, getIndicatorList,
         getBranchIndicators, getQuadrantData,
         getBranchInternalRanking, getGridDistrictRanking,
         getGridPillarScores, getDimensionStats,
         getPeerBankDistance, getPeerBankList, getNearbyBranches, getThreeFocusRanking, getPillarGap,
         getGridScoreByCity, getGridTopWithoutBranch } from '@/api/jwmap/data'
import { checkBranchAccess, getPendingCount } from '@/api/jwmap/data-access'
import '@/views/jwmap/map/assets/branch-icon.css'
import '@/views/jwmap/map/assets/peer-bank-icon.css'

// 同业银行品牌色映射表（静态常量）
const PEER_BANK_STYLE_MAP = {
  '建设银行': { css: 'peer-bank-ccb',  text: '建', color: '#1a73e8' },
  '建设银行股份有限公司': { css: 'peer-bank-ccb',  text: '建', color: '#1a73e8' },
  '农业银行': { css: 'peer-bank-abc',  text: '农', color: '#34a853' },
  '中国农业银行': { css: 'peer-bank-abc',  text: '农', color: '#34a853' },
  '中国银行': { css: 'peer-bank-boc',  text: '中', color: '#c41230' },
  '交通银行': { css: 'peer-bank-comm', text: '交', color: '#003366' },
  '招商银行': { css: 'peer-bank-cmb',  text: '招', color: '#d52b1e' },
  '邮储银行': { css: 'peer-bank-psbc', text: '邮', color: '#009a44' },
  '中国邮政储蓄银行': { css: 'peer-bank-psbc', text: '邮', color: '#009a44' },
  '中信银行': { css: 'peer-bank-citic', text: '信', color: '#e60012' },
  '浦发银行': { css: 'peer-bank-spdb', text: '浦', color: '#004098' },
  '民生银行': { css: 'peer-bank-cmbc', text: '民', color: '#2e6db4' },
  '兴业银行': { css: 'peer-bank-cib',  text: '兴', color: '#003399' },
  '光大银行': { css: 'peer-bank-ceb',  text: '光', color: '#7c3aed' },
  '平安银行': { css: 'peer-bank-pab',  text: '平', color: '#f37021' },
  '华夏银行': { css: 'peer-bank-hxb',  text: '华', color: '#dc2626' },
  '贵阳银行': { css: 'peer-bank-gyb',  text: '筑', color: '#1d6fa0' },
  '贵州银行': { css: 'peer-bank-gzb',  text: '黔', color: '#6b8e23' }
}
const PEER_BANK_AUTO_COLORS = [
  '#6366f1', '#ec4899', '#14b8a6', '#f97316',
  '#8b5cf6', '#06b6d4', '#84cc16', '#e11d48'
]

export default {
  name: 'JwMapTianditu',
  components: { TopToolbar, SidebarPanel, RankingList, QuadrantChart, DetailPanel, DimensionStats, PeerBankSection, RangeStatsPanel, ComparisonPanel },
  data() {
    return {
      map: null, boundaryMgr: null, measureTool: null,
      heatmapLayer: null, heatmapVisible: false,
      cityBoundaries: [], branchLayer: null,
      indicatorNameMap: {}, indicatorParentMap: {}, cityNameMap: {},
      peerBankLayer: null, peerBankLegend: null, peerBankNames: [], peerBankVisible: false,
      peerBankAutoIndex: 0, peerBankAutoMap: {},
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
      branchList: [],
      detailPanel: { visible: false, data: [], mode: 'branch', left: 400, branchId: null, noAccess: false },
      rangeStats: { visible: false },
      rangeShapeLayer: null,
      rangeModeActive: false,
      compareMode: false,
      pendingCount: 0,
      comparePanel: {
        visible: false,
        branches: [],
        loading: false
      },
      branchAccess: true, // 当前选中网点的数据访问权限
      blankSpotActive: false,
      blankSpotData: [],
      blankSpotLayer: null,
      blankSpotRanking: { visible: false, items: [], page: 1, hasMore: false, loading: false },
    }
  },
  computed: {
    availableYears() {
      const cur = new Date().getFullYear()
      return [cur - 2, cur - 1, cur]
    }
  },
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
      // 统一地图点击：网格 → 打开侧边栏；空白区域且侧边栏已打开 → 关闭
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
      // 热力图颜色图例（左下角）
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
      // adcode(520100) → 中文名(贵阳市)，后端 city 列存中文名
      const cityName = this.cityNameMap[adcode]
      if (!cityName) return
      this.currentCity = cityName; this.currentAdcode = +adcode; this.currentFilter = null
      this.closeSidebar()
      if (this.heatmapVisible) { this.heatmapLayer.hide(); this.heatmapVisible = false; if (this.heatLegend) this.map.removeControl(this.heatLegend) }
      // 同业银行默认关闭，不添加到地图
      this.peerBankVisible = false
      if (this.boundaryMgr) this.boundaryMgr.showCity(this.currentAdcode)
      await this.loadGridDataCache()
      await this.loadBranches()
      await this.loadPeerBankMarkers()
      this.loadRanking('grid', false) // 预加载排名数据，不显示
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
    onTogglePeerBank() {
      if (!this.peerBankLayer) return
      if (this.peerBankVisible) {
        this.map.removeLayer(this.peerBankLayer)
        this._hidePeerBankLegend()
      } else {
        this.map.addLayer(this.peerBankLayer)
        if (this.peerBankNames.length) this._showPeerBankLegend(this.peerBankNames)
      }
      this.peerBankVisible = !this.peerBankVisible
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
        // 延迟打开侧边栏，等飞行动画完成
        setTimeout(() => this.onBranchClick(branch), 400)
      }
    },
    async onBranchClick(branch) {
      // ==== 对比模式下直接加入对比列表 ====
      if (this.compareMode) {
        this.onAddCompareBranch(branch)
        return
      }
      this.sidebar.mode = 'branch-only'; this.sidebar.width = 380
      this.sidebar.branchData = branch
      this.sidebar.visible = true

      // 权限检查
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

      // 有权限才加载详细数据
      if (this.branchAccess) {
        await Promise.all([
          this.loadBranchScores(branch.branchId),
          this.loadBranchRankMeta(branch.branchId),
          this.loadBranchQuadrant(branch),
          this.loadPeerAndNearby(branch.branchId),
          this.loadPillarGap(branch.gridCode)
        ])
      }
    },
    async loadPillarGap(gridCode) {
      const safeGap = { population: { gap: 0, name: '---' }, enterprise: { gap: 0, name: '---' }, business: { gap: 0, name: '---' } }
      if (!gridCode) { this.sidebar.pillarGap = safeGap; return }
      try {
        const res = await getPillarGap(gridCode)
        const d = res.data || {}
        const gapValues = Object.values(d)  // 按 LinkedHashMap 顺序
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
          .map(s => ({
            ...s,
            categoryName: this.indicatorNameMap[s.scoreCategory] || ''
          }))
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
      // 先立即弹出侧边栏，再加载数据（UI 反馈优先）
      this.sidebar.visible = true
      this.sidebar.gridData = data
      const hd = this.heatmapLayer && this.heatmapLayer.getData()
      if (hd && hd.length) {
        const idx = hd.findIndex(d => d.gridCode === gridCode)
        this.sidebar.gridRank = idx >= 0 ? idx + 1 : null
      }
      // 版本标记防止并发竞态：后续点击不会覆盖前次点击的数据
      const _seq = (this._gridClickSeq || 0) + 1
      this._gridClickSeq = _seq
      try {
        // 并行加载指标、排名、三聚集
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
        // 后端 key 是根指标 code（不固定为 population/enterprise/business），按 LinkedHashMap 顺序取值
        const pillarValues = Object.values(rawPillar)
        const pillarKeys = Object.keys(rawPillar)
        // 计算每个根指标下的叶子指标数量
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

    // ==== 同业银行地标 ====

    getBankStyle(bankName) {
      if (!bankName) return { css: '', text: '?', color: '#999' }
      const name = bankName.trim()
      // 1) 优先精确匹配
      if (PEER_BANK_STYLE_MAP[name]) return PEER_BANK_STYLE_MAP[name]
      // 2) 尝试包含匹配（如 "中国建设银行xx支行" → 匹配 "建设银行"）
      for (const key of Object.keys(PEER_BANK_STYLE_MAP)) {
        if (name.includes(key)) return PEER_BANK_STYLE_MAP[key]
      }
      // 3) 自动分配：取第一个汉字，从色盘轮询
      const firstChar = name.charAt(0)
      if (!this.peerBankAutoMap[name]) {
        this.peerBankAutoMap[name] = PEER_BANK_AUTO_COLORS[this.peerBankAutoIndex % PEER_BANK_AUTO_COLORS.length]
        this.peerBankAutoIndex++
      }
      return { css: '', text: firstChar, color: this.peerBankAutoMap[name], auto: true }
    },

    async loadPeerBankMarkers() {
      if (!this.currentCity) return
      this.peerBankLayer.clearLayers()
      try {
        const res = await getPeerBankList(this.currentCity)
        const list = res.data || []
        if (!list.length) return
        // 收集当前城市中存在的银行名称集合（用于图例）
        const bankNamesInCity = new Set()

        for (const p of list) {
          if (p.longitude == null || p.latitude == null) continue
          const style = this.getBankStyle(p.bankName)
          bankNamesInCity.add(p.bankName || '未知')

          const className = 'peer-bank-icon' + (style.css ? ' ' + style.css : '')
          const icon = L.divIcon({
            className,
            html: style.text,
            iconSize: [24, 24],
            iconAnchor: [12, 12]
          })
          const m = L.marker([p.latitude, p.longitude], { icon })
          // 点击弹窗显示银行详情
          m.bindPopup(this.buildPeerBankPopup(p), { closeButton: true, maxWidth: 280 })
          this.peerBankLayer.addLayer(m)
        }
        // 保存银行名称列表供图例使用
        this.peerBankNames = Array.from(bankNamesInCity)
        // 不主动添加图例，由 onTogglePeerBank 控制显隐
      } catch (e) {
        console.error('[jwmap] 加载同业银行地标失败:', e)
      }
    },

    buildPeerBankPopup(p) {
      const style = this.getBankStyle(p.bankName)
      const color = style.color || '#d40000'
      const name = p.bankName || ''
      return '<div style="font-size:13px;line-height:1.6;min-width:160px">'
        + '<div style="font-weight:700;font-size:14px;margin-bottom:4px;color:' + color + '">'
        + '<span style="display:inline-block;width:18px;height:18px;border-radius:3px;border:2px solid '
        + color + ';text-align:center;font-size:10px;line-height:18px;margin-right:6px">'
        + style.text + '</span>' + (name || '同业银行') + '</div>'
        + (p.orgName ? '<div style="color:#555;font-size:12px">' + p.orgName + '</div>' : '')
        + (p.orgAddress ? '<div style="color:#888;font-size:11px;margin-top:2px">' + p.orgAddress + '</div>' : '')
        + '</div>'
    },

    _showPeerBankLegend(bankNames) {
      // 移除旧图例
      this._hidePeerBankLegend()
      if (!bankNames || bankNames.length === 0) return

      // 按银行名称排序
      const sorted = [...bankNames].sort()

      // 构建图例 HTML
      let itemsHtml = ''
      for (const name of sorted) {
        const style = this.getBankStyle(name)
        const dotColor = style.color || '#d40000'
        itemsHtml += '<div style="display:flex;align-items:center;gap:6px;padding:2px 0">'
          + '<span style="display:inline-flex;width:16px;height:16px;border-radius:3px;'
          + 'border:2px solid ' + dotColor + ';align-items:center;justify-content:center;'
          + 'font-size:8px;font-weight:700;color:' + dotColor + ';flex-shrink:0">'
          + style.text + '</span>'
          + '<span style="font-size:11px;color:#333;white-space:nowrap">' + name + '</span>'
          + '</div>'
      }

      const div = document.createElement('div')
      div.id = 'peer-bank-legend'
      div.style.cssText = 'background:rgba(255,255,255,0.92);border-radius:6px;padding:8px 10px;font-size:11px;box-shadow:0 1px 6px rgba(0,0,0,0.15);max-height:260px;overflow-y:auto'
      div.innerHTML = '<div style="font-weight:600;font-size:11px;color:#333;margin-bottom:4px;border-bottom:1px solid #eee;padding-bottom:4px">同业银行</div>'
        + itemsHtml

      // 追加到 Leaflet 右下角 control 容器
      const bottomRight = this.map._controlCorners && this.map._controlCorners.bottomright
      if (bottomRight) {
        bottomRight.appendChild(div)
        this.peerBankLegend = div
      }
    },

    _hidePeerBankLegend() {
      if (this.peerBankLegend) {
        const el = typeof this.peerBankLegend === 'object' && this.peerBankLegend.nodeType === 1
          ? this.peerBankLegend
          : document.getElementById('peer-bank-legend')
        if (el && el.parentNode) el.parentNode.removeChild(el)
        this.peerBankLegend = null
      }
    },

    // ==== 网点对比 ====
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
    },

    // ==== 网点空白服务点 ====
    async onToggleBlankSpot() {
      if (this.blankSpotActive) {
        this.removeBlankSpotLayer()
        this.blankSpotActive = false
      } else {
        if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
        await this.loadBlankSpotData()
        this.blankSpotActive = true
      }
    },
    async loadBlankSpotData() {
      this.blankSpotRanking.loading = true
      try {
        const res = await getGridTopWithoutBranch(this.currentCity)
        this.blankSpotData = res.data || []
        if (!this.blankSpotData.length) {
          this.$message.info('该城市暂无空白服务点')
          this.blankSpotRanking.loading = false
          return
        }
        // 创建高亮矩形图层
        const group = L.featureGroup()
        for (const item of this.blankSpotData) {
          const bounds = [
            [item.southLatitude, item.westLongitude],
            [item.northLatitude, item.eastLongitude]
          ]
          const rect = L.rectangle(bounds, {
            stroke: false,
            fillColor: '#00bbff',
            fillOpacity: 0.6
          })
          rect.bindTooltip(
            `${item.gridCode}<br>得分: ${(item.siteScore || 0).toFixed(4)}<br>${item.district || ''}`,
            { direction: 'center', className: 'blankspot-tooltip' }
          )
          const gridItem = item
          rect.on('click', () => {
            this.onGridClick(gridItem.gridCode, gridItem)
          })
          group.addLayer(rect)
        }
        this.map.addLayer(group)
        this.blankSpotLayer = group

        // 填充排名列表
        this.blankSpotRanking.items = this.blankSpotData.map(d => ({
          id: d.gridCode,
          name: d.district || d.gridCode,
          score: d.siteScore || 0
        }))
        this.blankSpotRanking.page = 1
        this.blankSpotRanking.hasMore = this.blankSpotData.length > 20
        this.blankSpotRanking.visible = true
      } catch (e) {
        console.error('[jwmap] 加载空白服务点失败:', e)
        this.$message.error('加载空白服务点数据失败')
      }
      this.blankSpotRanking.loading = false
    },
    removeBlankSpotLayer() {
      if (this.blankSpotLayer) {
        this.map.removeLayer(this.blankSpotLayer)
        this.blankSpotLayer = null
      }
      this.blankSpotData = []
      this.blankSpotRanking.visible = false
      this.blankSpotRanking.items = []
    },
    onBlankSpotItemClick(item) {
      const data = this.blankSpotData.find(d => d.gridCode === item.id)
      if (!data) return
      const center = L.latLng(data.latitude, data.longitude)
      this.map.flyTo(center, 13, { duration: 0.6 })
      setTimeout(() => {
        this.onGridClick(data.gridCode, data)
      }, 700)
    },
    onBlankSpotClose() {
      this.removeBlankSpotLayer()
      this.blankSpotActive = false
    },
    loadMoreBlankSpot() {
      // 全部数据已在前端，做前端分页
      const nextPage = this.blankSpotRanking.page + 1
      const start = (nextPage - 1) * 20
      const end = start + 20
      const more = this.blankSpotData.slice(start, end).map(d => ({
        id: d.gridCode,
        name: d.district || d.gridCode,
        score: d.siteScore || 0
      }))
      if (more.length) {
        this.blankSpotRanking.items = this.blankSpotRanking.items.concat(more)
        this.blankSpotRanking.page = nextPage
        this.blankSpotRanking.hasMore = end < this.blankSpotData.length
      } else {
        this.blankSpotRanking.hasMore = false
      }
    },

    // ==== 范围统计 ====
    onToggleRangeStats() {
      if (!this.rangeModeActive && !this.currentCity) {
        this.$message.warning('请先在工具栏选择城市')
        return
      }
      this.rangeModeActive = !this.rangeModeActive
      if (this.rangeModeActive) {
        this.rangeStats.visible = true
        this.map.dragging.disable()
        this.map.getContainer().style.cursor = 'crosshair'
        // 绑定拖拽绘制事件
        this.map.on('mousedown', this.onRangeDrawStart)
        this.map.on('mousemove', this.onRangeDrawMove)
        this.map.on('mouseup', this.onRangeDrawEnd)
        this.$message.info('在地图上按住拖拽划定范围')
      } else {
        this.unbindRangeEvents()
        this.rangeStats.visible = false
        this.clearRangeShape()
        this.map.dragging.enable()
        this.map.getContainer().style.cursor = ''
      }
    },
    onCloseRangeStats() {
      this.rangeModeActive = false
      this.rangeStats.visible = false
      this.unbindRangeEvents()
      this.clearRangeShape()
      this.map.dragging.enable()
      this.map.getContainer().style.cursor = ''
    },
    unbindRangeEvents() {
      this.map.off('mousedown', this.onRangeDrawStart)
      this.map.off('mousemove', this.onRangeDrawMove)
      this.map.off('mouseup', this.onRangeDrawEnd)
      this._drawing = null
    },
    // 拖拽绘制事件
    onRangeDrawStart(e) {
      if (!this.rangeModeActive) return
      this._drawing = { start: e.latlng, tempLayer: null }
    },
    onRangeDrawMove(e) {
      if (!this._drawing || !this._drawing.start) return
      const start = this._drawing.start
      const end = e.latlng
      if (!end) return
      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      const previewStyle = { color: '#409eff', weight: 1, dashArray: '5,5', fillOpacity: 0.05 }
      if (shapeType === 'circle') {
        const r = start.distanceTo(end)
        if (this._drawing.tempLayer && this._drawing.tempLayer.setRadius) {
          this._drawing.tempLayer.setRadius(r)
        } else {
          if (this._drawing.tempLayer) this.map.removeLayer(this._drawing.tempLayer)
          this._drawing.tempLayer = L.circle([start.lat, start.lng], { radius: r, ...previewStyle }).addTo(this.map)
        }
      } else {
        const bounds = L.latLngBounds(start, end)
        if (this._drawing.tempLayer && this._drawing.tempLayer.setBounds) {
          this._drawing.tempLayer.setBounds(bounds)
        } else {
          if (this._drawing.tempLayer) this.map.removeLayer(this._drawing.tempLayer)
          this._drawing.tempLayer = L.rectangle(bounds, previewStyle).addTo(this.map)
        }
      }
    },
    onRangeDrawEnd(e) {
      if (!this._drawing || !this._drawing.start) return
      const start = this._drawing.start
      const end = e.latlng
      // 移除临时预览
      if (this._drawing.tempLayer) { this.map.removeLayer(this._drawing.tempLayer); this._drawing.tempLayer = null }
      this._drawing = null
      // 过滤过小范围
      if (start.distanceTo(end) < 10) return

      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      let centerLat, centerLng, radius

      if (shapeType === 'circle') {
        // 圆形：起点为圆心，终点距离为半径
        centerLat = start.lat; centerLng = start.lng
        radius = Math.round(start.distanceTo(end))
      } else {
        // 方形：两点为对角，取中心点和半边长的最大值
        const bounds = L.latLngBounds(start, end)
        const sw = bounds.getSouthWest(), ne = bounds.getNorthEast()
        centerLat = (sw.lat + ne.lat) / 2
        centerLng = (sw.lng + ne.lng) / 2
        // 以米为单位计算半宽和半高
        const halfWidth = start.distanceTo(L.latLng(start.lat, end.lng)) / 2
        const halfHeight = start.distanceTo(L.latLng(end.lat, start.lng)) / 2
        radius = Math.round(Math.max(halfWidth, halfHeight))
      }

      // 清除旧形状，绘制最终形状
      this.clearRangeShape()
      this.drawRangeShape(centerLat, centerLng, radius, shapeType)

      // 通知面板
      if (this.$refs.rangeStats) {
        this.$refs.rangeStats.setCenter(centerLat, centerLng, radius)
      }
    },
    drawRangeShape(lat, lng, radius, shapeType) {
      if (this.rangeShapeLayer) { this.map.removeLayer(this.rangeShapeLayer); this.rangeShapeLayer = null }
      const style = { color: '#409eff', weight: 2, fillOpacity: 0.1, opacity: 0.7 }
      if (shapeType === 'circle') {
        this.rangeShapeLayer = L.circle([lat, lng], { radius, ...style }).addTo(this.map)
      } else {
        const halfSide = radius
        const latDelta = halfSide / 111320.0
        const lngDelta = halfSide / (111320.0 * Math.cos(lat * Math.PI / 180))
        const bounds = [[lat - latDelta, lng - lngDelta], [lat + latDelta, lng + lngDelta]]
        this.rangeShapeLayer = L.rectangle(bounds, style).addTo(this.map)
      }
    },
    clearRangeShape() {
      if (this.rangeShapeLayer) { this.map.removeLayer(this.rangeShapeLayer); this.rangeShapeLayer = null }
    },
    onRangeItemLocate(latlng) {
      if (latlng && latlng.length === 2) { this.map.flyTo(latlng, 16) }
    },
    onRangeParamChange(params) {
      // 面板参数改变时，如有已划定范围则重绘并重新查询
      if (this.$refs.rangeStats && this.$refs.rangeStats.placed) {
        // 从面板获取当前中心点
        const lat = this.$refs.rangeStats.centerLat
        const lng = this.$refs.rangeStats.centerLng
        if (lat != null && lng != null) {
          this.drawRangeShape(lat, lng, params.radius, params.shapeType)
        }
      }
    },

    onToggleRanking() {
      if (this.ranking.visible) {
        this.ranking.visible = false
      } else if (this.currentCity) {
        this.loadRanking(this.ranking.type || 'grid')
      }
    },

    // ==== 排名 ====
    loadRanking(type, show) {
      if (!this.currentCity) return
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
          await this.onGridClick(found.gridCode, found)
        }
      } catch (e) { console.error('[jwmap] navigateToGrid error:', e) }
    },
    closeSidebar() { this.sidebar.visible = false; this.detailPanel.visible = false; this.detailPanel.noAccess = false },

    // 跳转到数据查看申请页面（携带网点ID，自动回显目标支行）
    navigateToApplyAccess(branchId) {
      this.$router.push({ path: '/jwmap/access-request', query: { branchId } })
    },
    goToAccessRequest() {
      this.$router.push('/jwmap/access-request')
    },
    goToApproval() {
      this.$router.push('/jwmap/access-approval')
    },
    loadPendingCount() {
      getPendingCount().then(res => {
        this.pendingCount = res.data || 0
      }).catch(() => {})
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
      const sidebarLeft = 12
      const sidebarWidth = this.sidebar.width
      this.detailPanel.left = sidebarLeft + sidebarWidth + 8
      this.detailPanel.data = []

      if (type === 'grid' || type === 'all') {
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
    // 获取从根节点到直接父节点的完整路径名数组（根→…→直接父）
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
      // chain 现在是 [root, ..., directParent]
      return chain
    },
    // 获取叶子节点的直接父级分类名（用于指标明细的分组展示）
    getParentCategoryName(code) {
      if (!code || !this.indicatorParentMap) return null
      const parentCode = this.indicatorParentMap[code]
      return parentCode ? (this.indicatorNameMap[parentCode] || parentCode) : null
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
.jw-map-container { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
#jwmap-tianditu { width: 100%; height: 100%; }
</style>

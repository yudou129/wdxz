<template>
  <div class="jw-map-container">
    <div id="jwmap-baidu" ref="mapEl"></div>

    <TopToolbar
      :cities="cityBoundaries"
      :heatmapActive="heatmapVisible"
      :branchActive="branchVisible"
      :peerBankActive="peerBankVisible"
      :rangeActive="rangeModeActive"
      :quadrantActive="quadrant.visible"
      :rankingActive="ranking.visible || branchRanking.visible"
      :compareActive="compareMode"
      :toolActive="measureToolActive"
      :branchList="branchList"
      :blankSpotActive="blankSpotActive"
      :blankSpotLimit="blankSpotLimit"
      @toggle-blank-spot="onToggleBlankSpot"
      @toggle-branch="onToggleBranch"
      @select-city="onSelectCity"
      @select-district="onSelectDistrict"
      @blank-spot-params="onBlankSpotParamsChange"
      @toggle-heatmap="onToggleHeatmap"
      @toggle-peerbank="onTogglePeerBank"
      @toggle-range="onToggleRangeStats"
      @toggle-quadrant="loadQuadrant"
      @toggle-dim-stats="loadDimStats"
      @filter-branch="onFilterBranch"
      @search-branch="onSearchBranch"
      @toggle-ranking="onToggleRankingDropdown"
      @toggle-compare="onToggleCompare"
      @add-compare-branch="onAddCompareBranch"
      :pendingCount="pendingCount"
      :searchTool="searchTool"
      @address-select="onAddressSelect"
      @goto-access="goToAccessRequest"
      @goto-approval="goToApproval"
      @tool-command="onToolCommand" />

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
      :gridBranches="gridBranches"
      :activeGridBranchIdx="activeGridBranchIdx"
      :pillar="sidebar.pillar"
      :peerBanks="peerBanks"
      :nearbyBranches="nearbyBranches"
      :pillarGap="sidebar.pillarGap"
      :nearestBranch="sidebar.nearestBranch"
      :years="availableYears"
      :year="selectedYear"
      @close="closeSidebar"
      @view-detail="showDetailDialog"
      @zoom-branch="zoomToBranch"
      @year-change="onYearChange"
      @switch-branch="onSwitchGridBranch" />

    <RankingList
      :visible="ranking.visible"
      title="网格选址排名"
      type="grid"
      :items="ranking.items"
      :page="ranking.page"
      :hasMore="ranking.hasMore"
      :loading="ranking.loading"
      :showTypeSwitch="false"
      :showFocusTabs="false"
      @item-click="onRankingItemClick"
      @load-more="loadMoreGridRanking"
      @close="ranking.visible = false" />

    <RankingList
      :visible="branchRanking.visible"
      title="网点效能排名"
      type="branch"
      :items="branchRanking.items"
      :page="branchRanking.page"
      :hasMore="branchRanking.hasMore"
      :loading="branchRanking.loading"
      :showYearPicker="true"
      :year="branchRanking.year"
      :years="availableYears"
      :showFocusTabs="false"
      @item-click="onRankingItemClick"
      @load-more="loadMoreBranchRanking"
      @focus-change="onFocusChange"
      @year-change="onBranchRankingYearChange"
      @close="branchRanking.visible = false" />

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
      :year="selectedYear"
      :years="availableYears"
      @close="onQuadrantClose"
      @item-click="onQuadrantItemClick"
      @filter-quadrant="onFilterQuadrant"
      @year-change="onYearChange" />

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
      @param-change="onRangeParamChange"
      @mode-change="onRangeModeChange" />

    <ComparisonPanel
      :visible="comparePanel.visible"
      :branches="comparePanel.branches"
      :loading="comparePanel.loading"
      :year="selectedYear"
      :years="availableYears"
      @close="onCompareClose"
      @remove-branch="onCompareRemoveBranch"
      @clear-all="onCompareClearAll"
      @year-change="onYearChange" />

    <!-- AI 侧边抽屉 -->
    <AiDrawer
      :visible="aiDrawerVisible"
      :active-tab="aiDrawerActiveTab"
      :tabs-data="aiTabsData"
      :showFab="true"
      @close="closeAiDrawer"
      @switch-tab="tab => aiDrawerActiveTab = tab"
      @regenerate="handleAiRegenerate"
      @generate-report="onGenerateSiteReport"
      @jump-to-grid="handleJumpToGrid">
      <template v-slot:fab>
        <AiFabButton
          :inline="true"
          :gridContext="fabGridContext"
          :branchContext="fabBranchContext"
          :comparisonCount="comparePanel.branches.length"
          :aiStates="aiTabsData"
          @select="handleFabSelect" />
      </template>
    </AiDrawer>

    <!-- AI 全局悬浮按钮（AiDrawer 关闭时显示在右下角） -->
    <AiFabButton
      v-if="!aiDrawerVisible"
      :gridContext="fabGridContext"
      :branchContext="fabBranchContext"
      :comparisonCount="comparePanel.branches.length"
      :aiStates="aiTabsData"
      @select="handleFabSelect" />
  </div>
</template>

<script>

import TopToolbar from '../map/components/TopToolbar'
import SidebarPanel from '../map/components/SidebarPanel'
import RankingList from '../map/components/RankingList'
import DetailPanel from '../map/components/DetailPanel'
import QuadrantChart from '../map/components/QuadrantChart'
import DimensionStats from '../map/components/DimensionStats'
import RangeStatsPanel from '../map/components/RangeStatsPanel'
import ComparisonPanel from '../map/components/ComparisonPanel'
import AiDrawer from '../map/components/AiDrawer'
import AiFabButton from '../map/components/AiFabButton'
import useMapLifecycle from './mixins/useMapLifecycle'
import useIndicatorTree from '../shared/mixins/useIndicatorTree'
import useRanking from './mixins/useRanking'
import useBranchComparison from '../shared/mixins/useBranchComparison'
import {
  fetchSSE, getSiteSuggestionStream, getBranchAnalysisStream, getBranchAnalysisCached,
  getBranchComparisonStream, getGridAnalysisStream, getGridAnalysisCached,
  getQuadrantAnalysisStream, getQuadrantAnalysisCached,
  getPerBranchQuadrantStream, getPerBranchQuadrantCached,
  generateSiteReport, downloadReport,
  getRelocationSuggestionStream, getRelocationSuggestionCached
} from '@/api/jwmap/ai'
import { getBranchList } from '@/api/jwmap/data'

// 二级分行 → city 映射
const BRANCH_CITIES = [
  { label: '贵阳分行', city: '贵阳市' },
  { label: '遵义分行', city: '遵义市' },
  { label: '六盘水分行', city: '六盘水市' },
  { label: '安顺分行', city: '安顺市' },
  { label: '毕节分行', city: '毕节市' },
  { label: '铜仁分行', city: '铜仁市' },
  { label: '凯里分行', city: '黔东南州' },
  { label: '都匀分行', city: '黔南州' },
  { label: '兴义分行', city: '黔西南州' },
]

export default {
  name: 'JwMapBaidu',
  components: {
    TopToolbar, SidebarPanel, RankingList, QuadrantChart,
    DetailPanel, DimensionStats, RangeStatsPanel, ComparisonPanel,
    AiDrawer, AiFabButton
  },
  mixins: [useMapLifecycle, useIndicatorTree, useRanking, useBranchComparison],
  data() {
    return {
      map: null, boundaryMgr: null, measureTool: null,
      heatmapOverlays: [], heatmapData: [], heatmapVisible: false,
      branchVisible: true,
      heatLegendEl: null,
      cityBoundaries: [],
      branchMarkers: [],
      indicatorNameMap: {}, indicatorParentMap: {}, cityNameMap: {},
      peerBankMarkers: [], peerBankNames: [], peerBankVisible: false,
      peerBankAutoIndex: 0, peerBankAutoMap: {},
      selectedYear: new Date().getFullYear() - 1,
      sidebar: {
        visible: false, mode: 'grid-only', width: Math.min(380, Math.max(300, window.innerWidth * 0.28)),
        gridData: {}, gridRank: null, gridIndicators: [],
        gridRankMeta: { cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0, scoreGap: 0, topScore: 0, districtTopScore: 0, districtScoreGap: 0 },
        branchData: {}, branchScores: [],
        branchRankMeta: { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 },
        branchQuadrant: '',
        pillar: { population: { score: 0, count: 0 }, enterprise: { score: 0, count: 0 }, business: { score: 0, count: 0 } },
        pillarGap: { population: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' }, enterprise: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' }, business: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' } },
        nearestBranch: null
      },
      ranking: { visible: false, title: '', items: [], page: 1, hasMore: false, loading: false, type: 'grid' },
      branchRanking: { visible: false, title: '', items: [], page: 1, hasMore: false, loading: false, type: 'branch', year: new Date().getFullYear() - 1 },
      quadrant: { visible: false, data: null },
      dimStats: { visible: false, data: [], dimension: 'district' },
      peerBanks: [],
      nearbyBranches: [],
      gridDataCache: null,
      currentCity: null, currentFilter: null, currentAdcode: null, currentDistrict: null,
      branchList: [],
      // 全省全量网点缓存（行政区筛选用）
      allBranchList: [],
      allBranchMarkers: [],
      detailPanel: { visible: false, data: [], mode: 'branch', left: 400, branchId: null, noAccess: false },
      rangeStats: { visible: false },
      rangeShapeOverlay: null,
      rangeModeActive: false,
      compareMode: false,
      pendingCount: 0,
      comparePanel: { visible: false, branches: [], loading: false },
      gridBranches: [],
      activeGridBranchIdx: 0,
      measureToolActive: false,
      branchAccess: true,
      blankSpotActive: false,
      blankSpotLimit: 100,
      blankSpotData: [],
      blankSpotOverlays: [],
      blankSpotRanking: { visible: false, items: [], page: 1, hasMore: false, loading: false },
      highlightOverlays: [],
      // 地址搜索
      searchTool: null,
      // ===== AI 状态 =====
      gridAiState: { loading: false, content: '', error: '', mode: '' },   // mode: 'site' | 'grid'
      branchAiState: { loading: false, content: '', error: '' },
      compareAiState: { loading: false, content: '', error: '' },
      quadrantAiState: { loading: false, content: '', error: '' },
      relocationAiState: { loading: false, content: '', error: '' },
      // ===== AI 侧边抽屉 =====
      aiDrawerVisible: false,
      aiDrawerActiveTab: '',
      /**
       * AI Tab 数据容器
       * key: 'grid' | 'branch' | 'comparison' | 'quadrant'
       *   grid 通过 mode 字段区分 'site'(选址建议)/'grid'(网格分析)
       * value: { loading, content, error, mode, entityKey }
       */
      aiTabsData: {}
    }
  },
  computed: {
    availableYears() {
      const cur = new Date().getFullYear()
      return [cur - 3, cur - 2, cur - 1, cur]
    },
    /**
     * FAB 网格上下文（从 sidebar.gridData 读取）
     */
    fabGridContext() {
      const d = this.sidebar.gridData
      return d && d.gridCode ? {
        gridCode: d.gridCode,
        blankSpot: d.blankSpot
      } : null
    },
    /**
     * FAB 网点上下文（从 sidebar.branchData 读取）
     */
    fabBranchContext() {
      const d = this.sidebar.branchData
      return d && d.branchId ? {
        branchId: d.branchId,
        year: this.selectedYear
      } : null
    }
  },
  methods: {
    // ==================== 城市选择（覆盖 mixin） ====================
    async onSelectCity(val, adcode) {
      // TopToolbar 的 onCityChange emit 而来，val 是 city 字符串，adcode 是地市编码
      if (!val) {
        if (this.boundaryMgr) this.boundaryMgr.showAllCities()
        this.removeAllBranchMarkers()
        this.branchList = []
        return
      }
      this.currentCity = val
      this.currentAdcode = adcode || null
      this.currentFilter = null
      this.closeSidebar()
      if (this.heatmapVisible) { this.removeHeatmapLayer() }
      this.peerBankVisible = false
      this._peerBankLoaded = false
      this.loadGridDataCache()
      await this.loadBranches()
      this.loadGridRanking(false)
      // 预加载区县数据，供区县过滤使用
      if (adcode && this.boundaryMgr) {
        this.boundaryMgr.showCity(adcode)
      }
      this.flyToCity(val)
    },

    // ==================== 飞往目标城市 ====================
    flyToCity(cityName) {
      const BMapGL = window.BMapGL
      if (!BMapGL || !this.map) return
      const ls = new BMapGL.LocalSearch(this.map, {
        renderOptions: { map: this.map, autoViewport: true },
        onSearchComplete: function(results) {
          if (ls.getStatus() === window.BMAP_STATUS_SUCCESS && results.getPoi(0)) {
            const pt = results.getPoi(0).point
            this.map.flyTo(pt, 10, { duration: 0.5 })
          }
        }.bind(this)
      })
      ls.search(cityName)
    },

    /** 并行加载全省所有分行的网点数据 */
    async loadAllBranches() {
      try {
        const promises = BRANCH_CITIES.map(b => getBranchList(b.city).then(r => r.data || []))
        const results = await Promise.all(promises)
        const allBranches = results.flat()
        // 缓存在前端
        this.allBranchList = allBranches
        // 生成全省网点标记并缓存
        this.allBranchMarkers = this.createBranchMarkers(allBranches)
        // 显示到地图
        for (const m of this.allBranchMarkers) this.map.addOverlay(m)
        this.$message.success(`已加载全省 ${allBranches.length} 个网点`)
      } catch (e) {
        console.error('加载全省网点失败:', e)
        this.$message.error('加载全省网点数据失败')
      }
    },
    switchToAllBranches() {
      // 清除当前分行网点标记，显示全省所有网点
      this.branchMarkers.forEach(m => this.map.removeOverlay(m))
      for (const m of this.allBranchMarkers) this.map.addOverlay(m)
    },
    /** 射线法判断点是否在多边形内 */
    pointInPolygon(lng, lat, paths) {
      let inside = false
      for (const ring of paths) {
        for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
          const xi = ring[i][0], yi = ring[i][1]
          const xj = ring[j][0], yj = ring[j][1]
          if ((yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
            inside = !inside
          }
        }
      }
      return inside
    },
    /** 从 GeoJSON feature 提取多边形路径 */
    getPolygonPaths(feature) {
      const paths = []
      const coords = feature.geometry.coordinates
      if (feature.geometry.type === 'Polygon') {
        paths.push(...coords)
      } else if (feature.geometry.type === 'MultiPolygon') {
        for (const poly of coords) paths.push(...poly)
      }
      return paths
    },
    /** 按行政区多边形过滤全省网点 */
    async filterBranchesByDistrict(adcode) {
      // 移除之前由 loadBranches 显示的地市网点标记
      for (const m of this.branchMarkers) {
        try { this.map.removeOverlay(m) } catch(e) {}
      }
      // 懒加载：第一次过滤时才加载全省数据
      if (this.allBranchMarkers.length === 0) {
        await this.loadAllBranches()
      } else {
        // 重新显示全省标记
        for (const m of this.allBranchMarkers) {
          try { this.map.addOverlay(m) } catch(e) {}
        }
      }
      // 遍历 districtCache 所有地市查找区县 feature
      let dFeature = null
      for (const features of Object.values(this.boundaryMgr.districtCache)) {
        dFeature = features.find(f => f.properties.adcode === adcode)
        if (dFeature) break
      }
      if (!dFeature) return
      const paths = this.getPolygonPaths(dFeature)
      // 先隐藏所有全省网点标记
      for (const m of this.allBranchMarkers) this.map.removeOverlay(m)
      // 只显示在边界内的
      let count = 0
      for (const marker of this.allBranchMarkers) {
        const b = marker._branchData
        if (!b.longitude || !b.latitude) continue
        if (this.pointInPolygon(b.longitude, b.latitude, paths)) {
          this.map.addOverlay(marker)
          count++
        }
      }
      // 更新地图边界
      this.boundaryMgr.showDistrict(+adcode)
    },
    removeAllBranchMarkers() {
      const all = [...this.branchMarkers, ...this.allBranchMarkers]
      for (const m of all) this.map.removeOverlay(m)
    },

    /** 排名下拉选择：网格/网点 */
    onToggleRankingDropdown(type) {
      if (type === 'grid') this.loadGridRanking()
      else this.loadBranchRanking()
    },

    /** 工具下拉命令：标点/测距/清除 */
    onToolCommand(cmd) {
      if (!this.measureTool) return
      if (cmd === 'pick') {
        this.measureTool.activatePick()
        this.measureToolActive = this.measureTool.active === 'pick'
      } else if (cmd === 'measure') {
        this.measureTool.activateMeasure()
        this.measureToolActive = this.measureTool.active === 'measure'
      } else if (cmd === 'clear') {
        this.measureTool.clearAll()
        this.measureToolActive = false
      }
    },

    /** 网格侧边栏中切换展示的网点 */
    onSwitchGridBranch(branch, idx) {
      this.switchGridBranch(branch, idx)
    },

    /** 按区县多边形过滤地图上的网点标记 */
    onSelectDistrict(adcode, districtName) {
      if (!this.boundaryMgr) return
      if (!adcode) {
        this.currentDistrict = null
        // 选择"全部区县"——移除全省过滤标记，恢复显示地市网点
        for (const m of this.allBranchMarkers) {
          try { this.map.removeOverlay(m) } catch(e) {}
        }
        for (const m of this.branchMarkers) {
          try { this.map.addOverlay(m) } catch(e) {}
        }
        if (this.currentAdcode) this.boundaryMgr.showCity(this.currentAdcode)
      } else {
        this.currentDistrict = districtName || null
        this.filterBranchesByDistrict(adcode)
      }
      // 联动刷新热力图
      if (this.heatmapVisible) {
        this.removeHeatmapLayer()
        this.loadHeatmapData()
        this.heatmapVisible = true
        if (this.heatLegendEl) this.heatLegendEl.style.display = 'block'
      }
      // 联动刷新空白点
      if (this.blankSpotActive) {
        this.removeBlankSpotLayer()
        this.loadBlankSpotData()
        this.blankSpotActive = true
      }
      // 联动刷新网格排名
      if (this.ranking.visible) {
        this.loadGridRanking(false)
      }
    },

    onAddressSelect(item) {
      // 飞至选中地址并显示标记
      if (this.searchTool && item.point) {
        this.searchTool.clearResultMarkers()
        this.searchTool.showResultMarkers([item])
        this.map.panTo(item.point)
        this.map.setZoom(16)
      }
    },

    // ==================== AI 抽屉方法 ====================

    /**
     * 打开 AI 抽屉并触发指定类型的分析
     * @param {string} tabType - 'grid' | 'branch' | 'comparison' | 'quadrant'
     * @param {boolean} forceRefresh - 是否强制重新生成
     */
    openAiDrawer(tabType, forceRefresh = false) {
      // 初始化 Tab 数据容器（如果不存在）
      if (!this.aiTabsData[tabType]) {
        this.aiTabsData = { ...this.aiTabsData, [tabType]: { loading: false, content: '', error: '', mode: '', entityKey: '' } }
      }

      this.aiDrawerActiveTab = tabType
      this.aiDrawerVisible = true

      // 同步 SidebarPanel 的旧 prop，使按钮 loading/已完成状态更新
      if (tabType === 'grid') {
        this.gridAiState = this.aiTabsData[tabType]
      } else if (tabType === 'branch') {
        this.branchAiState = this.aiTabsData[tabType]
      } else if (tabType === 'relocation') {
        this.relocationAiState = this.aiTabsData[tabType]
      }

      // 根据类型触发对应的加载
      if (tabType === 'grid') {
        this.loadGridAi(forceRefresh)
      } else if (tabType === 'branch') {
        this.loadBranchAi(forceRefresh)
      } else if (tabType === 'comparison') {
        this.loadCompareAi()
      } else if (tabType === 'quadrant') {
        this.loadQuadrantAi(forceRefresh)
      } else if (tabType === 'relocation') {
        this.loadRelocationAi(forceRefresh)
      }
    },

    /**
     * 关闭侧边栏，同时重置所有 AI 按钮状态和标签页数据
     */
    closeSidebar() {
      this.sidebar.visible = false
      this.detailPanel.visible = false
      this.detailPanel.noAccess = false
      if (this.clearHighlight) this.clearHighlight()

      // 重置所有 AI 状态（按钮已完成/loading 标记）
      this.gridAiState = { loading: false, content: '', error: '', mode: '' }
      this.branchAiState = { loading: false, content: '', error: '' }
      this.quadrantAiState = { loading: false, content: '', error: '' }
      this.relocationAiState = { loading: false, content: '', error: '' }
      this.compareAiState = { loading: false, content: '', error: '' }

      // 清空 AI 标签页数据和抽屉（权威数据源同步清除）
      this.aiTabsData = {}
      this.aiDrawerVisible = false
    },

    /**
     * 关闭 AI 抽屉
     */
    closeAiDrawer() {
      this.aiDrawerVisible = false
    },

    /**
     * 统一处理重新生成
     */
    handleAiRegenerate(tabType) {
      this.openAiDrawer(tabType, true)
    },

    /**
     * 从侧边栏打开网格 AI（自动确定选址建议或网格分析）
     */
    openGridAiDrawer(forceRefresh = false) {
      const isBlank = !!(this.sidebar.gridData && this.sidebar.gridData.blankSpot)
      const mode = isBlank ? 'site' : 'grid'

      const gridCode = this.sidebar.gridData?.gridCode
      if (!gridCode) return

      // 如果 mode 变了（例如之前是选址现在切到网格分析），清除旧内容
      const prevMode = (this.aiTabsData.grid || {}).mode
      const prevEntity = (this.aiTabsData.grid || {}).entityKey
      const isNewGrid = prevEntity !== 'grid_' + gridCode

      // 统一使用 'grid' tabType，通过 mode 区分选址/网格分析
      this.aiTabsData = {
        ...this.aiTabsData,
        grid: {
          ...((this.aiTabsData.grid) || {}),
          mode: mode,
          entityKey: 'grid_' + gridCode
        }
      }

      // 如果是新网格，清除旧内容
      if (isNewGrid) {
        this.aiTabsData.grid.content = ''
      }

      this.openAiDrawer('grid', forceRefresh)
    },

    /**
     * 从侧边栏打开网点 AI 诊断
     */
    openBranchAiDrawer(forceRefresh = false) {
      const branchId = this.sidebar.branchData?.branchId
      const year = this.selectedYear
      if (!branchId || !year) return

      this.aiTabsData = {
        ...this.aiTabsData,
        branch: {
          ...((this.aiTabsData.branch) || {}),
          mode: 'branch',
          entityKey: 'branch_' + branchId + '_' + year
        }
      }

      this.openAiDrawer('branch', forceRefresh)
    },

    // ==================== AI 数据加载 ====================

    /**
     * 更新 AI Tab 的某个属性并触发响应式更新
     */
    updateAiTab(tabType, patch) {
      const old = this.aiTabsData[tabType] || {}
      this.$set(this.aiTabsData, tabType, { ...old, ...patch })
      // 同步 SidebarPanel 旧 state prop（用于按钮 loading/已完成状态）
      if (tabType === 'grid') {
        this.gridAiState = this.aiTabsData[tabType]
      } else if (tabType === 'branch') {
        this.branchAiState = this.aiTabsData[tabType]
      } else if (tabType === 'relocation') {
        this.relocationAiState = this.aiTabsData[tabType]
      } else if (tabType === 'quadrant') {
        this.quadrantAiState = this.aiTabsData[tabType]
      }
    },

    /**
     * 加载网格/选址 AI 分析
     */
    loadGridAi(forceRefresh) {
      const gridCode = this.sidebar.gridData?.gridCode
      if (!gridCode) return

      const tab = this.aiTabsData.grid
      if (!tab) return

      const isBlank = tab.mode === 'site' // 通过 mode 区分选址建议还是网格分析
      this.updateAiTab('grid', { loading: true, content: '', error: '' })

      const doStream = () => {
        const url = isBlank
          ? getSiteSuggestionStream(gridCode)
          : getGridAnalysisStream(gridCode, forceRefresh)
        fetchSSE(url,
          (chunk) => {
            const cur = this.aiTabsData.grid
            this.updateAiTab('grid', { content: (cur ? cur.content : '') + chunk })
          },
          () => { this.updateAiTab('grid', { loading: false }) },
          (err) => { this.updateAiTab('grid', { error: err, loading: false }) }
        )
      }

      if (isBlank || forceRefresh) {
        doStream()
      } else {
        getGridAnalysisCached(gridCode).then(res => {
          if (res && res.data && typeof res.data === 'string' && res.data.length > 50) {
            this.$confirm('检测到上次分析结果，是否查看？', '提示', {
              confirmButtonText: '查看上次分析',
              cancelButtonText: '重新生成',
              type: 'info'
            }).then(() => {
              this.updateAiTab('grid', { content: res.data, loading: false })
            }).catch(() => {
              doStream()
            })
            return
          }
          doStream()
        }).catch(() => doStream())
      }
    },

    /**
     * 加载网点 AI 诊断
     */
    loadBranchAi(forceRefresh) {
      const branchId = this.sidebar.branchData?.branchId
      const year = this.selectedYear
      if (!branchId || !year) return

      this.updateAiTab('branch', { loading: true, content: '', error: '' })

      const doStream = () => {
        const url = getBranchAnalysisStream(branchId, year, forceRefresh)
        fetchSSE(url,
          (chunk) => {
            const cur = this.aiTabsData.branch
            this.updateAiTab('branch', { content: (cur ? cur.content : '') + chunk })
          },
          () => { this.updateAiTab('branch', { loading: false }) },
          (err) => { this.updateAiTab('branch', { error: err, loading: false }) }
        )
      }

      if (!forceRefresh) {
        getBranchAnalysisCached(branchId, year).then(res => {
          if (res && res.data && typeof res.data === 'string' && res.data.length > 50) {
            this.$confirm('检测到上次分析结果，是否查看？', '提示', {
              confirmButtonText: '查看上次分析',
              cancelButtonText: '重新生成',
              type: 'info'
            }).then(() => {
              this.updateAiTab('branch', { content: res.data, loading: false })
            }).catch(() => {
              doStream()
            })
            return
          }
          doStream()
        }).catch(() => doStream())
      } else {
        doStream()
      }
    },

    /**
     * 加载对比 AI 分析
     */
    loadCompareAi() {
      const branches = this.comparePanel.branches
      if (!branches || branches.length < 2) return

      this.updateAiTab('comparison', { loading: true, content: '', error: '' })

      const branchIds = branches.map(b => b.branchId)
      const url = getBranchComparisonStream(branchIds, this.currentCity, this.selectedYear)

      fetchSSE(url,
        (chunk) => {
          const cur = this.aiTabsData.comparison
          this.updateAiTab('comparison', { content: (cur ? cur.content : '') + chunk })
        },
        () => { this.updateAiTab('comparison', { loading: false }) },
        (err) => { this.updateAiTab('comparison', { error: err, loading: false }) }
      )
    },

    /**
     * 加载象限 AI 分析
     */
    loadQuadrantAi(forceRefresh) {
      const city = this.currentCity
      const year = this.selectedYear
      if (!city || !year) return

      this.updateAiTab('quadrant', { loading: true, content: '', error: '' })

      const doStream = () => {
        const url = getQuadrantAnalysisStream(city, year, forceRefresh)
        fetchSSE(url,
          (chunk) => {
            const cur = this.aiTabsData.quadrant
            this.updateAiTab('quadrant', { content: (cur ? cur.content : '') + chunk })
          },
          () => { this.updateAiTab('quadrant', { loading: false }) },
          (err) => { this.updateAiTab('quadrant', { error: err, loading: false }) }
        )
      }

      if (!forceRefresh) {
        getQuadrantAnalysisCached(city, year).then(res => {
          if (res && res.data && typeof res.data === 'string' && res.data.length > 50) {
            this.$confirm('检测到上次分析结果，是否查看？', '提示', {
              confirmButtonText: '查看上次分析',
              cancelButtonText: '重新生成',
              type: 'info'
            }).then(() => {
              this.updateAiTab('quadrant', { content: res.data, loading: false })
            }).catch(() => {
              doStream()
            })
            return
          }
          doStream()
        }).catch(() => doStream())
      } else {
        doStream()
      }
    },

    /**
     * 加载单网点四象限深度分析
     */
    loadPerBranchQuadrantAi(forceRefresh = false) {
      const branchId = this.sidebar.branchData?.branchId
      const year = this.selectedYear
      if (!branchId || !year) return

      // 初始化 Tab
      const tabKey = 'quadrant'
      if (!this.aiTabsData[tabKey]) {
        this.aiTabsData = { ...this.aiTabsData, [tabKey]: { loading: false, content: '', error: '', mode: '', entityKey: '' } }
      }

      this.updateAiTab(tabKey, { loading: true, content: '', error: '' })
      this.aiDrawerActiveTab = tabKey
      this.aiDrawerVisible = true

      const doStream = () => {
        const url = getPerBranchQuadrantStream(branchId, year, forceRefresh)
        fetchSSE(url,
          (chunk) => {
            const cur = this.aiTabsData[tabKey]
            this.updateAiTab(tabKey, { content: (cur ? cur.content : '') + chunk })
          },
          () => { this.updateAiTab(tabKey, { loading: false }) },
          (err) => { this.updateAiTab(tabKey, { error: err, loading: false }) }
        )
      }

      if (!forceRefresh) {
        getPerBranchQuadrantCached(branchId, year).then(res => {
          if (res && res.data && typeof res.data === 'string' && res.data.length > 50) {
            this.updateAiTab(tabKey, { content: res.data, loading: false })
            return
          }
          doStream()
        }).catch(() => doStream())
      } else {
        doStream()
      }
    },

    /**
     * 从侧边栏打开迁址建议 AI
     */
    openRelocationAiDrawer(forceRefresh = false) {
      const branchId = this.sidebar.branchData?.branchId
      const year = this.selectedYear
      const city = this.currentCity || this.sidebar.branchData?.city
      if (!branchId || !year || !city) {
        console.warn('[AI迁址] 参数不足', { branchId, year, city })
        this.$message.warning('请先选择城市')
        return
      }

      this.aiTabsData = {
        ...this.aiTabsData,
        relocation: {
          ...((this.aiTabsData.relocation) || {}),
          mode: 'relocation',
          entityKey: 'relocation_' + branchId + '_' + year
        }
      }

      this.openAiDrawer('relocation', forceRefresh)
    },

    /**
     * 加载迁址建议 AI（流式，先查缓存）
     */
    loadRelocationAi(forceRefresh) {
      const branchId = this.sidebar.branchData?.branchId
      const year = this.selectedYear
      const city = this.currentCity || this.sidebar.branchData?.city
      if (!branchId || !year || !city) {
        console.warn('[AI迁址] 加载失败，参数不足', { branchId, year, city })
        return
      }

      this.updateAiTab('relocation', { loading: true, content: '', error: '' })

      const doStream = () => {
        const url = getRelocationSuggestionStream(branchId, year, city, forceRefresh)
        fetchSSE(url,
          (chunk) => {
            const cur = this.aiTabsData.relocation
            this.updateAiTab('relocation', { content: (cur ? cur.content : '') + chunk })
          },
          () => { this.updateAiTab('relocation', { loading: false }) },
          (err) => { this.updateAiTab('relocation', { error: err, loading: false }) },
          (replaceAll) => { this.updateAiTab('relocation', { content: replaceAll }) }
        )
      }

      if (!forceRefresh) {
        getRelocationSuggestionCached(branchId, year).then(res => {
          if (res && res.data && typeof res.data === 'string' && res.data.length > 50) {
            this.$confirm('检测到上次分析结果，是否查看？', '提示', {
              confirmButtonText: '查看上次分析',
              cancelButtonText: '重新生成',
              type: 'info'
            }).then(() => {
              this.updateAiTab('relocation', { content: res.data, loading: false })
            }).catch(() => {
              doStream()
            })
            return
          }
          doStream()
        }).catch(() => doStream())
      } else {
        doStream()
      }
    },


    /**
     * 生成并下载选址报告
     */
    async onGenerateSiteReport(gridCode) {
      if (!gridCode) return
      try {
        this.$message.info('正在生成选址报告...')
        const res = await generateSiteReport(gridCode)
        if (res && res.data) {
          const reportId = res.data
          // 用隐藏 <a> 触发下载（避免浏览器拦截 popup）
          const link = document.createElement('a')
          link.href = '/dev-api/jwmap/ai/report/download/' + reportId
          link.target = '_blank'
          link.style.display = 'none'
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
          this.$message.success('报告生成成功')
        } else {
          this.$message.error('报告生成失败')
        }
      } catch (e) {
        this.$message.error('报告生成异常: ' + (e.message || ''))
      }
    },

    /**
     * 从 AI 分析结果跳转到指定网格
     */
    handleJumpToGrid(gridCode) {
      if (!gridCode) return
      // 从 heatmapData 查找网格数据
      const data = this.heatmapData && this.heatmapData.find(d => d.gridCode === gridCode)
      if (data) {
        this.onGridClick(gridCode, data)
        // 地图 flyTo 定位
        const BMapGL = window.BMapGL
        if (BMapGL && this.map && data.longitude != null && data.latitude != null) {
          this.map.flyTo(new BMapGL.Point(data.longitude, data.latitude), 13, { duration: 0.5 })
        }
      } else {
        this.$message.info('网格数据未加载，请先加载热力图')
      }
    },

    // ==================== AI 悬浮按钮 ====================

    /**
     * 处理 AI 悬浮按钮选择事件
     */
    handleFabSelect(type) {
      // 始终打开 AiDrawer，让用户看到交互反馈
      // 初始化 tab（loading 状态填充骨架屏）
      this.aiTabsData = {
        ...this.aiTabsData,
        [type]: { loading: true, content: '', error: '', mode: '', entityKey: '' }
      }
      this.aiDrawerActiveTab = type
      this.aiDrawerVisible = true

      // 检查上下文数据是否存在，不存在则在 drawer 内显示引导提示
      if (type === 'grid' && !this.sidebar.gridData?.gridCode) {
        this.updateAiTab('grid', { error: '请先在地图上点击选择一个网格，再使用 AI 分析功能', loading: false })
        return
      }
      if ((type === 'branch' || type === 'quadrant' || type === 'relocation') && !this.sidebar.branchData?.branchId) {
        const errorTab = type === 'branch' ? 'branch' : type
        this.updateAiTab(errorTab, { error: '请先在地图上点击选择一个网点，再使用 AI 分析功能', loading: false })
        return
      }
      if (type === 'comparison' && (!this.comparePanel.branches || this.comparePanel.branches.length < 2)) {
        this.updateAiTab('comparison', { error: '请在对比面板中选择至少 2 个网点，再使用 AI 对比分析', loading: false })
        return
      }

      // 直接发起 SSE 流式分析
      if (type === 'grid') {
        const gridCode = this.sidebar.gridData.gridCode
        const isBlank = !!this.sidebar.gridData.blankSpot
        const mode = isBlank ? 'site' : 'grid'
        this.aiTabsData.grid.mode = mode
        this.aiTabsData.grid.entityKey = 'grid_' + gridCode
        const url = isBlank
          ? getSiteSuggestionStream(gridCode)
          : getGridAnalysisStream(gridCode, true)
        const self = this
        fetchSSE(url,
          (chunk) => { self.updateAiTab('grid', { content: (self.aiTabsData.grid ? self.aiTabsData.grid.content : '') + chunk }) },
          () => { self.updateAiTab('grid', { loading: false }) },
          (err) => { self.updateAiTab('grid', { error: err, loading: false }) }
        )
      } else if (type === 'branch') {
        const branchId = this.sidebar.branchData.branchId
        const year = this.selectedYear
        this.aiTabsData.branch.entityKey = 'branch_' + branchId + '_' + year
        const url = getBranchAnalysisStream(branchId, year, true)
        const self = this
        fetchSSE(url,
          (chunk) => { self.updateAiTab('branch', { content: (self.aiTabsData.branch ? self.aiTabsData.branch.content : '') + chunk }) },
          () => { self.updateAiTab('branch', { loading: false }) },
          (err) => { self.updateAiTab('branch', { error: err, loading: false }) }
        )
      } else if (type === 'comparison') {
        this.loadCompareAi()
      } else if (type === 'quadrant') {
        this.loadPerBranchQuadrantAi()
      } else if (type === 'relocation') {
        this.loadRelocationAi()
      }
    }
  }
}
</script>

<style scoped>
.jw-map-container { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
#jwmap-baidu { width: 100%; height: 100%; }
</style>

<style>
/* 边界控制样式 — 复用 Leaflet 版的同类定义 */
.boundary-control {
  position: absolute;
  top: 10px;
  left: 12px;
  z-index: 1000;
  background: rgba(255,255,255,0.95);
  border-radius: 6px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.18);
  padding: 4px 6px;
}
.boundary-select {
  font-size: 13px;
  padding: 4px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  outline: none;
  min-width: 130px;
  cursor: pointer;
  background: #fff;
  color: #333;
}
.boundary-select:focus { border-color: #409eff; }
.boundary-label {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  font-size: 13px !important;
  font-weight: 600 !important;
  color: #444 !important;
}

/* 测距标签样式（BMapMeasureTool 生成的覆盖物） */
.measure-label { background: transparent !important; border: none !important; }
.measure-label span {
  background: rgba(255,255,255,0.85);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
  font-family: monospace;
  color: #2980b9;
  border: 1px solid #2980b9;
}
.measure-label.total span {
  color: #e74c3c;
  border-color: #e74c3c;
  font-weight: bold;
}

/* BMapGL 标点标签样式重置 */
.pick-marker-icon { background: none !important; border: none !important; }

</style>

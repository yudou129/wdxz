<template>
  <div class="jw-map-container">
    <div id="jwmap-baidu" ref="mapEl"></div>

    <TopToolbar
      :cities="cityBoundaries"
      :heatmapActive="heatmapVisible"
      :peerBankActive="peerBankVisible"
      :rangeActive="rangeModeActive"
      :rankingActive="ranking.visible"
      :compareActive="compareMode"
      :branchList="branchList"
      :blankSpotActive="blankSpotActive"
      :blankSpotLimit="blankSpotLimit"
      @toggle-blank-spot="onToggleBlankSpot"
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
      @toggle-ranking="onToggleRanking"
      @toggle-compare="onToggleCompare"
      @add-compare-branch="onAddCompareBranch"
      :pendingCount="pendingCount"
      :searchTool="searchTool"
      @address-select="onAddressSelect"
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
      :nearestBranch="sidebar.nearestBranch"
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
      @close="onQuadrantClose"
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
      @param-change="onRangeParamChange"
      @mode-change="onRangeModeChange" />

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

import TopToolbar from '../map/components/TopToolbar'
import SidebarPanel from '../map/components/SidebarPanel'
import RankingList from '../map/components/RankingList'
import DetailPanel from '../map/components/DetailPanel'
import QuadrantChart from '../map/components/QuadrantChart'
import DimensionStats from '../map/components/DimensionStats'
import RangeStatsPanel from '../map/components/RangeStatsPanel'
import ComparisonPanel from '../map/components/ComparisonPanel'
import useMapLifecycle from './mixins/useMapLifecycle'
import useIndicatorTree from '../shared/mixins/useIndicatorTree'
import useRanking from './mixins/useRanking'
import useBranchComparison from '../shared/mixins/useBranchComparison'

export default {
  name: 'JwMapBaidu',
  components: {
    TopToolbar, SidebarPanel, RankingList, QuadrantChart,
    DetailPanel, DimensionStats, RangeStatsPanel, ComparisonPanel
  },
  mixins: [useMapLifecycle, useIndicatorTree, useRanking, useBranchComparison],
  data() {
    return {
      map: null, boundaryMgr: null, measureTool: null,
      heatmapOverlays: [], heatmapData: [], heatmapVisible: false,
      heatLegendEl: null,
      cityBoundaries: [],
      branchMarkers: [],
      indicatorNameMap: {}, indicatorParentMap: {}, cityNameMap: {},
      peerBankMarkers: [], peerBankNames: [], peerBankVisible: false,
      peerBankAutoIndex: 0, peerBankAutoMap: {},
      selectedYear: 2024,
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
      quadrant: { visible: false, data: null },
      dimStats: { visible: false, data: [], dimension: 'district' },
      peerBanks: [],
      nearbyBranches: [],
      gridDataCache: null,
      currentCity: null, currentFilter: null, currentAdcode: null, currentDistrict: 'all',
      branchList: [],
      detailPanel: { visible: false, data: [], mode: 'branch', left: 400, branchId: null, noAccess: false },
      rangeStats: { visible: false },
      rangeShapeOverlay: null,
      rangeModeActive: false,
      compareMode: false,
      pendingCount: 0,
      comparePanel: { visible: false, branches: [], loading: false },
      branchAccess: true,
      blankSpotActive: false,
      blankSpotLimit: 100,
      blankSpotData: [],
      blankSpotOverlays: [],
      blankSpotRanking: { visible: false, items: [], page: 1, hasMore: false, loading: false },
      highlightOverlays: [],
      // 地址搜索
      searchTool: null
    }
  },
  computed: {
    availableYears() {
      const cur = new Date().getFullYear()
      return [cur - 2, cur - 1, cur]
    }
  },
  methods: {
    onAddressSelect(item) {
      // 飞至选中地址并显示标记
      if (this.searchTool && item.point) {
        this.searchTool.clearResultMarkers()
        this.searchTool.showResultMarkers([item])
        this.map.panTo(item.point)
        this.map.setZoom(16)
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

/* 测距控制样式 */
.measure-control {
  position: absolute;
  bottom: 30px;
  left: 12px;
  z-index: 1000;
  display: flex;
  gap: 4px;
  background: rgba(255,255,255,0.95);
  border-radius: 6px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.18);
  padding: 5px 8px;
}
.measure-btn {
  padding: 5px 10px;
  font-size: 13px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  color: #333;
  transition: all 0.15s;
  white-space: nowrap;
}
.measure-btn:hover { background: #f0f5ff; border-color: #409eff; color: #409eff; }
.measure-btn.active { background: #409eff; border-color: #409eff; color: #fff; }
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

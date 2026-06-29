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
import TopToolbar from './components/TopToolbar'
import SidebarPanel from './components/SidebarPanel'
import RankingList from './components/RankingList'
import DetailPanel from './components/DetailPanel'
import QuadrantChart from './components/QuadrantChart'
import DimensionStats from './components/DimensionStats'
import RangeStatsPanel from './components/RangeStatsPanel'
import ComparisonPanel from './components/ComparisonPanel'
import useMapLifecycle from './mixins/useMapLifecycle'
import usePeerBanks from './mixins/usePeerBanks'
import useIndicatorTree from './mixins/useIndicatorTree'
import useBlankSpots from './mixins/useBlankSpots'
import useHighlight from './mixins/useHighlight'
import useRangeStats from './mixins/useRangeStats'
import useRanking from './mixins/useRanking'
import useBranchComparison from './mixins/useBranchComparison'

export default {
  name: 'JwMapTianditu',
  components: { TopToolbar, SidebarPanel, RankingList, QuadrantChart, DetailPanel, DimensionStats, RangeStatsPanel, ComparisonPanel },
  mixins: [useMapLifecycle, usePeerBanks, useIndicatorTree, useBlankSpots, useRangeStats, useRanking, useBranchComparison, useHighlight],
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
  }
</script>

<style scoped>
.jw-map-container { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
#jwmap-tianditu { width: 100%; height: 100%; }
</style>

<style>
/* 高亮网格 tooltip — 网格编码居中显示 */
.highlight-grid-tooltip {
  background: rgba(255, 215, 0, 0.85) !important;
  color: #8B6914 !important;
  font-size: 13px !important;
  font-weight: 700 !important;
  padding: 2px 8px !important;
  border: 1px solid #FFD700 !important;
  border-radius: 3px !important;
  box-shadow: 0 1px 4px rgba(0,0,0,0.25) !important;
  white-space: nowrap !important;
}
.highlight-grid-tooltip::before {
  display: none !important;
}

/* 高亮网点 tooltip — 网点名称在标记上方 */
.highlight-branch-tooltip {
  background: rgba(255, 215, 0, 0.9) !important;
  color: #8B6914 !important;
  font-size: 13px !important;
  font-weight: 600 !important;
  padding: 3px 10px !important;
  border: 1px solid #FFD700 !important;
  border-radius: 4px !important;
  box-shadow: 0 1px 6px rgba(0,0,0,0.3) !important;
  white-space: nowrap !important;
}
.highlight-branch-tooltip::before {
  display: none !important;
}

/* 空白服务点 tooltip */
.blankspot-tooltip {
  font-size: 13px !important;
  color: #333 !important;
  font-weight: 500 !important;
  line-height: 1.5 !important;
}
</style>

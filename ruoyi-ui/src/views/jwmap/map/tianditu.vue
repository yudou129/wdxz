<template>
  <div class="jw-map-container">
    <div id="jwmap-tianditu" ref="mapEl"></div>

    <TopToolbar
      :cities="cityBoundaries"
      :heatmapActive="heatmapVisible"
      @select-city="onSelectCity"
      @select-district="onSelectDistrict"
      @toggle-heatmap="onToggleHeatmap"
      @filter-branch="onFilterBranch" />

    <SidebarPanel
      :visible="sidebar.visible"
      :mode="sidebar.mode"
      :width="sidebar.width"
      :gridData="sidebar.gridData"
      :gridRank="sidebar.gridRank"
      :gridIndicators="sidebar.gridIndicators"
      :branchData="sidebar.branchData"
      :branchScores="sidebar.branchScores"
      :years="availableYears"
      :year="selectedYear"
      @close="closeSidebar"
      @view-detail="showDetailDialog"
      @year-change="onYearChange" />

    <RankingList
      :visible="ranking.visible"
      :title="ranking.title"
      :type="ranking.type"
      :items="ranking.items"
      :page="ranking.page"
      :hasMore="ranking.hasMore"
      :loading="ranking.loading"
      @item-click="onRankingItemClick"
      @load-more="loadMoreRanking"
      @close="ranking.visible = false" />

    <el-dialog title="详细指标数据表" :visible.sync="detailDialog.visible" width="70%" top="5vh">
      <el-table :data="detailDialog.data" border size="small" max-height="60vh" style="width:100%">
        <el-table-column prop="name" label="指标名称" min-width="160" />
        <el-table-column prop="value" label="数值" width="120" />
      </el-table>
    </el-dialog>
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
import { HeatmapLayer } from './utils/heatmapLayer'
import { getGridIndicators, getBranchScoreDetail, getGridBranches,
         getGridRanking, getBranchRanking, getBranchList, getIndicatorList,
         getBranchIndicators } from '@/api/jwmap/data'
import '@/views/jwmap/map/assets/branch-icon.css'

export default {
  name: 'JwMapTianditu',
  components: { TopToolbar, SidebarPanel, RankingList },
  data() {
    return {
      map: null, boundaryMgr: null, measureTool: null,
      heatmapLayer: null, heatmapVisible: false,
      cityBoundaries: [], branchLayer: null,
      indicatorNameMap: {}, cityNameMap: {},
      selectedYear: 2024,
      sidebar: {
        visible: false, mode: 'grid-only', width: 380,
        gridData: {}, gridRank: null, gridIndicators: [],
        branchData: {}, branchScores: []
      },
      ranking: { visible: false, title: '', items: [], page: 1, hasMore: false, loading: false, type: 'grid' },
      currentCity: null, currentFilter: null, currentAdcode: null,
      detailDialog: { visible: false, data: [] }
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
      this.map.on('grid-click', (e) => this.onGridClick(e.gridCode, e.data))

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
      await this.loadBranchScores(branch.branchId)
      this.sidebar.visible = true
    },
    async loadBranchScores(branchId) {
      const res = await getBranchScoreDetail(branchId, this.selectedYear)
      this.sidebar.branchScores = res.data || []
    },

    // ==== 网格点击 ====
    async onGridClick(gridCode, data) {
      this.sidebar.gridData = data
      const hd = this.heatmapLayer && this.heatmapLayer.getData()
      if (hd && hd.length) {
        const idx = hd.findIndex(d => d.gridCode === gridCode)
        this.sidebar.gridRank = idx >= 0 ? idx + 1 : null
      }
      const indRes = await getGridIndicators(gridCode)
      this.sidebar.gridIndicators = (indRes.data || []).map(d => ({
        code: d.indicatorCode,
        name: this.indicatorNameMap[d.indicatorCode] || d.indicatorCode,
        value: d.indicatorValue,
        categoryLevel1: d.categoryLevel1,
        categoryLevel2: d.categoryLevel2
      }))
      const brRes = await getGridBranches(gridCode)
      const branches = brRes.data || []
      if (branches.length > 0) {
        this.sidebar.mode = 'split'; this.sidebar.width = 600
        this.sidebar.branchData = branches[0]
        await this.loadBranchScores(branches[0].branchId)
      } else {
        this.sidebar.mode = 'grid-only'; this.sidebar.width = 380
      }
      this.sidebar.visible = true
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
    onRankingItemClick(item) {
      if (!item.id) return
      if (item.type === 'branch') {
        // 网点排名: 在 branchLayer 中查找并定位
        this.branchLayer.eachLayer(layer => {
          if (layer.branchData && layer.branchData.branchId === item.id) {
            this.map.flyTo([layer.branchData.latitude, layer.branchData.longitude], 14)
            this.onBranchClick(layer.branchData)
          }
        })
      } else {
        // 网格排名: 在 heatmap 数据中查找
        if (!this.heatmapLayer) return
        const hd = this.heatmapLayer.getData()
        if (!hd) return
        const found = hd.find(d => d.gridCode === item.id)
        if (found) { this.map.flyTo([found.latitude, found.longitude], 14); this.onGridClick(found.gridCode, found) }
      }
    },
    closeSidebar() { this.sidebar.visible = false },
    async showDetailDialog(type) {
      let data = []
      if (type === 'grid' || type === 'all') {
        data = data.concat(this.sidebar.gridIndicators.map(i => ({ name: i.name, value: i.value })))
      }
      if (type === 'branch' || type === 'all') {
        const branchId = this.sidebar.branchData.branchId
        if (branchId) {
          try {
            const res = await getBranchIndicators(branchId, this.selectedYear)
            const indicators = res.data || []
            data = data.concat(indicators.map(i => ({
              name: this.indicatorNameMap[i.indicatorCode] || i.indicatorCode,
              value: i.indicatorValue
            })))
          } catch (e) { /* 衍生指标加载失败 */ }
        }
        // 也包含分项得分
        data = data.concat(this.sidebar.branchScores.map(s => ({
          name: s.scoreCategory + '得分',
          value: s.categoryScore.toFixed(4)
        })))
      }
      this.detailDialog.data = data; this.detailDialog.visible = true
    },

    // ==== 指标名称映射 ====
    async loadIndicatorNames() {
      try {
        const [gridRes, branchRes] = await Promise.all([
          getIndicatorList('jw_grid_data_raw'),
          getIndicatorList('jw_branch_indicator')
        ])
        const map = {}
        for (const item of [...(gridRes.data || []), ...(branchRes.data || [])]) {
          if (item.indicatorCode) map[item.indicatorCode] = item.indicatorName || item.indicatorCode
        }
        this.indicatorNameMap = map
      } catch (e) { /* 名称映射加载失败，回退显示编码 */ }
    }
  }
}
</script>

<style scoped>
.jw-map-container { position: absolute; top: 0; left: 0; right: 0; bottom: 0; overflow: hidden; }
#jwmap-tianditu { width: 100%; height: 100%; }
</style>

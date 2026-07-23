/**
 * 百度地图 GL 版地图生命周期 mixin
 *
 * 功能：地图初始化 + 工具栏 + 网点/网格点击编排 + 同行标记 + 空白点 + 高亮 + 范围统计
 * 不依赖 Leaflet，所有地图交互使用 BMapGL API
 */
import { loadBMapGL } from '../utils/sdkLoader'
import { BMapBoundaryManager } from '../utils/bMapBoundaryManager'
import { BMapMeasureTool } from '../utils/bMapMeasureTool'
import { BMapSearchTool } from '../utils/bMapSearchTool'
import { getGridScoreByCity, getBranchList, getGridIndicators, getGridBranches,
         getGridDistrictRanking, getGridPillarScores, getBranchScoreDetail,
         getBranchInternalRanking, getBranchTopScores, getQuadrantData, getDimensionStats,
         getPillarGap, getBranchIndicators, getGridNearestBranch, getGridTopWithoutBranch,
         getPeerBankList, getPeerBankDistance, getNearbyBranches } from '@/api/jwmap/data'
import { checkBranchAccess, getPendingCount } from '@/api/jwmap/data-access'
import '@/views/jwmap/shared/assets/branch-icon.css'
import { getBankSvgUrl, getOwnBankSvgUrl } from '@/views/jwmap/shared/utils/bankSvgMap'

// --- 同行银行样式（从 usePeerBanks 复制，无 Leaflet 依赖）---
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
  mounted() {
    this.$nextTick(() => this.initMap())
    this.loadPendingCount()
    this.$root.$on('pending-count-changed', this.loadPendingCount)
  },

  beforeDestroy() {
    if (this.measureTool) { this.measureTool.destroy() }
    this.unbindRangeEvents()

    // 清理覆盖物
    if (this.branchMarkers && this.branchMarkers.length) {
      this.branchMarkers.forEach(m => { try { this.map.removeOverlay(m) } catch(e) {} })
    }
    if (this.peerBankMarkers && this.peerBankMarkers.length) {
      this.peerBankMarkers.forEach(m => { try { this.map.removeOverlay(m) } catch(e) {} })
    }
    this.removeBlankSpotLayer()
    if (this.highlightOverlays && this.highlightOverlays.length) {
      this.highlightOverlays.forEach(o => { try { this.map.removeOverlay(o) } catch(e) {} })
    }

    this.$root.$off('pending-count-changed', this.loadPendingCount)

    if (this.map) {
      // 移除所有事件监听
      try { this.map.removeEventListener('click', this._onMapClick) } catch(e) {}
      try { this.map.removeEventListener('zoomend', this._onZoomEnd) } catch(e) {}
    }
  },

  methods: {
    // ======== 地图初始化 ========
    async initMap() {
      const BMapGL = await loadBMapGL()

      // 1. 创建地图实例
      this.map = new BMapGL.Map(this.$refs.mapEl, {
        enableHighResolution: true,
        enableAutoResize: true
      })
      this.map.centerAndZoom(new BMapGL.Point(106.7238, 26.5807), 10)
      this.map.setMinZoom(9)
      this.map.setMaxZoom(17)
      this.map.enableScrollWheelZoom(true)

      // 2. 添加导航控件
      this.map.addControl(new BMapGL.NavigationControl({
        anchor: BMapGL.ANCHOR_TOP_RIGHT,
        type: BMapGL.NAVIGATION_CONTROL_LARGE
      }))

      // 3. 地图点击事件
      this.map.addEventListener('click', this._onMapClick)

      // 部分操作需要在 zoomend 后重置
      this.map.addEventListener('zoomend', this._onZoomEnd)

      // 4. 初始化子系统
      this.branchMarkers = []
      this.peerBankMarkers = []

      // 5. 边界管理器
      this.boundaryMgr = new BMapBoundaryManager(this.map)
      await this.boundaryMgr.init({ createControl: false }).then(() => {
        this.cityBoundaries = this.boundaryMgr.cities || []
        const map = {}
        for (const c of this.cityBoundaries) {
          if (c.properties && c.properties.adcode != null) map[c.properties.adcode] = c.properties.name
        }
        this.cityNameMap = map
      }).catch(err => console.error('[bmap] 边界加载失败:', err))

      // 6. 测距工具
      this.measureTool = new BMapMeasureTool(this.map)

      // 7. 地址搜索工具
      this.searchTool = new BMapSearchTool(this.map)

      // 8. 加载指标名称
      this.loadIndicatorNames()

      // 9. 热度图图例
      this._createHeatLegend()
    },

    _onMapClick(e) {
      if (!this.heatmapVisible) return
      const target = e.originalEvent && e.originalEvent.target
      if (target && (target.classList.contains('branch-icon') || target.closest('.branch-icon'))) return

      // 从 heatmapData 中查找被点击的网格
      const lat = e.latlng.lat
      const lng = e.latlng.lng
      const gridData = this.heatmapData && this.heatmapData.find(item =>
        item.siteScore != null &&
        item.southLatitude <= lat && lat <= item.northLatitude &&
        item.westLongitude <= lng && lng <= item.eastLongitude
      )
      if (gridData) {
        this.onGridClick(gridData.gridCode, gridData)
      } else if (this.sidebar.visible) {
        this.closeSidebar()
      }
    },

    _onZoomEnd() {
      // zoom 变化后重置一些状态
    },

    _createHeatLegend() {
      // 创建热度图图例 DOM
      this.heatLegendEl = document.createElement('div')
      this.heatLegendEl.style.cssText = 'display:none;background:rgba(255,255,255,0.9);border-radius:6px;padding:8px 10px;font-size:11px;box-shadow:0 1px 6px rgba(0,0,0,0.15);position:absolute;bottom:40px;left:10px;z-index:1000;pointer-events:none'
      this.heatLegendEl.innerHTML = '<div style="display:flex;align-items:center;gap:6px;margin-bottom:2px">'
        + '<span style="color:#333;font-weight:600;font-size:13px">选址得分</span></div>'
        + '<div style="display:flex;align-items:stretch;gap:4px">'
        + '<span style="font-size:13px;color:#555;line-height:1.2">低</span>'
        + '<div style="width:120px;height:14px;border-radius:3px;background:linear-gradient(90deg,#00ff00,#ffff00,#ff0000)"></div>'
        + '<span style="font-size:13px;color:#555;line-height:1.2">高</span>'
        + '</div>'
      const container = this.map.getContainer()
      container.appendChild(this.heatLegendEl)
    },

    // ======== 工具栏 ========
    async onSelectCity(adcode) {
      if (!adcode) {
        if (this.boundaryMgr) this.boundaryMgr.showAllCities()
        return
      }
      const cityName = this.cityNameMap[adcode]
      if (!cityName) return
      this.currentCity = cityName
      this.currentAdcode = +adcode
      this.currentFilter = null
      this.closeSidebar()
      if (this.heatmapVisible) {
        this.removeHeatmapLayer()
      }
      this.peerBankVisible = false
      this._peerBankLoaded = false
      if (this.boundaryMgr) this.boundaryMgr.showCity(this.currentAdcode)
      await this.loadGridDataCache()
      await this.loadBranches()
      this.loadGridRanking(false)
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
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      if (this.heatmapVisible) {
        this.removeHeatmapLayer()
        return
      }
      await this.loadHeatmapData()
      this.heatmapVisible = true
      if (this.heatLegendEl) this.heatLegendEl.style.display = 'block'
    },

    async loadHeatmapData() {
      try {
        const district = this.currentDistrict || null
        const res = await getGridScoreByCity(this.currentCity, district)
        this.heatmapData = res.data || []
        if (!this.heatmapData.length) {
          this.$message.info('该城市暂无网格评分数据')
          return
        }
        const scores = this.heatmapData.map(d => d.siteScore).filter(s => s != null)
        const minScore = scores.length ? Math.min(...scores) : 0
        const maxScore = scores.length ? Math.max(...scores) : 1
        const range = maxScore - minScore || 1

        const BMapGL = window.BMapGL
        if (!BMapGL) return

        this.heatmapOverlays = []

        for (const item of this.heatmapData) {
          const t = Math.max(0, Math.min(1, ((item.siteScore || 0) - minScore) / range))
          const color = t < 0.5
            ? `rgba(${Math.round(255 * t / 0.5)}, 255, 0, 0.55)`
            : `rgba(255, ${Math.round(255 * (1 - (t - 0.5) / 0.5))}, 0, 0.55)`

          const sw = new BMapGL.Point(item.westLongitude, item.southLatitude)
          const nw = new BMapGL.Point(item.westLongitude, item.northLatitude)
          const ne = new BMapGL.Point(item.eastLongitude, item.northLatitude)
          const se = new BMapGL.Point(item.eastLongitude, item.southLatitude)

          const rect = new BMapGL.Polygon([sw, nw, ne, se, sw], {
            strokeColor: 'rgba(255,255,255,0.25)',
            strokeWeight: 0.5,
            fillColor: color,
            fillOpacity: 0.7,
            enableEditing: false,
            enableMassClear: false
          })
          rect.addEventListener('click', () => {
            this.onGridClick(item.gridCode, item)
          })
          this.map.addOverlay(rect)
          this.heatmapOverlays.push(rect)
        }
      } catch (e) {
        console.error('[bmap] 加载热力图数据失败:', e)
        this.$message.error('加载热力图数据失败')
      }
    },

    removeHeatmapLayer() {
      if (this.heatmapOverlays && this.heatmapOverlays.length) {
        this.heatmapOverlays.forEach(o => { try { this.map.removeOverlay(o) } catch(e) {} })
        this.heatmapOverlays = []
      }
      this.heatmapData = []
      this.heatmapVisible = false
      if (this.heatLegendEl) this.heatLegendEl.style.display = 'none'
    },

    onFilterBranch(primaryBranch) {
      this.currentFilter = primaryBranch
      for (const marker of this.branchMarkers) {
        const match = !primaryBranch || (marker._branchData && marker._branchData.primaryBranch === primaryBranch)
        if (match) {
          try { this.map.addOverlay(marker) } catch(e) {}
        } else {
          try { this.map.removeOverlay(marker) } catch(e) {}
        }
      }
      // 联动刷新网点排名
      if (this.branchRanking && this.branchRanking.visible) {
        this.loadBranchRanking()
      }
    },

    async onYearChange(year) {
      const _seq = (this._yearChangeSeq || 0) + 1
      this._yearChangeSeq = _seq
      this.selectedYear = year
      if (this.sidebar.visible && this.sidebar.branchData && this.sidebar.branchData.branchId) {
        await this.loadBranchScores(this.sidebar.branchData.branchId)
      }
      if (this._yearChangeSeq !== _seq) return
      if (this.ranking.visible) this.loadGridRanking(false)
      if (this._yearChangeSeq !== _seq) return
      if (this.quadrant && this.quadrant.visible) {
        this.loadQuadrant()
      }
      if (this._yearChangeSeq !== _seq) return
      if (this.comparePanel && this.comparePanel.branches && this.comparePanel.branches.length > 0) {
        await this.refreshAllCompareData()
      }
    },

    // ======== 网点 ========
    async loadBranches() {
      if (!this.currentCity) return
      // 清除旧标记
      if (this.branchMarkers && this.branchMarkers.length) {
        this.branchMarkers.forEach(m => { try { this.map.removeOverlay(m) } catch(e) {} })
      }
      this.branchMarkers = []

      const BMapGL = window.BMapGL
      if (!BMapGL) return

      const res = await getBranchList(this.currentCity)
      const branches = res.data || []
      this.branchList = branches

      const markers = this.createBranchMarkers(branches)
      this.branchMarkers = markers
      if (this.branchVisible !== false) {
        for (const m of markers) this.map.addOverlay(m)
      }
    },

    createBranchMarkers(branches) {
      const BMapGL = window.BMapGL
      if (!BMapGL) return []
      const markers = []
      const svgUrl = getOwnBankSvgUrl()
      const iconSize = 28
      const half = iconSize / 2

      for (const b of branches) {
        if (b.longitude == null || b.latitude == null) continue
        const point = new BMapGL.Point(b.longitude, b.latitude)

        const marker = new BMapGL.CustomOverlay(function () {
          const wrapper = document.createElement('div')
          wrapper.style.cssText =
            'position:absolute;cursor:pointer;width:' + iconSize + 'px;height:' + iconSize + 'px;'

          const bubble = document.createElement('div')
          bubble.style.cssText =
            'width:' + iconSize + 'px;height:' + iconSize + 'px;border-radius:50%;' +
            'background:#fff;border:2px solid #4a6cf7;' +
            'box-shadow:0 1px 4px rgba(0,0,0,0.18);' +
            'display:flex;align-items:center;justify-content:center;' +
            'transition:transform 0.15s ease,box-shadow 0.15s ease;'

          if (svgUrl) {
            bubble.innerHTML = '<img src="' + svgUrl + '" style="width:18px;height:18px;object-fit:contain;">'
          } else {
            bubble.style.background = '#4a6cf7'
            bubble.style.color = '#fff'
            bubble.style.fontSize = '11px'
            bubble.style.fontWeight = '700'
            bubble.textContent = '行'
          }

          wrapper.appendChild(bubble)

          const nameDiv = document.createElement('div')
          nameDiv.style.cssText =
            'display:none;position:absolute;top:-24px;left:50%;transform:translateX(-50%);' +
            'padding:2px 8px;border:none;border-radius:4px;' +
            'background:rgba(0,0,0,0.7);color:#fff;font-size:12px;white-space:nowrap;' +
            'pointer-events:none;'
          nameDiv.textContent = b.secondaryBranch || b.branchName || '网点'
          wrapper.appendChild(nameDiv)

          bubble.addEventListener('mouseover', () => {
            bubble.style.transform = 'scale(1.25)'
            bubble.style.boxShadow = '0 2px 8px rgba(74,108,247,0.4)'
            nameDiv.style.display = 'inline-block'
          })
          bubble.addEventListener('mouseout', () => {
            bubble.style.transform = 'scale(1)'
            bubble.style.boxShadow = '0 1px 4px rgba(0,0,0,0.2)'
            nameDiv.style.display = 'none'
          })
          bubble.addEventListener('click', () => { this.onBranchClick(b) })

          return wrapper
        }.bind(this), {
          point: point,
          offsetX: -half,
          offsetY: -half
        })

        marker._branchData = b
        markers.push(marker)
      }
      return markers
    },

    onSearchBranch(branch) {

      if (branch.latitude != null && branch.longitude != null) {
        const BMapGL = window.BMapGL
        if (BMapGL) {
          this.map.flyTo(new BMapGL.Point(branch.longitude, branch.latitude), 15, { duration: 0.5 })
        }
        setTimeout(() => this.onBranchClick(branch), 500)
      }
    },

    // ======== 地址搜索 ========
    async doAddressSearch(keyword) {
      if (!this.searchTool) return
      if (!keyword || !keyword.trim()) {
        this.addressSearchResults = []
        return
      }
      this.addressSearchLoading = true
      try {
        const rawResult = await this.searchTool.search(keyword)
        this.addressSearchResults = this.searchTool.parseResults(rawResult)
      } catch (e) {
        console.error('[bmap] 地址搜索失败:', e)
        this.addressSearchResults = []
      }
      this.addressSearchLoading = false
    },

    selectAddressResult(item) {
      if (!item || !item.point) return
      const BMapGL = window.BMapGL
      if (!BMapGL) return

      // 清除旧的搜索结果标记
      this.searchTool.clearResultMarkers()

      // 飞到目标位置
      this.map.flyTo(item.point, 15, { duration: 0.5 })

      // 添加单个搜索结果标记
      this.searchTool.showResultMarkers([item])

      // 隐藏下拉
      this.addressSearchResults = []
      this.addressSearchKeyword = ''
    },

    clearAddressSearch() {
      this.addressSearchResults = []
      this.addressSearchKeyword = ''
      this.addressSearchLoading = false
      if (this.searchTool) this.searchTool.clearResultMarkers()
    },

    async onBranchClick(branch) {
      this.clearHighlight()
      if (this.compareMode) {
        this.onAddCompareBranch(branch)
        return
      }
      const _seq = (this._branchClickSeq || 0) + 1
      this._branchClickSeq = _seq

      // 切换网点时重置所有 AI 状态，防止旧内容串到新网点
      this.gridAiState = { loading: false, content: '', error: '', mode: '' }
      this.branchAiState = { loading: false, content: '', error: '' }
      this.quadrantAiState = { loading: false, content: '', error: '' }
      this.relocationAiState = { loading: false, content: '', error: '' }

      this.sidebar.mode = 'branch-only'
      this.sidebar.width = 380
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
      const safeGap = {
        population: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' },
        enterprise: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' },
        business:   { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' }
      }
      if (!gridCode) { this.sidebar.pillarGap = safeGap; return }
      try {
        const res = await getPillarGap(gridCode)
        const d = res.data || {}
        const gapValues = Object.values(d)
        const keys = ['population', 'enterprise', 'business']
        const result = {}
        keys.forEach((key, i) => {
          const v = gapValues[i]
          if (v) {
            result[key] = {
              name: v.name || '---',
              maxCity: v.maxCity != null ? v.maxCity : (v.max != null ? v.max : 0),
              maxDistrict: v.maxDistrict != null ? v.maxDistrict : 0,
              gapCity: v.gapCity != null ? v.gapCity : (v.gap != null ? v.gap : 0),
              gapDistrict: v.gapDistrict != null ? v.gapDistrict : 0
            }
          } else {
            result[key] = { ...safeGap[key] }
          }
        })
        this.sidebar.pillarGap = result
      } catch (e) { this.sidebar.pillarGap = safeGap }
    },

    async loadBranchScores(branchId) {
      try {
        const res = await getBranchScoreDetail(branchId, this.selectedYear)
        let scores = (res.data || [])
          .filter(s => !s.scoreCategory || !s.scoreCategory.toLowerCase().includes('_auto'))
          .map(s => ({ ...s, categoryName: this.indicatorNameMap[s.scoreCategory] || '' }))
        if (this.currentCity) {
          try {
            const topRes = await getBranchTopScores(this.currentCity, this.selectedYear)
            const topScores = topRes.data || {}
            scores = scores.map(s => ({
              ...s,
              topScore: topScores[s.scoreCategory] != null ? topScores[s.scoreCategory] : null,
              gap: topScores[s.scoreCategory] != null ? Math.max(0, topScores[s.scoreCategory] - s.categoryScore) : 0
            }))
          } catch (e) { /* 差距数据非关键 */ }
        }
        this.sidebar.branchScores = scores
      } catch (e) { this.sidebar.branchScores = [] }
    },

    async loadBranchRankMeta(branchId) {
      try {
        const res = await getBranchInternalRanking(branchId, this.selectedYear)
        this.sidebar.branchRankMeta = res.data || { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
      } catch (e) {
        this.sidebar.branchRankMeta = { branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }
      }
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

    // ======== 网格点击 ========
    async onGridClick(gridCode, data) {
      this.clearHighlight()

      // 切换网格时重置所有 AI 状态，防止旧分析内容串到新网格
      this.gridAiState = { loading: false, content: '', error: '', mode: '' }
      this.branchAiState = { loading: false, content: '', error: '' }
      this.quadrantAiState = { loading: false, content: '', error: '' }
      this.relocationAiState = { loading: false, content: '', error: '' }

      this.sidebar.visible = true
      this.sidebar.gridData = data

      // 异步逆地理编码获取地址
      if (data && data.longitude != null && data.latitude != null) {
        this.reverseGeocode(data.longitude, data.latitude).then(addr => {
          if (addr) {
            this.sidebar.gridData = { ...this.sidebar.gridData, address: addr }
          }
        })
      }
      if (this.heatmapData && this.heatmapData.length) {
        const idx = this.heatmapData.findIndex(d => d.gridCode === gridCode)
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
          categoryLevel2: this.getParentCategoryName ? this.getParentCategoryName(d.indicatorCode) || d.level1Name : d.level1Name,
          level1Code: d.level1Code,
          level1Name: d.level1Name
        }))
        if (this._gridClickSeq !== _seq) return

        this.sidebar.gridRankMeta = rankRes.data || {
          cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0,
          scoreGap: 0, topScore: 0, districtTopScore: 0, districtScoreGap: 0
        }
        const rawPillar = pillarRes.data || {}
        const pillarKeys = Object.keys(rawPillar)
        const pillarValues = Object.values(rawPillar)
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
          this.sidebar.mode = 'split'
          this.sidebar.width = 600
          this.gridBranches = branches
          this.activeGridBranchIdx = 0
          this.sidebar.branchData = branches[0]
          await Promise.all([
            this.loadBranchScores(branches[0].branchId),
            this.loadBranchRankMeta(branches[0].branchId),
            this.loadBranchQuadrant(branches[0])
          ])
        } else {
          this.sidebar.mode = 'grid-only'
          this.sidebar.width = 380
          this.gridBranches = []
          this.activeGridBranchIdx = 0
          if (data && data.blankSpot) {
            this.loadNearestBranch(gridCode)
          } else {
            this.sidebar.nearestBranch = null
          }
        }
      } catch (e) {
        console.error('[bmap] 网格点击加载失败:', e)
      }
    },

    async switchGridBranch(branch, idx) {
      if (!branch || !branch.branchId) return
      this.activeGridBranchIdx = idx
      this.sidebar.branchData = branch
      await Promise.all([
        this.loadBranchScores(branch.branchId),
        this.loadBranchRankMeta(branch.branchId),
        this.loadBranchQuadrant(branch)
      ])
    },

    async loadNearestBranch(gridCode) {
      try {
        const res = await getGridNearestBranch(gridCode)
        this.sidebar.nearestBranch = res.data || null
      } catch (e) {
        this.sidebar.nearestBranch = null
      }
    },

    zoomToBranch() {
      const b = this.sidebar.branchData
      if (b && b.latitude != null && b.longitude != null) {
        const BMapGL = window.BMapGL
        if (BMapGL) {
          this.map.flyTo(new BMapGL.Point(b.longitude, b.latitude), 15, { duration: 0.5 })
        }
      }
    },

    async loadGridDataCache() {
      try {
        const res = await getGridScoreByCity(this.currentCity)
        this.gridDataCache = res.data || []
      } catch (e) { this.gridDataCache = [] }
    },

    closeSidebar() {
      this.sidebar.visible = false
      this.detailPanel.visible = false
      this.detailPanel.noAccess = false
      this.clearHighlight()
    },

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

    // ======== 维度统计 ========
    async loadDimStats(dimension) {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      const dim = dimension || this.dimStats.dimension
      this.dimStats.dimension = dim
      try {
        const res = await getDimensionStats(this.currentCity, this.selectedYear, dim)
        this.dimStats.data = res.data || []
        this.dimStats.visible = true
      } catch (e) { this.$message.error('加载统计数据失败') }
    },

    // ======== 四象限 ========
    async loadQuadrant() {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      try {
        const res = await getQuadrantData(this.currentCity, this.selectedYear)
        this.quadrant.data = res.data || null
        this.quadrant.visible = true
      } catch (e) { this.$message.error('加载象限数据失败') }
    },

    onQuadrantItemClick(item) {
      for (const marker of this.branchMarkers) {
        if (marker._branchData && marker._branchData.branchId === item.branchId) {
          const b = marker._branchData
          const BMapGL = window.BMapGL
          if (BMapGL) {
            this.map.flyTo(new BMapGL.Point(b.longitude, b.latitude), 14, { duration: 0.5 })
          }
          this.onBranchClick(b)
          this.highlightBranch(b)
          break
        }
      }
    },

    onQuadrantClose() {
      this.quadrant.visible = false
      if (this.currentFilter) {
        this.onFilterBranch(this.currentFilter)
      } else {
        for (const marker of this.branchMarkers) {
          try { this.map.addOverlay(marker) } catch(e) {}
        }
      }
    },

    onFilterQuadrant(quadrantCode) {
      if (!this.quadrant.data || !this.quadrant.data.quadrants) return
      const branches = this.quadrant.data.quadrants[quadrantCode] || []
      const branchIds = new Set(branches.map(b => b.branchId))
      for (const marker of this.branchMarkers) {
        if (marker._branchData) {
          const match = branchIds.has(marker._branchData.branchId)
          if (match) {
            try { this.map.addOverlay(marker) } catch(e) {}
          } else {
            try { this.map.removeOverlay(marker) } catch(e) {}
          }
        }
      }
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
          const v = parseFloat(i.value)
          return !isNaN(v) && v > m ? v : m
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
        const branchId = this.sidebar.branchData && this.sidebar.branchData.branchId
        this.detailPanel.branchId = branchId
        if (branchId && this.branchAccess) {
          try {
            const res = await getBranchIndicators(branchId, this.selectedYear)
            const list = res.data || res || []
            this.detailPanel.data = (Array.isArray(list) ? list : []).map(i => ({
              name: this.indicatorNameMap[i.indicatorCode] || i.indicatorCode,
              value: i.indicatorValue,
              ancestors: this.getAncestorChain ? this.getAncestorChain(i.indicatorCode) : []
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

    // ======== 同行银行（原 usePeerBanks） ========
    getBankStyle(bankName) {
      if (!bankName) return { css: '', text: '?', color: '#999' }
      const name = bankName.trim()
      if (PEER_BANK_STYLE_MAP[name]) return PEER_BANK_STYLE_MAP[name]
      for (const key of Object.keys(PEER_BANK_STYLE_MAP)) {
        if (name.includes(key)) return PEER_BANK_STYLE_MAP[key]
      }
      const firstChar = name.charAt(0)
      if (!this.peerBankAutoMap) this.peerBankAutoMap = {}
      if (!this.peerBankAutoMap[name]) {
        this.peerBankAutoMap[name] = PEER_BANK_AUTO_COLORS[this.peerBankAutoIndex % PEER_BANK_AUTO_COLORS.length]
        this.peerBankAutoIndex++
      }
      return { css: '', text: firstChar, color: this.peerBankAutoMap[name], auto: true }
    },

    async loadPeerBankMarkers() {
      if (!this.currentCity) return
      // 清除旧标记
      if (this.peerBankMarkers && this.peerBankMarkers.length) {
        this.peerBankMarkers.forEach(m => {
          try { this.map.removeOverlay(m) } catch(e) {}
        })
      }
      this.peerBankMarkers = []

      const BMapGL = window.BMapGL
      if (!BMapGL) return

      try {
        const res = await getPeerBankList(this.currentCity)
        const list = res.data || []
        if (!list.length) return
        const bankNamesInCity = new Set()

        for (const p of list) {
          if (p.longitude == null || p.latitude == null) continue
          const style = this.getBankStyle(p.bankName)
          bankNamesInCity.add(p.bankName || '未知')
          const point = new BMapGL.Point(p.longitude, p.latitude)
          const color = style.color || '#d40000'

          // ══ 使用 BMapGL.CustomOverlay + DOM 渲染同业标记 ══
          //   - 如有 SVG 则显示 SVG 图标
          //   - 无 SVG 则显示文字缩写（与天地图版行为一致）
          const svgUrl = getBankSvgUrl(p.bankName)

          const marker = new BMapGL.CustomOverlay(function () {
            const outer = document.createElement('div')
            outer.style.cssText =
              'width:20px;height:20px;border-radius:3px;border:2px solid ' + color + ';' +
              'background:#fff;cursor:pointer;overflow:hidden;' +
              'display:flex;align-items:center;justify-content:center;'

            if (svgUrl) {
              // SVG 图标
              outer.innerHTML = '<img src="' + svgUrl + '" style="width:14px;height:14px;object-fit:contain;">'
            } else {
              // 文字回退
              outer.style.color = color
              outer.style.fontSize = '11px'
              outer.style.fontWeight = 'bold'
              outer.style.lineHeight = '20px'
              outer.textContent = style.text
            }

            outer.addEventListener('click', () => {
              const infoWin = new BMapGL.InfoWindow(
                this.buildPeerBankPopup(p),
                { width: 260 }
              )
              this.map.openInfoWindow(infoWin, point)
            })

            return outer
          }.bind(this), {
            point: point,
            offsetX: -10,
            offsetY: -10
          })

          marker._peerBankData = p
          // 不直接 addOverlay，存入数组等待 onTogglePeerBank 控制显示
          this.peerBankMarkers.push(marker)
        }

        this.peerBankNames = Array.from(bankNamesInCity)

      } catch (e) {
        console.error('[bmap] 加载同业银行地标失败:', e)
      }
    },

    buildPeerBankPopup(p) {
      const style = this.getBankStyle(p.bankName)
      const color = style.color || '#d40000'
      const name = String(p.bankName || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
      const orgName = String(p.orgName || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
      const orgAddress = String(p.orgAddress || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
      return '<div style="font-size:13px;line-height:1.6;min-width:160px">'
        + '<div style="font-weight:700;font-size:14px;margin-bottom:4px;color:' + color + '">'
        + '<span style="display:inline-block;width:18px;height:18px;border-radius:3px;border:2px solid '
        + color + ';text-align:center;font-size:11px;line-height:18px;margin-right:6px">'
        + style.text + '</span>' + (name || '同业银行') + '</div>'
        + (orgName ? '<div style="color:#555;font-size:13px">' + orgName + '</div>' : '')
        + (orgAddress ? '<div style="color:#555;font-size:12px;margin-top:2px">' + orgAddress + '</div>' : '')
        + '</div>'
    },

    async onTogglePeerBank() {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      // 首次切换时加载数据
      if (!this._peerBankLoaded) {
        await this.loadPeerBankMarkers()
        this._peerBankLoaded = true
      }
      if (!this.peerBankMarkers.length) { this.$message.info('当前城市无同业银行数据'); return }
      if (this.peerBankVisible) {
        this.peerBankMarkers.forEach(m => { try { this.map.removeOverlay(m) } catch(e) {} })
      } else {
        this.peerBankMarkers.forEach(m => { try { this.map.addOverlay(m) } catch(e) {} })
      }
      this.peerBankVisible = !this.peerBankVisible
    },

    onToggleBranch() {
      if (!this.branchMarkers || !this.branchMarkers.length) { return }
      if (this.branchVisible) {
        this.branchMarkers.forEach(m => { try { this.map.removeOverlay(m) } catch(e) {} })
      } else {
        this.branchMarkers.forEach(m => { try { this.map.addOverlay(m) } catch(e) {} })
      }
      this.branchVisible = !this.branchVisible
    },

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

    // ======== 空白服务点（原 useBlankSpots） ========
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
        const limit = this.blankSpotLimit || 100
        const district = this.currentDistrict || undefined
        const res = await getGridTopWithoutBranch(this.currentCity, { limit, district })
        this.blankSpotData = res.data || []
        if (!this.blankSpotData.length) {
          this.$message.info('该范围内暂无空白服务点')
          this.blankSpotRanking.loading = false
          return
        }
        const scores = this.blankSpotData.map(d => d.siteScore).filter(s => s != null)
        const minScore = scores.length ? Math.min(...scores) : 0
        const maxScore = scores.length ? Math.max(...scores) : 1
        const range = maxScore - minScore || 1

        const BMapGL = window.BMapGL
        if (!BMapGL) return

        this.blankSpotOverlays = []

        for (const item of this.blankSpotData) {
          const t = ((item.siteScore || 0) - minScore) / range
          const darken = Math.round(t * 80)
          const g = Math.round(187 - darken)
          const b = Math.round(255 - darken)
          const opacity = 0.4 + t * 0.4

          const sw = new BMapGL.Point(item.westLongitude, item.southLatitude)
          const nw = new BMapGL.Point(item.westLongitude, item.northLatitude)
          const ne = new BMapGL.Point(item.eastLongitude, item.northLatitude)
          const se = new BMapGL.Point(item.eastLongitude, item.southLatitude)

          const points = [sw, nw, ne, se, sw]
          const rect = new BMapGL.Polygon(points, {
            strokeColor: 'transparent',
            strokeWeight: 0,
            fillColor: `rgb(0,${g},${b})`,
            fillOpacity: opacity,
            enableEditing: false,
            enableMassClear: false
          })
          const gridItem = { ...item, blankSpot: true }
          rect.addEventListener('click', () => {
            this.onGridClick(gridItem.gridCode, gridItem)
          })
          this.map.addOverlay(rect)
          this.blankSpotOverlays.push(rect)
        }

        this.blankSpotRanking.items = this.blankSpotData.map(d => ({
          id: d.gridCode,
          name: d.district || d.gridCode,
          score: d.siteScore || 0
        }))
        this.blankSpotRanking.page = 1
        this.blankSpotRanking.hasMore = this.blankSpotData.length > 20
        this.blankSpotRanking.visible = true
      } catch (e) {
        console.error('[bmap] 加载空白服务点失败:', e)
        this.$message.error('加载空白服务点数据失败')
      }
      this.blankSpotRanking.loading = false
    },

    onBlankSpotParamsChange(params) {
      if (params.limit != null) {
        this.blankSpotLimit = params.limit
      }
      // district 由 onSelectDistrict 统一管理，不在空白点 params 中修改
      if (this.blankSpotActive) {
        this.removeBlankSpotLayer()
        this.loadBlankSpotData()
      }
    },

    removeBlankSpotLayer() {
      if (this.blankSpotOverlays && this.blankSpotOverlays.length) {
        this.blankSpotOverlays.forEach(o => { try { this.map.removeOverlay(o) } catch(e) {} })
        this.blankSpotOverlays = []
      }
      this.blankSpotData = []
      this.blankSpotRanking.visible = false
      this.blankSpotRanking.items = []
    },

    onBlankSpotItemClick(item) {
      const data = this.blankSpotData.find(d => d.gridCode === item.id)
      if (!data) return
      const BMapGL = window.BMapGL
      if (BMapGL) {
        this.map.flyTo(new BMapGL.Point(data.longitude, data.latitude), 13, { duration: 0.5 })
      }
      setTimeout(async () => {
        await this.onGridClick(data.gridCode, data)
        this.highlightGrid(data)
      }, 700)
    },

    onBlankSpotClose() {
      this.removeBlankSpotLayer()
      this.blankSpotActive = false
    },

    loadMoreBlankSpot() {
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

    // ======== 高亮（原 useHighlight） ========
    clearHighlight() {
      if (this.highlightOverlays && this.highlightOverlays.length) {
        if (this.map) {
          this.highlightOverlays.forEach(o => { try { this.map.removeOverlay(o) } catch(e) {} })
        }
        this.highlightOverlays = []
      }
    },

    highlightGrid(gridData) {
      this.clearHighlight()
      if (!gridData) return
      const { southLatitude: s, northLatitude: n, westLongitude: w, eastLongitude: e } = gridData
      if (s == null || n == null || w == null || e == null) return

      const BMapGL = window.BMapGL
      if (!BMapGL) return

      this.highlightOverlays = []

      // 金色虚线边框
      const sw = new BMapGL.Point(w, s)
      const nw = new BMapGL.Point(w, n)
      const ne = new BMapGL.Point(e, n)
      const se = new BMapGL.Point(e, s)
      const rect = new BMapGL.Polygon([sw, nw, ne, se, sw], {
        strokeColor: '#FFD700',
        strokeWeight: 3,
        strokeOpacity: 1,
        fillColor: '#FFD700',
        fillOpacity: 0.08,
        strokeStyle: 'dashed',
        enableEditing: false,
        enableMassClear: false
      })
      this.map.addOverlay(rect)
      this.highlightOverlays.push(rect)

      // 中心点标签（网格编码）
      const lat = gridData.latitude || (s + n) / 2
      const lng = gridData.longitude || (w + e) / 2
      const label = new BMapGL.Label(gridData.gridCode || '', {
        position: new BMapGL.Point(lng, lat),
        offset: new BMapGL.Size(0, 0)
      })
      label.setStyle({
        color: '#FF8C00',
        fontSize: '13px',
        fontWeight: 'bold',
        border: 'none',
        backgroundColor: 'transparent',
        textShadow: '0 0 4px #fff, 0 0 4px #fff'
      })
      this.map.addOverlay(label)
      this.highlightOverlays.push(label)
    },

    highlightBranch(branchData) {
      this.clearHighlight()
      if (!branchData || branchData.latitude == null || branchData.longitude == null) return

      const BMapGL = window.BMapGL
      if (!BMapGL) return

      this.highlightOverlays = []
      const center = new BMapGL.Point(branchData.longitude, branchData.latitude)

      // 三层金色同心圆 — 使用小半径 BMapGL.Circle
      const circles = [
        { radius: 24, opacity: 0.08 },
        { radius: 16, opacity: 0.15 },
        { radius: 8, opacity: 0.35 }
      ]
      for (const c of circles) {
        const circle = new BMapGL.Circle(center, c.radius, {
          strokeColor: '#FFD700',
          strokeWeight: c.radius === 8 ? 2.5 : (c.radius === 16 ? 2 : 1),
          fillColor: '#FFD700',
          fillOpacity: c.opacity,
          enableEditing: false,
          enableMassClear: false
        })
        this.map.addOverlay(circle)
        this.highlightOverlays.push(circle)
      }

      // 深红中心点
      const dot = new BMapGL.Circle(center, 4, {
        strokeColor: '#FF4400',
        strokeWeight: 1.5,
        fillColor: '#FF4400',
        fillOpacity: 1,
        enableEditing: false,
        enableMassClear: false
      })
      this.map.addOverlay(dot)
      this.highlightOverlays.push(dot)

      // 名称标签
      const name = branchData.secondaryBranch || branchData.branchName || ''
      const label = new BMapGL.Label(name, {
        position: center,
        offset: new BMapGL.Size(0, -26)
      })
      label.setStyle({
        padding: '2px 6px',
        border: '1px solid #FFD700',
        borderRadius: '4px',
        backgroundColor: 'rgba(255,255,255,0.9)',
        fontSize: '13px',
        fontWeight: 'bold',
        color: '#FF8C00'
      })
      this.map.addOverlay(label)
      this.highlightOverlays.push(label)
    },

    // ======== 范围统计（原 useRangeStats） ========
    onToggleRangeStats() {
      if (!this.currentCity) { this.$message.warning('请先选择城市'); return }
      if (this.rangeModeActive) {
        this.unbindRangeEvents()
        this.unbindCenterClick()
        this.rangeStats.visible = false
        this.clearRangeShape()
        this.clearCenterMarker()
        this.map.enableDragging()
        this.map.getContainer().style.cursor = ''
      } else {
        this.rangeStats.visible = true
        this.map.getContainer().style.cursor = 'crosshair'
        this.bindDragEvents()
        this.$message.info('在地图上按住拖拽划定范围')
      }
      this.rangeModeActive = !this.rangeModeActive
    },

    onCloseRangeStats() {
      this.rangeModeActive = false
      this.rangeStats.visible = false
      this.unbindRangeEvents()
      this.unbindCenterClick()
      this.clearRangeShape()
      this.clearCenterMarker()
      this.map.enableDragging()
      this.map.getContainer().style.cursor = ''
    },

    unbindRangeEvents() {
      try {
        this.map.removeEventListener('mousedown', this.onRangeDrawStart)
        this.map.removeEventListener('mousemove', this.onRangeDrawMove)
        this.map.removeEventListener('mouseup', this.onRangeDrawEnd)
      } catch(e) {}
      this._rangeDrawing = null
    },

    // ===== 自由拖拽模式 =====
    bindDragEvents() {
      this.unbindCenterClick()
      this.map.disableDragging()
      this.map.addEventListener('mousedown', this.onRangeDrawStart)
      this.map.addEventListener('mousemove', this.onRangeDrawMove)
      this.map.addEventListener('mouseup', this.onRangeDrawEnd)
    },

    // ===== 定点选择模式 =====
    bindCenterClick() {
      this.unbindRangeEvents()
      this.map.disableDragging()
      this.map.getContainer().style.cursor = 'crosshair'
      this.map.addEventListener('click', this.onCenterClick)
    },

    unbindCenterClick() {
      try { this.map.removeEventListener('click', this.onCenterClick) } catch(e) {}
    },

    onCenterClick(e) {
      if (!this.rangeModeActive) return
      const BMapGL = window.BMapGL
      if (!BMapGL) return

      const point = e.latlng
      this.clearCenterMarker()

      this._centerMarkerCircle = new BMapGL.Circle(point, 6, {
        strokeColor: '#409eff',
        strokeWeight: 3,
        fillColor: '#fff',
        fillOpacity: 1
      })
      this.map.addOverlay(this._centerMarkerCircle)

      this.map.flyTo(point, 14, { duration: 0.5 })

      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      const radius = this.$refs.rangeStats ? this.$refs.rangeStats.radius : 500
      this.drawRangeShape(point.lat, point.lng, radius, shapeType)
      if (this.$refs.rangeStats) {
        this.$refs.rangeStats.setCenter(point.lat, point.lng)
      }
    },

    clearCenterMarker() {
      if (this._centerMarkerCircle) {
        try { this.map.removeOverlay(this._centerMarkerCircle) } catch(e) {}
        this._centerMarkerCircle = null
      }
    },

    onRangeModeChange(mode) {
      this.clearRangeShape()
      this.clearCenterMarker()
      if (this.$refs.rangeStats) {
        if (mode === 'center') {
          this.bindCenterClick()
          this.$message.info('在地图上点击选定中心点')
        } else {
          this.bindDragEvents()
          this.$message.info('在地图上按住拖拽划定范围')
        }
      }
    },

    // 拖拽辅助函数
    _rangeGetLatLng(e) {
      // BMapGL 鼠标事件可能不直接包含 latlng
      return e.latlng || { lat: e.clientY, lng: e.clientX }
    },

    _rangeDistance(lat1, lng1, lat2, lng2) {
      const R = 6371000
      const dLat = (lat2 - lat1) * Math.PI / 180
      const dLng = (lng2 - lng1) * Math.PI / 180
      const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLng / 2) ** 2
      return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    },

    onRangeDrawStart(e) {
      if (!this.rangeModeActive) return
      this._rangeDrawing = {
        startPoint: { lat: e.latlng.lat, lng: e.latlng.lng },
        tempLayer: null
      }
    },

    onRangeDrawMove(e) {
      if (!this._rangeDrawing || !this._rangeDrawing.startPoint) return
      const start = this._rangeDrawing.startPoint
      const end = e.latlng
      if (!end) return

      const BMapGL = window.BMapGL
      if (!BMapGL) return

      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      const previewStyle = { strokeColor: '#409eff', strokeWeight: 1, strokeOpacity: 0.5, fillOpacity: 0.05, strokeStyle: 'dashed' }

      if (this._rangeDrawing.tempLayer) {
        try { this.map.removeOverlay(this._rangeDrawing.tempLayer) } catch(e) {}
        this._rangeDrawing.tempLayer = null
      }

      if (shapeType === 'circle') {
        const r = this._rangeDistance(start.lat, start.lng, end.lat, end.lng)
        const overlay = new BMapGL.Circle(
          new BMapGL.Point(start.lng, start.lat),
          r,
          previewStyle
        )
        this.map.addOverlay(overlay)
        this._rangeDrawing.tempLayer = overlay
      } else {
        const sw = new BMapGL.Point(Math.min(start.lng, end.lng), Math.min(start.lat, end.lat))
        const nw = new BMapGL.Point(Math.min(start.lng, end.lng), Math.max(start.lat, end.lat))
        const ne = new BMapGL.Point(Math.max(start.lng, end.lng), Math.max(start.lat, end.lat))
        const se = new BMapGL.Point(Math.max(start.lng, end.lng), Math.min(start.lat, end.lat))
        const overlay = new BMapGL.Polygon([sw, nw, ne, se, sw], previewStyle)
        this.map.addOverlay(overlay)
        this._rangeDrawing.tempLayer = overlay
      }
    },

    onRangeDrawEnd(e) {
      if (!this._rangeDrawing || !this._rangeDrawing.startPoint) return
      const start = this._rangeDrawing.startPoint
      const end = e.latlng
      if (this._rangeDrawing.tempLayer) {
        try { this.map.removeOverlay(this._rangeDrawing.tempLayer) } catch(e) {}
        this._rangeDrawing.tempLayer = null
      }
      this._rangeDrawing = null

      if (this._rangeDistance(start.lat, start.lng, end.lat, end.lng) < 10) return

      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      let centerLat, centerLng, radius

      if (shapeType === 'circle') {
        centerLat = start.lat
        centerLng = start.lng
        radius = Math.round(this._rangeDistance(start.lat, start.lng, end.lat, end.lng))
      } else {
        centerLat = (start.lat + end.lat) / 2
        centerLng = (start.lng + end.lng) / 2
        const halfWidth = this._rangeDistance(start.lat, start.lng, start.lat, end.lng) / 2
        const halfHeight = this._rangeDistance(start.lat, start.lng, end.lat, start.lng) / 2
        radius = Math.round(Math.max(halfWidth, halfHeight))
      }

      this.clearRangeShape()
      this.drawRangeShape(centerLat, centerLng, radius, shapeType)
      if (this.$refs.rangeStats) {
        this.$refs.rangeStats.setCenter(centerLat, centerLng, radius)
      }
    },

    drawRangeShape(lat, lng, radius, shapeType) {
      this.clearRangeShape()
      const BMapGL = window.BMapGL
      if (!BMapGL) return

      const style = { strokeColor: '#409eff', strokeWeight: 2, fillOpacity: 0.1, strokeOpacity: 0.7 }

      if (shapeType === 'circle') {
        this.rangeShapeOverlay = new BMapGL.Circle(new BMapGL.Point(lng, lat), radius, style)
        this.map.addOverlay(this.rangeShapeOverlay)
      } else {
        const halfSide = radius
        const latDelta = halfSide / 111320.0
        const lngDelta = halfSide / (111320.0 * Math.cos(lat * Math.PI / 180))
        const sw = new BMapGL.Point(lng - lngDelta, lat - latDelta)
        const nw = new BMapGL.Point(lng - lngDelta, lat + latDelta)
        const ne = new BMapGL.Point(lng + lngDelta, lat + latDelta)
        const se = new BMapGL.Point(lng + lngDelta, lat - latDelta)
        this.rangeShapeOverlay = new BMapGL.Polygon([sw, nw, ne, se, sw], style)
        this.map.addOverlay(this.rangeShapeOverlay)
      }
    },

    clearRangeShape() {
      if (this.rangeShapeOverlay) {
        try { this.map.removeOverlay(this.rangeShapeOverlay) } catch(e) {}
        this.rangeShapeOverlay = null
      }
    },

    onRangeItemLocate(latlng) {
      if (latlng && latlng.length === 2) {
        const BMapGL = window.BMapGL
        if (BMapGL) {
          this.map.flyTo(new BMapGL.Point(latlng[1], latlng[0]), 16, { duration: 0.5 })
        }
      }
    },

    onRangeParamChange(params) {
      if (this.$refs.rangeStats && this.$refs.rangeStats.placed) {
        const lat = this.$refs.rangeStats.centerLat
        const lng = this.$refs.rangeStats.centerLng
        if (lat != null && lng != null) {
          this.drawRangeShape(lat, lng, params.radius, params.shapeType)
        }
      }
    },

    // ======== 逆地理编码 ========
    reverseGeocode(lng, lat) {
      return new Promise(resolve => {
        const BMapGL = window.BMapGL
        if (!BMapGL) { resolve(null); return }
        try {
          const geocoder = new BMapGL.Geocoder()
          geocoder.getLocation(new BMapGL.Point(lng, lat), function (result) {
            if (result && result.address) {
              resolve(result.address)
            } else {
              resolve(null)
            }
          })
        } catch (e) {
          console.warn('[bmap] 逆地理编码失败:', e)
          resolve(null)
        }
      })
    }
  }
}

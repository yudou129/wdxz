import L from 'leaflet'
import { getGridTopWithoutBranch } from '@/api/jwmap/data'

/**
 * 空白服务点 mixin — 高潜力空白网格的加载、渲染、排名
 * 支持按数量限制和行政区筛选
 */
export default {
  methods: {
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
        const district = this.currentDistrict !== 'all' && this.currentDistrict ? this.currentDistrict : undefined
        const res = await getGridTopWithoutBranch(this.currentCity, { limit, district })
        this.blankSpotData = res.data || []
        if (!this.blankSpotData.length) {
          this.$message.info('该范围内暂无空白服务点')
          this.blankSpotRanking.loading = false
          return
        }
        // 计算得分范围用于颜色映射
        const scores = this.blankSpotData.map(d => d.siteScore).filter(s => s != null)
        const minScore = scores.length ? Math.min(...scores) : 0
        const maxScore = scores.length ? Math.max(...scores) : 1
        const range = maxScore - minScore || 1

        const group = L.featureGroup()
        for (const item of this.blankSpotData) {
          const bounds = [
            [item.southLatitude, item.westLongitude],
            [item.northLatitude, item.eastLongitude]
          ]
          const t = ((item.siteScore || 0) - minScore) / range
          const darken = Math.round(t * 80)
          const g = Math.round(187 - darken)
          const b = Math.round(255 - darken)
          const opacity = 0.4 + t * 0.4
          const rect = L.rectangle(bounds, {
            stroke: false,
            fillColor: `rgb(0,${g},${b})`,
            fillOpacity: opacity
          })
          rect.bindTooltip(
            `${item.gridCode}<br>得分: ${(item.siteScore || 0).toFixed(4)}<br>${item.district || ''}`,
            { direction: 'center', className: 'blankspot-tooltip' }
          )
          const gridItem = { ...item, blankSpot: true }
          rect.on('click', () => {
            this.onGridClick(gridItem.gridCode, gridItem)
          })
          group.addLayer(rect)
        }
        this.map.addLayer(group)
        this.blankSpotLayer = group
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

    /** 空白点参数变更（数量/行政区），重新加载 */
    onBlankSpotParamsChange(params) {
      if (params.limit != null || params.district !== undefined) {
        this.blankSpotLimit = params.limit
        this.currentDistrict = params.district
      }
      if (this.blankSpotActive) {
        this.removeBlankSpotLayer()
        this.loadBlankSpotData()
      }
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
    }
  }
}

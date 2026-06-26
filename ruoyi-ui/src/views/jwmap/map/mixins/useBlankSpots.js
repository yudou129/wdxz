import L from 'leaflet'
import { getGridTopWithoutBranch } from '@/api/jwmap/data'

/**
 * 空白服务点 mixin — 高潜力空白网格的加载、渲染、排名
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
        const res = await getGridTopWithoutBranch(this.currentCity)
        this.blankSpotData = res.data || []
        if (!this.blankSpotData.length) {
          this.$message.info('该城市暂无空白服务点')
          this.blankSpotRanking.loading = false
          return
        }
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

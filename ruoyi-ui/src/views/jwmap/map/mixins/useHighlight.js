import L from 'leaflet'

/**
 * 高亮 mixin — 排名/搜索点击后在地图上叠加金色高亮标记
 *
 * 颜色选择：
 *   热力图使用 绿→黄→红 渐变，空白点使用蓝色系，网点标记为灰蓝色。
 *   金色(#FFD700)与以上所有色系均保持高对比，且不混淆。
 *
 * 网格高亮：金色虚线边框 + 极淡填充 + 深橙中心点 + 网格编码标签
 * 网点高亮：三层金色同心圆涟漪 + 深红实心中心点 + 网点名称标签
 */
export default {
  data() {
    return {
      highlightLayer: null
    }
  },
  methods: {
    /**
     * 移除当前高亮层
     */
    clearHighlight() {
      if (this.highlightLayer && this.map) {
        this.map.removeLayer(this.highlightLayer)
        this.highlightLayer = null
      }
    },

    /**
     * 高亮选中的网格（适用于网格排名、空白点排名）
     * @param {object} gridData - 必须含 southLatitude/northLatitude/westLongitude/eastLongitude
     */
    highlightGrid(gridData) {
      this.clearHighlight()
      if (!gridData) return

      const { southLatitude: s, northLatitude: n, westLongitude: w, eastLongitude: e } = gridData
      if (s == null || n == null || w == null || e == null) return

      const group = L.featureGroup()
      const bounds = [[s, w], [n, e]]

      // █ 金色虚线边框 — 与热力图实色填充、空白点无边框形成三重区分
      const rect = L.rectangle(bounds, {
        stroke: true,
        color: '#FFD700',
        weight: 3,
        fillColor: '#FFD700',
        fillOpacity: 0.08,
        dashArray: '6,4',
        lineCap: 'round'
      })
      group.addLayer(rect)

      // ● 深橙色中心圆点 — 精确定位网格中心
      const lat = gridData.latitude || (s + n) / 2
      const lng = gridData.longitude || (w + e) / 2
      L.circleMarker([lat, lng], {
        radius: 5,
        color: '#FF8C00',
        fillColor: '#FF8C00',
        fillOpacity: 1,
        weight: 2
      }).addTo(group)

      // 固定网格编码标签（居中显示）
      rect.bindTooltip(gridData.gridCode || '', {
        permanent: true,
        direction: 'center',
        className: 'highlight-grid-tooltip'
      })

      this.map.addLayer(group)
      this.highlightLayer = group
    },

    /**
     * 高亮选中的网点（适用于网点排名）
     * @param {object} branchData - 必须含 latitude/longitude
     */
    highlightBranch(branchData) {
      this.clearHighlight()
      if (!branchData || branchData.latitude == null || branchData.longitude == null) return

      const group = L.featureGroup()
      const center = [branchData.latitude, branchData.longitude]

      // 三层金色同心圆 — 由外到内逐层加深，制造涟漪聚焦效果
      L.circleMarker(center, {
        radius: 24,
        color: '#FFD700',
        fillColor: '#FFD700',
        fillOpacity: 0.08,
        weight: 1
      }).addTo(group)

      L.circleMarker(center, {
        radius: 16,
        color: '#FFD700',
        fillColor: '#FFD700',
        fillOpacity: 0.15,
        weight: 2
      }).addTo(group)

      L.circleMarker(center, {
        radius: 8,
        color: '#FFD700',
        fillColor: '#FFD700',
        fillOpacity: 0.35,
        weight: 2.5
      }).addTo(group)

      // 深红实心中心点
      L.circleMarker(center, {
        radius: 4,
        color: '#FF4400',
        fillColor: '#FF4400',
        fillOpacity: 1,
        weight: 1.5
      }).addTo(group)

      // 固定网点名称标签（上方显示）
      const name = branchData.secondaryBranch || branchData.branchName || ''
      group.bindTooltip(name, {
        permanent: true,
        direction: 'top',
        offset: [0, -18],
        className: 'highlight-branch-tooltip'
      })

      this.map.addLayer(group)
      this.highlightLayer = group
    }
  }
}

import L from 'leaflet'

/**
 * 热力图层 — Canvas 瓦片渲染 + 坐标命中检测
 * 配色：low(0) #00ff00 → #ffff00 → #ff0000 high(1)
 * 数据格式：{ gridCode, longitude, latitude, westLongitude, eastLongitude,
 *             northLatitude, southLatitude, siteScore }
 *
 * 点击检测不使用 L.rectangle（SVG visiblePainted 在透明填充下不可靠），
 * 改用 getGridAtLatLng() 数学判断，由外部 map click 事件驱动。
 */
export class HeatmapLayer {
  constructor(map) {
    this.map = map
    this._canvasLayer = null
    this._data = []
    this._visible = false
    this._minScore = 0
    this._maxScore = 1
  }

  isVisible() { return this._visible }
  getData() { return this._data }

  async loadData(city) {
    const { getGridScoreByCity } = await import('@/api/jwmap/data')
    const res = await getGridScoreByCity(city)
    this._data = res.data || []
    // 计算实际得分范围用于颜色映射（最低→绿色，最高→红色）
    const scores = this._data.map(d => d.siteScore).filter(s => s != null)
    this._minScore = scores.length ? Math.min(...scores) : 0
    this._maxScore = scores.length ? Math.max(...scores) : 1
    if (this._minScore === this._maxScore) this._maxScore = this._minScore + 0.001
    console.log(`[heatmap] ${city}: ${scores.length}个网格, siteScore范围 ${this._minScore.toFixed(4)} ~ ${this._maxScore.toFixed(4)}`)
    return this._data
  }

  /**
   * 判断 latlng（BD09 坐标）落在哪个网格内
   * @param {L.LatLng} latlng
   * @returns {object|null} 匹配的网格数据项，无匹配返回 null
   */
  getGridAtLatLng(latlng) {
    const lat = latlng.lat
    const lng = latlng.lng
    for (const item of this._data) {
      if (item.siteScore == null) continue
      if (item.southLatitude <= lat && lat <= item.northLatitude &&
          item.westLongitude <= lng && lng <= item.eastLongitude) {
        return item
      }
    }
    return null
  }

  show() {
    if (this._visible) return
    this._visible = true
    this._renderCanvas()
    if (this._canvasLayer) this.map.addLayer(this._canvasLayer)
  }

  hide() {
    if (!this._visible) return
    this._visible = false
    if (this._canvasLayer) { this.map.removeLayer(this._canvasLayer); this._canvasLayer = null }
  }

  _renderCanvas() {
    const self = this
    this._canvasLayer = L.gridLayer({ tileSize: 512, minZoom: this.map.getMinZoom(), maxZoom: this.map.getMaxZoom() })
    this._canvasLayer.createTile = function (coords) {
      const tile = L.DomUtil.create('canvas', 'leaflet-tile')
      tile.width = 512; tile.height = 512
      tile.style.width = '512px'; tile.style.height = '512px'
      const ctx = tile.getContext('2d')
      const zoom = coords.z; const ts = 512
      const tx = coords.x; const ty = coords.y
      const crs = self.map.options.crs

      for (const item of self._data) {
        const rawScore = item.siteScore
        if (rawScore == null) continue
        // 按实际最低→最高值映射到 0→1 颜色范围
        const score = (rawScore - self._minScore) / (self._maxScore - self._minScore)

        // Project bounding box corners to tile pixel coords
        const nw = crs.latLngToPoint(L.latLng(item.northLatitude, item.westLongitude), zoom)
        const se = crs.latLngToPoint(L.latLng(item.southLatitude, item.eastLongitude), zoom)
        const x1 = nw.x - tx * ts; const y1 = nw.y - ty * ts
        const x2 = se.x - tx * ts; const y2 = se.y - ty * ts

        // Skip tiles that don't overlap this grid
        if (x2 < -10 || x1 > 522 || y2 < -10 || y1 > 522) continue

        const w = x2 - x1; const h = y2 - y1
        if (w <= 0 || h <= 0) continue

        // Fill with score color at 60% opacity for underlying map visibility
        ctx.fillStyle = self._scoreToColor(score, 0.6)
        ctx.fillRect(x1, y1, w, h)

        // Thin border to distinguish adjacent cells
        ctx.strokeStyle = 'rgba(255,255,255,0.3)'
        ctx.lineWidth = 0.5
        ctx.strokeRect(x1, y1, w, h)
      }
      return tile
    }
  }

  _scoreToColor(score, alpha) {
    const t = Math.max(0, Math.min(1, score))
    const a = alpha != null ? alpha : 0.8
    if (t < 0.5) {
      const s = t / 0.5
      return `rgba(${Math.round(255 * s)}, 255, 0, ${a})`
    } else {
      const s = (t - 0.5) / 0.5
      return `rgba(255, ${Math.round(255 * (1 - s))}, 0, ${a})`
    }
  }
}

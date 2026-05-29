import L from 'leaflet'

/**
 * 热力图层 — Canvas 渲染 + L.rectangle 点击热区
 * 配色：low(0) #00ff00 → #ffff00 → #ff0000 high(1)
 * 数据格式：{ gridCode, longitude, latitude, westLongitude, eastLongitude,
 *             northLatitude, southLatitude, siteScore }
 */
export class HeatmapLayer {
  constructor(map) {
    this.map = map
    this._canvasLayer = null
    this._clickLayer = L.layerGroup()
    this._data = []
    this._visible = false
  }

  isVisible() { return this._visible }
  getData() { return this._data }

  async loadData(city) {
    const { getGridScoreByCity } = await import('@/api/jwmap/data')
    const res = await getGridScoreByCity(city)
    this._data = res.data || []
    return this._data
  }

  show() {
    if (this._visible) return
    this._visible = true
    this._renderCanvas()
    this._renderClickRects()
    if (this._canvasLayer) this.map.addLayer(this._canvasLayer)
    if (this._clickLayer) this.map.addLayer(this._clickLayer)
  }

  hide() {
    if (!this._visible) return
    this._visible = false
    if (this._canvasLayer) { this.map.removeLayer(this._canvasLayer); this._canvasLayer = null }
    if (this._clickLayer) { this.map.removeLayer(this._clickLayer); this._clickLayer.clearLayers() }
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
        const score = item.siteScore
        if (score == null) continue

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

  _renderClickRects() {
    this._clickLayer.clearLayers()
    for (const item of this._data) {
      if (item.siteScore == null) continue
      const rect = L.rectangle(
        [[item.southLatitude, item.westLongitude], [item.northLatitude, item.eastLongitude]],
        { color: 'transparent', weight: 0, fillColor: 'transparent', fillOpacity: 0 }
      )
      rect.on('click', () => this.map.fire('grid-click', { gridCode: item.gridCode, data: item }))
      this._clickLayer.addLayer(rect)
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

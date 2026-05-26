/**
 * 地图测量工具：标点（经纬度拾取）+ 测距（多点距离累加）
 */

import L from 'leaflet'

// Haversine 距离公式（米）
function haversine(lat1, lng1, lat2, lng2) {
  const R = 6371000
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLng = (lng2 - lng1) * Math.PI / 180
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLng / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

function fmtDist(m) {
  return m >= 1000 ? `${(m / 1000).toFixed(2)} km` : `${m.toFixed(1)} m`
}

export class MeasureTool {
  constructor(map) {
    this.map = map
    this._active = null   // null | 'pick' | 'measure'

    // 标点模式
    this._pickMarker = null

    // 测距模式
    this._points = []       // L.LatLng[]
    this._markers = []      // 点标记
    this._polyline = null   // 连线
    this._labels = []       // 距离标签
  }

  // ---- 初始化 ----

  init() {
    this._createControl()
    this._createCursorStyle()
  }

  // ---- 控制面板 ----

  _createControl() {
    const MeasureControl = L.Control.extend({
      onAdd: () => {
        const div = L.DomUtil.create('div', 'measure-control')
        div.innerHTML = `
          <button class="measure-btn pick-btn" title="经纬度标点">📍 标点</button>
          <button class="measure-btn ruler-btn" title="测距">📏 测距</button>
          <button class="measure-btn clear-btn" title="清除">✕ 清除</button>
        `
        this._pickBtn = div.querySelector('.pick-btn')
        this._rulerBtn = div.querySelector('.ruler-btn')
        this._clearBtn = div.querySelector('.clear-btn')

        L.DomEvent.disableClickPropagation(div)
        L.DomEvent.disableScrollPropagation(div)
        L.DomEvent.on(this._pickBtn, 'click', this._togglePick, this)
        L.DomEvent.on(this._rulerBtn, 'click', this._toggleMeasure, this)
        L.DomEvent.on(this._clearBtn, 'click', this._clear, this)
        L.DomEvent.on(document, 'keydown', this._onKey, this)

        return div
      }
    })
    new MeasureControl({ position: 'topleft' }).addTo(this.map)
  }

  _createCursorStyle() {
    const style = document.createElement('style')
    style.textContent = '.leaflet-container.measure-crosshair { cursor: crosshair !important; }'
    document.head.appendChild(style)
    this._container = this.map.getContainer()
  }

  // ---- 模式切换 ----

  _togglePick() {
    if (this._active === 'pick') { this._deactivate(); return }
    this._deactivate()
    this._active = 'pick'
    this._pickBtn.classList.add('active')
    L.DomUtil.addClass(this._container, 'measure-crosshair')
    this.map.on('click', this._onPickClick, this)
  }

  _toggleMeasure() {
    if (this._active === 'measure') { this._finishMeasure(); return }
    this._deactivate()
    this._active = 'measure'
    this._rulerBtn.classList.add('active')
    this._points = []
    L.DomUtil.addClass(this._container, 'measure-crosshair')
    this.map.on('click', this._onMeasureClick, this)
    this.map.doubleClickZoom.disable()
  }

  _deactivate() {
    this._active = null
    this._pickBtn.classList.remove('active')
    this._rulerBtn.classList.remove('active')
    L.DomUtil.removeClass(this._container, 'measure-crosshair')
    this.map.off('click', this._onPickClick, this)
    this.map.off('click', this._onMeasureClick, this)
    this.map.doubleClickZoom.enable()
    this._clearPickMarker()
  }

  // ---- 标点 ----

  _onPickClick(e) {
    this._clearPickMarker()

    const icon = L.divIcon({
      className: 'pick-marker-icon',
      html: '<div style="width:12px;height:12px;background:#e74c3c;border:2px solid #fff;border-radius:50%;box-shadow:0 0 4px rgba(0,0,0,.5)"></div>',
      iconSize: [12, 12],
      iconAnchor: [6, 6]
    })

    this._pickMarker = L.marker(e.latlng, { icon, interactive: false }).addTo(this.map)
    this._pickMarker
      .bindPopup(`
        <div style="font-size:13px;font-family:monospace;white-space:nowrap">
          <b>纬度</b> ${e.latlng.lat.toFixed(6)}<br>
          <b>经度</b> ${e.latlng.lng.toFixed(6)}
        </div>`,
        { offset: [0, -10] }
      )
      .openPopup()
  }

  _clearPickMarker() {
    if (this._pickMarker) { this.map.removeLayer(this._pickMarker); this._pickMarker = null }
  }

  // ---- 测距 ----

  _onMeasureClick(e) {
    this._points.push(e.latlng)

    // 标记点
    const icon = L.divIcon({
      className: 'measure-point-icon',
      html: `<div style="width:8px;height:8px;background:#2980b9;border:2px solid #fff;border-radius:50%"></div>`,
      iconSize: [8, 8],
      iconAnchor: [4, 4]
    })
    const m = L.marker(e.latlng, { icon }).addTo(this.map)
    this._markers.push(m)

    // 更新折线
    this._updatePolyline()
    // 更新标签
    this._updateLabels()
  }

  _updatePolyline() {
    if (this._polyline) this.map.removeLayer(this._polyline)
    if (this._points.length < 2) return
    this._polyline = L.polyline(this._points, {
      color: '#2980b9', weight: 2, dashArray: '6 4', opacity: 0.8
    }).addTo(this.map)
  }

  _updateLabels() {
    this._labels.forEach(l => this.map.removeLayer(l))
    this._labels = []

    let total = 0
    for (let i = 1; i < this._points.length; i++) {
      const a = this._points[i - 1]
      const b = this._points[i]
      const d = haversine(a.lat, a.lng, b.lat, b.lng)
      total += d

      const mid = L.latLng((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
      const label = L.marker(mid, {
        icon: L.divIcon({
          className: 'measure-label',
          html: `<span>${fmtDist(d)}</span>`,
          iconSize: null
        }),
        interactive: false
      }).addTo(this.map)
      this._labels.push(label)
    }

    // 终点总距离
    if (this._points.length >= 2) {
      const last = this._points[this._points.length - 1]
      const totalLabel = L.marker(last, {
        icon: L.divIcon({
          className: 'measure-label total',
          html: `<span>总计: ${fmtDist(total)}</span>`,
          iconSize: null
        }),
        interactive: false
      }).addTo(this.map)
      this._labels.push(totalLabel)
      this._totalDist = total
    }
  }

  _finishMeasure() {
    // 保留测距结果，仅退出编辑模式
    this._active = null
    this._rulerBtn.classList.remove('active')
    L.DomUtil.removeClass(this._container, 'measure-crosshair')
    this.map.off('click', this._onMeasureClick, this)
    this.map.doubleClickZoom.enable()
  }

  // ---- 清除全部 ----

  _clear() {
    this._deactivate()
    this._clearMeasure()
  }

  _clearMeasure() {
    this._points = []
    this._markers.forEach(m => this.map.removeLayer(m))
    this._markers = []
    if (this._polyline) { this.map.removeLayer(this._polyline); this._polyline = null }
    this._labels.forEach(l => this.map.removeLayer(l))
    this._labels = []
  }

  _onKey(e) {
    if (e.key === 'Escape') {
      this._deactivate()
      if (this._active === 'measure') this._clearMeasure()
    }
  }
}

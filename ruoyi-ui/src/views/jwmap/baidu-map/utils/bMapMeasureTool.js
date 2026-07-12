/**
 * 百度地图 GL 测量工具：标点（经纬度拾取）+ 测距（多点距离累加）
 *
 * 使用纯 HTML 按钮 + BMapGL 覆盖物，无 Leaflet 依赖
 */

// Haversine 距离公式（米）— 坐标系统无关，直接复制
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

export class BMapMeasureTool {
  constructor(map) {
    this.map = map
    this._active = null   // null | 'pick' | 'measure'
    this._container = null

    // 标点模式
    this._pickMarkers = []

    // 测距模式
    this._points = []       // BMapGL.Point[]
    this._markers = []      // BMapGL.Marker[]
    this._polyline = null   // BMapGL.Polyline
    this._labels = []       // BMapGL.Label[]
  }

  // ---- 初始化 ----

  init() {
    this._createControl()
    this._createCursorStyle()
  }

  // ---- 控制面板 ----

  _createControl() {
    const div = document.createElement('div')
    div.className = 'measure-control'
    div.innerHTML = `
      <button class="measure-btn pick-btn" title="经纬度标点">📍 标点</button>
      <button class="measure-btn ruler-btn" title="测距">📏 测距</button>
      <button class="measure-btn clear-btn" title="清除">✕ 清除</button>
    `
    this._pickBtn = div.querySelector('.pick-btn')
    this._rulerBtn = div.querySelector('.ruler-btn')
    this._clearBtn = div.querySelector('.clear-btn')

    // 阻止地图事件穿透
    div.addEventListener('click', e => e.stopPropagation())
    div.addEventListener('dblclick', e => e.stopPropagation())
    div.addEventListener('wheel', e => e.stopPropagation())
    div.addEventListener('touchstart', e => e.stopPropagation())

    this._pickBtn.addEventListener('click', () => this._togglePick())
    this._rulerBtn.addEventListener('click', () => this._toggleMeasure())
    this._clearBtn.addEventListener('click', () => this._clear())
    document.addEventListener('keydown', (e) => this._onKey(e))

    // 插入到地图容器
    const container = this.map.getContainer()
    container.appendChild(div)
    // 记录容器引用用于光标样式
    this._maContainer = container
  }

  _createCursorStyle() {
    const style = document.createElement('style')
    style.textContent = `
      .bmap-measure-crosshair {
        cursor: crosshair !important;
      }
    `
    document.head.appendChild(style)
  }

  // ---- 模式切换 ----

  _togglePick() {
    if (this._active === 'pick') { this._deactivate(); return }
    this._deactivate()
    this._active = 'pick'
    this._pickBtn.classList.add('active')
    if (this._maContainer) this._maContainer.classList.add('bmap-measure-crosshair')
    this.map.addEventListener('click', this._onPickClick)
  }

  _toggleMeasure() {
    if (this._active === 'measure') { this._finishMeasure(); return }
    this._deactivate()
    this._active = 'measure'
    this._rulerBtn.classList.add('active')
    this._points = []
    if (this._maContainer) this._maContainer.classList.add('bmap-measure-crosshair')
    this.map.addEventListener('click', this._onMeasureClick)
  }

  _deactivate() {
    this._active = null
    if (this._pickBtn) this._pickBtn.classList.remove('active')
    if (this._rulerBtn) this._rulerBtn.classList.remove('active')
    if (this._maContainer) this._maContainer.classList.remove('bmap-measure-crosshair')
    try {
      this.map.removeEventListener('click', this._onPickClick)
      this.map.removeEventListener('click', this._onMeasureClick)
    } catch (e) { /* ignore */ }
    this._clearPickMarkers()
  }

  // ---- 标点 ----

  _onPickClick = (e) => {
    const BMapGL = window.BMapGL
    if (!BMapGL) return
    const point = e.latlng

    // 清除旧标点
    this._clearPickMarkers()

    // 创建标记
    const marker = new BMapGL.Marker(point)
    this.map.addOverlay(marker)
    this._pickMarkers.push(marker)

    // 信息窗口
    const infoWin = new BMapGL.InfoWindow(
      `<div style="font-size:13px;font-family:monospace;white-space:nowrap">
        <b>纬度</b> ${point.lat.toFixed(6)}<br>
        <b>经度</b> ${point.lng.toFixed(6)}
      </div>`,
      { width: 180, offset: new BMapGL.Size(0, -30) }
    )
    this.map.openInfoWindow(infoWin, point)
  }

  _clearPickMarkers() {
    for (const m of this._pickMarkers) {
      try { this.map.removeOverlay(m) } catch (e) { /* ignore */ }
    }
    this._pickMarkers = []
    try { this.map.closeInfoWindow() } catch (e) { /* ignore */ }
  }

  // ---- 测距 ----

  _onMeasureClick = (e) => {
    const BMapGL = window.BMapGL
    if (!BMapGL) return
    const point = e.latlng
    this._points.push(point)

    // 标记点（蓝色圆点）
    const marker = this._createMeasureMarker(point)
    this.map.addOverlay(marker)
    this._markers.push(marker)

    // 更新折线和标签
    this._updatePolyline()
    this._updateLabels()
  }

  _createMeasureMarker(point) {
    const BMapGL = window.BMapGL
    if (!BMapGL) return null

    // 使用 Label 实现圆形标记（BMapGL Marker 只能使用图片图标）
    const label = new BMapGL.Label('', {
      position: point,
      offset: new BMapGL.Size(-4, -4)
    })
    label.setStyle({
      width: '8px',
      height: '8px',
      borderRadius: '50%',
      background: '#2980b9',
      border: '2px solid #fff',
      padding: '0',
      cursor: 'pointer'
    })
    return label
  }

  _updatePolyline() {
    const BMapGL = window.BMapGL
    if (!BMapGL) return

    if (this._polyline) {
      try { this.map.removeOverlay(this._polyline) } catch (e) { /* ignore */ }
      this._polyline = null
    }
    if (this._points.length < 2) return

    this._polyline = new BMapGL.Polyline(this._points, {
      strokeColor: '#2980b9',
      strokeWeight: 2,
      strokeOpacity: 0.8,
      strokeStyle: 'dashed'
    })
    this.map.addOverlay(this._polyline)
  }

  _updateLabels() {
    const BMapGL = window.BMapGL
    if (!BMapGL) return

    // 清除旧标签
    for (const l of this._labels) {
      try { this.map.removeOverlay(l) } catch (e) { /* ignore */ }
    }
    this._labels = []

    let total = 0
    for (let i = 1; i < this._points.length; i++) {
      const a = this._points[i - 1]
      const b = this._points[i]
      const d = haversine(a.lat, a.lng, b.lat, b.lng)
      total += d

      const mid = new BMapGL.Point((a.lng + b.lng) / 2, (a.lat + b.lat) / 2)
      const label = new BMapGL.Label(`<span>${fmtDist(d)}</span>`, {
        position: mid,
        offset: new BMapGL.Size(0, -12)
      })
      label.setStyle({
        backgroundColor: 'rgba(255,255,255,0.85)',
        border: '1px solid #2980b9',
        borderRadius: '3px',
        padding: '1px 5px',
        fontSize: '12px',
        fontFamily: 'monospace',
        color: '#2980b9'
      })
      this.map.addOverlay(label)
      this._labels.push(label)
    }

    // 终点总距离
    if (this._points.length >= 2) {
      const last = this._points[this._points.length - 1]
      const totalLabel = new BMapGL.Label(`<span>总计: ${fmtDist(total)}</span>`, {
        position: last,
        offset: new BMapGL.Size(10, -10)
      })
      totalLabel.setStyle({
        backgroundColor: 'rgba(255,255,255,0.9)',
        border: '1px solid #e74c3c',
        borderRadius: '3px',
        padding: '2px 6px',
        fontSize: '12px',
        fontFamily: 'monospace',
        color: '#e74c3c',
        fontWeight: 'bold'
      })
      this.map.addOverlay(totalLabel)
      this._labels.push(totalLabel)
      this._totalDist = total
    }
  }

  _finishMeasure() {
    // 保留测距结果，仅退出编辑模式
    this._active = null
    if (this._rulerBtn) this._rulerBtn.classList.remove('active')
    if (this._maContainer) this._maContainer.classList.remove('bmap-measure-crosshair')
    try {
      this.map.removeEventListener('click', this._onMeasureClick)
    } catch (e) { /* ignore */ }
  }

  // ---- 清除全部 ----

  _clear() {
    this._deactivate()
    this._clearMeasure()
  }

  _clearMeasure() {
    this._points = []
    for (const m of this._markers) {
      try { this.map.removeOverlay(m) } catch (e) { /* ignore */ }
    }
    this._markers = []
    if (this._polyline) {
      try { this.map.removeOverlay(this._polyline) } catch (e) { /* ignore */ }
      this._polyline = null
    }
    for (const l of this._labels) {
      try { this.map.removeOverlay(l) } catch (e) { /* ignore */ }
    }
    this._labels = []
  }

  _onKey(e) {
    if (e.key === 'Escape') {
      this._deactivate()
      if (this._active === 'measure') this._clearMeasure()
    }
  }

  destroy() {
    this._clear()
    if (this._controlEl && this._controlEl.parentNode) {
      this._controlEl.parentNode.removeChild(this._controlEl)
    }
  }
}

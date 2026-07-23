/**
 * 百度地图 GL 测量工具：标点（经纬度拾取）+ 测距（多点距离累加）
 *
 * 无 DOM 控制栏，由外部 Vue 组件通过公共方法控制
 */

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

export class BMapMeasureTool {
  constructor(map) {
    this.map = map
    this._active = null   // null | 'pick' | 'measure'

    // 标点模式
    this._pickMarkers = []

    // 测距模式
    this._points = []
    this._markers = []
    this._polyline = null
    this._labels = []

    this._createCursorStyle()
    this._maContainer = map && map.getContainer()
  }

  /** 当前激活的模式 */
  get active() { return this._active }

  // ---- 光标样式 ----

  _createCursorStyle() {
    if (typeof document === 'undefined') return
    if (document.getElementById('bmap-measure-style')) return
    const style = document.createElement('style')
    style.id = 'bmap-measure-style'
    style.textContent = `.bmap-measure-crosshair { cursor: crosshair !important; }`
    document.head.appendChild(style)
  }

  _setCrosshair(on) {
    if (!this._maContainer) return
    const el = this._maContainer
    if (on) el.classList.add('bmap-measure-crosshair')
    else el.classList.remove('bmap-measure-crosshair')
  }

  // ---- 公共 API ----

  /** 激活标点模式 */
  activatePick() {
    if (this._active === 'pick') { this.deactivate(); return }
    this.deactivate()
    this._active = 'pick'
    this._setCrosshair(true)
    this.map.addEventListener('click', this._onPickClick)
  }

  /** 激活测距模式 */
  activateMeasure() {
    if (this._active === 'measure') { this._finishMeasure(); return }
    this.deactivate()
    this._active = 'measure'
    this._points = []
    this._setCrosshair(true)
    this.map.addEventListener('click', this._onMeasureClick)
  }

  /** 取消激活（保留测量结果不清除） */
  deactivate() {
    const prev = this._active
    this._active = null
    this._setCrosshair(false)
    try {
      this.map.removeEventListener('click', this._onPickClick)
      this.map.removeEventListener('click', this._onMeasureClick)
    } catch (e) { /* ignore */ }
    this._clearPickMarkers()
    return prev
  }

  /** 清除所有标记和测量结果 */
  clearAll() {
    this.deactivate()
    this._clearMeasure()
  }

  /** 销毁 */
  destroy() {
    this.clearAll()
  }

  // ---- 标点 ----

  _onPickClick = (e) => {
    const BMapGL = window.BMapGL
    if (!BMapGL) return
    const point = e.latlng
    this._clearPickMarkers()

    const marker = new BMapGL.Marker(point)
    this.map.addOverlay(marker)
    this._pickMarkers.push(marker)

    this._showPointInfo(point, '标点信息')
  }

  /** 展示点位信息 InfoWindow（含经纬度 + 逆地理编码地址） */
  _showPointInfo(point, title) {
    const BMapGL = window.BMapGL
    if (!BMapGL) return
    const ts = Date.now()
    const html = `<div style="font-size:13px;white-space:nowrap">
      <div style="font-weight:600;color:#232845;margin-bottom:4px">📍 ${title}</div>
      <div style="color:#666;font-family:monospace">
        <span style="display:inline-block;width:48px">纬度</span> ${point.lat.toFixed(6)}<br>
        <span style="display:inline-block;width:48px">经度</span> ${point.lng.toFixed(6)}
      </div>
      <div id="pinfo-${ts}" style="margin-top:4px;color:#999;font-size:12px">正在获取地址...</div>
    </div>`
    this.map.openInfoWindow(new BMapGL.InfoWindow(html, { width: 240, offset: new BMapGL.Size(0, -30) }), point)

    try {
      const geocoder = new BMapGL.Geocoder()
      geocoder.getLocation(point, (result) => {
        const addr = (result && result.address) ? result.address : ''
        const addrHtml = `<div style="font-size:13px;white-space:nowrap">
          <div style="font-weight:600;color:#232845;margin-bottom:4px">📍 ${title}</div>
          <div style="color:#666;font-family:monospace">
            <span style="display:inline-block;width:48px">纬度</span> ${point.lat.toFixed(6)}<br>
            <span style="display:inline-block;width:48px">经度</span> ${point.lng.toFixed(6)}
          </div>
          ${addr ? `<div style="margin-top:4px;font-size:12px;color:#4f6ef6;border-top:1px solid #eee;padding-top:4px;max-width:260px;word-break:break-all;white-space:normal">
            <span style="font-weight:500">地址</span> ${addr}
          </div>` : ''}
        </div>`
        this.map.openInfoWindow(new BMapGL.InfoWindow(addrHtml, { width: 280, offset: new BMapGL.Size(0, -30) }), point)
      })
    } catch (e) { /* 获取地址失败，保持经纬度显示 */ }
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

    const marker = this._createMeasureMarker(point)
    this.map.addOverlay(marker)
    this._markers.push(marker)

    this._updatePolyline()
    this._updateLabels()

    // 展示当前测距点的 InfoWindow（含地址）
    this._showPointInfo(point, '测距点 #' + this._points.length)
  }

  _createMeasureMarker(point) {
    const BMapGL = window.BMapGL
    if (!BMapGL) return null
    const label = new BMapGL.Label('', {
      position: point,
      offset: new BMapGL.Size(-4, -4)
    })
    label.setStyle({
      width: '8px', height: '8px',
      borderRadius: '50%', background: '#2980b9',
      border: '2px solid #fff', padding: '0', cursor: 'pointer'
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
      strokeColor: '#2980b9', strokeWeight: 2, strokeOpacity: 0.8, strokeStyle: 'dashed'
    })
    this.map.addOverlay(this._polyline)
  }

  _updateLabels() {
    const BMapGL = window.BMapGL
    if (!BMapGL) return
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
        position: mid, offset: new BMapGL.Size(0, -12)
      })
      label.setStyle({
        backgroundColor: 'rgba(255,255,255,0.85)', border: '1px solid #2980b9',
        borderRadius: '3px', padding: '1px 5px', fontSize: '12px',
        fontFamily: 'monospace', color: '#2980b9'
      })
      this.map.addOverlay(label)
      this._labels.push(label)
    }

    if (this._points.length >= 2) {
      const last = this._points[this._points.length - 1]
      const totalLabel = new BMapGL.Label(`<span>总计: ${fmtDist(total)}</span>`, {
        position: last, offset: new BMapGL.Size(10, -10)
      })
      totalLabel.setStyle({
        backgroundColor: 'rgba(255,255,255,0.9)', border: '1px solid #e74c3c',
        borderRadius: '3px', padding: '2px 6px', fontSize: '12px',
        fontFamily: 'monospace', color: '#e74c3c', fontWeight: 'bold'
      })
      this.map.addOverlay(totalLabel)
      this._labels.push(totalLabel)
    }
  }

  _finishMeasure() {
    this._active = null
    this._setCrosshair(false)
    try {
      this.map.removeEventListener('click', this._onMeasureClick)
    } catch (e) { /* ignore */ }
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
}

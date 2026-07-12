/**
 * 百度地图 GL 热力图层 — 双层 Canvas 策略消除拖动延迟
 *
 * 设计：
 *   主 Canvas（_bufCanvas）：只在 map 静止时完整绘制，画幅 = 视口 × 2
 *   显示 Canvas（_displayCanvas）：拖动中从 _bufCanvas 拷贝 + CSS translate 偏移
 *
 *   拖动中只做像素拷贝 + translate，零 pointToPixel 调用 → 无延迟
 *   拖停/缩放完成时 → 重新完整绘制 _bufCanvas
 *
 * 数据格式：{ gridCode, longitude, latitude, westLongitude, eastLongitude,
 *             northLatitude, southLatitude, siteScore }
 * 配色：low(0) → green → yellow → red high(1)
 * 点击检测：使用 BD09 经纬度数学判断
 */
export class BMapHeatmapLayer {
  constructor(map) {
    this.map = map
    this._data = []
    this._visible = false
    this._minScore = 0
    this._maxScore = 1

    // 双层 canvas
    this._container = null
    this._bufCanvas = null    // 缓冲画布（离线绘制，画幅 = 视口 × 2）
    this._displayCanvas = null // 显示画布（全视口，只做像素拷贝）
    this._isMoving = false   // 是否正在拖动/缩放中
    this._dirty = true       // 需要重新绘制主画布
    this._offsetX = 0        // 拖动累计偏移 X
    this._offsetY = 0        // 拖动累计偏移 Y
    this._lastCenter = null  // 上次完整绘制时的地图中心（lng, lat）
    this._lastZoom = 0       // 上次完整绘制时的 zoom 级别
  }

  isVisible() { return this._visible }
  getData() { return this._data }

  async loadData(city) {
    const { getGridScoreByCity } = await import('@/api/jwmap/data')
    const res = await getGridScoreByCity(city)
    this._data = res.data || []
    const scores = this._data.map(d => d.siteScore).filter(s => s != null)
    this._minScore = scores.length ? Math.min(...scores) : 0
    this._maxScore = scores.length ? Math.max(...scores) : 1
    if (this._minScore === this._maxScore) this._maxScore = this._minScore + 0.001
    console.log(`[bmap-heatmap] ${city}: ${scores.length}个网格, siteScore范围 ${this._minScore.toFixed(4)} ~ ${this._maxScore.toFixed(4)}`)
    this._dirty = true
    return this._data
  }

  getGridAtPoint(point) {
    const lat = point.lat
    const lng = point.lng
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
    this._createOverlay()
  }

  hide() {
    if (!this._visible) return
    this._visible = false
    this._unbindReDraw()
    if (this._container && this._container.parentNode) {
      this._container.parentNode.removeChild(this._container)
    }
    this._container = null
    this._bufCanvas = null
    this._displayCanvas = null
  }

  // ====== 创建双层 Canvas ======

  _createOverlay() {
    if (!this.map) return
    const mapContainer = this.map.getContainer()
    if (!mapContainer) return

    this._container = document.createElement('div')
    this._container.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:500;overflow:hidden;'

    // 显示画布 — 覆盖整个视口
    this._displayCanvas = document.createElement('canvas')
    this._displayCanvas.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;'
    this._container.appendChild(this._displayCanvas)

    // 缓冲画布 — 不可见，只在后台绘制
    this._bufCanvas = document.createElement('canvas')
    // display:none 的 canvas 仍然可以 getContext 和 draw

    mapContainer.appendChild(this._container)

    // 初始完整绘制
    this._fullRedraw()

    // 绑定事件
    this._bindReDraw()
  }

  // ====== 事件绑定 ======

  _bindReDraw() {
    this._onMoveStart = (e) => {
      if (!this._visible) return
      this._onDragStart()
    }
    this._onMoveEnd = () => {
      if (!this._visible) return
      this._onDragEnd()
    }
    this._onZoomEnd = () => {
      if (!this._visible) return
      this._dirty = true
      this._onDragEnd()
    }
    this._onResize = () => {
      if (!this._visible) return
      this._dirty = true
      this._fullRedraw()
    }

    this.map.addEventListener('movestart', this._onMoveStart)
    this.map.addEventListener('moveend', this._onMoveEnd)
    this.map.addEventListener('zoomend', this._onZoomEnd)
    this.map.addEventListener('resize', this._onResize)
  }

  _unbindReDraw() {
    try {
      this.map.removeEventListener('movestart', this._onMoveStart)
      this.map.removeEventListener('moveend', this._onMoveEnd)
      this.map.removeEventListener('zoomend', this._onZoomEnd)
      this.map.removeEventListener('resize', this._onResize)
    } catch (e) { /* ignore */ }
    this._onMoveStart = null
    this._onMoveEnd = null
    this._onZoomEnd = null
    this._onResize = null
  }

  // ====== 拖动策略：像素拷贝 + CSS translate ======

  _onDragStart() {
    if (this._isMoving) return
    this._isMoving = true

    // 记录拖动起始时的地图中心，用于计算偏移
    const c = this.map.getCenter()
    this._dragStartCenter = { lng: c.lng, lat: c.lat }

    // 把缓冲画布的内容拷贝到显示画布
    this._copyBufToDisplay()

    // 启动 RAF 循环：只做 CSS translate，不重绘
    this._rafLoop()
  }

  _rafLoop() {
    if (!this._isMoving || !this._displayCanvas) return

    // 计算当前中心相对拖动起始中心的屏幕偏移
    const c = this.map.getCenter()
    const startPx = this.map.pointToPixel(this._dragStartCenter)
    const curPx = this.map.pointToPixel(c)

    const dx = curPx.x - startPx.x
    const dy = curPx.y - startPx.y

    // 应用 CSS translate
    this._displayCanvas.style.transform = `translate(${dx}px, ${dy}px)`
    // 累计偏移（用于 moveend 时调整缓冲绘制偏移）
    this._accDx = dx
    this._accDy = dy

    requestAnimationFrame(() => this._rafLoop())
  }

  _onDragEnd() {
    this._isMoving = false

    // 重置 display canvas 的偏移
    if (this._displayCanvas) {
      this._displayCanvas.style.transform = 'translate(0px, 0px)'
    }

    this._accDx = 0
    this._accDy = 0

    // 立即完整重绘
    this._fullRedraw()
  }

  // ====== 完整重绘 ======

  _fullRedraw() {
    if (!this._visible || !this._bufCanvas) return
    const mapContainer = this.map.getContainer()
    if (!mapContainer) return

    const dpr = window.devicePixelRatio || 1
    const w = mapContainer.clientWidth
    const h = mapContainer.clientHeight
    if (w <= 0 || h <= 0) return

    const dprW = w * dpr
    const dprH = h * dpr

    // 缓冲画布用视口尺寸（不需要扩大，只绘制视口内的网格）
    this._bufCanvas.width = dprW
    this._bufCanvas.height = dprH
    const ctx = this._bufCanvas.getContext('2d')
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.scale(dpr, dpr)
    ctx.clearRect(0, 0, w, h)

    // 绘制所有在视口内的网格
    for (const item of this._data) {
      const rawScore = item.siteScore
      if (rawScore == null) continue
      const score = (rawScore - this._minScore) / (this._maxScore - this._minScore)

      const sw = this.map.pointToPixel({ lng: item.westLongitude, lat: item.southLatitude })
      const ne = this.map.pointToPixel({ lng: item.eastLongitude, lat: item.northLatitude })

      const x1 = Math.min(sw.x, ne.x)
      const y1 = Math.min(sw.y, ne.y)
      const x2 = Math.max(sw.x, ne.x)
      const y2 = Math.max(sw.y, ne.y)

      const m = 5
      if (x2 < -m || x1 > w + m || y2 < -m || y1 > h + m) continue

      const rw = Math.max(1, x2 - x1)
      const rh = Math.max(1, y2 - y1)

      ctx.fillStyle = this._scoreToColor(score, 0.6)
      ctx.fillRect(x1, y1, rw, rh)
      ctx.strokeStyle = 'rgba(255,255,255,0.3)'
      ctx.lineWidth = 0.5
      ctx.strokeRect(x1, y1, rw, rh)
    }

    // 拷贝到显示画布
    this._copyBufToDisplay()

    // 记录当前状态
    const c = this.map.getCenter()
    this._lastCenter = { lng: c.lng, lat: c.lat }
    this._lastZoom = this.map.getZoom()
    this._dirty = false
  }

  // ====== 像素拷贝 ======

  _copyBufToDisplay() {
    if (!this._bufCanvas || !this._displayCanvas) return
    const w = this._bufCanvas.width
    const h = this._bufCanvas.height
    const dprW = w  // 已经是乘以 dpr 后的值
    const dprH = h
    const styleW = this.map.getContainer().clientWidth
    const styleH = this.map.getContainer().clientHeight

    this._displayCanvas.width = dprW
    this._displayCanvas.height = dprH
    this._displayCanvas.style.width = styleW + 'px'
    this._displayCanvas.style.height = styleH + 'px'

    const ctx = this._displayCanvas.getContext('2d')
    // drawImage 直接像素拷贝，极快
    ctx.drawImage(this._bufCanvas, 0, 0)
  }

  // ====== 颜色工具 ======

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

  destroy() {
    this.hide()
    this._data = []
  }
}
